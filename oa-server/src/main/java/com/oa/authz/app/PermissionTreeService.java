package com.oa.authz.app;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysPermission;
import com.oa.authz.infra.SysPermissionMapper;
import com.oa.authz.infra.SysRolePermissionMapper;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限树维护（{@code /api/v1/authz/permissions/**}，REQ-ADMIN-003）。
 *
 * <p>行为约定：
 * <ul>
 *   <li>读取返回**嵌套树**（按 {@code sort_no, id} 稳定排序），前端逐级勾选；</li>
 *   <li>权限码全局唯一（{@code uk_sys_permission_code}），重复 409；</li>
 *   <li>父子关系不得成环（自身 / 后代作为父节点一律 400）；</li>
 *   <li>删除：有子节点 → 409；否则**级联清理** {@code sys_role_permission} 并失效
 *       受影响用户（被删节点不再属于任何角色的有效权限），全过程留痕（Controller 的 {@code @Audited}）。</li>
 * </ul>
 */
@Service
public class PermissionTreeService {

    private static final Logger log = LoggerFactory.getLogger(PermissionTreeService.class);

    /** 权限码格式（如 {@code flow:task:approve}）：小写开头，允许小写字母/数字/冒号/下划线/点/短横。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_:.\\-]{1,63}$");

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final EffectivePermissionService effectivePermissionService;
    private final AuthzOperatorProvider operatorProvider;

    public PermissionTreeService(SysPermissionMapper permissionMapper,
                                 SysRolePermissionMapper rolePermissionMapper,
                                 EffectivePermissionService effectivePermissionService,
                                 AuthzOperatorProvider operatorProvider) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.effectivePermissionService = effectivePermissionService;
        this.operatorProvider = operatorProvider;
    }

    /**
     * 权限树维护的准入（**仅系统管理员**，REQ-ADMIN-003）。
     *
     * <p>为什么必须有这一层：权限树是「谁能做什么」的定义本身——若任意已登录用户可读可写，
     * 就等于任何人都能给自己加权限（越权零放行的底线）。阶段 1 DoD 的逐接口矩阵测试
     * 明确把「直连接口换角色调用」列为必测项，本闸门是它的落点。
     *
     * @throws BizException 403 {@link ErrorCode#FORBIDDEN}（非系统管理员）
     */
    private void assertCanMaintainPermissionTree(String action) {
        if (!operatorProvider.current().isSuperAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "权限树" + action + "仅系统管理员可用（REQ-ADMIN-003）");
        }
    }

    /** 当前权限树（纯策略对象，供勾选规范化/还原复用）。**内部调用不做准入**（调用方自行鉴权）。 */
    public PermissionTreePolicy.Tree loadTree() {
        List<PermissionTreePolicy.Node> nodes = new ArrayList<>();
        for (SysPermission permission : permissionMapper.selectAll()) {
            nodes.add(EffectivePermissionService.toTreeNode(permission));
        }
        return PermissionTreePolicy.Tree.of(nodes);
    }

    /** {@code GET /api/v1/authz/permissions/tree}：完整权限树（仅系统管理员）。 */
    public List<AuthzDtos.PermissionNodeView> tree() {
        assertCanMaintainPermissionTree("查看");
        List<SysPermission> all = permissionMapper.selectAll();
        PermissionTreePolicy.Tree policyTree = PermissionTreePolicy.Tree.of(toNodes(all));
        Map<Long, SysPermission> byId = new LinkedHashMap<>();
        for (SysPermission permission : all) {
            byId.put(permission.getId(), permission);
        }
        List<AuthzDtos.PermissionNodeView> forest = new ArrayList<>();
        for (Long rootId : policyTree.roots()) {
            forest.add(assemble(policyTree, byId, rootId));
        }
        return forest;
    }

    /** {@code POST /api/v1/authz/permissions}。 */
    @Transactional
    public AuthzDtos.PermissionView create(AuthzDtos.PermissionUpsertRequest request) {
        assertCanMaintainPermissionTree("维护");
        String code = requireValidCode(request.code());
        String permType = requireValidPermType(request.permType());
        if (request.parentId() != null) {
            requirePermission(request.parentId());
        }
        SysPermission existing = permissionMapper.selectByCode(code);
        if (existing != null) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "权限码已存在（uk_sys_permission_code）：" + code);
        }
        SysPermission permission = new SysPermission();
        permission.setParentId(request.parentId());
        permission.setPermType(permType);
        permission.setCode(code);
        permission.setName(request.name().trim());
        permission.setUrl(trim(request.url()));
        permission.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        permissionMapper.insertPermission(permission);
        log.info("新增权限节点：id={} code={} parentId={}", permission.getId(), code, request.parentId());
        return toView(permission);
    }

    /** {@code PUT /api/v1/authz/permissions/{id}}。 */
    @Transactional
    public AuthzDtos.PermissionView update(Long id, AuthzDtos.PermissionUpsertRequest request) {
        assertCanMaintainPermissionTree("维护");
        SysPermission permission = requirePermission(id);
        Long previousParentId = permission.getParentId();
        String code = requireValidCode(request.code());
        String permType = requireValidPermType(request.permType());
        SysPermission sameCode = permissionMapper.selectByCode(code);
        if (sameCode != null && !sameCode.getId().equals(id)) {
            throw new BizException(ErrorCode.DUPLICATE, "权限码已存在（uk_sys_permission_code）：" + code);
        }
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "权限节点不能以自身为父节点");
            }
            requirePermission(request.parentId());
            PermissionTreePolicy.Tree policyTree = loadTree();
            if (policyTree.descendants(id).contains(request.parentId())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "权限节点不能挂到自己的后代之下（会形成环）：parentId=" + request.parentId());
            }
        }
        permission.setParentId(request.parentId());
        permission.setPermType(permType);
        permission.setCode(code);
        permission.setName(request.name().trim());
        permission.setUrl(trim(request.url()));
        permission.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        permissionMapper.updatePermission(permission);
        if (!java.util.Objects.equals(previousParentId, request.parentId())) {
            repairAncestorClosure(id);
        }
        log.info("修改权限节点：id={} code={} parentId={}", id, code, request.parentId());
        return toView(permission);
    }

    /**
     * 「改挂父节点」后的授权自愈。
     *
     * <p>被移动的节点在角色授权里已经是「已授予」的，改挂之后它的新祖先链上可能出现
     * **未授予的父节点** —— 那就违反了「父节点未授予时子节点不得单独授予」。
     * 这里对持有该节点的角色补齐缺失祖先并失效缓存，让落库集合始终满足祖先闭合不变式
     * （否则前端读到的树会缺父菜单，用户会「有权限但看不到入口」）。
     */
    private void repairAncestorClosure(Long movedPermissionId) {
        PermissionTreePolicy.Tree tree = loadTree();
        for (Long roleId : rolePermissionMapper.selectRoleIdsByPermissionId(movedPermissionId)) {
            Set<Long> stored = new LinkedHashSet<>(rolePermissionMapper.selectPermissionIdsByRoleId(roleId));
            Set<Long> missing = new LinkedHashSet<>();
            for (Long grantedId : stored) {
                if (!tree.contains(grantedId)) {
                    continue;
                }
                for (Long ancestor : tree.ancestors(grantedId)) {
                    if (!stored.contains(ancestor)) {
                        missing.add(ancestor);
                    }
                }
            }
            if (!missing.isEmpty()) {
                rolePermissionMapper.insertBatch(roleId, missing, null);
                effectivePermissionService.invalidateByRole(roleId);
                log.warn("权限节点 {} 改挂父节点后，角色 {} 的授权已补齐祖先节点：{}", movedPermissionId, roleId, missing);
            }
        }
    }

    /**
     * {@code DELETE /api/v1/authz/permissions/{id}}。
     *
     * <p>级联口径：先把该节点从所有角色的授权中摘除（并失效相关用户缓存），再删除节点本身——
     * 这样「删除权限节点」不会留下指向不存在权限的脏行。
     */
    @Transactional
    public void delete(Long id) {
        assertCanMaintainPermissionTree("维护");
        SysPermission permission = requirePermission(id);
        int children = permissionMapper.countChildren(id);
        if (children > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "该权限节点仍有 " + children + " 个子节点，请先删除子节点");
        }
        List<Long> roleIds = rolePermissionMapper.selectRoleIdsByPermissionId(id);
        if (!roleIds.isEmpty()) {
            rolePermissionMapper.deleteByPermissionId(id);
            for (Long roleId : roleIds) {
                effectivePermissionService.invalidateByRole(roleId);
            }
            log.info("删除权限节点 {}（{}）并级联清理 {} 个角色的授权", id, permission.getCode(), roleIds.size());
        }
        permissionMapper.deleteById(id);
    }

    /** 按 id 取节点（不存在 → 404）。 */
    public SysPermission requirePermission(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "权限节点不存在");
        }
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "权限节点不存在：" + id);
        }
        return permission;
    }

    // ---------------------------------------------------------------- 内部

    private static List<PermissionTreePolicy.Node> toNodes(List<SysPermission> permissions) {
        List<PermissionTreePolicy.Node> nodes = new ArrayList<>();
        for (SysPermission permission : permissions) {
            nodes.add(EffectivePermissionService.toTreeNode(permission));
        }
        return nodes;
    }

    private AuthzDtos.PermissionNodeView assemble(PermissionTreePolicy.Tree tree, Map<Long, SysPermission> byId, Long id) {
        SysPermission permission = byId.get(id);
        List<AuthzDtos.PermissionNodeView> children = new ArrayList<>();
        for (Long childId : tree.childrenOf(id)) {
            children.add(assemble(tree, byId, childId));
        }
        return new AuthzDtos.PermissionNodeView(permission.getId(), permission.getParentId(), permission.getCode(),
                permission.getName(), permission.getPermType(), permission.getUrl(), permission.getSortNo(), children);
    }

    private static String requireValidCode(String code) {
        String normalized = code == null ? null : code.trim();
        if (normalized == null || !CODE_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "权限码格式不合法（如 flow:task:approve，小写字母开头，≤64 字符）：" + code);
        }
        return normalized;
    }

    private static String requireValidPermType(String permType) {
        String normalized = permType == null ? null : permType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SysPermission.TYPE_MENU.equals(normalized)
                && !SysPermission.TYPE_BUTTON.equals(normalized)
                && !SysPermission.TYPE_API.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "perm_type 仅允许 menu|button|api：" + permType);
        }
        return normalized;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 实体 → 扁平视图。 */
    public static AuthzDtos.PermissionView toView(SysPermission permission) {
        return new AuthzDtos.PermissionView(permission.getId(), permission.getParentId(), permission.getCode(),
                permission.getName(), permission.getPermType(), permission.getUrl(), permission.getSortNo());
    }
}
