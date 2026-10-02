package com.oa.authz.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysRole;
import com.oa.authz.infra.SysRolePermissionMapper;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 角色权限勾选保存（{@code PUT /api/v1/authz/roles/{id}/permissions}）单测。
 *
 * <p>验收点：
 * <ol>
 *   <li>ticks 口径：勾选父 ⇒ 后代全授予 + 补齐祖先，落库集合为展开后的全量；</li>
 *   <li>granted 口径：「父节点未授予时子节点不得单独授予」→ 400，且**不写库**；</li>
 *   <li>未知权限 id → 400，且不写库；</li>
 *   <li>分公司流程管理员越权授予自己没有的权限 → 403；集团级角色 → 403；</li>
 *   <li>保存成功后必须触发该角色全部用户的权限缓存失效。</li>
 * </ol>
 */
class RolePermissionServiceTest {

    private SysRolePermissionMapper rolePermissionMapper;
    private PermissionTreeService permissionTreeService;
    private EffectivePermissionService effectivePermissionService;
    private AuthzOperatorProvider operatorProvider;
    private RoleService roleService;
    private RolePermissionService service;

    private static final SysRole COMPANY_ROLE = role(5L, "custom_operator", SysRole.SCOPE_COMPANY);
    private static final SysRole GROUP_ROLE = role(6L, "group_leader", SysRole.SCOPE_GROUP);

    @BeforeEach
    void setUp() {
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        permissionTreeService = mock(PermissionTreeService.class);
        effectivePermissionService = mock(EffectivePermissionService.class);
        operatorProvider = mock(AuthzOperatorProvider.class);
        roleService = mock(RoleService.class);
        service = new RolePermissionService(rolePermissionMapper, permissionTreeService,
                effectivePermissionService, operatorProvider, roleService);
        when(permissionTreeService.loadTree()).thenReturn(tree());
    }

    private static SysRole role(Long id, String code, String scope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setRoleScope(scope);
        role.setDataScope("company");
        return role;
    }

    /** 1 dashboard ├2 form ├4 create └5 delete └3 admin └6 user */
    private static PermissionTreePolicy.Tree tree() {
        return PermissionTreePolicy.Tree.of(List.of(
                new PermissionTreePolicy.Node(1L, null, "oa:dashboard", "dashboard", "menu", null, 1),
                new PermissionTreePolicy.Node(2L, 1L, "oa:form", "form", "menu", null, 2),
                new PermissionTreePolicy.Node(3L, 1L, "oa:admin", "admin", "menu", null, 3),
                new PermissionTreePolicy.Node(4L, 2L, "oa:form:create", "create", "button", null, 4),
                new PermissionTreePolicy.Node(5L, 2L, "oa:form:delete", "delete", "button", null, 5),
                new PermissionTreePolicy.Node(6L, 3L, "oa:admin:user", "user", "button", null, 6)));
    }

    private static AuthorizationPolicy.Operator operator(String... roles) {
        return AuthorizationPolicy.Operator.of(1L, Set.of(roles), Set.of(1L, 2L, 3L, 4L, 5L, 6L), 12L);
    }

    private static void assertStatus(BizException ex, ErrorCode expected) {
        assertThat(ex.getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("ticks 口径：勾选叶子 4 ⇒ 落库 {1,2,4}（后代闭包 + 祖先补齐），并失效该角色用户缓存")
    void ticksModeExpandsAndInvalidatesCache() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(4L), null));

        verify(rolePermissionMapper).deleteByRoleId(5L);
        verify(rolePermissionMapper).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 3 && ids.containsAll(List.of(1L, 2L, 4L))), eq(1L));
        verify(effectivePermissionService).invalidateByRole(5L);
    }

    @Test
    @DisplayName("ticks 口径：勾选父节点 2 ⇒ 落库其整棵子树 {1,2,4,5}")
    void tickingParentGrantsWholeSubtree() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(2L), "ticks"));

        verify(rolePermissionMapper).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 4 && ids.containsAll(List.of(1L, 2L, 4L, 5L))), eq(1L));
    }

    @Test
    @DisplayName("空数组 = 取消全部权限（不插行，但仍然失效缓存）")
    void emptyTicksClearsAllPermissions() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(), null));

        verify(rolePermissionMapper).deleteByRoleId(5L);
        verify(rolePermissionMapper, never()).insertBatch(any(), anyCollection(), any());
        verify(effectivePermissionService).invalidateByRole(5L);
    }

    // ---------------------------------------------------------------- 缺省 auto 口径（前端实际口径）

    @Test
    @DisplayName("auto（缺省）：祖先闭合的入参原样落库 —— 半选父下的兄弟子树**不被连锁授予**（防静默提权）")
    void autoModeKeepsClosedPayloadExactly() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        // 前端 PUT 的真实形状：permissionIds = checked ∪ halfChecked = {1,2,4}，且**不传 mode**
        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(1L, 2L, 4L), null));

        verify(rolePermissionMapper).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 3 && ids.containsAll(List.of(1L, 2L, 4L))), eq(1L));
        // 反向断言：3/5/6 不得被「展开」进来
        verify(rolePermissionMapper, never()).insertBatch(any(),
                argThat(ids -> ids != null && (ids.contains(3L) || ids.contains(5L) || ids.contains(6L))), any());
        verify(effectivePermissionService).invalidateByRole(5L);
    }

    @Test
    @DisplayName("auto（缺省）：不闭合的入参（只提交叶子）按「勾选父 ⇒ 后代全授予」展开")
    void autoModeExpandsLeafOnlyPayload() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(4L), null));

        verify(rolePermissionMapper).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 3 && ids.containsAll(List.of(1L, 2L, 4L))), eq(1L));
    }

    @Test
    @DisplayName("auto（缺省）：读回 → 原样保存是幂等的（连续两次保存落库集合不变）")
    void autoModeRoundTripIsIdempotent() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        // 第一次：勾叶子 4 ⇒ G = {1,2,4}
        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(4L), null));
        // GET 会返回 permissionIds = G；第二次原样回传（不传 mode）⇒ 仍必须是 {1,2,4}
        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(1L, 2L, 4L), null));

        verify(rolePermissionMapper, times(2)).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 3 && ids.containsAll(List.of(1L, 2L, 4L))), eq(1L));
    }

    @Test
    @DisplayName("mode=ticks 是**显式**选择：对已闭合集合会再次展开（故缺省不能是 ticks）")
    void explicitTicksModeExpandsEvenClosedPayload() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(1L, 2L, 4L), "ticks"));

        // 1 被当成「勾选节点」⇒ 整棵子树被授予（这就是缺省不能走 ticks 的原因）
        verify(rolePermissionMapper).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 6), eq(1L));
    }

    @Test
    @DisplayName("mode 归一：缺省/空串 → auto；auto|ticks|granted 合法；其它 → 400")
    void modeNormalization() {
        assertThat(RolePermissionService.normalizeMode(null)).isEqualTo(RolePermissionService.MODE_AUTO);
        assertThat(RolePermissionService.normalizeMode("")).isEqualTo(RolePermissionService.MODE_AUTO);
        assertThat(RolePermissionService.normalizeMode("  AUTO ")).isEqualTo(RolePermissionService.MODE_AUTO);
        assertThat(RolePermissionService.normalizeMode("TICKS")).isEqualTo(RolePermissionService.MODE_TICKS);
        assertThat(RolePermissionService.normalizeMode("granted")).isEqualTo(RolePermissionService.MODE_GRANTED);
        assertThatThrownBy(() -> RolePermissionService.normalizeMode("bogus"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("mode 仅允许 auto|ticks|granted");
    }

    @Test
    @DisplayName("granted 口径：父节点未授予时子节点不得单独授予 → 400 且不写库")
    void grantedModeRejectsChildWithoutParent() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        assertThatThrownBy(() -> service.save(5L,
                new AuthzDtos.RolePermissionSaveRequest(List.of(4L), "granted")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("父节点未授予时子节点不得单独授予")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertStatus(ex, ErrorCode.PARAM_INVALID));

        verify(rolePermissionMapper, never()).deleteByRoleId(any());
        verify(rolePermissionMapper, never()).insertBatch(any(), anyCollection(), any());
        verify(effectivePermissionService, never()).invalidateByRole(any());
    }

    @Test
    @DisplayName("granted 口径：合法的祖先闭合集合可正常保存")
    void grantedModeAcceptsAncestorClosedSet() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(1L, 2L, 4L), "granted"));

        verify(rolePermissionMapper).insertBatch(eq(5L),
                argThat(ids -> ids != null && ids.size() == 3 && ids.containsAll(List.of(1L, 2L, 4L))), eq(1L));
    }

    @Test
    @DisplayName("未知权限 id / 非法 mode → 400 且不写库")
    void unknownPermissionOrBadModeIsRejected() {
        when(operatorProvider.current()).thenReturn(operator("admin"));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        assertThatThrownBy(() -> service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(999L), null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("权限节点不存在");
        assertThatThrownBy(() -> service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(4L), "bogus")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("mode 仅允许");

        verify(rolePermissionMapper, never()).deleteByRoleId(any());
    }

    @Test
    @DisplayName("分公司流程管理员不得授予自己没有的权限 → 403 且不写库")
    void companyAdminCannotGrantBeyondOwnPermissions() {
        when(operatorProvider.current()).thenReturn(
                AuthorizationPolicy.Operator.of(2L, Set.of("company_admin"), Set.of(999L), 12L));
        when(roleService.requireRole(5L)).thenReturn(COMPANY_ROLE);

        assertThatThrownBy(() -> service.save(5L, new AuthzDtos.RolePermissionSaveRequest(List.of(4L), null)))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class, ex -> assertStatus(ex, ErrorCode.FORBIDDEN));

        verify(rolePermissionMapper, never()).deleteByRoleId(any());
        verify(effectivePermissionService, never()).invalidateByRole(any());
    }

    @Test
    @DisplayName("分公司流程管理员不得维护集团级角色的权限勾选 → 403")
    void companyAdminCannotTouchGroupRole() {
        when(operatorProvider.current()).thenReturn(
                AuthorizationPolicy.Operator.of(2L, Set.of("company_admin"), Set.of(1L), 12L));
        when(roleService.requireRole(6L)).thenReturn(GROUP_ROLE);

        assertThatThrownBy(() -> service.save(6L, new AuthzDtos.RolePermissionSaveRequest(List.of(4L), null)))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class, ex -> assertStatus(ex, ErrorCode.FORBIDDEN));
        verify(rolePermissionMapper, never()).deleteByRoleId(any());
    }
}
