package com.oa.authz.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysRole;
import com.oa.authz.domain.SysUserRole;
import com.oa.authz.infra.AuthzOrgLookupMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.authz.infra.row.UserRoleRow;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysUserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 用户角色分配写入口（{@code sys_user_role}）单测 —— import-spec 第 ⑤ 步的实现落点。
 *
 * <p>验收点：
 * <ol>
 *   <li>重复分配 → **409**，且文案指向唯一键 {@code uk_sys_user_role(user_id, role_id, scope_org_key)}；</li>
 *   <li>{@code scope_org_id} 为空按 **0** 归一后判重（MySQL 唯一键对 NULL 不去重）；</li>
 *   <li>跨公司分配 → 403（分公司流程管理员只能给本公司用户分配）；</li>
 *   <li>{@code roleCode} 与 {@code roleId} 二选一；角色码未初始化 → 404；</li>
 *   <li>分配/撤销后必须失效该用户权限缓存；支持一人多角色。</li>
 * </ol>
 */
class UserRoleServiceTest {

    private SysUserRoleMapper userRoleMapper;
    private SysUserMapper userMapper;
    private RoleService roleService;
    private EffectivePermissionService effectivePermissionService;
    private AuthzOperatorProvider operatorProvider;
    private OrgService orgService;
    private AuthzOrgLookupMapper orgLookupMapper;
    private UserRoleService service;

    private static final SysRole EMPLOYEE_ROLE = role(3L, "employee", SysRole.SCOPE_COMPANY);

    @BeforeEach
    void setUp() {
        userRoleMapper = mock(SysUserRoleMapper.class);
        userMapper = mock(SysUserMapper.class);
        roleService = mock(RoleService.class);
        effectivePermissionService = mock(EffectivePermissionService.class);
        operatorProvider = mock(AuthzOperatorProvider.class);
        orgService = mock(OrgService.class);
        orgLookupMapper = mock(AuthzOrgLookupMapper.class);
        service = new UserRoleService(userRoleMapper, userMapper, roleService, effectivePermissionService,
                operatorProvider, orgService, orgLookupMapper);
    }

    private static SysRole role(Long id, String code, String scope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        role.setRoleScope(scope);
        role.setDataScope("self");
        return role;
    }

    private static SysUser user(Long id, Long companyId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setAccount("u" + id);
        user.setName("用户" + id);
        user.setCompanyId(companyId);
        user.setStatus("active");
        return user;
    }

    private static AuthorizationPolicy.Operator companyAdmin(long companyId) {
        return AuthorizationPolicy.Operator.of(2L, Set.of("company_admin"), Set.of(), companyId);
    }

    // ---------------------------------------------------------------- 409 重复分配

    @Test
    @DisplayName("重复分配 → 409，文案指向唯一键 uk_sys_user_role(user_id, role_id, scope_org_key)")
    void duplicateAssignmentReturns409() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRole(3L)).thenReturn(EMPLOYEE_ROLE);
        when(userRoleMapper.countByUniqueKey(7L, 3L, 0L)).thenReturn(1);

        assertThatThrownBy(() -> service.assign(7L, new AuthzDtos.UserRoleAssignRequest(3L, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("uk_sys_user_role")
                .hasMessageContaining("scope_org_key")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE));

        verify(userRoleMapper, never()).insertUserRole(any());
        verify(effectivePermissionService, never()).invalidateUser(any());
    }

    @Test
    @DisplayName("scope_org_id 为空按 0 归一参与唯一键（NULL 不去重的漏洞由此堵住）")
    void nullScopeOrgNormalizesToZero() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRole(3L)).thenReturn(EMPLOYEE_ROLE);
        when(userRoleMapper.countByUniqueKey(eq(7L), eq(3L), eq(0L))).thenReturn(0);
        when(userRoleMapper.insertUserRole(any())).thenAnswer(invocation -> {
            SysUserRole row = invocation.getArgument(0);
            row.setId(88L);
            return 1;
        });
        when(userRoleMapper.selectById(88L)).thenReturn(assignmentRow(88L, 7L, 12L));

        service.assign(7L, new AuthzDtos.UserRoleAssignRequest(3L, null, null, null, null));

        // 关键断言：判重用的 scope_org_key 是 0（= IFNULL(NULL, 0)），不是 NULL
        verify(userRoleMapper).countByUniqueKey(7L, 3L, 0L);
        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insertUserRole(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getRoleId()).isEqualTo(3L);
        assertThat(captor.getValue().getScopeOrgId()).isNull();
        assertThat(SysUserRole.scopeOrgKey(captor.getValue().getScopeOrgId())).isZero();
        verify(effectivePermissionService).invalidateUser(7L);
    }

    @Test
    @DisplayName("同一人可多角色：不同 roleId 各自成功（一个 shop 员工可同时是 dept_leader）")
    void oneUserCanHoldMultipleRoles() {
        SysRole leaderRole = role(4L, "dept_leader", SysRole.SCOPE_COMPANY);
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRole(4L)).thenReturn(leaderRole);
        when(userRoleMapper.insertUserRole(any())).thenAnswer(invocation -> {
            SysUserRole row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        });
        when(userRoleMapper.selectById(99L)).thenReturn(assignmentRow(99L, 7L, 12L));

        service.assign(7L, new AuthzDtos.UserRoleAssignRequest(4L, "dept_leader", null, null, "兼任"));

        verify(userRoleMapper).countByUniqueKey(7L, 4L, 0L);
        verify(effectivePermissionService).invalidateUser(7L);
    }

    // ---------------------------------------------------------------- 跨公司 403

    @Test
    @DisplayName("跨公司分配 → 403（目标用户不在本公司数据域内）")
    void crossCompanyAssignmentIsForbidden() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.assign(7L, new AuthzDtos.UserRoleAssignRequest(3L, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DATA_SCOPE_DENIED);
                    assertThat(ex.getErrorCode().getHttpStatus()).isEqualTo(403);
                });
        verify(userRoleMapper, never()).insertUserRole(any());
    }

    @Test
    @DisplayName("目标用户可见但属于别的公司 → 403（授权策略层再拦一次）")
    void visibleButOtherCompanyIsForbidden() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 99L));
        when(roleService.requireRole(3L)).thenReturn(EMPLOYEE_ROLE);

        assertThatThrownBy(() -> service.assign(7L, new AuthzDtos.UserRoleAssignRequest(3L, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不得给超出本公司范围的用户分配角色")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(userRoleMapper, never()).insertUserRole(any());
    }

    @Test
    @DisplayName("分公司流程管理员不得分配集团级角色 → 403")
    void companyAdminCannotAssignGroupRole() {
        SysRole groupRole = role(9L, "group_leader", SysRole.SCOPE_GROUP);
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRole(9L)).thenReturn(groupRole);

        assertThatThrownBy(() -> service.assign(7L, new AuthzDtos.UserRoleAssignRequest(9L, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ---------------------------------------------------------------- 角色解析 / 组织范围

    @Test
    @DisplayName("roleCode 未初始化 → 404（导入不创建角色，import-spec §2.3）")
    void unknownRoleCodeIsNotFound() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRoleByCode("group_exec"))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "角色码不存在（未初始化的角色不随导入创建）：group_exec"));

        assertThatThrownBy(() -> service.assign(7L,
                new AuthzDtos.UserRoleAssignRequest(null, "group_exec", null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色码不存在")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("roleId 与 roleCode 不一致 → 400；roleCode/roleId 都缺 → 400")
    void roleIdAndRoleCodeMustAgree() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRole(3L)).thenReturn(EMPLOYEE_ROLE);

        assertThatThrownBy(() -> service.assign(7L,
                new AuthzDtos.UserRoleAssignRequest(3L, "chairman", null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不一致");
        assertThatThrownBy(() -> service.assign(7L,
                new AuthzDtos.UserRoleAssignRequest(null, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少提供一个");
    }

    @Test
    @DisplayName("scopeOrgPath 解析为 scope_org_id 并归一化路径；未知路径 → 400")
    void scopeOrgPathIsResolvedAndValidated() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(roleService.requireRole(3L)).thenReturn(EMPLOYEE_ROLE);
        when(orgLookupMapper.selectIdByPath("/1/12/135/")).thenReturn(135L);
        when(userRoleMapper.insertUserRole(any())).thenAnswer(invocation -> {
            SysUserRole row = invocation.getArgument(0);
            row.setId(77L);
            return 1;
        });
        when(userRoleMapper.selectById(77L)).thenReturn(assignmentRow(77L, 7L, 12L, 135L));
        SysOrg org = new SysOrg();
        org.setId(135L);
        org.setPath("/1/12/135/");
        when(orgService.findOrg(135L)).thenReturn(org);

        AuthzDtos.UserRoleView view = service.assign(7L,
                new AuthzDtos.UserRoleAssignRequest(3L, null, null, "1/12/135", null));

        assertThat(view.scopeOrgId()).isEqualTo(135L);
        assertThat(view.scopeOrgPath()).isEqualTo("/1/12/135/");
        verify(orgLookupMapper).selectIdByPath("/1/12/135/");

        when(orgLookupMapper.selectIdByPath("/9/9/9/")).thenReturn(null);
        assertThatThrownBy(() -> service.assign(7L,
                new AuthzDtos.UserRoleAssignRequest(3L, null, null, "/9/9/9/", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("E-ROLE-004");
    }

    @Test
    @DisplayName("组织路径归一化：缺首尾斜杠都能对齐 sys_org.path")
    void orgPathNormalization() {
        assertThat(UserRoleService.normalizeOrgPath("1/12/135")).isEqualTo("/1/12/135/");
        assertThat(UserRoleService.normalizeOrgPath("/1/12/135/")).isEqualTo("/1/12/135/");
        assertThat(UserRoleService.normalizeOrgPath("  /1/12/  ")).isEqualTo("/1/12/");
        assertThat(UserRoleService.normalizeOrgPath("")).isNull();
        assertThat(UserRoleService.normalizeOrgPath(null)).isNull();
    }

    // ---------------------------------------------------------------- 撤销

    @Test
    @DisplayName("撤销：分配记录不属于该用户 → 404；正常撤销 → 删行 + 失效缓存")
    void revokeValidatesOwnershipAndInvalidatesCache() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(userRoleMapper.selectById(88L)).thenReturn(assignmentRow(88L, 8L, 12L));

        assertThatThrownBy(() -> service.revoke(7L, 88L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于该用户");
        verify(userRoleMapper, never()).deleteById(anyLong());

        when(userRoleMapper.selectById(88L)).thenReturn(assignmentRow(88L, 7L, 12L));
        service.revoke(7L, 88L);

        verify(userRoleMapper).deleteById(88L);
        verify(effectivePermissionService).invalidateUser(7L);
    }

    @Test
    @DisplayName("列表：目标用户不可见 → 403；可见 → 返回其全部分配")
    void listRespectsDataScope() {
        when(operatorProvider.current()).thenReturn(companyAdmin(12L));
        when(userMapper.selectUserById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.list(7L))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DATA_SCOPE_DENIED));

        when(userMapper.selectUserById(7L)).thenReturn(user(7L, 12L));
        when(userRoleMapper.selectByUserId(7L)).thenReturn(List.of(assignmentRow(88L, 7L, 12L)));

        List<AuthzDtos.UserRoleView> views = service.list(7L);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.assignmentId()).isEqualTo(88L);
            assertThat(view.roleCode()).isEqualTo("employee");
        });
    }

    private static UserRoleRow assignmentRow(Long assignmentId, Long userId, Long companyId) {
        return assignmentRow(assignmentId, userId, companyId, null);
    }

    private static UserRoleRow assignmentRow(Long assignmentId, Long userId, Long companyId, Long scopeOrgId) {
        UserRoleRow row = new UserRoleRow();
        row.setId(assignmentId);
        row.setUserId(userId);
        row.setRoleId(3L);
        row.setRoleCode("employee");
        row.setRoleName("普通员工");
        row.setRoleScope(SysRole.SCOPE_COMPANY);
        row.setDataScope("self");
        row.setUserCompanyId(companyId);
        row.setScopeOrgId(scopeOrgId);
        row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return row;
    }
}
