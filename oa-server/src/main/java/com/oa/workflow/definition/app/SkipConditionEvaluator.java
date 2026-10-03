package com.oa.workflow.definition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点跳过条件（{@code flow_node.skip_condition}）的解析、校验与求值 —— <b>纯函数</b>。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/data-model.md §4.2：{@code skip_condition} 形如
 *       {@code {"field":"involve_cost","op":"eq","value":false}}；</li>
 *   <li>doc/templates.md §1.1 / §1.5：<b>一期唯一的「分支」</b>是事项审批单的「是否涉及费用」——
 *       仅 {@code matter} 模板的 ② {@code finance_review} 允许非空跳过条件，
 *       其余三类单据（fund / contract / seal）恒为「涉及」，{@code skip_condition} 必须是 {@code null}；</li>
 *   <li>{@code oa.workflow.definition.node-behavior.skip}：只做**单字段二元条件**，
 *       拒绝二期条件表达式（{@code and} / {@code or} / 嵌套）。</li>
 * </ul>
 *
 * <p>跳过语义（PRD 6.3）：跳过时节点实例状态 {@code skipped}，**但单据归口部门仍记为财务部**，
 * 且「不涉及费用」的场景**不产生待办**。
 */
public final class SkipConditionEvaluator {

    /** 允许的操作符（白名单；其余一律拒绝）。 */
    public static final Set<String> ALLOWED_OPS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
                    "eq", "ne", "gt", "gte", "lt", "lte", "in", "notIn")));

    /** 允许的键（其余键一律拒绝，避免把二期表达式悄悄塞进来）。 */
    private static final Set<String> ALLOWED_KEYS = Set.of("field", "op", "value");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkipConditionEvaluator() {
    }

    /** 解析结果。 */
    public record Condition(String field, String op, JsonNode value) {
    }

    // ================================================================ 解析 / 校验

    /**
     * 解析跳过条件。
     *
     * @param json {@code null} / 空串 = 无跳过条件
     * @return 条件；无跳过条件返回 {@code null}
     */
    public static Condition parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("skip_condition 不是合法 JSON：" + ex.getMessage());
        }
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("skip_condition 必须是对象，如 {\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}");
        }
        List<String> problems = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!ALLOWED_KEYS.contains(name)) {
                problems.add("不允许的键「" + name + "」（一期只支持 field/op/value 单字段条件，二期条件表达式属 2b 之后）");
            }
        });
        JsonNode field = node.get("field");
        JsonNode op = node.get("op");
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            problems.add("缺少 field（表单字段 code，如 involve_cost）");
        }
        if (op == null || !op.isTextual() || !ALLOWED_OPS.contains(op.asText())) {
            problems.add("op 必须是 " + ALLOWED_OPS + " 之一");
        }
        if (!node.has("value")) {
            problems.add("缺少 value");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("skip_condition 非法：" + String.join("；", problems));
        }
        return new Condition(field.asText(), op.asText(), node.get("value"));
    }

    /**
     * 求值。
     *
     * @param json   跳过条件 JSON
     * @param values 本次提交的表单字段值（键 = 字段 code）
     * @return {@code true} = 命中跳过条件（该节点应被跳过）
     */
    public static boolean matches(String json, Map<String, Object> values) {
        Condition condition = parse(json);
        if (condition == null) {
            return false;
        }
        Object actual = values == null ? null : values.get(condition.field());
        return compare(actual, condition.op(), condition.value());
    }

    // ================================================================ 字段存在性

    /**
     * 校验 {@code field} 是否存在于表单模板 {@code form_schema_json} 的 {@code fields[].code}。
     *
     * <p>依据 {@code oa.workflow.designer.validation}：「跳过条件字段存在」是发布前校验项之一。
     *
     * @return 问题清单（空 = 通过）
     */
    public static List<String> validateField(String formSchemaJson, String field) {
        List<String> problems = new ArrayList<>();
        if (field == null || field.isBlank()) {
            problems.add("跳过条件未指定字段");
            return problems;
        }
        Set<String> codes = fieldCodes(formSchemaJson);
        if (codes.isEmpty()) {
            problems.add("表单模板未定义任何字段（form_schema_json 为空），无法校验跳过条件字段「" + field + "」");
            return problems;
        }
        if (!codes.contains(field)) {
            problems.add("跳过条件字段「" + field + "」不在表单模板字段清单内：" + codes);
        }
        return problems;
    }

    /** {@code form_schema_json} 中的字段 code 集合（解析失败返回空集）。 */
    public static Set<String> fieldCodes(String formSchemaJson) {
        Set<String> codes = new LinkedHashSet<>();
        if (formSchemaJson == null || formSchemaJson.isBlank()) {
            return codes;
        }
        try {
            JsonNode root = MAPPER.readTree(formSchemaJson);
            JsonNode fields = root == null ? null : root.get("fields");
            if (fields != null && fields.isArray()) {
                for (JsonNode field : fields) {
                    JsonNode code = field.get("code");
                    if (code != null && code.isTextual()) {
                        codes.add(code.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            // 表单定义不可解析时按「无字段」处理，由调用方给出可读问题
        }
        return codes;
    }

    /** 供报告使用的可读呈现。 */
    public static String describe(String json) {
        Condition condition = parse(json);
        if (condition == null) {
            return null;
        }
        return condition.field() + " " + condition.op() + " " + condition.value();
    }

    // ================================================================ 内部比较

    private static boolean compare(Object actual, String op, JsonNode expected) {
        if ("in".equals(op) || "notIn".equals(op)) {
            List<JsonNode> items = new ArrayList<>();
            if (expected.isArray()) {
                expected.forEach(items::add);
            } else {
                items.add(expected);
            }
            boolean hit = items.stream().anyMatch(item -> scalarEquals(actual, item));
            return "in".equals(op) == hit;
        }
        if ("eq".equals(op)) {
            return scalarEquals(actual, expected);
        }
        if ("ne".equals(op)) {
            return !scalarEquals(actual, expected);
        }
        BigDecimal left = toNumber(actual);
        BigDecimal right = toNumber(expected);
        if (left == null || right == null) {
            return false;
        }
        int cmp = left.compareTo(right);
        return switch (op) {
            case "gt" -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "lte" -> cmp <= 0;
            default -> false;
        };
    }

    private static boolean scalarEquals(Object actual, JsonNode expected) {
        if (expected == null || expected.isNull()) {
            return actual == null;
        }
        if (expected.isBoolean()) {
            Boolean left = toBoolean(actual);
            return left != null && left == expected.asBoolean();
        }
        if (expected.isNumber()) {
            BigDecimal left = toNumber(actual);
            return left != null && left.compareTo(expected.decimalValue()) == 0;
        }
        if (actual == null) {
            return false;
        }
        return String.valueOf(actual).equals(expected.asText());
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static BigDecimal toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        JsonNode node = value instanceof JsonNode json ? json : null;
        if (node != null && node.isNumber()) {
            return node.decimalValue();
        }
        String text = node != null ? node.asText() : String.valueOf(value);
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
