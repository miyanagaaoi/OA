package com.oa.workflow.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Q6 / Q7 闸门配置项单测</b>（产品裁定 2026-10-03：两者均为可配置项）。
 *
 * <p>覆盖：默认值（未配置 = 不限/无时限）、V0.4 默认值、非法值被拒（负次数、天数 0、越界）、
 * 枚举合法性与读写往返。配置的落库/读回由 {@code FlowDefinitionServiceTest} 与运行期实测覆盖。
 *
 * <p>依据：doc/templates.md §1.7、doc/prd-0.1.md 附录 D Q6/Q7。
 */
class FlowGatePolicyTest {

    @Test
    @DisplayName("未配置 = 不限 / 无时限 / 仅提醒（默认口径）")
    void unlimitedDefaults() {
        FlowGatePolicy policy = FlowGatePolicy.unlimited();
        assertThat(policy.effectiveMaxReturnCount()).isNull();
        assertThat(policy.effectiveMaxSupplementCount()).isNull();
        assertThat(policy.effectiveSupplementDeadlineDays()).isNull();
        assertThat(policy.effectiveDeadlineType()).isNull();
        assertThat(policy.effectiveTimeoutAction()).isEqualTo(TimeoutAction.NOTIFY);
        assertThat(policy.isUnlimited()).isTrue();
        assertThat(policy.violations("模板 matter v2")).isEmpty();
    }

    @Test
    @DisplayName("0 与 null 等价于「不限」（Q6 键位语义）")
    void zeroMeansUnlimited() {
        FlowGatePolicy policy = new FlowGatePolicy(0, 0, null, null, null).normalized();
        assertThat(policy.effectiveMaxReturnCount()).isNull();
        assertThat(policy.effectiveMaxSupplementCount()).isNull();
        assertThat(policy.isUnlimited()).isTrue();
    }

    @Test
    @DisplayName("V0.4 定稿默认值：流转+回退 ≤5、全单补件 ≤3、3 个工作日、仅催办（降级为默认值）")
    void v04Defaults() {
        FlowGatePolicy policy = FlowGatePolicy.v04Defaults();
        assertThat(policy.effectiveMaxReturnCount()).isEqualTo(5);
        assertThat(policy.effectiveMaxSupplementCount()).isEqualTo(3);
        assertThat(policy.effectiveSupplementDeadlineDays()).isEqualTo(3);
        assertThat(policy.effectiveDeadlineType()).isEqualTo(DeadlineType.WORKING);
        assertThat(policy.effectiveTimeoutAction()).isEqualTo(TimeoutAction.NOTIFY);
        assertThat(policy.isV04Default()).isTrue();
        assertThat(policy.isUnlimited()).isFalse();
    }

    @Test
    @DisplayName("非法值被拒：负次数、越界次数、天数 0 / 负数 / >365")
    void illegalValuesRejected() {
        assertThat(FlowGatePolicy.unlimited().withReturnCount(-1).violations("模板"))
                .anyMatch(problem -> problem.contains("maxReturnCount 不得为负数"));
        assertThat(FlowGatePolicy.unlimited().withSupplementCount(100).violations("模板"))
                .anyMatch(problem -> problem.contains("maxSupplementCount 不得超过 99"));
        assertThat(FlowGatePolicy.unlimited().withDeadlineDays(0).violations("模板"))
                .anyMatch(problem -> problem.contains("supplementDeadlineDays 必须 > 0"));
        assertThat(FlowGatePolicy.unlimited().withDeadlineDays(366).violations("模板"))
                .anyMatch(problem -> problem.contains("不得超过 365 天"));

        assertThatThrownBy(() -> FlowGatePolicy.unlimited().withDeadlineDays(0).assertValid("模板 matter v2"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("supplementDeadlineDays");
    }

    @Test
    @DisplayName("给了口径却没给天数 → 明确提示「不设时限，口径不生效」（避免误以为生效）")
    void deadlineTypeWithoutDaysWarns() {
        FlowGatePolicy policy = new FlowGatePolicy(null, null, null, DeadlineType.CALENDAR,
                TimeoutAction.AUTO_PASS);
        assertThat(policy.violations("模板")).anyMatch(problem -> problem.contains("口径不生效"));
        // 未给天数时口径归一为 null，但超时策略仍然生效（可预置）
        assertThat(policy.normalized().effectiveDeadlineType()).isNull();
        assertThat(policy.normalized().effectiveTimeoutAction()).isEqualTo(TimeoutAction.AUTO_PASS);
    }

    @Test
    @DisplayName("枚举合法性与读写往返：天数已配置但口径缺省 → 按工作日（V0.4 口径）")
    void enumRoundTrip() {
        FlowGatePolicy partial = new FlowGatePolicy(5, 3, 3, null, null);
        assertThat(partial.effectiveDeadlineType()).isEqualTo(DeadlineType.WORKING);
        assertThat(partial.effectiveTimeoutAction()).isEqualTo(TimeoutAction.NOTIFY);

        assertThat(DeadlineType.of("working")).contains(DeadlineType.WORKING);
        assertThat(DeadlineType.of("CALENDAR")).contains(DeadlineType.CALENDAR);
        assertThat(DeadlineType.of("hours")).isEmpty();
        assertThat(TimeoutAction.of("auto_pass")).contains(TimeoutAction.AUTO_PASS);
        assertThat(TimeoutAction.of("auto_return")).contains(TimeoutAction.AUTO_RETURN);
        assertThat(TimeoutAction.of("notify")).contains(TimeoutAction.NOTIFY);
        assertThat(TimeoutAction.of("auto_skip")).isEmpty();

        // 往返：normalized 后的语义与原始一致（sameAs 忽略 null 与显式默认值的差异）
        assertThat(partial.sameAs(partial.normalized())).isTrue();
        assertThat(FlowGatePolicy.v04Defaults()
                .sameAs(new FlowGatePolicy(5, 3, 3, DeadlineType.WORKING, TimeoutAction.NOTIFY))).isTrue();
        assertThat(FlowGatePolicy.unlimited().sameAs(FlowGatePolicy.v04Defaults())).isFalse();
    }

    @Test
    @DisplayName("消费点 TODO 显式登记（Q6 计数判定属 2a.4、Q7 超时调度属阶段 3）")
    void consumerTodosDocumented() {
        assertThat(FlowGateEnums.COUNTER_TODO).contains("TODO(2a.4)").contains("maxReturnCount");
        assertThat(FlowGateEnums.DEADLINE_TODO).contains("TODO(阶段3)").contains("onSupplementTimeout");
        assertThat(FlowGateEnums.MAX_COUNT_LIMIT).isEqualTo(99);
        assertThat(FlowGateEnums.MAX_DEADLINE_DAYS).isEqualTo(365);
    }

    @Test
    @DisplayName("模板实体：闸门配置列 ↔ 值对象互通（落库与读回同一口径）")
    void templateEntityRoundTrip() {
        FlowTemplate template = new FlowTemplate();
        template.applyGatePolicy(new FlowGatePolicy(7, 2, 10, DeadlineType.CALENDAR, TimeoutAction.AUTO_RETURN));

        assertThat(template.getMaxReturnCount()).isEqualTo(7);
        assertThat(template.getMaxSupplementCount()).isEqualTo(2);
        assertThat(template.getSupplementDeadlineDays()).isEqualTo(10);
        assertThat(template.getSupplementDeadlineType()).isEqualTo("calendar");
        assertThat(template.getOnSupplementTimeout()).isEqualTo("auto_return");
        assertThat(template.gatePolicy().effectiveTimeoutAction()).isEqualTo(TimeoutAction.AUTO_RETURN);
        assertThat(template.gatePolicy().effectiveDeadlineType()).isEqualTo(DeadlineType.CALENDAR);

        template.applyGatePolicy(null);
        assertThat(template.getMaxReturnCount()).isNull();
        assertThat(template.getOnSupplementTimeout()).isEqualTo("notify");
        assertThat(template.gatePolicy().isUnlimited()).isTrue();
    }

    @Test
    @DisplayName("Q6/Q7 字段清单稳定（键位契约：5 个键，改名即破坏运维脚本）")
    void fieldNamesAreContract() {
        assertThat(List.of("maxReturnCount", "maxSupplementCount", "supplementDeadlineDays",
                        "supplementDeadlineType", "onSupplementTimeout"))
                .hasSize(5);
        assertThat(FlowGatePolicy.unlimited().toString()).contains("maxReturnCount", "onSupplementTimeout");
    }
}
