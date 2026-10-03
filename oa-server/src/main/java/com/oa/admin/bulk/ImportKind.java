package com.oa.admin.bulk;

import java.util.List;

/**
 * 五类批量导入模板（doc/import-spec.md §2.1 的不可调换顺序）。
 *
 * <p>列名与顺序逐字取自 import-spec §3.2 ~ §3.6 与 {@code oa-deploy/import/*.csv}；
 * 服务端以此做 {@code E-HDR-001} 表头逐字校验（改名/增列/减列/换序一律拒绝）。
 */
public enum ImportKind {

    /** ① 组织架构（{@code sys_org}）。 */
    ORG("org", "org.csv", "组织架构", "E-ORG",
            List.of("org_path", "org_name", "org_type", "parent_path", "status", "remark"),
            "/api/v1/identity/orgs/import"),

    /** ② 人员（{@code sys_user}）。 */
    USER("user", "user.csv", "人员", "E-USER",
            List.of("account", "employee_no", "name", "phone", "email", "company_path", "dept_path",
                    "status", "remark"),
            "/api/v1/identity/users/import"),

    /** ③ 组织负责人（{@code sys_org_leader}）。 */
    ORG_LEADER("org-leader", "org_leader.csv", "组织负责人", "E-LEAD",
            List.of("org_path", "user_account", "leader_type", "sort", "business_line", "remark"),
            "/api/v1/identity/org-leaders/import"),

    /** ④ 岗位任职 / 一人多岗（{@code sys_user_position}）。 */
    USER_POSITION("user-position", "user_position.csv", "岗位任职", "E-POS",
            List.of("user_account", "org_path", "post_name", "is_primary", "remark"),
            "/api/v1/identity/user-positions/import"),

    /** ⑤ 角色分配（{@code sys_user_role}）。 */
    USER_ROLE("user-role", "user_role.csv", "角色分配", "E-ROLE",
            List.of("user_account", "role_code", "scope_org_path", "remark"),
            "/api/v1/identity/user-roles/import");

    private final String code;

    private final String fileName;

    private final String label;

    /** 错误码段（import-spec §5.2）。 */
    private final String codePrefix;

    private final List<String> columns;

    private final String routePrefix;

    ImportKind(String code, String fileName, String label, String codePrefix, List<String> columns,
               String routePrefix) {
        this.code = code;
        this.fileName = fileName;
        this.label = label;
        this.codePrefix = codePrefix;
        this.columns = List.copyOf(columns);
        this.routePrefix = routePrefix;
    }

    public String code() {
        return code;
    }

    /** 模板文件名（大小写逐字一致，import-spec E-FILE-001）。 */
    public String fileName() {
        return fileName;
    }

    public String label() {
        return label;
    }

    public String codePrefix() {
        return codePrefix;
    }

    /** 表头列（顺序即契约）。 */
    public List<String> columns() {
        return columns;
    }

    /** 该类的 preview/commit 路由前缀（完整路由表见控制器注释）。 */
    public String routePrefix() {
        return routePrefix;
    }

    /** 本类的错误码（如 {@code E-ORG-002}）。 */
    public String error(String number) {
        return codePrefix + "-" + number;
    }

    /** 按码解析。 */
    public static ImportKind of(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        for (ImportKind kind : values()) {
            if (kind.code.equalsIgnoreCase(value) || kind.name().equalsIgnoreCase(value)
                    || kind.fileName.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return null;
    }
}
