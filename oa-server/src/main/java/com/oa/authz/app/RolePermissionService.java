package com.oa.authz.app;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.domain.SysRole;
import com.oa.authz.infra.SysRolePermissionMapper;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色权限勾选（{@code GET|PUT /api/v1/authz/roles/{id}/permissions}）—— REQ-ADMIN-003 的核心写入口。
 *
 * <h2>逐级勾选的三套入参口径</h2>
 * <ol>
 *   <li><b>{@code mode=auto}</b>（<b>缺省</b>，也是前端实际使用的口径）：由
 *       {@link PermissionTreePolicy#looksLikeGrantedSet} 自判定——
 *       入参祖先闭合（= {@code GET} 返回的 {@code permissionIds}，即前端 checked ∪ halfChecked
 *       原样回传）时视为「完整已授予集合」，**原样落库**；不闭合（例如复选框只提交叶子）时
 *       视为「全选节点集合」，按「勾选父 ⇒ 后代全授予 + 补齐祖先」展开。
 *       <b>缺省必须是 auto</b>：G 祖先闭合但**不**子树闭合，若让 ticks 兜底缺省，
 *       一次普通回传就会把半选父节点下的兄弟子树一并授权（静默提权）；</li>
 *   <li><b>{@code mode=ticks}</b>：强制按「全选节点集合」展开（调用方明确只提交勾选节点时用）；</li>
 *   <li><b>{@code mode=granted}</b>：强制按「完整已授予集合」处理，先跑
 *       {@link PermissionTreePolicy#requireAncestorClosed}——「父节点未授予时子节点不得单独授予」
 *       违规即 400，**绝不静默补齐**（避免把一次越权提交变成一次合法的权限提升）。</li>
 * </ol>
 *
 * <h2>写路径的其余闸门</h2>
 * <ul>
 *   <li>未知权限 id → 400（防止提交已删除节点，造成「看似成功实则丢权限」）；</li>
 *   <li>分级授权：**角色授权只有系统管理员可执行**，分公司流程管理员一律 403
 *       （{@link AuthorizationPolicy#assertCanGrantPermissions}，PRD §5.2「不可再向下分配权限」）；</li>
 *   <li>保存成功后**必须失效**该角色全部用户的权限缓存（{@code authz:perms:{userId}}）。</li>
 * </ul>
 */
@Service
public class RolePermissionService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);

    /** 入参口径：自判定（缺省；祖先闭合 ⇒ 已授予集合，否则 ⇒ 全选节点集合）。 */
    public static final String MODE_AUTO = "auto";

    /** 入参口径：全选节点集合（强制展开）。 */
    public static final String MODE_TICKS = "ticks";

    /** 入参口径：完整已授予集合（强制不展开 + 祖先闭合校验）。 */
    public static final String MODE_GRANTED = "granted";

    private final SysRolePermissionMapper rolePermissionMapper;
    private final PermissionTreeService permissionTreeService;
    private final EffectivePermissionService effectivePermissionService;
    private final AuthzOperatorProvider operatorProvider;
    private final RoleService roleService;

    public RolePermissionService(SysRolePermissionMapper rolePermissionMapper,
                                 PermissionTreeService permissionTreeService,
                                 EffectivePermissionService effectivePermissionService,
                                 AuthzOperatorProvider operatorProvider,
                                 RoleService roleService) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionTreeService = permissionTreeService;
        this.effectivePermissionService = effectivePermissionService;
        this.operatorProvider = operatorProvider;
        this.roleService = roleService;
    }

    /** {@code GET /api/v1/authz/roles/{id}/permissions}。 */
    public AuthzDtos.RolePermissionView view(Long roleId) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = roleService.requireRole(roleId);
        AuthorizationPolicy.assertCanReadRole(operator, role.getRoleScope());
        return buildView(roleId);
    }

    /** {@code PUT /api/v1/authz/roles/{id}/permissions}。 */
    @Transactional
    public AuthzDtos.RolePermissionView save(Long roleId, AuthzDtos.RolePermissionSaveRequest request) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        SysRole role = roleService.requireRole(roleId);
        AuthorizationPolicy.assertCanMaintainRole(operator, role.getRoleScope());

        PermissionTreePolicy.Tree tree = permissionTreeService.loadTree();
        Collection<Long> payload = request.permissionIds() == null ? List.of() : request.permissionIds();
        String mode = normalizeMode(request.mode());

        Set<Long> granted;
        if (MODE_GRANTED.equals(mode)) {
            // 1) 完整已授予集合（强制）：先验「父未授予则子不得单独授予」，再**原样落库**
            PermissionTreePolicy.requireKnown(tree, payload);
            PermissionTreePolicy.requireAncestorClosed(tree, payload);
            granted = PermissionTreePolicy.resolveGranted(tree, payload);
        } else if (MODE_TICKS.equals(mode)) {
            // 2) 全选节点集合（强制）：展开（勾选父 ⇒ 后代全授予 + 补齐祖先），结果天然祖先闭合
            granted = PermissionTreePolicy.expand(tree, payload);
        } else {
            // 3) auto（缺省）：祖先闭合 ⇒ 已授予集合原样落库；不闭合 ⇒ 按全选节点展开
            granted = PermissionTreePolicy.resolveAuto(tree, payload);
            log.debug("权限勾选缺省口径自判定：roleId={} 提交{}个 → {} interpretation",
                    roleId, payload.size(),
                    PermissionTreePolicy.looksLikeGrantedSet(tree, payload) ? "granted" : "ticks");
        }
        PermissionTreePolicy.requireAncestorClosed(tree, granted);
        AuthorizationPolicy.assertCanGrantPermissions(operator, role.getRoleScope(), granted);

        rolePermissionMapper.deleteByRoleId(roleId);
        if (!granted.isEmpty()) {
            rolePermissionMapper.insertBatch(roleId, granted, operator.userId());
        }
        effectivePermissionService.invalidateByRole(roleId);
        log.info("保存角色权限勾选：roleId={} mode={} 提交{}个 → 落库{}个（展开为全量落库口径）",
                roleId, mode, payload.size(), granted.size());
        return buildView(roleId);
    }

    // ---------------------------------------------------------------- 内部

    private AuthzDtos.RolePermissionView buildView(Long roleId) {
        PermissionTreePolicy.Tree tree = permissionTreeService.loadTree();
        List<Long> stored = rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
        PermissionTreePolicy.GrantSet grantSet = PermissionTreePolicy.restore(tree, stored);
        List<Long> permissionIds = new ArrayList<>(grantSet.granted());
        List<Long> checkedIds = new ArrayList<>(grantSet.checked());
        List<Long> halfChecked = new ArrayList<>(grantSet.halfChecked());
        List<String> codes = rolePermissionMapper.selectCodesByRoleId(roleId);
        if (!grantSet.ancestorViolations().isEmpty()) {
            // 只可能是历史脏数据（写路径强制祖先闭合）；暴露出来而不是静默修正，便于定位
            log.error("角色 {} 的授权存在祖先闭合违规（子已授予而父未授予）：{}", roleId, grantSet.ancestorViolations());
        }
        return new AuthzDtos.RolePermissionView(roleId, checkedIds, permissionIds, codes, halfChecked);
    }

    /** {@code mode} 归一：缺省 = {@link #MODE_AUTO}（见类注释「为什么缺省必须是 auto」）。 */
    static String normalizeMode(String mode) {
        String normalized = mode == null || mode.isBlank() ? MODE_AUTO : mode.trim().toLowerCase(Locale.ROOT);
        if (!MODE_AUTO.equals(normalized) && !MODE_TICKS.equals(normalized) && !MODE_GRANTED.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "mode 仅允许 auto|ticks|granted：" + mode);
        }
        return normalized;
    }
}
