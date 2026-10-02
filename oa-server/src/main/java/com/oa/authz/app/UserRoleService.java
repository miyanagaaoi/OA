package com.oa.authz.app;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户角色分配写入口（{@code sys_user_role}）—— **import-spec 第 ⑤ 步的实现落点**
 * （{@code user_role.csv} → {@code sys_user_role}，§2.3 / §3.6）。
 *
 * <h2>接口契约</h2>
 * <ul>
 *   <li>{@code GET    /api/v1/authz/users/{id}/roles}</li>
 *   <li>{@code POST   /api/v1/authz/users/{id}/roles}（{@code {roleCode|roleId, scopeOrgPath|scopeOrgId}}）</li>
 *   <li>{@code DELETE /api/v1/authz/users/{id}/roles/{assignmentId}}</li>
 * </ul>
 *
 * <h2>唯一键口径（E-ROLE-003）</h2>
 * <p>重复分配判定与库约束**完全一致**：{@code uk_sys_user_role (user_id, role_id, scope_org_key)}，
 * {@code scope_org_key = IFNULL(scope_org_id, 0)}。因此「同一人 + 同一角色 + 空范围」按
 * {@code scope_org_id = 0} 归一后判重，命中即 409 并在文案中指向该唯一键
 * （MySQL 唯一键对 NULL 不去重，这条归一正是为了堵住「空范围可重复分配」的漏洞）。
 *
 * <h2>数据域与分级授权</h2>
 * <ul>
 *   <li>所有读暴露语句都带 {@code @dataScope} 标记（{@link SysUserRoleMapper}），
 *       分公司管理员只能看到本公司用户的角色；</li>
 *   <li>目标用户先经 {@code sys_user} 的数据域可见性校验：不可见 → 403
 *       （跨公司分配被拒绝，而不是静默返回空列表）；</li>
 *   <li>分配/撤销后**必须失效**该用户的权限缓存。</li>
 * </ul>
 */
@Service
public class UserRoleService {

    private static final Logger log = LoggerFactory.getLogger(UserRoleService.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final RoleService roleService;
    private final EffectivePermissionService effectivePermissionService;
    private final AuthzOperatorProvider operatorProvider;
    private final OrgService orgService;
    private final AuthzOrgLookupMapper orgLookupMapper;

    public UserRoleService(SysUserRoleMapper userRoleMapper,
                           SysUserMapper userMapper,
                           RoleService roleService,
                           EffectivePermissionService effectivePermissionService,
                           AuthzOperatorProvider operatorProvider,
                           OrgService orgService,
                           AuthzOrgLookupMapper orgLookupMapper) {
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.roleService = roleService;
        this.effectivePermissionService = effectivePermissionService;
        this.operatorProvider = operatorProvider;
        this.orgService = orgService;
        this.orgLookupMapper = orgLookupMapper;
    }

    /** {@code GET /api/v1/authz/users/{id}/roles}。 */
    public List<AuthzDtos.UserRoleView> list(Long userId) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        requireVisibleUser(operator, userId);
        List<AuthzDtos.UserRoleView> views = new ArrayList<>();
        for (UserRoleRow row : userRoleMapper.selectByUserId(userId)) {
            views.add(toView(row));
        }
        return views;
    }

    /**
     * {@code POST /api/v1/authz/users/{id}/roles}：授予角色（可限定组织范围）。
     *
     * @throws BizException 400（角色/组织范围无法解析）、403（越权）、404（角色码未初始化）、
     *                      409（重复分配）
     */
    @Transactional
    public AuthzDtos.UserRoleView assign(Long userId, AuthzDtos.UserRoleAssignRequest request) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysUser target = requireVisibleUser(operator, userId);

        SysRole role = resolveRole(request);
        Long scopeOrgId = resolveScopeOrgId(request);
        AuthorizationPolicy.assertCanAssignRole(operator, role.getRoleScope(), target.getCompanyId());

        long scopeOrgKey = SysUserRole.scopeOrgKey(scopeOrgId);
        if (userRoleMapper.countByUniqueKey(userId, role.getId(), scopeOrgKey) > 0) {
            throw duplicate(role, scopeOrgId);
        }

        SysUserRole assignment = new SysUserRole();
        assignment.setUserId(userId);
        assignment.setRoleId(role.getId());
        assignment.setScopeOrgId(scopeOrgId);
        assignment.setCreatedBy(operator.userId());
        assignment.setRemark(trim(request.remark()));
        try {
            userRoleMapper.insertUserRole(assignment);
        } catch (DuplicateKeyException ex) {
            // 并发下的兜底：库约束 uk_sys_user_role 同样拦住
            throw duplicate(role, scopeOrgId);
        }

        effectivePermissionService.invalidateUser(userId);
        log.info("授予角色：userId={} roleCode={} scopeOrgId={} assignmentId={}",
                userId, role.getCode(), scopeOrgId, assignment.getId());
        return toView(requireAssignment(assignment.getId(), userId));
    }

    /** {@code DELETE /api/v1/authz/users/{id}/roles/{assignmentId}}：回收授权。 */
    @Transactional
    public void revoke(Long userId, Long assignmentId) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        requireVisibleUser(operator, userId);
        UserRoleRow row = requireAssignment(assignmentId, userId);
        AuthorizationPolicy.assertCanAssignRole(operator, row.getRoleScope(), row.getUserCompanyId());

        userRoleMapper.deleteById(assignmentId);
        effectivePermissionService.invalidateUser(userId);
        log.info("回收角色授权：userId={} assignmentId={} roleCode={}", userId, assignmentId, row.getRoleCode());
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 目标用户必须落在操作人的数据域内。
     *
     * <p>不可见时的状态码口径：
     * <ul>
     *   <li>系统管理员看得到全部用户 → 查不到即 404（真的是用户不存在）；</li>
     *   <li>其余角色查不到**可能是跨公司** → 403（AC-17「直接 URL 访问返回无权限，而非空页」）。
     *       这样既不泄露「该 id 是否存在」，也满足 REQ-ADMIN-003 的跨公司拒绝口径。</li>
     * </ul>
     */
    private SysUser requireVisibleUser(AuthorizationPolicy.Operator operator, Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        SysUser user = userMapper.selectUserById(userId);
        if (user == null) {
            if (operator.isSuperAdmin()) {
                throw new BizException(ErrorCode.NOT_FOUND, "用户不存在：" + userId);
            }
            throw new BizException(ErrorCode.DATA_SCOPE_DENIED,
                    "目标用户不在您的数据域内（跨公司分配被拒绝）：" + userId);
        }
        return user;
    }

    private UserRoleRow requireAssignment(Long assignmentId, Long userId) {
        if (assignmentId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色分配记录不存在");
        }
        UserRoleRow row = userRoleMapper.selectById(assignmentId);
        if (row == null || !userId.equals(row.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "角色分配记录不存在或不属于该用户：assignmentId=" + assignmentId + ", userId=" + userId);
        }
        return row;
    }

    /** {@code roleCode} 与 {@code roleId} 二选一；同时给出时必须自洽。 */
    private SysRole resolveRole(AuthzDtos.UserRoleAssignRequest request) {
        if (request.roleId() == null && (request.roleCode() == null || request.roleCode().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "roleId 与 roleCode 至少提供一个");
        }
        if (request.roleId() != null) {
            SysRole role = roleService.requireRole(request.roleId());
            if (request.roleCode() != null && !request.roleCode().isBlank()
                    && !request.roleCode().trim().equalsIgnoreCase(role.getCode())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "roleId 与 roleCode 不一致：" + request.roleId() + " / " + request.roleCode());
            }
            return role;
        }
        // 角色码必须命中已初始化角色集（import-spec §2.3 白名单；导入不创建角色）
        return roleService.requireRoleByCode(request.roleCode());
    }

    /** {@code scopeOrgId} 优先；否则用 {@code scopeOrgPath} 解析；都为空 = 按角色默认数据域。 */
    private Long resolveScopeOrgId(AuthzDtos.UserRoleAssignRequest request) {
        if (request.scopeOrgId() != null) {
            if (orgService.findOrg(request.scopeOrgId()) == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "scopeOrgId 对应的组织不存在：" + request.scopeOrgId());
            }
            return request.scopeOrgId();
        }
        String path = normalizeOrgPath(request.scopeOrgPath());
        if (path == null) {
            return null;
        }
        Long orgId = orgLookupMapper.selectIdByPath(path);
        if (orgId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "scopeOrgPath 无法解析为组织节点（import-spec E-ROLE-004）：" + request.scopeOrgPath());
        }
        return orgId;
    }

    /** 组织路径统一形如 {@code /1/12/135/}（与 {@code sys_org.path} 一致）。 */
    static String normalizeOrgPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    private BizException duplicate(SysRole role, Long scopeOrgId) {
        return new BizException(ErrorCode.DUPLICATE,
                "该用户已分配此角色（唯一键 uk_sys_user_role (user_id, role_id, scope_org_key)，"
                        + "scope_org_key = IFNULL(scope_org_id, 0)：scope_org_id 为空按 0 归一，"
                        + "同一人 + 同一角色 + 空范围视为同一条）：roleCode=" + role.getCode()
                        + ", scopeOrgId=" + scopeOrgId);
    }

    private AuthzDtos.UserRoleView toView(UserRoleRow row) {
        String scopeOrgPath = null;
        if (row.getScopeOrgId() != null) {
            SysOrg org = orgService.findOrg(row.getScopeOrgId());
            scopeOrgPath = org == null ? null : org.getPath();
        }
        return new AuthzDtos.UserRoleView(
                row.getId(),
                row.getUserId(),
                row.getRoleId(),
                row.getRoleCode(),
                row.getRoleName(),
                row.getRoleScope(),
                row.getDataScope(),
                row.getScopeOrgId(),
                scopeOrgPath,
                row.getRemark(),
                row.getCreatedAt() == null ? null : TIMESTAMP.format(row.getCreatedAt()));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
