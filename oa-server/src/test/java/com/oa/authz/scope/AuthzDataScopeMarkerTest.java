package com.oa.authz.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.authz.infra.AuthzOrgLookupMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * authz 领域的**数据域结构守卫**（纯逻辑，不连 DB / 不起 Spring 容器）。
 *
 * <p>回归背景（doc/tech-design.md §5.3、评审阻断项）：受控表的 SELECT 必须带
 * {@code /* @dataScope(table=..., alias=...) *}{@code /} 标记，否则
 * {@code DataScopeInterceptor} 会 fail-closed 拒绝（40303）；受控表 Mapper 也不得继承
 * {@code BaseMapper}（其注入语句不带标记，等于给数据域开后门）。
 *
 * <p>本测试把 authz 侧的三条硬约束变成机器可验证的：
 * <ol>
 *   <li>{@code sys_user_role} 的**读暴露**语句（按用户 / 按分配 id 取角色）必须 JOIN
 *       {@code sys_user} 并带标记 —— 分公司管理员因此只能看到本公司用户的角色；</li>
 *   <li>组织路径解析语句（{@code sys_org}，受控表）必须带标记；</li>
 *   <li>authz 侧 Mapper 一律不继承 {@code BaseMapper}，且每条语句**至多一个**标记
 *       （拦截器只替换第一个标记）。</li>
 * </ol>
 */
class AuthzDataScopeMarkerTest {

    private static final List<Class<?>> AUTHZ_MAPPERS = List.of(
            SysUserRoleMapper.class,
            AuthzOrgLookupMapper.class,
            com.oa.authz.infra.SysRoleMapper.class,
            com.oa.authz.infra.SysPermissionMapper.class,
            com.oa.authz.infra.SysRolePermissionMapper.class,
            com.oa.authz.infra.SysRoleCategoryMapper.class,
            com.oa.authz.infra.SysRoleOrgNodeMapper.class,
            com.oa.authz.infra.AuthzLogMapper.class);

    private static String sqlOf(Class<?> mapper, String method, Class<?>... parameterTypes) throws Exception {
        Method target = mapper.getMethod(method, parameterTypes);
        Select select = target.getAnnotation(Select.class);
        assertThat(select).as("%s.%s 必须是 @Select 显式语句（不依赖 BaseMapper 注入）", mapper.getSimpleName(), method)
                .isNotNull();
        return String.join(" ", select.value());
    }

    private static void assertSingleMarker(String sql, Class<?> mapper, String method) {
        int markers = sql.split("@dataScope", -1).length - 1;
        assertThat(markers)
                .as("%s.%s 的 @dataScope 标记数量必须恰好为 1（拦截器只替换第一个标记）", mapper.getSimpleName(), method)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sys_user_role 的读暴露语句带 @dataScope(table=sys_user) 标记（分公司管理员只能看本公司用户）")
    void userRoleReadStatementsAreDataScoped() throws Exception {
        String byUser = sqlOf(SysUserRoleMapper.class, "selectByUserId", Long.class);
        assertThat(byUser).contains("JOIN sys_user u");
        assertThat(byUser).contains("/* @dataScope(table=sys_user, alias=u) */");
        assertSingleMarker(byUser, SysUserRoleMapper.class, "selectByUserId");

        String byId = sqlOf(SysUserRoleMapper.class, "selectById", Long.class);
        assertThat(byId).contains("JOIN sys_user u");
        assertThat(byId).contains("/* @dataScope(table=sys_user, alias=u) */");
        assertSingleMarker(byId, SysUserRoleMapper.class, "selectById");
    }

    @Test
    @DisplayName("组织路径解析语句带 @dataScope(table=sys_org) 标记（sys_org 是受控表）")
    void orgLookupIsDataScoped() throws Exception {
        String sql = sqlOf(AuthzOrgLookupMapper.class, "selectIdByPath", String.class);
        assertThat(sql).contains("/* @dataScope(table=sys_org, alias=o) */");
        assertThat(sql).contains("FROM sys_org o");
        assertSingleMarker(sql, AuthzOrgLookupMapper.class, "selectIdByPath");
    }

    @Test
    @DisplayName("authz 侧 Mapper 一律不继承 BaseMapper（注入语句不带标记，会被 fail-closed 拒绝）")
    void authzMappersDoNotExtendBaseMapper() {
        for (Class<?> mapper : AUTHZ_MAPPERS) {
            assertThat(mapper.isInterface()).as("%s 应是 Mapper 接口", mapper.getName()).isTrue();
            assertThat(BaseMapper.class.isAssignableFrom(mapper))
                    .as("%s 不得继承 BaseMapper", mapper.getName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("唯一键判重语句的 scope_org_key 口径 = IFNULL(scope_org_id, 0)")
    void duplicateCheckUsesGeneratedKeySemantics() throws Exception {
        String sql = sqlOf(SysUserRoleMapper.class, "countByUniqueKey", Long.class, Long.class, long.class);
        assertThat(sql).contains("IFNULL(scope_org_id, 0)");
        assertThat(sql).contains("user_id = #{userId}");
        assertThat(sql).contains("role_id = #{roleId}");
    }
}
