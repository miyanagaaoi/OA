package com.oa.workflow.approver.app.rules;

import com.oa.workflow.approver.app.Candidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析规则的共用工具（**纯函数**）。
 *
 * <p>把两条对所有规则都成立的口径收敛到一处，避免 9 份实现各写一遍：
 * <ol>
 *   <li><b>在职过滤</b>：离职（{@code resigned}）与停用（{@code disabled}）人员**一律不作为候选人**
 *       （PRD §5.4「总经理离职未补」属空候选人场景，必须被拦截而不是静默通过）；</li>
 *   <li><b>可读证据</b>：候选人清单转成快照 evidence 的稳定文本（姓名 + 工号 + 组织）。</li>
 * </ol>
 */
final class RuleSupport {

    private RuleSupport() {
    }

    /** 过滤出可用于审批的候选人（保序、按用户 id 去重）。 */
    static List<Candidate> assignable(List<Candidate> candidates) {
        Map<Long, Candidate> unique = new LinkedHashMap<>();
        if (candidates != null) {
            for (Candidate candidate : candidates) {
                if (candidate != null && candidate.assignable()) {
                    unique.putIfAbsent(candidate.userId(), candidate);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** 「张三（工号 A001，/1/12/135/）」这样的可读清单。 */
    static String describe(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (Candidate candidate : candidates) {
            StringBuilder builder = new StringBuilder();
            builder.append(candidate.name() == null ? ("#" + candidate.userId()) : candidate.name());
            if (candidate.employeeNo() != null && !candidate.employeeNo().isBlank()) {
                builder.append("（工号 ").append(candidate.employeeNo());
                if (candidate.orgPath() != null) {
                    builder.append('，').append(candidate.orgPath());
                }
                builder.append('）');
            } else if (candidate.orgPath() != null) {
                builder.append("（").append(candidate.orgPath()).append('）');
            }
            parts.add(builder.toString());
        }
        return String.join("、", parts);
    }

    /** 组织描述：「名称（id=135，/1/12/135/）」。 */
    static String describeOrg(String name, Long id, String path) {
        StringBuilder builder = new StringBuilder(name == null ? "该组织" : name);
        builder.append("（id=").append(id);
        if (path != null) {
            builder.append('，').append(path);
        }
        builder.append('）');
        return builder.toString();
    }
}
