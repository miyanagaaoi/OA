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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>AC-49 终止端点的两层闸门</b>（纵深防御收紧后的回归网）。
 *
 * <h2>被收紧的是什么</h2>
 * <p>{@code POST /api/v1/flow-instances/{id}/terminate} 的**入口层**原先用
 * {@code WorkflowPermissionService#requireInitiator}（放行 {@code flow} 或 {@code admin:flow}）。
 * {@code flow} 是「门户基础权限」，{@code employee} 等角色经**祖先闭包**也持有 ——
 * 入口因此会放行一个本不该有入口的账号，唯一说不的人变成引擎：行为对，但层次脏。
 *
 * <p>现入口改为 AC-49 专用闸门（系统管理员 ∪ {@code group_leader} 角色 ∪ {@code flow:task:terminate} 权限），
 * 与引擎侧共用同一判据 {@link FlowConfigPermission#isTerminateSubject}，**两层都在**。
 *
 * <h2>本类怎么验</h2>
 * <ol>
 *   <li><b>入口层</b>：真实控制器 + 真实 {@code WorkflowPermissionService}（只把
 *       {@code EffectivePermissionService} 换成 mock）+ 真实 {@code GlobalExceptionHandler}，
 *       走 MockMvc 打**真请求**；断 403 / {@code 40301} / 文案，并用
 *       {@code verifyNoInteractions(engine)} 证明<b>根本没进引擎</b>
 *       （这正是「不再是入口通过后被引擎拒」的机器可判定形态）；</li>
 *   <li><b>引擎层兜底</b>：绕过控制器直调 {@code FlowEngineService#terminate}，
 *       非终止主体仍 403 且**一次 mapper 都没碰**；</li>
 *   <li><b>纯函数真值表</b>：四条并集分支逐条断言，并锁死权限码与
 *       {@code FlowAction.TERMINATE.permission()} 不漂移。</li>
 * </ol>
 */
class FlowTerminateGateTest {

    /** 示例实例 id（本测试不落库，任意值均可）。 */
    private static final long INSTANCE_ID = 9001L;

    private static final String REASON = "数据录入错误，需终止";

    private static final String AC49_MESSAGE = "「终止流程」仅系统管理员与集团分管领导可执行（AC-49 / REQ-FLOW-010）";

    private EffectivePermissionService permissions;

    private WorkflowPermissionService gate;

    private FlowEngineService engine;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        permissions = mock(EffectivePermissionService.class);
        engine = mock(FlowEngineService.class);
        gate = new WorkflowPermissionService(permissions);
        FlowRuntimeController controller =
                new FlowRuntimeController(engine, mock(FlowRuntimeQueryService.class), gate);
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

    private org.springframework.test.web.servlet.ResultActions terminate() throws Exception {
        return mvc.perform(post("/api/v1/flow-instances/{id}/terminate", INSTANCE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + REASON + "\"}"));
    }

    // ================================================================ ① 入口层

    @Test
    @DisplayName("入口层｜employee 持 flow（门户基础权限）→ 入口即 403/40301，引擎一次都没被调用")
    void employeeIsRejectedAtTheEntryGate() throws Exception {
        // 旧实现正是被这里的 flow 放行（employee 经祖先闭包持有它），随后才由引擎返回 403。
        login(user(101L, "emp01", "employee"),
                "flow", "flow:task:approve", "flow:task:reject", "flow:task:withdraw");

        terminate()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(AC49_MESSAGE));

        verifyNoInteractions(engine);
    }

    @Test
    @DisplayName("入口层｜company_admin（持 admin:flow 与 admin:flow:*）→ 入口即 403/40301，不触引擎")
    void companyAdminIsRejectedAtTheEntryGate() throws Exception {
        login(user(102L, "ca01", "company_admin"),
                "admin:flow", "admin:flow:template", "admin:flow:node", "admin:flow:publish");

        terminate()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(AC49_MESSAGE));

        verifyNoInteractions(engine);
    }

    @Test
    @DisplayName("入口层｜group_leader（按角色口径放行，即使权限码里没有 flow:task:terminate）→ 放行且进引擎")
    void groupLeaderPassesOnTheRoleBranchAlone() throws Exception {
        login(user(103L, "gl01", "group_leader"), "flow");

        terminate()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(engine).terminate(INSTANCE_ID, REASON);
    }

    @Test
    @DisplayName("入口层｜admin（isSuperAdmin）即使有效权限码为空也放行 → 进引擎")
    void superAdminPassesWithoutAnyPermissionCode() throws Exception {
        login(user(100L, "admin", "admin"));

        terminate()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(engine).terminate(INSTANCE_ID, REASON);
    }

    // ================================================================ ② 引擎兜底

    @Test
    @DisplayName("引擎兜底｜绕过控制器直调 engine.terminate：employee 仍 403/AC-49，且不触达任何 mapper")
    void engineStillRefusesNonTerminateSubjects() {
        login(user(101L, "emp01", "employee"), "flow", "flow:task:approve");

        FlowInstanceMapper instanceMapper = mock(FlowInstanceMapper.class);
        FlowNodeInstanceMapper nodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
        FlowTaskMapper taskMapper = mock(FlowTaskMapper.class);
        FlowRuntimeMapper runtimeMapper = mock(FlowRuntimeMapper.class);
        FlowRoutingMapper routingMapper = mock(FlowRoutingMapper.class);
        FlowInstanceService instanceService = mock(FlowInstanceService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        FlowEngineService realEngine = new FlowEngineService(instanceMapper, nodeInstanceMapper, taskMapper,
                runtimeMapper, routingMapper, mock(FlowGateService.class), mock(FlowThreadWriter.class),
                gate, mock(ApproverDirectory.class), auditLogWriter, instanceService,
                mock(com.oa.form.app.FormSubmitGate.class));

        assertThatThrownBy(() -> realEngine.terminate(INSTANCE_ID, REASON))
                .isInstanceOf(BizException.class)
                .hasMessage(AC49_MESSAGE)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(403);
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(40301);
                });

        verifyNoInteractions(instanceMapper, nodeInstanceMapper, taskMapper, runtimeMapper, routingMapper,
                auditLogWriter, instanceService);
    }

    // ================================================================ ③ 纯函数真值表

    @Test
    @DisplayName("真值表｜系统管理员 / group_leader 角色 / flow:task:terminate 权限三者并集；其余一律拒")
    void terminateSubjectTruthTable() {
        // 终止权限码本身 → 放行
        assertThat(FlowConfigPermission.isTerminateSubject(false, Set.of(FlowConfigPermission.TERMINATE), false))
                .isTrue();
        // group_leader 角色 → 放行（不需要权限码）
        assertThat(FlowConfigPermission.isTerminateSubject(false, Set.of(), true)).isTrue();
        // 系统管理员 → 放行（不需要角色名与权限码）
        assertThat(FlowConfigPermission.isTerminateSubject(true, Set.of(), false)).isTrue();

        // employee 的祖先闭包（含 flow，但不含终止权限码）→ 拒
        assertThat(FlowConfigPermission.isTerminateSubject(false,
                Set.of("flow", "flow:task:approve", "flow:task:reject"), false)).isFalse();
        // company_admin 的 admin:flow 一族 → 拒
        assertThat(FlowConfigPermission.isTerminateSubject(false,
                Set.of("admin:flow", "admin:flow:template", "admin:flow:node", "admin:flow:publish"), false))
                .isFalse();
        // 无上下文（权限码缺失）→ 拒，不 NPE
        assertThat(FlowConfigPermission.isTerminateSubject(null, null, false)).isFalse();
    }

    @Test
    @DisplayName("真值表｜拒绝时 403/40301 + AC-49 文案；并且权限码与 FlowAction.TERMINATE 不漂移")
    void requireTerminateThrowsForbiddenAndCodeDoesNotDrift() {
        assertThatThrownBy(() -> FlowConfigPermission.requireTerminate(false, Set.of("flow"), false))
                .isInstanceOf(BizException.class)
                .hasMessage(AC49_MESSAGE)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(40301);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(403);
                });

        assertThat(FlowConfigPermission.TERMINATE)
                .as("入口闸门与 FlowAction 的动作面权限码必须同源，否则两层口径会漂移")
                .isEqualTo(FlowAction.TERMINATE.permission());
    }
}
