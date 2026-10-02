package com.oa.authz.app;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysPermission;
import com.oa.authz.infra.SysPermissionMapper;
import com.oa.authz.infra.SysRolePermissionMapper;
import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.common.security.CurrentUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 有效权限计算与缓存（normify {@code oa.authz.rbac.effective}）。
 *
 * <h2>计算口径</h2>
 * <p>{@code permissions = ⋃(该用户全部角色的 sys_role_permission → sys_permission.code)}。
 * 因为角色侧落库的已经是**展开为全量**的祖先闭包（见 {@link PermissionTreePolicy}），
 * 这里只做一次集合求并，不需要任何树遍历。
 *
 * <h2>缓存与失效</h2>
 * <ul>
 *   <li>键 {@code authz:perms:{userId}}，TTL 10 分钟（{@link PermissionCache#DEFAULT_TTL}）；</li>
 *   <li>**必须失效**的四个时机：角色权限勾选变更（{@link #invalidateByRole}）、
 *       用户角色分配/撤销（{@link #invalidateUser}）、角色被删除或权限节点被级联删除、
 *       数据域/类别变更（虽然不影响权限码，但同一份缓存也用于判定越权再授权，保守失效）；</li>
 *   <li>{@code POST /api/v1/authz/cache/invalidate} 支持按用户或全量失效（仅系统管理员）。</li>
 * </ul>
 */
@Service
public class EffectivePermissionService {

    private static final Logger log = LoggerFactory.getLogger(EffectivePermissionService.class);

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final PermissionCache cache;

    public EffectivePermissionService(SysRolePermissionMapper rolePermissionMapper,
                                      SysUserRoleMapper userRoleMapper,
                                      SysPermissionMapper permissionMapper,
                                      PermissionCache cache) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.cache = cache;
    }

    /** 用户有效权限码（缓存优先；返回**不可变**有序集合）。 */
    public Set<String> permissionCodes(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Optional<Set<String>> cached = cache.get(userId);
        if (cached.isPresent()) {
            return cached.get();
        }
        Set<String> codes = new LinkedHashSet<>(rolePermissionMapper.selectCodesByUserId(userId));
        cache.put(userId, codes);
        log.debug("权限缓存回源：userId={} codes={}", userId, codes.size());
        return java.util.Collections.unmodifiableSet(codes);
    }

    /** 用户有效权限 id（越权再授权判定用，刻意**不走缓存**：管理操作频次低，读一致性优先）。 */
    public Set<Long> permissionIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(rolePermissionMapper.selectPermissionIdsByUserId(userId));
    }

    /** 是否系统管理员（{@code /auth/me} 的 {@code isSuperAdmin} 与前端的唯一真实来源）。 */
    public boolean isSuperAdmin(Collection<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(AuthorizationPolicy.ROLE_ADMIN);
    }

    /** {@code GET /api/v1/authz/effective-permissions} 与 {@code GET /api/v1/auth/me} 的共用产物。 */
    public AuthzDtos.EffectivePermissionView effectivePermissions(CurrentUser principal) {
        Set<String> codes = permissionCodes(principal.id());
        return new AuthzDtos.EffectivePermissionView(
                principal.id(),
                sorted(principal.roleCodes()),
                sorted(codes),
                menuForest(codes),
                isSuperAdmin(principal.roleCodes()));
    }

    // ---------------------------------------------------------------- 失效

    /** 失效单个用户（角色分配/撤销后调用）。 */
    public void invalidateUser(Long userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }

    /** 失效一批用户（权限节点被级联删除后按受影响角色反查）。 */
    public int invalidateUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (cache.invalidate(userId)) {
                count++;
            }
        }
        return count;
    }

    /** 失效「持有该角色的全部用户」（角色权限勾选 / 数据域 / 类别 / 组织节点变更后调用）。 */
    public int invalidateByRole(Long roleId) {
        if (roleId == null) {
            return 0;
        }
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(roleId);
        return invalidateUsers(userIds);
    }

    /** 全量失效（仅系统管理员；见 {@code AuthorizationPolicy#assertCanInvalidateCache}）。 */
    public int invalidateAll() {
        return cache.invalidateAll();
    }

    /** 观察用：某用户当前是否命中缓存。 */
    public boolean isCached(Long userId) {
        return userId != null && cache.get(userId).isPresent();
    }

    // ---------------------------------------------------------------- 菜单装配

    /**
     * 按权限树装配菜单森林：只保留 {@code perm_type='menu'} 且被授予的节点，
     * 父节点未被授予时子菜单不会出现（角色侧落库已是祖先闭包，故不会发生）。
     */
    List<AuthzDtos.PermissionNodeView> menuForest(Collection<String> grantedCodes) {
        Set<String> granted = grantedCodes == null ? Set.of() : new LinkedHashSet<>(grantedCodes);
        List<SysPermission> all = permissionMapper.selectAll();
        Set<Long> grantedMenuIds = new LinkedHashSet<>();
        List<PermissionTreePolicy.Node> nodes = new ArrayList<>();
        for (SysPermission permission : all) {
            nodes.add(toTreeNode(permission));
            if (SysPermission.TYPE_MENU.equals(permission.getPermType()) && granted.contains(permission.getCode())) {
                grantedMenuIds.add(permission.getId());
            }
        }
        PermissionTreePolicy.Tree tree = PermissionTreePolicy.Tree.of(nodes);
        // 森林根 = 被授予且**父菜单未被授予**（或本就是根）的菜单节点。
        // 不直接用 tree.roots()：这样即便数据里出现「子菜单已授予而其父未授予」的历史脏行，
        // 菜单也不会整棵消失（读路径只求稳健展示，脏数据由写路径的祖先闭合校验挡住）。
        List<AuthzDtos.PermissionNodeView> forest = new ArrayList<>();
        for (Long rootId : tree.ids()) {
            if (!grantedMenuIds.contains(rootId)) {
                continue;
            }
            Long parent = tree.parentOf(rootId);
            if (parent == null || !grantedMenuIds.contains(parent)) {
                AuthzDtos.PermissionNodeView view = assembleMenu(tree, all, grantedMenuIds, rootId);
                if (view != null) {
                    forest.add(view);
                }
            }
        }
        return forest;
    }

    private AuthzDtos.PermissionNodeView assembleMenu(PermissionTreePolicy.Tree tree, List<SysPermission> all,
                                                      Set<Long> grantedMenuIds, Long id) {
        if (!grantedMenuIds.contains(id)) {
            return null;
        }
        SysPermission permission = find(all, id);
        if (permission == null) {
            return null;
        }
        List<AuthzDtos.PermissionNodeView> children = new ArrayList<>();
        for (Long childId : tree.childrenOf(id)) {
            AuthzDtos.PermissionNodeView child = assembleMenu(tree, all, grantedMenuIds, childId);
            if (child != null) {
                children.add(child);
            }
        }
        return new AuthzDtos.PermissionNodeView(permission.getId(), permission.getParentId(), permission.getCode(),
                permission.getName(), permission.getPermType(), permission.getUrl(), permission.getSortNo(), children);
    }

    private static SysPermission find(List<SysPermission> all, Long id) {
        for (SysPermission permission : all) {
            if (permission.getId().equals(id)) {
                return permission;
            }
        }
        return null;
    }

    /** {@code sys_permission} → 策略层节点。 */
    public static PermissionTreePolicy.Node toTreeNode(SysPermission permission) {
        return new PermissionTreePolicy.Node(permission.getId(), permission.getParentId(), permission.getCode(),
                permission.getName(), permission.getPermType(), permission.getUrl(), permission.getSortNo());
    }

    private static List<String> sorted(Collection<String> values) {
        List<String> list = new ArrayList<>(values == null ? List.of() : values);
        list.sort(String::compareTo);
        return list;
    }
}
