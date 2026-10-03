package com.oa.admin.bulk.strategy;

import com.oa.admin.bulk.CsvTable;
import com.oa.admin.bulk.ImportContext;
import com.oa.admin.bulk.ImportFinding;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportReport;
import com.oa.admin.bulk.ImportStrategy;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.IdentityEnums.Category;
import com.oa.identity.domain.IdentityEnums.LeaderType;
import com.oa.identity.domain.IdentityEnums.OrgType;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysOrgLeader;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 第 ③ 步：组织负责人导入（{@code org_leader.csv} → {@code sys_org_leader}，import-spec §3.4 / §4.4）。
 *
 * <p>关键实现点：
 * <ol>
 *   <li><b>唯一键</b>：{@code (org_id, user_id, leader_type, category)} 对应库内 {@code uk_org_leader}；
 *       文件内按 {@code (org_path, user_account, leader_type, business_line)} 四元组去重（{@code E-LEAD-009}）；</li>
 *   <li><b>正职唯一</b>（T-09 定稿）：按 {@code org_path + business_line} 分组，每组至多 1 个正职
 *       （{@code business_line} 留空视为同一组），并与库内既有正职比对；</li>
 *   <li><b>{@code business_line} 仅集团层</b>（{@code E-LEAD-008}），取值复用事项类别五值（T-06）；</li>
 *   <li><b>负责人不得为离职人员</b>（{@code E-LEAD-006}）；</li>
 *   <li><b>upsert</b>：四元组命中后只更新 {@code sort_no} 与 {@code remark}（不覆盖后台维护的岗位名与生效区间）；</li>
 *   <li>落库后按正职**回填** {@code sys_org.leader_id}（§2.1 第 ③ 步的冗余回填）。</li>
 * </ol>
 */
@Component
public class OrgLeaderImportStrategy implements ImportStrategy {

    private final SysOrgLeaderMapper leaderMapper;

    private final SysOrgMapper orgMapper;

    public OrgLeaderImportStrategy(SysOrgLeaderMapper leaderMapper, SysOrgMapper orgMapper) {
        this.leaderMapper = leaderMapper;
        this.orgMapper = orgMapper;
    }

    @Override
    public ImportKind kind() {
        return ImportKind.ORG_LEADER;
    }

    @Override
    public void write(Object parsed, ImportContext context, ImportReport report) {
        commit(cast(parsed), context, report);
    }

    @SuppressWarnings("unchecked")
    private static List<LeaderImportRow> cast(Object parsed) {
        return (List<LeaderImportRow>) parsed;
    }

    @Override
    public Object validate(CsvTable table, ImportContext context, ImportReport report) {
        List<LeaderImportRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> primaryByGroup = new HashMap<>();
        Map<Long, String> nameIndex = OrgService.businessPathIndex(
                new ArrayList<>(context.snapshot().orgsById().values()));
        Map<String, SysOrg> orgByPath = new HashMap<>();
        for (SysOrg org : context.snapshot().orgsById().values()) {
            String businessPath = nameIndex.get(org.getId());
            if (businessPath != null) {
                orgByPath.put(businessPath, org);
            }
        }
        for (CsvTable.Row row : table.rows()) {
            LeaderImportRow parsed = new LeaderImportRow(row);
            rows.add(parsed);
            SysOrg org = parsed.orgPath.isEmpty() ? null : orgByPath.get(parsed.orgPath);
            if (parsed.orgPath.isEmpty()) {
                error(report, row, "org_path", parsed.orgPath, "org_path 必填", "E-LEAD-001");
                parsed.invalid = true;
            } else if (org == null) {
                error(report, row, "org_path", parsed.orgPath, "org_path 在组织表中不存在（请先导入组织）",
                        "E-LEAD-001");
                parsed.invalid = true;
            }
            parsed.org = org;
            SysUser user = parsed.userAccount.isEmpty() ? null
                    : context.snapshot().userByAccount(parsed.userAccount);
            if (parsed.userAccount.isEmpty()) {
                error(report, row, "user_account", parsed.userAccount, "user_account 必填", "E-LEAD-002");
                parsed.invalid = true;
            } else if (user == null) {
                error(report, row, "user_account", parsed.userAccount,
                        "user_account 在人员表中不存在（请先导入人员）", "E-LEAD-002");
                parsed.invalid = true;
            } else if (SysUser.STATUS_RESIGNED.equals(user.getStatus())) {
                error(report, row, "user_account", parsed.userAccount,
                        "负责人不得为离职人员（审批人解析会取到无效候选人）", "E-LEAD-006");
                parsed.invalid = true;
            }
            parsed.user = user;
            LeaderType leaderType = IdentityEnums.LeaderType.ofCode(parsed.leaderType);
            if (leaderType == null && "正职".equals(parsed.leaderType)) {
                leaderType = LeaderType.PRIMARY;
            }
            if (leaderType == null && "副职".equals(parsed.leaderType)) {
                leaderType = LeaderType.DEPUTY;
            }
            if (leaderType == null) {
                error(report, row, "leader_type", parsed.leaderType, "leader_type 只能是 正职 / 副职", "E-LEAD-003");
                parsed.invalid = true;
            }
            parsed.type = leaderType;
            if (!parsed.sort.isEmpty()) {
                try {
                    int sort = Integer.parseInt(parsed.sort);
                    if (sort < 0 || sort > 9999) {
                        throw new NumberFormatException("out of range");
                    }
                    parsed.sortNo = sort;
                } catch (NumberFormatException ex) {
                    error(report, row, "sort", parsed.sort, "sort 必须是 0-9999 的整数或留空", "E-LEAD-007");
                    parsed.invalid = true;
                }
            }
            if (!parsed.businessLine.isEmpty()) {
                try {
                    parsed.category = IdentityEnums.Category.parse(parsed.businessLine);
                } catch (RuntimeException ex) {
                    error(report, row, "business_line", parsed.businessLine,
                            "business_line 只能是 经营 / 经济 / 行政 / 人力 / 投资 或留空", "E-LEAD-005");
                    parsed.invalid = true;
                }
                if (parsed.category != null && org != null && !OrgType.GROUP.code().equals(org.getOrgType())) {
                    error(report, row, "business_line", parsed.businessLine,
                            "business_line 仅允许填在 org_type=集团 的节点上", "E-LEAD-008");
                    parsed.invalid = true;
                }
            }
            if (parsed.remark.length() > 255) {
                error(report, row, "remark", parsed.remark, "remark <=255 字符", "E-LEAD-010");
                parsed.invalid = true;
            }
            String key = String.join("\u0001", parsed.orgPath, parsed.userAccount, parsed.leaderType,
                    parsed.category == null ? "" : parsed.category.code());
            if (!seen.add(key)) {
                error(report, row, "org_path", parsed.orgPath,
                        "(org_path, user_account, leader_type, business_line) 四元组重复", "E-LEAD-009");
                parsed.invalid = true;
            }
            if (parsed.type == LeaderType.PRIMARY) {
                String group = parsed.orgPath + "\u0001"
                        + (parsed.category == null ? "" : parsed.category.code());
                Integer previous = primaryByGroup.put(group, row.line());
                if (previous != null) {
                    error(report, row, "leader_type", parsed.leaderType,
                            "同一组织 + 同一业务线只能有一个正职（第 " + previous + " 行已设正职）", "E-LEAD-004");
                    parsed.invalid = true;
                }
            }
        }
        for (LeaderImportRow row : rows) {
            if (row.invalid || row.type != LeaderType.PRIMARY || row.org == null || row.user == null) {
                continue;
            }
            String category = row.category == null ? null : row.category.code();
            for (SysOrgLeader binding : context.snapshot().leadersOf(row.org.getId(), category)) {
                if (LeaderType.PRIMARY.code().equals(binding.getLeaderType())
                        && !binding.getUserId().equals(row.user.getId())) {
                    error(report, row.source, "leader_type", row.leaderType,
                            "该组织该业务线在库中已有正职（user_id=" + binding.getUserId()
                                    + "），请先解除原正职绑定", "E-LEAD-004");
                    row.invalid = true;
                }
            }
        }
        for (LeaderImportRow row : rows) {
            if (row.invalid) {
                continue;
            }
            if (!context.scope().require(kind(), row.org.getPath(), row.source.line(), "org_path",
                    row.orgPath, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            SysOrg userCompany = row.user.getCompanyId() == null ? null
                    : context.snapshot().orgsById().get(row.user.getCompanyId());
            if (userCompany != null && !context.scope().require(kind(), userCompany.getPath(), row.source.line(),
                    "user_account", row.userAccount, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            row.existing = context.snapshot().leaderBinding(row.org.getId(), row.user.getId(),
                    row.type.code(), row.category == null ? null : row.category.code());
        }
        return rows;
    }

    private void commit(List<LeaderImportRow> rows, ImportContext context, ImportReport report) {
        int added = 0;
        int updated = 0;
        int skipped = 0;
        for (LeaderImportRow row : rows) {
            if (row.existing == null) {
                SysOrgLeader leader = new SysOrgLeader();
                leader.setOrgId(row.org.getId());
                leader.setUserId(row.user.getId());
                leader.setLeaderType(row.type.code());
                leader.setCategory(row.category == null ? null : row.category.code());
                leader.setSortNo(row.sortNo);
                leader.setRemark(blankToNull(row.remark));
                leader.setCreatedBy(context.operatorId());
                leader.setUpdatedBy(context.operatorId());
                leaderMapper.insertLeader(leader);
                added++;
            } else {
                boolean changed = !java.util.Objects.equals(row.existing.getSortNo(), row.sortNo)
                        || !java.util.Objects.equals(blankToNull(row.existing.getRemark()), blankToNull(row.remark));
                if (!changed) {
                    skipped++;
                    continue;
                }
                leaderMapper.updateLeaderFromImport(row.existing.getId(), row.sortNo, blankToNull(row.remark),
                        context.operatorId());
                updated++;
            }
            if (row.type == LeaderType.PRIMARY && row.category == null) {
                orgMapper.updateLeaderId(row.org.getId(), row.user.getId(), context.operatorId());
            }
        }
        report.stats(added, updated, skipped);
        report.note("负责人导入按 (org_path, user_account, leader_type, business_line) upsert；"
                + "正职已回填 sys_org.leader_id（冗余字段）");
    }

    private void error(ImportReport report, CsvTable.Row row, String column, String value, String message,
                       String code) {
        String number = code.substring(code.lastIndexOf('-') + 1);
        report.add(ImportFinding.error(kind().error(number), kind().fileName(), row.line(), column, value, message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 一行负责人数据。 */
    static final class LeaderImportRow {

        final CsvTable.Row source;

        final String orgPath;

        final String userAccount;

        final String leaderType;

        final String sort;

        final String businessLine;

        final String remark;

        boolean invalid;

        int sortNo;

        LeaderType type;

        Category category;

        SysOrg org;

        SysUser user;

        SysOrgLeader existing;

        LeaderImportRow(CsvTable.Row source) {
            this.source = source;
            this.orgPath = source.get("org_path");
            this.userAccount = source.get("user_account");
            this.leaderType = source.get("leader_type");
            this.sort = source.get("sort");
            this.businessLine = source.get("business_line");
            this.remark = source.get("remark");
        }
    }
}
