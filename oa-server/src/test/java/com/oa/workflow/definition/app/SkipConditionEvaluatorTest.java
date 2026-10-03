package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 节点跳过条件单测（templates.md §1.1/§1.5；prd §6.1；oa.workflow.definition.node-behavior.skip）。
 *
 * <p>一期唯一的「分支」是事项审批单的「是否涉及费用」；因此：
 * <ul>
 *   <li>只支持单字段条件（{@code field/op/value}）；带 {@code and}/{@code or}/嵌套一律拒绝（二期条件路由）；</li>
 *   <li>{@code field} 必须存在于 {@code form_schema_json.fields[].code}；</li>
 *   <li>操作符白名单 {@code eq/ne/gt/gte/lt/lte/in/notIn}。</li>
 * </ul>
 */
class SkipConditionEvaluatorTest {

    private static final String MATTER_SCHEMA = """
            {"form_type":"matter","fields":[
              {"code":"title","type":"text"},
              {"code":"involve_cost","type":"boolean"},
              {"code":"amount","type":"amount"}
            ]}""";

    @Test
    @DisplayName("parse：合法条件解析出 field/op/value；空输入 = 无跳过条件")
    void parseBasics() {
        SkipConditionEvaluator.Condition condition = SkipConditionEvaluator.parse(
                "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}");
        assertThat(condition.field()).isEqualTo("involve_cost");
        assertThat(condition.op()).isEqualTo("eq");
        assertThat(condition.value().asBoolean()).isFalse();

        assertThat(SkipConditionEvaluator.parse(null)).isNull();
        assertThat(SkipConditionEvaluator.parse("  ")).isNull();
        assertThat(SkipConditionEvaluator.parse("null")).isNull();
    }

    @Test
    @DisplayName("parse：拒绝二期条件表达式（多余键 / 非对象 / 非法操作符）")
    void parseRejectsPhaseTwoExpressions() {
        assertThatThrownBy(() -> SkipConditionEvaluator.parse(
                "{\"and\":[{\"field\":\"a\",\"op\":\"eq\",\"value\":1}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许的键");

        assertThatThrownBy(() -> SkipConditionEvaluator.parse("[1,2,3]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是对象");

        assertThatThrownBy(() -> SkipConditionEvaluator.parse(
                "{\"field\":\"amount\",\"op\":\"matches\",\"value\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("op 必须是");

        assertThatThrownBy(() -> SkipConditionEvaluator.parse("{\"op\":\"eq\",\"value\":true}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 field");
    }

    @Test
    @DisplayName("matches：布尔/数字/字符串三类比较，以及 in / notIn")
    void matches() {
        String notInvolved = "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}";
        assertThat(SkipConditionEvaluator.matches(notInvolved, Map.of("involve_cost", false))).isTrue();
        assertThat(SkipConditionEvaluator.matches(notInvolved, Map.of("involve_cost", "false"))).isTrue();
        assertThat(SkipConditionEvaluator.matches(notInvolved, Map.of("involve_cost", true))).isFalse();
        assertThat(SkipConditionEvaluator.matches(notInvolved, Map.of())).isFalse();
        assertThat(SkipConditionEvaluator.matches(null, Map.of("involve_cost", false))).isFalse();

        assertThat(SkipConditionEvaluator.matches("{\"field\":\"amount\",\"op\":\"gte\",\"value\":100}",
                Map.of("amount", "100.00"))).isTrue();
        assertThat(SkipConditionEvaluator.matches("{\"field\":\"amount\",\"op\":\"lt\",\"value\":100}",
                Map.of("amount", 99.99))).isTrue();

        String inList = "{\"field\":\"category\",\"op\":\"in\",\"value\":[\"economy\",\"hr\"]}";
        assertThat(SkipConditionEvaluator.matches(inList, Map.of("category", "hr"))).isTrue();
        assertThat(SkipConditionEvaluator.matches(inList, Map.of("category", "admin"))).isFalse();
        String notInList = "{\"field\":\"category\",\"op\":\"notIn\",\"value\":[\"economy\"]}";
        assertThat(SkipConditionEvaluator.matches(notInList, Map.of("category", "admin"))).isTrue();
    }

    @Test
    @DisplayName("validateField：字段必须存在于表单模板的 fields[].code（发布前校验项）")
    void validateField() {
        assertThat(SkipConditionEvaluator.validateField(MATTER_SCHEMA, "involve_cost")).isEmpty();
        assertThat(SkipConditionEvaluator.validateField(MATTER_SCHEMA, "no_such_field"))
                .anyMatch(problem -> problem.contains("不在表单模板字段清单内"));
        assertThat(SkipConditionEvaluator.fieldCodes(MATTER_SCHEMA))
                .containsExactlyInAnyOrder("title", "involve_cost", "amount");
        assertThat(SkipConditionEvaluator.fieldCodes(null)).isEmpty();
        assertThat(SkipConditionEvaluator.fieldCodes("not-json")).isEmpty();
    }

    @Test
    @DisplayName("describe：给出可读呈现（写进快照 evidence）")
    void describe() {
        assertThat(SkipConditionEvaluator.describe(
                "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}"))
                .isEqualTo("involve_cost eq false");
        assertThat(SkipConditionEvaluator.describe(null)).isNull();
    }

    @Test
    @DisplayName("操作符白名单恰为 8 个（其余一律拒绝，防止把二期表达式塞进来）")
    void operatorWhitelist() {
        assertThat(SkipConditionEvaluator.ALLOWED_OPS)
                .containsExactlyInAnyOrderElementsOf(List.of("eq", "ne", "gt", "gte", "lt", "lte", "in", "notIn"));
    }
}
