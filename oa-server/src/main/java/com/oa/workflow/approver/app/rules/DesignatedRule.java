package com.oa.workflow.approver.app.rules;

import com.oa.common.json.JsonText;
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
 * 规则⑦：<b>指定人员 / 角色</b> —— 规则码 {@code designated}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「指定人员/角色 —— IT部门在流程设计器中固定指定」；</li>
 *   <li>doc/enums.md §3 {@code designated}：参数存 {@code approver_param}，形如
 *       {@code {"user_ids":[1001,1002]}} 或 {@code {"role_code":"finance_clerk"}}；</li>
 *   <li>doc/templates.md §1.1 ⑦ 的参数：{@code {"role_code":"finance_clerk"}}（财务部内勤角色）。</li>
 * </ul>
 *
 * <p>两种参数**二选一**（同时给出属配置错误，由 {@code NodeDefinitionValidator} 在设计期拦下）：
 * <ul>
 *   <li>{@code user_ids}：逐个校验用户存在且在職（离职/停用即视为该 id 失效，不计入候选人）；</li>
 *   <li>{@code role_code}：取该角色下的**在职**用户。</li>
 * </ul>
 */
public class DesignatedRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.DESIGNATED.code();
    }

    @Override
    public String label() {
        return ApproverRule.DESIGNATED.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「指定人员/角色」行 + doc/enums.md §3 designated"
                + " + doc/templates.md §1.1（⑦ 的 role_code 参数）";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        if (node == null || node.approverParam() == null || node.approverParam().isNull()) {
            return RuleOutcome.empty(code(), "节点未声明 approver_param，无法解析指定人员/角色",
                    "请在流程设计器中为该节点配置 approver_param（{\"user_ids\":[...]} 或 {\"role_code\":\"...\"}）");
        }
        String paramText = node.approverParam().toString();
        List<Long> userIds = JsonText.longArray(paramText, "user_ids");
        String roleCode = JsonText.text(node.approverParam(), "role_code");

        if (!userIds.isEmpty()) {
            List<Candidate> found = RuleSupport.assignable(directory.users(userIds));
            if (found.isEmpty()) {
                return RuleOutcome.empty(code(),
                        "approver_param.user_ids=" + userIds + " 中没有任何在职用户",
                        "请检查这些用户是否存在且未离职/未停用（sys_user.status=active）");
            }
            List<Long> missing = new ArrayList<>();
            java.util.Set<Long> resolved = new java.util.HashSet<>();
            for (Candidate candidate : found) {
                resolved.add(candidate.userId());
            }
            for (Long id : userIds) {
                if (!resolved.contains(id)) {
                    missing.add(id);
                }
            }
            String evidence = "流程设计器固定指定的用户：" + RuleSupport.describe(found)
                    + (missing.isEmpty() ? "" : "（失效已剔除：" + missing + "）");
            return RuleOutcome.resolved(code(), found, evidence);
        }

        if (roleCode != null && !roleCode.isBlank()) {
            List<Candidate> byRole = RuleSupport.assignable(directory.usersByRoleCode(roleCode));
            if (byRole.isEmpty()) {
                return RuleOutcome.empty(code(),
                        "approver_param.role_code=" + roleCode + " 角色下没有任何在职用户",
                        "请为该角色分配在职用户（sys_user_role + sys_user.status=active），"
                                + "或改为 approver_param.user_ids 指定到人");
            }
            return RuleOutcome.resolved(code(), byRole,
                    "取角色 " + roleCode + " 下的在职用户：" + RuleSupport.describe(byRole));
        }

        return RuleOutcome.empty(code(), "approver_param 既未给出 user_ids 也未给出 role_code",
                "请配置 {\"user_ids\":[...]} 或 {\"role_code\":\"...\"}（doc/enums.md §3 designated）");
    }
}
