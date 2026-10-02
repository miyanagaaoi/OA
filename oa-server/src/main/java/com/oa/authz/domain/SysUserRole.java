package com.oa.authz.domain;

import java.time.LocalDateTime;

/**
 * 用户角色关联（{@code sys_user_role}，doc/data-model.md §3.2、import-spec §3.6）。
 *
 * <p>唯一键口径（**勿改**）：{@code uk_sys_user_role (user_id, role_id, scope_org_key)}，
 * 其中 {@code scope_org_key = IFNULL(scope_org_id, 0)} 是 STORED 生成列（**只读不写**）。
 * 原因是 MySQL 唯一键对 NULL 不去重：若直接用可空的 {@code scope_org_id} 参与唯一键，
 * 「同一人 + 同一角色 + 空数据域」可被重复插入（import-spec E-ROLE-003）。
 *
 * <p>{@code scopeOrgId} 为空 = 按角色默认数据域（如 {@code employee} 为本人、
 * {@code subsidiary_gm} 为本公司）。
 */
public class SysUserRole {

    private Long id;
    private Long userId;
    private Long roleId;
    private Long scopeOrgId;
    private LocalDateTime createdAt;
    private Long createdBy;
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

    /**
     * 唯一键归一：{@code scopeOrgId} 为空按 {@code 0} 参与唯一键
     * （与 {@code scope_org_key = IFNULL(scope_org_id, 0)} 完全一致）。
     */
    public static long scopeOrgKey(Long scopeOrgId) {
        return scopeOrgId == null ? 0L : scopeOrgId;
    }
}
