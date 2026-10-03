package com.oa.form.seal;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.form.FormSchemaFixtures;
import com.oa.form.document.FormRuleContext;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.validate.FormValidationReport;
import com.oa.form.template.validate.ValidationMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.3 印鉴证照审批单专属规则</b>（doc/forms.md §5 / §7；doc/dict-seed.md §3 / §9；
 * TC-FORM-008/009/010/013）。
 */
class SealFormRulesTest {

    private final SealFormRules rules = new SealFormRules();
    private FormSchema schema;

    @BeforeEach
    void setUp() {
        schema = FormSchemaFixtures.schema("seal");
    }

    private FormValidationReport validate(Map<String, Object> values, ValidationMode mode) {
        FormRuleContext context = new FormRuleContext(schema, values, values.keySet(), Map.of(), mode, null,
                LocalDate.of(2026, 7, 15));
        FormValidationReport.Collector collector = FormValidationReport.collector();
        rules.validate(context, collector);
        return collector.build();
    }

    private Map<String, Object> base() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "借用营业执照办理投标");
        payload.put("seal_type", "company_seal");
        payload.put("purpose", "用于投标文件盖章");
        payload.put("usage_start", "2026-08-01");
        payload.put("usage_end", "2026-08-31");
        payload.put("seal_count", 3);
        payload.put("is_external", false);
        payload.put("return_status", "pending");
        return payload;
    }

    @Test
    @DisplayName("正常单据通过（含用印份数与期限）")
    void happyPath() {
        FormValidationReport report = validate(base(), ValidationMode.SUBMIT);
        assertThat(report.passed()).as("实际失败项：%s", report.summary()).isTrue();
    }

    @Test
    @DisplayName("归还闭环（TC-FORM-009 的①）：状态改「已归还」但没有归还日期 → 拒绝；两档都判")
    void returnClosureRequiresDate() {
        Map<String, Object> payload = base();
        payload.put("return_status", "returned");
        for (ValidationMode mode : ValidationMode.values()) {
            assertThat(validate(payload, mode).messagesOf("return_date"))
                    .as("%s 档", mode)
                    .containsExactly("归还状态改为「已归还」时必须填写归还日期");
        }
        payload.put("return_date", "2026-09-01");
        assertThat(validate(payload, ValidationMode.SUBMIT).hasIssueOn("return_date")).isFalse();
    }

    @Test
    @DisplayName("归还闭环反向：状态不是「已归还」却写了归还日期 → 拒绝（避免脏数据污染超期口径）")
    void returnDateWithoutReturnedStatus() {
        Map<String, Object> payload = base();
        payload.put("return_status", "pending");
        payload.put("return_date", "2026-09-01");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("return_date"))
                .anyMatch(message -> message.contains("只有归还状态为「已归还」时"));
    }

    @Test
    @DisplayName("归还日期格式非法被拒；「无需归还」是终态备注，不要求日期（dict-seed §9 D-10）")
    void returnDateFormatAndNotRequired() {
        Map<String, Object> payload = base();
        payload.put("return_status", "returned");
        payload.put("return_date", "2026/09/01");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("return_date"))
                .anyMatch(message -> message.contains("YYYY-MM-DD"));

        payload.put("return_status", "not_required");
        payload.remove("return_date");
        assertThat(validate(payload, ValidationMode.SUBMIT).hasIssueOn("return_date")).isFalse();
    }

    @Test
    @DisplayName("证照借用：cert_name 必填且不得填用印份数；其他用印类型：份数必填且 1–999 整数（TC-FORM-008）")
    void certBorrowLinkage() {
        Map<String, Object> borrow = base();
        borrow.put("seal_type", "cert_borrow");
        borrow.remove("seal_count");
        assertThat(validate(borrow, ValidationMode.SUBMIT).messagesOf("cert_name"))
                .containsExactly("证照类型为必填");

        borrow.put("cert_name", "business_license");
        assertThat(validate(borrow, ValidationMode.SUBMIT).hasIssueOn("cert_name")).isFalse();

        borrow.put("seal_count", 3);
        assertThat(validate(borrow, ValidationMode.SUBMIT).messagesOf("seal_count"))
                .anyMatch(message -> message.contains("无需填写用印份数"));

        Map<String, Object> sealed = base();
        sealed.remove("seal_count");
        assertThat(validate(sealed, ValidationMode.SUBMIT).messagesOf("seal_count"))
                .anyMatch(message -> message.contains("用印份数为必填"));

        sealed.put("seal_count", 1000);
        assertThat(validate(sealed, ValidationMode.SUBMIT).messagesOf("seal_count"))
                .anyMatch(message -> message.contains("1–999"));

        sealed.put("seal_count", 2.5d);
        assertThat(validate(sealed, ValidationMode.SUBMIT).messagesOf("seal_count"))
                .anyMatch(message -> message.contains("1–999"));
    }

    @Test
    @DisplayName("期限：开始早于今天被拒；结束早于开始被拒")
    void usagePeriod() {
        Map<String, Object> payload = base();
        payload.put("usage_start", "2026-07-01");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("usage_start"))
                .containsExactly("使用开始日期不能早于今天");

        payload.put("usage_start", "2026-08-01");
        payload.put("usage_end", "2026-07-31");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("usage_end"))
                .containsExactly("使用结束日期不能早于使用开始日期");
    }

    @Test
    @DisplayName("用途说明：基础 ≥5；对外提供时 ≥20（forms.md §5 is_external 行）")
    void purposeLength() {
        Map<String, Object> payload = base();
        payload.put("purpose", "盖章");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("purpose"))
                .containsExactly("用途说明至少 5 个字符");

        payload.put("purpose", "用于投标文件盖章（对外）");
        payload.put("is_external", true);
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("purpose"))
                .anyMatch(message -> message.contains("至少 20 个字符"));
    }

    @Test
    @DisplayName("三态例外披露：四阶段白名单逐段列出（forms.md §5 例外边界表）")
    void threeStateExceptionDisclosure() {
        Map<String, Object> exception = SealFormRules.threeStateException();
        assertThat(exception.get("pendingSupplement").toString())
                .contains("return_status / return_date 同样只读");
        assertThat(exception.get("approving").toString()).contains("节点⑦归档登记人");
        assertThat(exception.get("fields")).isEqualTo(new java.util.LinkedHashSet<>(
                List.of("return_status", "return_date")));
        assertThat(SealFormRules.returnTraceFields()).containsExactlyInAnyOrder("return_status", "return_date");
    }

    @Test
    @DisplayName("读取侧派生：联动需求 + 归还闭环说明")
    void describe() {
        Map<String, Object> values = base();
        values.put("seal_type", "cert_borrow");
        FormRuleContext context = new FormRuleContext(schema, values, values.keySet(), Map.of(),
                ValidationMode.DRAFT, null, LocalDate.now());
        Map<String, Object> view = rules.describe(context);
        assertThat(view).containsEntry("certNameRequired", true).containsEntry("sealCountRequired", false);
        assertThat(view.get("returnClosure").toString()).contains("returned");
        assertThat(view.get("threeStateException")).isNotNull();
        assertThat(rules.formType()).isEqualTo("seal");
    }
}
