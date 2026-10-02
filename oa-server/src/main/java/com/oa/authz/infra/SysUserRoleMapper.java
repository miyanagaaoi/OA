package com.oa.authz.infra;

import com.oa.authz.domain.SysUserRole;
import com.oa.authz.infra.row.RoleCountRow;
import com.oa.authz.infra.row.UserRoleRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户角色 Mapper（{@code sys_user_role}）—— 本领域**唯一受数据域约束**的表。
 *
 * <h2>数据域铁律（doc/tech-design.md §5.3）</h2>
 * <ul>
 *   <li>对外暴露「某用户的角色」的语句一律 JOIN {@code sys_user}（受控表，{@code kind=USER}）
 *       并带标记 {@code /* @dataScope(table=sys_user, alias=u) *}{@code /}；
 *       分公司管理员因此只能看到**本公司用户**的角色（跨公司返回空集），
 *       服务层再用 {@code SysUserMapper.selectUserById} 的可见性结果决定 403 还是空列表；</li>
 *   <li>标记位置必须能容纳一个完整布尔表达式（{@code AND <片段>}）；
 *       若拦截器未生效，SQL 会因悬空 AND 直接报错（fail-closed），而不是静默放行；</li>
 *   <li><b>不继承 {@code BaseMapper}</b>：MP 注入的语句不带标记，会被拦截器 fail-closed 拒绝（40303）。</li>
 * </ul>
 *
 * <p>纯内部校验语句（唯一键查重、按角色反查用户）不参与读暴露，不 JOIN {@code sys_user}，
 * 因此无需标记——但**每一条都只按主键/外键自限**，不存在越权面。
 */
@Mapper
public interface SysUserRoleMapper {

    /**
     * 某用户的全部角色分配（含生效组织范围）。
     *
     * <p>受控表标记见类注释；{@code sys_user_role} 与 {@code sys_role} 自身不是受控表，
     * 但它们与受控表 {@code sys_user} 的关联查询属于「用户数据」的读暴露，必须带标记。
     */
    @Select("SELECT ur.id AS id, ur.user_id AS userId, ur.role_id AS roleId, ur.scope_org_id AS scopeOrgId,"
            + " ur.created_at AS createdAt, ur.created_by AS createdBy, ur.remark AS remark,"
            + " r.code AS roleCode, r.name AS roleName, r.role_scope AS roleScope, r.data_scope AS dataScope,"
            + " u.company_id AS userCompanyId"
            + " FROM sys_user_role ur"
            + " JOIN sys_user u ON u.id = ur.user_id AND u.deleted_at IS NULL"
            + " JOIN sys_role r ON r.id = ur.role_id AND r.deleted_at IS NULL"
            + " WHERE ur.user_id = #{userId}"
            + " AND /* @dataScope(table=sys_user, alias=u) */"
            + " ORDER BY ur.id ASC")
    List<UserRoleRow> selectByUserId(@Param("userId") Long userId);

    /** 按分配 id 取（撤销前校验归属；同样带数据域标记）。 */
    @Select("SELECT ur.id AS id, ur.user_id AS userId, ur.role_id AS roleId, ur.scope_org_id AS scopeOrgId,"
            + " ur.created_at AS createdAt, ur.created_by AS createdBy, ur.remark AS remark,"
            + " r.code AS roleCode, r.name AS roleName, r.role_scope AS roleScope, r.data_scope AS dataScope,"
            + " u.company_id AS userCompanyId"
            + " FROM sys_user_role ur"
            + " JOIN sys_user u ON u.id = ur.user_id AND u.deleted_at IS NULL"
            + " JOIN sys_role r ON r.id = ur.role_id AND r.deleted_at IS NULL"
            + " WHERE ur.id = #{id}"
            + " AND /* @dataScope(table=sys_user, alias=u) */"
            + " LIMIT 1")
    UserRoleRow selectById(@Param("id") Long id);

    /**
     * 唯一键查重（纯内部校验，不参与读暴露）。
     *
     * <p>口径与库约束**完全一致**：{@code uk_sys_user_role (user_id, role_id, scope_org_key)}，
     * {@code scope_org_key = IFNULL(scope_org_id, 0)}；因此 {@code scopeOrgId} 为空时按 {@code 0} 比较。
     */
    @Select("SELECT COUNT(1) FROM sys_user_role"
            + " WHERE user_id = #{userId} AND role_id = #{roleId} AND IFNULL(scope_org_id, 0) = #{scopeOrgKey}")
    int countByUniqueKey(@Param("userId") Long userId, @Param("roleId") Long roleId,
                         @Param("scopeOrgKey") long scopeOrgKey);

    /** 该角色被多少用户持有（删除角色前的闸门，也是「失效相关用户缓存」的反查源）。 */
    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId} ORDER BY user_id ASC")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 多个角色的用户分配数（{@code GROUP BY role_id}，一次取回 —— 角色列表页禁止 N+1）。
     *
     * <p><b>刻意不 JOIN {@code sys_user}</b>，因此既不带 {@code @dataScope} 标记、
     * 也**不按数据域裁剪**：本语句只做「某角色被分配了多少条」的聚合，不做任何用户数据的读暴露
     * （不返回 user_id，不返回用户字段）；口径是全局计数，依据见 {@code AuthzDtos.RoleView} 类注释
     * （该接口仅授权管理员可见，且与「删除角色时按全量分配数阻断」的服务端行为保持一致）。
     * 未出现在结果里的 roleId 表示计数为 0（由服务层补 0）。
     */
    @Select({"<script>",
            "SELECT role_id AS roleId, COUNT(1) AS total FROM sys_user_role",
            "WHERE role_id IN",
            "<foreach collection='roleIds' item='item' open='(' separator=',' close=')'>",
            "  #{item}",
            "</foreach>",
            "GROUP BY role_id",
            "</script>"})
    List<RoleCountRow> countByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    /** 新增分配（自增主键回填）。{@code scope_org_key} 是生成列，**不得写**。 */
    @Insert("INSERT INTO sys_user_role (user_id, role_id, scope_org_id, created_by, remark, created_at)"
            + " VALUES (#{userId}, #{roleId}, #{scopeOrgId}, #{createdBy}, #{remark}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUserRole(SysUserRole assignment);

    /** 撤销授权（硬删除：关联表无 {@code deleted_at}，留痕走 {@code sys_log}）。 */
    @Delete("DELETE FROM sys_user_role WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
