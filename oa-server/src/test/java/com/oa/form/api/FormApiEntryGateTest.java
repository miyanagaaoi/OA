package com.oa.form.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.error.ErrorCode;
import com.oa.common.error.GlobalExceptionHandler;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormDataService;
import com.oa.form.dict.DictType;
import com.oa.form.dict.FormDictService;
import com.oa.form.dict.InMemoryDictMapper;
import com.oa.form.document.FormRuleRegistry;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>2b 表单接口的入口闸门</b>（standalone MockMvc，不拉容器）。
 *
 * <p>口径与 {@code FlowInstanceController} 一致：<b>写入口取该动作自己的权限码</b>
 * {@code flow}（{@link FlowConfigPermission#FLOW_USE}），只读入口取 {@code requireInitiator}
 * （{@code flow} ∪ {@code admin:flow}）—— 入口不比动作面宽。
 */
class FormApiEntryGateTest {

    private static final long INSTANCE_ID = 8101L;

    private EffectivePermissionService permissions;
    private FormDataService formDataService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        permissions = mock(EffectivePermissionService.class);
        formDataService = mock(FormDataService.class);
        when(formDataService.read(anyLong())).thenReturn(java.util.Map.of("instanceId", INSTANCE_ID));
        when(formDataService.writableFields(anyLong())).thenReturn(java.util.Map.of("instanceId", INSTANCE_ID));
        when(formDataService.schemaOfInstance(anyLong())).thenReturn(java.util.Map.of("instanceId", INSTANCE_ID));
        when(formDataService.save(anyLong(), any(), any())).thenReturn(java.util.Map.of("instanceId", INSTANCE_ID));
        when(formDataService.validateInstance(anyLong(), any(), any()))
                .thenReturn(java.util.Map.of("instanceId", INSTANCE_ID));
        when(formDataService.validateByFormType(anyString(), any(), any()))
                .thenReturn(java.util.Map.of("formType", "matter"));
        when(formDataService.registerSealReturn(anyLong(), any()))
                .thenReturn(java.util.Map.of("instanceId", INSTANCE_ID));

        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        FormDictService dictService = new FormDictService(new InMemoryDictMapper());
        FormSchemaService schemaService = mock(FormSchemaService.class);
        FormRuleRegistry registry = new FormRuleRegistry(List.of());

        mvc = MockMvcBuilders
                .standaloneSetup(
                        new FormDataController(formDataService, gate),
                        new FormTemplateController(dictService, schemaService, gate),
                        new FormRuleController(schemaService, registry, formDataService, gate,
                                new com.oa.authz.visibility.FormFieldWriteGuard()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
        when(permissions.permissionCodes(id)).thenReturn(new LinkedHashSet<>(List.of(permissionCodes)));
    }

    // ================================================================ 写入口：flow 单码

    @Test
    @DisplayName("保存：只持 admin:flow → 403/40301，服务层零交互（入口不比动作面宽）")
    void saveRejectsAdminFlowOnly() throws Exception {
        login(101L, "ca01", "company_admin", FlowConfigPermission.FLOW_ADMIN);

        mvc.perform(put("/api/v1/forms/instances/" + INSTANCE_ID + "/draft")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":{\"title\":\"x\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));

        verifyNoInteractions(formDataService);
    }

    @Test
    @DisplayName("保存：持 flow → 200；校验档缺省为 draft")
    void savePassesWithFlow() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);

        mvc.perform(put("/api/v1/forms/instances/" + INSTANCE_ID + "/draft")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":{\"title\":\"x\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(formDataService).save(anyLong(), any(),
                org.mockito.ArgumentMatchers.eq(com.oa.form.template.validate.ValidationMode.DRAFT));
    }

    @Test
    @DisplayName("保存：mode=submit → 传 SUBMIT 档；mode=unknown → 400")
    void saveModeParameter() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);

        mvc.perform(put("/api/v1/forms/instances/" + INSTANCE_ID + "/draft?mode=submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":{}}"))
                .andExpect(status().isOk());
        verify(formDataService).save(anyLong(), any(),
                org.mockito.ArgumentMatchers.eq(com.oa.form.template.validate.ValidationMode.SUBMIT));

        mvc.perform(put("/api/v1/forms/instances/" + INSTANCE_ID + "/draft?mode=strict")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));
    }

    @Test
    @DisplayName("归还登记：持 flow → 200；401 未登录 → 40101")
    void sealReturnGate() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);
        mvc.perform(put("/api/v1/forms/seal/instances/" + INSTANCE_ID + "/return-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"returnStatus\":\"returned\",\"returnDate\":\"2026-09-01\"}"))
                .andExpect(status().isOk());
        verify(formDataService).registerSealReturn(anyLong(), any());

        DataScopeContext.clear();
        mvc.perform(put("/api/v1/forms/seal/instances/" + INSTANCE_ID + "/return-status")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    // ================================================================ 只读入口：flow ∪ admin:flow

    @Test
    @DisplayName("读取：admin:flow 也可读（只读入口刻意较宽，数据域在查询层过滤）")
    void readAllowsAdminFlow() throws Exception {
        login(101L, "ca01", "company_admin", FlowConfigPermission.FLOW_ADMIN);
        mvc.perform(get("/api/v1/forms/instances/" + INSTANCE_ID + "/draft"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/forms/instances/" + INSTANCE_ID + "/writable-fields"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/forms/instances/" + INSTANCE_ID + "/schema"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("读取：无任何权限 → 403")
    void readRejectsWithoutPermission() throws Exception {
        login(103L, "nobody", "employee");
        mvc.perform(get("/api/v1/forms/instances/" + INSTANCE_ID + "/draft"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("系统管理员放行（admin 角色兜底，与 /auth/me 同源）")
    void superAdminPasses() throws Exception {
        login(100L, "admin", "admin");
        mvc.perform(put("/api/v1/forms/instances/" + INSTANCE_ID + "/draft")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fields\":{}}"))
                .andExpect(status().isOk());
    }

    // ================================================================ 字典接口

    @Test
    @DisplayName("字典：8 类白名单 + 取值清单（含类别为配置项的口径）")
    void dictEndpoints() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);

        mvc.perform(get("/api/v1/forms/dicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(DictType.values().length));

        mvc.perform(get("/api/v1/forms/dicts/matter_category/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].itemCode").value("business"));

        mvc.perform(get("/api/v1/forms/dicts/category/items"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_INVALID.getCode()));

        mvc.perform(post("/api/v1/forms/dicts/cache/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("字典接口无权限 → 403（与表单读写同闸门）")
    void dictEndpointsRequirePermission() throws Exception {
        login(103L, "nobody", "employee");
        mvc.perform(get("/api/v1/forms/dicts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("事项单分支接口：入参布尔 → 返回跳过结论与引擎一致性标记")
    void involveCostEvaluate() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);
        mvc.perform(post("/api/v1/forms/matter/fields/involve-cost/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"involveCost\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipFinanceReview").value(true))
                .andExpect(jsonPath("$.data.engineAgreement").value(true))
                .andExpect(jsonPath("$.data.ownerDeptRecorded").value("财务部"));

        mvc.perform(post("/api/v1/forms/matter/fields/involve-cost/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"involveCost\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipFinanceReview").value(false));
    }

    @Test
    @DisplayName("资金单只存不用机检接口：被引用 → 40008")
    void storageOnlyAssert() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);
        mvc.perform(post("/api/v1/forms/fund/fields/plan-category/assert-storage-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"{\\\"field\\\":\\\"plan_category\\\",\\\"op\\\":\\\"eq\\\",\\\"value\\\":true}\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.FLOW_DEFINITION_INVALID.getCode()));

        mvc.perform(post("/api/v1/forms/fund/fields/plan-category/assert-storage-only")
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    @DisplayName("类别改判接口：state 非 draft 且值变化 → allowed=false（附 40309）")
    void categoryLockEndpoint() throws Exception {
        login(102L, "emp01", "employee", FlowConfigPermission.FLOW_USE);
        mvc.perform(post("/api/v1/forms/matter/fields/category/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentCategory\":\"business\",\"newCategory\":\"hr\",\"state\":\"approving\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.CATEGORY_IMMUTABLE.getCode()));

        mvc.perform(post("/api/v1/forms/matter/fields/category/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentCategory\":\"business\",\"newCategory\":\"hr\",\"state\":\"draft\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true));
    }

    @Test
    @DisplayName("闸门口径对照表锁：写入口是 flow 单码、只读入口是 flow ∪ admin:flow")
    void gateContractIsLocked() {
        assertThat(FlowConfigPermission.FLOW_USE).isEqualTo("flow");
        assertThat(FlowConfigPermission.has(false, Set.of(FlowConfigPermission.FLOW_ADMIN),
                FlowConfigPermission.FLOW_USE)).isFalse();
        assertThat(FlowConfigPermission.has(false, Set.of(FlowConfigPermission.FLOW_ADMIN),
                FlowConfigPermission.FLOW_USE, FlowConfigPermission.FLOW_ADMIN)).isTrue();
    }
}
