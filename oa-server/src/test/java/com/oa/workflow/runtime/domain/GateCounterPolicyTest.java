package com.oa.workflow.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Q6 闸门</b>的真实判定单测（2a.4）—— 消费 2a.2 留下的
 * {@code TODO(2a.4): 按 maxReturnCount / maxSupplementCount 判定}。
 *
 * <p>权威口径：doc/templates.md §1.7（{@code 0}/NULL = 不限；{@code 1..99} = 上限）+
 * doc/prd-0.1.md §7.2（「已达上限 → **拒绝操作**，不产生状态变更」）+
 * doc/data-model.md §5.1/§8.2（计数列 {@code routing_count} = route + rollback，不含 back_home）。
 */
class GateCounterPolicyTest {

    @Test
    @DisplayName("不限口径：NULL 与 0 等价（remaining 为 null，永不超限）")
    void unlimited() {
        for (Integer max : new Integer[] {null, 0}) {
            GateCounterPolicy.Budget budget = GateCounterPolicy.evaluateReturn(max, 99);
            assertThat(budget.unlimited()).isTrue();
            assertThat(budget.exceeded()).isFalse();
            assertThat(budget.allowed()).isTrue();
            assertThat(budget.remaining()).isNull();
            assertThat(budget.allowMessage("回退")).contains("不限");
        }
    }

    @Test
    @DisplayName("剩余次数：max=5 时 0..4 次放行、剩余递减；第 5 次动作后（routing_count=5）即拒绝")
    void remainingAndExceeded() {
        assertThat(GateCounterPolicy.evaluateReturn(5, 0).remaining()).isEqualTo(5);
        assertThat(GateCounterPolicy.evaluateReturn(5, 1).remaining()).isEqualTo(4);
        assertThat(GateCounterPolicy.evaluateReturn(5, 4).remaining()).isEqualTo(1);
        assertThat(GateCounterPolicy.evaluateReturn(5, 4).allowed()).isTrue();
        GateCounterPolicy.Budget full = GateCounterPolicy.evaluateReturn(5, 5);
        assertThat(full.exceeded()).isTrue();
        assertThat(full.remaining()).isZero();
        assertThat(full.used()).isEqualTo(5);
        boolean allowed = full.allowed();
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("拒绝文案必须含「上限 / 已用 / 剩余 0 次」与改用建议（报告要求的可读证据）")
    void denyMessageContent() {
        GateCounterPolicy.Budget full = GateCounterPolicy.evaluateReturn(3, 3);
        assertThat(full.denyMessage("回退"))
                .contains("maxReturnCount=3")
                .contains("已用 3")
                .contains("剩余 0 次")
                .contains("驳回")
                .contains("终止");
    }

    @Test
    @DisplayName("assertReturnBudget：未超限返回预算；超限抛 40908，且 details 带 max/used/remaining")
    void assertReturnBudget() {
        GateCounterPolicy.Budget ok = GateCounterPolicy.assertReturnBudget(5, 0, "回退");
        assertThat(ok.allowed()).isTrue();

        assertThatThrownBy(() -> GateCounterPolicy.assertReturnBudget(5, 5, "流转/回退"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_RETURN_BUDGET_EXCEEDED);
                    assertThat(biz.getMessage())
                            .contains("maxReturnCount=5")
                            .contains("已用 5")
                            .contains("剩余 0 次")
                            .contains("40908");
                    assertThat(biz.getDetails()).containsEntry("gate", "maxReturnCount")
                            .containsEntry("used", 5).containsEntry("remaining", 0);
                });
    }

    @Test
    @DisplayName("assertSupplementBudget：超限抛 40909（全单补件上限，种子默认 3）")
    void assertSupplementBudget() {
        assertThat(GateCounterPolicy.assertSupplementBudget(null, 10).unlimited()).isTrue();

        assertThatThrownBy(() -> GateCounterPolicy.assertSupplementBudget(3, 3))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_SUPPLEMENT_BUDGET_EXCEEDED);
                    assertThat(biz.getMessage()).contains("maxSupplementCount=3").contains("40909")
                            .contains("剩余 0 次");
                });
        // 用满 2 次、上限 3 → 仍可请求一次
        assertThat(GateCounterPolicy.assertSupplementBudget(3, 2).remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("负数已用按 0 处理（脏数据不放宽、也不误拒）")
    void negativeUsedNormalized() {
        GateCounterPolicy.Budget budget = GateCounterPolicy.evaluateReturn(5, -3);
        assertThat(budget.used()).isZero();
        assertThat(budget.remaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("normalize：0 → null（不限）")
    void normalize() {
        assertThat(GateCounterPolicy.normalize(0)).isNull();
        assertThat(GateCounterPolicy.normalize(null)).isNull();
        assertThat(GateCounterPolicy.normalize(7)).isEqualTo(7);
    }

    @Test
    @DisplayName("两个键名与 templates.md §1.7 的 JSON / 列名一致（出参回显用）")
    void keyNames() {
        assertThat(GateCounterPolicy.KEY_MAX_RETURN).isEqualTo("maxReturnCount");
        assertThat(GateCounterPolicy.KEY_MAX_SUPPLEMENT).isEqualTo("maxSupplementCount");
        assertThat(GateCounterPolicy.evaluateReturn(1, 0).key()).isEqualTo("maxReturnCount");
        assertThat(GateCounterPolicy.evaluateSupplement(1, 0).key()).isEqualTo("maxSupplementCount");
    }

    @Test
    @DisplayName("未超限时取拒绝文案是编程错误（显式抛异常，不静默返回空串）")
    void denyMessageGuarded() {
        assertThatThrownBy(() -> GateCounterPolicy.evaluateReturn(5, 1).denyMessage("回退"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未超限");
    }
}
