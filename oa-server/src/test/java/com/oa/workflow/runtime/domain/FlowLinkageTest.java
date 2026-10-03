package com.oa.workflow.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.SubStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * doc/prd-0.1.md <b>§7.2 联动规则</b>的逐行单测（2a.4）。
 *
 * <p>每条断言都对应 §7.2 表格的一行（或 enums.md 的一条定稿口径），
 * 目的是把「引擎层统一实现」这件事变成可验证事实：规则值对象错一位，测试就红。
 */
class FlowLinkageTest {

    @Test
    @DisplayName("§7.2 第1行：任一节点驳回 → 实例 rejected + 同节点其余任务 auto_closed + 其余节点 cancelled")
    void nodeRejectedCascade() {
        FlowLinkage.RejectCascade cascade = FlowLinkage.nodeRejected();
        assertThat(cascade.instanceStatus()).isEqualTo(InstanceStatus.REJECTED);
        assertThat(cascade.nodeStatus()).isEqualTo(NodeStatus.REJECTED);
        assertThat(cascade.sameNodeOtherTasks()).isEqualTo(TaskStatus.AUTO_CLOSED);
        assertThat(cascade.otherNodeInstances()).isEqualTo(NodeStatus.CANCELLED);
        assertThat(cascade.threadAction()).isEqualTo(ThreadAction.REJECT);
    }

    @Test
    @DisplayName("§7.2 第2/3行：或签任一人通过 与 会签达阈值 → 同一联动（节点 approved + 其余任务 auto_closed + 推进）")
    void nodePassedCascade() {
        FlowLinkage.PassCascade cascade = FlowLinkage.nodePassed();
        assertThat(cascade.nodeStatus()).isEqualTo(NodeStatus.APPROVED);
        assertThat(cascade.sameNodeOtherTasks()).isEqualTo(TaskStatus.AUTO_CLOSED);
        assertThat(cascade.advanceToNextNode()).isTrue();
        assertThat(cascade.threadAction()).isEqualTo(ThreadAction.APPROVE);
    }

    @Test
    @DisplayName("§7.2 第4行：实例终态 → 未完成节点 cancelled + 未决议任务 auto_closed + 通知发起人")
    void instanceTerminalCascade() {
        FlowLinkage.TerminalCascade cascade = FlowLinkage.instanceTerminal(InstanceStatus.APPROVED);
        assertThat(cascade.openNodeInstances()).isEqualTo(NodeStatus.CANCELLED);
        assertThat(cascade.openTasks()).isEqualTo(TaskStatus.AUTO_CLOSED);
        assertThat(cascade.notifyInitiator()).isTrue();

        assertThatThrownBy(() -> FlowLinkage.instanceTerminal(InstanceStatus.APPROVING))
                .as("非终态不存在「实例终态联动」")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非终态");
    }

    @Test
    @DisplayName("§7.2 第5行：财务节点「不涉及费用」跳过 → skipped + skip 轨迹 + 不产生待办 + 归口仍记财务部")
    void financeSkipped() {
        FlowLinkage.SkipCascade cascade = FlowLinkage.financeSkipped("财务部复核");
        assertThat(cascade.nodeStatus()).isEqualTo(NodeStatus.SKIPPED);
        assertThat(cascade.threadAction()).isEqualTo(ThreadAction.SKIP);
        assertThat(cascade.producesTask()).isFalse();
        assertThat(cascade.opinion()).contains("本单不涉及费用").contains("已跳过").contains("财务部");
    }

    @Test
    @DisplayName("自由跳转的中间节点同样落 skip（16 值里没有 jump），理由里带目标序号")
    void jumpSkipped() {
        FlowLinkage.SkipCascade cascade = FlowLinkage.jumpSkipped("分公司分管领导", 5);
        assertThat(cascade.nodeStatus()).isEqualTo(NodeStatus.SKIPPED);
        assertThat(cascade.threadAction()).isEqualTo(ThreadAction.SKIP);
        assertThat(cascade.opinion()).contains("自由跳转").contains("5");
    }

    @Test
    @DisplayName("§7.2 第6行：请求补件 → sub_status=pending_supplement + 节点 waiting_supplement + 任务 supplement_requested")
    void supplementRequested() {
        FlowLinkage.SupplementRequestCascade cascade = FlowLinkage.supplementRequested();
        assertThat(cascade.instanceSubStatus()).isEqualTo(SubStatus.PENDING_SUPPLEMENT);
        assertThat(cascade.nodeStatus()).isEqualTo(NodeStatus.WAITING_SUPPLEMENT);
        assertThat(cascade.taskStatus()).isEqualTo(TaskStatus.SUPPLEMENT_REQUESTED);
        assertThat(cascade.notifiesInitiator()).isTrue();
    }

    @Test
    @DisplayName("§7.2 第7行：提交补件 → 清空 sub_status + 节点回 active + 补件 submitted + 任务回到请求人；"
            + "**计数不在提交时 +1**（轮次在请求时占位，data-model §5.5/§8.2）")
    void supplementSubmitted() {
        FlowLinkage.SupplementSubmitCascade cascade = FlowLinkage.supplementSubmitted();
        assertThat(cascade.instanceSubStatus()).isNull();
        assertThat(cascade.nodeStatus()).isEqualTo(NodeStatus.ACTIVE);
        assertThat(cascade.supplementStatus()).isEqualTo("submitted");
        assertThat(cascade.taskBackToRequestingApprover()).isTrue();
        assertThat(cascade.incrementsSupplementCount()).isFalse();
        assertThat(FlowLinkage.supplementCountBasis()).contains("请求时占位").contains("以 DDL 为准");
    }

    @Test
    @DisplayName("§7.2 第8行：补件超时 → 仅催办（不自动通过、不自动退回），记录 overdue")
    void supplementOverdue() {
        FlowLinkage.SupplementTimeoutCascade cascade = FlowLinkage.supplementOverdue();
        assertThat(cascade.supplementStatus()).isEqualTo("overdue");
        assertThat(cascade.notifyOnly()).isTrue();
        assertThat(cascade.autoPass()).isFalse();
        assertThat(cascade.autoReturn()).isFalse();
    }

    @Test
    @DisplayName("§7.2 第9行：流转 → 当前节点 approved + 承接部门产生任务 + routing_seq/routing_count 均 +1")
    void routingApplied() {
        FlowLinkage.RoutingCascade cascade = FlowLinkage.routingApplied();
        assertThat(cascade.currentNodeStatus()).isEqualTo(NodeStatus.APPROVED);
        assertThat(cascade.createsTaskAtTargetDept()).isTrue();
        assertThat(cascade.incrementsRoutingSeq()).isTrue();
        assertThat(cascade.incrementsRoutingCount()).isTrue();
        assertThat(cascade.routingAction()).isEqualTo(RuntimeEnums.RoutingAction.ROUTE);
    }

    @Test
    @DisplayName("REQ-FLOW-022：回到本部门同样产生任务，但**不计入** routing_count")
    void backHomeApplied() {
        FlowLinkage.RoutingCascade cascade = FlowLinkage.backHomeApplied();
        assertThat(cascade.incrementsRoutingCount()).isFalse();
        assertThat(cascade.incrementsRoutingSeq()).isTrue();
        assertThat(cascade.routingAction()).isEqualTo(RuntimeEnums.RoutingAction.BACK_HOME);
    }

    @Test
    @DisplayName("§7.2 第10行：回退上一节点 → 当前节点 returned（非终态）+ 上一节点 active + returned_count/routing_count +1")
    void rollbackApplied() {
        FlowLinkage.RollbackCascade cascade = FlowLinkage.rollbackApplied();
        assertThat(cascade.currentNodeStatus()).isEqualTo(NodeStatus.RETURNED);
        assertThat(cascade.currentNodeStatus().finished()).isFalse();
        assertThat(cascade.targetNodeStatus()).isEqualTo(NodeStatus.ACTIVE);
        assertThat(cascade.incrementsReturnedCount()).isTrue();
        assertThat(cascade.incrementsRoutingCount()).isTrue();
        assertThat(cascade.returnsToCurrentAfterTargetPasses()).isTrue();
    }

    @Test
    @DisplayName("§7.2 第11/12行：闸门拒绝 → **不产生任何状态变更**，并给出改用建议")
    void gateRejected() {
        FlowLinkage.GateRejection rejection = FlowLinkage.gateRejected("已达上限", "改用驳回或终止");
        assertThat(rejection.stateChanged()).isFalse();
        assertThat(rejection.reason()).isNotBlank();
        assertThat(rejection.fallbackAdvice()).contains("驳回");
    }

    @Test
    @DisplayName("抄送不产生待办、不产生决议（enums.md §8）；⑦ 归档登记不产生审批决议（templates.md T-03）")
    void ccAndArchiveRegister() {
        FlowLinkage.CcCascade cc = FlowLinkage.ccRegistered();
        assertThat(cc.producesTask()).isFalse();
        assertThat(cc.producesDecision()).isFalse();
        assertThat(cc.threadAction()).isEqualTo(ThreadAction.CC);
        assertThat(cc.readOnlyVisible()).isTrue();

        FlowLinkage.ArchiveRegisterCascade archive = FlowLinkage.archiveRegistered();
        assertThat(archive.producesApprovalDecision()).isFalse();
        assertThat(archive.countedInEfficiencyStatistics()).isFalse();
        assertThat(archive.threadAction()).isEqualTo(ThreadAction.ARCHIVE_REGISTER);
    }

    @Test
    @DisplayName("规则书覆盖 §7.2 的全部 12 行（可回显给验收方逐行核对）")
    void ruleBookCoversAllRows() {
        List<FlowLinkage.Rule> rules = FlowLinkage.rules();
        assertThat(rules).hasSize(12);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.trigger()).isNotBlank();
            assertThat(rule.consequence()).isNotBlank();
            assertThat(rule.source()).contains("doc/");
        });
        assertThat(rules.get(0).source()).contains("§7.2");
        assertThat(rules).anySatisfy(rule -> assertThat(rule.trigger()).contains("驳回"));
        assertThat(rules).anySatisfy(rule -> assertThat(rule.trigger()).contains("回退超过 2 次"));
    }
}
