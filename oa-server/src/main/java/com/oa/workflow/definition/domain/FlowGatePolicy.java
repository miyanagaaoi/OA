package com.oa.workflow.definition.domain;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 模板级 Q6 / Q7 闸门配置（**纯值对象 + 纯校验**，不依赖 Spring / DB）。
 *
 * <p>持久化落点：{@code flow_template} 的 5 个可空列
 * （{@code max_return_count} / {@code max_supplement_count} /
 * {@code supplement_deadline_days} / {@code supplement_deadline_type} / {@code on_supplement_timeout}），
 * 真源为 {@code doc/data-model.md} §4.1（随 Flyway {@code V1__schema.sql} 建列），
 * 种子默认值见 {@code oa-deploy/sql/03-templates.sql} → {@code V3__templates.sql}。
 * 历史：曾由手写迁移 {@code V5__flow_gate_policy.sql} 追加；列并入 V1 后该迁移已删除。
 *
 * <h2>取值语义</h2>
 * <ul>
 *   <li>{@code maxReturnCount} / {@code maxSupplementCount}：{@code null} 或 {@code 0} = <b>不限</b>；
 *       {@code 1..99} = 上限（>99 拒绝，防空转）；负数拒绝。</li>
 *   <li>{@code supplementDeadlineDays}：{@code null} = <b>不设时限</b>；{@code 1..365} 才生效；
 *       {@code 0} 与负数一律拒绝（0 天没有意义，若要「不设时限」请传 null）。</li>
 *   <li>{@code supplementDeadlineType}：{@link DeadlineType}；给了天数却未给口径时按
 *       {@link DeadlineType#WORKING}（V0.4 定稿口径：3 个工作日）。</li>
 *   <li>{@code onSupplementTimeout}：{@link TimeoutAction}；{@code null} → {@link TimeoutAction#NOTIFY}
 *       （与 V0.4「超时仅催办」逐字一致，默认行为不变）。</li>
 * </ul>
 *
 * <h2>本期边界</h2>
 * <p>只做「配置 + 校验 + 持久化 + 读回」。**不做**计数判定与超时调度 —— 消费点是
 * {@link FlowGateEnums#COUNTER_TODO}（2a.4 运行时状态机）与
 * {@link FlowGateEnums#DEADLINE_TODO}（阶段 3 调度器）。
 */
public record FlowGatePolicy(
        Integer maxReturnCount,
        Integer maxSupplementCount,
        Integer supplementDeadlineDays,
        DeadlineType supplementDeadlineType,
        TimeoutAction onSupplementTimeout
) {

    /** 次数不限 / 无时限 / 仅提醒（**未配置时的口径**）。 */
    public static FlowGatePolicy unlimited() {
        return new FlowGatePolicy(null, null, null, null, TimeoutAction.NOTIFY);
    }

    /**
     * V0.4 定稿默认值（降级为「配置项的初始取值」）。
     *
     * <p>doc/prd-0.1.md 附录 D Q6/Q7：流转+回退 ≤5、同节点被回退 ≤2、回到本部门连续 ≤2、
     * 补件同节点 ≤1 且全单 ≤3；补件时限默认 3 个工作日、超时仅催办。
     * 其中「单次上限」属节点/动作级常量（见 doc/templates.md §1.0「闸门」行），
     * 落到模板可配置面上的是**全单累计**两项：≤5 与 ≤3。
     */
    public static FlowGatePolicy v04Defaults() {
        return new FlowGatePolicy(5, 3, 3, DeadlineType.WORKING, TimeoutAction.NOTIFY);
    }

    /** 出参/入库前把 {@code null} 归一（仅枚举列，数字列保留 null 以表达「不限」）。 */
    public FlowGatePolicy normalized() {        return new FlowGatePolicy(
                normalizeCount(maxReturnCount),
                normalizeCount(maxSupplementCount),
                supplementDeadlineDays,
                supplementDeadlineDays == null
                        ? null
                        : (supplementDeadlineType == null ? DeadlineType.WORKING : supplementDeadlineType),
                onSupplementTimeout == null ? TimeoutAction.NOTIFY : onSupplementTimeout);
    }

    /** {@code null}/{@code 0} → {@code null}（表示「不限」）；其余原样。 */
    private static Integer normalizeCount(Integer value) {
        if (value == null || value == 0) {
            return null;
        }
        return value;
    }

    // ---------------------------------------------------------------- 便捷改写（测试与调用方构造局部变体）

    /** 只改回退次数上限（其余字段保持）。 */
    public FlowGatePolicy withReturnCount(Integer value) {
        return new FlowGatePolicy(value, maxSupplementCount, supplementDeadlineDays, supplementDeadlineType,
                onSupplementTimeout);
    }

    /** 只改补件次数上限。 */
    public FlowGatePolicy withSupplementCount(Integer value) {
        return new FlowGatePolicy(maxReturnCount, value, supplementDeadlineDays, supplementDeadlineType,
                onSupplementTimeout);
    }

    /** 只改补件时限天数（天数非空且未给口径时口径按工作日补全）。 */
    public FlowGatePolicy withDeadlineDays(Integer days) {
        return new FlowGatePolicy(maxReturnCount, maxSupplementCount, days,
                days == null ? supplementDeadlineType
                        : (supplementDeadlineType == null ? DeadlineType.WORKING : supplementDeadlineType),
                onSupplementTimeout);
    }

    /** 回退次数上限（{@code null} = 不限）。 */
    public Integer effectiveMaxReturnCount() {
        return normalizeCount(maxReturnCount);
    }

    /** 补件次数上限（{@code null} = 不限）。 */
    public Integer effectiveMaxSupplementCount() {
        return normalizeCount(maxSupplementCount);
    }

    /** 补件时限（{@code null} = 不设时限）。 */
    public Integer effectiveSupplementDeadlineDays() {
        return supplementDeadlineDays;
    }

    /** 补件时限口径（无时限时为 {@code null}）。 */
    public DeadlineType effectiveDeadlineType() {
        if (supplementDeadlineDays == null) {
            return null;
        }
        return supplementDeadlineType == null ? DeadlineType.WORKING : supplementDeadlineType;
    }

    /** 超时处理策略（{@code null} → {@link TimeoutAction#NOTIFY}）。 */
    public TimeoutAction effectiveTimeoutAction() {
        return onSupplementTimeout == null ? TimeoutAction.NOTIFY : onSupplementTimeout;
    }

    // ================================================================ 校验

    /**
     * 逐条校验并返回全部问题（**不抛异常**，供发布前 dry-run 报告聚合使用）。
     *
     * @param context 问题文案的前缀（如「模板 matter v2」）
     */
    public List<String> violations(String context) {
        String prefix = context == null || context.isBlank() ? "" : context + "：";
        List<String> problems = new ArrayList<>();
        checkCount(problems, prefix, "maxReturnCount", maxReturnCount);
        checkCount(problems, prefix, "maxSupplementCount", maxSupplementCount);
        if (supplementDeadlineDays != null) {
            if (supplementDeadlineDays <= 0) {
                problems.add(prefix + "supplementDeadlineDays 必须 > 0（不设时限请留空），实际 "
                        + supplementDeadlineDays);
            } else if (supplementDeadlineDays > FlowGateEnums.MAX_DEADLINE_DAYS) {
                problems.add(prefix + "supplementDeadlineDays 不得超过 "
                        + FlowGateEnums.MAX_DEADLINE_DAYS + " 天，实际 " + supplementDeadlineDays);
            }
        }
        // 未给天数却给了口径：不视为错误（口径可预置），但如果给了天数而口径非法则由枚举反序列化挡住；
        // 反向提示一次，避免「以为生效了其实没生效」。
        if (supplementDeadlineDays == null && supplementDeadlineType != null) {
            problems.add(prefix + "supplementDeadlineType=" + supplementDeadlineType.code()
                    + " 已配置但 supplementDeadlineDays 为空 → 不设时限，口径不生效");
        }
        return problems;
    }

    private static void checkCount(List<String> problems, String prefix, String field, Integer value) {
        if (value == null) {
            return;
        }
        if (value < 0) {
            problems.add(prefix + field + " 不得为负数（0 或留空 = 不限），实际 " + value);
        } else if (value > FlowGateEnums.MAX_COUNT_LIMIT) {
            problems.add(prefix + field + " 不得超过 " + FlowGateEnums.MAX_COUNT_LIMIT
                    + "（防空转），实际 " + value);
        }
    }

    /** 校验不通过即抛 400（{@link ErrorCode#FLOW_DEFINITION_INVALID}）。 */
    public void assertValid(String context) {
        List<String> problems = violations(context);
        if (!problems.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID, String.join("；", problems))
                    .withDetail("gatePolicyViolations", problems);
        }
    }

    /** 是否与 V0.4 默认值等价（用于「默认行为不变」的可读判断）。 */
    public boolean isV04Default() {
        return equals(v04Defaults());
    }

    /** 是否「次数不限且无时限」（未配置口径）。 */
    public boolean isUnlimited() {
        return effectiveMaxReturnCount() == null
                && effectiveMaxSupplementCount() == null
                && effectiveSupplementDeadlineDays() == null;
    }

    /** 结构化比较（忽略枚举列上的 {@code null} 与显式默认值差异）。 */
    public boolean sameAs(FlowGatePolicy other) {
        return other != null
                && Objects.equals(effectiveMaxReturnCount(), other.effectiveMaxReturnCount())
                && Objects.equals(effectiveMaxSupplementCount(), other.effectiveMaxSupplementCount())
                && Objects.equals(effectiveSupplementDeadlineDays(), other.effectiveSupplementDeadlineDays())
                && Objects.equals(effectiveDeadlineType(), other.effectiveDeadlineType())
                && effectiveTimeoutAction() == other.effectiveTimeoutAction();
    }
}
