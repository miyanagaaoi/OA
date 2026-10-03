package com.oa.form.template.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个字段定义（{@code form_schema_json.fields[]} 一项）—— 结构契约见
 * {@code doc/templates.md} §2.2「字段项结构」。
 *
 * <h2>键位逐条对照（§2.2 表）</h2>
 * <ul>
 *   <li>{@code code}（必填，小写蛇形，**一经使用不得复用**）、{@code label}、{@code printLabel}、
 *       {@code printVisible}、{@code type}、{@code required}、{@code rules}、
 *       {@code maxLength}、{@code options}、{@code linkage}、{@code readonlyAfterSubmit}、
 *       {@code locked}；</li>
 *   <li>允许的扩展键（§2.2「本契约允许的扩展键」）：{@code defaultValue} / {@code optionsSource}
 *       / {@code placeholder} / {@code unit}；</li>
 *   <li>{@code options[]} 项结构：{@code {code,label,enabled,sortNo}}；</li>
 *   <li>{@code linkage} 条件对象：{@code {field,op,value}}，{@code op ∈ eq/ne/in/notIn/gt/gte/lt/lte/
 *       empty/notEmpty/checked}；多条件「与」用 {@code {all:[...]}}、或 {@code {any:[...]}}。</li>
 * </ul>
 *
 * <p>本类**只承载解析结果**，不含校验逻辑（校验在 {@code oa.form.template.validate}）。
 * {@code rules} 保持原始 JSON 节点，由各规则处理器按需读取 —— 这样新增规则类型不需要改本类。
 */
public final class FormFieldDef {

    /** 规则节点里的类型键（出参里与「参数键」区分开）。 */
    private static final String RULE_TYPE_KEY = "type";

    private final String code;
    private final String label;
    private final String printLabel;
    private final boolean printVisible;
    private final FormFieldType type;
    private final String rawType;
    private final boolean required;
    private final int maxLength;
    private final List<JsonNode> rules;
    private final List<Option> options;
    private final String dictType;
    private final JsonNode linkage;
    private final JsonNode defaultValue;
    private final boolean hasDefaultValue;
    private final boolean readonlyAfterSubmit;
    private final boolean locked;
    private final String placeholder;
    private final String unit;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private FormFieldDef(String code, String label, String printLabel, boolean printVisible, FormFieldType type,
                         String rawType, boolean required, int maxLength, List<JsonNode> rules, List<Option> options,
                         String dictType, JsonNode linkage, JsonNode defaultValue, boolean hasDefaultValue,
                         boolean readonlyAfterSubmit, boolean locked, String placeholder, String unit) {
        this.code = code;
        this.label = label;
        this.printLabel = printLabel;
        this.printVisible = printVisible;
        this.type = type;
        this.rawType = rawType;
        this.required = required;
        this.maxLength = maxLength;
        this.rules = Collections.unmodifiableList(rules);
        this.options = Collections.unmodifiableList(options);
        this.dictType = dictType;
        this.linkage = linkage;
        this.defaultValue = defaultValue;
        this.hasDefaultValue = hasDefaultValue;
        this.readonlyAfterSubmit = readonlyAfterSubmit;
        this.locked = locked;
        this.placeholder = placeholder;
        this.unit = unit;
    }

    /** 从 JSON 字段项构造（宽松：缺省键按 §2.2 的默认语义补齐；未知键忽略）。 */
    public static FormFieldDef from(JsonNode node) {
        String code = text(node, "code");
        String rawType = text(node, "type");
        FormFieldType type = FormFieldType.of(rawType).orElse(null);
        boolean required = node.path("required").asBoolean(false);
        int maxLength = node.path("maxLength").isNumber() ? node.path("maxLength").asInt() : 0;

        List<JsonNode> rules = new ArrayList<>();
        JsonNode rulesNode = node.get("rules");
        if (rulesNode != null && rulesNode.isArray()) {
            rulesNode.forEach(item -> {
                if (item != null && item.isObject()) {
                    rules.add(item);
                }
            });
        }

        List<Option> options = new ArrayList<>();
        JsonNode optionsNode = node.get("options");
        if (optionsNode != null && optionsNode.isArray()) {
            optionsNode.forEach(item -> {
                if (item != null && item.isObject()) {
                    options.add(new Option(text(item, "code"), text(item, "label"),
                            !item.has("enabled") || item.path("enabled").asBoolean(true),
                            item.path("sortNo").isNumber() ? item.path("sortNo").asInt() : 0));
                }
            });
        }

        String dictType = null;
        String sourceKind = null;
        JsonNode source = node.get("optionsSource");
        if (source != null && source.isObject()) {
            dictType = text(source, "dictType");
            sourceKind = text(source, "kind");
        }

        return new FormFieldDef(
                code,
                text(node, "label"),
                text(node, "printLabel") == null ? text(node, "label") : text(node, "printLabel"),
                !node.has("printVisible") || node.path("printVisible").asBoolean(true),
                type, rawType, required, maxLength, rules, options, dictType,
                node.get("linkage"), node.get("defaultValue"), node.has("defaultValue"),
                node.path("readonlyAfterSubmit").asBoolean(true),
                node.path("locked").asBoolean(false),
                text(node, "placeholder"), text(node, "unit"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String printLabel() {
        return printLabel;
    }

    public boolean printVisible() {
        return printVisible;
    }

    /** 解析后的类型；模板里写了未知类型时为 {@code null}（由校验层按「类型非法」报错）。 */
    public FormFieldType type() {
        return type;
    }

    /** 模板里的原始类型文本（错误文案用）。 */
    public String rawType() {
        return rawType;
    }

    /** 无条件必填（条件必填在 {@code rules[conditionalRequired]}）。 */
    public boolean required() {
        return required;
    }

    /** 长度上限（非文本类型为 0 = 不适用）。 */
    public int maxLength() {
        return maxLength;
    }

    /** 原始校验规则数组（顺序执行）。 */
    public List<JsonNode> rules() {
        return rules;
    }

    /** 内联静态选项。 */
    public List<Option> options() {
        return options;
    }

    /** 字典驱动选项的 {@code dictType}（{@code optionsSource.dictType}）。 */
    public String dictType() {
        return dictType;
    }

    public JsonNode linkage() {
        return linkage;
    }

    /** 模板声明的默认值（可能为 {@code null}）。 */
    public JsonNode defaultValue() {
        return defaultValue;
    }

    /** 模板是否**声明了** {@code defaultValue} 键（用于区分「默认值就是 null」与「未声明」）。 */
    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    /** 提交发起后是否只读（{@code false} 仅允许用于服务端字段级白名单内的字段）。 */
    public boolean readonlyAfterSubmit() {
        return readonlyAfterSubmit;
    }

    /** 任何状态都不可编辑（仅展示 {@code defaultValue}）—— 合同单/印鉴单固定类别用它。 */
    public boolean locked() {
        return locked;
    }

    public String placeholder() {
        return placeholder;
    }

    public String unit() {
        return unit;
    }

    /** 取某条规则（按 {@code type} 匹配，第一条命中）。 */
    public JsonNode rule(String ruleType) {
        for (JsonNode rule : rules) {
            if (ruleType.equals(text(rule, "type"))) {
                return rule;
            }
        }
        return null;
    }

    /** 按规则类型取全部规则。 */
    public List<JsonNode> rules(String ruleType) {
        List<JsonNode> matched = new ArrayList<>();
        for (JsonNode rule : rules) {
            if (ruleType.equals(text(rule, "type"))) {
                matched.add(rule);
            }
        }
        return matched;
    }

    /** {@code rules} 是否含某类型。 */
    public boolean hasRule(String ruleType) {
        return rule(ruleType) != null;
    }

    /** 规则自带的中文提示（{@code message}）；缺省返回 {@code null}（由调用方给默认文案）。 */
    public String ruleMessage(String ruleType) {
        JsonNode rule = rule(ruleType);
        return rule == null ? null : text(rule, "message");
    }

    /** 是否选项类字段（{@code select} / {@code multiselect}）。 */
    public boolean isOption() {
        return type != null && type.isOption();
    }

    /** 是否附件类字段。 */
    public boolean isAttachment() {
        return type != null && type.isAttachment();
    }

    /** 选项视图（{@code options[]} 项；对外出参与打印共用）。 */
    public record Option(String code, String label, boolean enabled, int sortNo) {
    }

    /** 字段摘要（出参用；含 {@code printLabel} / {@code printVisible} 便于打印与前端渲染）。 */
    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", code);
        view.put("label", label);
        view.put("printLabel", printLabel);
        view.put("printVisible", printVisible);
        view.put("type", type == null ? rawType : type.code());
        view.put("required", required);
        view.put("maxLength", maxLength);
        view.put("locked", locked);
        view.put("readonlyAfterSubmit", readonlyAfterSubmit);
        if (dictType != null) {
            view.put("dictType", dictType);
        }
        if (!options.isEmpty()) {
            List<Map<String, Object>> optionViews = new ArrayList<>();
            for (Option option : options) {
                Map<String, Object> optionView = new LinkedHashMap<>();
                optionView.put("code", option.code());
                optionView.put("label", option.label());
                optionView.put("enabled", option.enabled());
                optionView.put("sortNo", option.sortNo());
                optionViews.add(optionView);
            }
            view.put("options", optionViews);
        }
        if (hasDefaultValue) {
            view.put("defaultValue", defaultValue == null || defaultValue.isNull() ? null : javaValue(defaultValue));
        }
        if (placeholder != null) {
            view.put("placeholder", placeholder);
        }
        if (unit != null) {
            view.put("unit", unit);
        }
        List<String> ruleTypes = new ArrayList<>();
        for (JsonNode rule : rules) {
            ruleTypes.add(text(rule, "type"));
        }
        view.put("rules", ruleTypes);
        // 规则**参数**（2026-10-05 追加）：`rules` 只给类型名，前端拿不到 `pickerLimit.max`、
        // `filePolicy.maxCount`、`conditionalRequired.when` 这些**模板已经声明**的约束，只能按服务端
        // 默认值兜底或让用户试错。新增 `ruleDetails` 与 `rules` **下标一一对应**（`ruleDetails[i].type == rules[i]`），
        // 逐条**如实转写**模板声明的键（含 `message`），不注入任何默认值、不臆造模板未声明的键。
        view.put("ruleDetails", ruleDetailsView());
        return view;
    }

    /**
     * {@code ruleDetails[]}：与 {@link #rules()} 下标一一对应的规则明细。
     *
     * <h2>为什么是「新增键」而不是把 {@code rules} 改成对象数组</h2>
     * <p>{@code rules} 现有的形状是**字符串数组**（规则类型名），既有调用方按
     * {@code rules.includes("pickerLimit")} 的语义读取；改成对象数组等于删改既有契约。
     * 因此保持 {@code rules} 一字不动，另加 {@code ruleDetails}。
     *
     * <h2>取值口径（如实转写，不补默认）</h2>
     * <ul>
     *   <li>每条 = {@code {"type": 规则类型}} + 模板在该规则上**声明的全部其它键**（原样，含 {@code message}）；</li>
     *   <li>嵌套条件对象（如 {@code conditionalRequired.when}）与数组（如 {@code filePolicy.allowExt}）
     *       递归转成普通 JSON 值，前端可直接读；</li>
     *   <li><b>不注入服务端默认值</b>：模板没声明 {@code pickerLimit.max} 时这里就**没有** {@code max} 键
     *       （此时生效的是服务端默认 {@code FormPayloadValidator.PICKER_MAX_DEFAULT}，属另一层口径，
     *       若在这里补一个「看起来像模板声明」的值，会把默认值伪装成模板事实）；</li>
     *   <li>整数按 {@code Long} 出参，定点/大数以**字符串**出参（与 {@code amountRange}「金额禁浮点、
     *       以字符串定点数表达」的既有口径一致）。</li>
     * </ul>
     */
    private List<Map<String, Object>> ruleDetailsView() {
        List<Map<String, Object>> details = new ArrayList<>();
        for (JsonNode rule : rules) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("type", text(rule, "type"));
            rule.fields().forEachRemaining(entry -> {
                if (!RULE_TYPE_KEY.equals(entry.getKey())) {
                    detail.put(entry.getKey(), ruleValue(entry.getValue()));
                }
            });
            details.add(detail);
        }
        return details;
    }

    /** 规则参数的 JSON → 普通出参值（对象/数组递归；定点数以字符串表达）。 */
    private static Object ruleValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            // int 范围用 Integer（与 maxLength / templateVersion 等既有出参同为 JSON 数字）；
            // 超出 int 的整数按 Long 出参（本工程 JacksonConfig 把 Long 序列化成字符串，
            // 与 id 一族同口径，避免 JS 精度丢失）。
            return node.canConvertToInt() ? (Object) node.asInt() : (Object) node.asLong();
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(ruleValue(item)));
            return items;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), ruleValue(entry.getValue())));
            return map;
        }
        return node.asText();
    }

    private static Object javaValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.asLong() : node.asText();
        }
        return node.asText();
    }
}
