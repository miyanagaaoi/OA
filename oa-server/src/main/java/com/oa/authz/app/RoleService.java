package com.oa.authz.app;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysRole;
import com.oa.authz.infra.SysRoleCategoryMapper;
import com.oa.authz.infra.SysRoleMapper;
import com.oa.authz.infra.SysRoleOrgNodeMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.SysOrg;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色维护（{@code /api/v1/authz/roles/**}、{@code /data-scopes}、{@code /categories}、
 * {@code /org-nodes}）—— REQ-ADMIN-003 / REQ-ADMIN-006。
 *
 * <h2>分级授权（服务端强制，不依赖前端隐藏）</h2>
 * <ul>
 *   <li>集团级（{@code role_scope='group'}）角色的增删改与配置**仅系统管理员**可做；</li>
 *   <li>分公司流程管理员只在 {@code role_scope='company'} 的角色上有权限，
 *       且组织节点范围不得跨出本公司（{@link AuthorizationPolicy}）；</li>
 *   <li>9 个内置角色码受保护：可改名称 / 数据域 / 权限 / 类别，**不可删除、不可改 code 与 role_scope**。</li>
 * </ul>
 *
 * <h2>数据域与类别</h2>
 * <p>{@code data_scope} 只允许五值；{@code group_category} 的角色**必须**同时配置
 * {@code sys_role_category}（五值 business/economy/admin/hr/invest），否则 400——
 * 因为缺少类别时该口径会退化成「全集团无过滤」。
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final SysRoleMapper roleMapper;
    private final SysRoleCategoryMapper roleCategoryMapper;
    private final SysRoleOrgNodeMapper roleOrgNodeMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final EffectivePermissionService effectivePermissionService;
    private final AuthzOperatorProvider operatorProvider;
    private final OrgService orgService;

    public RoleService(SysRoleMapper roleMapper,
                       SysRoleCategoryMapper roleCategoryMapper,
                       SysRoleOrgNodeMapper roleOrgNodeMapper,
                       SysUserRoleMapper userRoleMapper,
                       EffectivePermissionService effectivePermissionService,
                       AuthzOperatorProvider operatorProvider,
                       OrgService orgService) {
        this.roleMapper = roleMapper;
        this.roleCategoryMapper = roleCategoryMapper;
        this.roleOrgNodeMapper = roleOrgNodeMapper;
        this.userRoleMapper = userRoleMapper;
        this.effectivePermissionService = effectivePermissionService;
        this.operatorProvider = operatorProvider;
        this.orgService = orgService;
    }

    // ---------------------------------------------------------------- 列表 / 详情

    /** {@code GET /api/v1/authz/roles}。 */
    public List<AuthzDtos.RoleView> list(String keyword, String roleScope) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        if (!operator.isSuperAdmin() && !operator.isCompanyAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看角色列表");
        }
        String scope = roleScope;
        if (!operator.isSuperAdmin()) {
            if (scope != null && !scope.isBlank()) {
                // 显式请求集团级视图 → 403（不区分「看不到」与「没权限」，避免探测）
                AuthorizationPolicy.assertCanReadRole(operator, scope);
            }
            scope = SysRole.SCOPE_COMPANY;
        }
        List<AuthzDtos.RoleView> views = new ArrayList<>();
        for (SysRole role : roleMapper.selectAll(trim(keyword), scope)) {
            views.add(toView(role));
        }
        return views;
    }

    /** 按 id 取角色（不存在 → 404）。 */
    public SysRole requireRole(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + id);
        }
        return role;
    }

    /** 按角色码取角色（导入第 ⑤ 步的解析入口；不存在 → 404）。 */
    public SysRole requireRoleByCode(String code) {
        SysRole role = code == null ? null : roleMapper.selectByCode(code.trim().toLowerCase(java.util.Locale.ROOT));
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色码不存在（未初始化的角色不随导入创建）：" + code);
        }
        return role;
    }

    // ---------------------------------------------------------------- 增删改

    /** {@code POST /api/v1/authz/roles}。 */
    @Transactional
    public AuthzDtos.RoleView create(AuthzDtos.RoleUpsertRequest request) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        String code = RoleCatalog.requireValidCode(request.code());
        RoleCatalog.requireNotBuiltInForCreate(code);
        String roleScope = RoleCatalog.requireValidRoleScope(request.roleScope());
        String dataScope = DataScopeCatalog.requireValid(request.dataScope());
        AuthorizationPolicy.assertCanMaintainRole(operator, roleScope);

        Set<String> categories = CategoryCatalog.normalizeAll(request.categories());
        DataScopeCatalog.requireCategoriesForGroupCategory(dataScope, categories);

        SysRole duplicate = roleMapper.selectAnyByCode(code);
        if (duplicate != null) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "角色码已存在（uk_sys_role_code）：" + code);
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setRoleScope(roleScope);
        role.setDataScope(dataScope);
        role.setRemark(trim(request.remark()));
        role.setCreatedBy(operator.userId());
        role.setUpdatedBy(operator.userId());
        roleMapper.insertRole(role);

        if (!categories.isEmpty()) {
            roleCategoryMapper.insertBatch(role.getId(), categories);
        }
        log.info("新增角色：id={} code={} roleScope={} dataScope={}", role.getId(), code, roleScope, dataScope);
        return toView(role);
    }

    /** {@code PUT /api/v1/authz/roles/{id}}。 */
    @Transactional
    public AuthzDtos.RoleView update(Long id, AuthzDtos.RoleUpsertRequest request) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanMaintainRole(operator, role.getRoleScope());

        String requestedCode = request.code() == null ? null : RoleCatalog.requireValidCode(request.code());
        String requestedScope = request.roleScope() == null ? null : RoleCatalog.requireValidRoleScope(request.roleScope());
        AuthorizationPolicy.assertProtectedRoleImmutable(role.getCode(), role.getRoleScope(), requestedCode, requestedScope);
        if (requestedScope != null && !requestedScope.equals(role.getRoleScope())) {
            // 换范围等于「把公司级角色提升为集团级」，必须对**新范围**也有维护权
            AuthorizationPolicy.assertCanMaintainRole(operator, requestedScope);
        }

        String dataScope = request.dataScope() == null ? role.getDataScope() : DataScopeCatalog.requireValid(request.dataScope());
        Set<String> categories = request.categories() == null
                ? new LinkedHashSet<>(roleCategoryMapper.selectCategoriesByRoleId(id))
                : CategoryCatalog.normalizeAll(request.categories());
        DataScopeCatalog.requireCategoriesForGroupCategory(dataScope, categories);

        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setName(request.name().trim());
        patch.setRoleScope(requestedScope);
        patch.setDataScope(dataScope);
        patch.setRemark(request.remark() == null ? null : trim(request.remark()));
        patch.setUpdatedBy(operator.userId());
        roleMapper.updateRole(patch);

        if (request.categories() != null) {
            replaceCategories(id, categories);
        }
        if (!role.getDataScope().equals(dataScope)) {
            // 数据域影响可见范围与越权判定，保守失效该角色全部用户的缓存
            effectivePermissionService.invalidateByRole(id);
        }
        log.info("修改角色：id={} code={} dataScope={}", id, role.getCode(), dataScope);
        return toView(requireRole(id));
    }

    /** {@code DELETE /api/v1/authz/roles/{id}}。 */
    @Transactional
    public void delete(Long id) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanDeleteRole(operator, role.getCode(), role.getRoleScope());

        List<Long> holders = userRoleMapper.selectUserIdsByRoleId(id);
        if (!holders.isEmpty()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "该角色仍有 " + holders.size() + " 条用户分配（sys_user_role），请先回收后再删除");
        }
        roleMapper.softDelete(id, operator.userId());
        roleCategoryMapper.deleteByRoleId(id);
        effectivePermissionService.invalidateByRole(id);
        log.info("删除角色：id={} code={}", id, role.getCode());
    }

    // ---------------------------------------------------------------- 数据域

    /** {@code GET /api/v1/authz/data-scopes}。 */
    public List<AuthzDtos.DataScopeView> dataScopes() {
        operatorProvider.current();
        List<AuthzDtos.DataScopeView> views = new ArrayList<>();
        for (DataScopeCatalog.Entry entry : DataScopeCatalog.entries()) {
            views.add(new AuthzDtos.DataScopeView(entry.value(), entry.label(), entry.description()));
        }
        return views;
    }

    /** {@code PUT /api/v1/authz/roles/{id}/data-scope}。 */
    @Transactional
    public AuthzDtos.RoleView updateDataScope(Long id, AuthzDtos.DataScopeUpdateRequest request) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanMaintainRole(operator, role.getRoleScope());

        String dataScope = DataScopeCatalog.requireValid(request.dataScope());
        List<String> categories = roleCategoryMapper.selectCategoriesByRoleId(id);
        DataScopeCatalog.requireCategoriesForGroupCategory(dataScope, categories);

        SysRole patch = new SysRole();
        patch.setId(id);
        patch.setDataScope(dataScope);
        patch.setUpdatedBy(operator.userId());
        roleMapper.updateRole(patch);
        effectivePermissionService.invalidateByRole(id);
        log.info("设置角色数据域：roleId={} dataScope={}", id, dataScope);
        return toView(requireRole(id));
    }

    // ---------------------------------------------------------------- 类别范围

    /** {@code GET /api/v1/authz/categories}：事项类别选项（配置项五值）。 */
    public List<AuthzDtos.CategoryView> categoryOptions() {
        operatorProvider.current();
        Map<String, String> labels = CategoryCatalog.labels();
        List<AuthzDtos.CategoryView> views = new ArrayList<>();
        for (String value : CategoryCatalog.values()) {
            views.add(new AuthzDtos.CategoryView(value, labels.get(value)));
        }
        return views;
    }

    /** {@code GET /api/v1/authz/roles/{id}/categories}。 */
    public AuthzDtos.CategoryBindingView categories(Long id) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanReadRole(operator, role.getRoleScope());
        return new AuthzDtos.CategoryBindingView(id, role.getDataScope(),
                roleCategoryMapper.selectCategoriesByRoleId(id));
    }

    /** {@code PUT /api/v1/authz/roles/{id}/categories}。 */
    @Transactional
    public AuthzDtos.CategoryBindingView setCategories(Long id, List<String> categories) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanMaintainRole(operator, role.getRoleScope());

        Set<String> normalized = CategoryCatalog.normalizeAll(categories);
        DataScopeCatalog.requireCategoriesForGroupCategory(role.getDataScope(), normalized);
        replaceCategories(id, normalized);
        effectivePermissionService.invalidateByRole(id);
        log.info("设置角色类别范围：roleId={} categories={}", id, normalized);
        return new AuthzDtos.CategoryBindingView(id, role.getDataScope(), new ArrayList<>(normalized));
    }

    // ---------------------------------------------------------------- 组织节点

    /**
     * {@code GET /api/v1/authz/roles/{id}/org-nodes}。
     *
     * <p>数据源 {@code sys_role_org_node}（doc/data-model.md §3.2b）：角色级可访问组织节点，
     * 唯一键 {@code (role_id, org_id)}，硬删除语义。
     */
    public AuthzDtos.OrgNodeBindingView orgNodes(Long id) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanReadRole(operator, role.getRoleScope());
        List<Long> orgIds = roleOrgNodeMapper.selectOrgIdsByRoleId(id);
        return new AuthzDtos.OrgNodeBindingView(id, orgIds, describeOrgs(orgIds));
    }

    /** {@code PUT /api/v1/authz/roles/{id}/org-nodes}。 */
    @Transactional
    public AuthzDtos.OrgNodeBindingView setOrgNodes(Long id, List<Long> orgIds) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = requireRole(id);
        AuthorizationPolicy.assertCanMaintainRole(operator, role.getRoleScope());

        Set<Long> requested = new LinkedHashSet<>();
        if (orgIds != null) {
            for (Long orgId : orgIds) {
                if (orgId != null) {
                    requested.add(orgId);
                }
            }
        }
        Map<Long, String> paths = new LinkedHashMap<>();
        List<Long> unknown = new ArrayList<>();
        for (Long orgId : requested) {
            SysOrg org = orgService.findOrg(orgId);
            if (org == null) {
                unknown.add(orgId);
                continue;
            }
            paths.put(orgId, org.getPath());
        }
        if (!unknown.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组织节点不存在：" + unknown);
        }
        AuthorizationPolicy.assertOrgNodesWithinBoundary(operator, companyPathPrefix(), paths);

        roleOrgNodeMapper.deleteByRoleId(id);
        if (!requested.isEmpty()) {
            roleOrgNodeMapper.insertBatch(id, requested, operator.userId());
        }
        effectivePermissionService.invalidateByRole(id);
        // 回显与落库同序（sys_role_org_node 按 org_id 升序读出），避免前端拿到两种顺序
        List<Long> stored = roleOrgNodeMapper.selectOrgIdsByRoleId(id);
        log.info("设置角色组织节点范围：roleId={} orgIds={}", id, stored);
        return new AuthzDtos.OrgNodeBindingView(id, stored, describeOrgs(stored));
    }

    // ---------------------------------------------------------------- 内部

    /** 视图（{@code builtIn} 由角色码目录判定，前端据此禁用「删除」与「code/role_scope」编辑）。 */
    public static AuthzDtos.RoleView toView(SysRole role) {
        return new AuthzDtos.RoleView(role.getId(), role.getCode(), role.getName(), role.getRoleScope(),
                role.getDataScope(), role.getRemark(), RoleCatalog.isBuiltIn(role.getCode()));
    }

    private void replaceCategories(Long roleId, Set<String> categories) {
        roleCategoryMapper.deleteByRoleId(roleId);
        if (!categories.isEmpty()) {
            roleCategoryMapper.insertBatch(roleId, categories);
        }
    }

    private List<AuthzDtos.OrgNodeView> describeOrgs(List<Long> orgIds) {
        List<AuthzDtos.OrgNodeView> views = new ArrayList<>();
        for (Long orgId : orgIds) {
            SysOrg org = orgService.findOrg(orgId);
            views.add(new AuthzDtos.OrgNodeView(orgId,
                    org == null ? null : org.getName(),
                    org == null ? null : org.getPath(),
                    org == null ? null : org.getOrgType()));
        }
        return views;
    }

    /**
     * 操作人所属公司的组织路径前缀（形如 {@code /1/12/}）。
     *
     * <p>推导方式：从操作人主归属组织沿 {@code path} 向上找第一个 {@code org_type='company'} 的节点；
     * 找不到时保守回落到操作人所在节点自身的路径（宁可把边界收紧，也不放宽）。
     */
    String companyPathPrefix() {
        CurrentUser principal = operatorProvider.currentUser();
        if (principal.orgId() == null) {
            return null;
        }
        SysOrg org = orgService.findOrg(principal.orgId());
        if (org == null || org.getPath() == null) {
            return null;
        }
        for (String segment : org.getPath().split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            try {
                SysOrg ancestor = orgService.findOrg(Long.valueOf(segment));
                if (ancestor != null && "company".equals(ancestor.getOrgType())) {
                    return ancestor.getPath();
                }
            } catch (NumberFormatException ex) {
                // path 段异常：忽略该段，继续向上
            }
        }
        return org.getPath();
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
