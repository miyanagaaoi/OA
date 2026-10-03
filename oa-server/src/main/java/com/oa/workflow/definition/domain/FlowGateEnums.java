package com.oa.workflow.definition.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Q6 / Q7 裁定（产品决定 2026-10-03）：流转 / 回退 / 补件的**次数上限**与补件的**时限 / 超时处理**
 * 一律做成**可配置项**，不再是硬编码规则。
 *
 * <p><b>权威源</b>：doc/templates.md §1.7「Q6/Q7 闸门配置项」（本节由本次裁定新增）；
 * 原 V0.4 的固定数值降级为**默认值**（见 {@link FlowGatePolicy#v04Defaults()}）。
 *
 * <p><b>取值语义（重要）</b>：
 * <ul>
 *   <li>次数上限：{@code NULL} 或 {@code 0} = <b>不限</b>；{@code >0} = 上限，建议 ≤99（防空转）。</li>
 *   <li>时限：{@code supplementDeadlineDays NULL} = <b>不设时限</b>；{@code >0} 才生效，天数 ≤365。</li>
 *   <li>{@code onSupplementTimeout} 为 {@code notify} 时行为与 V0.4 一致（**仅催办**）；
 *       {@code auto_pass} / {@code auto_return} 是本次裁定新开的可选项，默认不启用。</li>
 * </ul>
 *
 * <p><b>本期（2a.2）只做「配置 + 校验 + 持久化 + 读回 + 接口」</b>：
 * 真正的计数判定属 2a.4 运行时状态机，真正的超时调度属 2b/阶段 3 —— 消费点见
 * {@link #COUNTER_TODO} 与 {@link #DEADLINE_TODO}。
 */
public final class FlowGateEnums {

    private FlowGateEnums() {
    }

    /** 消费点提示：计数判定依赖 2a.4。 */
    public static final String COUNTER_TODO =
            "TODO(2a.4): 按 maxReturnCount / maxSupplementCount 判定";

    /** 消费点提示：超时调度依赖阶段 3 的定时器。 */
    public static final String DEADLINE_TODO =
            "TODO(阶段3): 按 supplementDeadlineDays/Type 与 onSupplementTimeout 调度";

    /** 次数上限的合理上界（防空转；超出即拒绝写入）。 */
    public static final int MAX_COUNT_LIMIT = 99;

    /** 补件时限天数的合理上界（365 天）。 */
    public static final int MAX_DEADLINE_DAYS = 365;

    /** 补件时限的**日历口径**（doc/data-model.md §5.5 / PRD 6.3.1「默认 3 个工作日」）。 */
    public enum DeadlineType {

        /** 自然日。 */
        CALENDAR("自然日"),
        /** 工作日（默认口径，跳过周末；法定节假日口径属阶段 3 的排班能力）。 */
        WORKING("工作日");

        private final String label;

        DeadlineType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<DeadlineType> of(String code) {
            return byCode(values(), code);
        }
    }

    /**
     * 补件超时处理策略。
     *
     * <p>默认 {@link #NOTIFY}（与 V0.4「超时仅催办」一致，行为不变）。
     */
    public enum TimeoutAction {

        /** 仅提醒（站内信 + 邮件催办发起人）。 */
        NOTIFY("仅提醒"),
        /** 自动通过（把「待补件」当作已满足，继续流程）。 */
        AUTO_PASS("自动通过"),
        /** 自动退回（回到发起人，等同驳回语义）。 */
        AUTO_RETURN("自动退回");

        private final String label;

        TimeoutAction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<TimeoutAction> of(String code) {
            return byCode(values(), code);
        }
    }

    private static <E extends Enum<E>> Optional<E> byCode(E[] values, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (E value : values) {
            if (value.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
