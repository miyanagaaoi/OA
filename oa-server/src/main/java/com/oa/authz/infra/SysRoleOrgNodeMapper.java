package com.oa.authz.infra;

import com.oa.authz.domain.SysRoleOrgNode;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色 × 组织节点 Mapper（{@code sys_role_org_node}，doc/data-model.md §3.2b、V1 迁移第 28 张表）。
 *
 * <p>列定义逐字对齐 DDL：{@code id / role_id / org_id / created_at / created_by}；
 * 唯一键 {@code uk_role_org_node (role_id, org_id)}（两列都 NOT NULL，**无生成列**，恒等判重即可）；
 * 索引 {@code idx_role_org_node_org (org_id)}；
 * **无 {@code remark}/{@code deleted_at}** → 硬删除语义，「重新授权」= 先清后插。
 * {@code created_at} 由 DDL 的 {@code DEFAULT CURRENT_TIMESTAMP} 负责，**写入时不手工赋值**。
 *
 * <p>配置类表，无 {@code @dataScope} 标记；不继承 {@code BaseMapper}。
 */
@Mapper
public interface SysRoleOrgNodeMapper {

    @Select("SELECT id, role_id AS roleId, org_id AS orgId, created_at AS createdAt,"
            + " created_by AS createdBy FROM sys_role_org_node WHERE role_id = #{roleId} ORDER BY org_id ASC")
    List<SysRoleOrgNode> selectByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT org_id FROM sys_role_org_node WHERE role_id = #{roleId} ORDER BY org_id ASC")
    List<Long> selectOrgIdsByRoleId(@Param("roleId") Long roleId);

    /** 批量写入；{@code created_at} 交给 DDL 默认值，请求内已去重（唯一键不出冲突）。 */
    @Insert({"<script>",
            "INSERT INTO sys_role_org_node (role_id, org_id, created_by) VALUES",
            "<foreach collection='orgIds' item='item' separator=','>",
            "  (#{roleId}, #{item}, #{createdBy})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("roleId") Long roleId, @Param("orgIds") Collection<Long> orgIds,
                    @Param("createdBy") Long createdBy);

    /** 硬删除（本表无 {@code deleted_at}）：重新授权前清空该角色的组织节点。 */
    @Delete("DELETE FROM sys_role_org_node WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
