package com.oa.workflow.approver.app.rules;

import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.ApproverResolutionRule;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.NodeConfig;
import com.oa.workflow.approver.app.OrgNodeView;
import com.oa.workflow.approver.app.RuleOutcome;
import com.oa.workflow.approver.app.RuleRequest;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则⑥：<b>集团董事长</b> —— 规则码 {@code chairman}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「取集团董事长」（唯一候选人）；</li>
 *   <li>doc/enums.md §3 {@code chairman}：「取集团董事长（唯一）」；</li>
 *   <li>落位：集团根节点（{@code org_type='group'}）上 {@code category} 为空的正职负责人
 *       —— 与 ⑤ 的业务线正职共享集团节点，但属于不同的「正职分组」
 *       （import-spec T-09：同一组织同一业务线只能有一个正职，{@code category} 留空自成一组）。</li>
 * </ul>
 *
 * <p>本节点默认 {@code sign_policy=required}（templates.md §1.0「默认强制签名」），
 * 但签名采集属阶段 3，本工作包只做配置与冻结。
 */
public class ChairmanRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.CHAIRMAN.code();
    }

    @Override
    public String label() {
        return ApproverRule.CHAIRMAN.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「集团董事长」行 + doc/enums.md §3 chairman";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        List<OrgNodeView> groups = new ArrayList<>();
        for (OrgNodeView org : directory.orgsByType("group")) {
            if (org != null && org.active()) {
                groups.add(org);
            }
        }
        if (groups.isEmpty()) {
            return RuleOutcome.empty(code(), "库中不存在启用状态的集团（org_type='group'）根节点",
                    "请先初始化组织架构的集团根节点（import-spec §3.2）");
        }
        List<String> attempted = new ArrayList<>();
        List<Candidate> merged = new ArrayList<>();
        for (OrgNodeView group : groups) {
            List<Candidate> leaders = RuleSupport.assignable(directory.primaryLeaders(group.id()));
            if (!leaders.isEmpty()) {
                merged.addAll(leaders);
                attempted.add(RuleSupport.describeOrg(group.name(), group.id(), group.path())
                        + " → " + RuleSupport.describe(leaders));
            }
        }
        List<Candidate> unique = RuleSupport.assignable(merged);
        if (!unique.isEmpty()) {
            return RuleOutcome.resolved(code(), unique,
                    "取集团董事长（集团节点 category 为空的正职负责人）：" + String.join("；", attempted));
        }
        return RuleOutcome.empty(code(),
                "集团层 " + groups.size() + " 个节点均未配置董事长（category 为空的正职负责人）",
                "请在集团层配置董事长（sys_org_leader：org_type=group、category 为空、leader_type=primary）");
    }
}
