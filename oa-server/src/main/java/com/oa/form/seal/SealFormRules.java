package com.oa.form.seal;

import com.oa.form.app.FormWritePolicy;
import com.oa.form.document.FormRuleContext;
import com.oa.form.document.FormTypeRules;
import com.oa.form.template.validate.ConditionEvaluator;
import com.oa.form.template.validate.FormValidationReport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * <b>印鉴证照审批单（{@code form_type = seal}）专属规则</b> —— 2b.3 第四类。
 *
 * <h2>规则一：使用期限</h2>
 * <p>{@code doc/forms.md} §5 字段表：{@code usage_start}「不早于今天」、
 * {@code usage_end}「≥ {@code usage_start}」；正文（{@code return_status = 已归还} 需填归还日期）。
 *
 * <h2>规则二：归还状态 / 归还日期的**闭环**与**三态例外**</h2>
 * <p>真源：
 * <ul>
 *   <li>{@code doc/forms.md} §5 {@code return_status} 行：「未归还 / 已归还 / 无需归还 | **三态例外**：
 *       草稿可改；审批中仅 **发起人（归还登记）** 与 **节点⑦归档登记人** 可改；待补件期只读 |
 *       未归还 | 改「已归还」需填归还日期」；</li>
 *   <li>{@code doc/forms.md} §5 例外边界表（**精确边界**）：
 *       审批中仅 {@code return_status}/{@code return_date}、【待补件】仅
 *       {@code attachments}+{@code supplement_note} 且归还字段**同样只读**、
 *       已通过/已终止/已撤回无人可写；</li>
 *   <li>{@code doc/forms.md} §5：「节点⑦改「已归还」时系统写入 {@code return_date} 并留痕；
 *       发起人提前登记归还也走同一校验，**改已归还必须有归还日期**」；</li>
 *   <li>{@code doc/dict-seed.md} §9：{@code returned} 行「选择本项时 {@code return_date} 必填」、
 *       {@code not_required} 行「终态备注，不参与超期提醒与催办」；</li>
 *   <li>可执行用例 {@code doc/test-cases.md} TC-FORM-009（① 改「已归还」不填日期被拒）、
 *       TC-FORM-010（节点⑦可改、审批人不可改）、TC-FORM-013（待补件期同样只读）。</li>
 * </ul>
 * 本类的闭环校验**在两档都执行**（不只在 SUBMIT）：它是「字段对完整性」不变量，
 * 而不是「能否提交」的问题——审批中的归还登记同样必须成对。
 *
 * <h2>规则三：证照借用与用印份数互斥</h2>
 * <p>{@code doc/forms.md} §5 {@code seal_type} 行：「{@code 证照借用} 时 {@code cert_name} 必填、
 * {@code seal_count} 隐藏」；{@code seal_count} 行：「1–999 整数；{@code seal_type ≠ 证照借用} 时必填」；
 * {@code doc/dict-seed.md} §3 联动规则行同义（{@code cert_seal} 与 {@code cert_borrow} 并存）。
 */
@Service
public class SealFormRules implements FormTypeRules {

    /** 用印类型字段码（与合同单共用字典 {@code seal_type}）。 */
    public static final String FIELD_SEAL_TYPE = "seal_type";

    /** 证照类型字段码（字典类型 {@code cert_type}；标签统一为「证照类型」）。 */
    public static final String FIELD_CERT_NAME = "cert_name";

    /** 用印份数字段码。 */
    public static final String FIELD_SEAL_COUNT = "seal_count";

    /** 使用开始日期。 */
    public static final String FIELD_USAGE_START = "usage_start";

    /** 使用结束日期。 */
    public static final String FIELD_USAGE_END = "usage_end";

    /** 是否对外提供。 */
    public static final String FIELD_IS_EXTERNAL = "is_external";

    /** 归还状态字段码（三态例外的例外字段）。 */
    public static final String FIELD_RETURN_STATUS = "return_status";

    /** 归还日期字段码（三态例外的例外字段）。 */
    public static final String FIELD_RETURN_DATE = "return_date";

    /** 「证照借用」的字典 code（{@code doc/dict-seed.md} §3）。 */
    public static final String SEAL_TYPE_CERT_BORROW = "cert_borrow";

    /** 「已归还」的字典 code（{@code doc/dict-seed.md} §9）。 */
    public static final String RETURN_STATUS_RETURNED = "returned";

    /** 用印份数区间（{@code doc/forms.md} §5：1–999 整数）。 */
    public static final int SEAL_COUNT_MIN = 1;

    /** 用印份数上界。 */
    public static final int SEAL_COUNT_MAX = 999;

    @Override
    public String formType() {
        return "seal";
    }

    // ================================================================ 校验

    @Override
    public void validate(FormRuleContext context, FormValidationReport.Collector collector) {
        validateSealTypeLinkage(context, collector);
        validateUsagePeriod(context, collector);
        validateReturnClosure(context, collector);
        validatePurposeLength(context, collector);
    }

    /** 证照借用 / 用印份数互斥（{@code doc/forms.md} §5 联动列）。 */
    private void validateSealTypeLinkage(FormRuleContext context, FormValidationReport.Collector collector) {
        String sealType = context.value(FIELD_SEAL_TYPE) == null
                ? null : String.valueOf(context.value(FIELD_SEAL_TYPE)).trim();
        if (sealType == null || sealType.isEmpty()) {
            return;
        }
        if (SEAL_TYPE_CERT_BORROW.equals(sealType)) {
            if (context.mode().isSubmit() && context.schema().knows(FIELD_CERT_NAME)
                    && ConditionEvaluator.isEmpty(context.value(FIELD_CERT_NAME))) {
                collector.add(FIELD_CERT_NAME, labelOf(context, FIELD_CERT_NAME, "证照类型"),
                        "conditionalRequired", "证照类型为必填");
            }
            if (context.touched(FIELD_SEAL_COUNT) && !ConditionEvaluator.isEmpty(context.value(FIELD_SEAL_COUNT))) {
                collector.add(FIELD_SEAL_COUNT, labelOf(context, FIELD_SEAL_COUNT, "用印份数"),
                        "linkage", "「证照借用」无需填写用印份数（该字段在证照借用时隐藏）");
            }
            return;
        }
        if (!context.mode().isSubmit() || !context.schema().knows(FIELD_SEAL_COUNT)) {
            return;
        }
        Object raw = context.value(FIELD_SEAL_COUNT);
        if (ConditionEvaluator.isEmpty(raw)) {
            collector.add(FIELD_SEAL_COUNT, labelOf(context, FIELD_SEAL_COUNT, "用印份数"),
                    "conditionalRequired", "用印份数为必填（1–999 的整数）");
            return;
        }
        java.math.BigDecimal count = ConditionEvaluator.decimalOf(raw);
        if (count == null || count.stripTrailingZeros().scale() > 0
                || count.compareTo(java.math.BigDecimal.valueOf(SEAL_COUNT_MIN)) < 0
                || count.compareTo(java.math.BigDecimal.valueOf(SEAL_COUNT_MAX)) > 0) {
            collector.add(FIELD_SEAL_COUNT, labelOf(context, FIELD_SEAL_COUNT, "用印份数"),
                    "numberRange", "用印份数必须是 1–999 的整数");
        }
    }

    /** 使用期限：开始不早于今天、结束 ≥ 开始（{@code doc/forms.md} §5）。 */
    private void validateUsagePeriod(FormRuleContext context, FormValidationReport.Collector collector) {
        LocalDate start = parseDate(context.value(FIELD_USAGE_START));
        LocalDate end = parseDate(context.value(FIELD_USAGE_END));
        if (start != null && context.today() != null && start.isBefore(context.today())) {
            collector.add(FIELD_USAGE_START, labelOf(context, FIELD_USAGE_START, "使用开始日期"),
                    "dateNotBefore", "使用开始日期不能早于今天");
        }
        if (start != null && end != null && end.isBefore(start)) {
            collector.add(FIELD_USAGE_END, labelOf(context, FIELD_USAGE_END, "使用结束日期"),
                    "dateNotBeforeField", "使用结束日期不能早于使用开始日期");
        }
    }

    /**
     * 归还闭环：{@code return_status = returned} 必须有 {@code return_date}
     * （**两档都判**：审批中的归还登记同样必须成对，TC-FORM-009 的①）。
     *
     * <p>反向：擅自给出 {@code return_date} 而状态不是「已归还」→ 拒绝（否则会留下
     * 「状态未归还、却已写归还日期」的脏数据，超期提醒口径也会失真）。
     */
    private void validateReturnClosure(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.schema().knows(FIELD_RETURN_STATUS)) {
            return;
        }
        Object statusRaw = context.value(FIELD_RETURN_STATUS);
        String status = statusRaw == null ? null : String.valueOf(statusRaw).trim();
        Object dateRaw = context.value(FIELD_RETURN_DATE);
        boolean hasDate = !ConditionEvaluator.isEmpty(dateRaw);
        if (RETURN_STATUS_RETURNED.equals(status)) {
            if (!hasDate) {
                collector.add(FIELD_RETURN_DATE, labelOf(context, FIELD_RETURN_DATE, "归还日期"),
                        "conditionalRequired", "归还状态改为「已归还」时必须填写归还日期");
            } else if (parseDate(dateRaw) == null) {
                collector.add(FIELD_RETURN_DATE, labelOf(context, FIELD_RETURN_DATE, "归还日期"),
                        "dateFormat", "「归还日期」日期格式不合法（应为 YYYY-MM-DD）");
            }
            return;
        }
        if (hasDate && status != null && !status.isEmpty()) {
            collector.add(FIELD_RETURN_DATE, labelOf(context, FIELD_RETURN_DATE, "归还日期"),
                    "linkage", "只有归还状态为「已归还」时才填写归还日期");
        }
    }

    /** 用途说明：基础 ≥5；对外提供时 ≥20（schema 的 minLength / conditionalMinLength 兜底之外的业务口径）。 */
    private void validatePurposeLength(FormRuleContext context, FormValidationReport.Collector collector) {
        if (!context.schema().knows("purpose")) {
            return;
        }
        Object raw = context.value("purpose");
        if (ConditionEvaluator.isEmpty(raw)) {
            return;
        }
        int length = String.valueOf(raw).length();
        if (length < 5) {
            collector.add("purpose", labelOf(context, "purpose", "用途说明"), "minLength",
                    "用途说明至少 5 个字符");
            return;
        }
        Boolean external = ConditionEvaluator.booleanOf(context.value(FIELD_IS_EXTERNAL));
        if (Boolean.TRUE.equals(external) && length < 20) {
            collector.add("purpose", labelOf(context, "purpose", "用途说明"), "conditionalMinLength",
                    "「是否对外提供 = 是」时用途说明至少 20 个字符");
        }
    }

    // ================================================================ 读取侧

    @Override
    public Map<String, Object> describe(FormRuleContext context) {
        Map<String, Object> view = new LinkedHashMap<>();
        String sealType = context.value(FIELD_SEAL_TYPE) == null
                ? null : String.valueOf(context.value(FIELD_SEAL_TYPE)).trim();
        boolean borrow = SEAL_TYPE_CERT_BORROW.equals(sealType);
        view.put("sealType", sealType);
        view.put("certNameRequired", borrow);
        view.put("sealCountRequired", !borrow);
        view.put("usageStart", context.value(FIELD_USAGE_START));
        view.put("usageEnd", context.value(FIELD_USAGE_END));
        view.put("returnStatus", context.value(FIELD_RETURN_STATUS));
        view.put("returnDate", context.value(FIELD_RETURN_DATE));
        view.put("returnClosure", "return_status = returned ⟺ return_date 有值（doc/forms.md §5 / doc/dict-seed.md §9）");
        view.put("threeStateException", threeStateException());
        return view;
    }

    /**
     * 三态例外白名单的**机器可读披露**（前端置灰与排障都看这一份；边界仍在服务端）。
     */
    public static Map<String, Object> threeStateException() {
        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("formType", "seal");
        exception.put("fields", new LinkedHashSet<>(FormWritePolicy.SEAL_RETURN_FIELDS));
        exception.put("draft", "发起人可改全部字段");
        exception.put("approving", "仅发起人（归还登记）与节点⑦归档登记人可改 return_status / return_date；其余主字段一律只读");
        exception.put("pendingSupplement", "仅 attachments + supplement_note；return_status / return_date 同样只读");
        exception.put("closed", "无人可写");
        exception.put("evidence", "doc/forms.md §5 例外边界表 + §7 对照表 + doc/test-cases.md TC-FORM-013");
        return exception;
    }

    /** 归还需要写入 {@code sys_thread} 留痕的动作口径（{@code doc/forms.md} §5 末）。 */
    public static Set<String> returnTraceFields() {
        return new LinkedHashSet<>(FormWritePolicy.SEAL_RETURN_FIELDS);
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
}
