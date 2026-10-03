package com.oa.form.contract;

import com.oa.form.document.FormRuleContext;
import com.oa.form.document.FormTypeRules;
import com.oa.form.template.validate.ConditionEvaluator;
import com.oa.form.template.validate.FormValidationReport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * <b>合同审批单（{@code form_type = contract}）专属规则</b> —— 2b.3 第三类。
 *
 * <h2>规则一：必传文本字段（空串 / 仅空白视为未填）</h2>
 * <p>真源：{@code doc/forms.md} §4 字段表（必填列的「是」）+ §1.3 通用校验「必填 | 非空
 * （<b>空字符串、全空格视为空</b>）| 「请填写{标签}」」。
 * 合同单的**无条件必填文本字段**为：
 * <ul>
 *   <li>{@code title} 合同名称（≤80）；</li>
 *   <li>{@code counterparty} 对方主体名称（≤100）；</li>
 *   <li>{@code counterparty_credit} 对方统一社会信用代码（18 位数字+大写字母）；</li>
 *   <li>{@code contract_type} 合同类型（字典 {@code contract_type}）。</li>
 * </ul>
 * 条件必填：{@code contract_type_other}（{@code contract_type = 其他} 时必填，≤40）。
 *
 * <h2>规则二：合同文本附件必传（≥1）</h2>
 * <p>{@code doc/forms.md} §4：「attachments | 合同文本附件 | files | 是 | — | ≥1 个；见 1.4」；
 * {@code doc/templates.md} §1.3 末：「须上传合同文本附件（≥1）」。
 *
 * <h2>规则三：框架合同的金额与期限</h2>
 * <p>{@code doc/forms.md} §4 {@code is_framework} 行：「是 时 {@code amount} 可填上限金额，
 * 且 {@code period_end} 必填」。一期**不做**合同台账（§4 业务补充说明：审批通过后人工导出归档），
 * 故此处只做「上限金额」的**语义披露**（不改变 amountRange 校验值域），见
 * {@link #describe(FormRuleContext)} 的 {@code frameworkAmountSemantics}。
 */
@Service
public class ContractFormRules implements FormTypeRules {

    /** 必传文本字段（空串/仅空白视为未填）。 */
    public static final List<String> REQUIRED_TEXT_FIELDS =
            List.of("title", "counterparty", "counterparty_credit", "contract_type");

    /** 条件必填文本字段：{@code contract_type = other} 时必填。 */
    public static final String FIELD_CONTRACT_TYPE = "contract_type";

    /** 其他类型说明。 */
    public static final String FIELD_CONTRACT_TYPE_OTHER = "contract_type_other";

    /** 履约开始日期。 */
    public static final String FIELD_PERIOD_START = "period_start";

    /** 履约结束日期。 */
    public static final String FIELD_PERIOD_END = "period_end";

    /** 是否框架合同。 */
    public static final String FIELD_IS_FRAMEWORK = "is_framework";

    /** 合同文本附件。 */
    public static final String FIELD_ATTACHMENTS = "attachments";

    /** 统一社会信用代码形状（18 位数字 + 大写字母）。 */
    public static final String CREDIT_CODE_PATTERN = "^[0-9A-Z]{18}$";

    @Override
    public String formType() {
        return "contract";
    }

    // ================================================================ 校验

    @Override
    public void validate(FormRuleContext context, FormValidationReport.Collector collector) {
        validateRequiredText(context, collector);
        validateCounterpartyCredit(context, collector);
        validateOtherTypeNote(context, collector);
        validateTerm(context, collector);
        validateFramework(context, collector);
        validateContractAttachments(context, collector);
    }

    /**
     * 必传文本字段：空串与全空白视为未填（{@code doc/forms.md} §1.3 必填行）。
     *
     * <p>只对「schema 里登记了该字段」的做判定 —— 模板可以按 {@code doc/templates.md} §5.2
     * 「从 {@code form_schema_json.fields[]} 中删除该字段项」的方式隐藏字段，
     * 隐藏后不应再报「请填写」。
     */
    private void validateRequiredText(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.mode().isSubmit()) {
            return;
        }
        for (String code : REQUIRED_TEXT_FIELDS) {
            if (!context.schema().knows(code)) {
                continue;
            }
            Object raw = context.value(code);
            if (raw == null || isBlankText(raw)) {
                collector.add(code, labelOf(context, code, code), "required",
                        "请填写" + labelOf(context, code, code));
            }
        }
    }

    /** 统一社会信用代码：18 位数字或大写字母（无论必填与否，填了就必须合规）。 */
    private void validateCounterpartyCredit(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.schema().knows("counterparty_credit")) {
            return;
        }
        Object raw = context.value("counterparty_credit");
        if (raw == null || isBlankText(raw)) {
            return;
        }
        String text = String.valueOf(raw).trim();
        if (!text.matches(CREDIT_CODE_PATTERN)) {
            collector.add("counterparty_credit", labelOf(context, "counterparty_credit", "对方统一社会信用代码"),
                    "pattern", "统一社会信用代码须为 18 位数字或大写字母");
        }
    }

    /** {@code contract_type = other} → {@code contract_type_other} 条件必填（SUBMIT 档）。 */
    private void validateOtherTypeNote(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.mode().isSubmit() || !context.schema().knows(FIELD_CONTRACT_TYPE_OTHER)) {
            return;
        }
        Object type = context.value(FIELD_CONTRACT_TYPE);
        if (!"other".equals(String.valueOf(type))) {
            return;
        }
        Object note = context.value(FIELD_CONTRACT_TYPE_OTHER);
        if (note == null || isBlankText(note)) {
            collector.add(FIELD_CONTRACT_TYPE_OTHER, labelOf(context, FIELD_CONTRACT_TYPE_OTHER, "其他类型说明"),
                    "conditionalRequired", "合同类型为其他时，其他类型说明为必填");
        }
    }

    /** 履约区间：结束 ≥ 开始（{@code doc/forms.md} §4 {@code period_end} 行）。 */
    private void validateTerm(FormRuleContext context, FormValidationReport.Collector collector) {
        LocalDate start = parseDate(context.value(FIELD_PERIOD_START));
        LocalDate end = parseDate(context.value(FIELD_PERIOD_END));
        if (start == null || end == null) {
            return;
        }
        if (end.isBefore(start)) {
            collector.add(FIELD_PERIOD_END, labelOf(context, FIELD_PERIOD_END, "履约结束日期"),
                    "dateNotBeforeField", "履约结束日期不能早于履约开始日期");
        }
    }

    /** 框架合同：{@code period_end} 必填（{@code doc/forms.md} §4 {@code is_framework} 行）。 */
    private void validateFramework(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.mode().isSubmit()) {
            return;
        }
        Boolean framework = ConditionEvaluator.booleanOf(context.value(FIELD_IS_FRAMEWORK));
        if (!Boolean.TRUE.equals(framework)) {
            return;
        }
        if (context.schema().knows(FIELD_PERIOD_END)
                && ConditionEvaluator.isEmpty(context.value(FIELD_PERIOD_END))) {
            collector.add(FIELD_PERIOD_END, labelOf(context, FIELD_PERIOD_END, "履约结束日期"),
                    "conditionalRequired", "框架合同的履约结束日期为必填");
        }
    }

    /** 合同文本附件 ≥1（{@code doc/forms.md} §4 / {@code doc/templates.md} §1.3）。 */
    private void validateContractAttachments(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.mode().isSubmit() || !context.schema().knows(FIELD_ATTACHMENTS)) {
            return;
        }
        if (ConditionEvaluator.isEmpty(context.value(FIELD_ATTACHMENTS))) {
            collector.add(FIELD_ATTACHMENTS, labelOf(context, FIELD_ATTACHMENTS, "合同文本附件"),
                    "filePolicy", "请上传合同文本附件（至少 1 个）");
        }
    }

    // ================================================================ 读取侧

    @Override
    public Map<String, Object> describe(FormRuleContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requiredTextFields", REQUIRED_TEXT_FIELDS);
        view.put("creditCodePattern", CREDIT_CODE_PATTERN);
        Boolean framework = ConditionEvaluator.booleanOf(context.value(FIELD_IS_FRAMEWORK));
        view.put("isFramework", framework);
        view.put("frameworkAmountSemantics", Boolean.TRUE.equals(framework)
                ? "框架合同：amount 记为合同金额上限（一期仅记录，不做台账与超限拦截）"
                : "非框架合同：amount 为合同金额");
        view.put("periodStart", context.value(FIELD_PERIOD_START));
        view.put("periodEnd", context.value(FIELD_PERIOD_END));
        view.put("manualArchive", "doc/forms.md §4：一期不做合同台账，审批通过后由集团办/经发部人工导出归档（PRD §8.2）");
        view.put("otherReviewDepts", context.value("other_review_depts"));
        view.put("otherReviewDeptsEvidence",
                "doc/forms.md §6.9：字段 other_review_depts 的字典类型为 review_dept_other，multiselect ≤10 项，不参与路由");
        return view;
    }

    // ================================================================ 工具

    private static boolean isBlankText(Object raw) {
        return raw instanceof CharSequence sequence && sequence.toString().trim().isEmpty();
    }

    private static LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw).trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String labelOf(FormRuleContext context, String code, String fallback) {
        return context.schema().field(code).map(field -> field.label()).orElse(fallback);
    }

    /** 必传文本字段清单（对外披露；不含条件必填）。 */
    public static List<String> requiredTextFields() {
        return new ArrayList<>(REQUIRED_TEXT_FIELDS);
    }
}
