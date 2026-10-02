package com.oa.authz.infra.row;

import java.time.LocalDateTime;

/**
 * 用户角色关联行（{@code sys_user_role} 关联 {@code sys_role} 的读模型）。
 *
 * <p>供 {@code GET /api/v1/authz/users/{id}/roles} 与撤销授权前的归属校验使用。
 */
public class UserRoleRow {

    private Long id;
    private Long userId;
    private Long roleId;
    private Long scopeOrgId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private String remark;
    private String roleCode;
    private String roleName;
    private String roleScope;
    private String dataScope;
    private Long userCompanyId;

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

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getScopeOrgId() {
        return scopeOrgId;
    }

    public void setScopeOrgId(Long scopeOrgId) {
        this.scopeOrgId = scopeOrgId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleScope() {
        return roleScope;
    }

    public void setRoleScope(String roleScope) {
        this.roleScope = roleScope;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public Long getUserCompanyId() {
        return userCompanyId;
    }

    public void setUserCompanyId(Long userCompanyId) {
        this.userCompanyId = userCompanyId;
    }
}
