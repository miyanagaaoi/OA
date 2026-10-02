package com.oa.authz.infra;

import com.oa.authz.domain.SysRoleCategory;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色 × 类别 Mapper（{@code sys_role_category}，doc/data-model.md §3.3）。
 *
 * <p>仅 {@code data_scope = group_category} 的角色需要配置；类别为配置项五值
 * （{@code business/economy/admin/hr/invest}），由 {@code CategoryCatalog} 校验。
 * 配置类表，无 {@code @dataScope} 标记；不继承 {@code BaseMapper}。
 */
@Mapper
public interface SysRoleCategoryMapper {

    @Select("SELECT id, role_id AS roleId, category, created_at AS createdAt FROM sys_role_category"
            + " WHERE role_id = #{roleId} ORDER BY category ASC")
    List<SysRoleCategory> selectByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT category FROM sys_role_category WHERE role_id = #{roleId} ORDER BY category ASC")
    List<String> selectCategoriesByRoleId(@Param("roleId") Long roleId);

    @Insert({"<script>",
            "INSERT INTO sys_role_category (role_id, category, created_at) VALUES",
            "<foreach collection='categories' item='item' separator=','>",
            "  (#{roleId}, #{item}, NOW())",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("roleId") Long roleId, @Param("categories") Collection<String> categories);

    @Delete("DELETE FROM sys_role_category WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
