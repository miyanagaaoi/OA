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

    /** 当前登录人（缺失返回 {@code null}，用于「取自己姓名」这类非授权判定）。 */
    public CurrentUser principalOrNull() {
        DataScopeContext context = DataScopeContext.current();
        return context == null ? null : context.getPrincipal();
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

    /**
     * 动作面权限（2a.4 运行时）：按 {@code FlowAction.permission()} 给出的权限码放行。
     *
     * <p>与 {@link #requireInitiator} 同一闸门实现（{@link FlowConfigPermission#require}），
     * 权限码来自 {@code V4__permissions.sql} 的 {@code flow:task:*} 一族 —— 不在别处硬编码。
     */
    public CurrentUser requirePermission(String actionLabel, String... permissionCodes) {
        CurrentUser principal = requirePrincipal();
        FlowConfigPermission.require(isSuperAdmin(principal), permissionCodes(principal), actionLabel,
                permissionCodes);
        return principal;
    }

    /** 仅系统管理员（改派 AC-52 等「admin-only」动作）。 */
    public CurrentUser requireSuperAdmin(String actionLabel) {
        CurrentUser principal = requirePrincipal();
        if (!isSuperAdmin(principal)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "「" + actionLabel + "」仅系统管理员可执行");
        }
        return principal;
    }

    /**
     * <b>AC-49 终止入口闸门</b>：系统管理员 ∪ {@code group_leader} 角色 ∪ {@code flow:task:terminate} 权限。
     *
     * <p>与 {@link #requireInitiator} <b>刻意不同</b>：后者放行 {@code flow} 或 {@code admin:flow}，
     * 而 {@code flow} 是门户基础权限（{@code employee} 经由祖先闭包也持有），拿它当终止入口的凭据
     * 会让入口层形同虚设、只剩引擎兜底。终止入口必须按 AC-49 自身口径判定。
     *
     * <p>判定逻辑落在纯函数 {@link FlowConfigPermission#requireTerminate}（可穷举单测）；
     * 引擎侧 {@code FlowEngineService#terminate} 复用同一判据作第二层兜底。
     */
    public CurrentUser requireTerminate() {
        CurrentUser principal = requirePrincipal();
        FlowConfigPermission.requireTerminate(isSuperAdmin(principal), permissionCodes(principal),
                hasRole(principal, "group_leader"));
        return principal;
    }

    /** 是否持有某角色码（如 {@code group_leader} 才能终止流程，AC-49）。 */
    public boolean hasRole(CurrentUser principal, String roleCode) {
        return principal != null && principal.hasRole(roleCode);
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
