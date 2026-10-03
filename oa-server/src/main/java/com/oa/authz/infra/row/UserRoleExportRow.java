package com.oa.authz.infra.row;

/**
 * 角色分配导出投影（{@code sys_user_role} JOIN {@code sys_user} / {@code sys_role} / {@code sys_org}）。
 *
 * <p>列口径对齐 import-spec §9.1 的 {@code user_role.csv}：
 * {@code user_account,role_code,scope_org_path,remark}；
 * {@code org_path} 返回**真实路径**（{@code /1/12/}），导出时由服务层经名称路径索引还原为业务键。
 */
public class UserRoleExportRow {

    private Long id;

    private Long userId;

    private String account;

    private String roleCode;

    private Long scopeOrgId;

    private String scopeOrgPath;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Long getScopeOrgId() {
        return scopeOrgId;
    }

    public void setScopeOrgId(Long scopeOrgId) {
        this.scopeOrgId = scopeOrgId;
    }

    public String getScopeOrgPath() {
        return scopeOrgPath;
    }

    public void setScopeOrgPath(String scopeOrgPath) {
        this.scopeOrgPath = scopeOrgPath;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
