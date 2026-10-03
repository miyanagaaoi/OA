package com.oa.form.template.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>金额禁浮点</b>的定点口径单测（2b.1 / forms.md §1.5 / §1.3 / §11.5）。
 *
 * <p>断言的核心是「JSON 浮点字面量一律拒绝」与「规范化结果恒为 scale=2 的字符串」，
 * 这两条合起来才构成「禁止经过浮点运算后再落库」的可验证形态。
 */
class AmountTextTest {

    @Test
    @DisplayName("合法输入：字符串定点数被规范化到两位小数；整数亦可")
    void acceptsCanonicalText() {
        assertThat(AmountText.parse("1250000.00").canonical()).isEqualTo("1250000.00");
        assertThat(AmountText.parse("1250000").canonical()).isEqualTo("1250000.00");
        assertThat(AmountText.parse("0.01").canonical()).isEqualTo("0.01");
        assertThat(AmountText.parse("100.1").canonical()).isEqualTo("100.10");
        assertThat(AmountText.parse(1250000).canonical()).isEqualTo("1250000.00");
        assertThat(AmountText.parse(new BigDecimal("100.12")).canonical()).isEqualTo("100.12");
        assertThat(AmountText.parse("99999999999.99").canonical()).isEqualTo("99999999999.99");
    }

    @Test
    @DisplayName("禁浮点：JSON 浮点字面量（Double/Float）直接判失败，并给出定点数提示")
    void rejectsFloatingPointLiterals() {
        AmountText.Parsed doubleValue = AmountText.parse(100.12d);
        assertThat(doubleValue.ok()).isFalse();
        assertThat(doubleValue.error()).contains("禁止使用浮点数");

        assertThat(AmountText.parse(0.1f).ok()).isFalse();
        assertThat(AmountText.isValid(100.1d)).isFalse();
        assertThat(AmountText.normalize(100.1d)).isEmpty();
    }

    @Test
    @DisplayName("非法值：空 / 0 / 负数 / 三位小数 / 非数字 / 超上限 一律拒绝")
    void rejectsInvalidValues() {
        assertThat(AmountText.parse(null).error()).isEqualTo("金额不能为空");
        assertThat(AmountText.parse("").error()).isEqualTo("金额不能为空");
        assertThat(AmountText.parse("0").ok()).isFalse();
        assertThat(AmountText.parse("0.00").ok()).isFalse();
        assertThat(AmountText.parse("-100").ok()).isFalse();
        assertThat(AmountText.parse("100.123").error()).isEqualTo("金额必须大于 0 且最多两位小数");
        assertThat(AmountText.parse("abc").ok()).isFalse();
        assertThat(AmountText.parse("1e3").ok()).isFalse();
        assertThat(AmountText.parse("100000000000.00").error()).contains("不能超过");
    }

    @Test
    @DisplayName("自定义范围（模板 amountRange 的 min/max/scale）")
    void respectsTemplateRange() {
        assertThat(AmountText.parse("50.00", "100.00", "1000.00", 2).ok()).isFalse();
        assertThat(AmountText.parse("500.00", "100.00", "1000.00", 2).ok()).isTrue();
        assertThat(AmountText.parse("1000.01", "100.00", "1000.00", 2).error()).contains("不能超过");
        assertThat(AmountText.parse("1.234", null, null, 3).canonical()).isEqualTo("1.234");
    }

    @Test
    @DisplayName("金额不参与路由的口径披露仍在")
    void routingDisclosure() {
        assertThat(AmountText.routingDisclosure()).contains("不参与流程路由");
    }
}
