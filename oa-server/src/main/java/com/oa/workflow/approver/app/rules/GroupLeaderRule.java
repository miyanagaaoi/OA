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
 * 规则⑤：<b>集团分管领导</b> —— 规则码 {@code group_leader}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「按事项类别匹配集团层绑定的分管领导」；</li>
 *   <li>doc/enums.md §3 {@code group_leader}：「按事项类别匹配集团层业务线绑定的分管领导」；</li>
 *   <li>doc/import-spec.md §3.4 / T-06：业务线（{@code business_line}）复用事项类别五值
 *       （经营 business / 经济 economy / 行政 admin / 人力 hr / 投资 invest），
 *       落 {@code sys_org_leader.category}；且<b>仅集团层节点可绑</b>（E-LEAD-008）。</li>
 * </ul>
 *
 * <p>解析路径：集团根节点（{@code org_type='group'}）→ 该节点上 {@code category = 事项类别}
 * 的正职负责人。类别是**发起时的快照**（不可改判），因此本规则同时服务发起解析与
 * 驳回重提时的重新解析（V-06）。
 *
 * <p>为什么必须先定位集团根：业务线绑定只可能落在集团层（E-LEAD-008），
 * 直接用 {@code category} 全库查会命中历史脏数据（例如误绑在公司层的行）。
 */
public class GroupLeaderRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.GROUP_LEADER.code();
    }

    @Override
    public String label() {
        return ApproverRule.GROUP_LEADER.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「集团分管领导」行 + doc/enums.md §3 group_leader"
                + " + doc/import-spec.md §3.4（业务线仅集团层可绑）";
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
        String category = request == null ? null : request.category();
        if (category == null || category.isBlank()) {
            return RuleOutcome.empty(code(), "本单未提供事项类别（category），无法匹配集团分管领导的业务线",
                    "请在发起时确定事项类别（配置项，doc/enums.md §10.1 五值之一）");
        }
        List<String> attempted = new ArrayList<>();
        for (OrgNodeView group : groups) {
            List<Candidate> leaders = RuleSupport.assignable(directory.categoryLeaders(group.id(), category));
            if (!leaders.isEmpty()) {
                return RuleOutcome.resolved(code(), leaders,
                        "按事项类别 " + category + " 匹配集团层 " + RuleSupport.describeOrg(group.name(), group.id(), group.path())
                                + " 绑定的业务线分管领导：" + RuleSupport.describe(leaders));
            }
            attempted.add(RuleSupport.describeOrg(group.name(), group.id(), group.path()));
        }
        return RuleOutcome.empty(code(),
                "集团层 " + String.join("、", attempted) + " 均未绑定事项类别「" + category + "」的分管领导",
                "请在集团层为该业务线绑定分管领导（sys_org_leader：org_type=group 且 category=" + category
                        + "，leader_type=primary；业务线只能绑在集团层节点，import-spec E-LEAD-008）");
    }
}
