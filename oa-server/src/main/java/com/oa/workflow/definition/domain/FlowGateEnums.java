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
 * {@link #CONSUMER} 与 {@link #DEADLINE_TODO}。
 */
public final class FlowGateEnums {

    private FlowGateEnums() {
    }

    /**
     * 计数判定的**消费点**（2a.4 已落地，2a.2 的 {@code TODO(2a.4)} 至此消费完毕）。
     *
     * <p>真实执行器：{@code com.oa.workflow.runtime.domain.GateCounterPolicy}
     * （纯判定）→ {@code com.oa.workflow.runtime.app.FlowGateService}（按实例锁定的模板行读取配置）
     * → {@code FlowEngineService} 在「回退/流转/请求补件」三个动作的入口调用并**超限即拒**。
     */
    public static final String CONSUMER =
            "2a.4 已消费：GateCounterPolicy 判定 maxReturnCount / maxSupplementCount，"
                    + "FlowGateService 按 flow_instance 锁定的模板行读配置，FlowEngineService 超限即拒（40908 / 40909）";

    /** 消费点提示：超时调度依赖阶段 3 的定时器（Q7 的**时限计算**已在 2a.4 落地，见 CONSUMER 同族）。 */
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

    /**
     * <b>撤回窗口口径</b>（2026-10-04 产品裁定：模板级配置项，落 {@code flow_template.withdraw_window}）。
     *
     * <p>真源矛盾：{@code doc/prd-0.1.md} 的 REQ-FLOW-009（第 372/407 行）与 V0.4 澄清（第 787 行）、附录B 状态机图
     * （第 501/773 行）写「撤回仅限节点②<b>通过</b>之前」（②审批中可撤），而 AC-16（第 611 行）写「②审批中 → 撤回失败」。
     * 裁定：**不再二选一**，两种口径都支持、按模板可配，默认取 REQ-FLOW-009 口径（行为不变）；
     * 键位、判定差异表与读写契约见 {@code doc/templates.md} §1.8。
     *
     * <p>取数：引擎**按实例发起时锁定的模板版本**读该值（{@code FlowGateService#withdrawWindowOf}），
     * 不读当前 published 模板 —— 否则「改模板影响在途单据」会违反 AC-09。
     */
    public enum WithdrawWindow {

        /**
         * 允许撤回到**节点②通过之前**（含②审批中）—— <b>REQ-FLOW-009 口径，也是默认值</b>（= 历史行为）。
         */
        UNTIL_FINANCE_APPROVED("②通过前（含②审批中）"),

        /**
         * 仅允许在**节点②开始前**撤回（②一旦 {@code active} / {@code waiting_supplement} / {@code returned}
         * 即不可撤）—— <b>AC-16 的严格口径</b>。
         */
        UNTIL_FINANCE_STARTED("②开始前（②一旦受理即不可撤回）");

        private final String label;

        WithdrawWindow(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 未配置（列 {@code NULL}）时的生效口径：REQ-FLOW-009 口径（默认行为不变）。 */
        public static WithdrawWindow defaultWindow() {
            return UNTIL_FINANCE_APPROVED;
        }

        public static Optional<WithdrawWindow> of(String code) {
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
