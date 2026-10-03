package com.oa.workflow.runtime.api.dto;

import java.util.List;

/**
 * 运行时（2a.4）与任务（2a.5）接口的出参/入参。
 *
 * <p>分三类：
 * <ol>
 *   <li><b>动作面清单</b>（{@link ActionCatalogView}）：由 {@code FlowAction} 直接投影，
 *       保证「接口文档 = 代码里的动作矩阵」不会漂移；</li>
 *   <li><b>动作结果</b>（{@link ActionResult}）：每个动作统一返回「三层状态 + 闸门剩余 + 补件时限」；</li>
 *   <li><b>运行态视图</b>（{@link InstanceRuntimeView}）：节点实例 / 任务 / 轨迹 / 流转链 / 补件 / 抄送，
 *       端到端验收与排障的同一份证据来源。</li>
 * </ol>
 */
public final class RuntimeDtos {

    private RuntimeDtos() {
    }

    // ================================================================ 动作面

    /** 动作面清单项（权限 / 是否必填原因 / 意见下限 / 任务后置状态 / 轨迹动作 / 状态迁移）。 */
    public record ActionCatalogView(
            String action,
            String label,
            String permission,
            boolean requiresReason,
            int minOpinionChars,
            String taskStatusAfter,
            String threadAction,
            String transition
    ) {
    }

    // ================================================================ 闸门 / 时限

    /** Q6 预算出参（{@code unlimited=true} 时 {@code max/remaining} 为 {@code null}）。 */
    public record GateView(
            String key,
            Integer max,
            Integer used,
            Integer remaining,
            boolean unlimited,
            String note
    ) {
    }

    /**
     * Q7 补件时限出参。
     *
     * <p>{@code timeoutExecuted=false} + {@code executionTodo} 即「就位但不调度」的显式口径：
     * 时限算出并落库，但 {@code on_supplement_timeout} 的**执行**属阶段 3。
     */
    public record SupplementDeadlineView(
            String dueAt,
            Integer days,
            String type,
            boolean holidayCalendarMissing,
            String calendar,
            String note,
            String timeoutAction,
            boolean timeoutExecuted,
            String executionTodo
    ) {
    }

    // ================================================================ 动作结果

    /** 统一动作结果。 */
    public record ActionResult(
            String action,
            String actionLabel,
            Long instanceId,
            String instanceStatus,
            String subStatus,
            Integer currentNodeSeq,
            Long nodeInstanceId,
            String nodeStatus,
            Long taskId,
            String taskStatus,
            String message,
            GateView gate,
            SupplementDeadlineView deadline
    ) {
    }

    // ================================================================ 运行态视图

    /** 节点实例视图。 */
    public record NodeInstanceView(
            Long id,
            Integer nodeSeq,
            String nodeCode,
            String nodeName,
            String nodeKey,
            Long deptId,
            String status,
            String statusLabel,
            String decisionMode,
            String passThreshold,
            List<Long> approverIds,
            Integer returnedCount,
            boolean supplementRequested,
            String addSignChain,
            boolean addSignOpen,
            String startedAt,
            String finishedAt
    ) {
    }

    /** 任务视图。 */
    public record TaskView(
            Long id,
            Long nodeInstanceId,
            Integer nodeSeq,
            String nodeName,
            Long assigneeId,
            String assigneeName,
            Long originAssigneeId,
            Long delegateFrom,
            String addSignType,
            String status,
            String statusLabel,
            String opinion,
            String handoverReason,
            String createdAt,
            String decidedAt
    ) {
    }

    /** 轨迹视图（16 值动作 + 中文名）。 */
    public record ThreadView(
            Integer seq,
            String action,
            String actionLabel,
            Long nodeInstanceId,
            Long actorId,
            String actorName,
            String actorPosition,
            String opinion,
            Long signatureId,
            String createdAt
    ) {
    }

    /** 流转链视图。 */
    public record RoutingView(
            Integer seq,
            String actionType,
            String actionLabel,
            Long fromDeptId,
            Long toDeptId,
            Integer fromNodeSeq,
            Integer toNodeSeq,
            Long designatedBy,
            String reason,
            String status,
            String createdAt,
            String finishedAt
    ) {
    }

    /** 补件视图。 */
    public record SupplementView(
            Long id,
            Integer round,
            String status,
            String reason,
            Long requestedBy,
            Long nodeInstanceId,
            String deadline,
            boolean overdue,
            String submittedAt,
            Long submittedBy,
            String submittedNote
    ) {
    }

    /** 抄送视图。 */
    public record CcView(
            Long userId,
            String userName,
            String source,
            String readAt
    ) {
    }

    /** 实例运行态总览（端到端验收的证据载体）。 */
    public record InstanceRuntimeView(
            Long instanceId,
            String bizNo,
            String instanceStatus,
            String subStatus,
            Integer currentNodeSeq,
            Long currentDeptId,
            Long ownerDeptId,
            Integer routingSeq,
            Integer routingCount,
            Integer supplementCount,
            GateView returnGate,
            GateView supplementGate,
            List<NodeInstanceView> nodes,
            List<TaskView> tasks,
            List<ThreadView> thread,
            List<RoutingView> routing,
            List<SupplementView> supplements,
            List<CcView> cc,
            boolean hasOpenAddSign
    ) {
    }

    // ================================================================ 列表（待办 / 已办 / 我发起）

    /** 列表项。 */
    public record TaskListItemView(
            Long taskId,
            Long instanceId,
            String bizNo,
            String formType,
            String category,
            Integer nodeSeq,
            String nodeName,
            Long assigneeId,
            Long originAssigneeId,
            String addSignType,
            String taskStatus,
            String taskStatusLabel,
            String opinion,
            String taskCreatedAt,
            String decidedAt,
            Long initiatorId,
            String initiatorName,
            Integer currentNodeSeq,
            String instanceStatus,
            String subStatus
    ) {
    }

    /** 分页结果（{@code page} 从 1 起）。 */
    public record PageResult<T>(
            List<T> items,
            long total,
            int page,
            int size
    ) {
    }
}
