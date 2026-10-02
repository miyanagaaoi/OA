package com.oa.authz.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.authz.domain.SysRole;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分级授权策略单测（REQ-ADMIN-003 / REQ-ADMIN-006、PRD §5.2）。
 *
 * <p>硬性口径（违规一律 **403**，服务端强制，不能只靠前端隐藏）：
 * <ol>
 *   <li>{@code role_scope='company'} 的分公司流程管理员不得创建/修改/删除集团级角色；</li>
 *   <li><b>不得再向下分配权限</b>——角色权限勾选 / 数据域 / 类别 / 组织节点一律只有系统管理员可改
 *       （PRD §5.2 原文「分公司流程管理员…不可再向下分配权限」；**无「仅可授予自身子集」例外**）；
 *       其维护本公司组织/人员/流程模板的能力不受影响（那是「被授予的权限」）；</li>
 *   <li>不得给超出本公司范围的用户分配角色（跨公司分配）；</li>
 *   <li>内置 9 个角色码受保护：不可删除、不可改 {@code code} 与 {@code role_scope}；</li>
 *   <li>权限缓存失效仅系统管理员可做。</li>
 * </ol>
 *
 * <p>纯逻辑单测：不连 DB、不起 Spring 容器。
 */
class AuthorizationPolicyTest {

    /** 系统管理员：集团级随便改。 */
    private static final AuthorizationPolicy.Operator SUPER_ADMIN =
            AuthorizationPolicy.Operator.of(1L, Set.of("admin"), Set.of(101L, 102L), 1L);

    /** 分公司流程管理员：归属公司 12；**严格口径下没有任何再授权能力**。 */
    private static final AuthorizationPolicy.Operator COMPANY_ADMIN =
            AuthorizationPolicy.Operator.of(2L, Set.of("company_admin"), Set.of(101L), 12L);

    /** 普通员工：什么都不能改。 */
    private static final AuthorizationPolicy.Operator EMPLOYEE =
            AuthorizationPolicy.Operator.of(3L, Set.of("employee"), Set.of(101L), 12L);

    private static void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(ex.getErrorCode().getHttpStatus()).isEqualTo(403);
                });
    }

    // ---------------------------------------------------------------- 集团级角色维护

    @Test
    @DisplayName("分公司流程管理员不得创建/修改/删除集团级（role_scope=group）角色 → 403")
    void companyAdminCannotMaintainGroupRoles() {
        assertForbidden(() -> AuthorizationPolicy.assertCanMaintainRole(COMPANY_ADMIN, SysRole.SCOPE_GROUP));
        assertForbidden(() -> AuthorizationPolicy.assertCanDeleteRole(COMPANY_ADMIN, "custom_role", SysRole.SCOPE_GROUP));
        assertForbidden(() -> AuthorizationPolicy.assertCanReadRole(COMPANY_ADMIN, SysRole.SCOPE_GROUP));
    }

    @Test
    @DisplayName("系统管理员可维护集团级与公司级角色")
    void superAdminCanMaintainAnyRole() {
        assertThatCode(() -> AuthorizationPolicy.assertCanMaintainRole(SUPER_ADMIN, SysRole.SCOPE_GROUP)).doesNotThrowAnyException();
        assertThatCode(() -> AuthorizationPolicy.assertCanMaintainRole(SUPER_ADMIN, SysRole.SCOPE_COMPANY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("分公司流程管理员可在本公司级角色上工作（但授权另受权限子集约束）")
    void companyAdminCanMaintainCompanyRoles() {
        assertThatCode(() -> AuthorizationPolicy.assertCanMaintainRole(COMPANY_ADMIN, SysRole.SCOPE_COMPANY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("普通员工不得维护任何角色 → 403")
    void employeeCannotMaintainAnyRole() {
        assertForbidden(() -> AuthorizationPolicy.assertCanMaintainRole(EMPLOYEE, SysRole.SCOPE_COMPANY));
        assertForbidden(() -> AuthorizationPolicy.assertCanMaintainRole(EMPLOYEE, SysRole.SCOPE_GROUP));
    }

    @Test
    @DisplayName("内置 9 码受保护：任何角色（含系统管理员）都不可删除")
    void builtInRolesCannotBeDeleted() {
        for (String code : RoleCatalog.BUILT_IN_CODES) {
            assertForbidden(() -> AuthorizationPolicy.assertCanDeleteRole(SUPER_ADMIN, code, SysRole.SCOPE_GROUP));
        }
        assertThatCode(() -> AuthorizationPolicy.assertCanDeleteRole(SUPER_ADMIN, "custom_role", SysRole.SCOPE_GROUP))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("内置角色不可改 code 与 role_scope（名称/数据域/权限可改）")
    void builtInRoleCodeAndScopeAreImmutable() {
        assertForbidden(() -> AuthorizationPolicy.assertProtectedRoleImmutable(
                "admin", SysRole.SCOPE_GROUP, "super_admin", null));
        assertForbidden(() -> AuthorizationPolicy.assertProtectedRoleImmutable(
                "admin", SysRole.SCOPE_GROUP, null, SysRole.SCOPE_COMPANY));

        // 名称 / 数据域变更不涉及受保护字段 → 放行
        assertThatCode(() -> AuthorizationPolicy.assertProtectedRoleImmutable(
                "admin", SysRole.SCOPE_GROUP, "admin", SysRole.SCOPE_GROUP)).doesNotThrowAnyException();
        // 非内置角色不受限
        assertThatCode(() -> AuthorizationPolicy.assertProtectedRoleImmutable(
                "custom_role", SysRole.SCOPE_COMPANY, "custom_role_v2", SysRole.SCOPE_GROUP))
                .doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- 再授权（严格口径）

    @Test
    @DisplayName("严格口径：分公司流程管理员**一律**不可再授权 —— 即使授予的是自己拥有的权限也 403")
    void companyAdminCanNeverGrantPermissions() {
        // PRD 5.2：「分公司流程管理员…不可再向下分配权限」，没有「仅可授予自身子集」的例外；
        // 与前端 canGrantRolePermission 对 company_admin 恒为 false 的口径一致。
        assertForbidden(() -> AuthorizationPolicy.assertCanGrantPermissions(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, List.of(101L)));
        assertForbidden(() -> AuthorizationPolicy.assertCanGrantPermissions(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, List.of(101L, 999L)));
        assertForbidden(() -> AuthorizationPolicy.assertCanGrantPermissions(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, List.of()));
        assertForbidden(() -> AuthorizationPolicy.assertCanGrantPermissions(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, null));
        // 403 文案须点名依据（PRD 5.2），便于前端与审计对齐
        assertThatThrownBy(() -> AuthorizationPolicy.assertCanGrantPermissions(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, List.of(101L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可再向下分配权限");
    }

    @Test
    @DisplayName("系统管理员可授予任意权限（含集团级角色）")
    void superAdminCanAlwaysGrantPermissions() {
        assertThatCode(() -> AuthorizationPolicy.assertCanGrantPermissions(
                SUPER_ADMIN, SysRole.SCOPE_GROUP, List.of(1L, 2L, 3L))).doesNotThrowAnyException();
        assertThatCode(() -> AuthorizationPolicy.assertCanGrantPermissions(
                SUPER_ADMIN, SysRole.SCOPE_COMPANY, List.of(1L, 999L))).doesNotThrowAnyException();
        assertThatCode(() -> AuthorizationPolicy.assertCanGrantPermissions(
                SUPER_ADMIN, SysRole.SCOPE_COMPANY, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("「再授权」与「被授予的权限」区分：company_admin 仍可维护本公司级角色本身")
    void companyAdminStillMaintainsCompanyRoles() {
        // 维护本公司组织/人员/流程模板与角色元数据属于「被授予的权限」，不受严格再授权口径影响
        assertThatCode(() -> AuthorizationPolicy.assertCanMaintainRole(COMPANY_ADMIN, SysRole.SCOPE_COMPANY))
                .doesNotThrowAnyException();
        assertThatCode(() -> AuthorizationPolicy.assertCanDeleteRole(COMPANY_ADMIN, "custom_role", SysRole.SCOPE_COMPANY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("分公司流程管理员不得在集团级角色上授权 → 403")
    void companyAdminCannotGrantOnGroupRole() {
        assertForbidden(() -> AuthorizationPolicy.assertCanGrantPermissions(
                COMPANY_ADMIN, SysRole.SCOPE_GROUP, List.of(101L)));
    }

    @Test
    @DisplayName("普通员工不得授权 → 403")
    void employeeCannotGrantPermissions() {
        assertForbidden(() -> AuthorizationPolicy.assertCanGrantPermissions(
                EMPLOYEE, SysRole.SCOPE_COMPANY, List.of(101L)));
    }

    // ---------------------------------------------------------------- 跨公司分配

    @Test
    @DisplayName("分公司流程管理员不得给超出本公司范围的用户分配角色 → 403")
    void companyAdminCannotAssignAcrossCompanies() {
        assertForbidden(() -> AuthorizationPolicy.assertCanAssignRole(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, 99L));
        assertThatCode(() -> AuthorizationPolicy.assertCanAssignRole(
                COMPANY_ADMIN, SysRole.SCOPE_COMPANY, 12L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("分公司流程管理员不得分配集团级角色；无公司归属时一律拒绝")
    void companyAdminCannotAssignGroupRoleOrWithoutCompany() {
        assertForbidden(() -> AuthorizationPolicy.assertCanAssignRole(
                COMPANY_ADMIN, SysRole.SCOPE_GROUP, 12L));
        AuthorizationPolicy.Operator homeless =
                AuthorizationPolicy.Operator.of(4L, Set.of("company_admin"), Set.of(), null);
        assertForbidden(() -> AuthorizationPolicy.assertCanAssignRole(homeless, SysRole.SCOPE_COMPANY, 12L));
        assertThatCode(() -> AuthorizationPolicy.assertCanAssignRole(
                SUPER_ADMIN, SysRole.SCOPE_GROUP, 99L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("普通员工不得分配角色 → 403")
    void employeeCannotAssignRoles() {
        assertForbidden(() -> AuthorizationPolicy.assertCanAssignRole(EMPLOYEE, SysRole.SCOPE_COMPANY, 12L));
    }

    // ---------------------------------------------------------------- 组织节点边界

    @Test
    @DisplayName("组织节点范围不得跨出授权人本公司；系统管理员不受限")
    void orgNodesMustStayWithinOwnCompany() {
        Map<Long, String> paths = new LinkedHashMap<>();
        paths.put(100L, "/1/12/100/");
        paths.put(200L, "/1/99/200/");

        assertThatCode(() -> AuthorizationPolicy.assertOrgNodesWithinBoundary(
                COMPANY_ADMIN, "/1/12/", Map.of(100L, "/1/12/100/"))).doesNotThrowAnyException();
        assertForbidden(() -> AuthorizationPolicy.assertOrgNodesWithinBoundary(COMPANY_ADMIN, "/1/12/", paths));
        assertThatCode(() -> AuthorizationPolicy.assertOrgNodesWithinBoundary(SUPER_ADMIN, null, paths))
                .doesNotThrowAnyException();
        // 操作人没有公司边界时一律拒绝（宁可收紧）
        assertForbidden(() -> AuthorizationPolicy.assertOrgNodesWithinBoundary(
                COMPANY_ADMIN, null, Map.of(100L, "/1/12/100/")));
    }

    // ---------------------------------------------------------------- 缓存

    @Test
    @DisplayName("权限缓存失效仅系统管理员；未认证（无操作人）一律 401")
    void cacheInvalidationIsAdminOnly() {
        assertThatCode(() -> AuthorizationPolicy.assertCanInvalidateCache(SUPER_ADMIN)).doesNotThrowAnyException();
        assertForbidden(() -> AuthorizationPolicy.assertCanInvalidateCache(COMPANY_ADMIN));
        assertForbidden(() -> AuthorizationPolicy.assertCanInvalidateCache(EMPLOYEE));

        assertThatThrownBy(() -> AuthorizationPolicy.assertCanInvalidateCache(null))
                .isInstanceOf(BizException.class)
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Operator 的角色/权限集合不可变，且 isSuperAdmin 只看 admin 码")
    void operatorViewIsImmutableAndDetectsSuperAdmin() {
        assertThat(SUPER_ADMIN.isSuperAdmin()).isTrue();
        assertThat(COMPANY_ADMIN.isSuperAdmin()).isFalse();
        assertThat(COMPANY_ADMIN.isCompanyAdmin()).isTrue();
        assertThatThrownBy(() -> SUPER_ADMIN.roleCodes().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
