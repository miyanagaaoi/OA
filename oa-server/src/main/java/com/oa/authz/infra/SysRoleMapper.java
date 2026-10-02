package com.oa.authz.infra;

import com.oa.authz.domain.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 角色 Mapper（{@code sys_role}）。
 *
 * <p>**刻意不继承 {@code BaseMapper}**（与 {@code SysUserMapper}/{@code SysOrgMapper} 同一约定）：
 * 全部语句显式声明，避免日后有人顺手用回无标记的注入方法。
 * {@code sys_role} 属**配置类**表（非用户数据），未登记为受控表，因此其 SELECT 无需
 * {@code @dataScope} 标记；**唯一例外**是任何 JOIN {@code sys_user} 的语句（见
 * {@link SysUserRoleMapper}），那类语句必须带标记。
 */
@Mapper
public interface SysRoleMapper {

    String COLUMNS = "id, code, name, role_scope AS roleScope, data_scope AS dataScope, remark,"
            + " created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt,"
            + " updated_by AS updatedBy, deleted_at AS deletedAt";

    /** 角色列表（逻辑删除过滤 + 可选关键字/范围筛选）。 */
    @Select({"<script>",
            "SELECT " + COLUMNS + " FROM sys_role",
            "WHERE deleted_at IS NULL",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (code LIKE CONCAT('%', #{keyword}, '%') OR name LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='roleScope != null and roleScope != \"\"'>",
            "  AND role_scope = #{roleScope}",
            "</if>",
            "ORDER BY id ASC",
            "</script>"})
    List<SysRole> selectAll(@Param("keyword") String keyword, @Param("roleScope") String roleScope);

    /** 按 id 取（逻辑删除过滤；不存在返回 null）。 */
    @Select("SELECT " + COLUMNS + " FROM sys_role WHERE id = #{id} AND deleted_at IS NULL LIMIT 1")
    SysRole selectById(@Param("id") Long id);

    /** 按角色码取（逻辑删除过滤）。 */
    @Select("SELECT " + COLUMNS + " FROM sys_role WHERE code = #{code} AND deleted_at IS NULL LIMIT 1")
    SysRole selectByCode(@Param("code") String code);

    /**
     * 按角色码取**含已逻辑删除**的行：{@code uk_sys_role_code} 不包含 {@code deleted_at}，
     * 因此「已删除但同码」的行同样会撞唯一键，唯一性校验必须看全量。
     */
    @Select("SELECT " + COLUMNS + " FROM sys_role WHERE code = #{code} LIMIT 1")
    SysRole selectAnyByCode(@Param("code") String code);

    /** 新增角色（自增主键回填）。 */
    @Insert("INSERT INTO sys_role (code, name, role_scope, data_scope, remark, created_by, updated_by,"
            + " created_at, updated_at)"
            + " VALUES (#{code}, #{name}, #{roleScope}, #{dataScope}, #{remark}, #{createdBy}, #{updatedBy},"
            + " NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(SysRole role);

    /** 更新名称 / 数据域 / 备注（{@code code} 与 {@code role_scope} 是否可变由服务层按内置码保护裁决）。 */
    @Update({"<script>",
            "UPDATE sys_role",
            "<set>",
            "  <if test='name != null'>name = #{name},</if>",
            "  <if test='roleScope != null'>role_scope = #{roleScope},</if>",
            "  <if test='dataScope != null'>data_scope = #{dataScope},</if>",
            "  <if test='remark != null'>remark = #{remark},</if>",
            "  <if test='updatedBy != null'>updated_by = #{updatedBy},</if>",
            "  updated_at = NOW()",
            "</set>",
            "WHERE id = #{id} AND deleted_at IS NULL",
            "</script>"})
    int updateRole(SysRole role);

    /** 逻辑删除（{@code deleted_at} 置位；{@code uk_sys_role_code} 仍占位，避免同码重建绕过审计）。 */
    @Update("UPDATE sys_role SET deleted_at = NOW(), updated_by = #{updatedBy}, updated_at = NOW()"
            + " WHERE id = #{id} AND deleted_at IS NULL")
    int softDelete(@Param("id") Long id, @Param("updatedBy") Long updatedBy);
}
