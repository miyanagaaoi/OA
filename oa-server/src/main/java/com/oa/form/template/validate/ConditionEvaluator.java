package com.oa.form.template.validate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 联动 / 条件必填的**条件求值** —— {@code doc/templates.md} §2.2「{@code linkage} 条件对象」。
 *
 * <p>原文：「{@code {field, op, value}}；{@code op} 取值 {@code eq / ne / in / notIn / gt / gte / lt / lte /
 * empty / notEmpty / checked}。多条件「与」关系用 {@code {all:[条件,...]}}，或关系用
 * {@code {any:[条件,...]}}」。
 *
 * <p>本类**只读取**形参里传入的 {@code payload}（本单当前值），不做任何 IO —— 联动是纯函数，
 * 「显示 / 必填 / 取值依赖其他字段」三件事共用同一份判定（§1.1「联动」列）。
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /**
     * 求值一个条件节点。
     *
     * @param condition {@code {field,op,value}} / {@code {all:[...]}} / {@code {any:[...]}}；
     *                  为 {@code null} 或非对象时按 {@code true}（无条件的条件恒成立）
     * @param values    当前单据字段值
     */
    public static boolean evaluate(JsonNode condition, Map<String, Object> values) {
        if (condition == null || condition.isNull()) {
            return true;
        }
        if (!condition.isObject()) {
            return true;
        }
        JsonNode all = condition.get("all");
        if (all != null && all.isArray()) {
            for (JsonNode item : all) {
                if (!evaluate(item, values)) {
                    return false;
                }
            }
            return true;
        }
        JsonNode any = condition.get("any");
        if (any != null && any.isArray()) {
            for (JsonNode item : any) {
                if (evaluate(item, values)) {
                    return true;
                }
            }
            return false;
        }
        String field = text(condition, "field");
        String op = text(condition, "op");
        if (field == null || op == null) {
            return true;
        }
        Object actual = values == null ? null : values.get(field);
        JsonNode expected = condition.get("value");
        return compare(actual, op, expected);
    }

    /** 单条比较。 */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public static boolean compare(Object actual, String op, JsonNode expected) {
        String normalizedOp = op == null ? "eq" : op.trim();
        return switch (normalizedOp) {
            case "eq" -> equal(actual, expected);
            case "ne" -> !equal(actual, expected);
            case "in" -> in(actual, expected);
            case "notIn" -> !in(actual, expected);
            case "gt" -> numeric(actual, expected) > 0;
            case "gte" -> numeric(actual, expected) >= 0;
            case "lt" -> numeric(actual, expected) < 0;
            case "lte" -> numeric(actual, expected) <= 0;
            case "empty" -> isEmpty(actual);
            case "notEmpty" -> !isEmpty(actual);
            case "checked" -> Boolean.TRUE.equals(booleanOf(actual)) == (expected == null || expected.asBoolean(true));
            default -> false;
        };
    }

    /** 与 {@code expected} 等价（容错 bool / 数字 / 文本三种表示）。 */
    public static boolean equal(Object actual, JsonNode expected) {
        if (expected == null || expected.isNull()) {
            return isEmpty(actual);
        }
        if (expected.isBoolean()) {
            Boolean actualBool = booleanOf(actual);
            return actualBool != null && actualBool == expected.asBoolean();
        }
        if (expected.isNumber()) {
            BigDecimal left = decimalOf(actual);
            return left != null && left.compareTo(expected.decimalValue()) == 0;
        }
        String right = expected.asText();
        if (actual == null) {
            return right.isEmpty();
        }
        String left = actual instanceof CharSequence seq ? seq.toString().trim() : String.valueOf(actual).trim();
        return left.equals(right);
    }

    /** 是否属于 {@code expected} 数组。 */
    public static boolean in(Object actual, JsonNode expected) {
        if (expected == null || !expected.isArray()) {
            return false;
        }
        List<Object> actuals = new ArrayList<>();
        if (actual instanceof Collection<?> collection) {
            actuals.addAll(collection);
        } else if (actual instanceof Object[] array) {
            for (Object item : array) {
                actuals.add(item);
            }
        } else {
            actuals.add(actual);
        }
        for (JsonNode item : expected) {
            for (Object candidate : actuals) {
                if (equal(candidate, item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 「空」的口径 —— {@code doc/forms.md} §1.3 必填行原文：「非空（**空字符串、全空格视为空**）」
     * （条件必填的 {@code empty} / {@code notEmpty} 复用同一定义）。
     */
    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString().trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Object[] array) {
            return array.length == 0;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /** 宽松的布尔解析（{@code true}/{@code "true"}/{@code "是"}/{@code 1} 都算真）。 */
    public static Boolean booleanOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "是".equals(text) || "y".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "否".equals(text) || "n".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 宽松的定点解析（**不**接受 double 字面量的尾差：走 {@link BigDecimal} 字符串构造）。 */
    public static BigDecimal decimalOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Double || value instanceof Float) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int numeric(Object actual, JsonNode expected) {
        BigDecimal left = decimalOf(actual);
        if (left == null || expected == null || !expected.isNumber()) {
            return Integer.MIN_VALUE;
        }
        return left.compareTo(expected.decimalValue());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
