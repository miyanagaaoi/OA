package com.oa.workflow.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.error.ErrorCode;
import com.oa.common.error.GlobalExceptionHandler;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.app.FlowEngineService;
import com.oa.workflow.runtime.app.FlowRuntimeQueryService;
import com.oa.workflow.runtime.domain.FlowAction;
import com.oa.workflow.task.api.FlowTaskController;
import com.oa.workflow.task.app.FlowTaskService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>任务动作端点的入口闸门收敛（14 个端点一次到位）——「动作 → 入口权限码」的可执行对照表</b>。
 *
 * <h2>被收敛的是什么</h2>
 * <p>改前：{@code FlowTaskController} 的 11 个动作端点与 {@code FlowRuntimeController} 的
 * {@code reopen} / {@code resubmit} / {@code supplement} 都用
 * {@code WorkflowPermissionService#requireInitiator}（放行 {@code flow} ∪ {@code admin:flow}），
 * 而引擎按**动作级** {@code FlowAction.XXX.permission()} 复核 —— 入口比引擎宽，
 * 持 {@code admin:flow} 的 {@code company_admin}（种子 V4：既不持 {@code flow}
 * 也不持任何 {@code flow:task:*}）会「先过入口、再被引擎 403」，**层次不干净**。
 *
 * <p>现在：入口与引擎取**同一个动作权限码**（单一真源 {@link FlowAction#permission()}），
 * 与上一轮已改好的 {@code terminate}（AC-49 专用判据）/ {@code withdraw} / {@code cc} 同一范式。
 *
 * <h2>两层各管什么（纵深防御，不合并）</h2>
 * <ul>
 *   <li><b>入口层</b>（本测试打的是它）：不持有该动作码 → 403 / {@code 40301}，
 *       请求**不进引擎**、也不读实例 —— 机器可判定形态就是
 *       {@code verifyNoInteractions(engine, taskService, queryService)}；</li>
 *   <li><b>引擎层</b>：同一动作码复核 + 「任务本人」（{@code requireAssignee}）+
 *       实例/节点/任务状态机。入口放行不等于业务放行，因此本类的放行侧只断言
 *       「入口把请求交给了引擎」，不假装覆盖引擎的业务判定。</li>
 * </ul>
 *
 * <h2>刻意保持现状的例外（逐个说明，不为了整齐而误伤）</h2>
 * <ol>
 *   <li><b>{@code POST /flow-instances/{id}/terminate}</b>：保留 AC-49 专用闸门
 *       {@code requireTerminate}（系统管理员 ∪ {@code group_leader} 角色 ∪
 *       {@code flow:task:terminate} 权限）。它的权限码与引擎判据**天然不同**：
 *       {@code flow:task:terminate} 只是三者之一，单取权限码会把 AC-49 的角色口径丢掉
 *       （种子实测：{@code group_leader} 恰为该码持有人，但角色口径是 PRD 655/764 行的原文）。</li>
 *   <li><b>只读视图（GET）</b>：{@code /flow-tasks/todo|done|initiated}、
 *       {@code /flow-instances/{id}/runtime|node-instances|task-list|thread|routing|supplements|cc}
 *       —— **不加**粗粒度动作码闸门，靠数据域织入（域外 404）。
 *       {@code todo/done/initiated} 的主语是「我自己的待办」，只要求门户基础权限
 *       （{@code flow} ∪ {@code admin:flow}），无动作码可挂；给它们挂 {@code flow:task:*} 会误伤
 *       只读主体。</li>
 *   <li><b>{@code POST /flow-tasks/{id}/reassign}</b>：入口 = {@code flow:task:reassign}
 *       （动作面唯一真源），引擎另有 {@code requireSuperAdmin}（身份层）。
 *       种子层只有 {@code admin} 持有该码，因此实操两层一致；
 *       若管理员把该码下放给别的角色，入口放行、引擎 403 —— 这是**设计内的第二层**，
 *       不把「仅系统管理员」这条身份判定搬进入口（入口层不读实例，但身份判定的真源在引擎）。</li>
 * </ol>
 */
class FlowActionEntryGateTest {

    private static final long TASK_ID = 77L;

    private static final long INSTANCE_ID = 9101L;

    /** 一个动作端点：路径 + 合法请求体 + 「放行时应调用的服务方法」断言。 */
    private record Endpoint(FlowAction action, String path, String body, Consumer<Fixture> call) {

        @Override
        public String toString() {
            return "POST " + path + " → " + action.permission();
        }
    }

    /** 三个协作者：入口拒人时一个都不该被碰。 */
    private record Fixture(FlowTaskService taskService, FlowEngineService engine,
                           FlowRuntimeQueryService queryService) {

        Object[] all() {
            return new Object[] {taskService, engine, queryService};
        }
    }

    /** 任务侧 11 个动作端点 + 实例侧 3 个（reopen / resubmit / supplement）= 14。 */
    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(FlowAction.APPROVE, "/api/v1/flow-tasks/" + TASK_ID + "/approve",
                    "{\"opinion\":\"同意\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).approve(TASK_ID, "同意", null)),
            new Endpoint(FlowAction.REJECT, "/api/v1/flow-tasks/" + TASK_ID + "/reject",
                    "{\"opinion\":\"材料不全请补充\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).reject(TASK_ID, "材料不全请补充")),
            new Endpoint(FlowAction.ARCHIVE_REGISTER, "/api/v1/flow-tasks/" + TASK_ID + "/archive-register",
                    "{\"opinion\":\"已登记\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).archiveRegister(TASK_ID, "已登记")),
            new Endpoint(FlowAction.ROLLBACK, "/api/v1/flow-tasks/" + TASK_ID + "/rollback",
                    "{\"reason\":\"回退重审\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).rollback(TASK_ID, "回退重审")),
            new Endpoint(FlowAction.ROUTE, "/api/v1/flow-tasks/" + TASK_ID + "/route",
                    "{\"toDeptId\":150,\"reason\":\"转财务部\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).route(TASK_ID, 150L, "转财务部")),
            new Endpoint(FlowAction.BACK_HOME, "/api/v1/flow-tasks/" + TASK_ID + "/back-home",
                    "{\"reason\":\"收束回本部门\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).backHome(TASK_ID, "收束回本部门")),
            new Endpoint(FlowAction.JUMP, "/api/v1/flow-tasks/" + TASK_ID + "/jump",
                    "{\"targetSeq\":4,\"reason\":\"跳转至④\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).jump(TASK_ID, 4, "跳转至④")),
            new Endpoint(FlowAction.ADD_SIGN, "/api/v1/flow-tasks/" + TASK_ID + "/add-sign",
                    "{\"type\":\"pre\",\"delegateUserId\":301,\"reason\":\"请协助审核\"}",
                    f -> org.mockito.Mockito.verify(f.taskService())
                            .addSign(TASK_ID, "pre", 301L, "请协助审核")),
            new Endpoint(FlowAction.SUPPLEMENT_REQUEST, "/api/v1/flow-tasks/" + TASK_ID + "/supplement-request",
                    "{\"reason\":\"请补充附件\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).supplementRequest(TASK_ID, "请补充附件")),
            new Endpoint(FlowAction.TRANSFER, "/api/v1/flow-tasks/" + TASK_ID + "/transfer",
                    "{\"toUserId\":301,\"reason\":\"本人休假\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).transfer(TASK_ID, 301L, "本人休假")),
            new Endpoint(FlowAction.REASSIGN, "/api/v1/flow-tasks/" + TASK_ID + "/reassign",
                    "{\"toUserId\":301,\"reason\":\"原审批人离职\"}",
                    f -> org.mockito.Mockito.verify(f.taskService()).reassign(TASK_ID, 301L, "原审批人离职")),
            new Endpoint(FlowAction.REOPEN, "/api/v1/flow-instances/" + INSTANCE_ID + "/reopen",
                    null,
                    f -> org.mockito.Mockito.verify(f.engine()).reopen(INSTANCE_ID)),
            new Endpoint(FlowAction.SUBMIT, "/api/v1/flow-instances/" + INSTANCE_ID + "/resubmit",
                    "{\"reason\":\"修改后重提\"}",
                    f -> org.mockito.Mockito.verify(f.engine()).resubmit(INSTANCE_ID, "修改后重提")),
            new Endpoint(FlowAction.SUPPLEMENT_SUBMIT, "/api/v1/flow-instances/" + INSTANCE_ID + "/supplement",
                    "{\"note\":\"已补附件\"}",
                    f -> org.mockito.Mockito.verify(f.engine()).supplementSubmit(INSTANCE_ID, "已补附件")));

    private EffectivePermissionService permissions;

    private Fixture fixture;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        permissions = mock(EffectivePermissionService.class);
        fixture = new Fixture(mock(FlowTaskService.class), mock(FlowEngineService.class),
                mock(FlowRuntimeQueryService.class));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        mvc = MockMvcBuilders.standaloneSetup(
                        new FlowTaskController(fixture.taskService(), gate),
                        new FlowRuntimeController(fixture.engine(), fixture.queryService(), gate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    static Stream<Endpoint> endpoints() {
        return ENDPOINTS.stream();
    }

    // ================================================================ 夹具

    private static CurrentUser user(long id, String account, String... roleCodes) {
        return CurrentUser.of(id, account, account, "T" + id, 135L, 12L, Set.of(roleCodes), Set.of(), false);
    }

    private void login(CurrentUser principal, String... permissionCodes) {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(principal.roleCodes())
                .build());
        when(permissions.permissionCodes(principal.id())).thenReturn(Set.of(permissionCodes));
    }

    /** 「除了本动作的权限码以外全都有」——用作拒人侧的主体（含 {@code admin:flow} 兜底码）。 */
    private static String[] allPermissionCodesExcept(FlowAction action) {
        Set<String> codes = new java.util.LinkedHashSet<>();
        for (FlowAction other : FlowAction.values()) {
            codes.add(other.permission());
        }
        codes.add(FlowConfigPermission.FLOW_ADMIN);
        codes.add(FlowConfigPermission.FLOW_USE);
        codes.remove(action.permission());
        return codes.toArray(String[]::new);
    }

    private org.springframework.test.web.servlet.ResultActions perform(Endpoint endpoint) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder =
                post(endpoint.path()).contentType(MediaType.APPLICATION_JSON);
        if (endpoint.body() != null) {
            builder = builder.content(endpoint.body());
        }
        return mvc.perform(builder);
    }

    /** 入口闸门的期望文案（从动作面取值拼装，避免在测试里重复魔法字符串）。 */
    private static String rejectionMessage(FlowAction action) {
        return "无权执行「" + action.label() + "」：需要权限 " + action.permission();
    }

    // ================================================================ ① 入口层：拒人

    @ParameterizedTest(name = "[{index}] {0}｜不持有该动作码 → 入口 403/40301，引擎与任务服务一次都没被调用")
    @MethodSource("endpoints")
    @DisplayName("入口层｜14 个动作端点：各自取该动作的权限码，缺码即 403 且不触达任何协作者")
    void entryGateRejectsWithoutTheActionPermission(Endpoint endpoint) throws Exception {
        // 主体持有「其它所有动作码 + admin:flow」——改前正是被 requireInitiator 的 admin:flow 分支放行。
        login(user(101L, "emp01", "employee"), allPermissionCodesExcept(endpoint.action()));

        perform(endpoint)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value(rejectionMessage(endpoint.action())));

        verifyNoInteractions(fixture.all());
    }

    @Test
    @DisplayName("入口层｜company_admin（持 admin:flow，种子中不持任何 flow:* 动作码）对 14 个端点全部入口即 403")
    void companyAdminIsRejectedAtEveryActionEntry() throws Exception {
        // 这是「入口比引擎宽」的真实形态：admin:flow 过得了 requireInitiator，
        // 过不了引擎的动作码；收敛后拒人发生在入口层、读库之前。
        for (Endpoint endpoint : ENDPOINTS) {
            login(user(102L, "ca01", "company_admin"),
                    FlowConfigPermission.FLOW_ADMIN, "admin:flow:template", "admin:flow:node",
                    "admin:flow:publish");
            perform(endpoint)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(rejectionMessage(endpoint.action())));
        }
        verifyNoInteractions(fixture.all());
    }

    // ================================================================ ② 入口层：放行

    @ParameterizedTest(name = "[{index}] {0}｜恰好持有该动作码 → 200 且把请求交给服务/引擎")
    @MethodSource("endpoints")
    @DisplayName("入口层｜14 个动作端点：持有该动作自己的权限码即放行（身份与状态机留给第二层）")
    void entryGatePassesWithTheActionPermission(Endpoint endpoint) throws Exception {
        login(user(101L, "emp01", "employee"), endpoint.action().permission());

        perform(endpoint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        endpoint.call().accept(fixture);
    }

    @Test
    @DisplayName("入口层｜admin（isSuperAdmin）即使有效权限码为空：14 个动作端点全部放行")
    void superAdminPassesEveryActionEntry() throws Exception {
        for (Endpoint endpoint : ENDPOINTS) {
            login(user(100L, "admin", "admin"));
            perform(endpoint)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
        for (Endpoint endpoint : ENDPOINTS) {
            endpoint.call().accept(fixture);
        }
    }

    // ================================================================ ③ 对照表锁（防以后漂移）

    @Test
    @DisplayName("对照表锁｜14 个动作端点 → 入口权限码：路径与动作的配对逐行锁定（含 3 个 flow 基础码端点）")
    void entryGateTableIsLocked() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/approve", "approve:flow:task:approve");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/reject", "reject:flow:task:reject");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/archive-register",
                "archive_register:flow:task:approve");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/rollback", "rollback:flow:task:rollback");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/route", "route:flow:task:route");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/back-home", "back_home:flow:task:route");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/jump", "jump:flow:task:route");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/add-sign", "add_sign:flow:task:addsign");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/supplement-request",
                "supplement_request:flow:supplement:request");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/transfer", "transfer:flow:task:transfer");
        expected.put("POST /api/v1/flow-tasks/" + TASK_ID + "/reassign", "reassign:flow:task:reassign");
        expected.put("POST /api/v1/flow-instances/" + INSTANCE_ID + "/reopen", "reopen:flow");
        expected.put("POST /api/v1/flow-instances/" + INSTANCE_ID + "/resubmit", "submit:flow");
        expected.put("POST /api/v1/flow-instances/" + INSTANCE_ID + "/supplement", "supplement_submit:flow");

        Map<String, String> actual = new LinkedHashMap<>();
        for (Endpoint endpoint : ENDPOINTS) {
            actual.put("POST " + endpoint.path(),
                    endpoint.action().code() + ":" + endpoint.action().permission());
        }

        assertThat(actual)
                .as("入口闸门一律取 FlowAction.XXX.permission()；改端点与动作的配对必须同时改这张表")
                .isEqualTo(expected);
        assertThat(ENDPOINTS).as("14 个动作端点，一个都不能少（少一个就是漏收敛）").hasSize(14);
    }

    @Test
    @DisplayName("例外锁｜terminate 用 AC-49 专用判据（不取 flow:task:terminate 单码）；只读 GET 不设动作码闸门")
    void documentedExceptionsAreStillInPlace() {
        // terminate：AC-49 = 系统管理员 ∪ group_leader 角色 ∪ flow:task:terminate 权限（三者并集）
        assertThat(ENDPOINTS.stream().map(Endpoint::action))
                .as("terminate 刻意不在 14 个「取动作码」的端点内")
                .doesNotContain(FlowAction.TERMINATE);
        assertThat(FlowAction.TERMINATE.permission())
                .as("终止的动作码仍是 AC-49 三并集里的一项，由 requireTerminate 整体判定")
                .isEqualTo(FlowConfigPermission.TERMINATE);

        // 只读视图不在收敛清单内（GET 无动作码，靠数据域）
        List<String> readOnlyPaths = new ArrayList<>(List.of(
                "/api/v1/flow-tasks/todo", "/api/v1/flow-tasks/done", "/api/v1/flow-tasks/initiated",
                "/api/v1/flow-instances/{id}/runtime", "/api/v1/flow-instances/{id}/node-instances",
                "/api/v1/flow-instances/{id}/task-list", "/api/v1/flow-instances/{id}/thread",
                "/api/v1/flow-instances/{id}/routing", "/api/v1/flow-instances/{id}/supplements",
                "/api/v1/flow-instances/{id}/cc"));
        for (Endpoint endpoint : ENDPOINTS) {
            assertThat(readOnlyPaths).doesNotContain(endpoint.path());
        }
    }
}
