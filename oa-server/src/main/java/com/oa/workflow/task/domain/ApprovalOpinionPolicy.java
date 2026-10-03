package com.oa.workflow.task.domain;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.runtime.domain.FlowAction;
import java.util.List;

/**
 * 审批意见 / 动作原因的**准入校验**（2a.5）—— 纯函数。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §6.6 / REQ-FLOW-013 / AC-50：「审批人驳回必须填写意见（<b>≥5 字</b>，
 *       不允许空白驳回）」；</li>
 *   <li>doc/data-model.md §8.2：「驳回意见非空且 ≥5 字 | 应用层 + {@code flow_task.opinion} 长度约束」；</li>
 *   <li>doc/prd-0.1.md §6.3.1：「流转 / 回退 / 终止 / 跳转」均**必须填写原因**；</li>
 *   <li>doc/prd-0.1.md §6.4 REQ-FLOW-003：「加签人必须签署意见」。</li>
 * </ul>
 *
 * <h2>字数口径</h2>
 * <p>按 <b>Unicode 码点</b>计数（{@link String#codePointCount}），先 {@code trim()}：
 * 中文一个字 = 一个码点；emoji / 生僻字（代理对）也只算 1，不因 UTF-16 存储长度被多算。
 * 全角空格、制表符、换行等空白不计入（{@code trim} 只去首尾，中间空白仍计入 —— 与「不允许空白驳回」一致：
 * 纯空白串 trim 后为空，直接不足 5 字）。
 */
public final class ApprovalOpinionPolicy {

    /** 驳回意见的最小字数（REQ-FLOW-013 / AC-50）。 */
    public static final int MIN_REJECT_CHARS = 5;

    /** 意见列的库级上限（{@code flow_task.opinion VARCHAR(1000)}）。 */
    public static final int MAX_OPINION_CHARS = 1000;

    /** 原因列的库级上限（{@code flow_routing.reason} / {@code flow_task.handover_reason VARCHAR(255)}）。 */
    public static final int MAX_REASON_CHARS = 255;

    private ApprovalOpinionPolicy() {
    }

    /**
     * 驳回意见校验：**必须 ≥5 字**，否则 400（{@link ErrorCode#FLOW_OPINION_TOO_SHORT}，错误码 40009）。
     *
     * @return 归一后的意见（已 trim）
     */
    public static String requireRejectOpinion(String rawOpinion) {
        String opinion = normalize(rawOpinion);
        int chars = charCount(opinion);
        if (chars < MIN_REJECT_CHARS) {
            throw new BizException(ErrorCode.FLOW_OPINION_TOO_SHORT,
                    "驳回必须填写意见且不少于 " + MIN_REJECT_CHARS + " 个字（当前 " + chars + " 字，"
                            + "错误码 " + ErrorCode.FLOW_OPINION_TOO_SHORT.getCode() + "）")
                    .withDetail("minChars", MIN_REJECT_CHARS)
                    .withDetail("actualChars", chars);
        }
        return opinion;
    }

    /**
     * 需要填写「原因」的动作校验（流转 / 回退 / 终止 / 跳转 / 转办 / 改派 / 加签）。
     *
     * <p>必填但**不设 5 字下限**（PRD 只在驳回上写了 ≥5 字）；
     * 空白一律拒绝（{@link ErrorCode#FLOW_REASON_REQUIRED}，错误码 40010）。
     */
    public static String requireReason(FlowAction action, String rawReason) {
        String reason = normalize(rawReason);
        if (reason.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_REASON_REQUIRED,
                    "「" + (action == null ? "该动作" : action.label()) + "」必须填写原因"
                            + "（错误码 " + ErrorCode.FLOW_REASON_REQUIRED.getCode() + "）");
        }
        return truncate(reason, MAX_REASON_CHARS);
    }

    /** 可选意见（通过时可不填）：空白 → {@code null}；超长截断到库上限。 */
    public static String optionalOpinion(String rawOpinion) {
        String opinion = normalize(rawOpinion);
        return opinion.isEmpty() ? null : truncate(opinion, MAX_OPINION_CHARS);
    }

    /** 必填意见（加签人必须签署意见）：空白 → 400。 */
    public static String requireOpinion(String actionLabel, String rawOpinion) {
        String opinion = normalize(rawOpinion);
        if (opinion.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_REASON_REQUIRED,
                    "「" + (actionLabel == null ? "该动作" : actionLabel) + "」必须签署意见"
                            + "（错误码 " + ErrorCode.FLOW_REASON_REQUIRED.getCode() + "）");
        }
        return truncate(opinion, MAX_OPINION_CHARS);
    }

    /** 归一：{@code null} → 空串；去首尾空白。 */
    public static String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    /** 字数（Unicode 码点）。 */
    public static int charCount(String text) {
        String value = text == null ? "" : text;
        return value.codePointCount(0, value.length());
    }

    /** 是否达到驳回意见下限。 */
    public static boolean isRejectOpinionValid(String rawOpinion) {
        return charCount(normalize(rawOpinion)) >= MIN_REJECT_CHARS;
    }

    /** 按码点截断（防止超长写入被库拒绝）。 */
    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        int count = charCount(text);
        if (count <= maxChars) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxChars);
        return text.substring(0, end);
    }

    /** 全部校验规则的说明书（接口回显 / 文档核对）。 */
    public static List<String> rules() {
        return List.of(
                "驳回：意见必填且 ≥" + MIN_REJECT_CHARS + " 字（REQ-FLOW-013 / AC-50，错误码 "
                        + ErrorCode.FLOW_OPINION_TOO_SHORT.getCode() + "）",
                "流转 / 回退上一节点 / 回到本部门 / 自由跳转 / 终止：原因必填（PRD §6.3.1，错误码 "
                        + ErrorCode.FLOW_REASON_REQUIRED.getCode() + "）",
                "加签：加签人必须签署意见（REQ-FLOW-003，错误码 " + ErrorCode.FLOW_REASON_REQUIRED.getCode() + "）",
                "通过：意见可选，最长 " + MAX_OPINION_CHARS + " 字（库列上限，超长按码点截断）");
    }
}
