package com.oa.workflow.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * <b>引擎侧 4 个发起人入口的权限码收敛（F 项，2026-10-04）</b> —— 「入口 ⊂ 引擎」的不对称被拉平。
 *
 * <h2>被收敛的是什么</h2>
 * <p>控制器入口（{@code FlowRuntimeController} 的 {@code reopen} / {@code resubmit} / {@code supplement}）
 * 已按 {@code FlowAction.XXX.permission()} 收紧，而 {@code FlowEngineService} 里
 * {@code submit} / {@code reopen} / {@code resubmit} 仍用
 * {@code requireInitiator}（放行 {@code flow} ∪ {@code admin:flow}）——
 * <b>入口比引擎窄</b>：只持 {@code admin:flow} 的 {@code company_admin} 过不了入口，却过得了引擎第一层；
 * 反过来任何绕过控制器直调服务的路径都拿到了一道更宽的门。收敛后两层同参同源。
 *
 * <h2>本轮取证与任务描述的差异（已按真源修正）</h2>
 * <p>任务描述写的是「reopen / resubmit / supplement / submit 仍用 requireInitiator」，
 * 实测 {@code supplementSubmit} 在改前**已经**是
 * {@code requirePermission(FlowAction.SUPPLEMENT_SUBMIT.label(), …permission())}
 * （{@code FlowEngineService} 的 {@code supplementSubmit} 首行），因此本轮只改了
 * {@code submit} / {@code reopen} / {@code resubmit} 三处；{@code supplementSubmit} 以
 * 「已经在闸门上」的形态纳入本测试矩阵，防它被改回去。
 *
 * <h2>身份判定不得削弱</h2>
 * <p>{@code requireInitiatorOrAdmin}（发起人本人 ∪ 系统管理员）**一行未动**。因此矩阵是三态：
 * ① 无动作码 → 403（且 mapper 零交互）；② 有动作码但非发起人 → 403（身份层）；
 * ③ 有动作码且是发起人 → 进入状态机（拿到状态类错误即证明前两层都过了）。
 */
class FlowEngineInitiatorGateTest {

    private static final long INSTANCE_ID = 9101L;

    private static final long INITIATOR_ID = 501L;

    /** 另一个真实存在的账号（不是这张单的发起人）。 */
    private static final long OTHER_USER_ID = 502L;

    private FlowInstanceMapper instanceMapper;
    private FlowEngineService engine;

    @BeforeEach
    void setUp() {
        instanceMapper = mock(FlowInstanceMapper.class);
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance(InstanceStatus.DRAFT.code()));
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    /** 一个入口动作：引擎方法 + 它必须使用的动作码 + 「放行后应到达的状态机分支」。 */
    private record Entry(String name, FlowAction action, Consumer<String> call, String reachedStateError) {

        @Override
        public String toString() {
            return name + " → " + action.permission();
        }
    }

    private static FlowInstanceRow instance(String status) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-000001");
        row.setFormType("matter");
        row.setInitiatorId(INITIATOR_ID);
        row.setStatus(status);
        row.setApproverSnapshotJson("{}");
        return row;
    }

    /** 四个入口：动作码一律取 {@code FlowAction.XXX.permission()}（单一真源）。 */
    private static final Map<String, FlowAction> ENTRIES = new LinkedHashMap<>();

    static {
        ENTRIES.put("submit", FlowAction.SUBMIT);
        ENTRIES.put("reopen", FlowAction.REOPEN);
        ENTRIES.put("resubmit", FlowAction.SUBMIT);
        ENTRIES.put("supplementSubmit", FlowAction.SUPPLEMENT_SUBMIT);
    }

    /** 用「动作码」驱动的引擎装配（每个用例一个主体权限集）。 */
    private void boot(long principalId, String... permissionCodes) {
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        // 去重：多个动作共用 flow（SUBMIT / REOPEN / SUPPLEMENT_SUBMIT / CC），Set.of 不接受重复元素
        when(permissions.permissionCodes(principalId))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.Arrays.asList(permissionCodes)));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        engine = new FlowEngineService(instanceMapper, mock(FlowNodeInstanceMapper.class),
                mock(FlowTaskMapper.class), mock(FlowRuntimeMapper.class), mock(FlowRoutingMapper.class),
                mock(FlowGateService.class), mock(FlowThreadWriter.class), gate,
                mock(ApproverDirectory.class), mock(AuditLogWriter.class), mock(FlowInstanceService.class));
        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(principalId, "u" + principalId, "用户" + principalId,
                        "T" + principalId, 135L, 12L, Set.of("employee"), Set.of(), false))
                .roleCodes(Set.of("employee"))
                .build());
    }

    private void invoke(String entry) {
        switch (entry) {
            case "submit" -> engine.submit(INSTANCE_ID, null);
            case "reopen" -> engine.reopen(INSTANCE_ID);
            case "resubmit" -> engine.resubmit(INSTANCE_ID, null);
            case "supplementSubmit" -> engine.supplementSubmit(INSTANCE_ID, null);
            default -> throw new IllegalStateException("未知入口：" + entry);
        }
    }

    static Stream<String> entries() {
        return ENTRIES.keySet().stream();
    }

    // ================================================================ ① 权限层

    @ParameterizedTest(name = "[{index}] 引擎 {0}｜只持 admin:flow（改前被 requireInitiator 放行）→ 403，且不读实例")
    @MethodSource("entries")
    @DisplayName("引擎层｜4 个入口：动作码 = FlowAction.XXX.permission()，admin:flow 一律不放过")
    void engineEntriesRejectAdminFlowOnlyPrincipal(String entry) {
        FlowAction action = ENTRIES.get(entry);
        // 「除本动作码以外全都有」——含 admin:flow（改前正是它的分支放行），但不含本动作的权限码
        Set<String> codes = new java.util.LinkedHashSet<>();
        for (FlowAction other : FlowAction.values()) {
            codes.add(other.permission());
        }
        codes.add(FlowConfigPermission.FLOW_ADMIN);
        codes.remove(action.permission());
        boot(INITIATOR_ID, codes.toArray(String[]::new));

        assertThatThrownBy(() -> invoke(entry))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("无权执行「" + action.label() + "」")
                .hasMessageContaining(action.permission());

        verifyNoInteractions(instanceMapper);
    }

    @Test
    @DisplayName("引擎层｜对照表锁：4 个入口的闸门权限码逐行锁定（submit/resubmit 同为 flow）")
    void engineGateTableIsLocked() {
        assertThat(ENTRIES).containsExactly(
                Map.entry("submit", FlowAction.SUBMIT),
                Map.entry("reopen", FlowAction.REOPEN),
                Map.entry("resubmit", FlowAction.SUBMIT),
                Map.entry("supplementSubmit", FlowAction.SUPPLEMENT_SUBMIT));
        assertThat(FlowAction.SUBMIT.permission()).isEqualTo("flow");
        assertThat(FlowAction.REOPEN.permission()).isEqualTo("flow");
        assertThat(FlowAction.SUPPLEMENT_SUBMIT.permission()).isEqualTo("flow");
    }

    // ================================================================ ② 身份层（不得削弱）

    @Test
    @DisplayName("引擎层｜持动作码但**不是发起人** → 403「只有发起人本人」（身份判定原样保留）")
    void engineEntriesStillEnforceInitiatorIdentity() {
        boot(OTHER_USER_ID, FlowConfigPermission.FLOW_USE, FlowAction.SUPPLEMENT_SUBMIT.permission());

        for (String entry : ENTRIES.keySet()) {
            assertThatThrownBy(() -> invoke(entry))
                    .as("引擎 %s 的身份层必须仍在", entry)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("只有发起人本人");
        }
    }

    @Test
    @DisplayName("引擎层｜系统管理员（admin 角色）即使不是发起人也放行到状态机（与改前一致）")
    void superAdminStillPassesIdentityLayer() {
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(OTHER_USER_ID)).thenReturn(Set.of());
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        engine = new FlowEngineService(instanceMapper, mock(FlowNodeInstanceMapper.class),
                mock(FlowTaskMapper.class), mock(FlowRuntimeMapper.class), mock(FlowRoutingMapper.class),
                mock(FlowGateService.class), mock(FlowThreadWriter.class), gate,
                mock(ApproverDirectory.class), mock(AuditLogWriter.class), mock(FlowInstanceService.class));
        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(OTHER_USER_ID, "admin2", "管理员", "T502", 135L, 12L,
                        Set.of("admin"), Set.of(), false))
                .roleCodes(Set.of("admin"))
                .build());

        // 实例是 draft → reopen 幂等返回视图（证明权限层与身份层都放行）
        assertThat(engine.reopen(INSTANCE_ID).status()).isEqualTo(InstanceStatus.DRAFT.code());
    }

    // ================================================================ ③ 放行侧：进入状态机

    @Test
    @DisplayName("引擎层｜持动作码且是发起人 → 进入状态机（拿到状态类错误 = 前两层都过了）")
    void engineEntriesReachStateMachine() {
        boot(INITIATOR_ID, FlowConfigPermission.FLOW_USE, FlowAction.SUPPLEMENT_SUBMIT.permission());

        // submit：快照为空 → 409（不是 403）
        assertThatThrownBy(() -> engine.submit(INSTANCE_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("审批人快照为空")
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        // resubmit：实例为 draft 且快照为空 → 同样走到状态机（409）
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(instance(InstanceStatus.DRAFT.code()));
        assertThatThrownBy(() -> engine.resubmit(INSTANCE_ID, null)).isInstanceOf(BizException.class);

        // supplementSubmit：不在待补件状态 → 409
        when(instanceMapper.selectInstanceById(INSTANCE_ID))
                .thenReturn(instance(InstanceStatus.APPROVING.code()));
        assertThatThrownBy(() -> engine.supplementSubmit(INSTANCE_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不在待补件状态")
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }
}
