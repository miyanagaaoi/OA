package com.oa.workflow.runtime.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseRequest;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.ApproverSnapshotCodec;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.FlowInstanceService;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.approver.domain.ApproverSnapshot.SnapshotNode;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionResult;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.GateView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.SupplementDeadlineView;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.domain.FlowLinkage;
import com.oa.workflow.runtime.domain.GateCounterPolicy;
import com.oa.workflow.runtime.domain.RuntimeEnums.AddSignType;
import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.RoutingAction;
import com.oa.workflow.runtime.domain.RuntimeEnums.SubStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import com.oa.workflow.runtime.domain.SupplementDeadlinePolicy;
import com.oa.workflow.runtime.domain.SupplementDeadlinePolicy.Deadline;
import com.oa.workflow.runtime.domain.WithdrawWindowPolicy;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import com.oa.workflow.runtime.infra.row.FlowCcRow;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import com.oa.workflow.runtime.infra.row.FlowRoutingRow;
import com.oa.workflow.runtime.infra.row.FlowSupplementRow;
import com.oa.workflow.runtime.infra.row.FlowTaskRow;
import com.oa.workflow.task.domain.AddSignPolicy;
import com.oa.workflow.task.domain.ApprovalOpinionPolicy;
import com.oa.workflow.task.domain.JumpPolicy;
import com.oa.workflow.task.domain.TaskDecisionPolicy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>流程引擎内核（2a.4 状态机 + 2a.5 决议）</b> —— 三层状态（实例 / 节点 / 任务）的**唯一**写入者。
 *
 * <h2>为什么收敛到一个类</h2>
 * <p>doc/prd-0.1.md 附录 B 原文：「**联动规则**见 7.2 表格（驳回、会签达标、或签通过、实例终态、跳过、
 * 补件请求与提交、流转与回退、上限拒绝）—— 这些规则必须在**引擎层统一实现，不允许各页面自行处理**」。
 * 因此本类是全部动作（{@link FlowAction}）的唯一执行者；规则本身来自**纯函数**：
 * {@link FlowLinkage}（§7.2 联动）、{@link TaskDecisionPolicy}（或签/会签/依次）、
 * {@link GateCounterPolicy}（Q6）、{@link SupplementDeadlinePolicy}（Q7）、{@link WithdrawWindowPolicy}（撤回窗口）、
 * {@link AddSignPolicy} / {@link JumpPolicy} / {@link ApprovalOpinionPolicy}（2a.5 准入）。
 * Controller / Service 层不含任何状态迁移判断。
 *
 * <h2>三层状态的权威</h2>
 * <ol>
 *   <li><b>实例</b>（{@code flow_instance}）：{@code status} / {@code sub_status} / {@code current_node_seq}
 *       / {@code current_dept_id} / 三个计数列；</li>
 *   <li><b>节点实例</b>（{@code flow_node_instance}）：一节点一条；协同组 / 流转承接以
 *       {@code node_key = seq:code:dept|0} 区分（doc/data-model.md §8.2）；候选人是**运行时权威**
 *       （{@code approver_ids_json}）；</li>
 *   <li><b>任务</b>（{@code flow_task}）：会签/或签同节点多任务；{@code add_sign_type IS NULL} 的是主任务
 *       （参与阈值计数），加签任务不参与。</li>
 * </ol>
 *
 * <h2>与 2a.2 / 2a.3 的衔接（复用，不重写）</h2>
 * <ul>
 *   <li>发起：{@code FlowInstanceService#create}（预检 + 锁版本 + 固化快照）不变；</li>
 *   <li>提交：<b>本类接管</b>（2a.3 只做 {@code draft → approving} 的单条迁移，TODO(2a.4) 要求同时
 *       生成节点实例、跳过命中跳过条件的节点、为首个节点产生待办）；</li>
 *   <li>重提：先 {@code FlowInstanceService#reparse}（重新解析快照与模板版本，旧快照进审计），
 *       再由本类的 {@link #submit} 建新一轮节点实例（REQ-FLOW-017）；</li>
 *   <li>快照 JSON 的键名契约**不被本类修改**：加签链落 {@code flow_node_instance.add_sign_chain_json}，
 *       流转/协同的部门变体落 {@code flow_node_instance.dept_id}。</li>
 * </ul>
 *
 * <h2>明确未实现（不静默）</h2>
 * <ul>
 *   <li>签名记录（{@code flow_signature}）与 {@code sign_policy} 的强制校验：属阶段 2b 电子签名，
 *       本类只在 {@code sys_thread.signature_id} 留空；</li>
 *   <li>站内信 / 邮件（{@code sys_message}）与超时催办：属 2b 消息与阶段 3 调度器，
 *       §7.2 的「通知发起人」联动由 {@link #notifyPlaceholder} 记 WARN 留痕；</li>
 *   <li>{@code on_supplement_timeout} 的自动通过 / 自动退回：属阶段 3，本类只算时限并披露。</li>
 * </ul>
 */
@Service
public class FlowEngineService {

    private static final Logger log = LoggerFactory.getLogger(FlowEngineService.class);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 「回退上一节点」的节点级上限（doc/enums.md §7：同一节点被回退 ≤2）。 */
    private static final int NODE_RETURN_LIMIT = 2;

    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeInstanceMapper nodeInstanceMapper;
    private final FlowTaskMapper taskMapper;
    private final FlowRuntimeMapper runtimeMapper;
    private final FlowRoutingMapper routingMapper;
    private final FlowGateService gateService;
    private final FlowThreadWriter threadWriter;
    private final WorkflowPermissionService permissionService;
    private final ApproverDirectory directory;
    private final AuditLogWriter auditLogWriter;
    private final FlowInstanceService instanceService;

    public FlowEngineService(FlowInstanceMapper instanceMapper,
                             FlowNodeInstanceMapper nodeInstanceMapper,
                             FlowTaskMapper taskMapper,
                             FlowRuntimeMapper runtimeMapper,
                             FlowRoutingMapper routingMapper,
                             FlowGateService gateService,
                             FlowThreadWriter threadWriter,
                             WorkflowPermissionService permissionService,
                             ApproverDirectory directory,
                             AuditLogWriter auditLogWriter,
                             FlowInstanceService instanceService) {
        this.instanceMapper = instanceMapper;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.taskMapper = taskMapper;
        this.runtimeMapper = runtimeMapper;
        this.routingMapper = routingMapper;
        this.gateService = gateService;
        this.threadWriter = threadWriter;
        this.permissionService = permissionService;
        this.directory = directory;
        this.auditLogWriter = auditLogWriter;
        this.instanceService = instanceService;
    }

    // ================================================================ 提交 / 重提 / 回到草稿

    /**
     * 提交（{@code draft → approving}）：建（或复位）**全部**节点实例，跳过命中跳过条件的节点，
     * 激活首个节点并为其候选人产生待办（2a.3 留下的 TODO(2a.4)）。
     *
     * <p><b>入口/引擎同源（2026-10-04 收敛 F）</b>：动作级闸门取
     * {@code FlowAction.SUBMIT.permission()}（{@code flow}）——与
     * {@code FlowRuntimeController#submit} 的入口闸门**逐字同参**。改前这里用
     * {@code requireInitiator}（放行 {@code flow} ∪ {@code admin:flow}），比控制器入口宽，
     * 导致「入口 ⊂ 引擎」的不对称：只持 {@code admin:flow} 的 {@code company_admin}
     * 能过引擎第一层、再被第二层身份判定拒掉，排障时看到的是「引擎 403」而不是入口 403。
     * <p>身份判定（{@code requireInitiatorOrAdmin}：发起人本人 ∪ 系统管理员）**一行未动** ——
     * 入口判权限、引擎判身份与状态机，分工不变（两层都在）。
     */
    @Transactional
    public InstanceView submit(Long instanceId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.SUBMIT.label(),
                FlowAction.SUBMIT.permission());
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, actor);
        InstanceStatus status = InstanceStatus.of(instance.getStatus())
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "未知的实例状态：" + instance.getStatus()));
        if (status != InstanceStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT,
                    "只有草稿状态的单据可以提交，当前状态：" + status.code()
                            + "（已驳回/已撤回请先调用 reopen 或 resubmit）");
        }
        ApproverSnapshot snapshot = readSnapshot(instance);
        List<SnapshotNode> nodes = orderedNodes(snapshot);
        if (nodes.isEmpty()) {
            throw new BizException(ErrorCode.CONFLICT, "审批人快照为空，无法提交；请先重新解析审批人快照");
        }
        // 乐观迁移：只有 draft 允许提交（并发提交时只有一条生效）
        int updated = instanceMapper.markSubmitted(instanceId, null);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "单据状态已被他人变更，请刷新后重试");
        }

        boolean activated = false;
        Integer firstSeq = null;
        for (SnapshotNode node : nodes) {
            FlowNodeInstanceRow nodeInstance =
                    ensureNodeInstanceForRound(instance, node, null, approverIds(node));
            taskMapper.closePendingByNodeInstance(nodeInstance.getId());
            if (Boolean.TRUE.equals(node.skipped())) {
                skipNodeInstance(instance, nodeInstance, node, null);
                continue;
            }
            if (!activated) {
                activateNodeInstance(instance, nodeInstance, null);
                activated = true;
                firstSeq = node.nodeSeq();
            }
        }
        if (!activated) {
            // 全部节点都被跳过：直接通过（理论上不会发生：⑤⑥ 不可跳过）
            instanceMapper.markFinished(instanceId, InstanceStatus.APPROVED.code());
            log.warn("实例 {} 的全部节点均被跳过，直接置为已通过", instanceId);
        } else {
            instanceMapper.updateProgress(instanceId, InstanceStatus.APPROVING.code(), null, firstSeq, null);
        }
        threadWriter.append(instanceId, null, actor, ThreadAction.SUBMIT,
                reason == null || reason.isBlank() ? "提交审批" : reason);
        auditLogWriter.appendAsCurrentUser("submit", "instance", instanceId, null,
                "{\"reason\":" + JsonText.write(reason == null ? "" : reason)
                        + ",\"firstNodeSeq\":" + firstSeq + "}", null, null);
        log.info("引擎提交：operator={} instanceId={} 首节点={} 节点实例数={}",
                actor.account(), instanceId, firstSeq, nodes.size());
        return FlowInstanceService.toView(requireInstance(instanceId));
    }

    /**
     * 驳回/撤回后回到草稿（清掉未完成节点与待决议任务；重提时会重建节点实例）。
     *
     * <p>动作级闸门 = {@code FlowAction.REOPEN.permission()}（{@code flow}），
     * 与 {@code FlowRuntimeController#reopen} 的入口闸门同源同参（F 项收敛，理由见 {@link #submit}）。
     */
    @Transactional
    public InstanceView reopen(Long instanceId) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.REOPEN.label(),
                FlowAction.REOPEN.permission());
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, actor);
        InstanceStatus status = InstanceStatus.of(instance.getStatus())
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "未知的实例状态：" + instance.getStatus()));
        if (!status.editableByInitiator()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "只有草稿/已驳回/已撤回可以回到草稿编辑，当前状态：" + status.code());
        }
        if (status == InstanceStatus.DRAFT) {
            return FlowInstanceService.toView(instance);
        }
        reopenInternal(instanceId, status);
        return FlowInstanceService.toView(requireInstance(instanceId));
    }

    /**
     * 重提（REQ-FLOW-017）：**重新解析**审批人快照与流程版本（按最新已发布模板），回到草稿，再提交。
     *
     * <p>复用 2a.3 的 {@code FlowInstanceService#reparse}：它负责锁版本、换快照、旧快照进审计，
     * 并在空候选人时拒绝（AC-11 不因重提而放宽）。
     *
     * <p>动作级闸门 = {@code FlowAction.SUBMIT.permission()}（{@code flow}），与
     * {@code FlowRuntimeController#resubmit} 的入口闸门同源同参（F 项收敛，理由见 {@link #submit}）。
     */
    @Transactional
    public InstanceView resubmit(Long instanceId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.SUBMIT.label(),
                FlowAction.SUBMIT.permission());
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, actor);
        InstanceStatus status = InstanceStatus.of(instance.getStatus())
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "未知的实例状态：" + instance.getStatus()));
        if (status != InstanceStatus.DRAFT) {
            if (!status.editableByInitiator()) {
                throw new BizException(ErrorCode.CONFLICT,
                        "只有草稿/已驳回/已撤回可以重新提交，当前状态：" + status.code());
            }
            reopenInternal(instanceId, status);
        }
        // 重新解析快照与模板版本（2a.3）
        instanceService.reparse(instanceId, new ReparseRequest(reason));
        // 提交（建立新节点实例）
        return submit(instanceId, reason == null || reason.isBlank() ? "驳回后重新提交" : reason);
    }

    // ================================================================ 通过 / 驳回 / 归档登记

    /**
     * 通过（2a.5）：或签任一人即通过；会签达阈值即通过；依次签只放行当前那一位。
     *
     * @param collabDeptIds ②「财务部复核」通过时勾选的协同部门（PRD §6.3 / §6.4；可为空）
     */
    @Transactional
    public ActionResult approve(Long taskId, String opinion, List<Long> collabDeptIds) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.APPROVE.label(),
                FlowAction.APPROVE.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(task.getNodeInstanceId());
        requireNotRegistrationNode(nodeInstance);
        String normalized = ApprovalOpinionPolicy.optionalOpinion(opinion);

        if (task.addSignTask()) {
            return approveAddSignTask(instance, nodeInstance, task, actor, normalized);
        }

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.AGREED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor, ThreadAction.APPROVE, normalized);
        auditLogWriter.appendAsCurrentUser("approve", "task", task.getId(), null,
                "{\"taskStatus\":\"agreed\"}", null, null);

        // ② 的主节点通过时，勾选的协同部门生成**并行子任务组**（不占主链编号）
        if (nodeInstance.getNodeSeq() != null && nodeInstance.getNodeSeq() == 2
                && nodeInstance.getDeptId() == null && collabDeptIds != null && !collabDeptIds.isEmpty()) {
            createCollabGroups(instance, nodeInstance, collabDeptIds, actor);
        }
        return evaluateNode(instance, nodeInstance, actor, task);
    }

    /** ⑦ 归档登记：**仅登记不审批**（不产生审批决议、不参与阈值判定）。 */
    @Transactional
    public ActionResult archiveRegister(Long taskId, String opinion) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.ARCHIVE_REGISTER.label(),
                FlowAction.ARCHIVE_REGISTER.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(task.getNodeInstanceId());
        if (nodeInstance.getDecisionMode() != null) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "节点「" + nodeInstance.getNodeName() + "」是审批节点（decision_mode="
                            + nodeInstance.getDecisionMode() + "），请使用「通过」；归档登记仅适用于 ⑦ 登记节点"
                            + "（错误码 " + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        String normalized = ApprovalOpinionPolicy.optionalOpinion(opinion);
        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.AGREED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        FlowLinkage.ArchiveRegisterCascade cascade = FlowLinkage.archiveRegistered();
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor, cascade.threadAction(),
                normalized == null ? "归档登记完成（仅登记不审批，不产生审批决议）" : normalized);
        taskMapper.closePendingByNodeInstance(nodeInstance.getId());
        nodeInstanceMapper.finish(nodeInstance.getId(), NodeStatus.APPROVED.code());
        auditLogWriter.appendAsCurrentUser("archive_register", "task", task.getId(), null,
                "{\"nodeStatus\":\"approved\",\"producesApprovalDecision\":false}", null, null);
        onNodeApproved(instance, nodeInstance, actor);
        return actionResult(FlowAction.ARCHIVE_REGISTER, instance, task, "归档登记完成（不产生审批决议）",
                null, null);
    }

    /** 驳回：意见 ≥5 字；一期去向固定「回到发起人」；同节点其余任务与其余节点实例级联关闭。 */
    @Transactional
    public ActionResult reject(Long taskId, String opinion) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.REJECT.label(),
                FlowAction.REJECT.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(task.getNodeInstanceId());
        requireNotRegistrationNode(nodeInstance);
        String normalized = ApprovalOpinionPolicy.requireRejectOpinion(opinion);

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.REJECTED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        FlowLinkage.RejectCascade cascade = FlowLinkage.nodeRejected();
        nodeInstanceMapper.finish(nodeInstance.getId(), cascade.nodeStatus().code());
        taskMapper.closePendingByNodeInstance(nodeInstance.getId());
        cancelLiveNodes(instance.getId(), nodeInstance.getId());
        taskMapper.closePendingByInstance(instance.getId());
        instanceMapper.markFinished(instance.getId(), cascade.instanceStatus().code());
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor, cascade.threadAction(), normalized);
        auditLogWriter.appendAsCurrentUser("reject", "task", task.getId(),
                "{\"instanceStatus\":\"" + InstanceStatus.APPROVING.code() + "\"}",
                "{\"instanceStatus\":\"" + InstanceStatus.REJECTED.code() + "\",\"nodeStatus\":\"rejected\"}",
                null, null);
        notifyPlaceholder(instance, "被驳回");
        log.info("驳回：instanceId={} 节点={} 操作人={} 其余节点实例已取消、其余任务已自动关闭（§7.2 第 1 行）",
                instance.getId(), nodeInstance.getNodeSeq(), actor.account());
        return actionResult(FlowAction.REJECT, instance, task,
                "已驳回并回到发起人（意见 " + ApprovalOpinionPolicy.charCount(normalized) + " 字）", null, null);
    }

    // ================================================================ 回退 / 流转 / 回到本部门 / 跳转

    /** 回退上一已完成节点（REQ-FLOW-021）：受 Q6 与「同一节点被回退 ≤2」双重约束。 */
    @Transactional
    public ActionResult rollback(Long taskId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.ROLLBACK.label(),
                FlowAction.ROLLBACK.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow current = requireNodeInstance(task.getNodeInstanceId());
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.ROLLBACK, reason);

        FlowNodeInstanceRow target = nodeInstanceMapper.selectPreviousApproved(instance.getId(),
                current.getNodeSeq());
        if (target == null) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "当前节点之前没有「已通过」的节点，无法回退（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        int returned = target.getReturnedCount() == null ? 0 : target.getReturnedCount();
        if (returned >= NODE_RETURN_LIMIT) {
            throw new BizException(ErrorCode.FLOW_NODE_RETURN_LIMIT_EXCEEDED,
                    "节点「" + target.getNodeName() + "」已被回退 " + returned + " 次（上限 " + NODE_RETURN_LIMIT
                            + "），请改用「驳回」或「终止」（错误码 "
                            + ErrorCode.FLOW_NODE_RETURN_LIMIT_EXCEEDED.getCode() + "）")
                    .withDetail("nodeInstanceId", target.getId())
                    .withDetail("returnedCount", returned);
        }
        // Q6：maxReturnCount 对照 flow_instance.routing_count（route + rollback）
        GateCounterPolicy.Budget budget = gateService.assertReturnBudget(instance, "流转/回退");

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.ROLLED_BACK.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        FlowLinkage.RollbackCascade cascade = FlowLinkage.rollbackApplied();
        nodeInstanceMapper.finish(current.getId(), cascade.currentNodeStatus().code());
        taskMapper.closePendingByNodeInstance(current.getId());
        nodeInstanceMapper.incrementReturnedCount(target.getId());
        nodeInstanceMapper.activate(target.getId());
        createTasksForNode(target);

        FlowRoutingRow routing = new FlowRoutingRow();
        routing.setInstanceId(instance.getId());
        routing.setSeq(nextRoutingSeq(instance));
        routing.setActionType(RoutingAction.ROLLBACK.code());
        routing.setFromDeptId(current.getDeptId());
        routing.setToDeptId(target.getDeptId());
        routing.setFromNodeSeq(current.getNodeSeq());
        routing.setToNodeSeq(target.getNodeSeq());
        routing.setDesignatedBy(actor.id());
        routing.setReason(normalized);
        routing.setStatus("processing");
        routingMapper.insertRouting(routing);
        instanceMapper.incrementRoutingCount(instance.getId());
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(), null,
                target.getNodeSeq(), target.getDeptId());
        threadWriter.append(instance.getId(), current.getId(), actor, ThreadAction.ROLLBACK, normalized);
        auditLogWriter.appendAsCurrentUser("rollback", "task", task.getId(),
                "{\"nodeSeq\":" + current.getNodeSeq() + "}",
                "{\"rollbackTo\":" + target.getNodeSeq() + ",\"routingCount\":"
                        + (used(instance.getRoutingCount()) + 1) + "}", null, null);
        return actionResult(FlowAction.ROLLBACK, instance, task,
                "已回退至节点 " + target.getNodeSeq() + "（" + target.getNodeName() + "）重审："
                        + budget.allowMessage("回退"),
                budget, null);
    }

    /** 流转：指定下一承接部门（禁止回流；计入 Q6 预算）。 */
    @Transactional
    public ActionResult route(Long taskId, Long toDeptId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.ROUTE.label(),
                FlowAction.ROUTE.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow current = requireNodeInstance(task.getNodeInstanceId());
        ApproverSnapshot snapshot = readSnapshot(instance);
        SnapshotNode node = snapshotNode(snapshot, current.getNodeSeq());
        if (!Boolean.TRUE.equals(node.allowRoute())) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "该节点未开启流转（flow_node.allow_route = false），已拒绝（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.ROUTE, reason);
        if (toDeptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流转必须指定承接部门（toDeptId）");
        }
        requireNoLoopBack(instance, toDeptId);
        GateCounterPolicy.Budget budget = gateService.assertReturnBudget(instance, "流转/回退");
        List<Long> approvers = leadersOf(toDeptId, "承接部门");

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.ROUTED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        FlowLinkage.RoutingCascade cascade = FlowLinkage.routingApplied();
        nodeInstanceMapper.finish(current.getId(), cascade.currentNodeStatus().code());
        taskMapper.closePendingByNodeInstance(current.getId());
        FlowNodeInstanceRow target = ensureNodeInstanceForRound(instance, node, toDeptId, approvers);
        activateNodeInstance(instance, target, toDeptId);

        FlowRoutingRow routing = new FlowRoutingRow();
        routing.setInstanceId(instance.getId());
        routing.setSeq(nextRoutingSeq(instance));
        routing.setActionType(RoutingAction.ROUTE.code());
        routing.setFromDeptId(current.getDeptId());
        routing.setToDeptId(toDeptId);
        routing.setFromNodeSeq(current.getNodeSeq());
        routing.setToNodeSeq(current.getNodeSeq());
        routing.setDesignatedBy(actor.id());
        routing.setReason(normalized);
        routing.setStatus("processing");
        routingMapper.insertRouting(routing);
        instanceMapper.incrementRoutingCount(instance.getId());
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(), null,
                current.getNodeSeq(), toDeptId);
        threadWriter.append(instance.getId(), current.getId(), actor, ThreadAction.ROUTE, normalized);
        auditLogWriter.appendAsCurrentUser("route", "task", task.getId(), null,
                "{\"toDeptId\":" + toDeptId + ",\"routingCount\":" + (used(instance.getRoutingCount()) + 1) + "}",
                null, null);
        return actionResult(FlowAction.ROUTE, instance, task,
                "已流转至部门 " + toDeptId + "：" + budget.allowMessage("流转"), budget, null);
    }

    /** 回到本部门（REQ-FLOW-022）：不计入 Q6 预算，同一部门连续 ≤2。 */
    @Transactional
    public ActionResult backHome(Long taskId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.BACK_HOME.label(),
                FlowAction.BACK_HOME.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow current = requireNodeInstance(task.getNodeInstanceId());
        ApproverSnapshot snapshot = readSnapshot(instance);
        SnapshotNode node = snapshotNode(snapshot, current.getNodeSeq());
        if (!Boolean.TRUE.equals(node.allowRoute())) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "该节点未开启流转/回退（flow_node.allow_route = false），已拒绝（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.BACK_HOME, reason);
        Long toDeptId = current.getDeptId() != null ? current.getDeptId() : instance.getCurrentDeptId();
        if (toDeptId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "「回到本部门」需要本节点有承接部门（本节点为模板固定节点，无部门归属）");
        }
        int trailing = trailingBackHome(instance.getId(), toDeptId);
        if (trailing >= 2) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "部门 " + toDeptId + " 已连续「回到本部门」" + trailing + " 次（上限 2），已拒绝"
                            + "（REQ-FLOW-022，错误码 " + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        List<Long> approvers = leadersOf(toDeptId, "本部门");

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.ROUTED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        // 不计入 routing_count（doc/enums.md §7 / data-model.md §8.2）
        FlowLinkage.RoutingCascade cascade = FlowLinkage.backHomeApplied();
        nodeInstanceMapper.finish(current.getId(), cascade.currentNodeStatus().code());
        taskMapper.closePendingByNodeInstance(current.getId());
        FlowNodeInstanceRow target = ensureNodeInstanceForRound(instance, node, toDeptId, approvers);
        activateNodeInstance(instance, target, toDeptId);

        FlowRoutingRow routing = new FlowRoutingRow();
        routing.setInstanceId(instance.getId());
        routing.setSeq(nextRoutingSeq(instance));
        routing.setActionType(RoutingAction.BACK_HOME.code());
        routing.setFromDeptId(current.getDeptId());
        routing.setToDeptId(toDeptId);
        routing.setFromNodeSeq(current.getNodeSeq());
        routing.setToNodeSeq(current.getNodeSeq());
        routing.setDesignatedBy(actor.id());
        routing.setReason(normalized);
        routing.setStatus("processing");
        routingMapper.insertRouting(routing);
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(), null,
                current.getNodeSeq(), toDeptId);
        threadWriter.append(instance.getId(), current.getId(), actor, ThreadAction.BACK_HOME, normalized);
        auditLogWriter.appendAsCurrentUser("back_home", "task", task.getId(), null,
                "{\"toDeptId\":" + toDeptId + ",\"routingCount\":\"" + instance.getRoutingCount()
                        + "\"（不计入）}", null, null);
        return actionResult(FlowAction.BACK_HOME, instance, task,
                "已收束回本部门 " + toDeptId + "（不计入流转次数）", gateService.returnBudget(instance), null);
    }

    /** 自由跳转（REQ-FLOW-004 / AC-46）：默认关闭；必须填写原因；轨迹落 {@code skip}。 */
    @Transactional
    public ActionResult jump(Long taskId, Integer targetSeq, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.JUMP.label(),
                FlowAction.JUMP.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow current = requireNodeInstance(task.getNodeInstanceId());
        ApproverSnapshot snapshot = readSnapshot(instance);
        SnapshotNode node = snapshotNode(snapshot, current.getNodeSeq());
        JumpPolicy.Plan plan = JumpPolicy.plan(node.allowJump(), current.getNodeSeq(), trunkSeqs(snapshot),
                targetSeq, reason);

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.ROUTED.code(), plan.reason());
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        for (Integer skippedSeq : plan.skippedSeqs()) {
            Long deptId = skippedSeq.equals(current.getNodeSeq()) ? current.getDeptId() : null;
            SnapshotNode skippedNode = snapshotNode(snapshot, skippedSeq);
            FlowNodeInstanceRow row = nodeInstanceMapper.selectByInstanceAndKey(instance.getId(),
                    nodeKey(skippedSeq, skippedNode.nodeCode(), deptId));
            if (row == null) {
                row = ensureNodeInstanceForRound(instance, skippedNode, deptId, List.of());
            }
            skipNodeInstance(instance, row, skippedNode, plan.targetSeq());
        }
        SnapshotNode targetNode = snapshotNode(snapshot, plan.targetSeq());
        FlowNodeInstanceRow target = ensureNodeInstanceForRound(instance, targetNode, current.getDeptId(),
                approverIds(targetNode));
        activateNodeInstance(instance, target, current.getDeptId());
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(), null,
                plan.targetSeq(), current.getDeptId());
        auditLogWriter.appendAsCurrentUser("jump", "task", task.getId(), null,
                JumpPolicy.auditPayload(plan, current.getNodeSeq()), null, null);
        return actionResult(FlowAction.JUMP, instance, task,
                "已跳转至节点 " + plan.targetSeq() + "（跳过 " + plan.skippedSeqs() + "）",
                gateService.returnBudget(instance), null);
    }

    // ================================================================ 补件

    /** 请求补件（REQ-FLOW-023）：受 Q6 全单补件预算与「同节点 ≤1」双重约束；Q7 落应完成时间。 */
    @Transactional
    public ActionResult supplementRequest(Long taskId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.SUPPLEMENT_REQUEST.label(),
                FlowAction.SUPPLEMENT_REQUEST.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(task.getNodeInstanceId());
        requireNotRegistrationNode(nodeInstance);
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.SUPPLEMENT_REQUEST, reason);

        if (Boolean.TRUE.equals(nodeInstance.getSupplementRequested())) {
            throw new BizException(ErrorCode.FLOW_SUPPLEMENT_PER_NODE_LIMIT,
                    "节点「" + nodeInstance.getNodeName() + "」已请求过补件（同节点上限 1 次，REQ-FLOW-023），"
                            + "请改用「通过」「驳回」或「终止」（错误码 "
                            + ErrorCode.FLOW_SUPPLEMENT_PER_NODE_LIMIT.getCode() + "）");
        }
        // Q6：全单补件预算（maxSupplementCount 对照 flow_instance.supplement_count）
        GateCounterPolicy.Budget budget = gateService.assertSupplementBudget(instance);

        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.SUPPLEMENT_REQUESTED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        FlowLinkage.SupplementRequestCascade cascade = FlowLinkage.supplementRequested();
        nodeInstanceMapper.markSupplementWaiting(nodeInstance.getId());
        Deadline deadline = gateService.deadlineFor(instance, LocalDateTime.now());
        int round = used(instance.getSupplementCount()) + 1;

        FlowSupplementRow supplement = new FlowSupplementRow();
        supplement.setInstanceId(instance.getId());
        supplement.setNodeInstanceId(nodeInstance.getId());
        supplement.setRequestedBy(actor.id());
        supplement.setReason(normalized);
        supplement.setSupplementRound(round);
        supplement.setDeadline(deadline.dueAt());
        supplement.setStatus("pending");
        runtimeMapper.insertSupplement(supplement);
        instanceMapper.incrementSupplementCount(instance.getId());
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(),
                cascade.instanceSubStatus() == null ? null : cascade.instanceSubStatus().code(),
                nodeInstance.getNodeSeq(), nodeInstance.getDeptId());
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor,
                ThreadAction.SUPPLEMENT_REQUEST, normalized);
        auditLogWriter.appendAsCurrentUser("supplement_request", "task", task.getId(), null,
                "{\"round\":" + round + ",\"deadline\":" + JsonText.write(
                        deadline.dueAt() == null ? null : deadline.dueAt().format(TIME)) + "}", null, null);
        notifyPlaceholder(instance, "待补件");
        log.info("请求补件：instanceId={} 节点={} 第 {} 轮 应完成时间={}（Q7 已就位；超时策略执行属阶段 3）",
                instance.getId(), nodeInstance.getNodeSeq(), round, deadline.dueAt());
        return actionResult(FlowAction.SUPPLEMENT_REQUEST, instance, task,
                "已进入待补件（第 " + round + " 轮）：" + budget.allowMessage("补件"), budget,
                deadlineView(instance, deadline));
    }

    /** 提交补件（仅发起人）：只补附件与备注，主字段只读；补完回到请求补件的审批人。 */
    @Transactional
    public ActionResult supplementSubmit(Long instanceId, String note) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.SUPPLEMENT_SUBMIT.label(),
                FlowAction.SUPPLEMENT_SUBMIT.permission());
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, actor);
        if (!SubStatus.PENDING_SUPPLEMENT.code().equals(instance.getSubStatus())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "单据当前不在待补件状态，无法提交补件（当前 sub_status=" + instance.getSubStatus() + "）");
        }
        FlowSupplementRow supplement = runtimeMapper.selectPendingSupplement(instanceId);
        if (supplement == null) {
            throw new BizException(ErrorCode.CONFLICT, "未找到待处理的补件请求");
        }
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(supplement.getNodeInstanceId());
        int updated = runtimeMapper.markSupplementSubmitted(supplement.getId(), actor.id(),
                ApprovalOpinionPolicy.optionalOpinion(note));
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "补件请求已被处理，请刷新后重试");
        }
        FlowLinkage.SupplementSubmitCascade cascade = FlowLinkage.supplementSubmitted();
        nodeInstanceMapper.markSupplementResolved(nodeInstance.getId());
        // 任务回到**请求补件的审批人**（不复活旧任务，重新产生 pending 主任务；enums.md §6）
        createTask(nodeInstance, supplement.getRequestedBy(), null, null, null);
        instanceMapper.updateProgress(instanceId, InstanceStatus.APPROVING.code(),
                cascade.instanceSubStatus() == null ? null : cascade.instanceSubStatus().code(),
                nodeInstance.getNodeSeq(), nodeInstance.getDeptId());
        threadWriter.append(instanceId, nodeInstance.getId(), actor, ThreadAction.SUPPLEMENT_SUBMIT,
                note == null || note.isBlank() ? "已提交补件材料" : note);
        auditLogWriter.appendAsCurrentUser("supplement_submit", "instance", instanceId, null,
                "{\"round\":" + supplement.getSupplementRound() + "}", null, null);
        SupplementDeadlineView deadlineView = deadlineView(instance,
                new Deadline(supplement.getDeadline(), null, null, 0, true, "已落库的应完成时间"));
        boolean overdue = SupplementDeadlinePolicy.timedOut(supplement.getDeadline(), LocalDateTime.now());
        return actionResult(FlowAction.SUPPLEMENT_SUBMIT, instance, null,
                "补件已提交（第 " + supplement.getSupplementRound() + " 轮）"
                        + (overdue ? "；**已超时应完成时间**（超时策略执行属阶段 3）" : ""),
                gateService.supplementBudget(instance), deadlineView);
    }

    // ================================================================ 加签

    /** 加签（前/后）：<b>不改快照</b>，链落 {@code flow_node_instance.add_sign_chain_json}。 */
    @Transactional
    public ActionResult addSign(Long taskId, String addSignType, Long delegateUserId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.ADD_SIGN.label(),
                FlowAction.ADD_SIGN.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(task.getNodeInstanceId());
        SnapshotNode node = snapshotNode(readSnapshot(instance), nodeInstance.getNodeSeq());
        requireNotRegistrationNode(nodeInstance);
        AddSignPolicy.assertAllowed(node.allowAddSign(), actor.id(), delegateUserId);
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.ADD_SIGN, reason);
        AddSignType type = AddSignPolicy.parseType(addSignType);
        requireActiveUser(delegateUserId, "加签人");

        String chain = nodeInstance.getAddSignChainJson();
        String phase = type == AddSignType.PRE ? AddSignPolicy.PHASE_AWAITING_DELEGATE
                : AddSignPolicy.PHASE_AWAITING_OWNER;
        chain = AddSignPolicy.append(chain, type, actor.id(), delegateUserId, null, phase, normalized);
        int order = AddSignPolicy.read(chain).size();

        TaskStatus ownerStatus = type == AddSignType.PRE ? TaskStatus.ADDED_SIGN : TaskStatus.AGREED;
        int updated = taskMapper.updateDecision(task.getId(), ownerStatus.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        FlowTaskRow delegateTask = createTask(nodeInstance, delegateUserId, type.code(), actor.id(), null);
        chain = AddSignPolicy.bindTask(chain, order, delegateTask.getId());
        nodeInstanceMapper.updateAddSignChain(nodeInstance.getId(), chain);
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor, ThreadAction.ADD_SIGN,
                type.label() + " → " + delegateUserId + "：" + normalized);
        auditLogWriter.appendAsCurrentUser("add_sign", "task", task.getId(), null,
                "{\"type\":" + JsonText.write(type.code()) + ",\"delegateTo\":" + delegateUserId
                        + ",\"order\":" + order + "}", null, null);
        return actionResult(FlowAction.ADD_SIGN, instance, task,
                type.label() + "已提交给 " + delegateUserId + "（加签链第 " + order + " 条）", null, null);
    }

    /** 加签人通过：前加签回到原审批人；后加签闭环后再判定节点决议。 */
    private ActionResult approveAddSignTask(FlowInstanceRow instance, FlowNodeInstanceRow nodeInstance,
                                            FlowTaskRow task, CurrentUser actor, String opinion) {
        String normalized = ApprovalOpinionPolicy.requireOpinion("加签", opinion);
        int updated = taskMapper.updateDecision(task.getId(), TaskStatus.AGREED.code(), normalized);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        String chain = AddSignPolicy.close(nodeInstance.getAddSignChainJson(), task.getId());
        nodeInstanceMapper.updateAddSignChain(nodeInstance.getId(), chain);
        AddSignType type = AddSignType.of(task.getAddSignType()).orElse(AddSignType.PRE);
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor, ThreadAction.APPROVE,
                "加签（" + type.label() + "）：" + normalized);
        auditLogWriter.appendAsCurrentUser("approve", "task", task.getId(), null,
                "{\"addSignType\":" + JsonText.write(type.code()) + ",\"taskStatus\":\"agreed\"}", null, null);
        if (type == AddSignType.PRE) {
            // 前加签：审完回到本人（重新产生 pending 主任务，不复活旧任务）
            createTask(nodeInstance, task.getDelegateFrom(), null, null, null);
            return actionResult(FlowAction.APPROVE, instance, task,
                    "前加签完成，已回到原审批人 " + task.getDelegateFrom() + " 继续处理", null, null);
        }
        // 后加签：本人那一票已计入，现在判定节点决议
        return evaluateNode(instance, nodeInstance, actor, task);
    }

    // ================================================================ 转办 / 改派

    /** 转办（REQ-FLOW-018）：本人任务转他人，原审批人失去该任务（轨迹 + 审计留痕）。 */
    @Transactional
    public ActionResult transfer(Long taskId, Long toUserId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.TRANSFER.label(),
                FlowAction.TRANSFER.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireAssignee(task, actor);
        requireActionable(instance, task);
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.TRANSFER, reason);
        if (toUserId == null || toUserId.equals(actor.id())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转办对象不能为空，也不能是本人");
        }
        requireActiveUser(toUserId, "转办对象");
        return handover(instance, task, actor, toUserId, normalized, FlowAction.TRANSFER,
                TaskStatus.TRANSFERRED);
    }

    /** 改派（REQ-FLOW-019 / AC-52）：**仅系统管理员**，必须填写原因，轨迹与审计均留痕。 */
    @Transactional
    public ActionResult reassign(Long taskId, Long toUserId, String reason) {
        CurrentUser actor = permissionService.requireSuperAdmin(FlowAction.REASSIGN.label());
        permissionService.requirePermission(FlowAction.REASSIGN.label(), FlowAction.REASSIGN.permission());
        FlowTaskRow task = requireTask(taskId);
        FlowInstanceRow instance = requireInstance(task.getInstanceId());
        requireActionable(instance, task);
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.REASSIGN, reason);
        if (toUserId == null || toUserId.equals(task.getAssigneeId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "被改派人不能为空，也不能是当前处理人");
        }
        requireActiveUser(toUserId, "被改派人");
        return handover(instance, task, actor, toUserId, normalized, FlowAction.REASSIGN,
                TaskStatus.REASSIGNED);
    }

    private ActionResult handover(FlowInstanceRow instance, FlowTaskRow task, CurrentUser actor,
                                  Long toUserId, String reason, FlowAction action, TaskStatus terminal) {
        FlowNodeInstanceRow nodeInstance = requireNodeInstance(task.getNodeInstanceId());
        int updated = taskMapper.updateHandover(task.getId(), terminal.code(), reason);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "任务已被他人处理，请刷新后重试");
        }
        // 原任务 → 终态（transferred / reassigned）；受让人另起 pending 任务，
        // origin_assignee_id 记录**转办/改派前的原处理人**（doc/data-model.md §5.3）
        createHandoverTask(nodeInstance, toUserId, task.getAssigneeId());
        threadWriter.append(instance.getId(), nodeInstance.getId(), actor, action.threadAction(),
                action.label() + " → " + toUserId + "：" + reason);
        auditLogWriter.appendAsCurrentUser(action.code(), "task", task.getId(),
                "{\"assigneeId\":" + task.getAssigneeId() + "}",
                "{\"assigneeId\":" + toUserId + ",\"reason\":" + JsonText.write(reason) + "}", null, null);
        return actionResult(action, instance, task,
                action.label() + "完成：处理人 " + task.getAssigneeId() + " → " + toUserId, null, null);
    }

    // ================================================================ 撤回 / 终止 / 抄送

    /**
     * 撤回（REQ-FLOW-009）：仅发起人、仅撤回窗口内（窗口口径见下）；{@code withdrawn} 为到达态，立即回草稿。
     *
     * <p><b>窗口判据见 {@link #withdrawAllowed}（2026-10-04 修正 + 配置化）</b>：看「节点②是否已通过 /
     * ②之后的节点是否被推进过」，**不再**看「②之后的节点是否被取消」——
     * 后者因 {@link #submit} 一次性物化全部 7 个节点实例（全 {@code pending}）而恒为「已越过②」，
     * 使撤回在提交后永远失败。{@code pending}（未开始）不构成越界的证据。
     *
     * <p><b>窗口口径是模板级配置项</b>（{@code flow_template.withdraw_window}，doc/templates.md §1.8）：
     * 默认 {@code until_finance_approved}（②通过前，含②审批中 = REQ-FLOW-009 口径 = 历史行为），
     * 可配 {@code until_finance_started}（②开始前 = AC-16 严格口径）。取值按**实例锁定的模板版本**读
     * （{@link FlowGateService#withdrawWindowOf}，与 Q6/Q7 同一条取数路径），因此改模板不影响在途单据（AC-09）。
     *
     * <p><b>这是第二层（引擎兜底）</b>：第一层是 {@code FlowRuntimeController#withdraw} 的入口闸门，
     * 两处都取同一权限码 {@code FlowAction.WITHDRAW.permission()}（{@code flow:task:withdraw}），
     * 但**各管一件事**：
     * <ul>
     *   <li>入口层只判权限（无该权限码 → 403，不进引擎）；</li>
     *   <li>本层判**身份**（{@code instance.initiatorId} 本人，或系统管理员）、状态机（终态拒绝）与
     *       时间窗（{@code withdrawAllowed}：按锁定版本的撤回窗口）—— 删掉本层即安全回归：
     *       「有 {@code flow:task:withdraw} 但不是发起人」会拿到别人的单。</li>
     * </ul>
     */
    @Transactional
    public InstanceView withdraw(Long instanceId, String reason) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.WITHDRAW.label(),
                FlowAction.WITHDRAW.permission());
        FlowInstanceRow instance = requireInstance(instanceId);
        if (!actor.id().equals(instance.getInitiatorId()) && !permissionService.isSuperAdmin(actor)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有发起人本人（或系统管理员）可以撤回该单据");
        }
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.WITHDRAW, reason);
        InstanceStatus status = InstanceStatus.of(instance.getStatus())
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "未知的实例状态：" + instance.getStatus()));
        if (status == InstanceStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT, "草稿状态的单据无需撤回");
        }
        // 终态不可撤回（approved 的顺序本就被 withdrawAllowed 的「②已通过」挡住，
        // 这里补的是 terminated：`cancelLiveNodes` 会把全部节点置 cancelled，
        // 若只看节点状态，「终止后撤回→回草稿→重提」会绕过 REQ-FLOW-010「终止后不可再提交」）。
        if (status == InstanceStatus.APPROVED || status == InstanceStatus.TERMINATED) {
            throw new BizException(ErrorCode.CONFLICT,
                    "单据已处于终态（" + status.code() + "），不可撤回（REQ-FLOW-010 / AC-49）");
        }
        WithdrawWindow window = effectiveWindowOf(instance);
        if (!withdrawAllowed(instance, window)) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "撤回仅限「财务部复核（节点②）」通过之前（REQ-FLOW-009），当前流程已越过②，已拒绝"
                            + "（本单锁定版本的撤回窗口口径：" + window.code() + " = " + window.label()
                            + "；错误码 " + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        FlowLinkage.TerminalCascade cascade = FlowLinkage.instanceTerminal(InstanceStatus.WITHDRAWN);
        threadWriter.append(instanceId, null, actor, ThreadAction.WITHDRAW, normalized);
        cancelLiveNodes(instanceId, null);
        taskMapper.closePendingByInstance(instanceId);
        runtimeMapper.cancelPendingSupplements(instanceId);
        instanceMapper.updateProgress(instanceId, InstanceStatus.DRAFT.code(), null, null, null);
        auditLogWriter.appendAsCurrentUser("withdraw", "instance", instanceId,
                "{\"status\":\"" + status.code() + "\"}",
                "{\"status\":\"draft\",\"withdrawnArrival\":true,\"openNodes\":\""
                        + cascade.openNodeInstances().code() + "\"}", null, null);
        notifyPlaceholder(instance, "被撤回");
        return FlowInstanceService.toView(requireInstance(instanceId));
    }

    /**
     * 终止（REQ-FLOW-010 / AC-49）：仅系统管理员与集团分管领导，必填原因，终态不可再提交。
     *
     * <p><b>真源口径（2026-10-04 已对齐，「种子与 PRD 不一致」的遗留已消除）</b>：
     * <ul>
     *   <li>{@code doc/prd-0.1.md} AC-49（第 655 行）：「<b>系统管理员与集团分管领导</b>可终止（必填原因），
     *       其他角色无入口」；附录A 权限矩阵（第 764 行）「终止流程」行同口径（其余 6 个内置角色均为 {@code -}）；</li>
     *   <li>种子侧：{@code tools/gen-permission-seed.js} 的 {@code GROUP_LEADER_TERMINATE} 把
     *       {@code flow:task:terminate} 授给 {@code group_leader}（生成物
     *       {@code oa-deploy/sql/04-permissions.sql} → {@code db/migration/V4__permissions.sql}），
     *       并由 {@code check-permission-seed.js} 断言「持有人恰为 {admin, group_leader}」——
     *       AC-49 的「集团分管领导可终止」不再只靠本类的角色兜底。</li>
     * </ul>
     *
     * <p>放行条件 = 系统管理员 ∪ 持有 {@code group_leader} 角色 ∪ 持有 {@code flow:task:terminate} 权限。
     * 三者是**并集**：后台「角色与权限」界面若调整了授权，group_leader 仍按 AC-49 角色口径放行；
     * 而任何不持有该角色/权限的账号（含 {@code company_admin}，REQ-ADMIN-003）一律 403。
     *
     * <p><b>这是第二层（引擎兜底）</b>：第一层在 {@code FlowRuntimeController#terminate} 的入口闸门
     * （{@code WorkflowPermissionService#requireTerminate}）。两层共用判据
     * {@link FlowConfigPermission#isTerminateSubject}（单一口径），但**务必都在** ——
     * 本层兜的是「绕过控制器直调服务」以及「入口闸门被后续改动误删」，删掉本层即安全回归。
     */
    @Transactional
    public InstanceView terminate(Long instanceId, String reason) {
        CurrentUser actor = permissionService.requirePrincipal();
        if (!FlowConfigPermission.isTerminateSubject(permissionService.isSuperAdmin(actor),
                permissionService.permissionCodes(actor), permissionService.hasRole(actor, "group_leader"))) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "「终止流程」仅系统管理员与集团分管领导可执行（AC-49 / REQ-FLOW-010）");
        }
        FlowInstanceRow instance = requireInstance(instanceId);
        String normalized = ApprovalOpinionPolicy.requireReason(FlowAction.TERMINATE, reason);
        InstanceStatus status = InstanceStatus.of(instance.getStatus())
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "未知的实例状态：" + instance.getStatus()));
        if (status == InstanceStatus.APPROVED || status == InstanceStatus.TERMINATED) {
            throw new BizException(ErrorCode.CONFLICT, "单据已处于终态（" + status.code() + "），不可再终止");
        }
        FlowLinkage.TerminalCascade cascade = FlowLinkage.instanceTerminal(InstanceStatus.TERMINATED);
        threadWriter.append(instanceId, null, actor, ThreadAction.TERMINATE, normalized);
        cancelLiveNodes(instanceId, null);
        taskMapper.closePendingByInstance(instanceId);
        runtimeMapper.cancelPendingSupplements(instanceId);
        instanceMapper.markFinished(instanceId, InstanceStatus.TERMINATED.code());
        auditLogWriter.appendAsCurrentUser("terminate", "instance", instanceId,
                "{\"status\":\"" + status.code() + "\"}",
                "{\"status\":\"terminated\",\"openNodes\":\"" + cascade.openNodeInstances().code() + "\"}",
                null, null);
        notifyPlaceholder(instance, "已终止");
        return FlowInstanceService.toView(requireInstance(instanceId));
    }

    /**
     * 抄送登记（PRD §6.7 REQ-MSG-003）：只读可见、**不产生待办、不产生审批决议**。
     *
     * <p><b>这是第二层（引擎兜底）</b>：第一层是 {@code FlowRuntimeController#cc} 的入口闸门，
     * 两处都取同一权限码 {@code FlowAction.CC.permission()}（{@code flow}）；
     * 本层另外判**发起人身份**（{@code requireInitiatorOrAdmin}）—— 入口层放行只代表
     * 「该账号有资格抄送」，谁能抄这张单仍由本层按 {@code instance.initiatorId} 判。
     */
    @Transactional
    public Map<String, Object> addCc(Long instanceId, List<Long> userIds) {
        CurrentUser actor = permissionService.requirePermission(FlowAction.CC.label(),
                FlowAction.CC.permission());
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, actor);
        InstanceStatus status = InstanceStatus.of(instance.getStatus())
                .orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "未知的实例状态：" + instance.getStatus()));
        if (status.terminal()) {
            throw new BizException(ErrorCode.CONFLICT, "单据已处于终态，不能再增加抄送人");
        }
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抄送人不能为空");
        }
        FlowLinkage.CcCascade cascade = FlowLinkage.ccRegistered();
        List<Long> added = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (userId == null) {
                continue;
            }
            Optional<Candidate> candidate = directory.user(userId);
            if (candidate.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "抄送人不存在或已离职/停用：userId=" + userId);
            }
            FlowCcRow row = new FlowCcRow();
            row.setInstanceId(instanceId);
            row.setUserId(userId);
            row.setSource("initiator");
            runtimeMapper.insertCc(row);
            threadWriter.append(instanceId, null, actor, cascade.threadAction(),
                    "抄送 " + candidate.get().name() + "（userId=" + userId + "）；抄送不产生待办、不参与决议");
            added.add(userId);
        }
        auditLogWriter.appendAsCurrentUser("cc", "instance", instanceId, null,
                "{\"ccUserIds\":" + JsonText.write(added) + "}", null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("ccUserIds", added);
        result.put("producesTask", cascade.producesTask());
        result.put("producesDecision", cascade.producesDecision());
        result.put("note", "抄送人只读可见，不产生待办、不参与审批决议（enums.md §8）");
        return result;
    }

    // ================================================================ 节点决议与推进（§7.2 联动执行）

    /** 判定当前节点实例的决议，并执行 §7.2 的通过/等待联动。 */
    private ActionResult evaluateNode(FlowInstanceRow instance, FlowNodeInstanceRow nodeInstance,
                                      CurrentUser actor, FlowTaskRow sourceTask) {
        if (AddSignPolicy.hasOpen(nodeInstance.getAddSignChainJson())) {
            log.info("节点 {} 的加签链未闭环，暂缓决议判定（instanceId={}）",
                    nodeInstance.getId(), instance.getId());
            return actionResult(FlowAction.APPROVE, instance, sourceTask,
                    "加签链未闭环，等待加签人处理", null, null);
        }
        List<Long> candidates = readIds(nodeInstance.getApproverIdsJson());
        // 只数**本轮**的主任务：回退重审时上一轮的同意票不得沿用（见 mapper 的 round 口径说明）
        List<FlowTaskRow> primary = taskMapper.selectRoundPrimaryByNodeInstance(nodeInstance.getId());
        Map<Long, TaskStatus> statuses = new LinkedHashMap<>();
        for (FlowTaskRow row : primary) {
            TaskStatus status = TaskStatus.of(row.getStatus()).orElse(null);
            if (status != null && status != TaskStatus.PENDING) {
                statuses.put(row.getAssigneeId(), status);
            }
        }
        DecisionMode mode = DecisionMode.of(nodeInstance.getDecisionMode()).orElse(null);
        TaskDecisionPolicy.Decision decision = TaskDecisionPolicy.evaluate(mode, nodeInstance.getPassThreshold(),
                candidates, statuses);

        if (decision.outcome() == TaskDecisionPolicy.Outcome.REJECTED) {
            // 会签/协同任一人驳回：走统一驳回级联（本路径仅在加签闭环后可能到达）
            FlowLinkage.RejectCascade cascade = FlowLinkage.nodeRejected();
            nodeInstanceMapper.finish(nodeInstance.getId(), cascade.nodeStatus().code());
            taskMapper.closePendingByNodeInstance(nodeInstance.getId());
            cancelLiveNodes(instance.getId(), nodeInstance.getId());
            instanceMapper.markFinished(instance.getId(), cascade.instanceStatus().code());
            return actionResult(FlowAction.REJECT, instance, sourceTask,
                    "节点驳回：" + decision.description(), null, null);
        }
        if (decision.outcome() == TaskDecisionPolicy.Outcome.PENDING) {
            if (mode == DecisionMode.SEQUENCE && decision.nextApprover() != null
                    && taskMapper.selectPendingPrimaryByInstanceAndAssignee(instance.getId(),
                            decision.nextApprover()) == null) {
                createTask(nodeInstance, decision.nextApprover(), null, null, null);
            }
            return actionResult(FlowAction.APPROVE, instance, sourceTask, decision.description(), null, null);
        }
        // 节点通过：其余待处理任务自动关闭（AC-13）
        taskMapper.closePendingByNodeInstance(nodeInstance.getId());
        nodeInstanceMapper.finish(nodeInstance.getId(), NodeStatus.APPROVED.code());
        onNodeApproved(instance, nodeInstance, actor);
        return actionResult(FlowAction.APPROVE, instance, sourceTask,
                "节点「" + nodeInstance.getNodeName() + "」已通过：" + decision.description(), null, null);
    }

    /** 节点通过后的统一联动：收束流转记录 → 回退回归 → 推进下一节点 / 实例通过。 */
    private void onNodeApproved(FlowInstanceRow instance, FlowNodeInstanceRow nodeInstance, CurrentUser actor) {
        finishProcessingRoutings(instance.getId(), nodeInstance.getNodeSeq());
        if (resumeRollbackIfAny(instance, nodeInstance)) {
            return;
        }
        advance(instance, nodeInstance, actor);
    }

    /** 推进：同序号还有活动节点（协同组/流转承接）则等待；否则激活下一个未跳过节点或结束实例。 */
    private void advance(FlowInstanceRow instance, FlowNodeInstanceRow nodeInstance, CurrentUser actor) {
        if (nodeInstance.getNodeSeq() != null
                && nodeInstanceMapper.countLiveAtSeq(instance.getId(), nodeInstance.getNodeSeq()) > 0) {
            log.info("实例 {} 的序号 {} 仍有活动节点（协同组/流转承接），暂不推进主干",
                    instance.getId(), nodeInstance.getNodeSeq());
            return;
        }
        ApproverSnapshot snapshot = readSnapshot(instance);
        for (SnapshotNode next : orderedNodes(snapshot)) {
            if (next.nodeSeq() == null || next.nodeSeq() <= nodeInstance.getNodeSeq()) {
                continue;
            }
            FlowNodeInstanceRow row = nodeInstanceMapper.selectByInstanceAndKey(instance.getId(),
                    nodeKey(next.nodeSeq(), next.nodeCode(), null));
            if (Boolean.TRUE.equals(next.skipped())) {
                if (row == null) {
                    row = ensureNodeInstanceForRound(instance, next, null, approverIds(next));
                }
                skipNodeInstance(instance, row, next, null);
                continue;
            }
            if (row == null) {
                row = ensureNodeInstanceForRound(instance, next, null, approverIds(next));
            }
            if (NodeStatus.APPROVED.code().equals(row.getStatus())
                    || NodeStatus.SKIPPED.code().equals(row.getStatus())) {
                continue;
            }
            activateNodeInstance(instance, row, null);
            return;
        }
        // 没有下一个节点：实例通过（含 ⑦ 登记完成）
        taskMapper.closePendingByInstance(instance.getId());
        instanceMapper.markFinished(instance.getId(), InstanceStatus.APPROVED.code());
        notifyPlaceholder(instance, "已通过");
        log.info("实例 {} 全部节点通过 → approved（终态）", instance.getId());
    }

    /**
     * 「上一节点通过后自动回到本节点」（REQ-FLOW-021 / §7.2 第 10 行）。
     *
     * @return {@code true} 表示本次是回退回归（已重新激活发起回退的节点，不再走主干推进）
     */
    private boolean resumeRollbackIfAny(FlowInstanceRow instance, FlowNodeInstanceRow nodeInstance) {
        FlowRoutingRow rollback = routingMapper.selectProcessingRollback(instance.getId(),
                nodeInstance.getNodeSeq());
        if (rollback == null) {
            return false;
        }
        routingMapper.finishRouting(rollback.getId());
        FlowNodeInstanceRow returned = nodeInstanceMapper
                .selectByInstanceAndSeq(instance.getId(), rollback.getFromNodeSeq()).stream()
                .filter(row -> NodeStatus.RETURNED.code().equals(row.getStatus()))
                .findFirst()
                .orElse(null);
        if (returned == null) {
            log.warn("实例 {} 的回退记录 seq={} 找不到「已退回」节点实例，按正常推进处理",
                    instance.getId(), rollback.getSeq());
            return false;
        }
        taskMapper.closePendingByNodeInstance(returned.getId());
        nodeInstanceMapper.activate(returned.getId());
        createTasksForNode(returned);
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(), null,
                returned.getNodeSeq(), returned.getDeptId());
        log.info("回退回归：实例 {} 节点 {} 重审通过 → 回到节点 {}（§7.2 第 10 行）",
                instance.getId(), nodeInstance.getNodeSeq(), returned.getNodeSeq());
        return true;
    }

    /** ② 通过时勾选协同部门：为每个部门生成独立的并行子任务组（不占主链编号）。 */
    private void createCollabGroups(FlowInstanceRow instance, FlowNodeInstanceRow financeNode,
                                    List<Long> collabDeptIds, CurrentUser actor) {
        SnapshotNode financeSnapshot = snapshotNode(readSnapshot(instance), financeNode.getNodeSeq());
        Set<Long> depts = new LinkedHashSet<>(collabDeptIds);
        depts.removeIf(java.util.Objects::isNull);
        if (financeNode.getDeptId() != null) {
            depts.remove(financeNode.getDeptId());
        }
        for (Long deptId : depts) {
            List<Long> leaders = leadersOf(deptId, "协同部门");
            FlowNodeInstanceRow group = ensureNodeInstanceForRound(instance, financeSnapshot, deptId, leaders);
            activateNodeInstance(instance, group, deptId);
            threadWriter.append(instance.getId(), group.getId(), actor, ThreadAction.SUBMIT,
                    "协同部门 " + deptId + " 并行子任务组已生成（全部完成后进入下一节点）");
            log.info("协同子任务组：instanceId={} deptId={} 候选人={}（PRD §6.3 / REQ-FLOW-005）",
                    instance.getId(), deptId, leaders);
        }
    }

    // ================================================================ 节点 / 任务基础设施

    /** 建（或复位）节点实例行，候选人为给定集合。 */
    private FlowNodeInstanceRow ensureNodeInstanceForRound(FlowInstanceRow instance, SnapshotNode node,
                                                           Long deptId, List<Long> approverIds) {
        String key = nodeKey(node.nodeSeq(), node.nodeCode(), deptId);
        FlowNodeInstanceRow row = nodeInstanceMapper.selectByInstanceAndKey(instance.getId(), key);
        String idsJson = JsonText.write(approverIds == null ? List.of() : approverIds);
        if (row == null) {
            row = new FlowNodeInstanceRow();
            row.setInstanceId(instance.getId());
            row.setNodeSeq(node.nodeSeq());
            row.setNodeCode(node.nodeCode());
            row.setNodeName(node.nodeName());
            row.setNodeKey(key);
            row.setDeptId(deptId);
            row.setDecisionMode(node.decisionMode());
            row.setPassThreshold(node.passThreshold());
            row.setApproverIdsJson(idsJson);
            row.setStatus(NodeStatus.PENDING.code());
            row.setReturnedCount(0);
            row.setSupplementRequested(false);
            nodeInstanceMapper.insert(row);
        } else {
            nodeInstanceMapper.resetForNewRound(row.getId());
            nodeInstanceMapper.updateApprovers(row.getId(), idsJson);
            row.setApproverIdsJson(idsJson);
            row.setStatus(NodeStatus.PENDING.code());
            row.setReturnedCount(0);
            row.setSupplementRequested(false);
            row.setAddSignChainJson(null);
        }
        return row;
    }

    /** 激活节点实例并为其候选人产生待办（或签/会签=全部；依次=第一位；登记节点=单人登记任务）。 */
    private void activateNodeInstance(FlowInstanceRow instance, FlowNodeInstanceRow nodeInstance, Long deptId) {
        nodeInstanceMapper.activate(nodeInstance.getId());
        nodeInstance.setStatus(NodeStatus.ACTIVE.code());
        createTasksForNode(nodeInstance);
        instanceMapper.updateProgress(instance.getId(), InstanceStatus.APPROVING.code(), null,
                nodeInstance.getNodeSeq(), deptId != null ? deptId : nodeInstance.getDeptId());
    }

    /** 按决议模式产生任务：登记节点（decision_mode NULL）与依次签只产生一条。 */
    private void createTasksForNode(FlowNodeInstanceRow nodeInstance) {
        taskMapper.closePendingByNodeInstance(nodeInstance.getId());
        List<Long> candidates = readIds(nodeInstance.getApproverIdsJson());
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.APPROVER_RESOLUTION_BLOCKED,
                    "节点「" + nodeInstance.getNodeName() + "」无有效审批人，已拒绝（不允许静默跳过，AC-11）");
        }
        DecisionMode mode = DecisionMode.of(nodeInstance.getDecisionMode()).orElse(null);
        if (mode == null || mode == DecisionMode.SEQUENCE) {
            createTask(nodeInstance, candidates.get(0), null, null, null);
            return;
        }
        for (Long candidate : candidates) {
            createTask(nodeInstance, candidate, null, null, null);
        }
    }

    /** 跳过节点（② 不涉及费用 / 自由跳转的中间节点）：写 {@code skip} 轨迹，不产生待办。 */
    private void skipNodeInstance(FlowInstanceRow instance, FlowNodeInstanceRow row, SnapshotNode node,
                                  Integer targetSeq) {
        FlowLinkage.SkipCascade cascade = targetSeq == null
                ? FlowLinkage.financeSkipped(row.getNodeName())
                : FlowLinkage.jumpSkipped(row.getNodeName(), targetSeq);
        taskMapper.closePendingByNodeInstance(row.getId());
        nodeInstanceMapper.finish(row.getId(), cascade.nodeStatus().code());
        threadWriter.append(instance.getId(), row.getId(), null, cascade.threadAction(), cascade.opinion());
        log.info("节点跳过：instanceId={} seq={} 原因={}", instance.getId(), row.getNodeSeq(), cascade.opinion());
    }

    /** 新增一条任务（主任务：不参与改派留痕）。 */
    private FlowTaskRow createTask(FlowNodeInstanceRow nodeInstance, Long assigneeId, String addSignType,
                                   Long delegateFrom, String opinion) {
        return createTaskRow(nodeInstance, assigneeId, addSignType, delegateFrom, null, opinion);
    }

    /** 新增一条转办/改派任务（受让人为新处理人，{@code originAssigneeId} 留痕原处理人）。 */
    private FlowTaskRow createHandoverTask(FlowNodeInstanceRow nodeInstance, Long assigneeId,
                                           Long originAssigneeId) {
        return createTaskRow(nodeInstance, assigneeId, null, null, originAssigneeId, null);
    }

    /** 任务落库的唯一出口。 */
    private FlowTaskRow createTaskRow(FlowNodeInstanceRow nodeInstance, Long assigneeId, String addSignType,
                                      Long delegateFrom, Long originAssigneeId, String opinion) {
        if (assigneeId == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "任务处理人不能为空");
        }
        FlowTaskRow row = new FlowTaskRow();
        row.setInstanceId(nodeInstance.getInstanceId());
        row.setNodeInstanceId(nodeInstance.getId());
        row.setAssigneeId(assigneeId);
        row.setOriginAssigneeId(originAssigneeId);
        row.setAddSignType(addSignType);
        row.setDelegateFrom(delegateFrom);
        row.setStatus(TaskStatus.PENDING.code());
        row.setOpinion(opinion);
        taskMapper.insert(row);
        return row;
    }

    /** 未完成节点实例统一取消（§7.2 终态联动）。 */
    private void cancelLiveNodes(Long instanceId, Long exceptNodeInstanceId) {
        for (FlowNodeInstanceRow row : nodeInstanceMapper.selectLiveByInstance(instanceId)) {
            if (exceptNodeInstanceId != null && exceptNodeInstanceId.equals(row.getId())) {
                continue;
            }
            nodeInstanceMapper.finish(row.getId(), NodeStatus.CANCELLED.code());
        }
    }

    /** 序号对应的节点实例完成时，收束其流转记录（{@code processing → finished}）。 */
    private void finishProcessingRoutings(Long instanceId, Integer nodeSeq) {
        for (FlowRoutingRow row : routingMapper.selectRecentRoutings(instanceId, 50)) {
            if ("processing".equals(row.getStatus()) && RoutingAction.ROUTE.code().equals(row.getActionType())
                    && nodeSeq != null && nodeSeq.equals(row.getToNodeSeq())) {
                routingMapper.finishRouting(row.getId());
            }
        }
    }

    // ================================================================ 读取 / 校验基础设施

    /** 实例读取：走调用人数据域（域外 → 404）。 */
    private FlowInstanceRow requireInstance(Long instanceId) {
        if (instanceId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "instanceId 不能为空");
        }
        FlowInstanceRow instance = instanceMapper.selectInstanceById(instanceId);
        if (instance == null) {
            throw BizException.notFound("流程实例");
        }
        return instance;
    }

    /** 任务读取：走调用人数据域（域外 → 404）。 */
    private FlowTaskRow requireTask(Long taskId) {
        if (taskId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "taskId 不能为空");
        }
        FlowTaskRow task = taskMapper.selectTaskById(taskId);
        if (task == null) {
            throw BizException.notFound("审批任务");
        }
        return task;
    }

    private FlowNodeInstanceRow requireNodeInstance(Long nodeInstanceId) {
        FlowNodeInstanceRow row = nodeInstanceMapper.selectNodeInstanceById(nodeInstanceId);
        if (row == null) {
            throw BizException.notFound("节点实例");
        }
        return row;
    }

    /** 动作必须由**任务本人**执行（系统管理员也须先改派；避免「管理员代批」绕过责任认定）。 */
    private void requireAssignee(FlowTaskRow task, CurrentUser actor) {
        if (!task.getAssigneeId().equals(actor.id())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该任务不属于当前用户（只有任务处理人本人可以处理；如需换人请用「改派」，AC-52）");
        }
    }

    /** 动作前置状态：实例审批中、非待补件、节点进行中、任务待处理、节点即当前节点。 */
    private void requireActionable(FlowInstanceRow instance, FlowTaskRow task) {
        if (!TaskStatus.PENDING.code().equals(task.getStatus())) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "任务已不在待处理状态（当前 " + task.getStatus() + "），不可再操作（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        InstanceStatus status = InstanceStatus.of(instance.getStatus()).orElse(null);
        if (status != InstanceStatus.APPROVING) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "单据当前状态为「" + instance.getStatus() + "」，不可审批（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        if (SubStatus.PENDING_SUPPLEMENT.code().equals(instance.getSubStatus())) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "单据处于「待补件」状态，补件提交前不可审批（PRD §6.3.1 / REQ-FLOW-023，错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        FlowNodeInstanceRow node = nodeInstanceMapper.selectNodeInstanceById(task.getNodeInstanceId());
        if (node == null) {
            throw BizException.notFound("节点实例");
        }
        if (!NodeStatus.ACTIVE.code().equals(node.getStatus())) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "节点「" + node.getNodeName() + "」当前状态为「" + node.getStatus() + "」，不可审批（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        if (instance.getCurrentNodeSeq() != null && node.getNodeSeq() != null
                && !instance.getCurrentNodeSeq().equals(node.getNodeSeq())) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "该节点不是当前节点（当前节点序号 " + instance.getCurrentNodeSeq() + "，错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
    }

    /** ⑦ 登记节点（{@code decision_mode IS NULL}）不接受「通过 / 驳回」。 */
    private void requireNotRegistrationNode(FlowNodeInstanceRow nodeInstance) {
        if (nodeInstance.getDecisionMode() == null) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "节点「" + nodeInstance.getNodeName()
                            + "」是归档登记节点（decision_mode 为空，默认「仅登记不审批」），"
                            + "请使用「归档登记」动作（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
    }

    private void requireInitiatorOrAdmin(FlowInstanceRow instance, CurrentUser actor) {
        if (actor == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (permissionService.isSuperAdmin(actor)) {
            return;
        }
        if (!actor.id().equals(instance.getInitiatorId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有发起人本人（或系统管理员）可以对该单据执行本操作");
        }
    }

    /** 目标部门必须有在职负责人（否则会造出「无候选人的节点」，等同静默绕过审批）。 */
    private List<Long> leadersOf(Long deptId, String label) {
        List<Candidate> leaders = directory.primaryLeaders(deptId);
        if (leaders == null || leaders.isEmpty()) {
            throw new BizException(ErrorCode.APPROVER_RESOLUTION_BLOCKED,
                    label + " " + deptId + " 未配置正职负责人，无法产生审批任务（AC-11 不允许静默跳过）");
        }
        List<Long> ids = new ArrayList<>();
        for (Candidate candidate : leaders) {
            if (candidate != null && candidate.userId() != null && !ids.contains(candidate.userId())) {
                ids.add(candidate.userId());
            }
        }
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.APPROVER_RESOLUTION_BLOCKED,
                    label + " " + deptId + " 的正职负责人均不可用（离职/停用），无法产生审批任务");
        }
        return ids;
    }

    private void requireActiveUser(Long userId, String label) {
        if (directory.user(userId).isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "不存在或已离职/停用：userId=" + userId);
        }
    }

    /** 禁止回流到已处理部门（REQ-FLOW-020 第 ② 条）。 */
    private void requireNoLoopBack(FlowInstanceRow instance, Long toDeptId) {
        if (toDeptId.equals(instance.getCurrentDeptId())) {
            throw new BizException(ErrorCode.FLOW_ROUTING_LOOP_BACK,
                    "目标部门 " + toDeptId + " 就是单据当前承接部门，无需流转（错误码 "
                            + ErrorCode.FLOW_ROUTING_LOOP_BACK.getCode() + "）");
        }
        int processed = routingMapper.countRoutingToDept(instance.getId(), toDeptId);
        if (processed > 0) {
            throw new BizException(ErrorCode.FLOW_ROUTING_LOOP_BACK,
                    "目标部门 " + toDeptId + " 已处理过本单（流转记录 " + processed
                            + " 条），禁止回流（REQ-FLOW-020，错误码 "
                            + ErrorCode.FLOW_ROUTING_LOOP_BACK.getCode() + "）");
        }
    }

    /** 同一部门末尾连续「回到本部门」的次数。 */
    private int trailingBackHome(Long instanceId, Long toDeptId) {
        int count = 0;
        for (FlowRoutingRow row : routingMapper.selectRecentRoutings(instanceId, 20)) {
            if (RoutingAction.BACK_HOME.code().equals(row.getActionType())
                    && toDeptId.equals(row.getToDeptId())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 撤回闸门：**按实例锁定的模板版本**取撤回窗口口径后判定（REQ-FLOW-009 / AC-16 / doc/templates.md §1.8）。
     *
     * <h2>取数（AC-09 关键）</h2>
     * <p>口径从 {@link FlowGateService#withdrawWindowOf} 取 —— 与 Q6/Q7 **同一取数路径**
     * （按 {@code flow_instance.template_id} 即发起时锁定的模板行读列，缺行/{@code NULL} → 默认口径）。
     * <b>刻意不读当前 published 模板</b>：否则改模板会改到在途单据的撤回窗口，违反 AC-09。
     * <p>调用方（{@link #withdraw}）已把实例行读出来，这里复用同一行，避免二次查询与「两处读到的实例不一致」；
     * 生效口径由 {@link #effectiveWindowOf} 解析成**非空**枚举后再传入（mock 返回 {@code null} 也安全）。
     *
     * <h2>判据（2026-10-04：由硬编码口径改为配置化口径）</h2>
     * <p>真正的判定在纯策略 {@link WithdrawWindowPolicy#allowed}：
     * <ol>
     *   <li>公共拒绝：{@code seq = 2} 的节点已 {@code approved} ⇒ 已越过②；
     *       或 {@code seq > 2} 的节点只要**离开 {@code pending} 且未被取消** ⇒ 主干已推进到②之后；</li>
     *   <li>口径增量：{@code until_finance_started}（AC-16 严格口径）在①之外**再加一条** ——
     *       {@code seq = 2} 且状态 ∈ { {@code active} / {@code waiting_supplement} / {@code returned} } ⇒ 拒
     *       （②一旦开始处理即不可撤回）；</li>
     *   <li>其余（含 {@code seq > 2} 的 {@code pending}、已被上级动作取消的 {@code cancelled}）→ 放行。</li>
     * </ol>
     *
     * <h2>为什么不能拿「未取消」当「未到达」</h2>
     * <p>{@link #submit} 是**一次性物化**：提交时把快照里的 7 个节点实例全部落库为 {@code pending}，
     * 只有终态级联（驳回/撤回/终止）才会把它们改成 {@code cancelled}。因此「{@code seq > 2} 未取消」
     * 这条判据在**单据一提交就恒为「已越过②」** —— 撤回永远失败（上一轮运行期实测：两张单分别
     * 409 / 40910）。{@code pending} 的语义是「尚未轮到本节点」（doc/enums.md §5），
     * **未到达的节点不得阻止撤回**。
     */
    private boolean withdrawAllowed(FlowInstanceRow instance, WithdrawWindow window) {
        return WithdrawWindowPolicy.allowed(window, nodeInstanceMapper.selectByInstance(instance.getId()));
    }

    /**
     * 该实例**生效的**撤回窗口口径：按发起时锁定的模板版本读（与 Q6/Q7 同一取数路径），
     * {@code null}（列未配置 / 模板行缺失）→ 默认口径。**唯一入口**，见 {@link WithdrawWindowPolicy#effective}。
     */
    private WithdrawWindow effectiveWindowOf(FlowInstanceRow instance) {
        return WithdrawWindowPolicy.effective(gateService.withdrawWindowOf(instance));
    }

    /** 内部用（无权限包装）的回到草稿：清未完成节点与待决议任务，写审计。 */
    private void reopenInternal(Long instanceId, InstanceStatus from) {
        cancelLiveNodes(instanceId, null);
        taskMapper.closePendingByInstance(instanceId);
        runtimeMapper.cancelPendingSupplements(instanceId);
        instanceMapper.updateProgress(instanceId, InstanceStatus.DRAFT.code(), null, null, null);
        auditLogWriter.appendAsCurrentUser("reopen", "instance", instanceId,
                "{\"status\":\"" + from.code() + "\"}", "{\"status\":\"draft\"}", null, null);
    }

    // ================================================================ 快照 / 出参辅助

    private ApproverSnapshot readSnapshot(FlowInstanceRow instance) {
        try {
            ApproverSnapshot snapshot = ApproverSnapshotCodec.read(instance.getApproverSnapshotJson());
            if (snapshot == null || snapshot.nodes().isEmpty()) {
                throw new BizException(ErrorCode.CONFLICT, "审批人快照为空（请重新解析快照后再操作）");
            }
            return snapshot;
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.CONFLICT, "审批人快照无法解析：" + ex.getMessage());
        }
    }

    private static List<SnapshotNode> orderedNodes(ApproverSnapshot snapshot) {
        List<SnapshotNode> nodes = new ArrayList<>(snapshot.nodes());
        nodes.sort(Comparator.comparing(node -> node.nodeSeq() == null ? 0 : node.nodeSeq()));
        return nodes;
    }

    private static SnapshotNode snapshotNode(ApproverSnapshot snapshot, Integer seq) {
        for (SnapshotNode node : snapshot.nodes()) {
            if (node.nodeSeq() != null && node.nodeSeq().equals(seq)) {
                return node;
            }
        }
        throw new BizException(ErrorCode.CONFLICT,
                "快照中不存在节点序号 " + seq + "（本单锁定的模板版本与运行时状态不一致）");
    }

    private static List<Integer> trunkSeqs(ApproverSnapshot snapshot) {
        List<Integer> seqs = new ArrayList<>();
        for (SnapshotNode node : orderedNodes(snapshot)) {
            if (node.nodeSeq() != null && !seqs.contains(node.nodeSeq())) {
                seqs.add(node.nodeSeq());
            }
        }
        return seqs;
    }

    /** 快照节点 → 候选人 id 列表（**顺序即依次签顺序**）。 */
    private static List<Long> approverIds(SnapshotNode node) {
        List<Long> ids = new ArrayList<>();
        for (ApproverSnapshot.SnapshotApprover approver : node.approvers()) {
            if (approver != null && approver.userId() != null && !ids.contains(approver.userId())) {
                ids.add(approver.userId());
            }
        }
        return ids;
    }

    /** 读 {@code approver_ids_json}（裸数组，或 {@code {"ids":[...]}} 形态）。 */
    public static List<Long> readIds(String json) {
        List<Long> ids = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return ids;
        }
        JsonNode node;
        try {
            node = JsonText.read(json);
        } catch (IllegalArgumentException ex) {
            return ids;
        }
        if (node == null) {
            return ids;
        }
        JsonNode array = node.isArray() ? node : node.get("ids");
        if (node.isObject() && array == null) {
            array = node.get("approver_ids");
        }
        if (array == null || !array.isArray()) {
            return ids;
        }
        for (JsonNode item : array) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isNumber()) {
                ids.add(item.asLong());
            } else {
                try {
                    ids.add(Long.valueOf(item.asText().trim()));
                } catch (NumberFormatException ignored) {
                    // 非法项跳过：调用方据此走「无有效候选人」的拦截路径
                }
            }
        }
        return ids;
    }

    /** {@code node_key} 口径（doc/data-model.md §8.2）。 */
    public static String nodeKey(Integer seq, String nodeCode, Long deptId) {
        return seq + ":" + nodeCode + ":" + (deptId == null ? 0 : deptId);
    }

    private static int used(Integer count) {
        return count == null ? 0 : count;
    }

    private static int nextRoutingSeq(FlowInstanceRow instance) {
        return used(instance.getRoutingSeq()) + 1;
    }

    /** Q6 出参。 */
    public static GateView toGateView(GateCounterPolicy.Budget budget) {
        if (budget == null) {
            return null;
        }
        String note = budget.unlimited()
                ? budget.key() + " 未配置上限（0/NULL = 不限，doc/templates.md §1.7）"
                : budget.key() + "=" + budget.max() + "，已用 " + budget.used() + " 次，剩余 "
                        + budget.remaining() + " 次";
        return new GateView(budget.key(), budget.max(), budget.used(), budget.remaining(),
                budget.unlimited(), note);
    }

    /** Q7 出参（含「就位但不调度」的显式披露）。 */
    private SupplementDeadlineView deadlineView(FlowInstanceRow instance, Deadline deadline) {
        if (deadline == null) {
            return null;
        }
        return new SupplementDeadlineView(
                deadline.dueAt() == null ? null : deadline.dueAt().format(TIME),
                deadline.days(),
                deadline.type() == null ? null : deadline.type().code(),
                deadline.holidayCalendarMissing(),
                gateService.calendarDisclosure(),
                deadline.note(),
                gateService.timeoutActionOf(instance),
                false,
                SupplementDeadlinePolicy.executionTodo());
    }

    private ActionResult actionResult(FlowAction action, FlowInstanceRow instance, FlowTaskRow task,
                                      String message, GateCounterPolicy.Budget budget,
                                      SupplementDeadlineView deadline) {
        FlowInstanceRow fresh = requireInstance(instance.getId());
        FlowNodeInstanceRow node = task == null ? null
                : nodeInstanceMapper.selectNodeInstanceById(task.getNodeInstanceId());
        if (node == null && fresh.getCurrentNodeSeq() != null) {
            node = nodeInstanceMapper.selectByInstanceAndSeq(fresh.getId(), fresh.getCurrentNodeSeq())
                    .stream().filter(row -> NodeStatus.ACTIVE.code().equals(row.getStatus()))
                    .findFirst().orElse(null);
        }
        return new ActionResult(
                action.code(),
                action.label(),
                fresh.getId(),
                fresh.getStatus(),
                fresh.getSubStatus(),
                fresh.getCurrentNodeSeq(),
                node == null ? null : node.getId(),
                node == null ? null : node.getStatus(),
                task == null ? null : task.getId(),
                task == null ? null : task.getStatus(),
                message,
                toGateView(budget),
                deadline);
    }

    /**
     * §7.2「通知发起人」联动的**占位**：站内信 / 邮件属 2b（{@code sys_message} 已有表结构但无发送实现），
     * 这里只落 WARN 日志留痕，**不静默丢弃**。
     */
    private void notifyPlaceholder(FlowInstanceRow instance, String scene) {
        log.warn("站内信/邮件通知未实现（属 2b）：instanceId={} 场景={} 收件人=发起人 {}",
                instance.getId(), scene, instance.getInitiatorId());
    }

    /** 供接口出参：动作面清单（权限 / 必填原因 / 意见下限 / 轨迹动作）。 */
    public static List<FlowAction> actionCatalog() {
        return Arrays.stream(FlowAction.values()).toList();
    }
}
