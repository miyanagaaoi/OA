package com.oa.workflow.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.runtime.domain.FlowAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 驳回意见 / 动作原因的准入单测（2a.5）—— 对应 REQ-FLOW-013、AC-50、§6.3.1。
 *
 * <p>边界必须精确：4 字拒绝、5 字放行；纯空白一律拒绝；首尾空白不计入（{@code trim}）；
 * 代理对（emoji / 生僻字）按**码点**计数，不按 UTF-16 长度。
 */
class ApprovalOpinionPolicyTest {

    @Test
    @DisplayName("AC-50：4 字驳回被拒绝并提示（错误码 40009）")
    void rejectOpinionTooShort() {
        assertThatThrownBy(() -> ApprovalOpinionPolicy.requireRejectOpinion("不同意"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_OPINION_TOO_SHORT);
                    assertThat(biz.getMessage()).contains("5 个字").contains("当前 3 字").contains("40009");
                    assertThat(biz.getDetails()).containsEntry("minChars", 5).containsEntry("actualChars", 3);
                });
    }

    @Test
    @DisplayName("空白驳回被拒绝（REQ-FLOW-013「不允许空白驳回」）")
    void blankRejectOpinion() {
        for (String blank : new String[] {null, "", "   ", "\t\n"}) {
            assertThatThrownBy(() -> ApprovalOpinionPolicy.requireRejectOpinion(blank))
                    .as("空白意见必须被拒绝：%s", blank)
                    .isInstanceOf(BizException.class);
        }
        assertThat(ApprovalOpinionPolicy.isRejectOpinionValid("  资料不全  "))
                .as("trim 后只有 4 字 → 不合格")
                .isFalse();
    }

    @Test
    @DisplayName("5 字恰好放行；首尾空白不计入；返回值为 trim 后的文本")
    void rejectOpinionBoundary() {
        assertThat(ApprovalOpinionPolicy.requireRejectOpinion(" 金额与合同不一致 "))
                .isEqualTo("金额与合同不一致");
        assertThat(ApprovalOpinionPolicy.charCount("金额与合同不一致")).isEqualTo(8);
        assertThat(ApprovalOpinionPolicy.requireRejectOpinion("请补材料吧")).isEqualTo("请补材料吧");
        assertThat(ApprovalOpinionPolicy.charCount("请补材料吧")).isEqualTo(5);
        // 4 个汉字 + 首尾空白 → trim 后仍 4 字 → 拒绝
        assertThatThrownBy(() -> ApprovalOpinionPolicy.requireRejectOpinion("  资料不全  "))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("按 Unicode 码点计数：一个 emoji（代理对）算 1 字，不按 UTF-16 长度")
    void codePointCounting() {
        String emoji = "\uD83D\uDE00"; // 😀
        assertThat(emoji.length()).isEqualTo(2);
        assertThat(ApprovalOpinionPolicy.charCount(emoji)).isEqualTo(1);
        // 1 emoji + 4 汉字 = 5 码点 → 放行
        assertThat(ApprovalOpinionPolicy.requireRejectOpinion(emoji + "资料不全")).isNotBlank();
    }

    @Test
    @DisplayName("必填原因：流转/回退/终止/跳转/加签 空白即 40010；非空则 trim 后返回")
    void requireReason() {
        assertThatThrownBy(() -> ApprovalOpinionPolicy.requireReason(FlowAction.ROLLBACK, "  "))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.FLOW_REASON_REQUIRED);
                    assertThat(biz.getMessage()).contains("回退上一节点").contains("40010");
                });
        assertThat(ApprovalOpinionPolicy.requireReason(FlowAction.ROUTE, "客户要求补充说明"))
                .isEqualTo("客户要求补充说明");
        assertThatThrownBy(() -> ApprovalOpinionPolicy.requireReason(FlowAction.ROUTE, null))
                .as("null 与空白同码")
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("加签人必须签署意见（REQ-FLOW-003）；通过意见可选")
    void addSignOpinionAndOptionalOpinion() {
        assertThatThrownBy(() -> ApprovalOpinionPolicy.requireOpinion("加签", ""))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须签署意见");
        assertThat(ApprovalOpinionPolicy.requireOpinion("加签", "同意，请财务复核"))
                .isEqualTo("同意，请财务复核");
        assertThat(ApprovalOpinionPolicy.optionalOpinion("   ")).isNull();
        assertThat(ApprovalOpinionPolicy.optionalOpinion(null)).isNull();
        assertThat(ApprovalOpinionPolicy.optionalOpinion(" 同意 ")).isEqualTo("同意");
    }

    @Test
    @DisplayName("超长按码点截断到库列上限（opinion 1000 / reason 255），不写坏库")
    void truncate() {
        String longOpinion = "审".repeat(1200);
        String truncated = ApprovalOpinionPolicy.optionalOpinion(longOpinion);
        assertThat(ApprovalOpinionPolicy.charCount(truncated)).isEqualTo(ApprovalOpinionPolicy.MAX_OPINION_CHARS);

        String longReason = "因".repeat(300);
        String reason = ApprovalOpinionPolicy.requireReason(FlowAction.TERMINATE, longReason);
        assertThat(ApprovalOpinionPolicy.charCount(reason)).isEqualTo(ApprovalOpinionPolicy.MAX_REASON_CHARS);
    }

    @Test
    @DisplayName("规则说明书覆盖四条口径（接口回显用）")
    void rules() {
        assertThat(ApprovalOpinionPolicy.rules()).hasSize(4);
        assertThat(ApprovalOpinionPolicy.rules().get(0)).contains("5 字").contains("40009");
        assertThat(ApprovalOpinionPolicy.rules().get(1)).contains("40010");
    }
}
