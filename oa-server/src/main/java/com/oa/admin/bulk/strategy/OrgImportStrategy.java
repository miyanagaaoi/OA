package com.oa.admin.bulk.strategy;

import com.oa.admin.bulk.CsvTable;
import com.oa.admin.bulk.ImportContext;
import com.oa.admin.bulk.ImportFinding;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportReport;
import com.oa.admin.bulk.ImportStrategy;
import com.oa.identity.app.InFlightChecker;
import com.oa.identity.app.OrgHierarchy;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.IdentityEnums.OrgStatus;
import com.oa.identity.domain.IdentityEnums.OrgType;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 第 ① 步：组织架构导入（{@code org.csv} → {@code sys_org}，import-spec §3.2 / §4.2 / §6.1）。
 *
 * <p>关键实现点：
 * <ol>
 *   <li><b>业务键</b>：{@code org_path}（名称路径，全库唯一），文件内自底向上解析 ——
 *       按「路径段数升序」排序后落库，父行必然先于子行（父路径是子路径的严格前缀）；</li>
 *   <li><b>path/depth</b>：{@code sys_org.path} 依赖自增 id，故先以**事务内唯一占位路径**插入，
 *       拿到 id 后立即回填真实路径（与 {@code OrgService#create} 同一套路）；</li>
 *   <li><b>父节点变更</b>（§6.1 / §7.1 场景 1）：级联重算整棵子树 path（{@link OrgHierarchy#rebaseSubtree}）；</li>
 *   <li><b>停用补偿控制</b>（§8.2）：{@code status=停用} 且子树下存在途单据 → {@code E-ORG-010} 拒绝整批；</li>
 *   <li><b>数据域</b>：父节点（以及被更新的既有节点）必须在导入人数据域内，否则 {@code E-ORG-020} fail-closed。</li>
 * </ol>
 */
@Component
public class OrgImportStrategy implements ImportStrategy {

    private final SysOrgMapper orgMapper;

    private final SysUserMapper userMapper;

    private final SysOrgLeaderMapper leaderMapper;

    private final InFlightChecker inFlightChecker;

    public OrgImportStrategy(SysOrgMapper orgMapper, SysUserMapper userMapper, SysOrgLeaderMapper leaderMapper,
                             InFlightChecker inFlightChecker) {
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.leaderMapper = leaderMapper;
        this.inFlightChecker = inFlightChecker;
    }

    @Override
    public ImportKind kind() {
        return ImportKind.ORG;
    }

    @Override
    public void write(Object parsed, ImportContext context, ImportReport report) {
        commit(cast(parsed), context, report);
    }

    @SuppressWarnings("unchecked")
    private static List<OrgImportRow> cast(Object parsed) {
        return (List<OrgImportRow>) parsed;
    }

    @Override
    public Object validate(CsvTable table, ImportContext context, ImportReport report) {
        List<OrgImportRow> rows = new ArrayList<>();
        Map<String, OrgImportRow> byPath = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        int groupRows = 0;
        for (CsvTable.Row row : table.rows()) {
            OrgImportRow parsed = new OrgImportRow(row);
            rows.add(parsed);
            String path = parsed.orgPath;
            if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("//")
                    || path.length() > 255) {
                error(report, row, "org_path", path,
                        "org_path 不得为空、不得含空段或首尾斜杠，长度 <=255（当前 " + path.length() + "）",
                        "E-ORG-008");
                parsed.invalid = true;
            }
            if (!seen.add(path)) {
                error(report, row, "org_path", path, "org_path 在文件内重复，请合并重复行", "E-ORG-001");
                parsed.invalid = true;
            } else {
                byPath.put(path, parsed);
            }
            OrgType type = IdentityEnums.OrgType.ofCode(parsed.orgType);
            if (type == null) {
                type = labelToOrgType(parsed.orgType);
            }
            if (type == null) {
                error(report, row, "org_type", parsed.orgType, "org_type 只能是 集团 / 公司 / 部门 / 科室",
                        "E-ORG-007");
                parsed.invalid = true;
            }
            parsed.type = type;
            OrgStatus status = IdentityEnums.OrgStatus.ofCode(parsed.status);
            if (status == null && "停用".equals(parsed.status)) {
                status = OrgStatus.DISABLED;
            }
            if (status == null && "启用".equals(parsed.status)) {
                status = OrgStatus.ACTIVE;
            }
            if (status == null) {
                error(report, row, "status", parsed.status, "status 只能是 启用 / 停用", "E-ORG-009");
                parsed.invalid = true;
            }
            parsed.orgStatus = status;
            if (parsed.orgName.isEmpty() || parsed.orgName.length() > 100) {
                error(report, row, "org_name", parsed.orgName, "org_name 必填且 <=100 字符", "E-ORG-012");
                parsed.invalid = true;
            }
            if (parsed.remark.length() > 255) {
                error(report, row, "remark", parsed.remark, "remark <=255 字符", "E-ORG-013");
                parsed.invalid = true;
            }
            if (type == OrgType.GROUP) {
                groupRows++;
            }
        }

        Map<Long, String> nameIndex = OrgService.businessPathIndex(
                new ArrayList<>(context.snapshot().orgsById().values()));
        Map<String, SysOrg> dbByBusinessPath = new HashMap<>();
        for (SysOrg org : context.snapshot().orgsById().values()) {
            String businessPath = nameIndex.get(org.getId());
            if (businessPath != null) {
                dbByBusinessPath.put(businessPath, org);
            }
        }

        for (OrgImportRow row : rows) {
            if (row.invalid) {
                continue;
            }
            CsvTable.Row source = row.source;
            String expectedParent = OrgHierarchy.parentBusinessPath(row.orgPath);
            if (row.type == OrgType.GROUP) {
                if (!row.parentPath.isEmpty()) {
                    error(report, source, "parent_path", row.parentPath, "集团节点的 parent_path 必须留空",
                            "E-ORG-004");
                    row.invalid = true;
                }
            } else if (row.parentPath.isEmpty()) {
                error(report, source, "parent_path", row.parentPath, "非集团节点的 parent_path 必填", "E-ORG-005");
                row.invalid = true;
            } else if (!row.parentPath.equals(expectedParent)) {
                error(report, source, "parent_path", row.parentPath,
                        "parent_path 必须等于 org_path 去掉最后一段（期望 " + expectedParent + "）", "E-ORG-003");
                row.invalid = true;
            }
            if (row.invalid) {
                continue;
            }
            OrgImportRow inFileParent = byPath.get(row.parentPath);
            SysOrg dbParent = row.parentPath.isEmpty() ? null : dbByBusinessPath.get(row.parentPath);
            if (!row.parentPath.isEmpty() && inFileParent == null && dbParent == null) {
                error(report, source, "parent_path", row.parentPath,
                        "父路径不存在：" + row.parentPath + "。请先导入父组织行，或修正拼写", "E-ORG-002");
                row.invalid = true;
                continue;
            }
            if (inFileParent != null && (inFileParent.invalid || inFileParent.type == null)) {
                row.invalid = true;
                continue;
            }
            OrgType parentType = inFileParent != null ? inFileParent.type
                    : (dbParent == null ? null : IdentityEnums.OrgType.ofCode(dbParent.getOrgType()));
            if (row.type != OrgType.GROUP && (parentType == OrgType.SECTION
                    || (row.type == OrgType.COMPANY && parentType != OrgType.GROUP)
                    || (row.type == OrgType.SECTION && parentType != OrgType.DEPT))) {
                error(report, source, "org_type", row.orgType,
                        "层级必须为 集团->公司->部门->科室：公司只能挂在集团下，科室只能挂在部门下（当前父类型 "
                                + (parentType == null ? "未知" : parentType.label()) + "）", "E-ORG-006");
                row.invalid = true;
                continue;
            }
            if (row.type == OrgType.GROUP && parentType != null) {
                error(report, source, "parent_path", row.parentPath, "集团根节点不能有父节点", "E-ORG-004");
                row.invalid = true;
                continue;
            }
            if (dbParent != null && !context.scope().require(kind(), dbParent.getPath(), source.line(),
                    "parent_path", row.parentPath, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            SysOrg existing = dbByBusinessPath.get(row.orgPath);
            if (existing != null && !context.scope().require(kind(), existing.getPath(), source.line(),
                    "org_path", row.orgPath, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            row.existing = existing;
            if (existing != null && row.orgStatus == OrgStatus.DISABLED) {
                InFlightChecker.InFlightSummary summary = inFlightChecker.checkOrgSubtree(existing.getPath());
                if (summary.inFlightInstances() > 0) {
                    error(report, source, "status", row.status,
                            "组织停用前必须清空在途单据：" + row.orgPath + " 及其子树下仍有 "
                                    + summary.inFlightInstances() + " 张在途单据（PRD 5.5）"
                                    + (summary.bizNos().isEmpty() ? ""
                                            : "；涉及单号：" + String.join("、", summary.bizNos())),
                            "E-ORG-010");
                    row.invalid = true;
                    continue;
                }
                int activeStaff = userMapper.countActiveByOrgPath(OrgHierarchy.normalize(existing.getPath()) + "%");
                if (activeStaff > 0) {
                    warning(report, source, "status", row.status,
                            "W-ORG-017：停用组织下仍有 " + activeStaff + " 名在职人员，建议先转岗", "W-ORG-017");
                }
            }
            String parentStatus = inFileParent != null
                    ? (inFileParent.orgStatus == null ? null : inFileParent.orgStatus.code())
                    : (dbParent == null ? null : dbParent.getStatus());
            if ("disabled".equals(parentStatus)) {
                warning(report, source, "parent_path", row.parentPath,
                        "W-ORG-016：父组织已停用，子节点导入后不可作为发起归属", "W-ORG-016");
            }
        }

        int dbGroups = 0;
        for (SysOrg org : context.snapshot().orgsById().values()) {
            if (OrgType.GROUP.code().equals(org.getOrgType())) {
                dbGroups++;
            }
        }
        if (groupRows == 0 && dbGroups == 0) {
            report.add(ImportFinding.error(kind().error("015"), kind().fileName(), 0, "org_path", null,
                    "整棵组织树必须且只能有 1 个集团根节点：当前库内与文件中都没有集团节点"));
        } else if (dbGroups == 0 && groupRows > 1) {
            report.add(ImportFinding.error(kind().error("015"), kind().fileName(), 0, "org_path", null,
                    "整棵组织树有且仅有 1 个集团根节点：文件中出现 " + groupRows + " 行集团节点"));
        }

        Set<Long> primaryOrgs = new LinkedHashSet<>(leaderMapper.selectPrimaryOrgIds());
        for (OrgImportRow row : rows) {
            if (row.invalid || row.existing == null) {
                continue;
            }
            if ((row.type == OrgType.DEPT || row.type == OrgType.SECTION)
                    && !primaryOrgs.contains(row.existing.getId())) {
                warning(report, row.source, "org_path", row.orgPath,
                        "W-ORG-014：该节点未设正职负责人，其成员发起单据会被拦截（AC-11）", "W-ORG-014");
            }
        }
        return rows;
    }

    /** 阶段 C：按路径段数升序 upsert（父先于子），并回填 path/depth。 */
    private void commit(List<OrgImportRow> rows, ImportContext context, ImportReport report) {
        List<OrgImportRow> ordered = new ArrayList<>(rows);
        ordered.sort((left, right) -> {
            int byDepth = Integer.compare(depthOf(left.orgPath), depthOf(right.orgPath));
            return byDepth != 0 ? byDepth : Integer.compare(left.source.line(), right.source.line());
        });
        Map<Long, SysOrg> resolved = new HashMap<>(context.snapshot().orgsById());
        Map<Long, String> nameIndex = OrgService.businessPathIndex(
                new ArrayList<>(context.snapshot().orgsById().values()));
        Map<String, Long> idByBusinessPath = new HashMap<>();
        for (SysOrg org : context.snapshot().orgsById().values()) {
            String businessPath = nameIndex.get(org.getId());
            if (businessPath != null) {
                idByBusinessPath.put(businessPath, org.getId());
            }
        }

        int added = 0;
        int updated = 0;
        int skipped = 0;
        int sequence = 0;
        for (OrgImportRow row : ordered) {
            sequence++;
            String parentRealPath = null;
            Long parentId = null;
            if (!row.parentPath.isEmpty()) {
                Long resolvedParentId = idByBusinessPath.get(row.parentPath);
                if (resolvedParentId == null) {
                    for (OrgImportRow candidate : ordered) {
                        if (candidate.orgPath.equals(row.parentPath) && candidate.newId != null) {
                            resolvedParentId = candidate.newId;
                            break;
                        }
                    }
                }
                if (resolvedParentId == null) {
                    throw new IllegalStateException("导入内部错误：父路径未解析 " + row.parentPath);
                }
                parentId = resolvedParentId;
                SysOrg parent = resolved.get(parentId);
                if (parent == null) {
                    parent = orgMapper.selectById(parentId);
                }
                parentRealPath = parent == null ? null : parent.getPath();
            }
            int depth = row.type.depth();
            if (row.existing == null) {
                SysOrg org = new SysOrg();
                org.setParentId(parentId);
                org.setOrgType(row.type.code());
                org.setName(row.orgName);
                // 占位路径：path 依赖自增 id，且 uk_sys_org_path 唯一 -> 用「运算符 id + 序号」保证事务内不撞
                org.setPath("/pending-" + context.operatorId() + "-" + sequence + "/");
                org.setDepth(depth);
                org.setStatus(row.orgStatus.code());
                org.setRemark(blankToNull(row.remark));
                org.setCreatedBy(context.operatorId());
                orgMapper.insertOrg(org);
                String realPath = OrgHierarchy.childPath(parentRealPath, org.getId());
                orgMapper.updatePathAndDepth(org.getId(), realPath, depth, context.operatorId());
                org.setPath(realPath);
                resolved.put(org.getId(), org);
                idByBusinessPath.put(row.orgPath, org.getId());
                row.newId = org.getId();
                added++;
            } else {
                SysOrg org = new SysOrg();
                org.setId(row.existing.getId());
                org.setOrgType(row.type.code());
                org.setName(row.orgName);
                org.setParentId(parentId);
                org.setDepth(depth);
                org.setStatus(row.orgStatus.code());
                org.setRemark(blankToNull(row.remark));
                org.setUpdatedBy(context.operatorId());
                String realPath = OrgHierarchy.childPath(parentRealPath, row.existing.getId());
                org.setPath(realPath);
                boolean changed = !java.util.Objects.equals(row.existing.getParentId(), parentId)
                        || !java.util.Objects.equals(row.existing.getName(), row.orgName)
                        || !java.util.Objects.equals(row.existing.getOrgType(), row.type.code())
                        || !java.util.Objects.equals(row.existing.getStatus(), row.orgStatus.code())
                        || !java.util.Objects.equals(blankToNull(row.existing.getRemark()), blankToNull(row.remark));
                if (!changed) {
                    skipped++;
                    row.newId = row.existing.getId();
                    idByBusinessPath.put(row.orgPath, row.existing.getId());
                    continue;
                }
                if (!java.util.Objects.equals(row.existing.getParentId(), parentId)) {
                    cascadeRebase(row.existing, realPath, context.operatorId());
                }
                orgMapper.updateOrgImport(org);
                row.newId = row.existing.getId();
                idByBusinessPath.put(row.orgPath, row.existing.getId());
                updated++;
            }
        }
        report.stats(added, updated, skipped);
        report.note("组织导入按 org_path 业务键 upsert；文件内父行先于子行落库，路径与 depth 由服务端重算");
    }

    /** 父节点变更：级联重算整棵子树的 path（§6.1 / §7.1 场景 1）。 */
    private void cascadeRebase(SysOrg existing, String newRootPath, Long operator) {
        List<SysOrg> subtree = orgMapper.selectSubtree(existing.getPath(), true);
        List<OrgHierarchy.SubtreeNode> nodes = new ArrayList<>();
        for (SysOrg node : subtree) {
            nodes.add(new OrgHierarchy.SubtreeNode(node.getId(), node.getPath(),
                    node.getDepth() == null ? 0 : node.getDepth()));
        }
        for (OrgHierarchy.SubtreeNode node : OrgHierarchy.rebaseSubtree(nodes, existing.getPath(), newRootPath)) {
            if (node.id() == existing.getId()) {
                continue;
            }
            orgMapper.updatePathAndDepth(node.id(), node.path(), node.depth(), operator);
        }
    }

    private static int depthOf(String path) {
        int count = 0;
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static OrgType labelToOrgType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim()) {
            case "集团" -> OrgType.GROUP;
            case "公司" -> OrgType.COMPANY;
            case "部门" -> OrgType.DEPT;
            case "科室" -> OrgType.SECTION;
            default -> null;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    /** 一行组织数据（校验期填充解析结果与既有节点）。 */
    static final class OrgImportRow {

        final CsvTable.Row source;

        final String orgPath;

        final String orgName;

        final String orgType;

        final String parentPath;

        final String status;

        final String remark;

        boolean invalid;

        OrgType type;

        OrgStatus orgStatus;

        SysOrg existing;

        Long newId;

        OrgImportRow(CsvTable.Row source) {
            this.source = source;
            this.orgPath = source.get("org_path");
            this.orgName = source.get("org_name");
            this.orgType = source.get("org_type");
            this.parentPath = source.get("parent_path");
            this.status = source.get("status");
            this.remark = source.get("remark");
        }
    }
}
