package com.oa.workflow.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.error.GlobalExceptionHandler;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.FlowInstanceService;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.app.FlowEngineService;
import com.oa.workflow.runtime.app.FlowGateService;
import com.oa.workflow.runtime.app.FlowRuntimeQueryService;
import com.oa.workflow.runtime.app.FlowThreadWriter;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.FlowRoutingMapper;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.FlowTaskMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>撤回 / 抄送两个端点的两层闸门</b>（与 {@code FlowTerminateGateTest} 同族的回归网）。
 *
 * <h2>被收紧的是什么（两个反向的不一致）</h2>
 * <ol>
 *   <li>{@code POST /flow-instances/{id}/withdraw}：入口原为
 *       {@code WorkflowPermissionService#requireInitiator}（放行 {@code flow} 或 {@code admin:flow}），
 *       而引擎只认 {@code FlowAction.WITHDRAW.permission()}（{@code flow:task:withdraw}）——
 *       <b>入口比引擎宽</b>：持 {@code admin:flow} 的 {@code company_admin}
 *       （种子 {@code V4__permissions.sql}：{@code company_admin} 既不持 {@code flow}
 *       也不持 {@code flow:task:withdraw}）会「先过入口、再被引擎 403」；</li>
 *   <li>{@code POST /flow-instances/{id}/cc}：入口同为 {@code requireInitiator}，引擎只认
 *       {@code FlowAction.CC.permission()}（{@code flow}）—— 同一类「入口比引擎宽」。</li>
 * </ol>
 * <p>现两个入口都改为**与引擎同源同参**的动作面权限码：撤回 = {@code flow:task:withdraw}，
 * 抄送 = {@code flow}；引擎侧一行未改（{@code withdraw} 判身份与时间窗，{@code addCc} 判发起人身份）。
 *
 * <h2>分工（权限 vs 身份）</h2>
 * <ul>
 *   <li><b>入口层判权限</b>：不持有该权限码 → 403 / {@code 40301}，请求根本不进引擎，也不读实例
 *       （{@code verifyNoInteractions(engine, queryService)} 就是这条断言的机器可判定形态）；</li>
 *   <li><b>引擎层判身份</b>：发起人本人（或系统管理员）由引擎按 {@code instance.initiatorId} 判。
 *       「有权限但不是发起人」在引擎层被拒，属<b>可接受的第二层</b>（入口层不读实例）。</li>
 * </ul>
 *
 * <h2>本类怎么验</h2>
 * <ol>
 *   <li><b>入口层</b>：真实控制器 + 真实 {@code WorkflowPermissionService}（只把
 *       {@code EffectivePermissionService} 换成 mock）+ 真实 {@code GlobalExceptionHandler}，
 *       MockMvc 打真请求；拒人侧断 403 / {@code 40301} / 文案 + 不触引擎也不触实例读取，
 *       放行侧断 200 + {@code verify(engine)} 真的收到了调用；</li>
 *   <li><b>引擎层兜底</b>：绕过控制器直调 {@code FlowEngineService}，分三种情形 ——
 *       无权限码（读库之前就拒）、非发起人（身份层拒）、持 {@code flow} 的正常入口；
 *       「拒」的两种都断言不产生任何写副作用；</li>
 *   <li><b>口径锁</b>：入口与引擎的权限码同源（{@code FlowAction.CC.permission()} /
 *       {@code FlowAction.WITHDRAW.permission()}），并锁死与 {@code V4} 权限种子不漂移。</li>
 * </ol>
 */
class FlowWithdrawCcGateTest {

    /** 示例实例 id（本测试不落库，任意值均可）。 */
    private static final long INSTANCE_ID = 9101L;

    private static final String REASON = "录入有误，撤回重填";

    private static final List<Long> CC_USER_IDS = List.of(202L);

    /** 撤回入口的期望文案（从动作面取值拼装，避免在测试里重复魔法字符串）。 */
    private static final String WITHDRAW_REJECTED =
            "无权执行「" + FlowAction.WITHDRAW.label() + "」：需要权限 " + FlowAction.WITHDRAW.permission();

    /** 抄送入口的期望文案。 */
    private static final String CC_REJECTED =
            "无权执行「" + FlowAction.CC.label() + "」：需要权限 " + FlowAction.CC.permission();

    private EffectivePermissionService permissions;

    private WorkflowPermissionService gate;

    private FlowEngineService engine;

    /** 实例读取侧（引擎之外唯一的读库入口）：入口拒人时它必须一次都没被碰。 */
    private FlowRuntimeQueryService queryService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        permissions = mock(EffectivePermissionService.class);
        engine = mock(FlowEngineService.class);
        queryService = mock(FlowRuntimeQueryService.class);
        gate = new WorkflowPermissionService(permissions);
        FlowRuntimeController controller = new FlowRuntimeController(engine, queryService, gate);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ================================================================ 夹具

    private static CurrentUser user(long id, String account, String... roleCodes) {
        return CurrentUser.of(id, account, account, "T" + id, 135L, 12L, Set.of(roleCodes), Set.of(), false);
    }

    /** 装载登录上下文，并按「有效权限码」口径 stub 掉 {@code EffectivePermissionService}。 */
    private void login(CurrentUser principal, String... permissionCodes) {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(principal.roleCodes())
                .build());
        when(permissions.permissionCodes(principal.id())).thenReturn(Set.of(permissionCodes));
    }

    private ResultActions withdraw() throws Exception {
        return mvc.perform(post("/api/v1/flow-instances/{id}/withdraw", INSTANCE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + REASON + "\"}"));
    }

    private ResultActions cc() throws Exception {
        return mvc.perform(post("/api/v1/flow-instances/{id}/cc", INSTANCE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[202]}"));
    }

    /** 组装一个「真引擎 + 全 mock 基础设施」，用于验证绕过控制器时的兜底行为。 */
    private EngineFixture realEngine() {
        FlowInstanceMapper instanceMapper = mock(FlowInstanceMapper.class);
        FlowNodeInstanceMapper nodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowRuntimeMapper runtimeMapper = mock(FlowRuntimeMapper.class);
        FlowRoutingMapper routingMapper = mock(FlowRoutingMapper.class);
        FlowGateService gateService = mock(FlowGateService.class);
        FlowThreadWriter threadWriter = mock(FlowThreadWriter.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        FlowInstanceService instanceService = mock(FlowInstanceService.class);
        FlowEngineService service = new FlowEngineService(instanceMapper, nodeInstanceMapper, taskMapper,
                runtimeMapper, routingMapper, gateService, threadWriter, gate, mock(ApproverDirectory.class),
                auditLogWriter, instanceService);
        return new EngineFixture(service, instanceMapper, nodeInstanceMapper, taskMapper, runtimeMapper,
                routingMapper, gateService, threadWriter, auditLogWriter, instanceService);
    }

    private record EngineFixture(FlowEngineService service, FlowInstanceMapper instanceMapper,
                                 FlowNodeInstanceMapper nodeInstanceMapper, FlowTaskMapper taskMapper,
                                 FlowRuntimeMapper runtimeMapper, FlowRoutingMapper routingMapper,
                                 FlowGateService gateService, FlowThreadWriter threadWriter,
                                 AuditLogWriter auditLogWriter, FlowInstanceService instanceService) {

        /** 入口闸门拒绝时「一次都不该被碰」的全部协作者（撤回与抄送共用同一组）。 */
        Object[] untouchedOnEntryGateRejection() {
            return new Object[] {instanceMapper, nodeInstanceMapper, taskMapper, runtimeMapper, routingMapper,
                    gateService, threadWriter, auditLogWriter, instanceService};
        }
    }

    // ================================================================ ① 入口层：撤回

    @Test
    @DisplayName("入口层｜撤回：employee 只持 flow（不持 flow:task:withdraw）→ 入口即 403/40301，引擎一次都没被调用")
    void withdrawIsRejectedAtTheEntryGateWithoutTheActionPermission() throws Exception {
        // 改前正是被 requireInitiator 的 flow 分支放行，拒人只剩引擎一层。
        login(user(101L, "emp01", "employee"),
                FlowConfigPermission.FLOW_USE, "flow:task:approve", "flow:task:reject");

        withdraw()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(WITHDRAW_REJECTED));

        // 出参只回 code/message（AC-41 不泄露内部判据）；requiredPermissions 只进服务端日志，
        // 因此运行期证据要同时取「HTTP 响应 + 应用日志的 detail=」两处。
        // 引擎与实例读取侧都一次没被碰 —— 「拒在入口层、读库之前」的机器可判定形态。
        verifyNoInteractions(engine, queryService);
    }

    @Test
    @DisplayName("入口层｜撤回：company_admin（持 admin:flow，种子中不持 flow / flow:task:withdraw）→ 入口即 403，不触引擎")
    void companyAdminIsRejectedAtTheWithdrawEntryGate() throws Exception {
        // 这是「入口比引擎宽」的真实形态：admin:flow 过得了 requireInitiator，过不了引擎的 flow:task:withdraw。
        login(user(102L, "ca01", "company_admin"),
                FlowConfigPermission.FLOW_ADMIN, "admin:flow:template", "admin:flow:node", "admin:flow:publish");

        withdraw()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(WITHDRAW_REJECTED));

        verifyNoInteractions(engine, queryService);
    }

    @Test
    @DisplayName("入口层｜撤回：持 flow:task:withdraw 的发起人 → 放行且进引擎（身份判定留给引擎）")
    void initiatorWithTheWithdrawPermissionReachesTheEngine() throws Exception {
        login(user(101L, "emp01", "employee"),
                FlowConfigPermission.FLOW_USE, FlowAction.WITHDRAW.permission());

        withdraw()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(engine).withdraw(INSTANCE_ID, REASON);
    }

    // ================================================================ ① 入口层：抄送

    @Test
    @DisplayName("入口层｜抄送：company_admin（持 admin:flow，不持 flow）→ 入口即 403/40301，不再「过入口后被引擎拒」")
    void companyAdminIsRejectedAtTheCcEntryGate() throws Exception {
        login(user(102L, "ca01", "company_admin"),
                FlowConfigPermission.FLOW_ADMIN, "admin:flow:template", "admin:flow:node", "admin:flow:publish");

        cc()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(CC_REJECTED));

        verifyNoInteractions(engine, queryService);
    }

    @Test
    @DisplayName("入口层｜抄送：持 flow 的发起人 → 放行且进引擎")
    void initiatorWithFlowReachesTheEngineForCc() throws Exception {
        login(user(101L, "emp01", "employee"), FlowConfigPermission.FLOW_USE);

        cc()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(engine).addCc(INSTANCE_ID, CC_USER_IDS);
    }

    @Test
    @DisplayName("入口层｜admin（isSuperAdmin）即使有效权限码为空：撤回与抄送两条都放行")
    void superAdminPassesBothEntries() throws Exception {
        login(user(100L, "admin", "admin"));

        withdraw()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        cc()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(engine).withdraw(INSTANCE_ID, REASON);
        verify(engine).addCc(INSTANCE_ID, CC_USER_IDS);
    }

    // ================================================================ ② 引擎层兜底（绕过控制器）

    @Test
    @DisplayName("引擎兜底｜撤回：无 flow:task:withdraw 者直调服务仍 403，且读库之前就拒（不触达任何协作者）")
    void engineStillRefusesWithdrawWithoutTheActionPermission() {
        login(user(101L, "emp01", "employee"), FlowConfigPermission.FLOW_USE);
        EngineFixture fixture = realEngine();

        assertThatThrownBy(() -> fixture.service().withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessage(WITHDRAW_REJECTED)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(40301);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(403);
                });

        verifyNoInteractions(fixture.untouchedOnEntryGateRejection());
    }

    @Test
    @DisplayName("引擎兜底｜撤回：持权限但非发起人 → 引擎身份层 403（入口放行不等于业务放行），且无任何写副作用")
    void engineStillRefusesWithdrawByANonInitiatorHoldingThePermission() {
        login(user(101L, "emp01", "employee"),
                FlowConfigPermission.FLOW_USE, FlowAction.WITHDRAW.permission());
        EngineFixture fixture = realEngine();
        FlowInstanceRow someoneElses = new FlowInstanceRow();
        someoneElses.setId(INSTANCE_ID);
        someoneElses.setInitiatorId(999L);
        when(fixture.instanceMapper().selectInstanceById(INSTANCE_ID)).thenReturn(someoneElses);

        assertThatThrownBy(() -> fixture.service().withdraw(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessage("只有发起人本人（或系统管理员）可以撤回该单据")
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        // 只读了实例这一行：节点/任务/轨迹/审计/补件一次都没碰（状态机与时间窗都还没走到）。
        verify(fixture.instanceMapper()).selectInstanceById(INSTANCE_ID);
        verifyNoInteractions(fixture.nodeInstanceMapper(), fixture.taskMapper(), fixture.runtimeMapper(),
                fixture.routingMapper(), fixture.gateService(), fixture.threadWriter(),
                fixture.auditLogWriter(), fixture.instanceService());
    }

    @Test
    @DisplayName("引擎兜底｜抄送：company_admin（仅 admin:flow）直调服务仍 403，且读库之前就拒")
    void engineStillRefusesCcWithoutFlow() {
        login(user(102L, "ca01", "company_admin"),
                FlowConfigPermission.FLOW_ADMIN, "admin:flow:template");
        EngineFixture fixture = realEngine();

        assertThatThrownBy(() -> fixture.service().addCc(INSTANCE_ID, CC_USER_IDS))
                .isInstanceOf(BizException.class)
                .hasMessage(CC_REJECTED)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(40301);
                });

        verifyNoInteractions(fixture.untouchedOnEntryGateRejection());
    }

    // ================================================================ ③ 口径锁

    @Test
    @DisplayName("口径锁｜入口与引擎同源：cc 的权限码 = flow（门户基础包），withdraw 的权限码 = flow:task:withdraw（V4 种子）")
    void entryGateCodesMatchTheActionFaceAndTheSeed() {
        assertThat(FlowAction.CC.permission())
                .as("抄送入口闸门直接取 FlowAction.CC.permission()，必须仍是门户基础权限 flow")
                .isEqualTo(FlowConfigPermission.FLOW_USE);
        assertThat(FlowAction.WITHDRAW.permission())
                .as("撤回入口闸门直接取 FlowAction.WITHDRAW.permission()，必须与 V4__permissions.sql 的种子权限码一致")
                .isEqualTo("flow:task:withdraw");
        assertThat(FlowAction.CC.permission())
                .as("抄送不再是 requireInitiator（flow ∪ admin:flow）——admin:flow 不在引擎口径内")
                .isNotEqualTo(FlowConfigPermission.FLOW_ADMIN);
    }
}
