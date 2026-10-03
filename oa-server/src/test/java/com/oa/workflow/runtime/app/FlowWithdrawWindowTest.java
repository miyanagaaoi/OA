package com.oa.workflow.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.FlowInstanceService;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.domain.FlowGateEnums.WithdrawWindow;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>撤回窗口（REQ-FLOW-009 / AC-16）的引擎级回归网 —— 「未到达的 pending 节点不得阻止撤回」</b>。
 *
 * <h2>被修的缺陷（2026-10-04）</h2>
 * <p>{@code FlowEngineService#submit} 是<b>一次性物化</b>：提交时按快照把 7 个节点实例全部落库为
 * {@code pending}，只有终态级联（驳回 / 撤回 / 终止）才会把它们置 {@code cancelled}。
 * 而旧 {@code withdrawAllowed} 的判据是「不存在 {@code seq > 2} 且<b>未取消</b>的节点实例」——
 * 在物化之后这条恒为「已越过②」，于是<b>单据一提交就永远撤回不了</b>
 * （上一轮运行期实测：两张单分别返回 409 / 40910）。想让撤回成功，只能运行期手工把
 * {@code seq >= 3} 的节点改成 {@code cancelled} —— 那是把测试改绿，不是把功能修好。
 *
 * <h2>修法（方向 a：判据语义化，不动物化时机）</h2>
 * <p>「越没越过②」看**节点是否被推进过**，不看「是否被取消」：
 * ① {@code seq = 2} 已 {@code approved} ⇒ 拒绝；② {@code seq > 2} 离开 {@code pending}
 * 且未被取消（{@code active} / {@code waiting_supplement} / {@code returned} / 各种终态）⇒ 拒绝；
 * ③ {@code pending}（未开始，doc/enums.md §5）与 {@code cancelled}（终态级联的产物）都不算越界。
 *
 * <p>没有选方向 b（惰性物化）：那会同时改到 {@code advance} / 快照 / 在途检查 / 轨迹的四条读取路径，
 * 收益只是把「已经正确的判据」换成另一种实现 —— 见任务回复的取向说明。
 *
 * <h2>两条不可回退的红线</h2>
 * <ul>
 *   <li>节点②通过后撤回仍被拒（REQ-FLOW-009 的正面口径，不许为了修①而放宽）；</li>
 *   <li>终态级联语义不变：撤回仍把未完成节点统一 {@code cancelled}、待决议任务统一关闭、
 *       补件请求取消、实例回 {@code draft}、轨迹落 {@code withdraw}、审计留痕。</li>
 * </ul>
 */
class FlowWithdrawWindowTest {

    private static final long INSTANCE_ID = 7001L;

    private static final long INITIATOR_ID = 501L;

    private static final String REASON = "录入有误，撤回重填";

    private FlowInstanceMapper instanceMapper;

    private FlowNodeInstanceMapper nodeInstanceMapper;

    private FlowTaskMapper taskMapper;

    private FlowRuntimeMapper runtimeMapper;

    private FlowRoutingMapper routingMapper;

    private FlowGateService gateService;

    private FlowThreadWriter threadWriter;

    private AuditLogWriter auditLogWriter;

    private FlowInstanceService instanceService;

    private FlowEngineService engine;

    @BeforeEach
    void setUp() {
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(INITIATOR_ID))
                .thenReturn(Set.of(FlowConfigPermission.FLOW_USE, FlowAction.WITHDRAW.permission()));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        instanceMapper = mock(FlowInstanceMapper.class);
        nodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
        taskMapper = mock(FlowTaskMapper.class);
        runtimeMapper = mock(FlowRuntimeMapper.class);
        routingMapper = mock(FlowRoutingMapper.class);
        gateService = mock(FlowGateService.class);
        // 撤回窗口（2026-10-04 配置化）：本类聚焦**默认口径**（until_finance_approved = REQ-FLOW-009 口径）
        // 下的窗口判据；严格口径（until_finance_started = AC-16 口径）见 WithdrawWindowPolicyTest，
        // 「按实例锁定版本取策略」见 FlowWithdrawLockedVersionTest。
        when(gateService.withdrawWindowOf(any())).thenReturn(WithdrawWindow.UNTIL_FINANCE_APPROVED);
        threadWriter = mock(FlowThreadWriter.class);
        auditLogWriter = mock(AuditLogWriter.class);
        instanceService = mock(FlowInstanceService.class);
        engine = new FlowEngineService(instanceMapper, nodeInstanceMapper, taskMapper, runtimeMapper,
                routingMapper, gateService, threadWriter, gate, mock(ApproverDirectory.class),
                auditLogWriter, instanceService, mock(com.oa.form.app.FormSubmitGate.class));

        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(INITIATOR_ID, "emp01", "员工甲", "T501", 135L, 12L,
                        Set.of("employee"), Set.of(), false))
                .roleCodes(Set.of("employee"))
                .build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ================================================================ 夹具

    private static FlowInstanceRow instance(String status) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-000001");
        row.setFormType("fund");
        row.setInitiatorId(INITIATOR_ID);
        row.setStatus(status);
        return row;
    }

    /** 一条节点实例（{@code seq} / 状态足够表达撤回窗口判据）。 */
    private static FlowNodeInstanceRow node(int seq, String status) {
        FlowNodeInstanceRow row = new FlowNodeInstanceRow();
        row.setId(9000L + seq);
        row.setInstanceId(INSTANCE_ID);
        row.setNodeSeq(seq);
        row.setNodeCode("node" + seq);
        row.setNodeName("节点" + seq);
        row.setStatus(status);
        return row;
    }

    /** 装载「当前实例 = {@code status}」与节点实例集合（并让 {@code selectLiveByInstance} 返回活动节点）。 */
    private void given(String status, List<FlowNodeInstanceRow> nodes) {
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance(status));
        when(nodeInstanceMapper.selectByInstance(INSTANCE_ID)).thenReturn(nodes);
        List<FlowNodeInstanceRow> live = new ArrayList<>();
        for (FlowNodeInstanceRow row : nodes) {
            if (row.getStatus().equals(NodeStatus.ACTIVE.code())
                    || row.getStatus().equals(NodeStatus.WAITING_SUPPLEMENT.code())
                    || row.getStatus().equals(NodeStatus.RETURNED.code())
                    || row.getStatus().equals(NodeStatus.PENDING.code())) {
                live.add(row);
            }
        }
        when(nodeInstanceMapper.selectLiveByInstance(INSTANCE_ID)).thenReturn(live);
    }

    /** 提交后（②未通过）的标准形状：①进行中，②~⑦未开始（一次性物化的产物）。 */
    private static List<FlowNodeInstanceRow> justSubmitted() {
        return List.of(
                node(1, NodeStatus.ACTIVE.code()),
                node(2, NodeStatus.PENDING.code()),
                node(3, NodeStatus.PENDING.code()),
                node(4, NodeStatus.PENDING.code()),
                node(5, NodeStatus.PENDING.code()),
                node(6, NodeStatus.PENDING.code()),
                node(7, NodeStatus.PENDING.code()));
    }

    // ================================================================ ① 允许撤回

    @Test
    @DisplayName("撤回窗口｜提交后（仅①进行中、②~⑦未开始）→ 允许撤回：实例回 draft、活动节点 cancelled、"
            + "轨迹落 withdraw、审计留痕")
    void withdrawIsAllowedRightAfterSubmit() {
        given(InstanceStatus.APPROVING.code(), justSubmitted());

        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();

        // 终态级联语义不变（§7.2 第 4 行）
        verify(nodeInstanceMapper).finish(9001L, NodeStatus.CANCELLED.code());
        verify(taskMapper).closePendingByInstance(INSTANCE_ID);
        verify(runtimeMapper).cancelPendingSupplements(INSTANCE_ID);
        verify(instanceMapper).updateProgress(INSTANCE_ID, "draft", null, null, null);
        verify(threadWriter).append(eq(INSTANCE_ID), eq(null), any(CurrentUser.class),
                eq(ThreadAction.WITHDRAW), eq(REASON));
        verify(auditLogWriter).appendAsCurrentUser(eq("withdraw"), eq("instance"), eq(INSTANCE_ID),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("撤回窗口｜节点②之后的节点已被终态级联取消（历史手工改库的那种形状）→ 不阻止撤回")
    void cancelledNodesBeyondSecondDoNotBlockWithdrawal() {
        given(InstanceStatus.APPROVING.code(), List.of(
                node(1, NodeStatus.ACTIVE.code()),
                node(2, NodeStatus.CANCELLED.code()),
                node(3, NodeStatus.CANCELLED.code()),
                node(4, NodeStatus.CANCELLED.code()),
                node(5, NodeStatus.CANCELLED.code()),
                node(6, NodeStatus.CANCELLED.code()),
                node(7, NodeStatus.CANCELLED.code())));

        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();
        verify(instanceMapper).updateProgress(INSTANCE_ID, "draft", null, null, null);
    }

    @Test
    @DisplayName("撤回窗口｜已驳回的单据：发起人放弃 → 撤回回草稿（prd 附录B：已驳回 → 已撤回 → 草稿）")
    void rejectedInstanceCanStillBeWithdrawn() {
        given(InstanceStatus.REJECTED.code(), List.of(
                node(1, NodeStatus.REJECTED.code()),
                node(2, NodeStatus.CANCELLED.code()),
                node(3, NodeStatus.CANCELLED.code()),
                node(4, NodeStatus.CANCELLED.code()),
                node(5, NodeStatus.CANCELLED.code()),
                node(6, NodeStatus.CANCELLED.code()),
                node(7, NodeStatus.CANCELLED.code())));

        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();
        verify(instanceMapper).updateProgress(INSTANCE_ID, "draft", null, null, null);
    }

    // ================================================================ ② 拒绝撤回

    @Test
    @DisplayName("撤回窗口｜节点②已通过 → 撤回被拒（FLOW_ACTION_NOT_ALLOWED + 原口径文案），且无任何写副作用")
    void withdrawIsRejectedOnceNodeTwoApproved() {
        given(InstanceStatus.APPROVING.code(), List.of(
                node(1, NodeStatus.APPROVED.code()),
                node(2, NodeStatus.APPROVED.code()),
                node(3, NodeStatus.ACTIVE.code()),
                node(4, NodeStatus.PENDING.code()),
                node(5, NodeStatus.PENDING.code()),
                node(6, NodeStatus.PENDING.code()),
                node(7, NodeStatus.PENDING.code())));

        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("撤回仅限「财务部复核（节点②）」通过之前")
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_ACTION_NOT_ALLOWED);
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(40910);
                });

        verifyNoInteractions(threadWriter, auditLogWriter, taskMapper, runtimeMapper);
        verify(instanceMapper, never()).updateProgress(anyLong(), any(), any(), any(), any());
        verify(nodeInstanceMapper, never()).finish(anyLong(), any());
    }

    @Test
    @DisplayName("撤回窗口｜②之后的节点已被推进（③进行中，即便②的实例行缺失/未通过）→ 撤回被拒")
    void withdrawIsRejectedWhenTrunkAdvancedBeyondNodeTwo() {
        given(InstanceStatus.APPROVING.code(), List.of(
                node(1, NodeStatus.APPROVED.code()),
                node(2, NodeStatus.APPROVED.code()),
                node(3, NodeStatus.WAITING_SUPPLEMENT.code()),
                node(4, NodeStatus.PENDING.code())));

        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_ACTION_NOT_ALLOWED));
    }

    @Test
    @DisplayName("撤回窗口｜已终止（终态）不可撤回：否则「终止 → 撤回 → 草稿 → 重提」会绕过 REQ-FLOW-010")
    void terminatedInstanceCannotBeWithdrawn() {
        given(InstanceStatus.TERMINATED.code(), List.of(
                node(1, NodeStatus.CANCELLED.code()),
                node(2, NodeStatus.CANCELLED.code()),
                node(3, NodeStatus.CANCELLED.code())));

        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已处于终态")
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verifyNoInteractions(threadWriter, auditLogWriter, taskMapper, runtimeMapper);
        verify(instanceMapper, never()).updateProgress(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("撤回窗口｜已通过（终态）不可撤回")
    void approvedInstanceCannotBeWithdrawn() {
        given(InstanceStatus.APPROVED.code(), List.of(
                node(1, NodeStatus.APPROVED.code()),
                node(2, NodeStatus.APPROVED.code())));

        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("撤回窗口｜草稿不可撤回（原口径不变）")
    void draftInstanceCannotBeWithdrawn() {
        given(InstanceStatus.DRAFT.code(), List.of());

        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("草稿状态的单据无需撤回");
    }

    // ================================================================ ③ 判据本身的语义（防再次漂移）

    @Test
    @DisplayName("口径锁｜「未到达」= pending 不得阻止撤回；「已到达」= 离开 pending 即阻止（含 waiting_supplement/returned）")
    void pendingIsNotEvidenceOfHavingPassedTheSecondNode() {
        // 只读判定：拿真实引擎 + 假 mapper，逐个状态观察 withdraw 的放行/拒绝
        for (NodeStatus advanced : new NodeStatus[] {NodeStatus.ACTIVE, NodeStatus.WAITING_SUPPLEMENT,
                NodeStatus.RETURNED, NodeStatus.APPROVED, NodeStatus.SKIPPED, NodeStatus.REJECTED}) {
            given(InstanceStatus.APPROVING.code(), List.of(
                    node(1, NodeStatus.APPROVED.code()),
                    node(2, NodeStatus.APPROVED.code()),
                    node(3, advanced.code())));
            assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                    .as("seq>2 的节点处于 %s 时，说明主干已推进过②，必须拒绝撤回", advanced.code())
                    .isInstanceOf(BizException.class);
        }

        // pending 与 cancelled 都不构成「已越过②」
        for (NodeStatus notArrived : new NodeStatus[] {NodeStatus.PENDING, NodeStatus.CANCELLED}) {
            given(InstanceStatus.APPROVING.code(), List.of(
                    node(1, NodeStatus.ACTIVE.code()),
                    node(2, NodeStatus.PENDING.code()),
                    node(3, notArrived.code())));
            assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON))
                    .as("seq>2 的节点处于 %s 时，尚未到达该节点，不得阻止撤回", notArrived.code())
                    .doesNotThrowAnyException();
        }
    }
}
