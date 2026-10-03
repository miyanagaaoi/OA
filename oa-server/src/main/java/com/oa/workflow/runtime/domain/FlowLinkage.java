package com.oa.workflow.runtime.domain;

import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.SubStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import java.util.List;

/**
 * <b>doc/prd-0.1.md §7.2「联动规则（必须实现，否则会出现挂单）」的代码落点</b>（2a.4）。
 *
 * <p>本类刻意只放**纯函数**：每条联动规则返回一份「联动结果」值对象（三层状态分别该变成什么），
 * 由 {@code FlowEngineService} 统一执行。这样做的原因与 PRD 附录 B 的原话一致 ——
 * 「这些规则必须在**引擎层统一实现**，不允许各页面自行处理」：
 * 把规则写成值对象后，controller / service 各处**没有**第二处状态迁移判断，
 * 单测可以直接穷举每条规则（见 {@code FlowLinkageTest}）。
 *
 * <h2>§7.2 表格 × 本类的逐行对照</h2>
 * <table border="1">
 *   <tr><th>触发（PRD §7.2 原文）</th><th>联动结果</th><th>本类落点</th></tr>
 *   <tr><td>任一节点实例驳回</td>
 *       <td>实例 → 已驳回；同节点其余任务 → 已自动关闭；其余节点实例 → 已取消</td>
 *       <td>{@link #nodeRejected()}</td></tr>
 *   <tr><td>会签节点达到通过阈值 / 或签节点任一人通过</td>
 *       <td>节点实例 → 已通过；该节点未处理任务 → 已自动关闭；实例推进到下一节点</td>
 *       <td>{@link #nodePassed()}</td></tr>
 *   <tr><td>实例状态变为终态（通过/驳回/撤回/终止）</td>
 *       <td>全部进行中节点实例与任务 → 已取消 / 已自动关闭；站内信通知发起人</td>
 *       <td>{@link #instanceTerminal(InstanceStatus)}</td></tr>
 *   <tr><td>财务节点因「不涉及费用」跳过</td>
 *       <td>节点实例 → 已跳过（轨迹留「本单不涉及费用，财务节点已跳过」）</td>
 *       <td>{@link #financeSkipped(String)}</td></tr>
 *   <tr><td>审批人请求补充材料</td>
 *       <td>实例 sub_status → 待补件；当前节点实例 → 等待补件；该节点任务 → 已请求补件</td>
 *       <td>{@link #supplementRequested()}</td></tr>
 *   <tr><td>发起人提交补件</td>
 *       <td>sub_status → 空；补件记录 → 已补件；节点实例 → 进行中；任务回到请求补件的审批人</td>
 *       <td>{@link #supplementSubmitted()}</td></tr>
 *   <tr><td>补件超时未提交</td>
 *       <td>仅催办发起人；不自动驳回、不自动通过；补件记录 → 已超时</td>
 *       <td>{@link #supplementOverdue()}（策略执行属阶段 3，见 {@code FlowGateEnums.DEADLINE_TODO}）</td></tr>
 *   <tr><td>审批人发起流转</td>
 *       <td>routing 新增；routing_seq / routing_count +1；当前节点实例 → 已通过；新承接部门负责人产生新任务</td>
 *       <td>{@link #routingApplied()}</td></tr>
 *   <tr><td>审批人回退上一节点</td>
 *       <td>当前节点实例 → 已退回；上一节点实例 → 进行中（returned_count +1）；routing_count +1</td>
 *       <td>{@link #rollbackApplied()}</td></tr>
 *   <tr><td>流转/回退达到上限</td>
 *       <td><b>拒绝操作</b>并返回具体原因；不产生任何状态变更</td>
 *       <td>{@link #gateRejected()}</td></tr>
 *   <tr><td>同一节点被回退超过 2 次</td>
 *       <td>拒绝再次回退，提示改用驳回或终止</td>
 *       <td>{@link #gateRejected()}</td></tr>
 * </table>
 *
 * <p>另有两条不在 §7.2 表格、但由 enums.md / templates.md 定稿的口径，同样在此单点固化：
 * <ul>
 *   <li><b>抄送不产生审批决议</b>（enums.md §8「cc：抄送人只读可见，不产生待办」）→ {@link #ccRegistered()}；</li>
 *   <li><b>⑦ 归档登记默认仅登记不审批</b>（enums.md §2 + templates.md §0 T-03）→ {@link #archiveRegistered()}。</li>
 * </ul>
 */
public final class FlowLinkage {

    private FlowLinkage() {
    }

    // ================================================================ 规则书（可回显给运维/测试）

    /** §7.2 表格的一行（触发 → 联动结果），用于接口回显与文档核对。 */
    public record Rule(String trigger, String consequence, String source) {
    }

    /** 全部联动规则（顺序即 PRD §7.2 表格顺序）。 */
    public static List<Rule> rules() {
        return List.of(
                new Rule("任一节点实例驳回", "实例 → 已驳回；同节点其余任务 → 已自动关闭；其余节点实例 → 已取消",
                        "doc/prd-0.1.md §7.2 / 附录B；AC-14"),
                new Rule("会签节点达到通过阈值", "节点实例 → 已通过；该节点未处理任务 → 已自动关闭；实例推进到下一节点",
                        "doc/prd-0.1.md §7.2；AC-13"),
                new Rule("或签节点任一人通过", "节点实例 → 已通过；其余任务 → 已自动关闭",
                        "doc/prd-0.1.md §7.2；§5.4"),
                new Rule("实例状态变为终态（通过/驳回/撤回/终止）",
                        "全部进行中节点实例与任务 → 已取消 / 已自动关闭；站内信通知发起人",
                        "doc/prd-0.1.md §7.2"),
                new Rule("财务节点因「不涉及费用」跳过",
                        "节点实例 → 已跳过；轨迹留「本单不涉及费用，财务节点已跳过」；归口部门仍记为财务部",
                        "doc/prd-0.1.md §6.3 / §7.2"),
                new Rule("审批人请求补充材料",
                        "实例 sub_status → 待补件；当前节点实例 → 等待补件；该节点任务 → 已请求补件",
                        "doc/prd-0.1.md §7.2；REQ-FLOW-023"),
                new Rule("发起人提交补件",
                        "实例 sub_status → 空；补件记录 → 已补件；节点实例 → 进行中；任务回到请求补件的审批人",
                        "doc/prd-0.1.md §7.2；REQ-FLOW-023"),
                new Rule("补件超时未提交", "仅催办发起人；不自动驳回、不自动通过；补件记录 → 已超时",
                        "doc/prd-0.1.md §7.2（策略执行属阶段 3）"),
                new Rule("审批人发起流转",
                        "flow_routing 新增；routing_seq / routing_count +1；当前节点实例 → 已通过；新承接部门负责人产生新任务",
                        "doc/prd-0.1.md §7.2；REQ-FLOW-020"),
                new Rule("审批人回退上一节点",
                        "当前节点实例 → 已退回；上一节点实例 → 进行中（returned_count +1）；routing_count +1",
                        "doc/prd-0.1.md §7.2；REQ-FLOW-021"),
                new Rule("流转/回退达到上限（已满 maxReturnCount）或回流到已处理部门",
                        "拒绝操作并返回具体原因；不产生任何状态变更",
                        "doc/prd-0.1.md §7.2 / §6.3.1；doc/templates.md §1.7"),
                new Rule("同一节点被回退超过 2 次", "拒绝再次回退，提示改用驳回或终止",
                        "doc/prd-0.1.md §6.3.1；doc/enums.md §7"));
    }

    // ================================================================ 联动结果值对象

    /** 驳回联动（§7.2 第 1 行）。 */
    public record RejectCascade(
            InstanceStatus instanceStatus,
            NodeStatus nodeStatus,
            TaskStatus sameNodeOtherTasks,
            NodeStatus otherNodeInstances,
            ThreadAction threadAction
    ) {
    }

    /** 通过联动（§7.2 第 2、3 行）。 */
    public record PassCascade(
            NodeStatus nodeStatus,
            TaskStatus sameNodeOtherTasks,
            boolean advanceToNextNode,
            ThreadAction threadAction
    ) {
    }

    /** 实例终态联动（§7.2 第 4 行）。 */
    public record TerminalCascade(
            NodeStatus openNodeInstances,
            TaskStatus openTasks,
            boolean notifyInitiator
    ) {
    }

    /** 跳过联动（§7.2 第 5 行）。 */
    public record SkipCascade(
            NodeStatus nodeStatus,
            ThreadAction threadAction,
            String opinion,
            boolean producesTask
    ) {
    }

    /** 补件请求联动（§7.2 第 6 行）。 */
    public record SupplementRequestCascade(
            SubStatus instanceSubStatus,
            NodeStatus nodeStatus,
            TaskStatus taskStatus,
            boolean notifiesInitiator
    ) {
    }

    /** 补件提交联动（§7.2 第 7 行）。 */
    public record SupplementSubmitCascade(
            SubStatus instanceSubStatus,
            NodeStatus nodeStatus,
            String supplementStatus,
            boolean taskBackToRequestingApprover,
            boolean incrementsSupplementCount
    ) {
    }

    /** 补件超时联动（§7.2 第 8 行）。 */
    public record SupplementTimeoutCascade(
            String supplementStatus,
            boolean autoPass,
            boolean autoReturn,
            boolean notifyOnly
    ) {
    }

    /** 流转联动（§7.2 第 9 行）。 */
    public record RoutingCascade(
            NodeStatus currentNodeStatus,
            boolean createsTaskAtTargetDept,
            boolean incrementsRoutingSeq,
            boolean incrementsRoutingCount,
            RuntimeEnums.RoutingAction routingAction
    ) {
    }

    /** 回退联动（§7.2 第 10 行）。 */
    public record RollbackCascade(
            NodeStatus currentNodeStatus,
            NodeStatus targetNodeStatus,
            boolean incrementsReturnedCount,
            boolean incrementsRoutingCount,
            boolean returnsToCurrentAfterTargetPasses
    ) {
    }

    /** 闸门拒绝（§7.2 第 11、12 行）：**不产生任何状态变更**。 */
    public record GateRejection(boolean stateChanged, String reason, String fallbackAdvice) {
    }

    /** 抄送联动（enums.md §8）。 */
    public record CcCascade(boolean producesTask, boolean producesDecision, ThreadAction threadAction,
                            boolean readOnlyVisible) {
    }

    /** ⑦ 归档登记联动（enums.md §2 / templates.md T-03）。 */
    public record ArchiveRegisterCascade(boolean producesApprovalDecision,
                                         boolean countedInEfficiencyStatistics,
                                         ThreadAction threadAction) {
    }

    // ================================================================ 规则实现

    /** 任一节点实例驳回 → 实例已驳回、同节点其余任务自动关闭、其余节点取消（AC-14 / AC-51）。 */
    public static RejectCascade nodeRejected() {
        return new RejectCascade(InstanceStatus.REJECTED, NodeStatus.REJECTED,
                TaskStatus.AUTO_CLOSED, NodeStatus.CANCELLED, ThreadAction.REJECT);
    }

    /** 节点通过 → 节点已通过、同节点其余任务自动关闭、实例推进到下一节点（AC-13）。 */
    public static PassCascade nodePassed() {
        return new PassCascade(NodeStatus.APPROVED, TaskStatus.AUTO_CLOSED, true, ThreadAction.APPROVE);
    }

    /** 实例进入终态 → 未完成节点取消、未决议任务自动关闭、通知发起人。 */
    public static TerminalCascade instanceTerminal(InstanceStatus status) {
        if (status == null || !status.terminal()) {
            throw new IllegalArgumentException("非终态不存在「实例终态联动」：" + status);
        }
        return new TerminalCascade(NodeStatus.CANCELLED, TaskStatus.AUTO_CLOSED, true);
    }

    /**
     * 财务节点跳过（仅事项单②：{@code involve_cost=false}）。
     *
     * <p>不产生待办；轨迹留「本单不涉及费用，财务节点已跳过」；**归口部门仍记为财务部**。
     */
    public static SkipCascade financeSkipped(String nodeName) {
        String label = nodeName == null || nodeName.isBlank() ? "财务部复核" : nodeName;
        return new SkipCascade(NodeStatus.SKIPPED, ThreadAction.SKIP,
                "本单不涉及费用，" + label + "节点已跳过（归口部门仍记为财务部）", false);
    }

    /** 自由跳转跳过的中间节点（轨迹值域无 {@code jump}，一律落 {@code skip}）。 */
    public static SkipCascade jumpSkipped(String nodeName, Integer targetSeq) {
        String label = nodeName == null || nodeName.isBlank() ? "节点" : nodeName;
        return new SkipCascade(NodeStatus.SKIPPED, ThreadAction.SKIP,
                "自由跳转：跳过「" + label + "」，目标节点序号 " + targetSeq, false);
    }

    /** 请求补充材料 → 实例待补件子状态 + 节点等待补件 + 任务已请求补件 + 通知发起人。 */
    public static SupplementRequestCascade supplementRequested() {
        return new SupplementRequestCascade(SubStatus.PENDING_SUPPLEMENT, NodeStatus.WAITING_SUPPLEMENT,
                TaskStatus.SUPPLEMENT_REQUESTED, true);
    }

    /**
     * 提交补件 → sub_status 清空、补件记录已补件、节点回到进行中、任务回到请求补件的审批人。
     *
     * <p>{@code incrementsSupplementCount=false} 是**口径裁定**：补件轮次与计数在**请求时占位**
     * （doc/data-model.md §5.5「轮次在请求补件时占位（第 N 次请求即第 N 轮），提交补件不改轮次」
     * + §8.2「补件轮次唯一」），否则两次未提交的请求都会算出同一轮次并撞唯一键；
     * PRD §7.2 的字面（「提交补件 → supplement_count +1」）与 DDL 冲突，以 DDL 为准（见类尾说明）。
     */
    public static SupplementSubmitCascade supplementSubmitted() {
        return new SupplementSubmitCascade(null, NodeStatus.ACTIVE, "submitted", true, false);
    }

    /** 补件超时：仅催办（策略执行属阶段 3 —— {@code on_supplement_timeout} 的 auto_pass/auto_return 尚未调度）。 */
    public static SupplementTimeoutCascade supplementOverdue() {
        return new SupplementTimeoutCascade("overdue", false, false, true);
    }

    /** 流转：当前节点已通过，承接部门产生新任务，routing_seq 与 routing_count 均 +1。 */
    public static RoutingCascade routingApplied() {
        return new RoutingCascade(NodeStatus.APPROVED, true, true, true, RuntimeEnums.RoutingAction.ROUTE);
    }

    /** 回到本部门：当前节点已通过，承接部门产生新任务，但**不计入** routing_count。 */
    public static RoutingCascade backHomeApplied() {
        return new RoutingCascade(NodeStatus.APPROVED, true, true, false, RuntimeEnums.RoutingAction.BACK_HOME);
    }

    /** 回退上一节点：当前节点已退回（非终态），上一节点进行中、returned_count +1、routing_count +1。 */
    public static RollbackCascade rollbackApplied() {
        return new RollbackCascade(NodeStatus.RETURNED, NodeStatus.ACTIVE, true, true, true);
    }

    /** 闸门拒绝：**不产生任何状态变更**，并给出改用建议（§7.2 最后两行）。 */
    public static GateRejection gateRejected(String reason, String fallbackAdvice) {
        return new GateRejection(false, reason, fallbackAdvice);
    }

    /** 抄送：只读可见、不产生待办、不产生审批决议。 */
    public static CcCascade ccRegistered() {
        return new CcCascade(false, false, ThreadAction.CC, true);
    }

    /** ⑦ 归档登记：仅留痕，不产生审批决议、不计入审批时长与效率统计。 */
    public static ArchiveRegisterCascade archiveRegistered() {
        return new ArchiveRegisterCascade(false, false, ThreadAction.ARCHIVE_REGISTER);
    }

    // ================================================================ 口径说明

    /**
     * 补件计数的口径裁定（真源冲突取证）。
     *
     * <p><b>冲突</b>：
     * <ul>
     *   <li>doc/prd-0.1.md §7.2（行 522）：「发起人提交补件 … {@code supplement_count} +1」——**提交时**计数；</li>
     *   <li>doc/data-model.md §5.5（行 586）与 §8.2（行 875）：「轮次在**请求补件时**占位（第 N 次请求即第 N 轮），
     *       提交补件不改轮次，超时未补沿用同一轮」+ 唯一键 {@code (instance_id, supplement_round)}——**请求时**计数。</li>
     * </ul>
     * <p><b>裁定</b>：以 DDL 为准（请求时占位并计数）。理由有二：① 若提交时才计数，两次「请求补件」会算出
     * 同一个 {@code supplement_round}，直接撞唯一键（功能不可用）；② PRD §6.3.1 的闸门是「全单补件 ≤3 次」，
     * 而「补件次数用尽后审批人只能通过/驳回/终止」只有在请求时消耗额度才成立。
     * 提交补件**不**再 +1（{@link #supplementSubmitted()} 的 {@code incrementsSupplementCount=false}）。
     */
    public static String supplementCountBasis() {
        return "补件轮次与计数在请求时占位（doc/data-model.md §5.5 / §8.2），提交补件不改轮次；"
                + "PRD §7.2「提交补件 → supplement_count +1」的字面口径与 DDL 冲突，以 DDL 为准";
    }
}
