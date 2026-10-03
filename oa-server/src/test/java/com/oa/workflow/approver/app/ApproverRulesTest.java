package com.oa.workflow.approver.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.json.JsonText;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>9 条审批人解析规则逐条单测</b>（2a.3 的验收要求：每条规则一个可单测的单元）。
 *
 * <p>夹具沿用本仓库的真实组织形态（doc/test-cases.md §1.2 的口径）：
 * <pre>
 * 集团(1) ── 公司A(12) ── 部门1(135) ── 科室1A(138)
 *                  └──── 部门2(136)         财务部(210)
 * </pre>
 * 负责人：科室1A 无 → 部门1 = 207（导入员工）；公司A 正职（总经理）= 204，副职（分公司分管领导）= 203；
 * 集团正职（董事长）= 1，集团「经济」业务线正职 = 205；财务部正职 = 206。
 *
 * <p>所有断言同时校验**证据链**（evidence）与**缺配说明**（missingConfig）——
 * 后者是 AC-11「XX 节点无有效审批人，请联系管理员配置」的可读来源。
 */
class ApproverRulesTest {

    private InMemoryApproverDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(136L, 12L, "dept", "部门2", "/1/12/136/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(1L, "系统管理员", "A000", 1L, 1L)
                .user(203L, "郑领", "A203", 12L, 12L)
                .user(204L, "冯总", "A204", 12L, 12L)
                .user(205L, "集团分管", "A205", 1L, 1L)
                .user(206L, "财务负责人", "A206", 210L, 1L)
                .user(207L, "导入员工", "A207", 135L, 12L)
                .user(208L, "普通员工", "A208", 138L, 12L)
                .user(209L, "内勤", "A209", 210L, 1L)
                .primary(135L, 207L)
                .primary(12L, 204L)
                .deputy(12L, 203L)
                .primary(1L, 1L)
                .category(1L, "economy", 205L)
                .primary(210L, 206L)
                .role("admin", 209L);
    }

    private static RuleRequest initiatorInSection() {
        return RuleRequest.of(208L, 138L, 12L, "/1/12/135/138/", "economy", Map.of("involve_cost", true));
    }

    private static NodeConfig node(String rule, String approverParam) {
        JsonNode param = approverParam == null ? null : JsonText.read(approverParam);
        return new NodeConfig(1L, 1, "dept_leader", "直属部门负责人", "approve", rule, param,
                "any", null, "optional", 24, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE,
                Boolean.FALSE, null);
    }

    // ================================================================ ① 直属部门负责人（上溯）

    @Test
    @DisplayName("① dept_leader_upward：科室未设负责人 → 上溯取部门1 的负责人（prd §5.4 / enums.md §3）")
    void deptLeaderUpwardFallsBackToParentDept() {
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.DeptLeaderUpwardRule()
                .resolve(node("dept_leader_upward", null), initiatorInSection(), directory);

        assertThat(outcome.missingConfig()).isEmpty();
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(207L);
        assertThat(outcome.evidence())
                .contains("科室1A").contains("未配置负责人").contains("上溯").contains("部门1");
    }

    @Test
    @DisplayName("① dept_leader_upward：科室已设负责人 → 直接取本级（不上溯）")
    void deptLeaderUpwardUsesSectionLeaderFirst() {
        directory.primary(138L, 204L);
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.DeptLeaderUpwardRule()
                .resolve(node("dept_leader_upward", null), initiatorInSection(), directory);

        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(204L);
        assertThat(outcome.evidence()).contains("科室1A").doesNotContain("上溯");
    }

    @Test
    @DisplayName("① dept_leader_upward：科室与部门都未设负责人 → 空候选人 + 缺配说明（禁止发起）")
    void deptLeaderUpwardEmptyIsBlocking() {
        InMemoryApproverDirectory empty = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .user(208L, "普通员工", "A208", 138L, 12L);

        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.DeptLeaderUpwardRule()
                .resolve(node("dept_leader_upward", null), initiatorInSection(), empty);

        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.evidence()).contains("均未配置正职负责人");
        assertThat(outcome.missingConfig()).isNotEmpty();
    }

    // ================================================================ ② 集团财务部负责人

    @Test
    @DisplayName("② finance_owner：恒取集团财务部负责人，且**不受事项类别影响**（prd §5.4 / §6.1）")
    void financeOwnerAlwaysGroupFinance() {
        for (String category : List.of("business", "economy", "admin", "hr", "invest")) {
            RuleRequest request = new RuleRequest(208L, 138L, 12L, "/1/12/135/138/", category,
                    List.of(), List.of(), Map.of(), java.util.Set.of());
            RuleOutcome outcome = new com.oa.workflow.approver.app.rules.FinanceOwnerRule(null, "财务部")
                    .resolve(node("finance_owner", null), request, directory);
            assertThat(outcome.candidates()).as("类别 %s 下仍取财务部", category)
                    .extracting(Candidate::userId).containsExactly(206L);
        }
    }

    @Test
    @DisplayName("② finance_owner：财务部组织不存在 / 未设正职 → 空候选人 + 指明缺什么配置")
    void financeOwnerEmptyExplainsWhatIsMissing() {
        RuleOutcome noOrg = new com.oa.workflow.approver.app.rules.FinanceOwnerRule(null, "不存在的部门")
                .resolve(node("finance_owner", null), initiatorInSection(), directory);
        assertThat(noOrg.candidates()).isEmpty();
        assertThat(noOrg.evidence()).contains("未找到集团财务部组织");
        assertThat(noOrg.missingConfig()).isNotEmpty();

        InMemoryApproverDirectory noLeader = new InMemoryApproverDirectory()
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(208L, "普通员工", "A208", 210L, 1L);
        RuleOutcome empty = new com.oa.workflow.approver.app.rules.FinanceOwnerRule(null, "财务部")
                .resolve(node("finance_owner", null), initiatorInSection(), noLeader);
        assertThat(empty.candidates()).isEmpty();
        assertThat(empty.evidence()).contains("财务部").contains("未配置正职负责人");
        assertThat(empty.missingConfig()).isNotEmpty();
    }

    // ================================================================ ③ 分公司分管领导

    @Test
    @DisplayName("③ branch_leader：取公司副职（分公司分管领导），与④总经理是不同人（doc/test-cases.md 夹具口径）")
    void branchLeaderIsCompanyDeputy() {
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.BranchLeaderRule()
                .resolve(node("branch_leader", null), initiatorInSection(), directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(203L);
        assertThat(outcome.evidence()).contains("公司A").contains("副职");
    }

    @Test
    @DisplayName("③ branch_leader：公司未设副职 → 空候选人 + 缺配说明")
    void branchLeaderEmpty() {
        InMemoryApproverDirectory noDeputy = new InMemoryApproverDirectory()
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .user(204L, "冯总", "A204", 12L, 12L)
                .user(208L, "普通员工", "A208", 12L, 12L)
                .primary(12L, 204L);
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.BranchLeaderRule()
                .resolve(node("branch_leader", null), initiatorInSection(), noDeputy);
        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.missingConfig().get(0)).contains("分公司分管领导");
    }

    // ================================================================ ④ 子公司总经理

    @Test
    @DisplayName("④ subsidiary_gm：取公司正职（总经理）")
    void subsidiaryGmIsCompanyPrimary() {
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.SubsidiaryGmRule()
                .resolve(node("subsidiary_gm", null), initiatorInSection(), directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(204L);
        assertThat(outcome.evidence()).contains("公司A").contains("正职");
    }

    // ================================================================ ⑤ 集团分管领导

    @Test
    @DisplayName("⑤ group_leader：按事项类别匹配集团层业务线绑定的分管领导")
    void groupLeaderMatchesCategory() {
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.GroupLeaderRule()
                .resolve(node("group_leader", null), initiatorInSection(), directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(205L);
        assertThat(outcome.evidence()).contains("economy");
    }

    @Test
    @DisplayName("⑤ group_leader：业务线未绑定 / 未给类别 → 空候选人 + 缺配说明")
    void groupLeaderEmptyExplainsCategory() {
        RuleRequest noCategory = new RuleRequest(208L, 138L, 12L, "/1/12/135/138/", null,
                List.of(), List.of(), Map.of(), java.util.Set.of());
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.GroupLeaderRule()
                .resolve(node("group_leader", null), noCategory, directory);
        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.missingConfig().get(0)).contains("事项类别");

        RuleRequest hr = new RuleRequest(208L, 138L, 12L, "/1/12/135/138/", "hr",
                List.of(), List.of(), Map.of(), java.util.Set.of());
        RuleOutcome missing = new com.oa.workflow.approver.app.rules.GroupLeaderRule()
                .resolve(node("group_leader", null), hr, directory);
        assertThat(missing.candidates()).isEmpty();
        assertThat(missing.missingConfig().get(0)).contains("hr").contains("绑定");
    }

    // ================================================================ ⑥ 集团董事长

    @Test
    @DisplayName("⑥ chairman：取集团节点 category 为空的正职负责人（唯一候选人）")
    void chairmanIsGroupPrimary() {
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.ChairmanRule()
                .resolve(node("chairman", null), initiatorInSection(), directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(1L);
        assertThat(outcome.evidence()).contains("集团董事长");
    }

    // ================================================================ ⑦ 指定人员 / 角色

    @Test
    @DisplayName("⑦ designated：user_ids 指定到人；离职者被剔除并写进证据链")
    void designatedByUserIds() {
        JsonNode param = JsonText.read("{\"user_ids\":[206,209]}");
        NodeConfig config = new NodeConfig(null, 7, "archive_register", "归档登记", "archive",
                "designated", param, null, null, "none", 24, Boolean.FALSE, Boolean.FALSE,
                Boolean.FALSE, Boolean.FALSE, null);
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.DesignatedRule()
                .resolve(config, initiatorInSection(), directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(206L, 209L);

        directory.resign(209L);
        RuleOutcome afterResign = new com.oa.workflow.approver.app.rules.DesignatedRule()
                .resolve(config, initiatorInSection(), directory);
        assertThat(afterResign.candidates()).extracting(Candidate::userId).containsExactly(206L);
        assertThat(afterResign.evidence()).contains("失效已剔除").contains("209");
    }

    @Test
    @DisplayName("⑦ designated：role_code 取角色下的在职用户；角色为空 → 拦截")
    void designatedByRoleCode() {
        JsonNode param = JsonText.read("{\"role_code\":\"admin\"}");
        NodeConfig config = new NodeConfig(null, 7, "archive_register", "归档登记", "archive",
                "designated", param, null, null, "none", 24, Boolean.FALSE, Boolean.FALSE,
                Boolean.FALSE, Boolean.FALSE, null);
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.DesignatedRule()
                .resolve(config, initiatorInSection(), directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(209L);

        NodeConfig unknownRole = new NodeConfig(null, 7, "archive_register", "归档登记", "archive",
                "designated", JsonText.read("{\"role_code\":\"no_such_role\"}"), null, null, "none",
                24, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, null);
        RuleOutcome empty = new com.oa.workflow.approver.app.rules.DesignatedRule()
                .resolve(unknownRole, initiatorInSection(), directory);
        assertThat(empty.candidates()).isEmpty();
        assertThat(empty.evidence()).contains("no_such_role");
        assertThat(empty.missingConfig()).isNotEmpty();
    }

    @Test
    @DisplayName("⑦ designated：未声明 approver_param → 空候选人 + 指明要配什么")
    void designatedWithoutParam() {
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.DesignatedRule()
                .resolve(node("designated", null), initiatorInSection(), directory);
        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.missingConfig().get(0)).contains("approver_param");
    }

    // ================================================================ ⑧ 发起人自选

    @Test
    @DisplayName("⑧ initiator_pick：取发起时选择的人；未选 → 空候选人（禁止发起，REQ-FLOW-012）")
    void initiatorPick() {
        com.oa.workflow.approver.app.rules.InitiatorPickRule rule =
                new com.oa.workflow.approver.app.rules.InitiatorPickRule();
        RuleRequest picked = initiatorInSection().withPicks(List.of(204L, 203L));
        RuleOutcome outcome = rule.resolve(node("initiator_pick", null), picked, directory);
        assertThat(outcome.candidates()).extracting(Candidate::userId).containsExactly(204L, 203L);

        RuleOutcome empty = rule.resolve(node("initiator_pick", null), initiatorInSection(), directory);
        assertThat(empty.candidates()).isEmpty();
        assertThat(empty.evidence()).contains("未选择任何候选人");
        assertThat(empty.missingConfig()).isNotEmpty();
    }

    // ================================================================ ⑨ 协同部门负责人

    @Test
    @DisplayName("⑨ collab_dept_leader：每个勾选部门一组独立会签；未勾选=合法空组（不拦截）")
    void collabDeptLeaderGroups() {
        com.oa.workflow.approver.app.rules.CollabDeptLeaderRule rule =
                new com.oa.workflow.approver.app.rules.CollabDeptLeaderRule();
        RuleRequest hooked = initiatorInSection().withCollabDepts(List.of(135L, 210L));
        RuleOutcome outcome = rule.resolve(node("collab_dept_leader", null), hooked, directory);
        assertThat(outcome.groups()).hasSize(2);
        assertThat(outcome.groups().get(0)).extracting(Candidate::userId).containsExactly(207L);
        assertThat(outcome.groups().get(1)).extracting(Candidate::userId).containsExactly(206L);
        assertThat(outcome.evidence()).contains("2 组");

        RuleOutcome none = rule.resolve(node("collab_dept_leader", null), initiatorInSection(), directory);
        assertThat(none.groups()).isEmpty();
        assertThat(none.missingConfig()).as("未勾选协同部门是合法状态（prd §6.3）").isEmpty();
    }

    @Test
    @DisplayName("⑨ collab_dept_leader：勾选本节点部门自身（AC-05）→ 拦截")
    void collabDeptLeaderRejectsSelf() {
        RuleRequest hooked = initiatorInSection()
                .withCollabDepts(List.of(210L))
                .withCollabSelfExclude(java.util.Set.of(210L));
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.CollabDeptLeaderRule()
                .resolve(node("collab_dept_leader", null), hooked, directory);
        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.missingConfig().get(0)).contains("AC-05");
    }

    @Test
    @DisplayName("⑨ collab_dept_leader：协同部门未设负责人 → 拦截并指明是哪个部门")
    void collabDeptLeaderMissingLeader() {
        RuleRequest hooked = initiatorInSection().withCollabDepts(List.of(136L));
        RuleOutcome outcome = new com.oa.workflow.approver.app.rules.CollabDeptLeaderRule()
                .resolve(node("collab_dept_leader", null), hooked, directory);
        assertThat(outcome.candidates()).isEmpty();
        assertThat(outcome.missingConfig().get(0)).contains("部门2").contains("未配置正职负责人");
    }

    // ================================================================ 注册表覆盖

    @Test
    @DisplayName("注册表覆盖 enums.md §3 的**全部 9 条**规则，且接受基线短名别名")
    void registryCoversExactlyNineRules() {
        ApproverRuleRegistry registry = new ApproverRuleRegistry(new com.oa.common.config.OaProperties());
        assertThat(registry.codes()).hasSize(9);
        assertThat(registry.codes()).containsExactlyInAnyOrderElementsOf(
                com.oa.workflow.definition.domain.FlowDefinitionEnums.approverRuleCodes());

        assertThat(registry.find("dept-leader")).isPresent();
        assertThat(registry.find("company-exec").map(ApproverResolutionRule::code))
                .contains(ApproverRule.BRANCH_LEADER.code());
        assertThat(registry.find("gm").map(ApproverResolutionRule::code))
                .contains(ApproverRule.SUBSIDIARY_GM.code());
        assertThat(registry.find("collaborating-dept").map(ApproverResolutionRule::code))
                .contains(ApproverRule.COLLAB_DEPT_LEADER.code());
        assertThat(registry.find("no_such_rule")).isEmpty();
    }

    @Test
    @DisplayName("只有 designated 需要 approver_param（initiator_pick / collab_dept_leader 的入参来自运行时）")
    void onlyDesignatedRequiresParam() {
        assertThat(ApproverRule.DESIGNATED.requiresParam()).isTrue();
        assertThat(ApproverRule.INITIATOR_PICK.requiresParam())
                .as("发起人自选由发起时从通讯录选择，设计期不需要 approver_param")
                .isFalse();
        assertThat(ApproverRule.COLLAB_DEPT_LEADER.requiresParam())
                .as("协同部门由②审批人在审批时勾选，设计期不需要 approver_param")
                .isFalse();
        for (ApproverRule rule : List.of(ApproverRule.DEPT_LEADER_UPWARD, ApproverRule.FINANCE_OWNER,
                ApproverRule.BRANCH_LEADER, ApproverRule.SUBSIDIARY_GM, ApproverRule.GROUP_LEADER,
                ApproverRule.CHAIRMAN)) {
            assertThat(rule.requiresParam()).as("%s 不需要参数", rule.code()).isFalse();
        }
        assertThat(ApproverRule.COLLAB_DEPT_LEADER.trunkUsable())
                .as("协同部门不占主链编号").isFalse();
    }

    @Test
    @DisplayName("解析服务：未知规则码（含 enums.md §14 废弃值）→ 空候选人 + 可读纠错文案")
    void unknownRuleIsBlockingWithLegacyHint() {
        ApproverResolutionService service = new ApproverResolutionService(
                new ApproverRuleRegistry(new com.oa.common.config.OaProperties()),
                new com.oa.common.config.OaProperties());
        com.oa.workflow.approver.app.ApproverResolutionService.NodeResolution resolution = service.resolve(
                node("department_leader", null), initiatorInSection(), directory);

        assertThat(resolution.blocker()).isTrue();
        assertThat(resolution.missingConfig().get(0)).contains("废弃值").contains("dept_leader_upward");
    }

    @Test
    @DisplayName("解析服务：同一节点内重复候选人自动去重（prd §5.4）")
    void resolutionDeduplicatesWithinNode() {
        // 一人多岗：207 同时是部门1 的正职与另一个组织节点的正职 → 模拟重复
        directory.primary(138L, 207L);
        ApproverResolutionService service = new ApproverResolutionService(
                new ApproverRuleRegistry(new com.oa.common.config.OaProperties()),
                new com.oa.common.config.OaProperties());
        NodeConfig config = node(ApproverRule.DEPT_LEADER_UPWARD.code(), null);
        com.oa.workflow.approver.app.ApproverResolutionService.NodeResolution resolution = service.resolve(
                config, initiatorInSection(), directory);
        assertThat(resolution.candidates()).extracting(Candidate::userId).containsExactly(207L);
    }

    @Test
    @DisplayName("解析服务：命中跳过条件（不涉及费用）→ 标记 skipped 且**不构成空候选人拦截**（prd §6.1）")
    void resolutionHonoursSkipCondition() {
        ApproverResolutionService service = new ApproverResolutionService(
                new ApproverRuleRegistry(new com.oa.common.config.OaProperties()),
                new com.oa.common.config.OaProperties());
        NodeConfig financeNode = new NodeConfig(2L, 2, "finance_review", "财务部复核", "approve",
                ApproverRule.FINANCE_OWNER.code(), null, "any", null, "optional", 48, Boolean.FALSE,
                Boolean.TRUE, Boolean.FALSE, Boolean.TRUE,
                JsonText.read("{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}"));

        RuleRequest notInvolved = new RuleRequest(208L, 138L, 12L, "/1/12/135/138/", "economy",
                List.of(), List.of(), Map.of("involve_cost", false), java.util.Set.of());
        com.oa.workflow.approver.app.ApproverResolutionService.NodeResolution skipped = service.resolve(
                financeNode, notInvolved, directory);
        assertThat(skipped.skipped()).isTrue();
        assertThat(skipped.blocker()).isFalse();
        assertThat(skipped.skipReason()).contains("skipped").contains("财务部");

        RuleRequest involved = new RuleRequest(208L, 138L, 12L, "/1/12/135/138/", "economy",
                List.of(), List.of(), Map.of("involve_cost", true), java.util.Set.of());
        com.oa.workflow.approver.app.ApproverResolutionService.NodeResolution normal = service.resolve(
                financeNode, involved, directory);
        assertThat(normal.skipped()).isFalse();
        assertThat(normal.candidates()).extracting(Candidate::userId).containsExactly(206L);
    }

    @Test
    @DisplayName("阈值解析随节点解析结果一并给出：会签「3 人中 2 人同意」→ requiredApprovals=2（AC-13）")
    void resolutionResolvesThreshold() {
        directory.primary(12L, 204L);
        ApproverResolutionService service = new ApproverResolutionService(
                new ApproverRuleRegistry(new com.oa.common.config.OaProperties()),
                new com.oa.common.config.OaProperties());
        NodeConfig countersign = new NodeConfig(4L, 4, "subsidiary_gm", "子公司总经理", "approve",
                ApproverRule.SUBSIDIARY_GM.code(), null, DecisionMode.ALL.code(), "2", "optional", 24,
                Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, null);
        com.oa.workflow.approver.app.ApproverResolutionService.NodeResolution resolution = service.resolve(
                countersign, initiatorInSection(), directory);
        assertThat(resolution.requiredApprovals()).isEqualTo(2);
        assertThat(resolution.threshold().basis()).isEqualTo("absolute");
    }
}
