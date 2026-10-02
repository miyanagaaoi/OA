package com.oa.authz.domain;

import java.time.LocalDateTime;

/**
 * 权限树节点（{@code sys_permission}，doc/data-model.md §3.4）。
 *
 * <p>{@code parent_id} 为空表示根节点；{@code perm_type} = {@code menu|button|api}；
 * {@code code} 全表唯一（{@code uk_sys_permission_code}）。
 *
 * <p>DDL 无 {@code deleted_at} 列：权限节点为**硬删除**语义（删除前必须无子节点，
 * 且会级联清理 {@code sys_role_permission}）。
 */
public class SysPermission {

    /** 菜单。 */
    public static final String TYPE_MENU = "menu";

    /** 按钮。 */
    public static final String TYPE_BUTTON = "button";

    /** 接口。 */
    public static final String TYPE_API = "api";

    private Long id;
    private Long parentId;
    private String permType;
    private String code;
    private String name;
    private String url;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPermType() {
        return permType;
    }

    public void setPermType(String permType) {
        this.permType = permType;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
