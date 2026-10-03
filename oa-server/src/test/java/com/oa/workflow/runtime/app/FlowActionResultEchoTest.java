package com.oa.workflow.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.FlowInstanceService;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.api.dto.RuntimeDtos.ActionResult;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.domain.GateCounterPolicy;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.SupplementDeadlinePolicy.Deadline;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import com.oa.workflow.runtime.infra.row.FlowTaskRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>动作响应必须回显**动作后**的三层状态</b>（2026-10-05 修正）。
 *
 * <h2>被修的缺陷</h2>
 * <p>{@code FlowEngineService#actionResult} 把**实例**重新读了一遍，却把**任务**直接用入参
 * {@code task}（{@code requireTask} 在动作**之前**取的快照）。于是
 * {@code POST /flow-tasks/{id}/approve} 的响应回 {@code taskStatus:"pending"}，而库里（以及
 * 「我已审批」列表、运行态视图）是 {@code agreed} —— 同一事实两处不一致。
 *
 * <h2>本类怎么让「改前必红、改后必绿」</h2>
 * <p>{@code taskMapper.selectTaskById} 按**连续返回值**打桩：第一次（{@code requireTask}）返回
 * 「动作前 = pending」的行，第二次（{@code actionResult} 重新读库）返回「动作后 = 终态」的行。
 * 改前代码用的是第一次那个对象 ⇒ 断言 {@code pending} 失败；改后代码用的是第二次那个对象 ⇒ 通过。
 * 因此这条断言证明的是「响应与**库中值**（重新读出来的那一行）一致」，而不是「响应碰巧等于某个常量」。
 *
 * <p>实例 / 节点状态同理：动作写入时把**同一行对象**改写成动作后的值（模拟库中落定值），
 * 断言响应读到的是改写后的值（回显动作前快照的旧实现会在这些字段上一起露馅）。
 */
class FlowActionResultEchoTest {

    private static final long INSTANCE_ID = 9401L;
    private static final long NODE_INSTANCE_ID = 9402L;
    private static final long PREVIOUS_NODE_INSTANCE_ID = 9403L;
    private static final long TASK_ID = 9404L;
    private static final long ACTOR_ID = 501L;
    private static final long PEER_ID = 502L;
    private static final long INITIATOR_ID = 503L;

    private FlowInstanceMapper instanceMapper;
    private FlowNodeInstanceMapper nodeInstanceMapper;
    private FlowTaskMapper taskMapper;
    private FlowRuntimeMapper runtimeMapper;
    private FlowRoutingMapper routingMapper;
    private FlowGateService gateService;

    private FlowEngineService engine;

    private FlowInstanceRow instance;
    private FlowNodeInstanceRow node;
    private FlowNodeInstanceRow previousNode;
    private FlowTaskRow taskBefore;
    private FlowTaskRow taskAfter;

    @BeforeEach
    void setUp() {
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(ACTOR_ID)).thenReturn(Set.of(
                "flow",
                FlowAction.APPROVE.permission(),
                FlowAction.REJECT.permission(),
                FlowAction.ROLLBACK.permission(),
                FlowAction.SUPPLEMENT_REQUEST.permission()));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);

        instanceMapper = mock(FlowInstanceMapper.class);
        nodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
        taskMapper = mock(FlowTaskMapper.class);
        runtimeMapper = mock(FlowRuntimeMapper.class);
        routingMapper = mock(FlowRoutingMapper.class);
        gateService = mock(FlowGateService.class);

        engine = new FlowEngineService(instanceMapper, nodeInstanceMapper, taskMapper, runtimeMapper,
                routingMapper, gateService, mock(FlowThreadWriter.class), gate, mock(ApproverDirectory.class),
                mock(AuditLogWriter.class), mock(FlowInstanceService.class),
                mock(com.oa.form.app.FormSubmitGate.class));

        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(ACTOR_ID, "emp01", "员工甲", "T501", 135L, 12L,
                        Set.of("employee"), Set.of(), false))
                .roleCodes(Set.of("employee"))
                .build());

        instance = instance("approving", null, 2);
        node = node(NODE_INSTANCE_ID, 2, "dept_leader", "直属部门负责人", NodeStatus.ACTIVE.code(), "all", "2");
        previousNode = node(PREVIOUS_NODE_INSTANCE_ID, 1, "dept_leader", "直属部门负责人",
                NodeStatus.APPROVED.code(), "any", null);
        taskBefore = task("pending");
        taskAfter = task(null);

        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance);
        when(nodeInstanceMapper.selectNodeInstanceById(NODE_INSTANCE_ID)).thenReturn(node);
        when(nodeInstanceMapper.selectNodeInstanceById(PREVIOUS_NODE_INSTANCE_ID)).thenReturn(previousNode);
        // 关键打桩：第一次 = 动作前（requireTask），第二次 = 动作后重新读库（actionResult）
        when(taskMapper.selectTaskById(TASK_ID)).thenReturn(taskBefore, taskAfter);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ================================================================ 四个动作各一条

    @Test
    @DisplayName("approve：响应 taskStatus=agreed 且 == 库中值（改前回显 pending）")
    void approveEchoesAgreedFromDatabase() {
        // 节点为会签（阈值 2、两位审批人），本人一票 ⇒ 决议仍待定，节点保持 active
        when(taskMapper.selectRoundPrimaryByNodeInstance(NODE_INSTANCE_ID)).thenReturn(List.of(agreedPeer()));
        when(taskMapper.updateDecision(eq(TASK_ID), eq(TaskStatus.AGREED.code()), any()))
                .thenAnswer(invocation -> {
                    taskAfter.setStatus(TaskStatus.AGREED.code());
                    return 1;
                });

        ActionResult result = engine.approve(TASK_ID, "同意", null);

        assertThat(result.taskStatus()).as("动作后 = 库中值").isEqualTo(TaskStatus.AGREED.code());
        assertThat(result.taskId()).isEqualTo(TASK_ID);
        assertThat(result.instanceStatus()).isEqualTo("approving");
        assertThat(result.currentNodeSeq()).isEqualTo(2);
        assertThat(result.nodeStatus()).as("动作后节点状态（仍在本节点，active）")
                .isEqualTo(NodeStatus.ACTIVE.code());
    }

    @Test
    @DisplayName("reject：响应 taskStatus=rejected、实例 rejected、节点 rejected —— 全部与库中一致")
    void rejectEchoesPostActionStates() {
        when(taskMapper.updateDecision(eq(TASK_ID), eq(TaskStatus.REJECTED.code()), any()))
                .thenAnswer(invocation -> {
                    taskAfter.setStatus(TaskStatus.REJECTED.code());
                    return 1;
                });
        when(nodeInstanceMapper.selectLiveByInstance(INSTANCE_ID)).thenReturn(List.of(node));
        when(nodeInstanceMapper.finish(eq(NODE_INSTANCE_ID), anyString())).thenAnswer(invocation -> {
            node.setStatus(invocation.getArgument(1, String.class));
            return 1;
        });
        when(instanceMapper.markFinished(eq(INSTANCE_ID), anyString())).thenAnswer(invocation -> {
            instance.setStatus(invocation.getArgument(1, String.class));
            return 1;
        });

        ActionResult result = engine.reject(TASK_ID, "材料不齐，请补充后重报");

        assertThat(result.taskStatus()).isEqualTo(TaskStatus.REJECTED.code());
        assertThat(result.instanceStatus()).as("实例为动作后状态（rejected），不是动作前的 approving")
                .isEqualTo("rejected");
        assertThat(result.nodeStatus()).as("节点为动作后状态").isEqualTo(NodeStatus.REJECTED.code());
    }

    @Test
    @DisplayName("rollback（回退）：响应 taskStatus=rolled_back、当前节点序号已回到上一节点、节点 returned")
    void rollbackEchoesPostActionStates() {
        when(nodeInstanceMapper.selectPreviousApproved(INSTANCE_ID, 2)).thenReturn(previousNode);
        when(gateService.assertReturnBudget(any(), anyString()))
                .thenReturn(new GateCounterPolicy.Budget("maxReturnCount", 2, 0, 2, false));
        when(taskMapper.updateDecision(eq(TASK_ID), eq(TaskStatus.ROLLED_BACK.code()), any()))
                .thenAnswer(invocation -> {
                    taskAfter.setStatus(TaskStatus.ROLLED_BACK.code());
                    return 1;
                });
        when(nodeInstanceMapper.finish(eq(NODE_INSTANCE_ID), anyString())).thenAnswer(invocation -> {
            node.setStatus(invocation.getArgument(1, String.class));
            return 1;
        });
        when(instanceMapper.updateProgress(eq(INSTANCE_ID), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    instance.setCurrentNodeSeq(invocation.getArgument(3, Integer.class));
                    return 1;
                });

        ActionResult result = engine.rollback(TASK_ID, "金额核算有误，退回重审");

        assertThat(result.taskStatus()).isEqualTo(TaskStatus.ROLLED_BACK.code());
        assertThat(result.currentNodeSeq()).as("当前节点序号是动作后的值（回退到上一节点）").isEqualTo(1);
        assertThat(result.nodeStatus()).isEqualTo(NodeStatus.RETURNED.code());
    }

    @Test
    @DisplayName("请求补件：响应 taskStatus=supplement_requested、subStatus=pending_supplement、节点 waiting_supplement")
    void supplementRequestEchoesPostActionStates() {
        when(gateService.assertSupplementBudget(any()))
                .thenReturn(new GateCounterPolicy.Budget("maxSupplementCount", 2, 0, 2, false));
        when(gateService.deadlineFor(any(), any()))
                .thenReturn(new Deadline(LocalDateTime.now().plusDays(3), 3, null, 0, true, "夹具"));
        when(taskMapper.updateDecision(eq(TASK_ID), eq(TaskStatus.SUPPLEMENT_REQUESTED.code()), any()))
                .thenAnswer(invocation -> {
                    taskAfter.setStatus(TaskStatus.SUPPLEMENT_REQUESTED.code());
                    return 1;
                });
        when(nodeInstanceMapper.markSupplementWaiting(NODE_INSTANCE_ID)).thenAnswer(invocation -> {
            node.setStatus(NodeStatus.WAITING_SUPPLEMENT.code());
            return 1;
        });
        when(instanceMapper.updateProgress(eq(INSTANCE_ID), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    instance.setSubStatus((String) invocation.getArgument(2));
                    return 1;
                });

        ActionResult result = engine.supplementRequest(TASK_ID, "请补充发票原件");

        assertThat(result.taskStatus()).isEqualTo(TaskStatus.SUPPLEMENT_REQUESTED.code());
        assertThat(result.subStatus()).as("实例子状态是动作后的值").isEqualTo("pending_supplement");
        assertThat(result.nodeStatus()).isEqualTo(NodeStatus.WAITING_SUPPLEMENT.code());
    }

    // ================================================================ 夹具

    private static FlowInstanceRow instance(String status, String subStatus, int currentNodeSeq) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-940001");
        row.setFormType("matter");
        row.setInitiatorId(INITIATOR_ID);
        row.setStatus(status);
        row.setSubStatus(subStatus);
        row.setCurrentNodeSeq(currentNodeSeq);
        row.setRoutingSeq(0);
        row.setRoutingCount(0);
        row.setSupplementCount(0);
        return row;
    }

    private static FlowNodeInstanceRow node(long id, int seq, String code, String name, String status,
                                           String decisionMode, String passThreshold) {
        FlowNodeInstanceRow row = new FlowNodeInstanceRow();
        row.setId(id);
        row.setInstanceId(INSTANCE_ID);
        row.setNodeSeq(seq);
        row.setNodeCode(code);
        row.setNodeName(name);
        row.setStatus(status);
        row.setDecisionMode(decisionMode);
        row.setPassThreshold(passThreshold);
        row.setApproverIdsJson("[" + ACTOR_ID + "," + PEER_ID + "]");
        row.setSupplementRequested(Boolean.FALSE);
        return row;
    }

    private static FlowTaskRow task(String status) {
        FlowTaskRow row = new FlowTaskRow();
        row.setId(TASK_ID);
        row.setInstanceId(INSTANCE_ID);
        row.setNodeInstanceId(NODE_INSTANCE_ID);
        row.setAssigneeId(ACTOR_ID);
        row.setStatus(status);
        return row;
    }

    /** 同节点另一位审批人的同意票（会签阈值 2 时用来把决议留在 PENDING）。 */
    private static FlowTaskRow agreedPeer() {
        FlowTaskRow row = new FlowTaskRow();
        row.setId(TASK_ID + 1);
        row.setInstanceId(INSTANCE_ID);
        row.setNodeInstanceId(NODE_INSTANCE_ID);
        row.setAssigneeId(PEER_ID);
        row.setStatus(TaskStatus.AGREED.code());
        return row;
    }
}
