package com.oa.form.contract;

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
 * <b>2b.3 合同审批单专属规则</b>（doc/forms.md §4 / §1.3；doc/templates.md §1.3；TC-FORM-007）。
 */
class ContractFormRulesTest {

    private final ContractFormRules rules = new ContractFormRules();
    private FormSchema schema;

    @BeforeEach
    void setUp() {
        schema = FormSchemaFixtures.schema("contract");
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
        payload.put("title", "供热管网维护合同");
        payload.put("contract_type", "service");
        payload.put("counterparty", "某某市政工程有限公司");
        payload.put("counterparty_credit", "91310000MA1K35XXXX");
        payload.put("amount", "1250000.00");
        payload.put("period_start", "2026-08-01");
        payload.put("period_end", "2027-07-31");
        payload.put("is_framework", false);
        payload.put("seal_type", "contract_seal");
        payload.put("attachments", List.of(Map.of("fileName", "contract.pdf", "fileSize", 4096)));
        return payload;
    }

    @Test
    @DisplayName("必传文本字段：空串与**仅空白**都视为未填（forms.md §1.3 必填行）")
    void blankTextIsUnfilled() {
        for (String field : ContractFormRules.REQUIRED_TEXT_FIELDS) {
            Map<String, Object> payload = base();
            payload.put(field, "   ");
            assertThat(validate(payload, ValidationMode.SUBMIT).messagesOf(field))
                    .as("%s 仅空白 = 未填", field)
                    .isNotEmpty();
        }
        assertThat(validate(base(), ValidationMode.SUBMIT).passed())
                .as("完整合法的合同单通过：%s", validate(base(), ValidationMode.SUBMIT).summary())
                .isTrue();
    }

    @Test
    @DisplayName("必传文本字段：草稿档不强制（可先存一半）")
    void draftAllowsIncomplete() {
        assertThat(validate(Map.of(), ValidationMode.DRAFT).passed()).isTrue();
    }

    @Test
    @DisplayName("合同文本附件必传 ≥1（forms.md §4 / templates.md §1.3 / TC-FORM-007 的①）")
    void contractAttachmentsRequired() {
        Map<String, Object> payload = base();
        payload.remove("attachments");
        assertThat(validate(payload, ValidationMode.SUBMIT).messagesOf("attachments"))
                .anyMatch(message -> message.contains("请上传合同文本附件"));
    }

    @Test
    @DisplayName("统一社会信用代码：17 位被拒（TC-FORM-007 的②）")
    void creditCodeShape() {
        Map<String, Object> payload = base();
        payload.put("counterparty_credit", "91310000MA1K35XXX");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("counterparty_credit"))
                .containsExactly("统一社会信用代码须为 18 位数字或大写字母");
    }

    @Test
    @DisplayName("履约区间：结束早于开始被拒；其他会审部门沿用字典多选")
    void termAndReviewDepts() {
        Map<String, Object> payload = base();
        payload.put("period_end", "2026-07-31");
        assertThat(validate(payload, ValidationMode.DRAFT).messagesOf("period_end"))
                .containsExactly("履约结束日期不能早于履约开始日期");

        Map<String, Object> withDepts = base();
        withDepts.put("other_review_depts", List.of("econ_dev", "group_office"));
        assertThat(validate(withDepts, ValidationMode.SUBMIT).passed()).isTrue();
    }

    @Test
    @DisplayName("其他类型说明：contract_type=other 时必填")
    void otherTypeNoteConditional() {
        Map<String, Object> payload = base();
        payload.put("contract_type", "other");
        assertThat(validate(payload, ValidationMode.SUBMIT).messagesOf("contract_type_other")).isNotEmpty();
        payload.put("contract_type_other", "联合体协议");
        assertThat(validate(payload, ValidationMode.SUBMIT).messagesOf("contract_type_other")).isEmpty();
    }

    @Test
    @DisplayName("框架合同：period_end 必填；金额语义披露为上限金额")
    void frameworkRules() {
        Map<String, Object> payload = base();
        payload.put("is_framework", true);
        payload.remove("period_end");
        assertThat(validate(payload, ValidationMode.SUBMIT).messagesOf("period_end"))
                .anyMatch(message -> message.contains("框架合同"));

        Map<String, Object> values = base();
        values.put("is_framework", true);
        FormRuleContext context = new FormRuleContext(schema, values, values.keySet(), Map.of(),
                ValidationMode.DRAFT, null, LocalDate.now());
        assertThat(rules.describe(context).get("frameworkAmountSemantics").toString()).contains("上限");
    }

    @Test
    @DisplayName("读取侧派生：必填文本清单 + 人工归档口径 + 会审部门取证")
    void describe() {
        FormRuleContext context = new FormRuleContext(schema, base(), base().keySet(), Map.of(),
                ValidationMode.DRAFT, null, LocalDate.now());
        Map<String, Object> view = rules.describe(context);
        assertThat(view).containsEntry("creditCodePattern", "^[0-9A-Z]{18}$");
        assertThat(view.get("manualArchive").toString()).contains("人工导出归档");
        assertThat(view.get("otherReviewDeptsEvidence").toString()).contains("review_dept_other");
        assertThat(ContractFormRules.requiredTextFields()).hasSize(4);
        assertThat(rules.formType()).isEqualTo("contract");
    }
}
