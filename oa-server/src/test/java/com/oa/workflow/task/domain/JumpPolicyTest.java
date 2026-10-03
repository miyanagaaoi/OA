package com.oa.workflow.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自由跳转单测（2a.5）—— 对应 REQ-FLOW-004 / AC-46 / templates.md §0 T-08。
 *
 * <p>三条硬约束：① 节点开关默认关闭（{@code allow_jump=false} 无入口）；
 * ② 只能向后跳（退回历史节点要走「回退上一节点」）；③ 原因必填。
 * 轨迹口径：16 值里没有 {@code jump}，被跳过的节点一律落 {@code skip}。
 */
class JumpPolicyTest {

    private static final List<Integer> TRUNK = List.of(1, 2, 3, 4, 5, 6, 7);

    @Test
    @DisplayName("AC-46：节点未开启跳转 → 40910（默认全部节点关闭，T-08）")
    void jumpDisabledByDefault() {
        assertThatThrownBy(() -> JumpPolicy.plan(false, 3, TRUNK, 5, "客户催办"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_ACTION_NOT_ALLOWED);
                    assertThat(biz.getMessage()).contains("allow_jump").contains("40910");
                });
        assertThatThrownBy(() -> JumpPolicy.plan(null, 3, TRUNK, 5, "客户催办"))
                .as("未配置等同关闭")
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("开启后：跳过当前节点与中间节点，计划里带目标序号与原因")
    void jumpPlan() {
        JumpPolicy.Plan plan = JumpPolicy.plan(true, 3, TRUNK, 6, "客户已确认，直升董事长");
        assertThat(plan.targetSeq()).isEqualTo(6);
        assertThat(plan.skippedSeqs()).containsExactly(3, 4, 5);
        assertThat(plan.reason()).isEqualTo("客户已确认，直升董事长");
    }

    @Test
    @DisplayName("目标必须在本单锁定的流程版本内；不存在即 400 并列出可选序号")
    void targetMustExist() {
        assertThatThrownBy(() -> JumpPolicy.plan(true, 1, List.of(1, 2), 9, "原因"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不在本单锁定的流程版本内");
    }

    @Test
    @DisplayName("只能向后跳（<= 当前节点一律拒绝，提示改用回退上一节点）")
    void onlyForward() {
        assertThatThrownBy(() -> JumpPolicy.plan(true, 4, TRUNK, 4, "原因"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能向后跳");
        assertThatThrownBy(() -> JumpPolicy.plan(true, 4, TRUNK, 2, "原因"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回退上一节点");
    }

    @Test
    @DisplayName("原因必填（40010）")
    void reasonRequired() {
        assertThatThrownBy(() -> JumpPolicy.plan(true, 1, TRUNK, 3, "  "))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_REASON_REQUIRED));
    }

    @Test
    @DisplayName("审计载荷含 action=jump / from / to / skipped / reason（sys_log 与轨迹双留痕的一半）")
    void auditPayload() {
        JumpPolicy.Plan plan = JumpPolicy.plan(true, 2, TRUNK, 5, "直升集团分管");
        String payload = JumpPolicy.auditPayload(plan, 2);
        assertThat(payload)
                .contains("\"action\":\"jump\"")
                .contains("\"fromNodeSeq\":2")
                .contains("\"toNodeSeq\":5")
                .contains("\"skipped\":[2,3,4]")
                .contains("直升集团分管");
    }

    @Test
    @DisplayName("缺少当前节点（无活动节点）时拒绝")
    void currentSeqRequired() {
        assertThatThrownBy(() -> JumpPolicy.plan(true, null, TRUNK, 5, "原因"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有活动节点");
    }
}
