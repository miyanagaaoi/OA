package com.oa.workflow.approver.app.rules;

import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.ApproverResolutionRule;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.NodeConfig;
import com.oa.workflow.approver.app.OrgNodeView;
import com.oa.workflow.approver.app.RuleOutcome;
import com.oa.workflow.approver.app.RuleRequest;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import java.util.List;
import java.util.Optional;

/**
 * 规则④：<b>子公司总经理</b> —— 规则码 {@code subsidiary_gm}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「取发起者所属公司的总经理」；</li>
 *   <li>doc/enums.md §3 {@code subsidiary_gm}：「取发起人所属公司的总经理」；</li>
 *   <li>「公司正职 = 总经理」由 doc/import-spec.md T-09「同一组织同一业务线只能有一个正职」
 *       与 doc/test-cases.md 的夹具（「U-31 冯总（总经理）」为公司正职）共同确定；
 *       ③分公司分管领导 与 ④子公司总经理 是同一家公司下的**两个不同人**。</li>
 * </ul>
 */
public class SubsidiaryGmRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.SUBSIDIARY_GM.code();
    }

    @Override
    public String label() {
        return ApproverRule.SUBSIDIARY_GM.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「子公司总经理」行 + doc/enums.md §3 subsidiary_gm";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        if (request == null || request.initiatorCompanyId() == null) {
            return RuleOutcome.empty(code(), "发起人未归属任何公司（sys_user.company_id 为空），无法取子公司总经理",
                    "请先为发起人配置归属公司");
        }
        Optional<OrgNodeView> company = directory.org(request.initiatorCompanyId());
        if (company.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "发起人所属公司 id=" + request.initiatorCompanyId() + " 不存在或已删除",
                    "请修正发起人的归属公司（sys_user.company_id）");
        }
        OrgNodeView org = company.get();
        List<Candidate> leaders = RuleSupport.assignable(directory.primaryLeaders(org.id()));
        if (leaders.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "公司 " + RuleSupport.describeOrg(org.name(), org.id(), org.path()) + " 未配置正职（总经理）负责人",
                    "请为公司配置正职负责人（sys_org_leader.leader_type=primary 且 category 为空）");
        }
        return RuleOutcome.resolved(code(), leaders,
                "取发起人所属公司 " + RuleSupport.describeOrg(org.name(), org.id(), org.path())
                        + " 的正职（总经理）负责人：" + RuleSupport.describe(leaders));
    }
}
