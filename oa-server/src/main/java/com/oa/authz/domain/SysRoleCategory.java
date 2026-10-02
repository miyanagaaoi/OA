package com.oa.authz.domain;

import java.time.LocalDateTime;

/**
 * 角色 × 事项类别（{@code sys_role_category}，doc/data-model.md §3.3）。
 *
 * <p>仅 {@code data_scope = group_category} 的角色需要配置；
 * 类别为**配置项五值**：{@code business/economy/admin/hr/invest}；
 * 旧码 {@code operate} 已作废并迁移为 {@code business}。
 */
public class SysRoleCategory {

    private Long id;
    private Long roleId;
    private String category;
    private LocalDateTime createdAt;

    public SysRoleCategory() {
    }

    public SysRoleCategory(Long roleId, String category) {
        this.roleId = roleId;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
