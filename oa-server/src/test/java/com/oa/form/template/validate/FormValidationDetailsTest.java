package com.oa.form.template.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.api.ApiResponse;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.error.GlobalExceptionHandler;
import com.oa.form.FormSchemaFixtures;
import com.oa.form.dict.FormDictService;
import com.oa.form.dict.InMemoryDictMapper;
import com.oa.form.template.schema.FormSchema;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * <b>40011 的结构化 {@code details.errors[]} 进 HTTP 响应体</b>（2026-10-04）。
 *
 * <h2>为什么需要它</h2>
 * <p>改前 {@code GlobalExceptionHandler} 只透 {@code message}，前端只能从
 * 「字段码（标签）：原因」的**文本**里尽力还原逐字段错误（改前干跑接口 {@code POST /forms/…/validate}
 * 却已经有结构化的 {@code report.issues[]}）。现在两个面**同源**（同一个
 * {@link FormValidationReport#issueViews()}），前端拿到的形状一致。
 *
 * <h2>本类锁死的四条</h2>
 * <ol>
 *   <li>{@code details.errors[]} 逐项内容与**顺序**稳定（= 校验器累积顺序），且与干跑出参逐项相等；</li>
 *   <li>键只有 {@code field / label / rule / message}（**不泄露**内部实现：无异常类名、无 SQL、无内部 id）；</li>
 *   <li>{@code code / message / traceId / success} 的形状与语义不变（message 仍是「…（N 项）：…」）；</li>
 *   <li>其它错误码**不出现** {@code details} 字段（含 {@code withDetail} 放的排查上下文，
 *       如 {@code requiredPermissions} —— AC-41 的口径一行未放宽）。</li>
 * </ol>
 */
class FormValidationDetailsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    private final ObjectMapper mapper = new ObjectMapper();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static FormValidationReport failingReport() {
        FormPayloadValidator validator = FormPayloadValidator.offline(new FormDictService(new InMemoryDictMapper()));
        FormSchema matter = FormSchemaFixtures.schema("matter");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "标".repeat(61));
        payload.put("evil_field", "夹带");
        payload.put("expect_date", "2020-01-01");
        return validator.validate(matter, payload, ValidationMode.SUBMIT, TODAY, null);
    }

    private ResponseEntity<ApiResponse<Void>> handle(BizException ex) throws Exception {
        return handler.handleBiz(ex, new MockHttpServletRequest("PUT", "/api/v1/forms/instances/9101/draft"));
    }

    @Test
    @DisplayName("40011：响应体带 details.errors[]，逐项 {field,label,rule,message} 且顺序稳定")
    void formValidationFailureCarriesStructuredErrors() throws Exception {
        FormValidationReport report = failingReport();
        assertThat(report.passed()).isFalse();

        BizException ex = catchBiz(report);
        ResponseEntity<ApiResponse<Void>> response = handle(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.FORM_VALIDATION_FAILED.getCode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors =
                (List<Map<String, Object>>) response.getBody().getDetails().get("errors");
        assertThat(errors).as("与干跑接口 report.issues[] **同源**").isEqualTo(report.issueViews());
        assertThat(errors).isNotEmpty().allSatisfy(item ->
                assertThat(item.keySet()).containsExactly("field", "label", "rule", "message"));

        Map<String, Object> titleIssue = errors.stream()
                .filter(item -> "title".equals(item.get("field"))).findFirst().orElseThrow();
        assertThat(titleIssue).containsEntry("label", "事项标题").containsEntry("rule", "maxLength");
        assertThat(String.valueOf(titleIssue.get("message"))).contains("不能超过 60 个字符");

        // JSON 形状（纯追加：既有四个键原样保留，details 追加在最后）
        String json = mapper.writeValueAsString(response.getBody());
        assertThat(json).contains("\"code\":40011").contains("\"message\":\"表单字段校验未通过（");
        assertThat(json).contains("\"details\":{\"errors\":[");
        assertThat(json.indexOf("\"code\"")).isLessThan(json.indexOf("\"details\""));
    }

    @Test
    @DisplayName("40011：details 只含可读四键（不泄露异常类名 / SQL / 内部 id）")
    void detailsDoNotLeakInternals() throws Exception {
        String json = mapper.writeValueAsString(handle(catchBiz(failingReport())).getBody());
        assertThat(json)
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("com.oa.")
                .doesNotContain("SELECT ")
                .doesNotContain("form_schema_json");
    }

    @Test
    @DisplayName("其它错误码形状不变：没有 details 字段（withDetail 的排查上下文仍只进日志）")
    void otherErrorCodesKeepTheirShape() throws Exception {
        BizException forbidden = new BizException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage())
                .withDetail("requiredPermissions", List.of("flow"))
                .withDetail("requiredRoles", List.of("admin"));
        String json = mapper.writeValueAsString(handle(forbidden).getBody());
        assertThat(json).contains("\"code\":40301").doesNotContain("details")
                .doesNotContain("requiredPermissions");

        // 成功体同样不含 details（ApiResponse 的 @JsonInclude(NON_NULL)）
        assertThat(mapper.writeValueAsString(ApiResponse.success(Map.of("ok", true))))
                .doesNotContain("details")
                .contains("\"message\":\"OK\"");
    }

    @Test
    @DisplayName("同一次失败：message 与 details 一致（message 仍是「…（N 项）：逐条摘要」）")
    void messageAndDetailsStayConsistent() throws Exception {
        FormValidationReport report = failingReport();
        ResponseEntity<ApiResponse<Void>> response = handle(catchBiz(report));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors =
                (List<Map<String, Object>>) response.getBody().getDetails().get("errors");
        assertThat(response.getBody().getMessage())
                .startsWith("表单字段校验未通过（" + errors.size() + " 项）：")
                .contains("事项标题");
        assertThat(response.getBody().getData()).isNull();
    }

    private static BizException catchBiz(FormValidationReport report) {
        try {
            report.fail();
            throw new AssertionError("报告不通过时必须抛 40011");
        } catch (BizException ex) {
            return ex;
        }
    }
}
