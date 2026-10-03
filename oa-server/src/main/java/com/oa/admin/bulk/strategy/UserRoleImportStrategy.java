package com.oa.admin.bulk.strategy;

import com.oa.admin.bulk.CsvTable;
import com.oa.admin.bulk.ImportContext;
import com.oa.admin.bulk.ImportFinding;
import com.oa.admin.bulk.ImportKind;
import com.oa.admin.bulk.ImportReport;
import com.oa.admin.bulk.ImportStrategy;
import com.oa.authz.domain.SysRole;
import com.oa.authz.domain.SysUserRole;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 第 ⑤ 步：角色分配导入（{@code user_role.csv} → {@code sys_user_role}，import-spec §3.6 / §4.6）。
 *
 * <p>关键实现点：
 * <ol>
 *   <li><b>角色码白名单</b>：{@code role_code} 必须命中已初始化角色集（{@code sys_role}）——
 *       导入**不创建角色**（§2.3），未命中即 {@code E-ROLE-001}；</li>
 *   <li><b>唯一键</b>：{@code (user_account, role_code, scope_org_path)}，空 {@code scope_org_path} 归一为同一组
 *       （与库内 {@code uk_sys_user_role(user_id, role_id, scope_org_key)} 完全一致，§3.6 注）；</li>
 *   <li><b>upsert</b>：命中后只更新 {@code remark}（不改角色与数据域范围，避免误改他人授权面）；</li>
 *   <li><b>不校验人员状态</b>：离职人员的历史角色由后台清理（§4.6 注）；</li>
 *   <li>数据域：目标人员必须在导入人数据域内（{@code E-ROLE-020}）。</li>
 * </ol>
 */
@Component
public class UserRoleImportStrategy implements ImportStrategy {

    /** 角色码格式（enums.md §1.1 小写蛇形）。 */
    private static final Pattern ROLE_CODE = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");

    private final SysUserRoleMapper userRoleMapper;

    public UserRoleImportStrategy(SysUserRoleMapper userRoleMapper) {
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public ImportKind kind() {
        return ImportKind.USER_ROLE;
    }

    @Override
    public void write(Object parsed, ImportContext context, ImportReport report) {
        commit(cast(parsed), context, report);
    }

    @SuppressWarnings("unchecked")
    private static List<RoleImportRow> cast(Object parsed) {
        return (List<RoleImportRow>) parsed;
    }

    @Override
    public Object validate(CsvTable table, ImportContext context, ImportReport report) {
        List<RoleImportRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<Long, String> nameIndex = OrgService.businessPathIndex(
                new ArrayList<>(context.snapshot().orgsById().values()));
        Map<String, SysOrg> orgByPath = new HashMap<>();
        for (SysOrg org : context.snapshot().orgsById().values()) {
            String businessPath = nameIndex.get(org.getId());
            if (businessPath != null) {
                orgByPath.put(businessPath, org);
            }
        }
        List<String> roleCodes = context.snapshot().roleCodes();
        for (CsvTable.Row row : table.rows()) {
            RoleImportRow parsed = new RoleImportRow(row);
            rows.add(parsed);
            if (parsed.roleCode.isEmpty() || !ROLE_CODE.matcher(parsed.roleCode).matches()) {
                error(report, row, "role_code", parsed.roleCode,
                        "role_code 必填且为小写蛇形（^[a-z][a-z0-9_]{1,31}$）", "E-ROLE-001");
                parsed.invalid = true;
            } else {
                SysRole role = context.snapshot().roleByCode(parsed.roleCode);
                if (role == null) {
                    error(report, row, "role_code", parsed.roleCode,
                            "角色码不在已初始化角色集内（导入不创建角色）；可用值：" + String.join("、", roleCodes),
                            "E-ROLE-001");
                    parsed.invalid = true;
                }
                parsed.role = role;
            }
            SysUser user = parsed.userAccount.isEmpty() ? null
                    : context.snapshot().userByAccount(parsed.userAccount);
            if (parsed.userAccount.isEmpty()) {
                error(report, row, "user_account", parsed.userAccount, "user_account 必填", "E-ROLE-002");
                parsed.invalid = true;
            } else if (user == null) {
                error(report, row, "user_account", parsed.userAccount,
                        "user_account 在人员表中不存在（请先导入人员）", "E-ROLE-002");
                parsed.invalid = true;
            }
            parsed.user = user;
            SysOrg scopeOrg = null;
            if (!parsed.scopeOrgPath.isEmpty()) {
                scopeOrg = orgByPath.get(parsed.scopeOrgPath);
                if (scopeOrg == null) {
                    error(report, row, "scope_org_path", parsed.scopeOrgPath,
                            "scope_org_path 在组织表中不存在（空 = 按角色默认数据域）", "E-ROLE-004");
                    parsed.invalid = true;
                }
            }
            parsed.scopeOrg = scopeOrg;
            if (parsed.remark.length() > 255) {
                error(report, row, "remark", parsed.remark, "remark ≤255 字符", "E-ROLE-005");
                parsed.invalid = true;
            }
            String key = String.join("\u0001", parsed.userAccount, parsed.roleCode,
                    scopeOrg == null ? "" : String.valueOf(scopeOrg.getId()));
            if (!seen.add(key)) {
                error(report, row, "role_code", parsed.roleCode,
                        "(user_account, role_code, scope_org_path) 重复；空数据域归一为同一组", "E-ROLE-003");
                parsed.invalid = true;
            }
        }
        for (RoleImportRow row : rows) {
            if (row.invalid) {
                continue;
            }
            SysOrg userCompany = row.user.getCompanyId() == null ? null
                    : context.snapshot().orgsById().get(row.user.getCompanyId());
            if (userCompany != null && !context.scope().require(kind(), userCompany.getPath(),
                    row.source.line(), "user_account", row.userAccount, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            if (row.scopeOrg != null && !context.scope().require(kind(), row.scopeOrg.getPath(),
                    row.source.line(), "scope_org_path", row.scopeOrgPath, report.getFindings())) {
                row.invalid = true;
                continue;
            }
            long scopeOrgKey = row.scopeOrg == null ? 0L : row.scopeOrg.getId();
            row.existingId = context.snapshot().countAssignment(row.user.getId(), row.role.getId(), scopeOrgKey);
        }
        return rows;
    }

    private void commit(List<RoleImportRow> rows, ImportContext context, ImportReport report) {
        int added = 0;
        int updated = 0;
        int skipped = 0;
        for (RoleImportRow row : rows) {
            if (row.existingId == null) {
                SysUserRole assignment = new SysUserRole();
                assignment.setUserId(row.user.getId());
                assignment.setRoleId(row.role.getId());
                assignment.setScopeOrgId(row.scopeOrg == null ? null : row.scopeOrg.getId());
                assignment.setRemark(blankToNull(row.remark));
                assignment.setCreatedBy(context.operatorId());
                userRoleMapper.insertUserRole(assignment);
                added++;
            } else {
                String existingRemark = context.snapshot().assignmentRemark(row.existingId);
                if (java.util.Objects.equals(blankToNull(existingRemark), blankToNull(row.remark))) {
                    skipped++;
                    continue;
                }
                userRoleMapper.updateRemark(row.existingId, blankToNull(row.remark));
                updated++;
            }
        }
        report.stats(added, updated, skipped);
        report.note("角色分配按 (user_account, role_code, scope_org_path) upsert（空数据域归一为同一组）；"
                + "导入不创建角色、不改动数据域范围");
    }

    private void error(ImportReport report, CsvTable.Row row, String column, String value, String message,
                       String code) {
        String number = code.substring(code.lastIndexOf('-') + 1);
        report.add(ImportFinding.error(kind().error(number), kind().fileName(), row.line(), column, value, message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 一行角色分配数据。 */
    static final class RoleImportRow {

        final CsvTable.Row source;

        final String userAccount;

        final String roleCode;

        final String scopeOrgPath;

        final String remark;

        boolean invalid;

        SysUser user;

        SysRole role;

        SysOrg scopeOrg;

        Long existingId;

        RoleImportRow(CsvTable.Row source) {
            this.source = source;
            this.userAccount = source.get("user_account");
            this.roleCode = source.get("role_code");
            this.scopeOrgPath = source.get("scope_org_path");
            this.remark = source.get("remark");
        }
    }
}
