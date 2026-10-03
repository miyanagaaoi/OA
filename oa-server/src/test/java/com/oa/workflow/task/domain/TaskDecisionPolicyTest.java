package com.oa.workflow.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 三种决议模式的判定单测（2a.5）：或签 / 会签（绝对人数优先、百分比向上取整、NULL=过半）/ 依次签。
 *
 * <p>对应 AC-13（会签 3 人中 2 人通过：第 1 人同意后仍在进行中，第 2 人同意后立即通过）、
 * AC-14（会签任一人驳回即节点驳回）、AC-51（协同组同理）与 doc/prd-0.1.md §5.4。
 */
class TaskDecisionPolicyTest {

    private static final List<Long> THREE = List.of(201L, 202L, 203L);

    private static Map<Long, TaskStatus> map(Object... pairs) {
        Map<Long, TaskStatus> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((Long) pairs[i], (TaskStatus) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("或签（默认）：任一人同意即节点通过；无人处理则继续等待")
    void anyMode() {
        TaskDecisionPolicy.Decision pending = TaskDecisionPolicy.evaluate(DecisionMode.ANY, null, THREE,
                map(201L, TaskStatus.PENDING, 202L, TaskStatus.PENDING));
        assertThat(pending.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.PENDING);
        assertThat(pending.required()).isEqualTo(1);
        assertThat(pending.basis()).isEqualTo("any");

        TaskDecisionPolicy.Decision approved = TaskDecisionPolicy.evaluate(DecisionMode.ANY, null, THREE,
                map(201L, TaskStatus.PENDING, 202L, TaskStatus.AGREED));
        assertThat(approved.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.APPROVED);
        assertThat(approved.approved()).isEqualTo(1);
        assertThat(approved.description()).contains("或签").contains("任一人即通过");
    }

    @Test
    @DisplayName("AC-13：会签「3 人中 2 人」—— 第 1 人同意仍在进行中，第 2 人同意节点立即通过")
    void allModeAc13() {
        TaskDecisionPolicy.Decision first = TaskDecisionPolicy.evaluate(DecisionMode.ALL, "2", THREE,
                map(201L, TaskStatus.AGREED));
        assertThat(first.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.PENDING);
        assertThat(first.approved()).isEqualTo(1);
        assertThat(first.required()).isEqualTo(2);
        assertThat(first.basis()).isEqualTo("absolute");
        assertThat(first.description()).contains("1/2");

        TaskDecisionPolicy.Decision second = TaskDecisionPolicy.evaluate(DecisionMode.ALL, "2", THREE,
                map(201L, TaskStatus.AGREED, 202L, TaskStatus.AGREED));
        assertThat(second.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.APPROVED);
        assertThat(second.approved()).isEqualTo(2);
    }

    @Test
    @DisplayName("会签阈值：百分比向上取整（66% × 3 人 = 2 人）；NULL = 过半（3 人 → 2 人）")
    void allModeThresholds() {
        TaskDecisionPolicy.Decision percent = TaskDecisionPolicy.evaluate(DecisionMode.ALL, "66%", THREE,
                map(202L, TaskStatus.AGREED));
        assertThat(percent.required()).isEqualTo(2);
        assertThat(percent.basis()).isEqualTo("percent");

        TaskDecisionPolicy.Decision majority = TaskDecisionPolicy.evaluate(DecisionMode.ALL, null, THREE,
                map(202L, TaskStatus.AGREED));
        assertThat(majority.required()).isEqualTo(2);
        assertThat(majority.basis()).isEqualTo("majority");

        // 4 人过半 = 3 人（向下取整(4/2)+1）
        TaskDecisionPolicy.Decision four = TaskDecisionPolicy.evaluate(DecisionMode.ALL, null,
                List.of(1L, 2L, 3L, 4L), map(1L, TaskStatus.AGREED, 2L, TaskStatus.AGREED));
        assertThat(four.required()).isEqualTo(3);
        assertThat(four.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.PENDING);
    }

    @Test
    @DisplayName("AC-14 / AC-51：会签（含协同组）任一人驳回 → 节点立即驳回，且不因已有人同意而放行")
    void rejectWins() {
        TaskDecisionPolicy.Decision decision = TaskDecisionPolicy.evaluate(DecisionMode.ALL, "2", THREE,
                map(201L, TaskStatus.AGREED, 202L, TaskStatus.REJECTED));
        assertThat(decision.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.REJECTED);
        assertThat(decision.description()).contains("驳回").contains("会签/协同同口径");

        TaskDecisionPolicy.Decision single = TaskDecisionPolicy.evaluate(DecisionMode.ALL, null, List.of(201L),
                map(201L, TaskStatus.REJECTED));
        assertThat(single.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.REJECTED);
    }

    @Test
    @DisplayName("依次签：必须全部通过；部分通过时给出下一位处理人")
    void sequenceMode() {
        TaskDecisionPolicy.Decision first = TaskDecisionPolicy.evaluate(DecisionMode.SEQUENCE, null, THREE,
                map(201L, TaskStatus.AGREED));
        assertThat(first.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.PENDING);
        assertThat(first.required()).isEqualTo(3);
        assertThat(first.nextApprover()).isEqualTo(202L);
        assertThat(first.basis()).isEqualTo("sequence");

        TaskDecisionPolicy.Decision second = TaskDecisionPolicy.evaluate(DecisionMode.SEQUENCE, null, THREE,
                map(201L, TaskStatus.AGREED, 202L, TaskStatus.AGREED));
        assertThat(second.nextApprover()).isEqualTo(203L);

        TaskDecisionPolicy.Decision all = TaskDecisionPolicy.evaluate(DecisionMode.SEQUENCE, null, THREE,
                map(201L, TaskStatus.AGREED, 202L, TaskStatus.AGREED, 203L, TaskStatus.AGREED));
        assertThat(all.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.APPROVED);
    }

    @Test
    @DisplayName("依次签的下一位 = 候选人顺序里第一个尚未决议的人（顺序即快照顺序）")
    void nextSequentialApprover() {
        assertThat(TaskDecisionPolicy.nextSequentialApprover(THREE, Map.of())).isEqualTo(201L);
        assertThat(TaskDecisionPolicy.nextSequentialApprover(THREE,
                map(201L, TaskStatus.AGREED))).isEqualTo(202L);
        assertThat(TaskDecisionPolicy.nextSequentialApprover(THREE,
                map(201L, TaskStatus.AGREED, 202L, TaskStatus.AGREED, 203L, TaskStatus.AGREED))).isNull();
        assertThat(TaskDecisionPolicy.remainingSequential(THREE, map(201L, TaskStatus.AGREED)))
                .containsExactly(202L, 203L);
    }

    @Test
    @DisplayName("候选人集合为空 → 直接抛错（AC-11：不允许空候选人的节点进入决议）")
    void emptyCandidatesRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        TaskDecisionPolicy.evaluate(DecisionMode.ANY, null, List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("候选人集合为空");
    }

    @Test
    @DisplayName("evaluateOrdered：按顺序传状态即可（依次签的顺序语义不丢）")
    void evaluateOrdered() {
        TaskDecisionPolicy.Decision decision = TaskDecisionPolicy.evaluateOrdered(DecisionMode.SEQUENCE, null,
                THREE, java.util.Arrays.asList(TaskStatus.AGREED, null, null));
        assertThat(decision.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.PENDING);
        assertThat(decision.nextApprover()).isEqualTo(202L);

        TaskDecisionPolicy.Decision rejected = TaskDecisionPolicy.evaluateOrdered(DecisionMode.ALL, "2", THREE,
                java.util.Arrays.asList(TaskStatus.AGREED, TaskStatus.REJECTED, null));
        assertThat(rejected.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.REJECTED);
    }

    @Test
    @DisplayName("mode 为 null（未配置）按或签处理，不抛异常（防御性默认）")
    void nullModeDefaultsToAny() {
        TaskDecisionPolicy.Decision decision = TaskDecisionPolicy.evaluate(null, null, List.of(201L),
                map(201L, TaskStatus.AGREED));
        assertThat(decision.outcome()).isEqualTo(TaskDecisionPolicy.Outcome.APPROVED);
        assertThat(decision.mode()).isEqualTo(DecisionMode.ANY);
    }
}
