package com.oa.authz.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysPermission;
import com.oa.authz.infra.SysPermissionMapper;
import com.oa.authz.infra.SysRolePermissionMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.common.security.CurrentUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 有效权限计算与缓存单测。
 *
 * <p>验收点：
 * <ol>
 *   <li>有效权限 = 用户全部角色权限的**并集**（多角色合并，去重）；</li>
 *   <li>{@code isSuperAdmin} 只看 {@code admin} 角色码；</li>
 *   <li>缓存命中时不回源；失效后必须回源（角色权限变更 / 用户角色分配 / 全量失效）；</li>
 *   <li>菜单按权限树装配，父菜单未授予时不会出现子菜单。</li>
 * </ol>
 */
class EffectivePermissionServiceTest {

    private SysRolePermissionMapper rolePermissionMapper;
    private SysUserRoleMapper userRoleMapper;
    private SysPermissionMapper permissionMapper;
    private PermissionCache.InMemory cache;
    private EffectivePermissionService service;

    @BeforeEach
    void setUp() {
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        permissionMapper = mock(SysPermissionMapper.class);
        cache = new PermissionCache.InMemory();
        service = new EffectivePermissionService(rolePermissionMapper, userRoleMapper, permissionMapper, cache);
    }

    private static SysPermission permission(Long id, Long parentId, String code, String type) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setParentId(parentId);
        permission.setCode(code);
        permission.setName(code);
        permission.setPermType(type);
        permission.setSortNo(id.intValue());
        return permission;
    }

    // ---------------------------------------------------------------- 并集

    @Test
    @DisplayName("有效权限 = 全部角色权限的并集，且写缓存后不回源")
    void permissionCodesAreUnionAndCached() {
        when(rolePermissionMapper.selectCodesByUserId(7L))
                .thenReturn(List.of("flow:task:approve", "oa:admin:user", "flow:task:approve"));

        Set<String> codes = service.permissionCodes(7L);

        assertThat(codes).containsExactlyInAnyOrder("flow:task:approve", "oa:admin:user");
        assertThat(service.isCached(7L)).isTrue();

        service.permissionCodes(7L);
        verify(rolePermissionMapper, times(1)).selectCodesByUserId(7L);
    }

    @Test
    @DisplayName("无任何角色 ⇒ 空集合（也要缓存，避免每次请求都回源）")
    void emptyPermissionSetIsCached() {
        when(rolePermissionMapper.selectCodesByUserId(8L)).thenReturn(List.of());

        assertThat(service.permissionCodes(8L)).isEmpty();
        assertThat(service.isCached(8L)).isTrue();
        assertThat(service.permissionCodes(8L)).isEmpty();
        verify(rolePermissionMapper, times(1)).selectCodesByUserId(8L);
    }

    @Test
    @DisplayName("isSuperAdmin 只看 admin 角色码（/auth/me 的唯一真实来源）")
    void isSuperAdminDependsOnAdminRoleOnly() {
        assertThat(service.isSuperAdmin(Set.of("admin"))).isTrue();
        assertThat(service.isSuperAdmin(Set.of("admin", "employee"))).isTrue();
        assertThat(service.isSuperAdmin(Set.of("company_admin", "chairman"))).isFalse();
        assertThat(service.isSuperAdmin(Set.of())).isFalse();
        assertThat(service.isSuperAdmin(null)).isFalse();
    }

    @Test
    @DisplayName("userId 为空 ⇒ 空集合，且不触发任何查询")
    void nullUserHasNoPermissions() {
        assertThat(service.permissionCodes(null)).isEmpty();
        verify(rolePermissionMapper, times(0)).selectCodesByUserId(anyLong());
    }

    // ---------------------------------------------------------------- 菜单装配

    @Test
    @DisplayName("effective-permissions：权限码字典序、菜单按树装配、isSuperAdmin 与角色码一致")
    void effectivePermissionViewAssemblesMenus() {
        when(rolePermissionMapper.selectCodesByUserId(7L))
                .thenReturn(List.of("oa:admin:user", "oa:dashboard", "oa:admin"));
        when(permissionMapper.selectAll()).thenReturn(List.of(
                permission(1L, null, "oa:dashboard", "menu"),
                permission(2L, 1L, "oa:admin", "menu"),
                permission(3L, 2L, "oa:admin:user", "button"),
                permission(4L, 1L, "oa:report", "menu")));

        CurrentUser principal = CurrentUser.of(7L, "admin01", "系统管理员", "A0001", 1L, 1L,
                Set.of("admin", "employee"), Set.of(), false);
        AuthzDtos.EffectivePermissionView view = service.effectivePermissions(principal);

        assertThat(view.permissions()).containsExactly("oa:admin", "oa:admin:user", "oa:dashboard");
        assertThat(view.roleCodes()).containsExactly("admin", "employee");
        assertThat(view.isSuperAdmin()).isTrue();
        assertThat(view.menus()).singleElement()
                .satisfies(menu -> {
                    assertThat(menu.code()).isEqualTo("oa:dashboard");
                    assertThat(menu.children()).singleElement()
                            .satisfies(child -> assertThat(child.code()).isEqualTo("oa:admin"));
                });
        // oa:report 未被授予 → 不出现在菜单里
        assertThat(view.menus().get(0).children()).extracting(AuthzDtos.PermissionNodeView::code)
                .doesNotContain("oa:report");
    }

    // ---------------------------------------------------------------- 失效

    @Test
    @DisplayName("按角色失效：持有该角色的全部用户缓存被清空（下次回源）")
    void invalidateByRoleClearsAllHolders() {
        when(rolePermissionMapper.selectCodesByUserId(7L)).thenReturn(List.of("a"));
        when(rolePermissionMapper.selectCodesByUserId(8L)).thenReturn(List.of("b"));
        when(userRoleMapper.selectUserIdsByRoleId(5L)).thenReturn(List.of(7L, 8L));

        service.permissionCodes(7L);
        service.permissionCodes(8L);
        assertThat(cache.size()).isEqualTo(2);

        int invalidated = service.invalidateByRole(5L);

        assertThat(invalidated).isEqualTo(2);
        assertThat(cache.size()).isZero();
        service.permissionCodes(7L);
        verify(rolePermissionMapper, times(2)).selectCodesByUserId(7L);
    }

    @Test
    @DisplayName("按用户失效与全量失效")
    void invalidateUserAndAll() {
        when(rolePermissionMapper.selectCodesByUserId(7L)).thenReturn(List.of("a"));
        when(rolePermissionMapper.selectCodesByUserId(8L)).thenReturn(List.of("b"));

        service.permissionCodes(7L);
        service.permissionCodes(8L);

        service.invalidateUser(7L);
        assertThat(cache.contains(7L)).isFalse();
        assertThat(cache.contains(8L)).isTrue();

        assertThat(service.invalidateAll()).isEqualTo(1);
        assertThat(cache.size()).isZero();
        service.invalidateUser(null);
        assertThat(service.invalidateUsers(null)).isZero();
    }
}
