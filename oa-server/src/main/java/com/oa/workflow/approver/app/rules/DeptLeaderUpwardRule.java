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
 * 规则①：<b>直属部门负责人（上溯）</b> —— 规则码 {@code dept_leader_upward}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4 解析规则表首行：「取发起者所在科室的负责人；科室未设负责人时
 *       <b>上溯取所属部门负责人</b>」；</li>
 *   <li>doc/enums.md §3 {@code dept_leader_upward}：「取发起人科室负责人；科室无负责人则上溯到
 *       部门负责人；<b>仍为空则禁止发起</b>」。</li>
 * </ul>
 *
 * <h2>级数口径（刻意只上溯 1 级）</h2>
 * <p>enums.md §3 的措辞是「科室 → 所属部门」两级，并明确「仍为空则禁止发起」。
 * data-model.md §4.2 的列注释写成「本级无配置则逐级上溯」，但**逐级上溯到公司/集团会取错人**：
 * 公司层有 ④子公司总经理、集团层有 ⑤⑥，把它们当作「直属部门负责人」会让 ③④⑤⑥ 四个节点退化成同一个人。
 * 因此本实现按 enums.md §3（规则码的权威源）执行：**本级 → 上级，两级为止**；
 * 仍为空即返回空候选人（由发起前预检拦截，AC-11）。
 *
 * <p>典型场景（本仓库夹具）：发起人在「科室1A」（{@code /1/12/135/138/}，未设负责人）→
 * 上溯到「部门1」（{@code /1/12/135/}）的负责人。
 */
public class DeptLeaderUpwardRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.DEPT_LEADER_UPWARD.code();
    }

    @Override
    public String label() {
        return ApproverRule.DEPT_LEADER_UPWARD.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表首行 + doc/enums.md §3 dept_leader_upward";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        if (request == null || request.initiatorOrgId() == null) {
            return RuleOutcome.empty(code(), "发起人未归属任何组织节点，无法解析直属部门负责人",
                    "请先为发起人配置主归属组织（sys_user.org_id）");
        }
        Optional<OrgNodeView> current = directory.org(request.initiatorOrgId());
        if (current.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "发起人归属组织 id=" + request.initiatorOrgId() + " 不存在或已删除",
                    "请修正发起人的主归属组织（sys_user.org_id）");
        }
        OrgNodeView node1 = current.get();
        List<Candidate> level1 = RuleSupport.assignable(directory.primaryLeaders(node1.id()));
        if (!level1.isEmpty()) {
            return RuleOutcome.resolved(code(), level1,
                    "发起人所属" + typeLabel(node1.orgType()) + " " + RuleSupport.describeOrg(node1.name(), node1.id(), node1.path())
                            + " 的正职负责人：" + RuleSupport.describe(level1));
        }

        // 本级无负责人 → 只上溯一级（且不越过集团根：集团层的负责人归属 ⑤⑥ 两个节点）
        if (node1.parentId() == null) {
            return RuleOutcome.empty(code(),
                    "发起人所属" + typeLabel(node1.orgType()) + " " + RuleSupport.describeOrg(node1.name(), node1.id(), node1.path())
                            + " 未配置正职负责人，且已是根节点，无处上溯",
                    "请为该组织配置正职负责人（sys_org_leader.leader_type=primary）");
        }
        Optional<OrgNodeView> parent = directory.org(node1.parentId());
        if (parent.isEmpty() || parent.get().isGroup()) {
            return RuleOutcome.empty(code(),
                    "发起人所属" + typeLabel(node1.orgType()) + " " + RuleSupport.describeOrg(node1.name(), node1.id(), node1.path())
                            + " 未配置正职负责人；上级组织为集团层（不再上溯，集团负责人归属节点⑤⑥）",
                    "请为该科室/部门配置正职负责人（sys_org_leader.leader_type=primary，category 为空）");
        }
        OrgNodeView node2 = parent.get();
        List<Candidate> level2 = RuleSupport.assignable(directory.primaryLeaders(node2.id()));
        if (!level2.isEmpty()) {
            return RuleOutcome.resolved(code(), level2,
                    "发起人所属" + typeLabel(node1.orgType()) + " " + RuleSupport.describeOrg(node1.name(), node1.id(), node1.path())
                            + " 未配置负责人 → 上溯至" + typeLabel(node2.orgType()) + " "
                            + RuleSupport.describeOrg(node2.name(), node2.id(), node2.path())
                            + " 的正职负责人：" + RuleSupport.describe(level2));
        }
        return RuleOutcome.empty(code(),
                "发起人所属" + typeLabel(node1.orgType()) + " " + RuleSupport.describeOrg(node1.name(), node1.id(), node1.path())
                        + " 与其上级" + typeLabel(node2.orgType()) + " "
                        + RuleSupport.describeOrg(node2.name(), node2.id(), node2.path()) + " 均未配置正职负责人",
                "请为其中至少一个组织配置正职负责人（sys_org_leader.leader_type=primary 且 category 为空）");
    }

    private static String typeLabel(String orgType) {
        if (orgType == null) {
            return "组织";
        }
        return switch (orgType.toLowerCase(java.util.Locale.ROOT)) {
            case "group" -> "集团";
            case "company" -> "公司";
            case "dept" -> "部门";
            case "section" -> "科室";
            default -> "组织";
        };
    }
}
