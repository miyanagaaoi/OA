package com.oa.authz.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 组织路径 → id 解析（authz 侧的最小只读端口）。
 *
 * <p>用途：{@code user_role.csv} 的 {@code scope_org_path} 与
 * {@code POST /api/v1/authz/users/{id}/roles} 的 {@code scopeOrgPath} 必须能解析为
 * {@code sys_org.id}（import-spec §3.6、E-ROLE-004：「非空时必须能在 org.csv 中解析」）。
 *
 * <p><b>受控表铁律</b>：{@code sys_org} 已由 {@code @DataScopeTable(table="sys_org", kind=NONE)}
 * 登记，SELECT 必须带 {@code @dataScope} 标记（kind=NONE → 片段为 {@code 1=1}，不改语义）；
 * 缺标记会被 {@code DataScopeInterceptor} fail-closed 拒绝（40303）。
 * 本 Mapper **不继承 {@code BaseMapper}**。
 */
@Mapper
public interface AuthzOrgLookupMapper {

    /** 按组织路径取 id（形如 {@code /1/12/135/}）；不存在返回 {@code null}。 */
    @Select("SELECT o.id FROM sys_org o"
            + " WHERE 1 = 1"
            + " AND /* @dataScope(table=sys_org, alias=o) */"
            + " AND o.deleted_at IS NULL"
            + " AND o.path = #{path}"
            + " LIMIT 1")
    Long selectIdByPath(@Param("path") String path);
}
