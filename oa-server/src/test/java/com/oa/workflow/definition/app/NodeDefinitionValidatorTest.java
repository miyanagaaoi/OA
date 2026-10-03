package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.SignPolicy;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import com.oa.workflow.definition.domain.FlowGatePolicy;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 节点 / 模板配置校验与发布前 dry-run 单测（2a.2 的验收要求）。
 *
 * <p>覆盖：节点顺序连续、阈值合法（绝对人数优先）、必填节点不可删、
 * 超时 ≥24h、签名策略取值、跳过条件字段存在、Q6/Q7 闸门取值范围、发布前报告逐规则结论。
 */
class NodeDefinitionValidatorTest {

    private final FlowTemplate template = FlowDefinitionFixtures.matterV1();
    private final List<FlowNode> nodes = FlowDefinitionFixtures.matterNodes(1L);

    @Test
    @DisplayName("模板逐字复刻 templates.md §1.1：7 个主干节点、超时 ②=48 其余 24、⑤⑥ 强制签名、② 唯一可跳过")
    void fixturesMatchDocumentedContract() {
        assertThat(nodes).hasSize(7);
        assertThat(nodes).extracting(FlowNode::getNodeCode)
                .containsExactlyElementsOf(NodeCode.trunkCodes());
        assertThat(nodes.get(1).getTimeoutHours()).isEqualTo(48);
        assertThat(nodes.stream().filter(item -> item.getSeq() != 2)
                .map(FlowNode::getTimeoutHours)).allMatch(hours -> hours == 24);
        assertThat(nodes.get(4).getSignPolicy()).isEqualTo(SignPolicy.REQUIRED.code());
        assertThat(nodes.get(5).getSignPolicy()).isEqualTo(SignPolicy.REQUIRED.code());
        assertThat(nodes.get(6).getSignPolicy()).isEqualTo(SignPolicy.NONE.code());
        assertThat(nodes.get(6).getDecisionMode()).isNull();
        assertThat(nodes.get(6).getPassThreshold()).isNull();
        assertThat(nodes.stream().filter(item -> item.getSkipCondition() != null))
                .extracting(FlowNode::getNodeCode).containsExactly(NodeCode.FINANCE_REVIEW.code());
        assertThat(nodes.stream().filter(item -> Boolean.TRUE.equals(item.getAllowRoute())))
                .extracting(FlowNode::getNodeCode)
                .containsExactly(NodeCode.FINANCE_REVIEW.code(), NodeCode.GROUP_LEADER.code(),
                        NodeCode.CHAIRMAN.code());
    }

    @Test
    @DisplayName("合规的 7 节点模板：模板级校验 0 问题，发布前报告 passed=true")
    void validTemplatePasses() {
        assertThat(NodeDefinitionValidator.violations(template, nodes)).isEmpty();
        PrePublishReport report = PrePublishChecker.run(template, nodes);
        assertThat(report.passed()).isTrue();
        assertThat(report.problems()).isEmpty();
        assertThat(report.checks()).hasSize(PrePublishChecker.RULES.size());
        assertThat(report.checks()).filteredOn(check -> !"pass".equals(check.status())).isEmpty();
    }

    @Test
    @DisplayName("节点顺序必须从 1 连续递增（跳号 / 重复被拒）")
    void sequenceMustBeContinuous() {
        List<FlowNode> broken = new ArrayList<>(nodes);
        broken.get(2).setSeq(9);
        List<DefinitionProblem> problems = NodeDefinitionValidator.violations(template, broken);
        assertThat(problems).anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_SEQ));

        List<FlowNode> duplicated = new ArrayList<>(nodes);
        duplicated.get(1).setSeq(1);
        assertThat(NodeDefinitionValidator.violations(template, duplicated))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_SEQ));
    }

    @Test
    @DisplayName("必填节点不可删：缺任一主干节点 / 主干 seq 被改 → 发布前报告失败（R-TRUNK）")
    void requiredNodesCannotBeRemoved() {
        List<FlowNode> missing = new ArrayList<>(nodes);
        missing.remove(4);
        assertThat(NodeDefinitionValidator.violations(template, missing))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_TRUNK)
                        && problem.message().contains("group_leader"));

        FlowNode moved = nodes.get(5);
        int original = moved.getSeq();
        moved.setSeq(4);
        assertThat(NodeDefinitionValidator.violations(template, nodes))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_TRUNK));
        moved.setSeq(original);

        assertThatThrownBy(() -> RequiredNodePolicy.assertDeletable(nodes.get(0)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("主干必填节点不可删除");
        // 非主干节点可删
        RequiredNodePolicy.assertDeletable(FlowDefinitionFixtures.custom(1L, 8, "extra_cc", "额外抄送", "designated"));
    }

    @Test
    @DisplayName("主干节点 seq 必须等于主干序号；非主干节点不得占用 1–7")
    void trunkSequenceIsFixed() {
        FlowNode deptLeader = nodes.get(0);
        deptLeader.setSeq(3);
        assertThat(NodeDefinitionValidator.violations(deptLeader, template))
                .anyMatch(problem -> problem.message().contains("主干节点 seq 必须为 1"));
        deptLeader.setSeq(1);

        FlowNode custom = FlowDefinitionFixtures.custom(1L, 3, "extra_approve", "额外审批", "initiator_pick");
        custom.setNodeCode("extra_approve");
        assertThat(NodeDefinitionValidator.violations(custom, template))
                .anyMatch(problem -> problem.message().contains("不得占用主干序号"));
    }

    @Test
    @DisplayName("阈值必须合法（R-THRESHOLD）；归档登记节点不适用阈值")
    void thresholdValidation() {
        FlowNode countersign = nodes.get(3);
        countersign.setDecisionMode(DecisionMode.ALL.code());
        countersign.setPassThreshold("0");
        assertThat(NodeDefinitionValidator.violations(countersign, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_THRESHOLD));

        countersign.setPassThreshold("2");
        assertThat(NodeDefinitionValidator.violations(countersign, template)).isEmpty();
        countersign.setDecisionMode(DecisionMode.ANY.code());
        countersign.setPassThreshold(null);

        FlowNode archive = nodes.get(6);
        archive.setPassThreshold("2");
        assertThat(NodeDefinitionValidator.violations(archive, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_THRESHOLD));
        archive.setPassThreshold(null);

        FlowNode archiveWithDecision = nodes.get(6);
        archiveWithDecision.setDecisionMode(DecisionMode.ANY.code());
        assertThat(NodeDefinitionValidator.violations(archiveWithDecision, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_DECISION));
        archiveWithDecision.setDecisionMode(null);
    }

    @Test
    @DisplayName("超时 ≥24h（R-TIMEOUT）；开启抄送上级时必须配超时")
    void timeoutValidation() {
        FlowNode node = nodes.get(0);
        node.setTimeoutHours(12);
        assertThat(NodeDefinitionValidator.violations(node, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_TIMEOUT));
        node.setTimeoutHours(24);

        node.setTimeoutCcSuperior(Boolean.TRUE);
        node.setTimeoutHours(null);
        assertThat(NodeDefinitionValidator.violations(node, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_TIMEOUT)
                        && problem.message().contains("抄送上级"));
        node.setTimeoutCcSuperior(Boolean.FALSE);
        node.setTimeoutHours(24);
    }

    @Test
    @DisplayName("签名策略取值合法（R-SIGN）；归档登记节点不可强制签名")
    void signPolicyValidation() {
        FlowNode node = nodes.get(0);
        node.setSignPolicy("must");
        assertThat(NodeDefinitionValidator.violations(node, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_SIGN));
        node.setSignPolicy(SignPolicy.OPTIONAL.code());

        FlowNode archive = nodes.get(6);
        archive.setSignPolicy(SignPolicy.REQUIRED.code());
        assertThat(NodeDefinitionValidator.violations(archive, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_SIGN));
        archive.setSignPolicy(SignPolicy.NONE.code());
    }

    @Test
    @DisplayName("跳过条件：只能配在事项单②（R-SKIP）；字段必须存在于表单模板；操作符白名单")
    void skipConditionValidation() {
        FlowNode node = nodes.get(2);
        node.setSkipCondition("{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}");
        assertThat(NodeDefinitionValidator.violations(node, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_SKIP)
                        && problem.message().contains("只能配置在②财务部复核节点"));
        node.setSkipCondition(null);

        FlowNode finance = nodes.get(1);
        String original = finance.getSkipCondition();
        finance.setSkipCondition("{\"field\":\"no_such_field\",\"op\":\"eq\",\"value\":false}");
        assertThat(NodeDefinitionValidator.violations(finance, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_SKIP)
                        && problem.message().contains("不在表单模板字段清单内"));
        finance.setSkipCondition(original);

        // 非 matter 模板不得有跳过条件（templates.md §1.5）
        FlowTemplate fund = FlowDefinitionFixtures.matter(2L, 1, "published");
        fund.setCode("fund");
        fund.setFormType("fund");
        assertThat(NodeDefinitionValidator.violations(finance, fund))
                .anyMatch(problem -> problem.message().contains("仅事项审批单"));
    }

    @Test
    @DisplayName("解析规则必填且取值合法（R-APPROVER-RULE）；designated 必须给 user_ids 或 role_code")
    void approverRuleValidation() {
        FlowNode node = nodes.get(0);
        node.setApproverRule("department_leader");
        assertThat(NodeDefinitionValidator.violations(node, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_APPROVER_RULE)
                        && problem.message().contains("废弃值"));
        node.setApproverRule("dept_leader_upward");

        FlowNode designated = FlowDefinitionFixtures.custom(1L, 8, "extra", "额外审批", "designated");
        designated.setApproverParam(null);
        assertThat(NodeDefinitionValidator.violations(designated, template))
                .anyMatch(problem -> problem.message().contains("必须给出 approver_param"));

        designated.setApproverParam("{\"user_ids\":[1],\"role_code\":\"admin\"}");
        assertThat(NodeDefinitionValidator.violations(designated, template))
                .anyMatch(problem -> problem.message().contains("不得同时给出"));

        designated.setApproverParam("{\"role_code\":\"admin\"}");
        assertThat(NodeDefinitionValidator.violations(designated, template)).isEmpty();

        FlowNode trunkWithCollabRule = nodes.get(2);
        String original = trunkWithCollabRule.getApproverRule();
        trunkWithCollabRule.setApproverRule("collab_dept_leader");
        assertThat(NodeDefinitionValidator.violations(trunkWithCollabRule, template))
                .anyMatch(problem -> problem.message().contains("只用于②的并行子任务组"));
        trunkWithCollabRule.setApproverRule(original);
    }

    @Test
    @DisplayName("节点类型：二期条件节点（condition）一律拒绝（prd §6.1 一期无条件分支）")
    void conditionNodeRejected() {
        FlowNode node = FlowDefinitionFixtures.custom(1L, 8, "cond", "条件节点", "initiator_pick");
        node.setNodeType("condition");
        assertThat(NodeDefinitionValidator.violations(node, template))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_NODE_TYPE));
    }

    @Test
    @DisplayName("R-GATE：Q6/Q7 闸门取值范围进入发布前报告（负次数 / 天数 0 / 越界）")
    void gatePolicyInReport() {
        FlowTemplate broken = FlowDefinitionFixtures.matter(1L, 2, "draft");
        broken.applyGatePolicy(new FlowGatePolicy(-1, 100, 0, DeadlineType.WORKING, TimeoutAction.NOTIFY));

        PrePublishReport report = PrePublishChecker.run(broken, FlowDefinitionFixtures.matterNodes(1L));
        assertThat(report.passed()).isFalse();
        assertThat(report.checks()).filteredOn(check -> check.rule().equals(PrePublishChecker.R_GATE))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("fail");
                    assertThat(check.details()).hasSize(3);
                });
    }

    @Test
    @DisplayName("warnings：未配置 Q6/Q7 → 提示「次数不限、不设时限」；开启自由跳转 → 提示 AC-46")
    void warningsAreInformational() {
        FlowTemplate unlimited = FlowDefinitionFixtures.unlimitedGateTemplate(1L, 2, "draft");
        assertThat(PrePublishChecker.warnings(unlimited, FlowDefinitionFixtures.matterNodes(1L)))
                .anyMatch(warning -> warning.contains("Q6/Q7 未配置"));

        List<FlowNode> withJump = new ArrayList<>(FlowDefinitionFixtures.matterNodes(1L));
        withJump.get(4).setAllowJump(Boolean.TRUE);
        PrePublishReport report = PrePublishChecker.run(template, withJump);
        assertThat(report.passed()).as("自由跳转是提示项，不阻止发布").isTrue();
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("AC-46"));
    }

    @Test
    @DisplayName("node_count 与节点数不一致 → R-METADATA 报错（发布时回写）")
    void nodeCountConsistency() {
        FlowTemplate wrong = FlowDefinitionFixtures.matter(1L, 2, "draft");
        wrong.setNodeCount(6);
        assertThat(NodeDefinitionValidator.violations(wrong, FlowDefinitionFixtures.matterNodes(1L)))
                .anyMatch(problem -> problem.rule().equals(PrePublishChecker.R_METADATA)
                        && problem.message().contains("node_count"));
    }

    @Test
    @DisplayName("写接口即时校验与发布前报告**同一套规则**（能存进去的一定能发布）")
    void writePathAndPublishPathShareRules() {
        FlowNode broken = nodes.get(0);
        broken.setTimeoutHours(1);
        assertThatThrownBy(() -> NodeDefinitionValidator.assertNode(broken, template))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("timeout_hours");
        broken.setTimeoutHours(24);

        assertThatThrownBy(() -> NodeDefinitionValidator.assertTemplate(template, new ArrayList<>(List.of())))
                .isInstanceOf(BizException.class);
    }
}
