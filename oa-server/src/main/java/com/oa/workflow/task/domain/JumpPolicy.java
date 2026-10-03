package com.oa.workflow.task.domain;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <b>自由跳转</b>的准入与计划（2a.5）—— 纯函数。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §6.4 REQ-FLOW-004：「自由跳转：仅有<b>明确授权</b>的节点可跳转
 *       （<b>默认关闭</b>，需管理员在流程模板中逐个开启）；跳转<b>必须填写原因</b>，
 *       并记入审计日志与审批轨迹」；</li>
 *   <li>AC-46：「默认关闭跳转的节点<b>无跳转入口</b>；管理员在模板中开启后可跳转，且必须填写原因
 *       并写入审计日志与轨迹」；</li>
 *   <li>doc/templates.md §0 T-08：V0.4 定稿「全部节点 {@code allow_jump = false}」。</li>
 * </ul>
 *
 * <h2>轨迹口径（17 值约束）</h2>
 * <p>doc/enums.md §9 的 17 个定稿值中**没有** {@code jump}，而跳转的语义就是「跳过中间节点」，
 * 因此：被跳过的节点（含当前节点与中间节点）一律落 {@code sys_thread.action = skip}
 * （理由文案里带走目标节点序号）；审计日志（{@code sys_log.action}，自由文本列）另记 {@code jump}。
 */
public final class JumpPolicy {

    private JumpPolicy() {
    }

    /**
     * 跳转计划。
     *
     * @param targetSeq  目标节点序号
     * @param skippedSeqs 被跳过的节点序号（当前节点 + 中间节点，**升序**）
     * @param reason     原因（必填）
     */
    public record Plan(int targetSeq, List<Integer> skippedSeqs, String reason) {
    }

    /**
     * 生成跳转计划（**调库前**的准入校验，非法即抛）。
     *
     * @param nodeAllowJump  快照里该节点的开关（{@code allow_jump}）
     * @param currentNodeSeq 当前节点序号
     * @param trunkSeqs      快照里的主干节点序号（升序）
     * @param targetSeq      目标节点序号
     * @param reason         原因
     */
    public static Plan plan(Boolean nodeAllowJump, Integer currentNodeSeq, List<Integer> trunkSeqs,
                            Integer targetSeq, String reason) {
        if (!Boolean.TRUE.equals(nodeAllowJump)) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "该节点未开启自由跳转（flow_node.allow_jump = false，AC-46 默认关闭），已拒绝（错误码 "
                            + ErrorCode.FLOW_ACTION_NOT_ALLOWED.getCode() + "）");
        }
        if (targetSeq == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "跳转必须指定目标节点序号（targetSeq）");
        }
        List<Integer> ordered = trunkSeqs == null ? List.of() : trunkSeqs;
        if (!ordered.contains(targetSeq)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标节点序号 " + targetSeq + " 不在本单锁定的流程版本内，可选：" + ordered);
        }
        if (currentNodeSeq == null) {
            throw new BizException(ErrorCode.CONFLICT, "单据当前没有活动节点，不能跳转");
        }
        if (targetSeq <= currentNodeSeq) {
            throw new BizException(ErrorCode.FLOW_ACTION_NOT_ALLOWED,
                    "自由跳转只能向后跳（目标 " + targetSeq + " 必须大于当前节点 " + currentNodeSeq
                            + "）；退回历史节点请使用「回退上一节点」");
        }
        String normalizedReason = ApprovalOpinionPolicy.requireReason(
                com.oa.workflow.runtime.domain.FlowAction.JUMP, reason);

        Set<Integer> skipped = new LinkedHashSet<>();
        skipped.add(currentNodeSeq);
        for (Integer seq : ordered) {
            if (seq != null && seq > currentNodeSeq && seq < targetSeq) {
                skipped.add(seq);
            }
        }
        return new Plan(targetSeq, List.copyOf(skipped), normalizedReason);
    }

    /** 跳转的审计留痕载荷（{@code sys_log.after_json}），键名与其余审计条目一致。 */
    public static String auditPayload(Plan plan, Integer currentNodeSeq) {
        if (plan == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add("\"action\":\"jump\"");
        parts.add("\"fromNodeSeq\":" + currentNodeSeq);
        parts.add("\"toNodeSeq\":" + plan.targetSeq());
        parts.add("\"skipped\":" + JsonText.write(new ArrayList<>(plan.skippedSeqs())));
        parts.add("\"reason\":" + JsonText.write(plan.reason()));
        return "{" + String.join(",", parts) + "}";
    }
}
