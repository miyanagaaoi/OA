package com.oa.authz.app;

import com.oa.authz.domain.SysRole;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分级授权策略 —— <b>纯函数</b>（REQ-ADMIN-003 / REQ-ADMIN-006、PRD §5.2）。
 *
 * <p>核心口径：**集团级角色仅系统管理员可维护**；{@code role_scope='company'} 的分公司流程管理员
 * （{@code company_admin}）**不得**：
 * <ol>
 *   <li>创建 / 修改 / 删除集团级（{@code role_scope='group'}）角色；</li>
 *   <li><b>以任何形式再向下分配权限</b>——角色权限勾选 / 数据域 / 类别 / 组织节点一律只有系统管理员可改
 *       （PRD §5.2 原文：「分公司流程管理员…**不可再向下分配权限**」；**不保留**「仅可授予自身子集」的例外，
 *       与前端 {@code canGrantRolePermission} 对 {@code company_admin} 一律 false 的口径一致）；</li>
 *   <li>给超出本公司范围的用户分配角色（跨公司分配）。</li>
 * </ol>
 * 违规一律 403 —— 且**只在服务端判定**，不依赖前端隐藏（前端隐藏只是体验优化）。
 *
 * <p>注意区分「再授权」与「被授予的权限」：{@code company_admin} 维护本公司组织 / 人员 / 流程模板
 * 属于后者，**不受第 2 条影响**（它由数据域与角色码判定，见其他服务）。
 *
 * <p>本类刻意不做任何 IO：调用方把「操作人的角色码 / 有效权限 id / 所属公司」与
 * 「被操作对象的 role_scope / 目标用户公司」喂进来即可，因此可被穷举单测。
 */
public final class AuthorizationPolicy {

    /** 系统管理员角色码。 */
    public static final String ROLE_ADMIN = "admin";

    /** 分公司流程管理员角色码。 */
    public static final String ROLE_COMPANY_ADMIN = "company_admin";

    private AuthorizationPolicy() {
    }

    /**
     * 操作人视图（鉴权输入）。
     *
     * @param userId        操作人 id
     * @param roleCodes     操作人角色码集合
     * @param permissionIds 操作人**有效权限 id** 集合（其全部角色权限的并集）——
     *                      严格口径（分公司管理员不可再授权）下**不参与授权判定**，
     *                      保留为越权审计与「口径放宽为子集授权」时的判据
     * @param companyId     操作人所属公司（{@code sys_user.company_id}）
     */
    public record Operator(Long userId, Set<String> roleCodes, Set<Long> permissionIds, Long companyId) {

        public Operator {
            roleCodes = roleCodes == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
            permissionIds = permissionIds == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(permissionIds));
        }

        public static Operator of(Long userId, Set<String> roleCodes, Set<Long> permissionIds, Long companyId) {
            return new Operator(userId, roleCodes, permissionIds, companyId);
        }

        /** 是否为系统管理员（{@code /auth/me} 的 {@code isSuperAdmin} 与权限缓存判定同源）。 */
        public boolean isSuperAdmin() {
            return roleCodes.contains(ROLE_ADMIN);
        }

        /** 是否为分公司流程管理员。 */
        public boolean isCompanyAdmin() {
            return roleCodes.contains(ROLE_COMPANY_ADMIN);
        }
    }

    // ---------------------------------------------------------------- 角色维护

    /**
     * 维护（创建 / 修改 / 删除 / 配置数据域 / 类别 / 组织节点 / 勾选权限）角色。
     *
     * @param roleScope 目标角色的 {@code role_scope}
     */
    public static void assertCanMaintainRole(Operator operator, String roleScope) {
        requireOperator(operator);
        if (operator.isSuperAdmin()) {
            return;
        }
        if (SysRole.SCOPE_COMPANY.equals(roleScope) && operator.isCompanyAdmin()) {
            return;
        }
        throw forbidden("无权维护 role_scope=" + roleScope + " 的角色：集团级角色仅系统管理员可维护，"
                + "分公司流程管理员不可再向下分配权限");
    }

    /** 读取角色（列表 / 详情 / 已勾选权限 / 数据域 / 类别）：口径同维护，避免通过读接口探测集团级配置。 */
    public static void assertCanReadRole(Operator operator, String roleScope) {
        assertCanMaintainRole(operator, roleScope);
    }

    /** 删除角色：口径同维护；内置 9 码受保护，任何角色都不可删除。 */
    public static void assertCanDeleteRole(Operator operator, String roleCode, String roleScope) {
        assertCanMaintainRole(operator, roleScope);
        if (RoleCatalog.isBuiltIn(roleCode)) {
            throw forbidden("内置角色 " + roleCode + " 受保护，不可删除（可修改名称 / 数据域 / 权限）");
        }
    }

    /**
     * 内置角色受保护字段：{@code code} 与 {@code role_scope} 不可改（名称 / 数据域 / 权限可改）。
     *
     * @param roleCode           当前角色码
     * @param currentRoleScope   当前 {@code role_scope}
     * @param requestedRoleCode  请求中的角色码（可为空 = 不改）
     * @param requestedRoleScope 请求中的 {@code role_scope}（可为空 = 不改）
     */
    public static void assertProtectedRoleImmutable(String roleCode, String currentRoleScope,
                                                    String requestedRoleCode, String requestedRoleScope) {
        if (!RoleCatalog.isBuiltIn(roleCode)) {
            return;
        }
        if (requestedRoleCode != null && !requestedRoleCode.equals(roleCode)) {
            throw forbidden("内置角色 " + roleCode + " 的 code 不可修改（角色码权威源：sys_role.code 列注释）");
        }
        if (requestedRoleScope != null && !requestedRoleScope.equals(currentRoleScope)) {
            throw forbidden("内置角色 " + roleCode + " 的 role_scope 不可修改（" + currentRoleScope + " → "
                    + requestedRoleScope + "）");
        }
    }

    // ---------------------------------------------------------------- 权限授予

    /**
     * 勾选权限（角色授权）：**严格口径 —— 只有系统管理员可以授权**。
     *
     * <p>依据（PRD §5.2 原文）：「分公司流程管理员：由 IT 部门分配，可配置本公司流程模板、
     * 维护本公司组织与人员、查看本公司数据，**不可再向下分配权限**，不可跨公司操作。」
     * PRD 没有「仅可授予自身权限子集」的例外，因此 {@code company_admin} 一律 403；
     * 这与前端 {@code canGrantRolePermission} 对 {@code company_admin} 恒为 false 的口径一致
     * （服务端才是唯一真实来源，前端隐藏只是体验优化）。
     *
     * <p>不受影响的能力：{@code company_admin} 维护本公司组织 / 人员 / 流程模板属于
     * 「被授予的权限」而非「再授权」，仍按数据域与角色码判定。
     *
     * @param roleScope              目标角色的 {@code role_scope}（严格口径下不再据此放行，
     *                               保留以便口径回退与审计）
     * @param requestedPermissionIds 本次要授予的权限 id 集合（同上：保留为审计与口径回退判据）
     */
    public static void assertCanGrantPermissions(Operator operator, String roleScope,
                                                 Collection<Long> requestedPermissionIds) {
        requireOperator(operator);
        if (operator.isSuperAdmin()) {
            return;
        }
        if (operator.isCompanyAdmin()) {
            throw forbidden("分公司流程管理员不可再向下分配权限（REQ-ADMIN-003 / PRD 5.2）："
                    + "角色权限勾选仅系统管理员可执行，请求的权限 id " + asSet(requestedPermissionIds));
        }
        throw forbidden("无权为角色勾选权限：仅系统管理员可授权");
    }

    private static Set<Long> asSet(Collection<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    // ---------------------------------------------------------------- 角色分配

    /**
     * 给用户分配角色：分公司流程管理员只能在本公司范围内分配，且只能分配公司级角色。
     *
     * @param targetCompanyId 被授权人所属公司（{@code sys_user.company_id}）
     */
    public static void assertCanAssignRole(Operator operator, String roleScope, Long targetCompanyId) {
        requireOperator(operator);
        if (operator.isSuperAdmin()) {
            return;
        }
        if (!operator.isCompanyAdmin()) {
            throw forbidden("无权分配角色：仅系统管理员与分公司流程管理员可分配");
        }
        assertCanMaintainRole(operator, roleScope);
        if (operator.companyId() == null) {
            throw forbidden("操作人未归属任何公司，不得分配角色");
        }
        if (!operator.companyId().equals(targetCompanyId)) {
            throw forbidden("不得给超出本公司范围的用户分配角色：目标公司 " + targetCompanyId
                    + " ≠ 操作人公司 " + operator.companyId());
        }
    }

    // ---------------------------------------------------------------- 组织节点边界

    /**
     * 角色的组织节点范围**不得跨出授权人自身的组织边界**（normify
     * {@code oa.authz.rbac.grant.org-node}：「与数据域口径共同决定可见范围；不可跨出授权人自身的组织边界」）。
     *
     * <p>系统管理员不受限；分公司流程管理员的每个节点都必须位于本公司子树内
     * （按 {@code sys_org.path} 前缀判定）。
     *
     * @param companyPathPrefix 操作人所属公司的组织路径（形如 {@code /1/12/}）
     * @param orgPaths          本次要授予的 组织 id → 组织路径
     */
    public static void assertOrgNodesWithinBoundary(Operator operator, String companyPathPrefix,
                                                    java.util.Map<Long, String> orgPaths) {
        requireOperator(operator);
        if (operator.isSuperAdmin()) {
            return;
        }
        if (companyPathPrefix == null || companyPathPrefix.isBlank()) {
            throw forbidden("操作人未归属任何公司，无法确定组织节点授权边界");
        }
        List<Long> beyond = new ArrayList<>();
        if (orgPaths != null) {
            for (java.util.Map.Entry<Long, String> entry : orgPaths.entrySet()) {
                String path = entry.getValue();
                if (path == null || !path.startsWith(companyPathPrefix)) {
                    beyond.add(entry.getKey());
                }
            }
        }
        if (!beyond.isEmpty()) {
            throw forbidden("不得跨出本公司范围授予组织节点：" + beyond + "（本公司路径 " + companyPathPrefix + "）");
        }
    }

    // ---------------------------------------------------------------- 缓存

    /** 权限缓存失效：**仅系统管理员**（全量失效会影响所有在线用户的菜单可见性）。 */
    public static void assertCanInvalidateCache(Operator operator) {
        requireOperator(operator);
        if (!operator.isSuperAdmin()) {
            throw forbidden("仅系统管理员可失效权限缓存");
        }
    }

    // ---------------------------------------------------------------- 内部

    private static void requireOperator(Operator operator) {
        if (operator == null || operator.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }
}
