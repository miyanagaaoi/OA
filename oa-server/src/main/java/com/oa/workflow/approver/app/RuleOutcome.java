package com.oa.workflow.approver.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一条解析规则的输出。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link #groups()} 支持 {@code collab_dept_leader} 的「每个被勾选部门一组独立会签」
 *       （PRD §6.4 REQ-FLOW-005）；其余规则只有一组；</li>
 *   <li>{@link #evidence()} 是**必填**的：它写进 {@code approver_snapshot_json.nodes[].evidence}
 *       （doc/data-model.md §7.1），用于日后回答「当时为什么解析出这个人」；</li>
 *   <li>{@link #missingConfig()} 非空即表示**该节点没有可用审批人**，
 *       发起前预检据此拦截并给出「哪个节点、命中哪条规则、缺什么配置」（AC-11 / AC-19）。</li>
 * </ul>
 *
 * @param rule          解析规则码（{@code enums.md} §3）
 * @param groups        候选人多组（单组规则只有 1 个元素；组内已按规则去重）
 * @param evidence      解析依据（证据链，写进快照）
 * @param missingConfig 缺少的配置说明（空 = 解析成功）
 */
public record RuleOutcome(
        String rule,
        List<List<Candidate>> groups,
        String evidence,
        List<String> missingConfig
) {

    public RuleOutcome {
        groups = groups == null ? List.of() : List.copyOf(groups);
        missingConfig = missingConfig == null ? List.of() : List.copyOf(missingConfig);
    }

    /** 解析成功（有候选人、无缺配说明）。 */
    public static RuleOutcome resolved(String rule, List<Candidate> candidates, String evidence) {
        return new RuleOutcome(rule, List.of(candidates == null ? List.of() : List.copyOf(candidates)),
                evidence, List.of());
    }

    /** 解析成功（多组会签）。 */
    public static RuleOutcome grouped(String rule, List<List<Candidate>> groups, String evidence) {
        return new RuleOutcome(rule, groups, evidence, List.of());
    }

    /** 解析失败：候选人集合为空，必须给出缺什么配置。 */
    public static RuleOutcome empty(String rule, String evidence, String... missingConfig) {
        List<String> problems = new ArrayList<>();
        if (missingConfig != null) {
            for (String item : missingConfig) {
                if (item != null && !item.isBlank()) {
                    problems.add(item);
                }
            }
        }
        if (problems.isEmpty()) {
            problems.add("候选人集合为空，且未给出可读的配置缺失说明（实现缺陷）");
        }
        return new RuleOutcome(rule, List.of(), evidence, problems);
    }

    /** 全部候选人（跨组展平、按用户 id 保序去重）。 */
    public List<Candidate> candidates() {
        Set<Long> seen = new LinkedHashSet<>();
        List<Candidate> result = new ArrayList<>();
        for (List<Candidate> group : groups) {
            for (Candidate candidate : group) {
                if (candidate != null && candidate.userId() != null && seen.add(candidate.userId())) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /** 是否解析出至少一个可用候选人。 */
    public boolean hasCandidates() {
        return !candidates().isEmpty();
    }
}
