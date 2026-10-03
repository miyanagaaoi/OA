package com.oa.form.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
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
 * <b>2b.3 资金审批单专属规则</b>（doc/forms.md §3 / §6.7 / §6.8 / §1.5；
 * doc/data-model.md §4.4；doc/templates.md §1.2 末）。
 */
class FundFormRulesTest {

    private final FundFormRules rules = new FundFormRules();
    private FormSchema schema;

    @BeforeEach
    void setUp() {
        schema = FormSchemaFixtures.schema("fund");
    }

    private FormValidationReport validate(Map<String, Object> values, ValidationMode mode) {
        FormRuleContext context = new FormRuleContext(schema, values, values.keySet(), Map.of(), mode, null,
                LocalDate.of(2026, 7, 15));
        FormValidationReport.Collector collector = FormValidationReport.collector();
        rules.validate(context, collector);
        return collector.build();
    }

    @Test
    @DisplayName("金额：空/0/负数/三位小数/浮点 全部被拒；字符串定点数通过（TC-FORM-005）")
    void amountRules() {
        assertThat(validate(Map.of("amount", "0"), ValidationMode.SUBMIT).messagesOf("amount"))
                .containsExactly("金额必须大于 0 且最多两位小数");
        assertThat(validate(Map.of(), ValidationMode.SUBMIT).messagesOf("amount"))
                .containsExactly("金额必须大于 0 且最多两位小数");
        assertThat(validate(Map.of("amount", "-100"), ValidationMode.SUBMIT).hasIssueOn("amount")).isTrue();
        assertThat(validate(Map.of("amount", "100.123"), ValidationMode.SUBMIT).hasIssueOn("amount")).isTrue();
        assertThat(validate(Map.of("amount", 100.12d), ValidationMode.SUBMIT).messagesOf("amount"))
                .anyMatch(message -> message.contains("禁止使用浮点数"));
        assertThat(validate(Map.of("amount", "100.12"), ValidationMode.SUBMIT).hasIssueOn("amount")).isFalse();
    }

    @Test
    @DisplayName("金额：草稿档允许留空，但填了就必须合法（提交前必须通过，forms.md §3）")
    void amountEmptyAllowedInDraftOnly() {
        assertThat(validate(Map.of(), ValidationMode.DRAFT).hasIssueOn("amount")).isFalse();
        assertThat(validate(Map.of("amount", "abc"), ValidationMode.DRAFT).hasIssueOn("amount")).isTrue();
    }

    @Test
    @DisplayName("只存不用字段：必须是布尔，不得填字典 code（Q8/Q9 不是字典项）")
    void storageOnlyFieldsAreBoolean() {
        assertThat(validate(Map.of("plan_category", true, "payment_belong", false), ValidationMode.DRAFT).passed())
                .isTrue();
        assertThat(validate(Map.of("plan_category", "in_plan"), ValidationMode.DRAFT).messagesOf("plan_category"))
                .anyMatch(message -> message.contains("不是字典项"));
        assertThat(FundFormRules.defaults())
                .as("doc/data-model.md §4.4：默认均为勾选（true）")
                .containsEntry("plan_category", Boolean.TRUE)
                .containsEntry("payment_belong", Boolean.TRUE);
    }

    @Test
    @DisplayName("只存不用机检：被 skip_condition 引用即拒绝（doc/forms.md §3 末 / templates.md §1.2 末）")
    void assertNotRouted() {
        // Arrays.asList 允许 null（List.of 不允许），用于覆盖「节点无跳过条件」的真实形态
        assertThatCode(() -> FundFormRules.assertNotRouted(java.util.Arrays.asList(
                "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}", null, "  ")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> FundFormRules.assertNotRouted(List.of(
                "{\"field\":\"plan_category\",\"op\":\"eq\",\"value\":true}")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只存不用");

        assertThatThrownBy(() -> FundFormRules.assertNotRouted(List.of(
                "{\"field\":\"payment_belong\",\"op\":\"eq\",\"value\":false}")))
                .isInstanceOf(BizException.class);

        // 真源种子里的四类模板，② 的跳过条件只允许 involve_cost（matter）/ null（其余三类）
        assertThatCode(() -> FundFormRules.assertNotRouted(List.of(
                "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}",
                "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("附件必填（≥1）：提交档拒绝空附件")
    void attachmentsRequiredOnSubmit() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", "100.00");
        assertThat(validate(payload, ValidationMode.SUBMIT).messagesOf("attachments"))
                .anyMatch(message -> message.contains("请上传附件"));
    }

    @Test
    @DisplayName("读取侧派生：金额规范化 + 勾选语义 + 只存不用取证 + 不参与路由")
    void describe() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amount", "1250000");
        values.put("plan_category", true);
        values.put("payment_belong", true);
        FormRuleContext context = new FormRuleContext(schema, values, values.keySet(), Map.of(),
                ValidationMode.DRAFT, null, LocalDate.now());
        Map<String, Object> view = rules.describe(context);
        assertThat(view).containsEntry("amountCanonical", "1250000.00").containsEntry("amountValid", true);
        assertThat(view.get("routingDisclosure").toString()).contains("不参与流程路由");
        assertThat(view.get("storageOnlyEvidence").toString()).contains("Q8/Q9");
        assertThat(FundFormRules.storageOnlyMeaning("payment_belong")).contains("本月度");
        assertThat(rules.formType()).isEqualTo("fund");
    }
}
