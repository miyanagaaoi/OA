package com.oa.authz.domain;

import java.time.LocalDateTime;

/**
 * 角色（{@code sys_role}，doc/data-model.md §3.1）。
 *
 * <p>要点：
 * <ul>
 *   <li>{@code code} 的列注释是**角色码唯一权威源**（9 个内置码，见
 *       {@code com.oa.authz.app.RoleCatalog}）；前端不得随意创造保留码；</li>
 *   <li>{@code role_scope} = {@code group}（集团级）/ {@code company}（公司级）；</li>
 *   <li>{@code data_scope} = {@code self|dept|company|group_all|group_category}
 *       （与 DDL 的 CHECK 约束、{@link com.oa.common.scope.DataScopeType} 一致）。</li>
 * </ul>
 */
public class SysRole {

    /** 集团级。 */
    public static final String SCOPE_GROUP = "group";

    /** 公司级。 */
    public static final String SCOPE_COMPANY = "company";

    /** 按归口类别取数（必须同时配置 {@code sys_role_category}）。 */
    public static final String DATA_SCOPE_GROUP_CATEGORY = "group_category";

    private Long id;
    private String code;
    private String name;
    private String roleScope;
    private String dataScope;
    private String remark;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private LocalDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String toString() {
        return "SysRole{id=" + id + ", code='" + code + "', roleScope='" + roleScope
                + "', dataScope='" + dataScope + "'}";
    }
}
