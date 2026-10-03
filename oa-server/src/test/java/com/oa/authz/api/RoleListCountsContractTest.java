package com.oa.authz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.app.AuthzOperatorProvider;
import com.oa.authz.app.AuthorizationPolicy;
import com.oa.authz.app.EffectivePermissionService;
import com.oa.authz.app.RoleService;
import com.oa.authz.domain.SysRole;
import com.oa.authz.infra.SysRoleCategoryMapper;
import com.oa.authz.infra.SysRoleMapper;
import com.oa.authz.infra.SysRoleOrgNodeMapper;
import com.oa.authz.infra.SysRolePermissionMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.authz.infra.row.RoleCountRow;
import com.oa.common.config.JacksonConfig;
import com.oa.common.config.OaProperties;
import com.oa.identity.app.OrgService;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * {@code GET /api/v1/authz/roles} 的**权限数 / 用户数契约测试**（收口要求 1）。
 *
 * <p>回归背景：角色列表此前恒显示「权限数 0 / 用户数 0」（后端根本不下发这两个字段，
 * 前端只能回退 0）—— 管理员看到 admin 角色「0 个权限」会直接误判授权范围。
 *
 * <p>本测试把三条硬约束变成机器可验证的：
 * <ol>
 *   <li><b>真值</b>：admin = 94 / 1（种子库实测值），其余角色各自返回自己的计数；</li>
 *   <li><b>禁止 N+1</b>：两个计数各用**一条** {@code COUNT ... GROUP BY role_id} 聚合查询，
 *       与角色条数无关（此处 3 个角色仍只调用 1 次 / 每张表）；</li>
 *   <li><b>形状与类型</b>：{@code permissionCount} / {@code userCount} 是 JSON number，
 *       既有字段名一个都不变（id 仍是字符串）。</li>
 * </ol>
 */
class RoleListCountsContractTest {

    private SysRoleMapper roleMapper;
    private SysRolePermissionMapper rolePermissionMapper;
    private SysUserRoleMapper userRoleMapper;
    private AuthzOperatorProvider operatorProvider;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        operatorProvider = mock(AuthzOperatorProvider.class);
        service = new RoleService(roleMapper, mock(SysRoleCategoryMapper.class), mock(SysRoleOrgNodeMapper.class),
                userRoleMapper, rolePermissionMapper, mock(EffectivePermissionService.class), operatorProvider,
                mock(OrgService.class));
        // 系统管理员：可读全量角色（分公司管理员会被裁剪为 company 范围）
        when(operatorProvider.current()).thenReturn(AuthorizationPolicy.Operator.of(
                1L, Set.of("admin"), Set.of(), 1L));
    }

    private static SysRole role(Long id, String code, String name, String scope, String dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(name);
        role.setRoleScope(scope);
        role.setDataScope(dataScope);
        return role;
    }

    private static RoleCountRow count(Long roleId, int total) {
        RoleCountRow row = new RoleCountRow();
        row.setRoleId(roleId);
        row.setTotal(total);
        return row;
    }

    @Test
    @DisplayName("列表：admin=94/1、company_admin=38/0、employee=24/0（种子库实测值；company_admin 含门户基础权限 flow）")
    void listReturnsRealCounts() {
        when(roleMapper.selectAll(null, null)).thenReturn(List.of(
                role(1L, "admin", "系统管理员", SysRole.SCOPE_GROUP, "group_all"),
                role(2L, "company_admin", "分公司流程管理员", SysRole.SCOPE_COMPANY, "company"),
                role(3L, "employee", "普通员工", SysRole.SCOPE_COMPANY, "self")));
        when(rolePermissionMapper.countByRoleIds(anyCollection()))
                .thenReturn(List.of(count(1L, 94), count(2L, 38), count(3L, 24)));
        when(userRoleMapper.countByRoleIds(anyCollection())).thenReturn(List.of(count(1L, 1)));

        List<AuthzDtos.RoleView> views = service.list(null, null);

        assertThat(views).hasSize(3);
        assertThat(views.get(0).code()).isEqualTo("admin");
        assertThat(views.get(0).permissionCount()).isEqualTo(94);
        assertThat(views.get(0).userCount()).isEqualTo(1);
        assertThat(views.get(1).permissionCount()).isEqualTo(38);
        assertThat(views.get(1).userCount()).isZero();
        assertThat(views.get(2).permissionCount()).isEqualTo(24);
        assertThat(views.get(2).userCount()).isZero();
    }

    @Test
    @DisplayName("禁止 N+1：两张表各一次聚合查询（与角色条数无关），且一次带上全部 roleId")
    void countsAreAggregatedInOneQueryPerTable() {
        when(roleMapper.selectAll(null, null)).thenReturn(List.of(
                role(1L, "admin", "系统管理员", SysRole.SCOPE_GROUP, "group_all"),
                role(2L, "company_admin", "分公司流程管理员", SysRole.SCOPE_COMPANY, "company"),
                role(3L, "employee", "普通员工", SysRole.SCOPE_COMPANY, "self"),
                role(4L, "dept_leader", "部门负责人", SysRole.SCOPE_COMPANY, "dept")));
        when(rolePermissionMapper.countByRoleIds(anyCollection())).thenReturn(List.of(count(1L, 94)));
        when(userRoleMapper.countByRoleIds(anyCollection())).thenReturn(List.of());

        service.list(null, null);

        // 4 个角色 → 每张表仍然只有 1 次查询（若是逐个角色查会是 4 次）
        verify(rolePermissionMapper, times(1)).countByRoleIds(anyCollection());
        verify(userRoleMapper, times(1)).countByRoleIds(anyCollection());
        verify(rolePermissionMapper, times(1)).countByRoleIds(
                org.mockito.ArgumentMatchers.argThat((Collection<Long> ids) ->
                        ids.size() == 4 && ids.containsAll(List.of(1L, 2L, 3L, 4L))));
        // 其余角色的计数按 0 兜底（聚合结果里没有 = 没有行）
        assertThat(service.list(null, null).get(2).permissionCount()).isZero();
    }

    @Test
    @DisplayName("空角色列表：不发聚合查询（IN () 是非法 SQL）")
    void emptyRoleListSkipsAggregates() {
        when(roleMapper.selectAll("无匹配", null)).thenReturn(List.of());

        assertThat(service.list("无匹配", null)).isEmpty();

        verify(rolePermissionMapper, times(0)).countByRoleIds(anyCollection());
        verify(userRoleMapper, times(0)).countByRoleIds(anyCollection());
    }

    @Test
    @DisplayName("改角色的回参同样带真实计数（不是 0）——避免「保存后权限数被清零」的错觉")
    void updateReturnsRealCounts() {
        SysRole role = role(1L, "admin", "系统管理员", SysRole.SCOPE_GROUP, "group_all");
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(rolePermissionMapper.countByRoleIds(anyCollection())).thenReturn(List.of(count(1L, 94)));
        when(userRoleMapper.countByRoleIds(anyCollection())).thenReturn(List.of(count(1L, 1)));

        AuthzDtos.RoleView view = service.update(1L, new AuthzDtos.RoleUpsertRequest(
                "admin", "系统管理员", SysRole.SCOPE_GROUP, "group_all", null, null));

        assertThat(view.permissionCount()).isEqualTo(94);
        assertThat(view.userCount()).isEqualTo(1);
        verify(rolePermissionMapper, times(1)).countByRoleIds(anyCollection());
    }

    @Test
    @DisplayName("新建角色的两个计数为 0（字段存在且非 null），不需要聚合查询")
    void createReturnsZeroCounts() {
        when(roleMapper.selectAnyByCode("custom_operator")).thenReturn(null);
        when(roleMapper.selectAll(null, null)).thenReturn(List.of());
        when(rolePermissionMapper.countByRoleIds(anyCollection())).thenReturn(List.of());
        when(userRoleMapper.countByRoleIds(anyCollection())).thenReturn(List.of());

        AuthzDtos.RoleView view = service.create(new AuthzDtos.RoleUpsertRequest(
                "custom_operator", "自定义操作员", SysRole.SCOPE_COMPANY, "company", null, null));

        assertThat(view.permissionCount()).isZero();
        assertThat(view.userCount()).isZero();
        // 新角色还没有 id → 不发聚合查询
        verify(rolePermissionMapper, times(0)).countByRoleIds(anyCollection());
    }

    @Test
    @DisplayName("JSON：既有字段名一个不变，permissionCount/userCount 是 number（不是字符串）")
    void jsonShapeIsExact() throws Exception {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL);
        new JacksonConfig().oaNumberAsStringCustomizer(new OaProperties()).customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(new AuthzDtos.RoleView(
                1L, "admin", "系统管理员", "group", "group_all", "超级管理员", true, 94, 1));

        assertThat(json).isEqualTo("{\"id\":\"1\",\"code\":\"admin\",\"name\":\"系统管理员\","
                + "\"roleScope\":\"group\",\"dataScope\":\"group_all\",\"remark\":\"超级管理员\","
                + "\"builtIn\":true,\"permissionCount\":94,\"userCount\":1}");
    }

    @Test
    @DisplayName("计数口径守卫：用户数是全局 COUNT（不 JOIN sys_user、不带 @dataScope 标记），权限数按 role_id 分组")
    void countQueriesKeepGlobalScope() throws Exception {
        Method userCount = SysUserRoleMapper.class.getMethod("countByRoleIds", Collection.class);
        String userSql = String.join(" ", userCount.getAnnotation(Select.class).value());
        assertThat(userSql).contains("FROM sys_user_role").contains("GROUP BY role_id");
        assertThat(userSql)
                .as("用户数刻意不 JOIN sys_user：全局口径，且不做任何用户数据读暴露")
                .doesNotContain("JOIN sys_user");
        assertThat(userSql)
                .as("全局计数语句不得带 @dataScope 标记（标记会被拦截器替换成数据域过滤，与语义矛盾）")
                .doesNotContain("@dataScope");

        Method permissionCount = SysRolePermissionMapper.class.getMethod("countByRoleIds", Collection.class);
        String permissionSql = String.join(" ", permissionCount.getAnnotation(Select.class).value());
        assertThat(permissionSql).contains("FROM sys_role_permission").contains("GROUP BY role_id");
        assertThat(permissionSql).doesNotContain("@dataScope");
    }
}
