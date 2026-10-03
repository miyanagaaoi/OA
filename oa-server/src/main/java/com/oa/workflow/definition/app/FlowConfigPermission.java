package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程配置面的**权限闸门** —— <b>纯函数</b>（输入「是否系统管理员 + 有效权限码」，输出放行或 403）。
 *
 * <h2>权限码来源</h2>
 * <p>取自 doc 生成物 {@code oa-deploy/sql/04-permissions.sql}（种子 94 权限）：
 * <ul>
 *   <li>{@code admin:flow:template} —— 流程模板（模板列表/详情/节点/版本历史）；</li>
 *   <li>{@code admin:flow:node} —— 节点配置（节点增删改、决议/策略/跳过条件/解析规则）；</li>
 *   <li>{@code admin:flow:publish} —— 发布与停用（开新版本、发布、归档、发布前校验）；</li>
 *   <li>{@code flow} —— 审批动作基础包（发起前预检 / 建实例 / 提交 / 读快照）；</li>
 *   <li>{@code admin:flow} —— 流程管理整支（{@code company_admin} 持有，作发起侧的兜底）；</li>
 *   <li>{@code flow:task:terminate} —— 终止流程（AC-49：仅系统管理员与集团分管领导）。</li>
 * </ul>
 *
 * <p>系统管理员（{@code admin} 角色）一律放行 —— 与 {@code /auth/me} 的 {@code isSuperAdmin}
 * 同源（{@code EffectivePermissionService#isSuperAdmin}）。**有效权限码由调用方注入**
 * （生产由 {@code WorkflowPermissionService} 从 {@code EffectivePermissionService} 取，
 * 单测直接喂集合），因此本类可在无 Spring 容器下被穷举单测。
 *
 * <p><b>不判数据域</b>：模板/节点是**配置数据**（{@code flow_template} / {@code flow_node} 未登记为
 * 数据域受控表，见 {@code application.yml} 的 {@code oa.scope.tables}），访问控制由本权限闸门承担；
 * 单据数据（{@code flow_instance} / {@code flow_task}）仍走数据域织入，二者不混用。
 */
public final class FlowConfigPermission {

    /** 模板读（列表 / 详情 / 节点 / 版本历史 / 校验报告回看）。 */
    public static final String TEMPLATE_READ = "admin:flow:template";

    /** 节点与行为配置写（增删改节点、决议、策略、跳过条件、解析规则）。 */
    public static final String NODE_WRITE = "admin:flow:node";

    /** 版本发布（开新版本 / 发布 / 归档 / 发布前校验）。 */
    public static final String PUBLISH = "admin:flow:publish";

    /** 审批动作基础包（发起前预检 / 建实例 / 提交 / 读快照）。 */
    public static final String FLOW_USE = "flow";

    /** 流程管理整支（发起侧的兜底：{@code company_admin} 持有 {@code admin:flow:*} 但不含 {@code flow}）。 */
    public static final String FLOW_ADMIN = "admin:flow";

    /** 终止流程（AC-49 / REQ-FLOW-010）：种子 {@code V4__permissions.sql} 的持有人恰为 {admin, group_leader}。 */
    public static final String TERMINATE = "flow:task:terminate";

    private FlowConfigPermission() {
    }

    /** 是否命中任一权限码（系统管理员恒真；{@code required} 为空表示不设限）。 */
    public static boolean has(Boolean superAdmin, Collection<String> codes, String... required) {
        if (Boolean.TRUE.equals(superAdmin)) {
            return true;
        }
        if (required == null || required.length == 0) {
            return true;
        }
        Set<String> granted = codes == null ? Set.of() : new LinkedHashSet<>(codes);
        for (String code : required) {
            if (granted.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /** 命中任一权限码则返回，否则 403。 */
    public static void require(Boolean superAdmin, Collection<String> codes, String action, String... required) {
        if (!has(superAdmin, codes, required)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权执行「" + action + "」：需要权限 " + String.join(" 或 ", required))
                    .withDetail("requiredPermissions", List.of(required));
        }
    }

    /** 发起侧权限（预检 / 建实例 / 提交 / 读快照）：{@code flow} 或 {@code admin:flow} 均可。 */
    public static void requireInitiator(Boolean superAdmin, Collection<String> codes, String action) {
        require(superAdmin, codes, action, FLOW_USE, FLOW_ADMIN);
    }

    // ================================================================ AC-49 终止主体

    /**
     * <b>AC-49 终止主体</b>（纯函数）：系统管理员 ∪ 持有 {@code group_leader} 角色 ∪ 持有
     * {@link #TERMINATE} 权限 —— 三者**并集**，与 {@code doc/prd-0.1.md} 第 655 行（AC-49）
     * 及附录A 权限矩阵第 764 行同口径。
     *
     * <h2>为什么不能复用 {@link #requireInitiator}</h2>
     * <p>{@code flow} 是「门户基础权限」，{@code employee} 等 6 个内置角色都通过**祖先闭包**持有它；
     * 拿它当终止入口的凭据，等于让「终止」的入口判定落到「是不是流程用户」上，
     * 唯一说不的人变成引擎 —— 防御层次不干净。终止入口必须按「终止」这件事本身判定。
     *
     * <h2>两层各管什么（纵深防御）</h2>
     * <ul>
     *   <li><b>入口层</b>（{@code FlowRuntimeController#terminate} → 本方法）：请求一进门就按 AC-49 拒绝，
     *       不持有该角色/权限的账号**根本进不到引擎**；</li>
     *   <li><b>引擎层</b>（{@code FlowEngineService#terminate}）：同一判据再判一次作兜底，
     *       防的是「绕过控制器直调服务」与「入口闸门被后续改动削弱」。</li>
     * </ul>
     * 两层共用本判据（单一口径），但**都在**：删掉任一层都是安全回归。
     */
    public static boolean isTerminateSubject(Boolean superAdmin, Collection<String> codes, boolean groupLeader) {
        if (Boolean.TRUE.equals(superAdmin) || groupLeader) {
            return true;
        }
        return codes != null && codes.contains(TERMINATE);
    }

    /**
     * 终止主体校验：不命中 {@link #isTerminateSubject} 即 403（{@code 40301}）。
     *
     * <p>文案与引擎侧逐字一致，前端与排障都只看这一句：**「终止流程」仅系统管理员与集团分管领导可执行**。
     */
    public static void requireTerminate(Boolean superAdmin, Collection<String> codes, boolean groupLeader) {
        if (isTerminateSubject(superAdmin, codes, groupLeader)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "「终止流程」仅系统管理员与集团分管领导可执行（AC-49 / REQ-FLOW-010）")
                .withDetail("requiredRoles", List.of("admin", "group_leader"))
                .withDetail("requiredPermissions", List.of(TERMINATE));
    }
}
