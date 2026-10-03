package com.oa.form.template.validate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <b>金额禁浮点</b>的唯一定点口径（{@code doc/forms.md} §1.5 全局规则 + §11.5「金额字段禁止浮点」）。
 *
 * <h2>规则原文与落点</h2>
 * <ul>
 *   <li>{@code doc/forms.md} §1.5「存储 | 以 {@code DECIMAL(18,2)} 存储，**不使用浮点数**」；</li>
 *   <li>{@code doc/forms.md} §1.3「金额 | &gt; 0，最多两位小数，≤ 99,999,999,999.99 |
 *       「金额必须大于 0 且最多两位小数」」；</li>
 *   <li>{@code doc/forms.md} §11.5「金额字段禁止浮点：服务端只接受字符串或定点数，
 *       禁止经过浮点运算后再落库」；</li>
 *   <li>{@code doc/data-model.md} §4.4 示例：{@code "amount": "1250000.00"} —— **字符串**形式；</li>
 *   <li>{@code doc/templates.md} §2.3「{@code amountRange}：金额以**字符串**形式的定点数表达，禁止浮点；
 *       {@code scale = 2}」。</li>
 * </ul>
 *
 * <h2>为什么必须拒绝 JSON 浮点字面量</h2>
 * <p>请求体里写 {@code "amount": 1250000.1} 时，Jackson 默认把它反序列化成 {@code Double}。
 * 一旦这条链路里出现过 {@code double}，就再也无法证明「没经过浮点运算」——
 * 后续任何一次相加都可能引入 0.30000000000000004 这类尾差，而 {@code DECIMAL(18,2)} 落库会静默截断。
 * 因此本类对 {@code Double} / {@code Float} 输入**直接判失败**，要求改用字符串
 * （{@code "1250000.10"}）或整数（{@code 1250000}）。
 */
public final class AmountText {

    /** 上限：{@code DECIMAL(18,2)} 且 ≤ 99,999,999,999.99（forms.md §1.3）。 */
    public static final String MAX = "99999999999.99";

    /** 下限（严格大于 0，故最小可填值即 0.01）。 */
    public static final String MIN = "0.01";

    /** 小数位上限（forms.md §1.3「最多两位小数」）。 */
    public static final int SCALE = 2;

    /** 允许的字符串形状：可选负号被刻意排除（金额必须 &gt; 0）。 */
    private static final Pattern NUMERIC = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");

    private AmountText() {
    }

    /**
     * 定点解析结果。
     *
     * @param canonical 规范化后的字符串（{@code scale = 2}，如 {@code "1250000.00"}）
     * @param value     {@link BigDecimal} 定点值（**全程不经 double/float**）
     * @param error     失败原因（成功时为 {@code null}）
     */
    public record Parsed(String canonical, BigDecimal value, String error) {

        public boolean ok() {
            return error == null;
        }

        public static Parsed fail(String error) {
            return new Parsed(null, null, error);
        }

        public static Parsed of(BigDecimal value, int scale) {
            return new Parsed(value.setScale(scale, RoundingMode.UNNECESSARY).toPlainString(), value, null);
        }

        /** 默认两位小数的规范化。 */
        public static Parsed of(BigDecimal value) {
            return of(value, SCALE);
        }
    }

    /**
     * 解析并校验金额（默认范围 = forms.md §1.3 的全局口径：{@code > 0} 且
     * {@code ≤ 99,999,999,999.99} 且 {@code scale ≤ 2}）。
     *
     * @param raw 原始值（字符串 / 整数 / {@link BigDecimal} 均可；{@code Double}/{@code Float} 判失败）
     */
    public static Parsed parse(Object raw) {
        return parse(raw, MIN, MAX, SCALE);
    }

    /**
     * 解析并校验金额（自定义范围，对应模板里的 {@code rules[type=amountRange]}）。
     *
     * @param min   下界（含；{@code null} 表示不限）
     * @param max   上界（含；{@code null} 表示不限）
     * @param scale 小数位上限（{@code null} 表示默认 2）
     */
    public static Parsed parse(Object raw, String min, String max, Integer scale) {
        int allowedScale = scale == null || scale < 0 ? SCALE : scale;
        if (raw == null) {
            return Parsed.fail("金额不能为空");
        }
        String text;
        if (raw instanceof Double || raw instanceof Float) {
            return Parsed.fail("金额禁止使用浮点数提交（服务端只接受字符串形式的定点数，如 \"1250000.00\"）");
        }
        if (raw instanceof BigDecimal decimal) {
            text = decimal.toPlainString();
        } else if (raw instanceof Number number) {
            text = number.toString();
        } else if (raw instanceof CharSequence sequence) {
            text = sequence.toString().trim();
        } else {
            return Parsed.fail("金额格式不合法（应为字符串形式的定点数）");
        }
        if (text.isEmpty()) {
            return Parsed.fail("金额不能为空");
        }
        if (!NUMERIC.matcher(text).matches()) {
            return Parsed.fail("金额必须大于 0 且最多两位小数");
        }
        int dot = text.indexOf('.');
        if (dot >= 0 && text.length() - dot - 1 > allowedScale) {
            return Parsed.fail("金额必须大于 0 且最多两位小数");
        }
        BigDecimal value;
        try {
            // 刻意用字符串构造：绝不经 Double.parseDouble / new BigDecimal(double)
            value = new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return Parsed.fail("金额必须大于 0 且最多两位小数");
        }
        if (value.signum() <= 0) {
            return Parsed.fail("金额必须大于 0 且最多两位小数");
        }
        if (min != null) {
            BigDecimal lower = new BigDecimal(min);
            if (value.compareTo(lower) < 0) {
                return Parsed.fail("金额必须大于 0 且最多两位小数");
            }
        }
        if (max != null) {
            BigDecimal upper = new BigDecimal(max);
            if (value.compareTo(upper) > 0) {
                return Parsed.fail(String.format("金额不能超过 %s", max));
            }
        }
        try {
            return Parsed.of(value, allowedScale);
        } catch (ArithmeticException ex) {
            return Parsed.fail("金额必须大于 0 且最多两位小数");
        }
    }

    /** 便捷：金额是否合法（不抛异常）。 */
    public static boolean isValid(Object raw) {
        return parse(raw).ok();
    }

    /** 取规范化字符串（失败返回 {@link Optional#empty()}）。 */
    public static Optional<String> normalize(Object raw) {
        Parsed parsed = parse(raw);
        return parsed.ok() ? Optional.of(parsed.canonical()) : Optional.empty();
    }

    /** 上下文四则运算口径说明（金额不参与路由 —— forms.md §1.5「路由 | 金额不参与流程路由」）。 */
    public static String routingDisclosure() {
        return "金额不参与流程路由（doc/forms.md §1.5 / doc/prd-0.1.md §6.1）";
    }
}
