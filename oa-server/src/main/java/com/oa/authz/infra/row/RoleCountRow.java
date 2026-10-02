package com.oa.authz.infra.row;

/**
 * 角色聚合计数行 —— {@code GROUP BY role_id} 的读模型（避免角色列表 N+1）。
 *
 * <p>两条聚合语句共用本行类型：
 * <ul>
 *   <li>{@code SysRolePermissionMapper#countByRoleIds}（{@code sys_role_permission} 行数）；</li>
 *   <li>{@code SysUserRoleMapper#countByRoleIds}（{@code sys_user_role} 分配数）。</li>
 * </ul>
 *
 * <p>口径是**全局计数**（不按数据域裁剪），依据见
 * {@code com.oa.authz.api.dto.AuthzDtos.RoleView} 的类注释。
 */
public class RoleCountRow {

    private Long roleId;
    private Integer total;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
