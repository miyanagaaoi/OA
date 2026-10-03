package com.oa.workflow.runtime.app;

import com.oa.common.error.BizException;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.CcView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.InstanceRuntimeView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.NodeInstanceView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.RoutingView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.SupplementView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.TaskView;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ThreadView;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.RoutingAction;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import com.oa.workflow.runtime.domain.SupplementDeadlinePolicy;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import com.oa.workflow.runtime.infra.row.FlowCcRow;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import com.oa.workflow.runtime.infra.row.FlowRoutingRow;
import com.oa.workflow.runtime.infra.row.FlowSupplementRow;
import com.oa.workflow.runtime.infra.row.FlowTaskViewRow;
import com.oa.workflow.runtime.infra.row.FlowThreadRow;
import com.oa.workflow.task.domain.AddSignPolicy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 运行时**只读**视图（2a.4）：节点实例 / 任务 / 轨迹 / 流转链 / 补件 / 抄送。
 *
 * <h2>数据域纪律</h2>
 * <p>每个公开方法都**先**经 {@code FlowInstanceMapper#selectInstanceById}（带 {@code @dataScope} 标记，
 * 域外一律 404）取实例行，再按其 {@code instance_id} 读从表 —— 从表本身不是受控表，
 * 它们的可见性完全依附于这一步（见 {@code FlowNodeInstanceMapper} 的类注释）。
 * 越权读取因此 <b>fail-closed</b>：不是「过滤掉」，而是查不到实例 → 404。
 */
@Service
public class FlowRuntimeQueryService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeInstanceMapper nodeInstanceMapper;
    private final FlowTaskMapper taskMapper;
    private final FlowRuntimeMapper runtimeMapper;
    private final FlowRoutingMapper routingMapper;
    private final FlowGateService gateService;
    private final WorkflowPermissionService permissionService;
    private final ApproverDirectory directory;

    public FlowRuntimeQueryService(FlowInstanceMapper instanceMapper,
                                   FlowNodeInstanceMapper nodeInstanceMapper,
                                   FlowTaskMapper taskMapper,
                                   FlowRuntimeMapper runtimeMapper,
                                   FlowRoutingMapper routingMapper,
                                   FlowGateService gateService,
                                   WorkflowPermissionService permissionService,
                                   ApproverDirectory directory) {
        this.instanceMapper = instanceMapper;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.taskMapper = taskMapper;
        this.runtimeMapper = runtimeMapper;
        this.routingMapper = routingMapper;
        this.gateService = gateService;
        this.permissionService = permissionService;
        this.directory = directory;
    }

    /** 实例运行态总览（端到端验收与排障的证据载体）。 */
    public InstanceRuntimeView runtime(Long instanceId) {
        permissionService.requireInitiator("查看审批运行态");
        FlowInstanceRow instance = requireVisible(instanceId);
        List<FlowNodeInstanceRow> nodes = nodeInstanceMapper.selectByInstance(instanceId);
        return new InstanceRuntimeView(
                instance.getId(),
                instance.getBizNo(),
                instance.getStatus(),
                instance.getSubStatus(),
                instance.getCurrentNodeSeq(),
                instance.getCurrentDeptId(),
                instance.getOwnerDeptId(),
                instance.getRoutingSeq(),
                instance.getRoutingCount(),
                instance.getSupplementCount(),
                FlowEngineService.toGateView(gateService.returnBudget(instance)),
                FlowEngineService.toGateView(gateService.supplementBudget(instance)),
                toNodeViews(nodes),
                tasks(instanceId),
                thread(instanceId),
                routing(instanceId),
                supplements(instanceId),
                cc(instanceId),
                nodes.stream().anyMatch(row -> AddSignPolicy.hasOpen(row.getAddSignChainJson())));
    }

    /** 节点实例清单。 */
    public List<NodeInstanceView> nodes(Long instanceId) {
        permissionService.requireInitiator("查看节点实例");
        requireVisible(instanceId);
        return toNodeViews(nodeInstanceMapper.selectByInstance(instanceId));
    }

    /** 任务清单。 */
    public List<TaskView> tasks(Long instanceId) {
        permissionService.requireInitiator("查看审批任务");
        requireVisible(instanceId);
        List<TaskView> views = new ArrayList<>();
        for (FlowTaskViewRow row : taskMapper.selectTasksByInstance(instanceId)) {
            views.add(new TaskView(
                    row.getTaskId(), row.getInstanceId(), row.getNodeSeq(), row.getNodeName(),
                    row.getAssigneeId(), nameOf(row.getAssigneeId()), row.getOriginAssigneeId(),
                    row.getDelegateFrom(), row.getAddSignType(), row.getTaskStatus(),
                    taskStatusLabel(row.getTaskStatus()),
                    row.getOpinion(), row.getHandoverReason(), row.getTaskCreatedAt(), row.getDecidedAt()));
        }
        return views;
    }

    /** 审批轨迹（{@code sys_thread}，只追加）。 */
    public List<ThreadView> thread(Long instanceId) {
        permissionService.requireInitiator("查看审批轨迹");
        requireVisible(instanceId);
        List<ThreadView> views = new ArrayList<>();
        for (FlowThreadRow row : runtimeMapper.selectThreadByInstance(instanceId)) {
            views.add(new ThreadView(row.getSeq(), row.getAction(), threadActionLabel(row.getAction()),
                    row.getNodeInstanceId(), row.getActorId(), row.getActorName(), row.getActorPosition(),
                    row.getOpinion(), row.getSignatureId(), format(row.getCreatedAt())));
        }
        return views;
    }

    /** 流转链。 */
    public List<RoutingView> routing(Long instanceId) {
        permissionService.requireInitiator("查看流转链");
        requireVisible(instanceId);
        List<RoutingView> views = new ArrayList<>();
        for (FlowRoutingRow row : routingMapper.selectRoutingByInstance(instanceId)) {
            views.add(new RoutingView(row.getSeq(), row.getActionType(),
                    RoutingAction.of(row.getActionType()).map(RoutingAction::label).orElse(row.getActionType()),
                    row.getFromDeptId(), row.getToDeptId(), row.getFromNodeSeq(), row.getToNodeSeq(),
                    row.getDesignatedBy(), row.getReason(), row.getStatus(),
                    format(row.getCreatedAt()), format(row.getFinishedAt())));
        }
        return views;
    }

    /** 补件记录（含 Q7 的应完成时间与是否已超时）。 */
    public List<SupplementView> supplements(Long instanceId) {
        permissionService.requireInitiator("查看补件记录");
        requireVisible(instanceId);
        LocalDateTime now = LocalDateTime.now();
        List<SupplementView> views = new ArrayList<>();
        for (FlowSupplementRow row : runtimeMapper.selectSupplementsByInstance(instanceId)) {
            views.add(new SupplementView(row.getId(), row.getSupplementRound(), row.getStatus(),
                    row.getReason(), row.getRequestedBy(), row.getNodeInstanceId(),
                    format(row.getDeadline()),
                    row.getDeadline() != null && row.getSubmittedAt() == null
                            && SupplementDeadlinePolicy.timedOut(row.getDeadline(), now),
                    format(row.getSubmittedAt()), row.getSubmittedBy(), row.getSubmittedNote()));
        }
        return views;
    }

    /** 抄送记录（只读可见，不产生待办）。 */
    public List<CcView> cc(Long instanceId) {
        permissionService.requireInitiator("查看抄送记录");
        requireVisible(instanceId);
        List<CcView> views = new ArrayList<>();
        for (FlowCcRow row : runtimeMapper.selectCcByInstance(instanceId)) {
            views.add(new CcView(row.getUserId(), nameOf(row.getUserId()), row.getSource(),
                    format(row.getReadAt())));
        }
        return views;
    }

    // ================================================================ 内部

    private FlowInstanceRow requireVisible(Long instanceId) {
        if (instanceId == null) {
            throw new BizException(com.oa.common.error.ErrorCode.PARAM_INVALID, "instanceId 不能为空");
        }
        FlowInstanceRow instance = instanceMapper.selectInstanceById(instanceId);
        if (instance == null) {
            // 越权读取 fail-closed：域外实例一律 404（不区分「不存在」与「无权访问」）
            throw BizException.notFound("流程实例");
        }
        return instance;
    }

    private List<NodeInstanceView> toNodeViews(List<FlowNodeInstanceRow> rows) {
        List<NodeInstanceView> views = new ArrayList<>();
        for (FlowNodeInstanceRow row : rows) {
            List<Long> ids = FlowEngineService.readIds(row.getApproverIdsJson());
            NodeStatus status = NodeStatus.of(row.getStatus()).orElse(null);
            views.add(new NodeInstanceView(
                    row.getId(), row.getNodeSeq(), row.getNodeCode(), row.getNodeName(), row.getNodeKey(),
                    row.getDeptId(), row.getStatus(), status == null ? row.getStatus() : status.label(),
                    row.getDecisionMode(), row.getPassThreshold(), ids,
                    row.getReturnedCount(), Boolean.TRUE.equals(row.getSupplementRequested()),
                    row.getAddSignChainJson(), AddSignPolicy.hasOpen(row.getAddSignChainJson()),
                    format(row.getStartedAt()), format(row.getFinishedAt())));
        }
        return views;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        CurrentUser principal = permissionService.principalOrNull();
        if (principal != null && userId.equals(principal.id())) {
            return principal.name();
        }
        Optional<Candidate> candidate = directory.user(userId);
        return candidate.map(Candidate::name).orElse(null);
    }

    private static String taskStatusLabel(String status) {
        return TaskStatus.of(status).map(TaskStatus::label).orElse(status);
    }

    private static String threadActionLabel(String action) {
        return ThreadAction.of(action).map(ThreadAction::label).orElse(action);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME);
    }
}
