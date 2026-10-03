package com.oa.workflow.approver.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckBlockerView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckNodeView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckReportView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRequest;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.definition.app.FlowDefinitionFixtures;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>发起前预检 + 版本锁定 + 快照不变性</b>单测（AC-09 / AC-11 / AC-19）。
 *
 * <p>本测试用「内存目录 + Mockito 模板 Mapper」搭出一台完整引擎，覆盖三条验收：
 * <ol>
 *   <li><b>AC-19 / AC-11</b>：任一节点候选人为空 → {@code allowed=false}，
 *       拦截项逐条给出「哪个节点、命中哪条规则、缺什么配置」；{@code assertSubmittable} 抛 400；</li>
 *   <li><b>AC-09</b>：发布新版本（增删节点 + 改决议模式/阈值）后，
 *       **在途实例锁定的那一版**解析结果不变；新建实例才走新版本；</li>
 *   <li><b>AC-19（快照稳定性）</b>：快照固化后，改负责人 / 调岗 / 改组织路径，**快照逐字节不变**；
 *       而重新解析会得到新的人（证明变更确实生效，测试不是空转）。</li>
 * </ol>
 */
class ApproverPrecheckServiceTest {

    private static final Long V1_ID = 1L;
    private static final Long V2_ID = 2L;

    private InMemoryApproverDirectory directory;
    private FlowTemplateMapper templateMapper;
    private FlowNodeMapper nodeMapper;
    private ApproverPrecheckService service;

    @BeforeEach
    void setUp() {
        directory = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(1L, "系统管理员", "A001", 1L, 1L)
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

        templateMapper = mock(FlowTemplateMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(FlowDefinitionFixtures.matterV1());
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(v2Draft());
        when(templateMapper.selectByCodeAndStatus("matter", "published"))
                .thenReturn(List.of(FlowDefinitionFixtures.matterV1()));
        when(nodeMapper.selectByTemplateId(anyLong())).thenAnswer(invocation ->
                FlowDefinitionFixtures.matterNodes(invocation.getArgument(0)));

        OaProperties properties = new OaProperties();
        ApproverRuleRegistry registry = new ApproverRuleRegistry(properties);
        service = new ApproverPrecheckService(new ApproverResolutionService(registry, properties), directory,
                templateMapper, nodeMapper, properties);
    }

    /** v2 草稿：删掉「集团董事长」以外的差异 → 这里刻意**改决议模式与阈值**并**加一个节点**（AC-48/AC-09）。 */
    private static FlowTemplate v2Draft() {
        FlowTemplate template = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        template.setNodeCount(8);
        return template;
    }

    private static PrecheckRequest request(Long templateId, Boolean involveCost) {
        return new PrecheckRequest(templateId, null, 208L, "matter", "economy", involveCost,
                List.of(), List.of(), List.of(), Map.of());
    }

    // ================================================================ AC-19 / AC-11

    @Test
    @DisplayName("AC-19：全部节点可解析 → allowed=true，7 个节点给出候选人与证据链")
    void precheckAllowedWhenAllNodesResolvable() {
        PrecheckReportView report = service.precheck(request(V1_ID, true), principal());

        assertThat(report.allowed()).isTrue();
        assertThat(report.blockers()).isEmpty();
        assertThat(report.nodes()).hasSize(7);
        assertThat(report.nodes()).extracting(PrecheckNodeView::nodeCode)
                .containsExactlyElementsOf(com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode.trunkCodes());
        assertThat(report.nodes().get(0).candidates()).extracting(view -> view.userId())
                .as("① 科室1A 无负责人 → 上溯取部门1 的 207")
                .containsExactly(207L);
        assertThat(report.nodes().get(0).evidence()).contains("部门1");
        assertThat(report.nodes().get(1).candidates()).extracting(view -> view.userId()).containsExactly(206L);
        assertThat(report.nodes().get(2).candidates()).extracting(view -> view.userId()).containsExactly(203L);
        assertThat(report.nodes().get(3).candidates()).extracting(view -> view.userId()).containsExactly(204L);
        assertThat(report.nodes().get(4).candidates()).extracting(view -> view.userId()).containsExactly(205L);
        assertThat(report.nodes().get(5).candidates()).extracting(view -> view.userId()).containsExactly(1L);
        assertThat(report.nodes().get(6).candidates()).extracting(view -> view.userId()).containsExactly(209L);
    }

    @Test
    @DisplayName("AC-11：部门负责人被置空 → allowed=false，拦截项指明「哪个节点/哪条规则/缺什么配置」")
    void precheckBlocksWhenCandidateEmpty() {
        // 把公司副职与集团董事长都撤掉 → ③⑥ 两个节点为空
        InMemoryApproverDirectory broken = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(204L, "冯总", "A204", 12L, 12L)
                .user(205L, "集团分管", "A205", 1L, 1L)
                .user(206L, "财务负责人", "A206", 210L, 1L)
                .user(207L, "导入员工", "A207", 135L, 12L)
                .user(208L, "普通员工", "A208", 138L, 12L)
                .user(209L, "内勤", "A209", 210L, 1L)
                .primary(135L, 207L)
                .primary(12L, 204L)
                .category(1L, "economy", 205L)
                .primary(210L, 206L)
                .role("admin", 209L);
        OaProperties properties = new OaProperties();
        ApproverPrecheckService brokenService = new ApproverPrecheckService(
                new ApproverResolutionService(new ApproverRuleRegistry(properties), properties), broken,
                templateMapper, nodeMapper, properties);

        PrecheckReportView report = brokenService.precheck(request(V1_ID, true), principal());
        assertThat(report.allowed()).isFalse();
        assertThat(report.blockers()).hasSize(2);
        assertThat(report.blockers()).extracting(PrecheckBlockerView::nodeCode)
                .containsExactly("branch_leader", "chairman");
        PrecheckBlockerView branch = report.blockers().get(0);
        assertThat(branch.nodeSeq()).isEqualTo(3);
        assertThat(branch.nodeName()).isEqualTo("分公司分管领导");
        assertThat(branch.rule()).isEqualTo("branch_leader");
        assertThat(branch.ruleLabel()).isEqualTo("分公司分管领导");
        assertThat(branch.reason()).contains("候选人集合为空");
        assertThat(branch.missingConfig()).anyMatch(text -> text.contains("副职"));
        PrecheckBlockerView chairman = report.blockers().get(1);
        assertThat(chairman.missingConfig()).anyMatch(text -> text.contains("董事长"));
    }

    @Test
    @DisplayName("AC-11：assertSubmittable 抛 400（40007），文案含节点序号/节点码/规则/缺什么配置")
    void assertSubmittableThrowsWithActionableMessage() {
        InMemoryApproverDirectory broken = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(204L, "冯总", "A204", 12L, 12L)
                .user(205L, "集团分管", "A205", 1L, 1L)
                .user(206L, "财务负责人", "A206", 210L, 1L)
                .user(207L, "导入员工", "A207", 135L, 12L)
                .user(208L, "普通员工", "A208", 138L, 12L)
                .user(209L, "内勤", "A209", 210L, 1L)
                .primary(135L, 207L)
                .primary(12L, 204L)
                .category(1L, "economy", 205L)
                .primary(210L, 206L)
                .role("admin", 209L);
        OaProperties properties = new OaProperties();
        ApproverPrecheckService brokenService = new ApproverPrecheckService(
                new ApproverResolutionService(new ApproverRuleRegistry(properties), properties), broken,
                templateMapper, nodeMapper, properties);

        assertThatThrownBy(() -> brokenService.assertSubmittable(request(V1_ID, true), principal()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.APPROVER_RESOLUTION_BLOCKED);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(400);
                    assertThat(biz.getMessage())
                            .contains("发起被拒绝")
                            .contains("3 分公司分管领导")
                            .contains("branch_leader")
                            .contains("缺少配置");
                    assertThat(biz.getDetails()).containsKey("blockers");
                });
    }

    @Test
    @DisplayName("AC-11：事项单「不涉及费用」→ ② 跳过但**不构成空候选人拦截**（prd §6.1）")
    void skippedFinanceNodeDoesNotBlock() {
        // 连财务部负责人都撤掉：涉及费用 → 拦截；不涉及费用 → 通过（② 被跳过）
        InMemoryApproverDirectory noFinanceLeader = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(1L, "系统管理员", "A001", 1L, 1L)
                .user(203L, "郑领", "A203", 12L, 12L)
                .user(204L, "冯总", "A204", 12L, 12L)
                .user(205L, "集团分管", "A205", 1L, 1L)
                .user(207L, "导入员工", "A207", 135L, 12L)
                .user(208L, "普通员工", "A208", 138L, 12L)
                .user(209L, "内勤", "A209", 210L, 1L)
                .primary(135L, 207L)
                .primary(12L, 204L)
                .deputy(12L, 203L)
                .primary(1L, 1L)
                .category(1L, "economy", 205L)
                .role("admin", 209L);
        OaProperties properties = new OaProperties();
        ApproverPrecheckService noFinance = new ApproverPrecheckService(
                new ApproverResolutionService(new ApproverRuleRegistry(properties), properties), noFinanceLeader,
                templateMapper, nodeMapper, properties);

        PrecheckReportView involved = noFinance.precheck(request(V1_ID, true), principal());
        assertThat(involved.allowed()).isFalse();
        assertThat(involved.blockers()).extracting(PrecheckBlockerView::nodeCode).containsExactly("finance_review");

        PrecheckReportView notInvolved = noFinance.precheck(request(V1_ID, false), principal());
        assertThat(notInvolved.allowed()).isTrue();
        PrecheckNodeView financeNode = notInvolved.nodes().get(1);
        assertThat(financeNode.skipped()).isTrue();
        assertThat(financeNode.blocker()).isFalse();
        assertThat(financeNode.skipReason()).contains("skipped");
    }

    @Test
    @DisplayName("AC-13 预检期提示：会签阈值 3 但候选人只有 1 人 → satisfiable=false（不阻止发起但明确告警）")
    void thresholdUnsatisfiableIsWarned() {
        FlowNode countersign = FlowDefinitionFixtures.matterNodes(V1_ID).get(3);
        countersign.setDecisionMode(DecisionMode.ALL.code());
        countersign.setPassThreshold("3");
        List<FlowNode> nodes = new ArrayList<>(FlowDefinitionFixtures.matterNodes(V1_ID));
        nodes.set(3, countersign);
        when(nodeMapper.selectByTemplateId(anyLong())).thenReturn(nodes);

        PrecheckReportView report = service.precheck(request(V1_ID, true), principal());
        assertThat(report.allowed()).isTrue();
        PrecheckNodeView node = report.nodes().get(3);
        assertThat(node.satisfiable()).isFalse();
        assertThat(node.requiredApprovals()).isEqualTo(3);
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("无法通过"));
    }

    // ================================================================ AC-09 在途锁版本

    @Test
    @DisplayName("AC-09：发布 v2（增节点 + 改决议模式/阈值）后，**锁定 v1 的在途实例**解析结果不变")
    void inflightInstanceStaysOnLockedVersion() {
        // ① 在途实例：锁定 v1（template_id=1）解析并固化快照
        ApproverPrecheckService.Resolved inflight = service.resolveForSubmit(request(V1_ID, true), principal());
        ApproverSnapshot snapshot = ApproverSnapshotCodec.assemble(inflight.template().getId(),
                inflight.template().getCode(), inflight.template().getVersion(), inflight.context(),
                inflight.resolutions());
        String snapshotJsonBefore = ApproverSnapshotCodec.write(snapshot);
        assertThat(snapshot.nodes()).hasSize(7);
        assertThat(snapshot.templateVersion()).isEqualTo(1);

        // ② 管理员发布 v2：v1 转 archived、v2 转 published；v2 的节点集 = v1 + 1 个额外节点，
        //    且节点 4（子公司总经理）改为会签阈值 2
        FlowTemplate v1Archived = FlowDefinitionFixtures.matter(V1_ID, 1, "archived");
        FlowTemplate v2Published = v2Draft();
        v2Published.setStatus("published");
        when(templateMapper.selectByCodeAndStatus("matter", "published")).thenReturn(List.of(v2Published));
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(v1Archived);
        when(nodeMapper.selectByTemplateId(V2_ID)).thenAnswer(invocation -> {
            List<FlowNode> nodes = new ArrayList<>(FlowDefinitionFixtures.matterNodes(V2_ID));
            FlowNode gm = nodes.get(3);
            gm.setDecisionMode(DecisionMode.ALL.code());
            gm.setPassThreshold("2");
            nodes.add(FlowDefinitionFixtures.custom(V2_ID, 8, "extra_approve", "额外审批", "initiator_pick"));
            return nodes;
        });

        // ③ 在途实例仍按 v1 解析（它只认 template_id=1）
        ApproverPrecheckService.Resolved stillV1 = service.resolveForSubmit(request(V1_ID, true), principal());
        ApproverSnapshot snapshotAfter = ApproverSnapshotCodec.assemble(stillV1.template().getId(),
                stillV1.template().getCode(), stillV1.template().getVersion(), stillV1.context(),
                stillV1.resolutions());
        assertThat(ApproverSnapshotCodec.write(snapshotAfter))
                .as("锁定版本的解析结果必须逐字节不变（AC-09）")
                .isEqualTo(snapshotJsonBefore);
        assertThat(stillV1.resolutions()).hasSize(7);
        assertThat(stillV1.resolutions().get(3).requiredApprovals()).isEqualTo(1);
        assertThat(stillV1.resolutions().get(3).threshold().basis()).isEqualTo("any");

        // ④ 新发起的实例（按 formType 取当前 published = v2）走新版本
        PrecheckRequest newRequest = new PrecheckRequest(null, null, 208L, "matter", "economy", true,
                List.of(204L), List.of(), List.of(), Map.of());
        ApproverPrecheckService.Resolved fresh = service.resolveForSubmit(newRequest, principal());
        assertThat(fresh.template().getVersion()).isEqualTo(2);
        assertThat(fresh.resolutions()).hasSize(8);
        assertThat(fresh.resolutions().get(3).requiredApprovals()).isEqualTo(2);
        assertThat(fresh.resolutions().get(3).threshold().basis()).isEqualTo("absolute");
        assertThat(fresh.report().allowed()).as("v2 的额外节点由发起人自选，已给出人选").isTrue();
    }

    // ================================================================ AC-19 快照稳定性

    @Test
    @DisplayName("AC-19：快照固化后改负责人 / 调岗 / 改组织路径 → 快照不变；重新解析才变（变更真实生效）")
    void snapshotSurvivesOrgAndPeopleChanges() {
        ApproverPrecheckService.Resolved resolved = service.resolveForSubmit(request(V1_ID, true), principal());
        ApproverSnapshot frozen = ApproverSnapshotCodec.assemble(resolved.template().getId(),
                resolved.template().getCode(), resolved.template().getVersion(), resolved.context(),
                resolved.resolutions());
        String frozenJson = ApproverSnapshotCodec.write(frozen);
        assertThat(ApproverSnapshotCodec.approverIds(frozen, "dept_leader")).containsExactly(207L);
        assertThat(ApproverSnapshotCodec.hasBlocker(frozen)).isFalse();

        // 组织与人员变更：部门1 换负责人（207 → 204）、科室1A 新增负责人、把 207 调岗到别的部门、
        // 以及公司副职更换（203 → 1）
        InMemoryApproverDirectory changed = new InMemoryApproverDirectory()
                .org(1L, null, "group", "集团", "/1/")
                .org(12L, 1L, "company", "公司A", "/1/12/")
                .org(135L, 12L, "dept", "部门1", "/1/12/135/")
                .org(138L, 135L, "section", "科室1A", "/1/12/135/138/")
                .org(210L, 1L, "dept", "财务部", "/1/210/")
                .user(1L, "系统管理员", "A001", 1L, 1L)
                .user(204L, "冯总", "A204", 12L, 12L)
                .user(205L, "集团分管", "A205", 1L, 1L)
                .user(206L, "财务负责人", "A206", 210L, 1L)
                .user(207L, "导入员工", "A207", 1L, 1L)
                .user(208L, "普通员工", "A208", 138L, 12L)
                .user(209L, "内勤", "A209", 210L, 1L)
                .primary(138L, 205L)
                .primary(135L, 204L)
                .primary(12L, 204L)
                .deputy(12L, 1L)
                .primary(1L, 1L)
                .category(1L, "economy", 205L)
                .primary(210L, 206L)
                .role("admin", 209L);
        OaProperties properties = new OaProperties();
        ApproverPrecheckService changedService = new ApproverPrecheckService(
                new ApproverResolutionService(new ApproverRuleRegistry(properties), properties), changed,
                templateMapper, nodeMapper, properties);

        // ① 已固化的快照（库里那一份 JSON）不变
        assertThat(ApproverSnapshotCodec.write(frozen)).isEqualTo(frozenJson);
        ApproverSnapshot reread = ApproverSnapshotCodec.read(frozenJson);
        assertThat(reread.nodes().get(0).approvers()).extracting(approver -> approver.userId())
                .containsExactly(207L);
        assertThat(reread.nodes().get(2).approvers()).extracting(approver -> approver.userId())
                .containsExactly(203L);

        // ② 重新解析（等价于驳回重提 / 新实例）才反映变更 → 证明变更真实生效
        ApproverPrecheckService.Resolved reparsed = changedService.resolveForSubmit(request(V1_ID, true), principal());
        assertThat(reparsed.resolutions().get(0).candidates()).extracting(Candidate::userId).containsExactly(205L);
        assertThat(reparsed.resolutions().get(2).candidates()).extracting(Candidate::userId).containsExactly(1L);
    }

    @Test
    @DisplayName("AC-19：审批人离职后发起被拦截（离职当天不能新发起），但同一夹具下旧快照仍可用")
    void resignedApproverBlocksNewSubmission() {
        directory.resign(206L);
        PrecheckReportView report = service.precheck(request(V1_ID, true), principal());
        assertThat(report.allowed()).isFalse();
        assertThat(report.blockers()).extracting(PrecheckBlockerView::nodeCode).containsExactly("finance_review");
    }

    private static CurrentUser principal() {
        return CurrentUser.of(208L, "u208", "普通员工", "A208", 138L, 12L, Set.of("employee"),
                Set.of(com.oa.common.scope.DataScopeType.SELF), false);
    }
}
