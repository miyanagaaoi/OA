package com.oa.admin.bulk.strategy;

import com.oa.admin.bulk.CsvTable;
import com.oa.admin.bulk.ImportContext;
import com.oa.admin.bulk.ImportFinding;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportReport;
import com.oa.admin.bulk.ImportStrategy;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.domain.SysUserPosition;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 第 ④ 步：岗位任职 / 一人多岗导入（{@code user_position.csv} → {@code sys_user_position}，
 * import-spec §3.5 / §4.5）。
 *
 * <p>关键实现点：
 * <ol>
 *   <li><b>唯一键</b>：{@code (user_account, org_path)} → {@code uk_user_org(user_id, org_id)}（{@code E-POS-004}）；</li>
 *   <li><b>主岗唯一</b>：每人最多一条 {@code is_primary=是}（{@code E-POS-005}），
 *       落库后**回填** {@code sys_user.position}（主岗 {@code post_name}，§2.1 第 ④ 步）；</li>
 *   <li><b>upsert</b>：唯一键命中后更新 {@code position / is_primary / remark}（§6.1）；</li>
 *   <li>警告 {@code W-POS-009}（岗位组织不在该员工所属公司子树内，跨公司兼职）；</li>
 *   <li>数据域：任职组织与目标人员都必须在导入人数据域内（{@code E-POS-020}）。</li>
 * </ol>
 */
@Component
public class UserPositionImportStrategy implements ImportStrategy {

    private final SysUserPositionMapper positionMapper;

    private final SysUserMapper userMapper;

    public UserPositionImportStrategy(SysUserPositionMapper positionMapper, SysUserMapper userMapper) {
        this.positionMapper = positionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public ImportKind kind() {
        return ImportKind.USER_POSITION;
    }

    @Override
    public void write(Object parsed, ImportContext context, ImportReport report) {
        commit(cast(parsed), context, report);
    }

    @SuppressWarnings("unchecked")
    private static List<PositionImportRow> cast(Object parsed) {
        return (List<PositionImportRow>) parsed;
    }

    @Override
    public Object validate(CsvTable table, ImportContext context, ImportReport report) {
        List<PositionImportRow> rows = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        Map<String, Integer> primaryByUser = new HashMap<>();
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
            PositionImportRow parsed = new PositionImportRow(row);
            rows.add(parsed);
            SysOrg org = parsed.orgPath.isEmpty() ? null : orgByPath.get(parsed.orgPath);
            if (parsed.orgPath.isEmpty()) {
                error(report, row, "org_path", parsed.orgPath, "org_path 必填", "E-POS-001");
                parsed.invalid = true;
            } else if (org == null) {
                error(report, row, "org_path", parsed.orgPath, "org_path 在组织表中不存在", "E-POS-001");
                parsed.invalid = true;
            }
            parsed.org = org;
            SysUser user = parsed.userAccount.isEmpty() ? null
                    : context.snapshot().userByAccount(parsed.userAccount);
            if (parsed.userAccount.isEmpty()) {
                error(report, row, "user_account", parsed.userAccount, "user_account 必填", "E-POS-002");
                parsed.invalid = true;
            } else if (user == null) {
                error(report, row, "user_account", parsed.userAccount,
                        "user_account 在人员表中不存在（请先导入人员）", "E-POS-002");
                parsed.invalid = true;
            }
            parsed.user = user;
            try {
                parsed.primary = IdentityEnums.PrimaryFlag.parse(parsed.isPrimary);
            } catch (RuntimeException ex) {
                error(report, row, "is_primary", parsed.isPrimary, "is_primary 只能是 是 / 否", "E-POS-003");
                parsed.invalid = true;
            }
            if (parsed.postName.isEmpty() || parsed.postName.length() > 50) {
                error(report, row, "post_name", parsed.postName, "post_name 必填且 <=50 字符", "E-POS-006");
                parsed.invalid = true;
            }
            if (parsed.remark.length() > 255) {
                error(report, row, "remark", parsed.remark, "remark <=255 字符", "E-POS-007");
                parsed.invalid = true;
            }
            if (!seenPairs.add(parsed.userAccount + "\u0001" + parsed.orgPath)) {
                error(report, row, "org_path", parsed.orgPath,
                        "同一 (user_account, org_path) 在文件中重复（对应唯一键 uk_user_org）", "E-POS-004");
                parsed.invalid = true;
            }
            if (parsed.primary == IdentityEnums.PrimaryFlag.PRIMARY) {
                Integer previous = primaryByUser.put(parsed.userAccount, row.line());
                if (previous != null) {
                    error(report, row, "is_primary", parsed.isPrimary,
                            "每人最多一条 is_primary=是（第 " + previous + " 行已设主岗）", "E-POS-005");
                    parsed.invalid = true;
                }
            }
        }
        for (PositionImportRow row : rows) {
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
            if (userCompany != null && !context.scope().require(kind(), userCompany.getPath(),
                    row.source.line(), "user_account", row.userAccount, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            row.existing = context.snapshot().position(row.user.getId(), row.org.getId());
            if (userCompany != null && !row.org.getPath().startsWith(userCompany.getPath())) {
                warning(report, row.source, "org_path", row.orgPath,
                        "W-POS-009：岗位组织不在该员工所属公司（" + userCompany.getName()
                                + "）子树内，跨公司兼职需确认", "W-POS-009");
            }
        }
        return rows;
    }

    private void commit(List<PositionImportRow> rows, ImportContext context, ImportReport report) {
        int added = 0;
        int updated = 0;
        int skipped = 0;
        Set<Long> primarySynced = new HashSet<>();
        for (PositionImportRow row : rows) {
            Long positionId;
            if (row.existing == null) {
                SysUserPosition position = new SysUserPosition();
                position.setUserId(row.user.getId());
                position.setOrgId(row.org.getId());
                position.setIsPrimary(row.primary.value());
                position.setPosition(row.postName);
                position.setRemark(blankToNull(row.remark));
                positionMapper.insertPosition(position);
                positionId = position.getId();
                added++;
            } else {
                boolean changed = !java.util.Objects.equals(row.existing.getPosition(), row.postName)
                        || !java.util.Objects.equals(blankToNull(row.existing.getRemark()), blankToNull(row.remark))
                        || !java.util.Objects.equals(row.existing.getIsPrimary(), row.primary.value());
                positionId = row.existing.getId();
                if (changed) {
                    positionMapper.updatePosition(positionId, row.postName, blankToNull(row.remark),
                            row.primary.value());
                    updated++;
                } else {
                    skipped++;
                }
            }
            // 设为主岗：同事务把其它任职置为非主岗，并回填 sys_user.position（T-12 / §2.1 第 ④ 步）
            if (row.primary == IdentityEnums.PrimaryFlag.PRIMARY && primarySynced.add(row.user.getId())) {
                for (SysUserPosition other : context.snapshot().positionsOf(row.user.getId())) {
                    if (!java.util.Objects.equals(other.getId(), positionId)
                            && other.getIsPrimary() != null && other.getIsPrimary() == 1) {
                        positionMapper.updatePrimary(other.getId(), 0);
                    }
                }
                SysUser user = new SysUser();
                user.setId(row.user.getId());
                user.setPosition(row.postName);
                user.setUpdatedBy(context.operatorId());
                userMapper.updateUserProfile(user);
            }
        }
        report.stats(added, updated, skipped);
        report.note("岗位导入按 (user_account, org_path) upsert；主岗唯一由服务端强制并回填 sys_user.position");
    }

    private void error(ImportReport report, CsvTable.Row row, String column, String value, String message,
                       String code) {
        String number = code.substring(code.lastIndexOf('-') + 1);
        report.add(ImportFinding.error(kind().error(number), kind().fileName(), row.line(), column, value, message));
    }

    private void warning(ImportReport report, CsvTable.Row row, String column, String value, String message,
                         String code) {
        report.add(ImportFinding.warning(code, kind().fileName(), row.line(), column, value, message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 一行岗位数据。 */
    static final class PositionImportRow {

        final CsvTable.Row source;

        final String userAccount;

        final String orgPath;

        final String postName;

        final String isPrimary;

        final String remark;

        boolean invalid;

        IdentityEnums.PrimaryFlag primary = IdentityEnums.PrimaryFlag.NON_PRIMARY;

        SysOrg org;

        SysUser user;

        SysUserPosition existing;

        PositionImportRow(CsvTable.Row source) {
            this.source = source;
            this.userAccount = source.get("user_account");
            this.orgPath = source.get("org_path");
            this.postName = source.get("post_name");
            this.isPrimary = source.get("is_primary");
            this.remark = source.get("remark");
        }
    }
}
