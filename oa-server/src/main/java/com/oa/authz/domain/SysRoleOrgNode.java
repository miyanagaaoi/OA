package com.oa.authz.domain;

import java.time.LocalDateTime;

/**
 * 角色 × 组织节点（{@code sys_role_org_node}，doc/data-model.md §3.2b）。
 *
 * <p>用途（PRD §5.2）：IT 部门为角色勾选**可访问的组织节点**，与权限树的菜单/按钮维度正交。
 * 与相邻概念的分工：
 * <ul>
 *   <li>{@code sys_role_category} 限定「业务线 / 事项类别」，本表限定「组织节点」；</li>
 *   <li>本表是**角色级**可访问范围（授权面）；{@code sys_user_role.scope_org_id} 是
 *       **某个人**持有该角色时的生效范围（分配面）；二者取交集。</li>
 * </ul>
 *
 * <p>列定义逐字对齐 DDL：{@code id / role_id / org_id / created_at / created_by}；
 * 唯一键 {@code uk_role_org_node (role_id, org_id)}；**无 {@code deleted_at}** → 硬删除，
 * 「重新授权」= 先按 {@code role_id} 全删再批量 INSERT。
 */
public class SysRoleOrgNode {

    private Long id;
    private Long roleId;
    private Long orgId;
    private LocalDateTime createdAt;
    private Long createdBy;

    public SysRoleOrgNode() {
    }

    public SysRoleOrgNode(Long roleId, Long orgId) {
        this.roleId = roleId;
        this.orgId = orgId;
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

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
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
}
