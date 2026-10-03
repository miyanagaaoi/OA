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
 * 规则②：<b>集团财务部负责人</b>（＝集团归口部门）—— 规则码 {@code finance_owner}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「财务部复核（② = 集团归口部门）→ <b>恒取集团财务部负责人</b>
 *       （五个事项分类统一归口财务，不细分）」；</li>
 *   <li>doc/prd-0.1.md §6.1「集团归口部门恒为财务部」：五个事项类别统一归属财务管理，**不细分**；</li>
 *   <li>doc/enums.md §3 {@code finance_owner}：「恒取集团财务部负责人（五个类别统一归口，不细分）」。</li>
 * </ul>
 *
 * <p><b>本规则刻意不看 {@code category}</b>：类别只是分类标签，不决定归口部门。
 * 财务部组织由 {@code oa.scope.finance-dept-id} / {@code oa.scope.finance-dept-name}
 * （默认「财务部」）定位，取该组织的**正职**负责人。
 *
 * <p>「不涉及费用」（{@code involve_cost=false}）的事项单会**跳过本节点**（templates.md §1.1），
 * 该判断由 {@code ApproverPrecheckService} 用节点的 {@code skip_condition} 完成，不属本规则职责；
 * 跳过时归口部门仍记为财务部（PRD §6.3）。
 */
public class FinanceOwnerRule implements ApproverResolutionRule {

    private final Long configuredFinanceDeptId;
    private final String configuredFinanceDeptName;

    public FinanceOwnerRule(Long configuredFinanceDeptId, String configuredFinanceDeptName) {
        this.configuredFinanceDeptId = configuredFinanceDeptId;
        this.configuredFinanceDeptName = configuredFinanceDeptName == null || configuredFinanceDeptName.isBlank()
                ? "财务部" : configuredFinanceDeptName;
    }

    @Override
    public String code() {
        return ApproverRule.FINANCE_OWNER.code();
    }

    @Override
    public String label() {
        return ApproverRule.FINANCE_OWNER.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「财务部复核」行 + §6.1「集团归口部门恒为财务部」"
                + " + doc/enums.md §3 finance_owner";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        Optional<OrgNodeView> financeDept;
        if (configuredFinanceDeptId != null) {
            financeDept = directory.org(configuredFinanceDeptId);
        } else {
            financeDept = directory.orgByName(configuredFinanceDeptName);
        }
        if (financeDept.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "未找到集团财务部组织（配置 oa.scope.finance-dept-id=" + configuredFinanceDeptId
                            + " / oa.scope.finance-dept-name=" + configuredFinanceDeptName + "）",
                    "请建立财务部组织节点，或在 application.yml 中配置 oa.scope.finance-dept-id / finance-dept-name");
        }
        OrgNodeView dept = financeDept.get();
        List<Candidate> leaders = RuleSupport.assignable(directory.primaryLeaders(dept.id()));
        if (leaders.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "集团财务部 " + RuleSupport.describeOrg(dept.name(), dept.id(), dept.path())
                            + " 未配置正职负责人",
                    "请为财务部配置正职负责人（sys_org_leader.leader_type=primary 且 category 为空）");
        }
        return RuleOutcome.resolved(code(), leaders,
                "集团归口节点：五类事项统一归口财务部（node_code=" + node.nodeCode()
                        + "），不按类别分流；取财务部 " + RuleSupport.describeOrg(dept.name(), dept.id(), dept.path())
                        + " 的正职负责人：" + RuleSupport.describe(leaders));
    }
}
