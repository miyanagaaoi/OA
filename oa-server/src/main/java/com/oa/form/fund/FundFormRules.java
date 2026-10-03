package com.oa.form.fund;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.form.document.FormRuleContext;
import com.oa.form.document.FormTypeRules;
import com.oa.form.template.validate.AmountText;
import com.oa.form.template.validate.ConditionEvaluator;
import com.oa.form.template.validate.FormValidationReport;
import com.oa.workflow.definition.app.SkipConditionEvaluator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * <b>资金审批单（{@code form_type = fund}）专属规则</b> —— 2b.3 第二类。
 *
 * <h2>规则一：金额 &gt; 0 且**禁浮点**</h2>
 * <p>真源：
 * <ul>
 *   <li>{@code doc/forms.md} §3 字段表 {@code amount} 行原文：「申请金额 | amount | 是 | — |
 *       {@code > 0}，见 1.5；**提交前必须通过**」；</li>
 *   <li>{@code doc/forms.md} §3 业务补充说明：「金额为 0 或空时**禁止提交**（PRD 13.2）」；</li>
 *   <li>{@code doc/forms.md} §11.5「金额字段禁止浮点：服务端只接受字符串或定点数，
 *       禁止经过浮点运算后再落库」；</li>
 *   <li>{@code doc/data-model.md} §4.4 示例：{@code "amount": "1250000.00"}（**字符串**）。</li>
 * </ul>
 *
 * <h2>规则二：{@code plan_category} / {@code payment_belong} **只存不用**</h2>
 * <p>真源：
 * <ul>
 *   <li>{@code doc/forms.md} §3 字段表两行：「一期仅存储数据，不参与任何流程判断（Q8 已关闭）；
 *       **禁止**在 {@code dict_type} 中建目」/「一期仅存储数据（Q9 已关闭）」；</li>
 *   <li>{@code doc/forms.md} §3 末：「这两个字段**不得**被流程条件、数据域过滤或超时规则引用——
 *       一期定位是『只存不用』」；</li>
 *   <li>{@code doc/templates.md} §1.2 末：「{@code plan_category} / {@code payment_belong}
 *       **只存不用**，不得出现在任何 {@code skip_condition} 中」；</li>
 *   <li>{@code doc/data-model.md} §4.4：「一期**只写不读**——流程引擎、数据域过滤、超时规则
 *       一律不得引用这两个键」。</li>
 * </ul>
 * 落点：{@link #assertNotRouted} 是**拒绝式**机检（被引用即抛 40008），
 * 由接口 {@code POST /api/v1/forms/fund/fields/plan-category/assert-storage-only}
 * 与单测共同消费。
 */
@Service
public class FundFormRules implements FormTypeRules {

    /** 申请金额字段码。 */
    public static final String FIELD_AMOUNT = "amount";

    /** 计划类别（**布尔 checkbox，非字典项**）。 */
    public static final String FIELD_PLAN_CATEGORY = "plan_category";

    /** 付款归属（**布尔 checkbox，非字典项**）。 */
    public static final String FIELD_PAYMENT_BELONG = "payment_belong";

    /** 收款账号字段码（**不进 `fields_json`**，见 data-model.md §8.2）。 */
    public static final String FIELD_PAYEE_ACCOUNT = "payee_account";

    /** 附件字段码（必填 ≥1）。 */
    public static final String FIELD_ATTACHMENTS = "attachments";

    /** 只存不用字段。 */
    public static final List<String> STORAGE_ONLY_FIELDS = List.of(FIELD_PLAN_CATEGORY, FIELD_PAYMENT_BELONG);

    @Override
    public String formType() {
        return "fund";
    }

    // ================================================================ 校验

    @Override
    public void validate(FormRuleContext context, FormValidationReport.Collector collector) {
        validateAmount(context, collector);
        validateStorageOnlyFields(context, collector);
        validateAttachments(context, collector);
    }

    /**
     * 金额：空 / 0 / 负数 / 超过两位小数 / **浮点** 一律拒绝（**SUBMIT 档必须通过**）。
     *
     * <p>DRAFT 档只在「已填了值」时判格式与范围（草稿允许留空，但填了就必须是合法定点数）。
     */
    private void validateAmount(FormRuleContext context, FormValidationReport.Collector collector) {
        Object raw = context.value(FIELD_AMOUNT);
        boolean empty = ConditionEvaluator.isEmpty(raw);
        if (empty) {
            if (context.mode().isSubmit()) {
                // 与 forms.md §1.3 的金额文案逐字一致（§3 补充说明：0 或空时禁止提交）
                collector.add(FIELD_AMOUNT, labelOf(context, FIELD_AMOUNT, "申请金额"), "amountRange",
                        "金额必须大于 0 且最多两位小数");
            }
            return;
        }
        AmountText.Parsed parsed = AmountText.parse(raw);
        if (!parsed.ok()) {
            collector.add(FIELD_AMOUNT, labelOf(context, FIELD_AMOUNT, "申请金额"), "amountRange", parsed.error());
        }
    }

    /**
     * 只存不用字段：只接受真布尔（非布尔即拒；**不接受**字典 code，因为它们不是字典项）。
     */
    private void validateStorageOnlyFields(FormRuleContext context, FormValidationReport.Collector collector) {
        for (String code : STORAGE_ONLY_FIELDS) {
            if (!context.schema().knows(code) || !context.touched(code)) {
                continue;
            }
            Object raw = context.value(code);
            if (ConditionEvaluator.isEmpty(raw)) {
                continue;
            }
            Boolean parsed = ConditionEvaluator.booleanOf(raw);
            if (parsed == null || !(raw instanceof Boolean || raw instanceof Number
                    || "true".equalsIgnoreCase(String.valueOf(raw)) || "false".equalsIgnoreCase(String.valueOf(raw)))) {
                collector.add(code, labelOf(context, code, code), "typeMismatch",
                        String.format("「%s」是布尔勾选框（勾选 = %s），不是字典项，取值只能是 true/false",
                                labelOf(context, code, code), storageOnlyMeaning(code)));
            }
        }
    }

    private void validateAttachments(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.schema().knows(FIELD_ATTACHMENTS)) {
            return;
        }
        Object raw = context.value(FIELD_ATTACHMENTS);
        if (ConditionEvaluator.isEmpty(raw) && context.mode().isSubmit()) {
            collector.add(FIELD_ATTACHMENTS, labelOf(context, FIELD_ATTACHMENTS, "附件"), "filePolicy",
                    "请上传附件（发票/合同/说明，至少 1 个）");
        }
    }

    // ================================================================ 只存不用的机检

    /**
     * 断言这两个字段**没有**被流程条件引用（拒绝式）。
     *
     * @param skipConditions 模板各节点的 {@code skip_condition} JSON 文本（可含 {@code null}）
     * @throws com.oa.common.error.BizException 40008 一旦被引用
     */
    public static void assertNotRouted(List<String> skipConditions) {
        List<String> offenders = new ArrayList<>();
        if (skipConditions != null) {
            for (String json : skipConditions) {
                if (json == null || json.isBlank()) {
                    continue;
                }
                SkipConditionEvaluator.Condition condition;
                try {
                    condition = SkipConditionEvaluator.parse(json);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (condition != null && STORAGE_ONLY_FIELDS.contains(condition.field())) {
                    offenders.add(json.trim());
                }
            }
        }
        if (!offenders.isEmpty()) {
            throw new com.oa.common.error.BizException(
                    com.oa.common.error.ErrorCode.FLOW_DEFINITION_INVALID,
                    String.format("计划类别 / 付款归属是「只存不用」字段，不得出现在任何 skip_condition 中：%s"
                                    + "（doc/forms.md §3 末 / doc/templates.md §1.2 末 / doc/data-model.md §4.4）",
                            String.join("；", offenders)))
                    .withDetail("offenders", offenders)
                    .withDetail("storageOnlyFields", STORAGE_ONLY_FIELDS);
        }
    }

    /** 从任意 JSON 节点里抽出 {@code field}（供机检在无 DB 场景下直接判定）。 */
    public static String referencedField(JsonNode condition) {
        if (condition == null || !condition.isObject()) {
            return null;
        }
        JsonNode field = condition.get("field");
        return field == null || field.isNull() ? null : field.asText();
    }

    // ================================================================ 读取侧

    @Override
    public Map<String, Object> describe(FormRuleContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("amountField", FIELD_AMOUNT);
        Object raw = context.value(FIELD_AMOUNT);
        AmountText.Parsed parsed = AmountText.parse(raw);
        view.put("amountCanonical", parsed.ok() ? parsed.canonical() : null);
        view.put("amountValid", parsed.ok());
        view.put("amountNoFloat", true);
        view.put("storageOnlyFields", STORAGE_ONLY_FIELDS);
        view.put("planCategory", context.value(FIELD_PLAN_CATEGORY));
        view.put("paymentBelong", context.value(FIELD_PAYMENT_BELONG));
        view.put("planCategoryMeaning", storageOnlyMeaning(FIELD_PLAN_CATEGORY));
        view.put("paymentBelongMeaning", storageOnlyMeaning(FIELD_PAYMENT_BELONG));
        view.put("storageOnlyEvidence",
                "doc/forms.md §3：一期仅存储数据，不参与任何流程判断（Q8/Q9 已关闭）；"
                        + "不得被流程条件、数据域过滤或超时规则引用");
        view.put("routingDisclosure", AmountText.routingDisclosure());
        return view;
    }

    /** 勾选语义（打印稿文案，{@code doc/dict-seed.md} §6 / §7）。 */
    public static String storageOnlyMeaning(String code) {
        return FIELD_PLAN_CATEGORY.equals(code) ? "计划内 / 计划外（勾选 = 计划内）" : "本月度 / 非本月度（勾选 = 本月度）";
    }

    private static String labelOf(FormRuleContext context, String code, String fallback) {
        return context.schema().field(code).map(field -> field.label()).orElse(fallback);
    }

    /** 默认值补全口径（{@code doc/data-model.md} §4.4：缺省时按默认值补全后落库，默认均为 {@code true}）。 */
    public static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(FIELD_PLAN_CATEGORY, Boolean.TRUE);
        defaults.put(FIELD_PAYMENT_BELONG, Boolean.TRUE);
        return defaults;
    }
}
