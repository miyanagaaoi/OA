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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 规则⑨：<b>协同部门负责人</b> —— 规则码 {@code collab_dept_leader}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「协同部门（②的并行子任务）—— 由②的审批人<b>在审批时勾选</b>，
 *       每个被勾选部门取该部门负责人 → <b>候选人多组（每组独立会签）</b>」；</li>
 *   <li>doc/prd-0.1.md §6.3 并行协同的位置：「协同任务<b>不占主链编号</b>，作为②节点下的并行子任务组」；</li>
 *   <li>doc/prd-0.1.md §6.4 REQ-FLOW-005：「可勾选多个协同部门，系统为每个部门生成独立任务组，
 *       <b>全部完成后</b>才进入下一节点；任一协同部门驳回则单据驳回」；</li>
 *   <li>AC-05：「<b>不得勾选本节点部门自身</b>」。</li>
 * </ul>
 *
 * <p>因此本规则**只在运行时使用**（发起时 {@code collabDeptIds} 为空是正常的，不构成空候选人拦截）：
 * 它与主干节点无绑定（{@code trunkUsable=false}，见 {@code FlowDefinitionEnums.ApproverRule}），
 * 只有显式传入勾选部门时才解析出候选人组。
 */
public class CollabDeptLeaderRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.COLLAB_DEPT_LEADER.code();
    }

    @Override
    public String label() {
        return ApproverRule.COLLAB_DEPT_LEADER.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「协同部门」行 + §6.4 REQ-FLOW-005 + AC-05";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        List<Long> deptIds = request == null ? List.of() : request.collabDeptIds();
        if (deptIds.isEmpty()) {
            // 运行时的「未勾选」是合法状态（prd §6.3：若未勾选，②通过后直接进入③），
            // 因此这里返回**空组 + 无缺配说明**的成功结果，而不是空候选人拦截。
            return RuleOutcome.grouped(code(), List.of(),
                    "②审批人未勾选协同部门 → 无并行子任务，②通过后直接进入③（prd §6.3）");
        }
        Set<Long> selfDeptIds = request == null || request.collabSelfExcludeDeptIds() == null
                ? Set.of() : request.collabSelfExcludeDeptIds();
        List<List<Candidate>> groups = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        StringBuilder evidence = new StringBuilder("协同部门（②的并行子任务组，不占主链编号）：");
        boolean first = true;
        for (Long deptId : deptIds) {
            if (deptId == null) {
                continue;
            }
            if (selfDeptIds.contains(deptId)) {
                // AC-05：不得勾选本节点部门自身
                problems.add("协同部门 id=" + deptId + " 是本节点所属部门自身，不允许勾选（AC-05）");
                continue;
            }
            Optional<OrgNodeView> dept = directory.org(deptId);
            if (dept.isEmpty()) {
                problems.add("协同部门 id=" + deptId + " 不存在或已删除");
                continue;
            }
            OrgNodeView org = dept.get();
            List<Candidate> leaders = RuleSupport.assignable(directory.primaryLeaders(org.id()));
            if (leaders.isEmpty()) {
                problems.add("协同部门 " + RuleSupport.describeOrg(org.name(), org.id(), org.path())
                        + " 未配置正职负责人，无法生成会签组");
                continue;
            }
            groups.add(leaders);
            if (!first) {
                evidence.append('；');
            }
            first = false;
            evidence.append(RuleSupport.describeOrg(org.name(), org.id(), org.path()))
                    .append(" → ").append(RuleSupport.describe(leaders));
        }
        if (!problems.isEmpty()) {
            return RuleOutcome.empty(code(), evidence.toString(), problems.toArray(new String[0]));
        }
        return RuleOutcome.grouped(code(), groups, evidence
                + "（共 " + groups.size() + " 组，每组独立会签，全部完成后进入下一节点）");
    }
}
