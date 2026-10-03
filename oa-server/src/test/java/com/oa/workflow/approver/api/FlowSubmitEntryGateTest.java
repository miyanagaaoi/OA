package com.oa.workflow.approver.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.oa.workflow.approver.app.ApproverPrecheckService;
import com.oa.workflow.approver.app.FlowInstanceService;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.runtime.app.FlowEngineService;
import com.oa.workflow.runtime.domain.FlowAction;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>{@code POST /flow-instances/{id}/submit} 的入口闸门（F 项收敛的第二半，2026-10-04）</b>。
 *
 * <p>引擎 {@code FlowEngineService#submit} 已收敛到 {@code FlowAction.SUBMIT.permission()}（{@code flow}）；
 * 若入口仍用 {@code requireInitiator}（{@code flow} ∪ {@code admin:flow}），
 * 「入口比引擎宽」的不对称就会从 {@code reopen/resubmit/supplement} 挪到 {@code submit} 上
 * —— 只持 {@code admin:flow} 的角色能过入口、再被引擎 403。故入口同步取同一个动作码：
 * <b>入口判权限、引擎判身份与状态机</b>，两层同源。
 *
 * <p>刻意保留的宽口：{@code POST /flow-instances}（<b>建草稿</b>）仍由 {@code requireInitiator} 放行
 * ——「发起」不在 {@link FlowAction} 的动作面内（PRD 附录A「发起审批」行 8 列全 ✓）。
 */
class FlowSubmitEntryGateTest {

    private static final long INSTANCE_ID = 9101L;

    private EffectivePermissionService permissions;
    private FlowEngineService engine;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        permissions = mock(EffectivePermissionService.class);
        engine = mock(FlowEngineService.class);
        FlowInstanceService instances = mock(FlowInstanceService.class);
        ApproverPrecheckService precheck = mock(ApproverPrecheckService.class);
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        mvc = MockMvcBuilders
                .standaloneSetup(new FlowInstanceController(instances, precheck, gate, engine))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(instances.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private void login(long id, String account, String roleCode, String... permissionCodes) {
        CurrentUser principal = CurrentUser.of(id, account, account, "T" + id, 135L, 12L,
                Set.of(roleCode), Set.of(), false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(principal.roleCodes())
                .build());
        when(permissions.permissionCodes(id)).thenReturn(new java.util.LinkedHashSet<>(
                java.util.Arrays.asList(permissionCodes)));
    }

    @Test
    @DisplayName("入口层｜submit：只持 admin:flow（改前被 requireInitiator 放行）→ 403/40301，引擎零交互")
    void submitEntryRejectsAdminFlowOnly() throws Exception {
        login(101L, "ca01", "company_admin", FlowConfigPermission.FLOW_ADMIN, FlowConfigPermission.TEMPLATE_READ);

        mvc.perform(post("/api/v1/flow-instances/" + INSTANCE_ID + "/submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"提交\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value("无权执行「" + FlowAction.SUBMIT.label()
                        + "」：需要权限 " + FlowAction.SUBMIT.permission()));

        verifyNoInteractions(engine);
    }

    @Test
    @DisplayName("入口层｜submit：持 flow → 200 且交给引擎（身份与状态机留给引擎）")
    void submitEntryPassesWithFlow() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);

        mvc.perform(post("/api/v1/flow-instances/" + INSTANCE_ID + "/submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"提交\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(engine).submit(INSTANCE_ID, "提交");
    }

    @Test
    @DisplayName("入口层｜submit：系统管理员（admin 角色）即使无有效权限码也放行")
    void submitEntryPassesForSuperAdmin() throws Exception {
        login(100L, "admin", "admin");
        mvc.perform(post("/api/v1/flow-instances/" + INSTANCE_ID + "/submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"提交\"}"))
                .andExpect(status().isOk());

        verify(engine).submit(INSTANCE_ID, "提交");
    }

    @Test
    @DisplayName("对照表锁｜入口闸门与引擎闸门同参：submit 一侧取 FlowAction.SUBMIT.permission()")
    void entryGateMatchesEngine() {
        org.assertj.core.api.Assertions.assertThat(FlowAction.SUBMIT.permission())
                .as("入口与引擎共用同一动作码（单一真源 FlowAction）")
                .isEqualTo("flow");
    }
}
