package com.oa.workflow.approver.app.rules;

import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.ApproverResolutionRule;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.NodeConfig;
import com.oa.workflow.approver.app.RuleOutcome;
import com.oa.workflow.approver.app.RuleRequest;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则⑧：<b>发起人自选</b> —— 规则码 {@code initiator_pick}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「发起人自选 —— 发起时由发起人从通讯录中选择」；</li>
 *   <li>doc/enums.md §3 {@code initiator_pick}：「发起时由发起人从通讯录中选择候选人」；</li>
 *   <li>doc/enums.md §3 共同约束第 2 条：候选人集合为空 → <b>禁止发起</b>（本规则同样适用：
 *       发起人没选人就是空候选人）。</li>
 * </ul>
 *
 * <p>自选值由 {@link RuleRequest#initiatorPicks()} 传入（发起表单字段），
 * 逐个校验用户存在且在職；失效 id 被剔除并写进证据链（便于事后核对「发起时选的是谁」）。
 */
public class InitiatorPickRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.INITIATOR_PICK.code();
    }

    @Override
    public String label() {
        return ApproverRule.INITIATOR_PICK.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「发起人自选」行 + doc/enums.md §3 initiator_pick";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        List<Long> picks = request == null ? List.of() : request.initiatorPicks();
        if (picks.isEmpty()) {
            return RuleOutcome.empty(code(), "节点由发起人自选审批人，但本次发起未选择任何候选人",
                    "请在发起时从通讯录选择审批人（字段：审批人自选；空候选人禁止发起，REQ-FLOW-012）");
        }
        List<Candidate> found = RuleSupport.assignable(directory.users(picks));
        if (found.isEmpty()) {
            return RuleOutcome.empty(code(), "发起人自选的 " + picks + " 中没有在职用户",
                    "请重新选择在职人员作为审批人（离职/停用人员不可作为审批人）");
        }
        java.util.Set<Long> resolved = new java.util.HashSet<>();
        for (Candidate candidate : found) {
            resolved.add(candidate.userId());
        }
        List<Long> missing = new ArrayList<>();
        for (Long id : picks) {
            if (!resolved.contains(id)) {
                missing.add(id);
            }
        }
        return RuleOutcome.resolved(code(), found,
                "发起人自选：" + RuleSupport.describe(found)
                        + (missing.isEmpty() ? "" : "（失效已剔除：" + missing + "）"));
    }
}
