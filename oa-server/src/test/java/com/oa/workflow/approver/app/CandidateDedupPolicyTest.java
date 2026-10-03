package com.oa.workflow.approver.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 候选人去重与合并策略单测（prd §5.4、enums.md §3 共同约束第 3/4 条）。
 *
 * <p><b>文档口径</b>：
 * <ul>
 *   <li>「同一人在同一节点出现多次时<b>自动去重</b>」→ 节点内去重**恒开**；</li>
 *   <li>「同一人同时是多个串行节点的审批人时，<b>默认逐节点分别审批</b>（不做连续节点自动合并），
 *       如需合并由流程设计器显式配置」→ 合并默认关闭；</li>
 *   <li>文档**没有**给出「设计器显式配置」的落点（{@code flow_template}/{@code flow_node} 都无该列），
 *       因此本实现把它做成系统级开关 {@code oa.workflow.approver.merge-consecutive-nodes}，
 *       并把「缺少按模板/按节点的落点」列为待决策项（见交付说明）。</li>
 * </ul>
 */
class CandidateDedupPolicyTest {

    private static Candidate candidate(Long id, String name) {
        return new Candidate(id, name, "u" + id, "A" + id, 12L, "公司A", "/1/12/", 12L, null, "active");
    }

    @Test
    @DisplayName("节点内去重恒开：一人多岗 / 多组织挂职造成的重复自动去掉，并给出证据句")
    void dedupWithinNodeAlwaysOn() {
        // 一人多岗夹具：207 同时是「部门1 正职」与「科室1A 正职」 → 同一节点出现两次
        CandidateDedupPolicy.DedupResult result = CandidateDedupPolicy.dedupWithinNode(
                List.of(candidate(207L, "导入员工"), candidate(203L, "郑领"), candidate(207L, "导入员工")));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.candidates()).extracting(Candidate::userId).containsExactly(207L, 203L);
        assertThat(result.removed()).extracting(Candidate::userId).containsExactly(207L);
        assertThat(result.hasDuplicates()).isTrue();
        assertThat(result.evidence()).contains("原始 3 条").contains("去重后 2 条").contains("导入员工");
    }

    @Test
    @DisplayName("无重复时不产生「去重」噪音（证据句保持简洁）")
    void noDuplicates() {
        CandidateDedupPolicy.DedupResult result = CandidateDedupPolicy.dedupWithinNode(
                List.of(candidate(207L, "导入员工"), candidate(203L, "郑领")));
        assertThat(result.hasDuplicates()).isFalse();
        assertThat(result.evidence()).isEqualTo("候选人数 2（无重复）");
    }

    @Test
    @DisplayName("空 / null 输入安全（返回空清单而不是 null）")
    void nullSafe() {
        assertThat(CandidateDedupPolicy.dedupWithinNode(null).candidates()).isEmpty();
        assertThat(CandidateDedupPolicy.dedupWithinNode(List.of()).total()).isZero();
        assertThat(CandidateDedupPolicy.dedupWithinNode(
                java.util.Arrays.asList(null, candidate(null, "无 id"))).candidates()).isEmpty();
    }

    @Test
    @DisplayName("连续节点合并默认关闭（prd §5.4）；仅显式配置为 true 才开启")
    void mergeConsecutiveNodesDefaultOff() {
        assertThat(CandidateDedupPolicy.mergeConsecutiveNodes(null)).isFalse();
        assertThat(CandidateDedupPolicy.mergeConsecutiveNodes(false)).isFalse();
        assertThat(CandidateDedupPolicy.mergeConsecutiveNodes(true)).isTrue();

        assertThat(CandidateDedupPolicy.describeMergePolicy(null))
                .contains("关闭（默认）").contains("逐节点分别审批").contains("待决策项");
        assertThat(CandidateDedupPolicy.describeMergePolicy(true)).contains("已开启");
    }
}
