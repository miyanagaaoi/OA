package com.oa.form.template.validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.form.dict.FormDictService;
import com.oa.form.template.schema.FormFieldDef;
import com.oa.form.template.schema.FormFieldType;
import com.oa.form.template.schema.FormSchema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>2b.1 服务端二次校验（核心：不许信客户端）</b>。
 *
 * <h2>为什么必须有（真源原文）</h2>
 * <ul>
 *   <li>{@code doc/forms.md} §11.2「**服务端二次校验**：所有必填、长度、金额、日期校验必须在服务端执行，
 *       前端校验仅为体验」；</li>
 *   <li>{@code doc/templates.md} §2.5「服务端校验要求（不可省略）」：二次校验 / 状态白名单 /
 *       金额禁止浮点 / 字段级权限 / <b>未知键</b>——「服务端遇到未登记的字段 {@code code} 一律**拒绝**，
 *       不允许静默透传」；</li>
 *   <li>{@code doc/prd-0.1.md} AC-28：「尝试通过**前端篡改请求或内部接口越权调用**修改主字段被拒绝」。</li>
 * </ul>
 *
 * <h2>三条不可回退的行为</h2>
 * <ol>
 *   <li><b>未登记字段一律拒绝</b>（不是忽略）：{@link #rejectUnknownFields}；
 *       生产链路上该判定在 {@code com.oa.form.app.FormDataService} 里以
 *       {@link com.oa.common.error.ErrorCode#FIELD_NOT_IN_SCHEMA}（403）**先于**其余校验抛出，
 *       此处保留同一判定用于干跑接口按项返回；</li>
 *   <li><b>一次返回全部失败项</b>：收集器累积，最后 {@link FormValidationReport#fail()} 一次抛出；</li>
 *   <li><b>金额禁浮点</b>：JSON 浮点字面量（{@code Double}/{@code Float}）直接判失败
 *       （{@link AmountText}）。</li>
 * </ol>
 *
 * <p>本类**不做**状态白名单判定（那是 {@code oa.form.template.write-model} 的职责），
 * 也不做四类单据的业务分支（那是 {@code oa.form.{matter,fund,contract,seal}} 的职责）——
 * 三者由 {@code com.oa.form.app.FormDataService} 按固定顺序串起来：
 * <b>未知字段 → 状态白名单 → schema 校验 → 单据专属规则</b>。
 */
public final class FormPayloadValidator {

    private static final Logger log = LoggerFactory.getLogger(FormPayloadValidator.class);

    /** 附件全局规则（{@code doc/forms.md} §1.4，缺省值；模板 {@code filePolicy} 可收窄）。 */
    public static final int ATTACHMENT_MAX_SIZE_MB = 50;
    /** 单次上传数量上限（§1.4）。 */
    public static final int ATTACHMENT_MAX_PER_UPLOAD = 20;
    /** 单张单据附件总数上限（含补件，§1.4）。 */
    public static final int ATTACHMENT_MAX_PER_INSTANCE = 50;
    /** 允许格式 15 种（§1.4 表 + {@code doc/enums.md} §12.1）。 */
    public static final Set<String> ATTACHMENT_ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "wps", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "heic", "zip", "rar", "7z");
    /** 禁止格式 9 种（§1.4 表 + {@code doc/enums.md} §12.2「上传即拒绝」）。 */
    public static final Set<String> ATTACHMENT_DENIED_EXT = Set.of(
            "exe", "bat", "cmd", "js", "vbs", "ps1", "dll", "msi", "scr");

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /**
     * <b>系统预留字段</b>：不属于任何 {@code form_schema_json.fields[]}，但合法的写入键。
     *
     * <p>唯一一项 {@code supplement_note}（补件说明）：{@code doc/forms.md} §8 把它定义为
     * 「补件时除附件外，另有一个共用字段」——<b>跨表单共用</b>，因此不出现在四类模板的字段表里；
     * {@code doc/templates.md} §2.2 的 {@code readonlyAfterSubmit} 行也显式提到「补件字段」
     * 属服务端字段级白名单内的字段。三态白名单（{@code FormWritePolicy.SUPPLEMENT_FIELDS}）已经
     * 把它限死在「待补件」态，这里只负责**不把它误判成夹带**，并按 §8 的长度口径单独校验。
     */
    public static final Set<String> SYSTEM_FIELDS = Set.of("supplement_note");

    /** 补件说明的长度下限（{@code doc/forms.md} §8：≥5 字符）。 */
    public static final int SUPPLEMENT_NOTE_MIN = 5;

    /** 补件说明的长度上限（{@code doc/forms.md} §8：≤500）。 */
    public static final int SUPPLEMENT_NOTE_MAX = 500;

    /**
     * {@code user} / {@code org} 选择器的**统一数量上限**（模板未声明 {@code rules[pickerLimit]} 时生效）。
     *
     * <p>口径来源：{@code doc/forms.md} §2 事项单字段表 {@code cc_users} 行「长度 ≤20 人」——
     * 这是全文唯一写明数量上限的人员/组织选择字段；模板可用 {@code rules[pickerLimit].max}
     * **收窄**（{@code doc/templates.md} §2.3「{@code pickerLimit}：user / org / tag 的选择数量上限」）。
     * 「未声明的取 20」属推断口径，已列入待决策（见交付说明）。
     */
    public static final int PICKER_MAX_DEFAULT = 20;

    /** 元素存在性失败的规则名（{@code details.errors[].rule}）。 */
    public static final String RULE_PICKER_VALUE = "pickerValue";

    private final FormDictService dictService;
    private final UniqueValueChecker uniqueChecker;
    private final PickerValueChecker pickerChecker;

    public FormPayloadValidator(FormDictService dictService, UniqueValueChecker uniqueChecker) {
        this(dictService, uniqueChecker, null);
    }

    public FormPayloadValidator(FormDictService dictService, UniqueValueChecker uniqueChecker,
                                PickerValueChecker pickerChecker) {
        this.dictService = dictService;
        this.uniqueChecker = uniqueChecker;
        this.pickerChecker = pickerChecker;
    }

    /** 无外部依赖的构造（单测 / 字典不可用的降级场景：{@code inDict} 规则跳过并记 WARN）。 */
    public static FormPayloadValidator offline(FormDictService dictService) {
        return new FormPayloadValidator(dictService, null, null);
    }

    // ================================================================ 入口

    /**
     * 按 schema 校验载荷。
     *
     * @param schema  schema（由**实例锁定的模板版本**解析，见 {@code FormSchemaService}）
     * @param payload 载荷（已过状态白名单；未知字段在这里被拒）
     * @param mode    校验档（{@link ValidationMode}）
     * @param today   「今天」的口径（日期下限判定；生产传 {@code LocalDate.now()}，单测可固定）
     * @return 校验报告（可能是失败报告，由调用方决定抛还是返回）
     */
    public FormValidationReport validate(FormSchema schema, Map<String, Object> payload, ValidationMode mode,
                                        LocalDate today, Object submitDate) {
        FormValidationReport.Collector collector = FormValidationReport.collector();
        Map<String, Object> values = payload == null ? Map.of() : payload;
        rejectUnknownFields(schema, values, collector);
        for (FormFieldDef field : schema.fields()) {
            validateField(schema, field, values, mode, today, submitDate, collector);
        }
        validateSystemFields(values, collector);
        return collector.build();
    }

    /**
     * 系统预留字段的校验（{@code doc/forms.md} §8：{@code supplement_note} textarea、必填、≤500、≥5 字符）。
     *
     * <p>写权限另由三态白名单限死在「待补件」（{@code FormWritePolicy.SUPPLEMENT_FIELDS}），
     * 这里只判长度口径。
     */
    public void validateSystemFields(Map<String, Object> payload, FormValidationReport.Collector collector) {
        if (payload == null || !payload.containsKey("supplement_note")) {
            return;
        }
        Object raw = payload.get("supplement_note");
        if (ConditionEvaluator.isEmpty(raw)) {
            return;
        }
        String text = String.valueOf(raw);
        if (text.trim().length() < SUPPLEMENT_NOTE_MIN) {
            collector.add("supplement_note", "补件说明", "minLength", "补件说明至少 5 个字符");
        }
        if (text.length() > SUPPLEMENT_NOTE_MAX) {
            collector.add("supplement_note", "补件说明", "maxLength", "补件说明不能超过 500 个字符");
        }
    }

    // ================================================================ 未知字段（夹带）

    /**
     * 未在 schema 内登记的字段一律拒绝（{@code doc/templates.md} §2.5「未知键」）。
     *
     * <p>为什么是**拒绝**而不是忽略：静默忽略会让「客户端以为写成功、服务端没写」这种不一致
     * 长期隐身；而静默**透传**更糟——未登记的键会进 {@code form_data.fields_json}，
     * 之后被打印、导出、数据仓抽取读到。夹带一份「未审批过的字段」正是内控红线
     * （{@code doc/prd-0.1.md} §4.3 禁止事项「修改已审批通过单据的字段值」的同源风险）。
     */
    public void rejectUnknownFields(FormSchema schema, Map<String, Object> payload,
                                    FormValidationReport.Collector collector) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        for (String key : payload.keySet()) {
            if (key == null || key.isBlank()) {
                collector.add("(空字段名)", "(空字段名)", "unknownField",
                        "载荷中存在空字段名，请求已拒绝");
                continue;
            }
            if (!schema.knows(key) && !SYSTEM_FIELDS.contains(key)) {
                collector.add(key, key, "unknownField",
                        String.format("未在表单模板中登记（%s v%d 的字段集内不存在该字段），请求已拒绝：禁止夹带未登记字段",
                                schema.templateCode(), schema.schemaVersion()));
            }
        }
    }

    // ================================================================ 逐字段

    @SuppressWarnings({"checkstyle:CyclomaticComplexity", "checkstyle:MethodLength"})
    private void validateField(FormSchema schema, FormFieldDef field, Map<String, Object> values, ValidationMode mode,
                               LocalDate today, Object submitDate, FormValidationReport.Collector collector) {
        String code = field.code();
        boolean provided = values.containsKey(code) && !ConditionEvaluator.isEmpty(values.get(code));
        Object raw = values.get(code);

        // ---------- 锁定字段（locked）：任何状态都不可编辑，只展示 defaultValue ----------
        if (field.locked() && values.containsKey(code)) {
            Object declared = field.hasDefaultValue() && field.defaultValue() != null
                    ? field.defaultValue().asText() : null;
            String actual = raw == null ? null : String.valueOf(raw);
            if (declared == null || !declared.equals(actual)) {
                collector.add(code, field.label(), "locked",
                        String.format("「%s」为模板固定值（%s），不可填写其他取值",
                                field.label(), declared == null ? "由模板决定" : declared));
                return;
            }
        }

        // ---------- 必填（无条件）—— 仅 SUBMIT 档 ----------
        if (!provided) {
            if (field.required() && mode.isSubmit()) {
                collector.add(code, field.label(), "required",
                        "请填写" + field.label());
            }
            validateConditionalRequired(field, values, mode, collector);
            return;
        }

        // ---------- 类型 ----------
        FormFieldType type = field.type();
        if (!typeMatches(type, raw)) {
            collector.add(code, field.label(), "typeMismatch",
                    String.format("「%s」的取值类型不符合字段类型 %s", field.label(),
                            type == null ? field.rawType() : type.code()));
            return;
        }

        // ---------- 短文本/长文本：maxLength + minLength ----------
        if (raw instanceof CharSequence sequence) {
            String text = sequence.toString();
            int limit = field.maxLength();
            JsonNode maxRule = field.rule("maxLength");
            if (maxRule != null && maxRule.path("value").isNumber()) {
                limit = maxRule.path("value").asInt();
            }
            if (limit > 0 && text.length() > limit) {
                String message = field.ruleMessage("maxLength");
                collector.add(code, field.label(), "maxLength",
                        message == null ? String.format("%s不能超过 %d 个字符", field.label(), limit) : message);
            }
            JsonNode minRule = field.rule("minLength");
            if (minRule != null && minRule.path("value").isNumber() && text.length() < minRule.path("value").asInt()) {
                String message = field.ruleMessage("minLength");
                collector.add(code, field.label(), "minLength",
                        message == null ? String.format("%s至少 %d 个字符", field.label(),
                                minRule.path("value").asInt()) : message);
            }
        }

        // ---------- 金额（禁浮点 + >0 + scale + 上限） ----------
        if (type == FormFieldType.AMOUNT) {
            validateAmount(field, raw, collector);
        }

        // ---------- 数字（整数/范围） ----------
        if (type == FormFieldType.NUMBER) {
            validateNumber(field, raw, collector);
        }

        // ---------- 字典 / 静态选项 ----------
        if (type != null && type.isOption()) {
            validateOptions(schema, field, raw, collector);
        }

        // ---------- 日期 / 区间 ----------
        if (type == FormFieldType.DATE && !validateDate(field, raw, values, mode, today, submitDate, collector)) {
            return;
        }
        if (type == FormFieldType.DATERANGE) {
            validateDateRange(field, raw, collector);
        }

        // ---------- 正则 ----------
        validatePattern(field, raw, collector);

        // ---------- 数量上限（user / org / tag / multiselect） ----------
        validatePickerLimit(field, raw, collector);

        // ---------- 人员 / 组织选择：去重后的元素必须在通讯录 / 组织树内 ----------
        validatePicker(field, raw, collector);

        // ---------- 附件 ----------
        if (type != null && type.isAttachment()) {
            validateFiles(field, raw, collector);
        }

        // ---------- 条件最小长度（如印鉴单 is_external=true 时 purpose ≥20） ----------
        validateConditionalMinLength(field, values, raw, collector);

        // ---------- 唯一性（如 contract_ref 必须存在） ----------
        validateUnique(field, raw, collector);
    }

    // ================================================================ 各规则

    private boolean typeMatches(FormFieldType type, Object raw) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case TEXT, TEXTAREA, TAG, DATE, SELECT -> raw instanceof CharSequence
                    || (type == FormFieldType.SELECT && raw instanceof Number);
            // user / org 是「人员 / 组织选择」：**单值与数组都接受**
            // （单值兼容既有数据与前端当前行为；数组按 doc/forms.md §2 cc_users「≤20 人」口径，
            //   去重与元素存在性见 validatePicker / validatePickerLimit）
            case USER, ORG -> raw instanceof CharSequence
                    || raw instanceof Collection<?> || raw instanceof Object[];
            case NUMBER, AMOUNT -> raw instanceof Number || raw instanceof CharSequence;
            case BOOLEAN -> ConditionEvaluator.booleanOf(raw) != null;
            case MULTISELECT, FILES, FILE -> raw instanceof Collection<?> || raw instanceof Object[]
                    || raw instanceof CharSequence;
            case DATERANGE -> raw instanceof Collection<?> || raw instanceof Object[];
        };
    }

    private void validateAmount(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        JsonNode rule = field.rule("amountRange");
        String min = rule != null && rule.hasNonNull("min") ? rule.get("min").asText() : AmountText.MIN;
        String max = rule != null && rule.hasNonNull("max") ? rule.get("max").asText() : AmountText.MAX;
        Integer scale = rule != null && rule.path("scale").isNumber() ? rule.path("scale").asInt() : AmountText.SCALE;
        AmountText.Parsed parsed = AmountText.parse(raw, min, max, scale);
        if (!parsed.ok()) {
            // 文案口径（doc/forms.md §1.3 的表 + doc/templates.md §2.3 的 message）：
            //   · 模板给了 message 且与具体原因**不同**时，两者都给 —— 因为「禁浮点」这类
            //     结构性原因必须能被定位（模板文案只说「必须大于 0 且最多两位小数」，
            //     会把「传了 JSON 浮点字面量」这个真实原因藏起来）；
            //   · 相同时只给一条，避免重复啰嗦。
            String templateMessage = field.ruleMessage("amountRange");
            String detailed = parsed.error();
            String message = templateMessage == null || templateMessage.equals(detailed)
                    ? detailed : templateMessage + "（" + detailed + "）";
            collector.add(field.code(), field.label(), "amountRange", message);
        }
    }

    private void validateNumber(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        JsonNode rule = field.rule("numberRange");
        BigDecimal value = ConditionEvaluator.decimalOf(raw);
        if (value == null) {
            collector.add(field.code(), field.label(), "numberRange",
                    String.format("「%s」必须是数字", field.label()));
            return;
        }
        if (rule == null) {
            return;
        }
        boolean integer = rule.path("integer").asBoolean(false);
        if (integer && value.stripTrailingZeros().scale() > 0) {
            collector.add(field.code(), field.label(), "numberRange",
                    String.format("「%s」必须是整数", field.label()));
        }
        if (rule.hasNonNull("min") && value.compareTo(rule.get("min").decimalValue()) < 0) {
            String message = field.ruleMessage("numberRange");
            collector.add(field.code(), field.label(), "numberRange",
                    message == null ? String.format("「%s」不能小于 %s", field.label(), rule.get("min").asText())
                            : message);
        }
        if (rule.hasNonNull("max") && value.compareTo(rule.get("max").decimalValue()) > 0) {
            String message = field.ruleMessage("numberRange");
            collector.add(field.code(), field.label(), "numberRange",
                    message == null ? String.format("「%s」不能大于 %s", field.label(), rule.get("max").asText())
                            : message);
        }
    }

    private void validateOptions(FormSchema schema, FormFieldDef field, Object raw,
                                 FormValidationReport.Collector collector) {
        List<String> submitted = strings(raw);
        if (field.type() == FormFieldType.SELECT && submitted.size() > 1) {
            collector.add(field.code(), field.label(), "typeMismatch",
                    String.format("「%s」是单选字段，只能给定一个取值", field.label()));
            return;
        }
        String dictType = field.dictType();
        if (dictType != null) {
            if (dictService == null) {
                log.warn("字典服务不可用，字段 {} 的 inDict 校验被跳过（scope={} v{}）",
                        field.code(), schema.templateCode(), schema.schemaVersion());
                return;
            }
            for (String code : submitted) {
                if (!dictService.isValidOption(dictType, code)) {
                    String message = field.ruleMessage("inDict");
                    collector.add(field.code(), field.label(), "inDict",
                            message == null ? String.format("「%s」取值非法：%s（字典 %s 的合法取值为 %s）",
                                    field.label(), code, dictType, dictService.enabledCodes(dictType)) : message);
                }
            }
            return;
        }
        if (!field.options().isEmpty()) {
            Set<String> allowed = new LinkedHashSet<>();
            for (FormFieldDef.Option option : field.options()) {
                if (option.enabled()) {
                    allowed.add(option.code());
                }
            }
            for (String code : submitted) {
                if (!allowed.contains(code)) {
                    collector.add(field.code(), field.label(), "inDict",
                            String.format("「%s」取值非法：%s（可选值为 %s）", field.label(), code,
                                    String.join(" / ", allowed)));
                }
            }
        }
    }

    private boolean validateDate(FormFieldDef field, Object raw, Map<String, Object> values, ValidationMode mode,
                                 LocalDate today, Object submitDate, FormValidationReport.Collector collector) {
        String text = raw == null ? null : String.valueOf(raw).trim();
        if (text == null || !DATE.matcher(text).matches()) {
            collector.add(field.code(), field.label(), "dateFormat",
                    String.format("「%s」日期格式不合法（应为 YYYY-MM-DD）", field.label()));
            return false;
        }
        LocalDate value;
        try {
            value = LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            collector.add(field.code(), field.label(), "dateFormat",
                    String.format("「%s」不是合法日期：%s", field.label(), text));
            return false;
        }
        for (JsonNode rule : field.rules("dateNotBefore")) {
            String bound = rule.path("value").asText("today");
            LocalDate lower = "today".equals(bound) ? (today == null ? LocalDate.now() : today)
                    : "submit_date".equals(bound) ? (submitDate instanceof LocalDate d ? d
                            : today == null ? LocalDate.now() : today)
                    : parseOrNull(bound);
            if (lower == null) {
                continue;
            }
            if (value.isBefore(lower)) {
                String message = ruleMessage(rule);
                collector.add(field.code(), field.label(), "dateNotBefore",
                        message == null ? String.format("%s不能早于今天", field.label()) : message);
            }
        }
        for (JsonNode rule : field.rules("dateNotBeforeField")) {
            String other = rule.path("field").asText(null);
            LocalDate otherValue = parseOrNull(other == null ? null : String.valueOf(values.get(other)));
            if (otherValue != null && value.isBefore(otherValue)) {
                String message = ruleMessage(rule);
                collector.add(field.code(), field.label(), "dateNotBeforeField",
                        message == null ? String.format("「%s」不能早于「%s」", field.label(), other) : message);
            }
        }
        return true;
    }

    private void validateDateRange(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        List<String> parts = strings(raw);
        if (parts.size() != 2) {
            collector.add(field.code(), field.label(), "dateRange",
                    String.format("「%s」需要 [开始, 结束] 两个日期", field.label()));
            return;
        }
        LocalDate start = parseOrNull(parts.get(0));
        LocalDate end = parseOrNull(parts.get(1));
        if (start == null || end == null) {
            collector.add(field.code(), field.label(), "dateFormat",
                    String.format("「%s」日期格式不合法（应为 YYYY-MM-DD）", field.label()));
            return;
        }
        if (end.isBefore(start)) {
            collector.add(field.code(), field.label(), "dateRange", "结束日期不能早于开始日期");
        }
    }

    private void validatePattern(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        if (!(raw instanceof CharSequence sequence)) {
            return;
        }
        for (JsonNode rule : field.rules("pattern")) {
            String regex = rule.path("value").asText(null);
            if (regex == null) {
                continue;
            }
            int flags = 0;
            String flagText = rule.path("flags").asText("");
            if (flagText.contains("i")) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            try {
                if (!Pattern.compile(regex, flags).matcher(sequence.toString()).matches()) {
                    String message = ruleMessage(rule);
                    collector.add(field.code(), field.label(), "pattern",
                            message == null ? String.format("「%s」格式不合法", field.label()) : message);
                }
            } catch (PatternSyntaxException ex) {
                log.warn("模板字段 {} 的 pattern 规则不是合法正则（{}），该规则被跳过：{}",
                        field.code(), regex, ex.getMessage());
            }
        }
    }

    /**
     * 选择数量上限（{@code doc/templates.md} §2.3 {@code rules[pickerLimit]}）。
     *
     * <p>{@code user} / {@code org} 按**去重后**的条数计（{@code doc/forms.md} §2 cc_users 行
     * 「≤20 人」+「去重」：重复选择同一人不该把用户顶到上限外）；其余类型仍按原始条数计，
     * 以免改变 {@code multiselect} 的既有口径（重复项在 multiselect 里是**独立取值**，不去重）。
     *
     * <p>模板未声明 {@code pickerLimit} 时，{@code user} / {@code org} 回落到
     * {@link #PICKER_MAX_DEFAULT}（20），其余类型不判（保持旧行为）。
     */
    private void validatePickerLimit(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        JsonNode rule = field.rule("pickerLimit");
        boolean picker = field.type() == FormFieldType.USER || field.type() == FormFieldType.ORG;
        int max;
        if (rule != null && rule.path("max").isNumber()) {
            max = rule.path("max").asInt();
        } else if (picker) {
            max = PICKER_MAX_DEFAULT;
        } else {
            return;
        }
        int size = pickerCount(field.type(), raw);
        if (size > max) {
            String message = ruleMessage(rule);
            collector.add(field.code(), field.label(), "pickerLimit",
                    message == null ? defaultPickerLimitMessage(field, max) : message);
        }
    }

    /** 超限文案（表单类型感知：user 论「人」，org 论「个」；两处都含「最多 N …」）。 */
    private static String defaultPickerLimitMessage(FormFieldDef field, int max) {
        return field.type() == FormFieldType.USER
                ? String.format("「%s」最多选择 %d 人", field.label(), max)
                : String.format("「%s」最多选择 %d 个", field.label(), max);
    }

    /**
     * {@code user} / {@code org} 的**元素存在性**判定（{@code doc/forms.md} §2 cc_users 行
     * 「通讯录内」；{@code cost_bearer} 行为组织节点）。
     *
     * <p>三条口径：
     * <ol>
     *   <li>先 trim + 去空 + **去重**（保序），与 {@link #pickerElements(Object)} 同源，
     *       因此「同一个 id 重复 21 次」既不会触发上限，也不会重复报错；</li>
     *   <li>逐个元素判存在性，**一次返回全部**不合格元素（不是只报第一个；每条都带字段码 + 序号 + 原因）；</li>
     *   <li>端口未装配（单测 / 降级）时**跳过并记 WARN**，与 {@code unique} 规则同一缺省语义。</li>
     * </ol>
     */
    private void validatePicker(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        FormFieldType type = field.type();
        if (type != FormFieldType.USER && type != FormFieldType.ORG) {
            return;
        }
        List<String> elements = pickerElements(raw);
        if (pickerChecker == null) {
            log.warn("人员/组织选择器校验未装配，字段 {}（type={}）的元素存在性校验被跳过",
                    field.code(), type.code());
            return;
        }
        boolean user = type == FormFieldType.USER;
        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            boolean exists = user ? pickerChecker.userExists(element) : pickerChecker.orgExists(element);
            if (exists) {
                continue;
            }
            collector.add(field.code(), field.label(), RULE_PICKER_VALUE, user
                    ? String.format("「%s」的第 %d 项不是通讯录内的在职人员：%s（用户 id 不存在或已离职/停用）",
                            field.label(), i + 1, element)
                    : String.format("「%s」的第 %d 项不是有效的组织节点：%s（组织 id 不存在或已停用）",
                            field.label(), i + 1, element));
        }
    }

    /**
     * 人员/组织选择器的**规范化元素清单**（trim + 去空 + 去重，保持提交顺序）。
     *
     * <p>单值形态返回单元素清单（单值不受「去重」影响），与数组形态共用同一口径；
     * 落库前的同一份规范化见 {@code FormDataService#canonicalizePickers}。
     */
    public static List<String> pickerElements(Object raw) {
        Set<String> unique = new LinkedHashSet<>();
        for (String item : strings(raw)) {
            if (!item.isEmpty()) {
                unique.add(item);
            }
        }
        return new ArrayList<>(unique);
    }

    /** 选择器的计数量：{@code user} / {@code org} 按去重后的条数，其余按原始条数。 */
    private static int pickerCount(FormFieldType type, Object raw) {
        if (type == FormFieldType.USER || type == FormFieldType.ORG) {
            return pickerElements(raw).size();
        }
        return collectionSize(raw);
    }

    private void validateConditionalRequired(FormFieldDef field, Map<String, Object> values, ValidationMode mode,
                                             FormValidationReport.Collector collector) {
        if (!mode.isSubmit()) {
            return;
        }
        for (JsonNode rule : field.rules("conditionalRequired")) {
            JsonNode when = rule.get("when");
            if (!ConditionEvaluator.evaluate(when, values)) {
                continue;
            }
            if (ConditionEvaluator.isEmpty(values.get(field.code()))) {
                String message = ruleMessage(rule);
                // 条件必填的中文文案统一以「label 为必填」结尾（与 templates.md §2.3 的 message 口径一致）
                collector.add(field.code(), field.label(), "conditionalRequired",
                        message == null ? field.label() + "为必填" : message);
            }
        }
    }

    private void validateConditionalMinLength(FormFieldDef field, Map<String, Object> values, Object raw,
                                              FormValidationReport.Collector collector) {
        for (JsonNode rule : field.rules("conditionalMinLength")) {
            JsonNode when = rule.get("when");
            if (!ConditionEvaluator.evaluate(when, values)) {
                continue;
            }
            int min = rule.path("min").asInt(0);
            String text = raw == null ? "" : String.valueOf(raw);
            if (text.length() < min) {
                String message = ruleMessage(rule);
                collector.add(field.code(), field.label(), "conditionalMinLength",
                        message == null ? String.format("%s至少 %d 个字符", field.label(), min) : message);
            }
        }
    }

    private void validateUnique(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        for (JsonNode rule : field.rules("unique")) {
            String scope = rule.path("scope").asText(null);
            String value = raw == null ? null : String.valueOf(raw).trim();
            if (scope == null || value == null || value.isEmpty()) {
                continue;
            }
            if (uniqueChecker == null) {
                log.warn("unique 校验器未装配，字段 {} 的 unique 规则（scope={}）被跳过", field.code(), scope);
                continue;
            }
            if (!uniqueChecker.exists(scope, value)) {
                String message = ruleMessage(rule);
                collector.add(field.code(), field.label(), "unique",
                        message == null ? String.format("「%s」对应的记录不存在：%s", field.label(), value) : message);
            }
        }
    }

    // ================================================================ 附件

    /**
     * 附件字段校验（{@code doc/forms.md} §1.4 全局限制 + 模板 {@code filePolicy}）。
     *
     * <p>线上形状（2b.7 落地存储前的元数据契约）：数组项可以是字符串（文件名）或对象
     * {@code {fileName|name, fileSize|size, fileExt|ext}}。本类只校验**元数据**，
     * 不接触文件实体（附件存储与鉴权下载属 2b.7）。
     */
    public void validateFiles(FormFieldDef field, Object raw, FormValidationReport.Collector collector) {
        List<Object> items = list(raw);
        JsonNode rule = field.rule("filePolicy");
        int maxSizeMb = rule != null && rule.path("maxSizeMb").isNumber()
                ? rule.path("maxSizeMb").asInt() : ATTACHMENT_MAX_SIZE_MB;
        int maxCount = rule != null && rule.path("maxCount").isNumber()
                ? rule.path("maxCount").asInt() : ATTACHMENT_MAX_PER_UPLOAD;
        int minCount = rule != null && rule.path("minCount").isNumber() ? rule.path("minCount").asInt() : 0;
        if (field.required() && minCount < 1) {
            minCount = 1;
        }
        Set<String> allow = rule != null && rule.has("allowExt")
                ? lower(rule.get("allowExt")) : ATTACHMENT_ALLOWED_EXT;
        Set<String> deny = rule != null && rule.has("denyExt")
                ? lower(rule.get("denyExt")) : ATTACHMENT_DENIED_EXT;

        if (items.size() > maxCount) {
            collector.add(field.code(), field.label(), "filePolicy",
                    String.format("「%s」单次上传不超过 %d 个", field.label(), maxCount));
        }
        if (items.size() > ATTACHMENT_MAX_PER_INSTANCE) {
            collector.add(field.code(), field.label(), "filePolicy",
                    String.format("单张单据附件总数不超过 %d 个（含补件）", ATTACHMENT_MAX_PER_INSTANCE));
        }
        if (items.size() < minCount) {
            String message = field.ruleMessage("filePolicy");
            collector.add(field.code(), field.label(), "filePolicy",
                    message == null ? String.format("请上传%s（至少 %d 个）", field.label(), minCount) : message);
        }
        for (Object item : items) {
            String ext = extensionOf(item);
            long sizeBytes = sizeOf(item);
            if (ext != null) {
                if (deny.contains(ext)) {
                    collector.add(field.code(), field.label(), "filePolicy",
                            String.format("「%s」不允许上传 %s 格式的文件（上传即拒绝）", field.label(), ext));
                    continue;
                }
                if (!allow.contains(ext)) {
                    collector.add(field.code(), field.label(), "filePolicy",
                            String.format("「%s」仅支持 %s", field.label(),
                                    String.join("/", new java.util.TreeSet<>(allow))));
                    continue;
                }
            }
            if (sizeBytes > 0 && sizeBytes > maxSizeMb * 1024L * 1024L) {
                collector.add(field.code(), field.label(), "filePolicy",
                        String.format("「%s」单个文件不超过 %dMB", field.label(), maxSizeMb));
            }
        }
    }

    // ================================================================ 工具

    /** 结构化字符串清单（单选字段按单元素处理）。 */
    public static List<String> strings(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        if (raw instanceof Object[] array) {
            for (Object item : array) {
                if (item != null) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        String text = String.valueOf(raw).trim();
        if (!text.isEmpty()) {
            result.add(text);
        }
        return result;
    }

    private static List<Object> list(Object raw) {
        List<Object> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof Collection<?> collection) {
            result.addAll(collection);
        } else if (raw instanceof Object[] array) {
            for (Object item : array) {
                result.add(item);
            }
        } else {
            result.add(raw);
        }
        return result;
    }

    private static int collectionSize(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return collection.size();
        }
        if (raw instanceof Object[] array) {
            return array.length;
        }
        return raw == null ? 0 : 1;
    }

    private static Set<String> lower(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.isNull()) {
                    result.add(item.asText().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    /** 取附件元数据的扩展名（对象取 {@code ext} / {@code fileExt}，字符串按文件名后缀）。 */
    @SuppressWarnings("unchecked")
    private static String extensionOf(Object item) {
        String name = null;
        String explicit = null;
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> entry = (Map<String, Object>) map;
            for (String key : new String[] {"fileExt", "ext", "extension"}) {
                Object value = entry.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    explicit = String.valueOf(value).trim();
                    break;
                }
            }
            for (String key : new String[] {"fileName", "name", "filename"}) {
                Object value = entry.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    name = String.valueOf(value).trim();
                    break;
                }
            }
        } else if (item instanceof CharSequence sequence) {
            name = sequence.toString().trim();
        }
        String candidate = explicit != null ? explicit : name;
        if (candidate == null) {
            return null;
        }
        int dot = candidate.lastIndexOf('.');
        String ext = dot >= 0 && dot < candidate.length() - 1
                ? candidate.substring(dot + 1) : candidate;
        ext = ext.toLowerCase(Locale.ROOT).trim();
        return ext.isEmpty() ? null : ext;
    }

    @SuppressWarnings("unchecked")
    private static long sizeOf(Object item) {
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> entry = (Map<String, Object>) map;
            for (String key : new String[] {"fileSize", "size", "sizeBytes"}) {
                Object value = entry.get(key);
                if (value instanceof Number number) {
                    return number.longValue();
                }
                if (value != null) {
                    try {
                        return Long.parseLong(String.valueOf(value).trim());
                    } catch (NumberFormatException ignored) {
                        // 元数据缺省：不做大小判定（大小校验的真正落点在 2b.7 的上传链路）
                    }
                }
            }
        }
        return 0L;
    }

    private static LocalDate parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String ruleMessage(JsonNode rule) {
        JsonNode message = rule == null ? null : rule.get("message");
        return message == null || message.isNull() ? null : message.asText();
    }
}
