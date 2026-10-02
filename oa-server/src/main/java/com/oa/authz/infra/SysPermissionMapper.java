package com.oa.authz.infra;

import com.oa.authz.domain.SysPermission;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 权限树 Mapper（{@code sys_permission}）。
 *
 * <p>{@code sys_permission} 属**配置类**表（全局权限树，非用户数据），未登记为受控表，
 * 因此本 Mapper 的 SELECT 无需 {@code @dataScope} 标记；同样**不继承 {@code BaseMapper}**。
 *
 * <p>DDL 无 {@code deleted_at} 列：节点为**硬删除**，删除前必须无子节点，
 * 且由服务层级联清理 {@code sys_role_permission} 并失效相关用户缓存。
 */
@Mapper
public interface SysPermissionMapper {

    String COLUMNS = "id, parent_id AS parentId, perm_type AS permType, code, name, url,"
            + " sort_no AS sortNo, created_at AS createdAt, updated_at AS updatedAt";

    /** 全量节点（构建权限树用；按 {@code sort_no, id} 升序，保证树序稳定）。 */
    @Select("SELECT " + COLUMNS + " FROM sys_permission ORDER BY sort_no ASC, id ASC")
    List<SysPermission> selectAll();

    @Select("SELECT " + COLUMNS + " FROM sys_permission WHERE id = #{id} LIMIT 1")
    SysPermission selectById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM sys_permission WHERE code = #{code} LIMIT 1")
    SysPermission selectByCode(@Param("code") String code);

    /** 按 id 批量取（回显已勾选节点的权限码，避免 N+1）。 */
    @Select({"<script>",
            "SELECT " + COLUMNS + " FROM sys_permission WHERE id IN",
            "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach>",
            "</script>"})
    List<SysPermission> selectByIds(@Param("ids") Collection<Long> ids);

    /** 直接子节点数量（删除前的闸门）。 */
    @Select("SELECT COUNT(1) FROM sys_permission WHERE parent_id = #{parentId}")
    int countChildren(@Param("parentId") Long parentId);

    @Insert("INSERT INTO sys_permission (parent_id, perm_type, code, name, url, sort_no, created_at, updated_at)"
            + " VALUES (#{parentId}, #{permType}, #{code}, #{name}, #{url}, #{sortNo}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPermission(SysPermission permission);

    @Update("UPDATE sys_permission SET parent_id = #{parentId}, perm_type = #{permType}, code = #{code},"
            + " name = #{name}, url = #{url}, sort_no = #{sortNo}, updated_at = NOW() WHERE id = #{id}")
    int updatePermission(SysPermission permission);

    @Delete("DELETE FROM sys_permission WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
