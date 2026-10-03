package com.oa.workflow.definition.app;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 流程配置面的权限解析器（把 {@link FlowConfigPermission} 的纯判定接到真实上下文）。
 *
 * <p>数据来源：
 * <ul>
 *   <li>当前登录人 → {@link DataScopeContext#current()} 的 principal（由 {@code AuthInterceptor} 装载）；</li>
 *   <li>有效权限码 → {@link EffectivePermissionService#permissionCodes(Long)}（走
 *       {@code authz:perms:{userId}} 缓存，口径与 {@code /auth/me} 完全一致）；</li>
 *   <li>系统管理员 → {@code admin} 角色（与 {@code isSuperAdmin} 同源）。</li>
 * </ul>
 *
 * <p>因此**权限码是权威判据、角色名只是系统管理员的兜底**，与
 * {@code 04-permissions.sql} 的种子授权逐字对应。
 */
@Service
public class WorkflowPermissionService {

    private final EffectivePermissionService effectivePermissionService;

    public WorkflowPermissionService(EffectivePermissionService effectivePermissionService) {
        this.effectivePermissionService = effectivePermissionService;
    }

    /** 当前登录人（缺失即 401）。 */
    public CurrentUser requirePrincipal() {
        DataScopeContext context = DataScopeContext.current();
        CurrentUser principal = context == null ? null : context.getPrincipal();
        if (principal == null || principal.id() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /** 模板读权限。 */
    public CurrentUser requireTemplateRead() {
        return require("查看流程模板", FlowConfigPermission.TEMPLATE_READ);
    }

    /** 节点与行为配置写权限。 */
    public CurrentUser requireNodeWrite() {
        return require("配置流程节点", FlowConfigPermission.NODE_WRITE);
    }

    /** 版本发布权限。 */
    public CurrentUser requirePublish() {
        return require("发布/归档流程模板版本", FlowConfigPermission.PUBLISH);
    }

    /** 发起侧权限（预检 / 建实例 / 提交 / 读快照）。 */
    public CurrentUser requireInitiator(String action) {
        CurrentUser principal = requirePrincipal();
        FlowConfigPermission.requireInitiator(isSuperAdmin(principal), permissionCodes(principal), action);
        return principal;
    }

    private CurrentUser require(String action, String permission) {
        CurrentUser principal = requirePrincipal();
        FlowConfigPermission.require(isSuperAdmin(principal), permissionCodes(principal), action, permission);
        return principal;
    }

    /** 有效权限码（缓存优先；与 {@code /auth/me} 同源）。 */
    public Set<String> permissionCodes(CurrentUser principal) {
        return effectivePermissionService.permissionCodes(principal.id());
    }

    /** 是否系统管理员（{@code admin} 角色）。 */
    public boolean isSuperAdmin(CurrentUser principal) {
        return principal != null && principal.hasRole("admin");
    }
}
