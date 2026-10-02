package com.oa.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.config.JacksonConfig;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.common.security.LoginAttemptGuard;
import com.oa.common.security.PasswordService;
import com.oa.common.security.SessionStore;
import com.oa.identity.api.dto.AuthDtos;
import com.oa.identity.app.AuthService;
import com.oa.identity.infra.SysLoginLogMapper;
import com.oa.identity.infra.SysUserMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * {@code GET /api/v1/auth/me} 的权限字段契约单测。
 *
 * <p>背景（阶段 1.4 硬性要求）：前端管理入口的可见性此前只能用**角色码兜底**，
 * 阶段 1.4 起唯一真实来源是 {@code /auth/me} 的：
 * <ul>
 *   <li>{@code permissions}（字符串数组 = 用户全部角色权限的并集）；</li>
 *   <li>{@code isSuperAdmin}（是否拥有 {@code admin} 角色）。</li>
 * </ul>
 * 本测试同时锁死**JSON 字段名**：{@code boolean isSuperAdmin} 在 Jackson 的 Bean 命名法下
 * 有被改写成 {@code superAdmin} 的风险（记录组件的 {@code isX()} 访问器），
 * 而契约要求下发的键必须逐字是 {@code isSuperAdmin}。
 */
class AuthMePermissionsTest {

    private EffectivePermissionService effectivePermissionService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        effectivePermissionService = mock(EffectivePermissionService.class);
        authService = new AuthService(
                mock(SysUserMapper.class),
                mock(SysLoginLogMapper.class),
                new SessionStore.InMemory(),
                mock(PasswordService.class),
                mock(LoginAttemptGuard.class),
                mock(AuditLogWriter.class),
                new OaProperties(),
                effectivePermissionService);

        CurrentUser principal = CurrentUser.of(7L, "admin01", "系统管理员", "A0007", 1L, 12L,
                Set.of("admin", "employee"), Set.of(), false);
        DataScopeContext.set(DataScopeContext.builder().principal(principal).build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("me() 返回有效权限并集（字典序）与 isSuperAdmin 判定")
    void meReturnsPermissionsUnionAndSuperAdminFlag() {
        when(effectivePermissionService.permissionCodes(7L))
                .thenReturn(Set.of("flow:task:approve", "oa:admin:user", "flow:task:create"));
        when(effectivePermissionService.isSuperAdmin(Set.of("admin", "employee"))).thenReturn(true);

        AuthDtos.MeResponse me = authService.me();

        assertThat(me.permissions())
                .containsExactly("flow:task:approve", "flow:task:create", "oa:admin:user");
        assertThat(me.isSuperAdmin()).isTrue();
        assertThat(me.roleCodes()).containsExactlyInAnyOrder("admin", "employee");
    }

    @Test
    @DisplayName("非系统管理员：isSuperAdmin=false，permissions 为空数组")
    void nonSuperAdminHasEmptyPermissions() {
        when(effectivePermissionService.permissionCodes(7L)).thenReturn(Set.of());
        when(effectivePermissionService.isSuperAdmin(Set.of("admin", "employee"))).thenReturn(false);

        AuthDtos.MeResponse me = authService.me();

        assertThat(me.permissions()).isEmpty();
        assertThat(me.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("JSON 字段名逐字为 permissions / isSuperAdmin（前端据此判定管理入口可见性）")
    void jsonFieldNamesAreExact() throws Exception {
        AuthDtos.MeResponse me = new AuthDtos.MeResponse(7L, "admin01", "系统管理员", "A0007", 1L, 12L,
                Set.of("admin"), Set.of("group_all"), List.of("oa:admin:user"), true, false);

        ObjectMapper mapper = objectMapper();
        String json = mapper.writeValueAsString(me);

        assertThat(json).contains("\"permissions\":[\"oa:admin:user\"]");
        assertThat(json).contains("\"isSuperAdmin\":true");
        // 反向断言：不得出现被改写的键名
        assertThat(json).doesNotContain("\"superAdmin\"");
    }

    @Test
    @DisplayName("effective-permissions 与 /auth/me 的 isSuperAdmin 口径一致（同一判定函数）")
    void effectivePermissionsUsesSameSuperAdminRule() {
        EffectivePermissionService service = new EffectivePermissionService(
                mock(com.oa.authz.infra.SysRolePermissionMapper.class),
                mock(com.oa.authz.infra.SysUserRoleMapper.class),
                mock(com.oa.authz.infra.SysPermissionMapper.class),
                new com.oa.authz.app.PermissionCache.InMemory());

        assertThat(service.isSuperAdmin(Set.of("chairman"))).isFalse();
        assertThat(service.isSuperAdmin(Set.of("chairman", "admin"))).isTrue();
        AuthzDtos.EffectivePermissionView view = service.effectivePermissions(
                CurrentUser.of(9L, "chairman01", "董事长", "A0009", 1L, 1L, Set.of("chairman"), Set.of(), false));
        assertThat(view.isSuperAdmin()).isFalse();
        assertThat(view.permissions()).isEmpty();
    }

    private static ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().oaNumberAsStringCustomizer(new OaProperties()).customize(builder);
        return builder.build();
    }
}
