package com.oa.workflow.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>撤回窗口真值表单测</b>（2026-10-04 产品裁定：撤回窗口是模板级配置项）。
 *
 * <p>真源：{@code doc/templates.md} §1.8 的判定差异表；背景是 {@code doc/prd-0.1.md}
 * REQ-FLOW-009（②通过前可撤，含②审批中）与 AC-16（②审批中撤回失败）的真实矛盾。
 *
 * <p>三个时刻 = 节点②的 {@code pending} / {@code active}（严格口径另加
 * {@code waiting_supplement} / {@code returned} 两个「已开始处理」态）/ {@code approved}；
 * 两种口径 × 三个时刻的组合在 {@link #truthTable()} 里逐格断言。
 * 另有「不得削弱的既有语义」回归：②之后被推进即拒、终态级联的 cancelled 不阻止撤回、
 * {@code NULL} 口径等价于默认。
 */
class WithdrawWindowPolicyTest {

    @Test
    @DisplayName("真值表｜两种口径 × 节点②三时刻（pending / active / approved）+ 严格口径的两个增量态")
    void truthTable() {
        // ---------- ② pending（未开始）→ 两种口径都放行 ----------
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_APPROVED, nodes(2, NodeStatus.PENDING), true);
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_STARTED, nodes(2, NodeStatus.PENDING), true);

        // ---------- ② active（审批中）→ 默认放行（REQ-FLOW-009）/ 严格拒绝（AC-16）----------
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_APPROVED, nodes(2, NodeStatus.ACTIVE), true);
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_STARTED, nodes(2, NodeStatus.ACTIVE), false);

        // ---------- ② approved（已通过）→ 两种口径都拒绝（REQ-FLOW-009 的正面口径）----------
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_APPROVED, nodes(2, NodeStatus.APPROVED), false);
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_STARTED, nodes(2, NodeStatus.APPROVED), false);

        // ---------- 严格口径的两个增量态：也是「②已开始处理」，同样拒绝 ----------
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_APPROVED, nodes(2, NodeStatus.WAITING_SUPPLEMENT), true);
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_STARTED, nodes(2, NodeStatus.WAITING_SUPPLEMENT), false);
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_APPROVED, nodes(2, NodeStatus.RETURNED), true);
        assertAllowed(WithdrawWindow.UNTIL_FINANCE_STARTED, nodes(2, NodeStatus.RETURNED), false);
    }

    @Test
    @DisplayName("NULL 口径 ≡ until_finance_approved（历史数据与未配置模板行为不变）")
    void nullMeansDefaultWindow() {
        for (NodeStatus status : new NodeStatus[] {NodeStatus.PENDING, NodeStatus.ACTIVE,
                NodeStatus.WAITING_SUPPLEMENT, NodeStatus.RETURNED, NodeStatus.APPROVED}) {
            List<WithdrawWindowPolicy.NodeView> nodes = nodes(2, status);
            assertThat(WithdrawWindowPolicy.allowed(null, nodes))
                    .as("撤回窗口为 NULL 时按默认口径（②%s 可撤）", status.code())
                    .isEqualTo(WithdrawWindowPolicy.allowed(WithdrawWindow.UNTIL_FINANCE_APPROVED, nodes));
        }
    }

    @Test
    @DisplayName("不得削弱｜②之后（seq>2）的节点只要离开 pending 即拒；pending / cancelled 不阻止撤回")
    void advancedBeyondSecondAlwaysRejected() {
        for (WithdrawWindow window : WithdrawWindow.values()) {
            for (NodeStatus advanced : new NodeStatus[] {NodeStatus.ACTIVE, NodeStatus.WAITING_SUPPLEMENT,
                    NodeStatus.RETURNED, NodeStatus.APPROVED, NodeStatus.SKIPPED, NodeStatus.REJECTED}) {
                assertThat(WithdrawWindowPolicy.allowed(window,
                        trunk(NodeStatus.PENDING, NodeStatus.PENDING, advanced, NodeStatus.PENDING)))
                        .as("%s：seq>2 处于 %s ⇒ 主干已推进过②，必须拒绝", window.code(), advanced.code())
                        .isFalse();
            }
            for (NodeStatus notArrived : new NodeStatus[] {NodeStatus.PENDING, NodeStatus.CANCELLED}) {
                assertThat(WithdrawWindowPolicy.allowed(window,
                        trunk(NodeStatus.ACTIVE, NodeStatus.PENDING, notArrived, NodeStatus.PENDING)))
                        .as("%s：seq>2 处于 %s ⇒ 未到达该节点，不得阻止撤回", window.code(), notArrived.code())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("不得削弱｜提交后的标准形状（①active、②~⑦全 pending）在两种口径下都可撤回")
    void justSubmittedShapeAllowedUnderBothWindows() {
        List<WithdrawWindowPolicy.NodeView> submitted = new ArrayList<>();
        submitted.add(node(1, NodeStatus.ACTIVE));
        for (int seq = 2; seq <= 7; seq++) {
            submitted.add(node(seq, NodeStatus.PENDING));
        }
        for (WithdrawWindow window : WithdrawWindow.values()) {
            assertThat(WithdrawWindowPolicy.allowed(window, submitted))
                    .as("%s：一次性物化后的 pending 不构成「已越过②」", window.code())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("不得削弱｜已驳回后发起人放弃：②为 rejected/cancelled、其后全 cancelled → 两种口径都放行")
    void rejectedInstanceStillWithdrawableUnderBothWindows() {
        for (WithdrawWindow window : WithdrawWindow.values()) {
            assertThat(WithdrawWindowPolicy.allowed(window, List.of(
                    node(1, NodeStatus.REJECTED),
                    node(2, NodeStatus.CANCELLED),
                    node(3, NodeStatus.CANCELLED),
                    node(4, NodeStatus.CANCELLED))))
                    .as("%s：已驳回单据的「撤回回草稿」不受撤回窗口收紧影响", window.code())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("严格口径只收紧②这一档：②skipped / ②rejected 仍放行（终态另有守卫）")
    void strictWindowOnlyTightensSecondNodeInProgress() {
        assertThat(WithdrawWindowPolicy.allowed(WithdrawWindow.UNTIL_FINANCE_STARTED,
                List.of(node(1, NodeStatus.APPROVED), node(2, NodeStatus.SKIPPED)))).isTrue();
        assertThat(WithdrawWindowPolicy.allowed(WithdrawWindow.UNTIL_FINANCE_STARTED,
                List.of(node(1, NodeStatus.APPROVED), node(2, NodeStatus.REJECTED)))).isTrue();
    }

    @Test
    @DisplayName("健壮性｜空集合 / null 集合 / nodeSeq 缺失 一律放行（无证据不得阻止撤回）")
    void degenerateInputsAllowed() {
        for (WithdrawWindow window : WithdrawWindow.values()) {
            assertThat(WithdrawWindowPolicy.allowed(window, List.of())).isTrue();
            assertThat(WithdrawWindowPolicy.allowed(window, null)).isTrue();
            List<WithdrawWindowPolicy.NodeView> dirty = new ArrayList<>();
            dirty.add(null);
            dirty.add(new SimpleNode(null, NodeStatus.ACTIVE.code()));
            dirty.add(new SimpleNode(3, null));
            assertThat(WithdrawWindowPolicy.allowed(window, dirty)).isTrue();
        }
    }

    @Test
    @DisplayName("判据常量｜严格口径的增量集合恰为 {active, waiting_supplement, returned}")
    void strictIncrementSetIsExplicit() {
        for (NodeStatus status : NodeStatus.values()) {
            boolean expected = status == NodeStatus.ACTIVE
                    || status == NodeStatus.WAITING_SUPPLEMENT
                    || status == NodeStatus.RETURNED;
            assertThat(WithdrawWindowPolicy.startedAtFinance(status.code()))
                    .as("②处于 %s 是否算「已开始处理」", status.code())
                    .isEqualTo(expected);
        }
        assertThat(WithdrawWindowPolicy.startedAtFinance(null)).isFalse();
        assertThat(WithdrawWindowPolicy.startedAtFinance("not_a_status")).isFalse();
        assertThat(WithdrawWindowPolicy.FINANCE_SEQ).isEqualTo(2);
    }

    // ================================================================ 夹具

    /** 主干：① 与 ② 的状态由参数给出，③~⑦ 为 pending（与一次性物化后的形状一致）。 */
    private static List<WithdrawWindowPolicy.NodeView> nodes(int seq, NodeStatus status) {
        List<WithdrawWindowPolicy.NodeView> list = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            list.add(node(i, i == 1 ? NodeStatus.APPROVED : (i == seq ? status : NodeStatus.PENDING)));
        }
        return list;
    }

    /** 只关心 ①②③④ 四个位置的形状。 */
    private static List<WithdrawWindowPolicy.NodeView> trunk(NodeStatus first, NodeStatus second,
                                                            NodeStatus third, NodeStatus fourth) {
        return List.of(node(1, first), node(2, second), node(3, third), node(4, fourth));
    }

    private static WithdrawWindowPolicy.NodeView node(int seq, NodeStatus status) {
        return new SimpleNode(seq, status.code());
    }

    private static void assertAllowed(WithdrawWindow window, List<WithdrawWindowPolicy.NodeView> nodes,
                                      boolean expected) {
        assertThat(WithdrawWindowPolicy.allowed(window, nodes))
                .as("%s × %s ⇒ %s", window.code(), describe(nodes), expected ? "允许撤回" : "拒绝撤回")
                .isEqualTo(expected);
    }

    private static String describe(List<WithdrawWindowPolicy.NodeView> nodes) {
        StringBuilder sb = new StringBuilder();
        for (WithdrawWindowPolicy.NodeView node : nodes) {
            sb.append('#').append(node.nodeSeq()).append('=').append(node.status()).append(' ');
        }
        return sb.toString().trim();
    }

    /** 最小视图（与 {@code FlowNodeInstanceRow} 的接口实现同形）。 */
    private record SimpleNode(Integer nodeSeq, String status) implements WithdrawWindowPolicy.NodeView {
    }
}
