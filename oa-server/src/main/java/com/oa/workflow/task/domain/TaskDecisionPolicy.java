package com.oa.workflow.task.domain;

import com.oa.workflow.definition.app.ThresholdPolicy;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>节点决议判定</b>（2a.5）—— 或签 / 会签 / 依次 三种模式的**纯函数**唯一实现。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4「节点决议模式」：
 *       <ul>
 *         <li><b>或签</b>（默认）：候选人中任意一人通过即节点通过，其余人的任务自动关闭；</li>
 *         <li><b>会签</b>：同意人数达到配置的通过阈值（百分比或绝对人数）后节点通过；任一人驳回则节点驳回；</li>
 *         <li><b>依次审批</b>：候选人按配置顺序串行审批，全部通过才通过；任一人驳回则节点驳回。</li>
 *       </ul></li>
 *   <li>doc/prd-0.1.md §5.4「配置约束」：阈值支持百分比与绝对人数，**绝对人数优先**（判定细节复用
 *       {@link ThresholdPolicy#resolve}，本类不重复实现取整规则）；</li>
 *   <li>AC-13：会签 3 人中 2 人同意通过 → 第 1 人同意后节点仍在进行中，第 2 人同意后节点立即通过，
 *       第 3 人待办变为「已自动关闭」；</li>
 *   <li>AC-14 / REQ-FLOW-015：同一会签节点第 1 人驳回 → 节点立即驳回；</li>
 *   <li>AC-51 / REQ-FLOW-016：任一协同部门驳回 → 单据驳回（协同组的决议同样走本类）。</li>
 * </ul>
 *
 * <h2>输入口径（调用方必须遵守）</h2>
 * <ol>
 *   <li>{@code candidates} 是**本节点实例的候选人顺序**（{@code flow_node_instance.approver_ids_json} 的顺序，
 *       即快照 {@code nodes[].approvers} 的顺序）—— 依次审批依赖该顺序，调用方不得重排；</li>
 *   <li>{@code statusByCandidate} 只放**主任务**（{@code flow_task.add_sign_type IS NULL}）的状态：
 *       加签任务不参与阈值计数（加签人对结果负责，但不代替被加签人的同意票）；</li>
 *   <li>同一人只出现一次（发起时已去重，prd §5.4）。</li>
 * </ol>
 */
public final class TaskDecisionPolicy {

    private TaskDecisionPolicy() {
    }

    /** 决议结果。 */
    public enum Outcome {
        /** 尚未达成决议（继续等待）。 */
        PENDING,
        /** 节点通过。 */
        APPROVED,
        /** 节点驳回。 */
        REJECTED
    }

    /**
     * 一次判定的结果。
     *
     * @param outcome     结论
     * @param mode        生效的决议模式（{@code null} 输入按或签）
     * @param candidates  候选人数
     * @param approved    已同意人数（不含加签任务）
     * @param required    通过所需同意人数
     * @param basis       阈值依据（{@code any} / {@code absolute} / {@code percent} / {@code majority} / {@code sequence}）
     * @param nextApprover 依次审批时的下一位处理人（其余模式为 {@code null}）
     * @param description 可读说明（写进轨迹/出参）
     */
    public record Decision(
            Outcome outcome,
            DecisionMode mode,
            int candidates,
            int approved,
            int required,
            String basis,
            Long nextApprover,
            String description
    ) {

        /** 是否已达成决议。 */
        public boolean decided() {
            return outcome != Outcome.PENDING;
        }
    }

    /**
     * 判定一个节点实例的决议结果。
     *
     * @param mode              决议模式（{@code null} = 或签；⑦ 登记节点不适用决议，调用方不要调用本方法）
     * @param passThreshold     阈值字面量（{@code null} = 过半；仅会签参与）
     * @param candidates        候选人（**有序**，与快照一致）
     * @param statusByCandidate 候选人 → 其主任务状态（缺省视为未处理）
     */
    public static Decision evaluate(DecisionMode mode, String passThreshold, List<Long> candidates,
                                    Map<Long, TaskStatus> statusByCandidate) {
        List<Long> ordered = candidates == null ? List.of() : candidates;
        Map<Long, TaskStatus> statuses = statusByCandidate == null ? Map.of() : statusByCandidate;
        DecisionMode effective = mode == null ? DecisionMode.ANY : mode;
        int candidateCount = ordered.size();
        if (candidateCount == 0) {
            throw new IllegalArgumentException("节点候选人集合为空，禁止发起/决议（prd §5.4 配置约束）");
        }

        int approved = 0;
        boolean rejected = false;
        for (Long candidate : ordered) {
            TaskStatus status = statuses.get(candidate);
            if (status == TaskStatus.REJECTED) {
                rejected = true;
            } else if (status == TaskStatus.AGREED) {
                approved++;
            }
        }

        if (rejected) {
            // 或签 / 会签 / 依次 一律「任一人驳回 → 节点驳回」（prd §5.4；REQ-FLOW-015 / REQ-FLOW-016）
            return new Decision(Outcome.REJECTED, effective, candidateCount, approved, required(effective,
                    passThreshold, candidateCount), basis(effective, passThreshold, candidateCount), null,
                    "任一候选人驳回 → 节点立即驳回（会签/协同同口径）");
        }

        if (effective == DecisionMode.SEQUENCE) {
            int requiredAll = candidateCount;
            if (approved >= requiredAll) {
                return new Decision(Outcome.APPROVED, effective, candidateCount, approved, requiredAll,
                        "sequence", null, "依次审批：全部 " + requiredAll + " 位候选人已通过");
            }
            Long next = nextSequentialApprover(ordered, statuses);
            return new Decision(Outcome.PENDING, effective, candidateCount, approved, requiredAll,
                    "sequence", next, "依次审批：已通过 " + approved + "/" + requiredAll
                    + (next == null ? "（等待下一位任务生成）" : "，等待 " + next + " 处理"));
        }

        ThresholdPolicy.Threshold threshold = ThresholdPolicy.resolve(effective, passThreshold, candidateCount);
        int required = threshold.requiredApprovals();
        boolean passed = approved >= required;
        String description = effective == DecisionMode.ANY
                ? "或签：" + approved + " 人同意（任一人即通过，实际需要 " + required + " 人）"
                : "会签：" + approved + "/" + required + " 人同意（依据 " + threshold.basis()
                        + "，候选人 " + candidateCount + " 人）";
        return new Decision(passed ? Outcome.APPROVED : Outcome.PENDING, effective, candidateCount, approved,
                required, threshold.basis(), null, description);
    }

    /**
     * 决议判定等价写法：直接从**主任务状态列表**（与候选人同序）判定。
     *
     * <p>顺序敏感（依次审批）：{@code statuses} 的第 i 项对应 {@code candidates} 的第 i 项。
     */
    public static Decision evaluateOrdered(DecisionMode mode, String passThreshold, List<Long> candidates,
                                           List<TaskStatus> statuses) {
        Map<Long, TaskStatus> map = new LinkedHashMap<>();
        List<Long> ordered = candidates == null ? List.of() : candidates;
        List<TaskStatus> list = statuses == null ? List.of() : statuses;
        for (int i = 0; i < ordered.size(); i++) {
            map.put(ordered.get(i), i < list.size() ? list.get(i) : null);
        }
        return evaluate(mode, passThreshold, ordered, map);
    }

    /**
     * 依次审批的**下一位**处理人：候选人顺序里第一个尚未决议（无任务或任务未处理）的人。
     *
     * @return 下一位 user_id；全部已决议返回 {@code null}
     */
    public static Long nextSequentialApprover(List<Long> candidates, Map<Long, TaskStatus> statusByCandidate) {
        List<Long> ordered = candidates == null ? List.of() : candidates;
        Map<Long, TaskStatus> statuses = statusByCandidate == null ? Map.of() : statusByCandidate;
        for (Long candidate : ordered) {
            if (statuses.get(candidate) == null) {
                return candidate;
            }
        }
        return null;
    }

    /** 依次审批：已决议（同意/拒绝）之后仍需继续的候选人。 */
    public static List<Long> remainingSequential(List<Long> candidates, Map<Long, TaskStatus> statusByCandidate) {
        List<Long> remaining = new ArrayList<>();
        List<Long> ordered = candidates == null ? List.of() : candidates;
        Map<Long, TaskStatus> statuses = statusByCandidate == null ? Map.of() : statusByCandidate;
        for (Long candidate : ordered) {
            if (statuses.get(candidate) == null) {
                remaining.add(candidate);
            }
        }
        return remaining;
    }

    // ================================================================ 内部

    private static int required(DecisionMode mode, String passThreshold, int candidateCount) {
        return ThresholdPolicy.resolve(mode == null ? DecisionMode.ANY : mode, passThreshold, candidateCount)
                .requiredApprovals();
    }

    private static String basis(DecisionMode mode, String passThreshold, int candidateCount) {
        return ThresholdPolicy.resolve(mode == null ? DecisionMode.ANY : mode, passThreshold, candidateCount).basis();
    }
}
