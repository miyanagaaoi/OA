package com.oa.form.matter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.form.FormSchemaFixtures;
import com.oa.form.document.FormRuleContext;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.validate.FormValidationReport;
import com.oa.form.template.validate.ValidationMode;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.3 事项审批单专属规则</b>（doc/forms.md §2 / §9.1；doc/prd-0.1.md §6.1 / §6.3；
 * doc/templates.md §1.1 / §1.5；doc/test-cases.md TC-FORM-003/004）。
 */
class MatterFormRulesTest {

    private final MatterFormRules rules = new MatterFormRules();
    private FormSchema schema;

    @BeforeEach
    void setUp() {
        schema = FormSchemaFixtures.schema("matter");
    }

    private FormRuleContext context(Map<String, Object> values, Map<String, Object> stored,
                                    ValidationMode mode, FlowInstanceRow instance) {
        return new FormRuleContext(schema, values, values.keySet(), stored, mode, instance, LocalDate.of(2026, 7, 15));
    }

    private FormValidationReport validate(Map<String, Object> values, ValidationMode mode) {
        FormValidationReport.Collector collector = FormValidationReport.collector();
        rules.validate(context(values, Map.of(), mode, null), collector);
        return collector.build();
    }

    @Test
    @DisplayName("唯一分支：involve_cost=false → ②跳过；true → ②不跳过；缺省 = false")
    void uniqueBranch() {
        assertThat(MatterFormRules.branchOf(Map.of("involve_cost", Boolean.FALSE)))
                .containsEntry("skipFinanceReview", true)
                .containsEntry("skippedNode", 2)
                .containsEntry("skippedNodeCode", "finance_review")
                .containsEntry("ownerDeptRecorded", "财务部");

        assertThat(MatterFormRules.branchOf(Map.of("involve_cost", Boolean.TRUE)))
                .containsEntry("skipFinanceReview", false)
                .containsEntry("skippedNode", null);

        assertThat(MatterFormRules.branchOf(Map.of()))
                .as("模板 defaultValue=false（doc/templates.md §2.4）")
                .containsEntry("involveCost", Boolean.FALSE)
                .containsEntry("skipFinanceReview", true);
    }

    @Test
    @DisplayName("分支求值与**引擎同源**：复用 SkipConditionEvaluator，二者恒等（doc/templates.md §1.5）")
    void branchAgreesWithEngineSkipCondition() {
        for (Object value : new Object[] {Boolean.TRUE, Boolean.FALSE, "true", "false"}) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("involve_cost", value);
            assertThat(MatterFormRules.shouldSkipFinanceReview(values))
                    .as("表单侧分支与引擎跳过条件必须一致（否则②既产生待办又被标记 skipped）")
                    .isEqualTo(Boolean.TRUE.equals(MatterFormRules.branchOf(values).get("skipFinanceReview")));
        }
        assertThat(MatterFormRules.FINANCE_SKIP_CONDITION)
                .isEqualTo("{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}");

        // 「键缺失 / 值为 null」这一形态两侧**不**直接等价（引擎看到的是「不等 false」= 不跳过）；
        // 因此链路上必须先经模板默认值归一化（FormDataService#applyDefaults 补 false），
        // 归一化之后两侧才一致 —— 这是「分支只有一个判据」的落地条件，写成断言防回归。
        assertThat(MatterFormRules.branchOf(Map.of()).get("skipFinanceReview"))
                .as("模板 defaultValue=false（doc/templates.md §2.4）：缺省即「不涉及费用」→ 跳过②")
                .isEqualTo(true);
        assertThat(MatterFormRules.shouldSkipFinanceReview(Map.of("involve_cost", false)))
                .as("归一化补 false 后，引擎侧同样判定跳过")
                .isTrue();
    }

    @Test
    @DisplayName("涉及费用时两个费用字段必填；不涉及费用时不报必填（TC-FORM-004）")
    void involveCostConditionalRequired() {
        Map<String, Object> involving = new LinkedHashMap<>();
        involving.put("involve_cost", true);
        assertThat(validate(involving, ValidationMode.SUBMIT).issues())
                .extracting(issue -> issue.fieldCode())
                .contains("amount", "cost_bearer");

        Map<String, Object> notInvolving = new LinkedHashMap<>();
        notInvolving.put("involve_cost", false);
        assertThat(validate(notInvolving, ValidationMode.SUBMIT).hasIssueOn("amount")).isFalse();
        assertThat(validate(notInvolving, ValidationMode.SUBMIT).hasIssueOn("cost_bearer")).isFalse();
    }

    @Test
    @DisplayName("归一化：involve_cost=false 时清空 amount / cost_bearer（linkage.clearWhen）")
    void clearWhenNormalization() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("involve_cost", false);
        values.put("amount", "999.00");
        values.put("cost_bearer", "12");
        Map<String, Object> patch = rules.normalize(context(values, Map.of(), ValidationMode.DRAFT, null));
        assertThat(patch).containsEntry("amount", null).containsEntry("cost_bearer", null);

        Map<String, Object> involving = new LinkedHashMap<>();
        involving.put("involve_cost", true);
        assertThat(rules.normalize(context(involving, Map.of(), ValidationMode.DRAFT, null))).isEmpty();
    }

    @Test
    @DisplayName("类别：草稿可改；一经发起（非 draft）改判 → 40309（TC-FORM-003）")
    void categoryLock() {
        FlowInstanceRow draft = new FlowInstanceRow();
        draft.setStatus("draft");
        draft.setId(7001L);
        Map<String, Object> changed = Map.of("category", "hr");
        Map<String, Object> stored = Map.of("category", "business");

        FormValidationReport.Collector draftCollector = FormValidationReport.collector();
        rules.validateCategoryLock(context(changed, stored, ValidationMode.DRAFT, draft), draftCollector);
        assertThat(draftCollector.build().passed()).as("草稿（含驳回后重提回草稿）允许改判").isTrue();

        FlowInstanceRow approving = new FlowInstanceRow();
        approving.setStatus("approving");
        approving.setId(7002L);
        assertThatThrownBy(() -> rules.validateCategoryLock(
                context(changed, stored, ValidationMode.SUBMIT, approving), FormValidationReport.collector()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CATEGORY_IMMUTABLE)
                .hasMessageContaining("事项类别发起后不可改判");

        // 值未变化 → 不算改判
        rules.validateCategoryLock(context(Map.of("category", "business"), stored, ValidationMode.SUBMIT, approving),
                FormValidationReport.collector());
    }

    @Test
    @DisplayName("读取侧派生：分支 + 类别可改性 + 取证句")
    void describeCarriesEvidence() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("involve_cost", false);
        values.put("category", "business");
        Map<String, Object> view = rules.describe(context(values, Map.of(), ValidationMode.DRAFT, null));
        assertThat(view).containsEntry("skipFinanceReview", true).containsEntry("categoryMutable", true);
        assertThat(view.get("categoryLockEvidence").toString()).contains("forms.md");
        assertThat(rules.formType()).isEqualTo("matter");
        assertThat(MatterFormRules.formTypeEnum()).isEqualTo(com.oa.form.app.FormWritePolicy.FormType.MATTER);
    }
}
