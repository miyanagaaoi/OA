package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;

/**
 * 「强制继续」准入判定 —— <b>纯函数</b>（AC-52 / REQ-FLOW-019 / PRD §6.10 REQ-ADMIN-006）。
 *
 * <h2>口径（施工要求第 7 条）</h2>
 * 危险操作（组织停用/启用、解除负责人绑定、组织移动、业务线分管领导绑定、离职、调岗）
 * 都接受可选的 {@code {reason?, force?}} 请求体：
 * <ul>
 *   <li>{@code force} 未传或 {@code false}：不校验原因，照常执行（**向后兼容**：旧调用不带 body 也不报错）；</li>
 *   <li>{@code force=true}：{@code reason} 必须非空 → 否则 <b>400</b>；调用人必须是**系统管理员**
 *       （角色码 {@code admin}）→ 否则 <b>403</b>。两条同时满足才放行。</li>
 * </ul>
 *
 * <p><b>留痕</b>：{@code reason}/{@code force} 是控制器方法的入参，{@code AuditAspect} 在
 * {@code @Audited(recordBefore=true)} / {@code recordArgs=true} 时会把**全部入参**序列化进
 * {@code sys_log.before_json}（{@code reason} 不在脱敏键表内），因此「填写的强制原因」天然可审计；
 * 校验失败时切面同样会先写入入参 JSON 再抛出，**被拒绝的越权尝试同样留痕**（TC-AUTH-022 口径）。
 *
 * <p><b>边界（不得越权解释）</b>：本类只做「准入 + 留痕」，**不改变**
 * {@code oa.identity.block-on-inflight} 的拦截结论 —— AC-11/AC-12 的硬阻断由该开关决定，
 * 见 {@link InFlightGuard}。
 */
public final class ForceReasonPolicy {

    /** 系统管理员角色码（{@code sys_role.code}，与 {@code OrgService#exportCsv} 同一口径）。 */
    public static final String ADMIN_ROLE = "admin";

    /** 强制继续未填原因的对外文案（400）。 */
    public static final String REASON_REQUIRED_MESSAGE =
            "强制继续（force=true）必须填写原因（AC-52）：轨迹与审计日志均需留痕";

    private ForceReasonPolicy() {
    }

    /**
     * 校验「强制继续」（**不依赖 ThreadLocal**，便于单测直接驱动）。
     *
     * @param force     强制继续标记；非 {@code true} 一律放行
     * @param reason    操作原因；{@code force=true} 时必填
     * @param principal 当前登录人；{@code force=true} 时必须是系统管理员
     * @param action    动作名（仅用于对外文案与日志定位，如「停用组织」）
     */
    public static void assertAllowed(Boolean force, String reason, CurrentUser principal, String action) {
        if (!Boolean.TRUE.equals(force)) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, REASON_REQUIRED_MESSAGE + "（操作：" + action + "）");
        }
        if (principal == null || !principal.hasRole(ADMIN_ROLE)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "仅系统管理员可执行「强制继续」的危险操作（AC-52）：" + action);
        }
    }

    /**
     * 校验「强制继续」（从当前请求上下文取操作人）。
     *
     * <p>上下文缺失（未认证）时按「无权限」处理：{@code force=true} 不可能在未认证请求上生效。
     */
    public static void assertAllowed(Boolean force, String reason, String action) {
        DataScopeContext context = DataScopeContext.current();
        assertAllowed(force, reason, context == null ? null : context.getPrincipal(), action);
    }

    /** 是否声明了强制继续（供日志与出参文案使用）。 */
    public static boolean isForce(Boolean force) {
        return Boolean.TRUE.equals(force);
    }
}
