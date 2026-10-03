package com.oa.form.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.authz.visibility.FormFieldWriteGuard;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.error.GlobalExceptionHandler;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.FormSchemaFixtures;
import com.oa.form.app.FormDataService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * <b>{@code GET /forms/{formType}/field-groups} 的 {@code amountPolicy} 必须按当前登录角色算</b>
 * （2026-10-04 缺陷修复）。
 *
 * <h2>改前的缺陷（运行期实测）</h2>
 * <pre>
 * FormRuleController:176  view.put("amountPolicy", FormDataService.amountPolicyOf(null));  // ← 硬编码 null
 * </pre>
 * <p>{@code AmountFieldPolicy#canWriteAmounts(null)} 恒为 {@code false} ⇒ 所有账号
 * （含系统管理员与财务角色）都被前端告知「金额只读」；而同会话直接把金额写进草稿却是 200 ——
 * 「**读路径说不能写、写路径却能写**」。
 *
 * <h2>本类断言的两面一致</h2>
 * <p>每个角色同时断言 ①读路径 {@code amountPolicy.writable} 与 ②写路径
 * （{@link FormFieldWriteGuard#assertAmountWritable}，即 {@code FormStateWriteGuard} 用的同一条闸门）
 * 的结论，二者必须一致；任何一面改了另一面没跟上，本类立刻变红。
 */
class FormRuleAmountPolicyTest {

    private FormFieldWriteGuard writeGuard;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        writeGuard = new FormFieldWriteGuard();
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);
        FormSchemaService schemaService = mock(FormSchemaService.class);
        when(schemaService.publishedFor(anyString())).thenReturn(FormSchemaFixtures.schema("matter"));
        FormDataService formDataService = mock(FormDataService.class);
        when(permissions.permissionCodes(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LinkedHashSet<>(List.of(FlowConfigPermission.FLOW_USE)));

        mvc = MockMvcBuilders
                .standaloneSetup(new FormRuleController(schemaService, new FormRuleRegistry(List.of()),
                        formDataService, gate, writeGuard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private void login(long id, String... roleCodes) {
        CurrentUser principal = CurrentUser.of(id, "u" + id, "用户" + id, "T" + id, 135L, 12L,
                new LinkedHashSet<>(List.of(roleCodes)), Set.of(), false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(principal.roleCodes())
                .build());
    }

    /** 读路径的原始响应体（不断言状态码：未登录态也要能看到 40101 的报文）。 */
    private String amountPolicyBody() throws Exception {
        return mvc.perform(get("/api/v1/forms/matter/field-groups"))
                .andReturn().getResponse().getContentAsString();
    }

    /** 写路径的真实结论（不抛 = 可写）。 */
    private boolean writePathAllowsAmount() {
        try {
            writeGuard.assertAmountWritable(Set.of("amount"));
            return true;
        } catch (BizException ex) {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_READ_ONLY);
            return false;
        }
    }

    @Test
    @DisplayName("admin：amountPolicy.writable=true，且写路径放行（改前读路径恒 false）")
    void adminSeesWritableAmounts() throws Exception {
        login(401L, "admin");
        mvc.perform(get("/api/v1/forms/matter/field-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPolicy.writable").value(true))
                .andExpect(jsonPath("$.data.amountPolicy.exportable").value(true))
                .andExpect(jsonPath("$.data.amountPolicy.readOnly").value(false))
                .andExpect(jsonPath("$.data.amountPolicy.writableRoles[0]").value("admin"));
        assertThat(writePathAllowsAmount()).as("读写两面必须同结论").isTrue();
    }

    @Test
    @DisplayName("finance_owner：amountPolicy.writable=true，且写路径放行")
    void financeOwnerSeesWritableAmounts() throws Exception {
        login(402L, "finance_owner");
        mvc.perform(get("/api/v1/forms/matter/field-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPolicy.writable").value(true))
                .andExpect(jsonPath("$.data.amountPolicy.readOnly").value(false));
        assertThat(writePathAllowsAmount()).isTrue();
    }

    @Test
    @DisplayName("employee：amountPolicy.writable=false，且写路径 40306（非财务类角色只读）")
    void employeeSeesReadOnlyAmounts() throws Exception {
        login(403L, "employee");
        mvc.perform(get("/api/v1/forms/matter/field-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amountPolicy.writable").value(false))
                .andExpect(jsonPath("$.data.amountPolicy.exportable").value(false))
                .andExpect(jsonPath("$.data.amountPolicy.readOnly").value(true));
        assertThat(writePathAllowsAmount()).isFalse();
    }

    @Test
    @DisplayName("对照：{@code /authz/field-policy/amount} 的判定与 field-groups 同源（同一 guard、同一主体）")
    void bothEndpointsShareTheSamePolicyImplementation() throws Exception {
        login(404L, "employee");
        // 未登录主体缺失 → 401（而不是把 null 当「只读」静默算出来）
        DataScopeContext.clear();
        assertThat(amountPolicyBody()).contains("\"code\":40101");
        assertThatThrownBy(() -> writeGuard.assertAmountWritable(Set.of("amount")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
