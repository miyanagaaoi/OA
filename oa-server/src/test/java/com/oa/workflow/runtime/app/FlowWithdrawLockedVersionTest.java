package com.oa.workflow.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.oa.workflow.definition.domain.FlowGatePolicy;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>撤回窗口必须按「实例发起时锁定的模板版本」取策略 —— AC-09 的核心断言</b>（2026-10-04）。
 *
 * <h2>被防的错误</h2>
 * <p>若 {@code FlowEngineService#withdrawAllowed} 去读**当前 published 模板**，
 * 「管理员把模板改成宽松口径」会立刻改变**所有在途单据**的撤回窗口 —— 直接违反 AC-09
 * （已发起实例按发起时的版本与审批人快照执行）与 templates.md V-02。
 *
 * <p>因此本类**不使用 mock 的 {@link FlowGateService}**，而是装配**真实的** {@code FlowGateService}
 * 与 mock 的 {@link FlowTemplateMapper}，把「引擎 → GateService → 按 {@code flow_instance.template_id} 查模板行」
 * 这条**完整取数路径**跑起来，再显式断言：
 * <ol>
 *   <li>读到的是**锁定版本**那一行（{@code selectTemplateById(instance.templateId)}）；</li>
 *   <li>**从不**调用 {@code selectByCodeAndStatus(code, "published")}（当前已发布版本）、
 *       也不调用 {@code selectByCodeAndVersion(...)} 之外的任何其它读法；</li>
 *   <li>把「当前 published」换成反口径后，在途实例的判定结果**一个字节都不变**（双向都测）。</li>
 * </ol>
 *
 * <p>这正是 {@code FlowGateService#policyOf}（Q6/Q7）既有的取数方式，本任务**复用同一模式**，
 * 不另造一套（见 {@code FlowGateService} 的类注释）。
 */
class FlowWithdrawLockedVersionTest {

    private static final long INSTANCE_ID = 9101L;

    private static final long INITIATOR_ID = 511L;

    /** 实例发起时**锁定的**模板行 id（严格口径 v1）。 */
    private static final long LOCKED_STRICT_ID = 41L;

    /** 同单据类型的**当前已发布**版本 id（宽松口径 v2）。 */
    private static final long PUBLISHED_LOOSE_ID = 42L;

    /** 实例发起时**锁定的**模板行 id（宽松口径 v1）。 */
    private static final long LOCKED_LOOSE_ID = 51L;

    /** 同单据类型的**当前已发布**版本 id（严格口径 v2）。 */
    private static final long PUBLISHED_STRICT_ID = 52L;

    private static final String REASON = "录入有误，撤回重填";

    private FlowInstanceMapper instanceMapper;

    private FlowNodeInstanceMapper nodeInstanceMapper;

    private FlowTemplateMapper templateMapper;

    private FlowGateService gateService;

    private FlowEngineService engine;

    @BeforeEach
    void setUp() {
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(INITIATOR_ID))
                .thenReturn(Set.of(FlowConfigPermission.FLOW_USE, FlowAction.WITHDRAW.permission()));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        instanceMapper = mock(FlowInstanceMapper.class);
        nodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
        templateMapper = mock(FlowTemplateMapper.class);
        // 真实 GateService（不是 mock）：取数路径必须被真实执行
        gateService = new FlowGateService(templateMapper);
        engine = new FlowEngineService(instanceMapper, nodeInstanceMapper, mock(FlowTaskMapper.class),
                mock(FlowRuntimeMapper.class), mock(FlowRoutingMapper.class), gateService,
                mock(FlowThreadWriter.class), gate, mock(ApproverDirectory.class),
                mock(AuditLogWriter.class), mock(FlowInstanceService.class));

        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(INITIATOR_ID, "emp11", "员工乙", "T511", 135L, 12L,
                        Set.of("employee"), Set.of(), false))
                .roleCodes(Set.of("employee"))
                .build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ================================================================ 夹具

    /** 模板行（只填撤回窗口与标识；Q6/Q7 取默认值以免干扰）。 */
    private static FlowTemplate template(long id, String code, int version, String status,
                                        WithdrawWindow window) {
        FlowTemplate template = new FlowTemplate();
        template.setId(id);
        template.setCode(code);
        template.setVersion(version);
        template.setStatus(status);
        template.applyGatePolicy(FlowGatePolicy.v04Defaults().withWithdrawWindow(window));
        return template;
    }

    private static FlowInstanceRow instance(long templateId) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-900001");
        row.setFormType("fund");
        row.setInitiatorId(INITIATOR_ID);
        row.setStatus(InstanceStatus.APPROVING.code());
        row.setTemplateId(templateId);
        return row;
    }

    private static FlowNodeInstanceRow node(int seq, NodeStatus status) {
        FlowNodeInstanceRow row = new FlowNodeInstanceRow();
        row.setId(9900L + seq);
        row.setInstanceId(INSTANCE_ID);
        row.setNodeSeq(seq);
        row.setNodeCode("node" + seq);
        row.setNodeName("节点" + seq);
        row.setStatus(status.code());
        return row;
    }

    /** 停在②审批中（② active，③~⑦ 未开始 = 一次性物化后的形状）。 */
    private static List<FlowNodeInstanceRow> atFinanceActive() {
        return List.of(
                node(1, NodeStatus.APPROVED),
                node(2, NodeStatus.ACTIVE),
                node(3, NodeStatus.PENDING),
                node(4, NodeStatus.PENDING),
                node(5, NodeStatus.PENDING),
                node(6, NodeStatus.PENDING),
                node(7, NodeStatus.PENDING));
    }

    private void given(long lockedTemplateId, List<FlowNodeInstanceRow> nodes) {
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance(lockedTemplateId));
        when(nodeInstanceMapper.selectByInstance(INSTANCE_ID)).thenReturn(nodes);
        // 活动节点的口径与 FlowWithdrawWindowTest 一致（终态级联只处理未完成节点）
        List<FlowNodeInstanceRow> live = new java.util.ArrayList<>();
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

    // ================================================================ ① 锁定严格版本 → 在途一律严格

    @Test
    @DisplayName("锁版本｜发起时锁 v1（严格口径）：②审批中 → 40910；此后把「当前 published」改成宽松，在途**仍严格**")
    void lockedStrictVersionWinsAfterTemplateIsRelaxed() {
        FlowTemplate lockedStrict = template(LOCKED_STRICT_ID, "fund", 1, "published",
                WithdrawWindow.UNTIL_FINANCE_STARTED);
        // 同 code 的「当前已发布版本」= v2 宽松口径（管理员后来改的）
        FlowTemplate publishedLoose = template(PUBLISHED_LOOSE_ID, "fund", 2, "published",
                WithdrawWindow.UNTIL_FINANCE_APPROVED);

        when(templateMapper.selectTemplateById(LOCKED_STRICT_ID)).thenReturn(lockedStrict);
        when(templateMapper.selectTemplateById(PUBLISHED_LOOSE_ID)).thenReturn(publishedLoose);
        when(templateMapper.selectByCodeAndStatus("fund", "published")).thenReturn(List.of(publishedLoose));
        when(templateMapper.selectByCode("fund")).thenReturn(List.of(publishedLoose, lockedStrict));

        given(LOCKED_STRICT_ID, atFinanceActive());

        // ① 严格口径生效：② active 即不可撤回
        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("until_finance_started")
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_ACTION_NOT_ALLOWED);
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(40910);
                });

        // ② 取数路径断言：读的是**锁定行的 id**，从不读「当前 published」
        verify(templateMapper, atLeastOnce()).selectTemplateById(LOCKED_STRICT_ID);
        verify(templateMapper, never()).selectTemplateById(PUBLISHED_LOOSE_ID);
        verify(templateMapper, never()).selectByCodeAndStatus(anyString(), anyString());
        verify(templateMapper, never()).selectByCodeAndVersion(anyString(), any());
        verify(templateMapper, never()).selectByCode(anyString());
        verify(templateMapper, never()).selectTemplates(any(), any(), any());
        // ③ 拒绝路径不得有任何写副作用
        verify(instanceMapper, never()).updateProgress(anyLong(), any(), any(), any(), any());
        assertThat(gateService.withdrawWindowOf(instance(LOCKED_STRICT_ID)))
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_STARTED);
    }

    // ================================================================ ② 锁定宽松版本 → 在途一律宽松

    @Test
    @DisplayName("锁版本｜发起时锁 v1（默认口径）：②审批中 → 允许撤回回 draft；此后把 published 改成严格，在途**仍宽松**")
    void lockedDefaultVersionStaysLooseAfterTemplateIsTightened() {
        FlowTemplate lockedLoose = template(LOCKED_LOOSE_ID, "fund", 1, "published",
                WithdrawWindow.UNTIL_FINANCE_APPROVED);
        FlowTemplate publishedStrict = template(PUBLISHED_STRICT_ID, "fund", 2, "published",
                WithdrawWindow.UNTIL_FINANCE_STARTED);

        when(templateMapper.selectTemplateById(LOCKED_LOOSE_ID)).thenReturn(lockedLoose);
        when(templateMapper.selectTemplateById(PUBLISHED_STRICT_ID)).thenReturn(publishedStrict);
        when(templateMapper.selectByCodeAndStatus("fund", "published")).thenReturn(List.of(publishedStrict));

        given(LOCKED_LOOSE_ID, atFinanceActive());

        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();
        // 终态级联语义不变：②进行中 → cancelled，实例回 draft
        verify(nodeInstanceMapper).finish(9902L, NodeStatus.CANCELLED.code());
        verify(instanceMapper).updateProgress(INSTANCE_ID, "draft", null, null, null);

        verify(templateMapper).selectTemplateById(LOCKED_LOOSE_ID);
        verify(templateMapper, never()).selectTemplateById(PUBLISHED_STRICT_ID);
        verify(templateMapper, never()).selectByCodeAndStatus(anyString(), anyString());
    }

    // ================================================================ ③ NULL 列 = 默认口径（历史数据）

    @Test
    @DisplayName("锁版本｜锁定行的 withdraw_window 为 NULL（历史数据/种子）→ 取默认口径，②审批中可撤")
    void lockedRowWithNullColumnFallsBackToDefaultWindow() {
        FlowTemplate legacyRow = template(LOCKED_LOOSE_ID, "fund", 1, "published", null);
        assertThat(legacyRow.getWithdrawWindow()).isNull();
        when(templateMapper.selectTemplateById(LOCKED_LOOSE_ID)).thenReturn(legacyRow);

        given(LOCKED_LOOSE_ID, atFinanceActive());

        assertThat(gateService.withdrawWindowOf(instance(LOCKED_LOOSE_ID)))
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_APPROVED);
        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("锁版本｜锁定模板行缺失 → 默认口径降级（不阻断撤回，与 Q6/Q7 的降级口径同族）")
    void missingLockedRowFallsBackToDefaultWindow() {
        when(templateMapper.selectTemplateById(LOCKED_STRICT_ID)).thenReturn(null);
        given(LOCKED_STRICT_ID, atFinanceActive());

        assertThat(gateService.withdrawWindowOf(instance(LOCKED_STRICT_ID)))
                .isEqualTo(WithdrawWindow.UNTIL_FINANCE_APPROVED);
        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();
    }

    // ================================================================ ④ 严格口径下的其它档位不得误伤

    @Test
    @DisplayName("锁版本｜严格口径下②仍为 pending（未开始）→ 允许撤回（严格口径只收紧「②已开始处理」）")
    void strictWindowStillAllowsWithdrawBeforeFinanceStarts() {
        FlowTemplate lockedStrict = template(LOCKED_STRICT_ID, "fund", 1, "published",
                WithdrawWindow.UNTIL_FINANCE_STARTED);
        when(templateMapper.selectTemplateById(LOCKED_STRICT_ID)).thenReturn(lockedStrict);

        given(LOCKED_STRICT_ID, List.of(
                node(1, NodeStatus.ACTIVE),
                node(2, NodeStatus.PENDING),
                node(3, NodeStatus.PENDING),
                node(4, NodeStatus.PENDING)));

        assertThatCode(() -> engine.withdraw(INSTANCE_ID, REASON)).doesNotThrowAnyException();
        verify(instanceMapper).updateProgress(INSTANCE_ID, "draft", null, null, null);
    }

    @Test
    @DisplayName("锁版本｜两种口径都不得削弱「②已通过一律拒」：②approved + 锁定宽松版本 → 仍拒绝")
    void financeApprovedAlwaysRejectedEvenUnderDefaultWindow() {
        FlowTemplate lockedLoose = template(LOCKED_LOOSE_ID, "fund", 1, "published",
                WithdrawWindow.UNTIL_FINANCE_APPROVED);
        when(templateMapper.selectTemplateById(LOCKED_LOOSE_ID)).thenReturn(lockedLoose);

        given(LOCKED_LOOSE_ID, List.of(
                node(1, NodeStatus.APPROVED),
                node(2, NodeStatus.APPROVED),
                node(3, NodeStatus.ACTIVE)));

        assertThatThrownBy(() -> engine.withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_ACTION_NOT_ALLOWED));
    }
}
