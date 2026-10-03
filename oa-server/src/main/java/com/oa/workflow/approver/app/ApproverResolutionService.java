package com.oa.workflow.approver.app;

import com.oa.common.config.OaProperties;
import com.oa.workflow.definition.app.SkipConditionEvaluator;
import com.oa.workflow.definition.app.ThresholdPolicy;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 审批人解析服务（2a.3，{@code oa.workflow.approver.*}）—— <b>逐节点跑规则</b>。
 *
 * <h2>职责边界（刻意收窄）</h2>
 * <ol>
 *   <li>按节点声明的 {@code approver_rule} 取规则实现（{@link ApproverRuleRegistry}），
 *       传入发起上下文与身份目录，得到候选人与**证据链**；</li>
 *   <li>先判**跳过条件**：命中 {@code skip_condition} 的节点标记 {@code skipped}，
 *       **不参与空候选人拦截**（否则事项单「不涉及费用」会永远发不出去）；
 *       跳过时归口部门仍记为财务部（PRD §6.3，由 {@code FlowInstanceService} 落库）；</li>
 *   <li>节点内**自动去重**（prd §5.4，恒开）；</li>
 *   <li><b>不做</b>：任务生成、决议判定、流转（2a.4/2a.5）。</li>
 * </ol>
 *
 * <p>本类是**纯编排**：不访问数据库（数据访问在 {@link ApproverDirectory} 的实现里），
 * 因此可以用内存目录对「9 条规则 × 各种组织形态」做穷举单测。
 */
@Service
public class ApproverResolutionService {

    private final ApproverRuleRegistry registry;
    private final int maxCandidatesPerNode;

    public ApproverResolutionService(ApproverRuleRegistry registry, OaProperties properties) {
        this.registry = registry;
        this.maxCandidatesPerNode = Math.max(properties.getWorkflow().getApprover().getMaxCandidatesPerNode(), 1);
    }

    /**
     * 解析单个节点。
     *
     * @param node      节点配置（锁定版本的那一行）
     * @param request   发起上下文
     * @param directory 身份目录
     */
    public NodeResolution resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        if (node == null) {
            throw new IllegalArgumentException("node 不能为空");
        }
        // ---- 跳过条件（仅事项单②；prd §6.1）----
        String skipJson = node.skipConditionText();
        String skipReason = null;
        boolean skipped = false;
        if (skipJson != null && !skipJson.isBlank()) {
            boolean matched;
            try {
                matched = SkipConditionEvaluator.matches(skipJson, request == null ? null : request.formValues());
            } catch (IllegalArgumentException ex) {
                // 配置非法：**不能**静默当作「不跳过」，否则会绕过财务复核；按不跳过处理并给出证据
                matched = false;
                skipReason = "skip_condition 非法，已按「不跳过」处理：" + ex.getMessage();
            }
            if (matched) {
                skipped = true;
                skipReason = skipReason != null ? skipReason
                        : "命中跳过条件 " + SkipConditionEvaluator.describe(skipJson)
                        + " → 本节点标记 skipped（不产生待办）；单据归口部门仍记为财务部（prd §6.3）";
            }
        }

        if (skipped) {
            return new NodeResolution(node, List.of(), List.of(),
                    "跳过：" + skipReason, List.of(), true, skipReason, null);
        }

        Optional<ApproverResolutionRule> rule = registry.find(node.approverRule());
        if (rule.isEmpty()) {
            String legacy = com.oa.workflow.definition.domain.FlowDefinitionEnums
                    .legacyHint(node.approverRule());
            return new NodeResolution(node, List.of(), List.of(),
                    "未知解析规则：" + node.approverRule(), List.of(
                    (legacy != null ? legacy : "approver_rule「" + node.approverRule() + "」不在 enums.md §3 的 9 条规则内")
                            + "；可用规则：" + registry.codes()),
                    false, null, null);
        }

        RuleOutcome outcome = rule.get().resolve(node, request, directory);

        // ---- 节点内自动去重（prd §5.4；恒开）----
        CandidateDedupPolicy.DedupResult dedup = CandidateDedupPolicy.dedupWithinNode(outcome.candidates());
        List<Candidate> deduped = dedup.candidates();
        if (deduped.size() > maxCandidatesPerNode) {
            deduped = deduped.subList(0, maxCandidatesPerNode);
        }

        String evidence = outcome.evidence();
        if (dedup.hasDuplicates()) {
            evidence = evidence + "；" + dedup.evidence();
        }
        if (deduped.isEmpty()) {
            return new NodeResolution(node, List.of(), List.of(), evidence, outcome.missingConfig(),
                    false, null, null);
        }

        // 组内同样去重（协同部门多组会签时，同一人可能挂职多个部门）
        List<List<Candidate>> groups = new ArrayList<>();
        for (List<Candidate> group : outcome.groups()) {
            CandidateDedupPolicy.DedupResult groupDedup = CandidateDedupPolicy.dedupWithinNode(group);
            if (!groupDedup.candidates().isEmpty()) {
                groups.add(groupDedup.candidates());
            }
        }
        ThresholdPolicy.Threshold threshold = ThresholdPolicy.resolve(
                node.decisionMode() == null ? null : DecisionMode.of(node.decisionMode()).orElse(null),
                node.passThreshold(), deduped.size());
        return new NodeResolution(node, deduped, groups, evidence, List.of(), false, null, threshold);
    }

    /**
     * 解析模板（锁定版本）下的全部节点。
     *
     * @param nodes     锁定版本的节点（按 seq 排序由调用方保证）
     * @param request   发起上下文
     * @param directory 身份目录
     * @param maxNodeCount 解析上限（防御性）
     */
    public List<NodeResolution> resolveAll(List<NodeConfig> nodes, RuleRequest request,
                                           ApproverDirectory directory) {
        List<NodeResolution> results = new ArrayList<>();
        if (nodes != null) {
            for (NodeConfig node : nodes) {
                if (node == null) {
                    continue;
                }
                results.add(resolve(node, request, directory));
                if (results.size() >= Math.max(maxCandidatesPerNode, 1) * 100) {
                    break;
                }
            }
        }
        return results;
    }

    /**
     * 单节点解析结果（同时是**预检明细**与**快照节点**的数据源）。
     *
     * @param node          节点配置（冻结写进快照）
     * @param candidates    去重后的候选人（跨组展平）
     * @param groups        候选人多组（协同部门每组独立会签；其余规则只有一组）
     * @param evidence      证据链（写进快照 {@code nodes[].evidence}）
     * @param missingConfig 缺配说明（非空 = 该节点无有效审批人 → 拦截）
     * @param skipped       是否命中跳过条件
     * @param skipReason    跳过原因（写进快照，便于事后核对）
     * @param threshold     通过条件解析（或签/会签/依次）
     */
    public record NodeResolution(
            NodeConfig node,
            List<Candidate> candidates,
            List<List<Candidate>> groups,
            String evidence,
            List<String> missingConfig,
            boolean skipped,
            String skipReason,
            ThresholdPolicy.Threshold threshold
    ) {

        public NodeResolution {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            groups = groups == null ? List.of() : List.copyOf(groups);
            missingConfig = missingConfig == null ? List.of() : List.copyOf(missingConfig);
        }

        /** node_seq。 */
        public Integer seq() {
            return node == null ? null : node.seq();
        }

        public String nodeCode() {
            return node == null ? null : node.nodeCode();
        }

        public String nodeName() {
            return node == null ? null : node.name();
        }

        public String rule() {
            return node == null ? null : node.approverRule();
        }

        /**
         * 是否为**阻断项**：未跳过、且没有可用候选人。
         *
         * <p>AC-11 / AC-19 的判定口径：候选人集合为空即禁止发起，**不允许静默跳过**。
         */
        public boolean blocker() {
            return !skipped && candidates.isEmpty();
        }

        /** 该节点需要几人同意（或签恒为 1；⑦ 登记节点为 0）。 */
        public int requiredApprovals() {
            return threshold == null ? candidates.isEmpty() ? 0 : 1 : threshold.requiredApprovals();
        }
    }
}
