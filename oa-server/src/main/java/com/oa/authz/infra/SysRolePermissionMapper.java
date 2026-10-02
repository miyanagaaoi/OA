package com.oa.authz.infra;

import com.oa.authz.infra.row.RoleCountRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色权限 Mapper（{@code sys_role_permission}）。
 *
 * <p>存储口径见 {@code PermissionTreePolicy}：本表落的是**展开为全量**的祖先闭包
 * （被勾选节点 + 其全部后代 + 到达它们的祖先），因此「角色的有效权限集合」= 本表行集合本身，
 * 无需读时再遍历树。
 *
 * <p>{@code sys_role_permission} 属**配置类**表，未登记为受控表 → 无 {@code @dataScope} 标记；
 * 不继承 {@code BaseMapper}。
 */
@Mapper
public interface SysRolePermissionMapper {

    /** 角色已授予的权限 id。 */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission_id ASC")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 角色已授予的权限码（返回给前端做勾选回显）。 */
    @Select("SELECT p.code FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id"
            + " WHERE rp.role_id = #{roleId} ORDER BY p.sort_no ASC, p.id ASC")
    List<String> selectCodesByRoleId(@Param("roleId") Long roleId);

    /**
     * 用户有效权限码（其全部角色权限的并集）—— 权限缓存的回源查询。
     *
     * <p>本语句只 JOIN {@code sys_user_role} 与 {@code sys_permission}（都不是受控表），
     * 按 {@code user_id} 自限，不存在越权面。
     */
    @Select("SELECT DISTINCT p.code FROM sys_user_role ur"
            + " JOIN sys_role r ON r.id = ur.role_id AND r.deleted_at IS NULL"
            + " JOIN sys_role_permission rp ON rp.role_id = ur.role_id"
            + " JOIN sys_permission p ON p.id = rp.permission_id"
            + " WHERE ur.user_id = #{userId}"
            + " ORDER BY p.code ASC")
    List<String> selectCodesByUserId(@Param("userId") Long userId);

    /** 用户有效权限 id（越权再授权判定用；不走缓存，管理操作频次低）。 */
    @Select("SELECT DISTINCT rp.permission_id FROM sys_user_role ur"
            + " JOIN sys_role r ON r.id = ur.role_id AND r.deleted_at IS NULL"
            + " JOIN sys_role_permission rp ON rp.role_id = ur.role_id"
            + " WHERE ur.user_id = #{userId}")
    List<Long> selectPermissionIdsByUserId(@Param("userId") Long userId);

    /** 该权限被多少角色引用（删除权限节点前的级联影响面）。 */
    @Select("SELECT DISTINCT role_id FROM sys_role_permission WHERE permission_id = #{permissionId}")
    List<Long> selectRoleIdsByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * 多个角色的权限数（{@code GROUP BY role_id}，一次取回 —— 角色列表页禁止 N+1）。
     *
     * <p>口径：{@code sys_role_permission} 的**全表行数**，不按数据域裁剪
     * （该接口仅授权管理员可见，理由见 {@code AuthzDtos.RoleView} 类注释）。
     * {@code sys_role_permission} 是配置类表、未登记为受控表，故无需 {@code @dataScope} 标记；
     * 未出现在结果里的 roleId 表示计数为 0（由服务层补 0，而不是左连接拼全表）。
     */
    @Select({"<script>",
            "SELECT role_id AS roleId, COUNT(1) AS total FROM sys_role_permission",
            "WHERE role_id IN",
            "<foreach collection='roleIds' item='item' open='(' separator=',' close=')'>",
            "  #{item}",
            "</foreach>",
            "GROUP BY role_id",
            "</script>"})
    List<RoleCountRow> countByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    /** 批量写入（一次勾选保存）。 */
    @Insert({"<script>",
            "INSERT INTO sys_role_permission (role_id, permission_id, created_by, created_at) VALUES",
            "<foreach collection='permissionIds' item='item' separator=','>",
            "  (#{roleId}, #{item}, #{createdBy}, NOW())",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("roleId") Long roleId,
                    @Param("permissionIds") Collection<Long> permissionIds,
                    @Param("createdBy") Long createdBy);

    /** 清空角色全部权限（保存前先删后插，保持「整棵勾选是一次整体提交」的语义）。 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 级联清理某权限节点的全部角色引用（权限节点硬删除时调用）。 */
    @Delete("DELETE FROM sys_role_permission WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") Long permissionId);
}
