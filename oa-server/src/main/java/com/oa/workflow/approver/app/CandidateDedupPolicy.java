package com.oa.workflow.approver.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 候选人去重与合并策略 —— <b>纯函数</b>。
 *
 * <h2>文档写了什么（逐条）</h2>
 * <ol>
 *   <li>doc/prd-0.1.md §5.4「配置约束」：<b>「同一人在同一节点出现多次时自动去重」</b> —— 语义明确，
 *       因此**节点内去重恒为开启**（{@link #dedupWithinNode}），不可配置关闭；
 *       触发场景见 {@code oa.workflow.approver.dedup}：「一人多岗、多组织挂职」；</li>
 *   <li>doc/prd-0.1.md §5.4 + doc/enums.md §3 共同约束第 4 条：<b>「同一人同时是多个串行节点的审批人时，
 *       默认逐节点分别审批（不做连续节点自动合并），如需合并由流程设计器显式配置」</b>
 *       —— 默认**不合并**是明确口径；「由流程设计器显式配置」这一半**文档没写清落点**：
 *       {@code flow_template} / {@code flow_node} 都没有承载该开关的列（见 doc/data-model.md §4.1/§4.2）。</li>
 * </ol>
 *
 * <h2>因此的落地方式（把歧义显式化，而不是猜）</h2>
 * <ul>
 *   <li>节点内去重：**恒开**（文档明确）；</li>
 *   <li>连续节点合并：默认**关闭**（文档明确），合并能力做成**系统级配置**
 *       {@code oa.workflow.approver.merge-consecutive-nodes}（默认 {@code false}），
 *       并作为**待决策项**交付：一旦业务确认需要「按模板/按节点」勾选，
 *       需要给 {@code flow_template}（或 {@code flow_node}）加一列才能落到设计器上。</li>
 * </ul>
 *
 * <p>{@link #mergeConsecutiveNodes(boolean)} 只回答「允许不允许合并」，
 * **不执行合并**：真正的合并发生在 2a.4/2a.5 的任务生成阶段（本工作包不实现流转与任务）。
 */
public final class CandidateDedupPolicy {

    private CandidateDedupPolicy() {
    }

    /**
     * 节点内去重（恒开）：「同一人在同一节点出现多次时自动去重」（prd §5.4）。
     *
     * @param candidates 原始候选人（可能因一人多岗/多组织挂职而重复）
     * @return 去重结果（保序，按 {@code user_id}）
     */
    public static DedupResult dedupWithinNode(List<Candidate> candidates) {
        Map<Long, Candidate> unique = new LinkedHashMap<>();
        List<Candidate> removed = new ArrayList<>();
        int total = 0;
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate == null || candidate.userId() == null) {
                    continue;
                }
                total++;
                Candidate previous = unique.putIfAbsent(candidate.userId(), candidate);
                if (previous != null) {
                    removed.add(candidate);
                }
            }
        }
        return new DedupResult(new ArrayList<>(unique.values()), removed, total);
    }

    /**
     * 是否允许「连续节点自动合并」。
     *
     * @param configured 配置值（{@code null} 视为未配置 → 关闭，与 prd §5.4「默认逐节点分别审批」一致）
     */
    public static boolean mergeConsecutiveNodes(Boolean configured) {
        return Boolean.TRUE.equals(configured);
    }

    /** 合并策略的可读说明（{@code GET /approver-rules/merge-policy} 的出参文案）。 */
    public static String describeMergePolicy(Boolean configured) {
        return mergeConsecutiveNodes(configured)
                ? "连续节点合并：**已开启**（系统级配置 oa.workflow.approver.merge-consecutive-nodes=true）；"
                + "同一人连续担任多个串行节点的审批人时，任务将合并为一组（由 2a.4/2a.5 执行）"
                : "连续节点合并：**关闭（默认）** —— 同一人同时是多个串行节点的审批人时逐节点分别审批"
                + "（prd §5.4 / enums.md §3 共同约束第 4 条）；"
                + "若业务要求「按模板显式配置合并」，需新增 flow_template/flow_node 配置列（**待决策项**）";
    }

    /**
     * 去重结果。
     *
     * @param candidates 去重后的候选人（保序）
     * @param removed    被去掉的重复项（写进证据链，便于核对「一人多岗」）
     * @param total      去重前的条目数
     */
    public record DedupResult(List<Candidate> candidates, List<Candidate> removed, int total) {

        public DedupResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            removed = removed == null ? List.of() : List.copyOf(removed);
        }

        public boolean hasDuplicates() {
            return !removed.isEmpty();
        }

        /** 证据句：「原始 3 条 → 去重后 2 条（去掉：张三）」。 */
        public String evidence() {
            if (!hasDuplicates()) {
                return "候选人数 " + total + "（无重复）";
            }
            List<String> names = new ArrayList<>();
            for (Candidate candidate : removed) {
                names.add(candidate.name() == null ? ("#" + candidate.userId()) : candidate.name());
            }
            return "原始 " + total + " 条 → 去重后 " + candidates.size() + " 条（同一节点内重复已自动去重："
                    + String.join("、", names) + "）";
        }
    }
}
