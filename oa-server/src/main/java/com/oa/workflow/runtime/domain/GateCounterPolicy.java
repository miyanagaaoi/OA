package com.oa.workflow.runtime.domain;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.Locale;

/**
 * <b>Q6 闸门的真实执行器</b>（2a.4 消费点）—— 把 {@code flow_template} 的两个模板级可配置项
 * 变成「拒绝 / 放行 + 剩余次数」的判定。
 *
 * <h2>权威口径（doc/templates.md §1.7）</h2>
 * <table border="1">
 *   <tr><th>键（列名）</th><th>取值范围</th><th>语义</th><th>计数列</th></tr>
 *   <tr><td>{@code max_return_count}</td><td>{@code 0}/NULL = 不限；{@code 1..99}</td>
 *       <td>全单<b>回退</b>次数上限（含流转，见下）</td><td>{@code flow_instance.routing_count}</td></tr>
 *   <tr><td>{@code max_supplement_count}</td><td>{@code 0}/NULL = 不限；{@code 1..99}</td>
 *       <td>全单<b>补件</b>次数上限</td><td>{@code flow_instance.supplement_count}</td></tr>
 * </table>
 *
 * <h2>「回退」口径 = routing_count（route + rollback，不含 back_home）</h2>
 * <p>doc/data-model.md §5.1 与 §8.2 定稿：{@code routing_count} 只计 {@code route} 与 {@code rollback}
 * （上限 5，即 {@code max_return_count} 的种子默认值），**不含** {@code back_home}；
 * 「连续 {@code back_home} ≤2」由 {@code flow_routing} 按 {@code seq} 的连续记录单独判定。
 * 因此本类对「回退」与「流转」用**同一个**预算：
 * <ul>
 *   <li>{@link #evaluateReturn} 的 {@code used} 传 {@code flow_instance.routing_count}；</li>
 *   <li>动作是 {@code route} 还是 {@code rollback} 不影响判定，只影响 {@code flow_routing.action_type}。</li>
 * </ul>
 *
 * <h2>为什么 {@code used >= max} 是拒绝而不是 {@code used > max}</h2>
 * <p>PRD §7.2 的原文是「流转/回退达到上限（<b>已满 5 次</b>）→ <b>拒绝操作</b>」：
 * 已达上限（已满）即拒绝，即允许的动作次数恰好等于上限值（5 次动作后 routing_count = 5，第 6 次被拒）。
 *
 * <h2>与旧实现的关系</h2>
 * <p>2a.2 只做了配置面并留下 {@code TODO(2a.4): 按 maxReturnCount / maxSupplementCount 判定}
 * （见 {@link com.oa.workflow.definition.domain.FlowGateEnums}）；本类即该 TODO 的消费点，
 * TODO 标记已从 {@code FlowGateEnums} 移除（见 {@code FlowGateEnums.CONSUMER}）。
 *
 * <p>纯函数、无 Spring / DB 依赖，可穷举单测（{@code GateCounterPolicyTest}）。
 */
public final class GateCounterPolicy {

    /** 「回退」预算的配置键（{@code flow_template.max_return_count}）。 */
    public static final String KEY_MAX_RETURN = "maxReturnCount";

    /** 「补件」预算的配置键（{@code flow_template.max_supplement_count}）。 */
    public static final String KEY_MAX_SUPPLEMENT = "maxSupplementCount";

    private GateCounterPolicy() {
    }

    /**
     * 一个预算的判定结果。
     *
     * @param key        配置键（出参用）
     * @param max        上限（{@code null} = 不限）
     * @param used       已用次数（实例计数列的值）
     * @param remaining  剩余次数（不限时为 {@code null}）
     * @param unlimited  是否不限（{@code 0} 或 {@code null}）
     */
    public record Budget(String key, Integer max, int used, Integer remaining, boolean unlimited) {

        /** 是否已超限（已达上限）。 */
        public boolean exceeded() {
            return !unlimited && used >= max;
        }

        /** 是否放行。 */
        public boolean allowed() {
            return !exceeded();
        }

        /**
         * 拒绝文案：**必须**同时给出上限、已用、剩余次数（任务书要求「明确错误码与剩余次数」）。
         */
        public String denyMessage(String actionLabel) {
            if (!exceeded()) {
                throw new IllegalStateException("未超限时不应取拒绝文案：" + key);
            }
            return actionLabel + "次数已达上限（" + key + "=" + max + "，已用 " + used
                    + "，剩余 0 次）：请改用「驳回」或「终止」";
        }

        /** 放行文案（剩余次数可读化；不限时明确写「不限」）。 */
        public String allowMessage(String actionLabel) {
            if (unlimited) {
                return actionLabel + "：" + key + " 未配置上限（0/NULL = 不限）";
            }
            return actionLabel + "：已用 " + used + " 次，剩余 " + (max - used) + " 次（上限 " + max + "）";
        }
    }

    /** 回退/流转预算：{@code used = flow_instance.routing_count}。 */
    public static Budget evaluateReturn(Integer maxReturnCount, int routingCount) {
        return evaluate(KEY_MAX_RETURN, maxReturnCount, routingCount);
    }

    /** 补件预算：{@code used = flow_instance.supplement_count}。 */
    public static Budget evaluateSupplement(Integer maxSupplementCount, int supplementCount) {
        return evaluate(KEY_MAX_SUPPLEMENT, maxSupplementCount, supplementCount);
    }

    /**
     * 通用判定。
     *
     * @param key  配置键
     * @param max  上限（{@code null} 或 {@code 0} = 不限）
     * @param used 已用次数（负数按 0 处理）
     */
    public static Budget evaluate(String key, Integer max, int used) {
        int effectiveUsed = Math.max(used, 0);
        if (max == null || max <= 0) {
            return new Budget(key, max, effectiveUsed, null, true);
        }
        int remaining = Math.max(max - effectiveUsed, 0);
        return new Budget(key, max, effectiveUsed, remaining, false);
    }

    /** 超限即抛（带具体错误码与剩余次数文案）；放行时返回预算结果供出参回显。 */
    public static Budget assertReturnBudget(Integer maxReturnCount, int routingCount, String actionLabel) {
        Budget budget = evaluateReturn(maxReturnCount, routingCount);
        if (budget.exceeded()) {
            throw new BizException(ErrorCode.FLOW_RETURN_BUDGET_EXCEEDED,
                    budget.denyMessage(actionLabel) + "（错误码 " + ErrorCode.FLOW_RETURN_BUDGET_EXCEEDED.getCode() + "）")
                    .withDetail("gate", KEY_MAX_RETURN)
                    .withDetail("max", budget.max())
                    .withDetail("used", budget.used())
                    .withDetail("remaining", 0);
        }
        return budget;
    }

    /** 补件预算：超限即抛。 */
    public static Budget assertSupplementBudget(Integer maxSupplementCount, int supplementCount) {
        Budget budget = evaluateSupplement(maxSupplementCount, supplementCount);
        if (budget.exceeded()) {
            throw new BizException(ErrorCode.FLOW_SUPPLEMENT_BUDGET_EXCEEDED,
                    budget.denyMessage("补件")
                            + "（错误码 " + ErrorCode.FLOW_SUPPLEMENT_BUDGET_EXCEEDED.getCode() + "）")
                    .withDetail("gate", KEY_MAX_SUPPLEMENT)
                    .withDetail("max", budget.max())
                    .withDetail("used", budget.used())
                    .withDetail("remaining", 0);
        }
        return budget;
    }

    /** 归一化上限（{@code 0} → {@code null} = 不限）。 */
    public static Integer normalize(Integer max) {
        return max == null || max <= 0 ? null : max;
    }

    /** 出参用的剩余次数（不限时返回 {@code null}，便于前端显示「不限」）。 */
    public static Integer remaining(Integer max, int used) {
        Budget budget = evaluate("max", max, used);
        return budget.remaining();
    }

    /** 键名归一（防止出参大小写漂移）。 */
    static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
