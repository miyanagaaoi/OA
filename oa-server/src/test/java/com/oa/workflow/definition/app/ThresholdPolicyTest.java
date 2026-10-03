package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 会签阈值策略单测（templates.md §0 T-07 / §1.0；prd §5.4；data-model.md §4.2）。
 *
 * <p>三条硬口径：
 * <ol>
 *   <li><b>绝对人数优先</b>：同时给出绝对人数与百分比时以绝对人数为准；</li>
 *   <li><b>百分比向上取整</b>：66% × 3 人 = 1.98 → <b>2</b> 人；</li>
 *   <li>{@code NULL} = <b>过半</b>：向下取整(候选人数/2)+1。</li>
 * </ol>
 */
class ThresholdPolicyTest {

    @Test
    @DisplayName("parse：绝对人数与百分比的合法写法（1~99 / 1%~100%）")
    void parseAcceptedForms() {
        assertThat(ThresholdPolicy.parse("2").absolute()).isEqualTo(2);
        assertThat(ThresholdPolicy.parse("2").percent()).isNull();
        assertThat(ThresholdPolicy.parse("66%").percent()).isEqualTo(66);
        assertThat(ThresholdPolicy.parse("66%").absolute()).isNull();
        assertThat(ThresholdPolicy.parse(" 3 ").absolute()).isEqualTo(3);
        assertThat(ThresholdPolicy.parse(null).isEmpty()).isTrue();
        assertThat(ThresholdPolicy.parse("  ").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("parse：0 / 负数 / 越界 / 非法格式一律拒绝（不能出现「0 人也算通过」）")
    void parseRejectsIllegal() {
        for (String illegal : new String[]{"0", "0%", "100", "101%", "-1", "half", "2.5", "2 人", "50 %"}) {
            assertThatThrownBy(() -> ThresholdPolicy.parse(illegal))
                    .as("「%s」应被拒绝", illegal)
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("会签");
        }
    }

    @Test
    @DisplayName("compose：绝对人数优先（两者同时给出时写绝对人数，并留痕 absoluteWins）")
    void composePrefersAbsolute() {
        ThresholdPolicy.Composed both = ThresholdPolicy.compose(2, 66);
        assertThat(both.value()).isEqualTo("2");
        assertThat(both.absoluteWins()).isTrue();

        ThresholdPolicy.Composed percentOnly = ThresholdPolicy.compose(null, 50);
        assertThat(percentOnly.value()).isEqualTo("50%");
        assertThat(percentOnly.absoluteWins()).isFalse();

        assertThat(ThresholdPolicy.compose(null, null).value()).isNull();
    }

    @Test
    @DisplayName("resolve：或签恒需 1 人（阈值不参与判定）；⑤ 会签按配置；依次审批同会签")
    void resolveModes() {
        assertThat(ThresholdPolicy.resolve(DecisionMode.ANY, "3", 5).requiredApprovals()).isEqualTo(1);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ANY, "3", 5).basis()).isEqualTo("any");
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "3", 5).requiredApprovals()).isEqualTo(3);
        assertThat(ThresholdPolicy.resolve(DecisionMode.SEQUENCE, "2", 5).requiredApprovals()).isEqualTo(2);
    }

    @Test
    @DisplayName("resolve：百分比向上取整（66% × 3 人 = 2 人；50% × 3 人 = 2 人；50% × 1 人 = 1 人）")
    void resolvePercentCeils() {
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "66%", 3).requiredApprovals()).isEqualTo(2);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "50%", 3).requiredApprovals()).isEqualTo(2);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "50%", 1).requiredApprovals()).isEqualTo(1);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "34%", 3).requiredApprovals()).isEqualTo(2);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "34%", 3).basis()).isEqualTo("percent");
    }

    @Test
    @DisplayName("resolve：NULL = 过半（向下取整(候选人数/2)+1）：3 人→2、4 人→3、1 人→1")
    void resolveMajority() {
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, null, 3).requiredApprovals()).isEqualTo(2);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, null, 4).requiredApprovals()).isEqualTo(3);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, null, 1).requiredApprovals()).isEqualTo(1);
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, null, 3).basis()).isEqualTo("majority");
    }

    @Test
    @DisplayName("resolve：阈值大于候选人数 → satisfiable=false（给出「该节点无法通过」的可读信号）")
    void resolveSatisfiable() {
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "3", 2).satisfiable()).isFalse();
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "3", 3).satisfiable()).isTrue();
        assertThat(ThresholdPolicy.resolve(DecisionMode.ANY, "3", 1).satisfiable()).isTrue();
        assertThat(ThresholdPolicy.resolve(DecisionMode.ALL, "2", 0).satisfiable()).isTrue();
    }

    @Test
    @DisplayName("violations：仅会签参与阈值判定；归档登记节点不适用阈值")
    void violations() {
        assertThat(ThresholdPolicy.violations("节点 2", DecisionMode.ALL, "2")).isEmpty();
        assertThat(ThresholdPolicy.violations("节点 2", DecisionMode.ALL, null)).isEmpty();
        assertThat(ThresholdPolicy.violations("节点 5", DecisionMode.ANY, "2"))
                .anyMatch(problem -> problem.contains("仅在「会签（all）」下参与判定"));
        assertThat(ThresholdPolicy.violations("节点 7", null, "2"))
                .anyMatch(problem -> problem.contains("归档登记节点"));
        assertThat(ThresholdPolicy.violations("节点 5", DecisionMode.ALL, "abc"))
                .anyMatch(problem -> problem.contains("只支持"));
    }

    @Test
    @DisplayName("isBlank：null / 空串 / 字面量 \"null\" 都视为未配置")
    void blankDetection() {
        assertThat(ThresholdPolicy.isBlank(null)).isTrue();
        assertThat(ThresholdPolicy.isBlank(" ")).isTrue();
        assertThat(ThresholdPolicy.isBlank("null")).isTrue();
        assertThat(ThresholdPolicy.isBlank("2")).isFalse();
    }
}
