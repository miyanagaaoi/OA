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
 * 规则③：<b>分公司分管领导</b> —— 规则码 {@code branch_leader}。
 *
 * <h2>权威口径</h2>
 * <ul>
 *   <li>doc/prd-0.1.md §5.4：「按发起者所属公司匹配分公司绑定的分管领导」；</li>
 *   <li>doc/enums.md §3 {@code branch_leader}：「按发起人 {@code company_id} 匹配该公司绑定的分管领导」；</li>
 *   <li>doc/import-spec.md §2.3：角色 {@code branch_leader} 的数据域为 {@code company}（本公司）。</li>
 * </ul>
 *
 * <h2>「分管领导」在 {@code sys_org_leader} 里怎么落（**本实现的口径与歧义**）</h2>
 * <p>一期可用的区分维度只有三个：{@code leader_type}（正职/副职）、{@code category}（业务线）、
 * {@code duty_title}（岗位名）。其中：
 * <ul>
 *   <li>{@code category} 按 import-spec E-LEAD-008「业务线只能绑在集团层节点」，
 *       因此**不可能**用来区分公司层的两位领导；</li>
 *   <li>{@code duty_title} 按 import-spec T-08「本期不开放」（走 {@code NULL}），也不能作为判据；</li>
 *   <li>于是只剩 {@code leader_type}。而 doc/test-cases.md 的夹具明确同一家公司下并存
 *       「分公司分管领导（U-30 郑领）」与「总经理（U-31 冯总）」两人，
 *       结合「同一组织同一业务线只能有一个正职、副职不限」（import-spec T-09），
 *       唯一自洽的映射是：<b>总经理 = 公司正职，分公司分管领导 = 公司副职</b>。</li>
 * </ul>
 * <p>本实现据此取公司节点的**副职**负责人。注意 doc/import-spec.md §4.4 的 {@code leader_type}
 * 列备注写作「审批人解析取『正职』」，与上述结论存在措辞冲突 —— 已在交付说明中列为**待业务确认项**；
 * 若业务确认改为其它判据（例如新增绑定位），只需替换本类的取数口径，两侧规则无语义耦合。
 */
public class BranchLeaderRule implements ApproverResolutionRule {

    @Override
    public String code() {
        return ApproverRule.BRANCH_LEADER.code();
    }

    @Override
    public String label() {
        return ApproverRule.BRANCH_LEADER.label();
    }

    @Override
    public String source() {
        return "doc/prd-0.1.md §5.4 解析规则表「分公司分管领导」行 + doc/enums.md §3 branch_leader"
                + "（落位口径见 doc/import-spec.md §4.4 / T-09）";
    }

    @Override
    public RuleOutcome resolve(NodeConfig node, RuleRequest request, ApproverDirectory directory) {
        if (request == null || request.initiatorCompanyId() == null) {
            return notUnderCompany(request, directory, null);
        }
        Optional<OrgNodeView> company = directory.org(request.initiatorCompanyId());
        if (company.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "发起人所属公司 id=" + request.initiatorCompanyId() + " 不存在或已删除",
                    "请修正发起人的归属公司（sys_user.company_id）");
        }
        OrgNodeView org = company.get();
        // ---------------------------------------------------------------
        // 成因 (b)：company_id 指向的**不是公司节点**（集团层账号挂集团根：夹具 matrix_admin / dev_gl01
        // 的 sys_user.company_id = 1 = 集团根）。若不在这里分流，会落到下面的「公司未配置副职」文案，
        // 管理员去配集团节点的副职也解决不了问题 —— ③ 要的是**公司**的副职。
        // 产品裁定（2026-10-05）：**不跳过③、不沿组织链上溯**，拦截照旧，只把缺配说明指对方向。
        // ---------------------------------------------------------------
        if (!org.isCompany()) {
            return notUnderCompany(request, directory, org);
        }
        List<Candidate> deputies = RuleSupport.assignable(directory.deputyLeaders(org.id()));
        if (deputies.isEmpty()) {
            return RuleOutcome.empty(code(),
                    "公司 " + RuleSupport.describeOrg(org.name(), org.id(), org.path())
                            + " 未配置副职（分公司分管领导）负责人",
                    "请为公司配置副职负责人（sys_org_leader.leader_type=deputy；"
                            + "I型口径：总经理=正职、分公司分管领导=副职）");
        }
        return RuleOutcome.resolved(code(), deputies,
                "按发起人所属公司 " + RuleSupport.describeOrg(org.name(), org.id(), org.path())
                        + " 匹配分公司分管领导（副职）：" + RuleSupport.describe(deputies));
    }

    /**
     * <b>成因 (b)：发起人不在任何公司节点下</b>（{@code sys_user.company_id} 为空，或指向集团根等非公司节点）。
     *
     * <p>与「公司没配副职」（成因 (a)）是**两件事**，必须给不同的缺配说明：
     * <ul>
     *   <li>(a) 管理员要配的是 {@code sys_org_leader.leader_type=deputy}（公司副职）；</li>
     *   <li>(b) 管理员配副职**没有用** —— ③ 取的是「发起人所属公司」的副职，发起人不属于任何公司时，
     *       必须先给这个人指定所属公司（{@code sys_user.company_id} 指向 {@code company} 节点），
     *       或改由公司层账号发起。</li>
     * </ul>
     * <p>两种情况**判定结果不变**（都仍为空候选人 → AC-19 拦截 40007），只改文案 —— 文案是给管理员看的
     * 唯一线索，指错方向等于让人白跑一趟。
     *
     * @param companyIdNode {@code company_id} 指向的非公司节点；{@code null} 表示 {@code company_id} 本身为空
     */
    private RuleOutcome notUnderCompany(RuleRequest request, ApproverDirectory directory, OrgNodeView companyIdNode) {
        String current = describeInitiatorOrg(request, directory, companyIdNode);
        return RuleOutcome.empty(code(),
                "发起人不在任何公司节点下（当前挂 " + current + "）：节点③「分公司分管领导」取的是"
                        + "「发起人所属公司的副职」，发起人无所属公司时该节点必然为空",
                "请先为该人员指定所属公司（sys_user.company_id 指向 company 节点），或改由公司层账号发起"
                        + "（2026-10-05 产品裁定：③ 不跳过、不上溯，缺的配置由管理员在组织架构里补齐）");
    }

    /** 发起人当前所在组织/公司的可读描述（成因 (b) 的文案用；全部取不到时给「未知组织」）。 */
    private static String describeInitiatorOrg(RuleRequest request, ApproverDirectory directory,
                                               OrgNodeView companyIdNode) {
        if (companyIdNode != null) {
            return RuleSupport.describeOrg(companyIdNode.name(), companyIdNode.id(), companyIdNode.path());
        }
        if (request == null) {
            return "未知组织";
        }
        Long orgId = request.initiatorOrgId();
        if (orgId != null) {
            if (directory != null) {
                Optional<OrgNodeView> org = directory.org(orgId);
                if (org.isPresent()) {
                    return RuleSupport.describeOrg(org.get().name(), org.get().id(), org.get().path());
                }
            }
            return "id=" + orgId
                    + (request.initiatorOrgPath() == null ? "" : "（" + request.initiatorOrgPath() + "）");
        }
        return request.initiatorOrgPath() == null ? "未知组织" : request.initiatorOrgPath();
    }
}
