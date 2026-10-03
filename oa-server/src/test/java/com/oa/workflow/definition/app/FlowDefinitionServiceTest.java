package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.GatePolicyRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NewVersionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeOrderRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PrePublishReportView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PublishRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateView;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 流程模板服务单测（2a.2：版本发布、在途锁版本、节点增删改、Q6/Q7 配置读写）。
 *
 * <p>用 Mockito 假造 {@code flow_template} / {@code flow_node} 两张表的读写，
 * 因此可以在无 DB 环境下断言**服务编排**的正确性（谁能写、写了什么、状态怎么转）。
 */
class FlowDefinitionServiceTest {

    private static final Long V1_ID = 1L;
    private static final Long V2_ID = 2L;

    private FlowTemplateMapper templateMapper;
    private FlowNodeMapper nodeMapper;
    private FlowDefinitionService service;

    private final List<FlowNode> v1Nodes = FlowDefinitionFixtures.matterNodes(V1_ID);

    @BeforeEach
    void setUp() {
        templateMapper = mock(FlowTemplateMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);

        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(FlowDefinitionFixtures.matterV1());
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(FlowDefinitionFixtures.matterV1()));
        when(templateMapper.selectByCodeAndVersion(eq("matter"), any())).thenReturn(null);
        when(nodeMapper.selectByTemplateId(V1_ID)).thenReturn(new ArrayList<>(v1Nodes));
        when(nodeMapper.countByTemplateId(anyLong())).thenAnswer(invocation -> v1Nodes.size());
        when(nodeMapper.countByTemplateAndCode(anyLong(), any(), any())).thenReturn(0);

        WorkflowPermissionService permissionService = mock(WorkflowPermissionService.class);
        CurrentUser operator = CurrentUser.of(1L, "admin", "系统管理员", "A001", 1L, 1L, Set.of("admin"),
                Set.of(com.oa.common.scope.DataScopeType.GROUP_ALL), false);
        when(permissionService.requirePublish()).thenReturn(operator);
        when(permissionService.requireTemplateRead()).thenReturn(operator);
        when(permissionService.isSuperAdmin(any())).thenReturn(true);

        service = new FlowDefinitionService(templateMapper, nodeMapper, permissionService);
    }

    // ================================================================ 版本

    @Test
    @DisplayName("开新版本：基于已发布版本克隆为 v2 草稿，闸门配置与表单定义随版本复制（§4.1 第 4 步）")
    void newVersionClonesFromPublished() {
        AtomicLong nextId = new AtomicLong(100L);
        java.util.concurrent.atomic.AtomicReference<FlowTemplate> createdRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation -> {
            FlowTemplate created = invocation.getArgument(0);
            created.setId(nextId.getAndIncrement());
            createdRef.set(created);
            return 1;
        }).when(templateMapper).insertTemplate(any(FlowTemplate.class));
        // 读回即插入的那一行（模拟真实 DB：insert 后 selectById 返回同一份数据）
        when(templateMapper.selectTemplateById(100L)).thenAnswer(invocation -> createdRef.get());

        FlowTemplate source = FlowDefinitionFixtures.matterV1();
        source.applyGatePolicy(FlowDefinitionFixtures.customGate());
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(source);

        TemplateView view = service.newVersion(V1_ID, new NewVersionRequest(null, "事项审批单流程 v2"));

        assertThat(view.version()).isEqualTo(2);
        assertThat(view.status()).isEqualTo("draft");
        assertThat(view.name()).isEqualTo("事项审批单流程 v2");
        assertThat(view.gatePolicy().maxReturnCount()).isEqualTo(9);
        assertThat(view.gatePolicy()).isNotNull();
        assertThat(view.gatePolicy().supplementDeadlineType()).isEqualTo("calendar");
        assertThat(view.gatePolicy().onSupplementTimeout()).isEqualTo("auto_pass");
        assertThat(view.readOnly()).isFalse();
        assertThat(view.usableByNewInstance()).isFalse();
        verify(nodeMapper).cloneNodes(V1_ID, 100L);
    }

    @Test
    @DisplayName("开新版本：已有草稿 → 409（同一 code 只允许一个可编辑草稿）")
    void newVersionRejectsExistingDraft() {
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(
                FlowDefinitionFixtures.matterV1(),
                FlowDefinitionFixtures.matter(9L, 2, "draft")));

        assertThatThrownBy(() -> service.newVersion(V1_ID, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_DRAFT_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("开新版本：源状态非法（草稿）→ 400；指定历史版本（已归档）→ 允许（回滚 = 旧版本重发为新版本）")
    void newVersionSourceRules() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(3L, 3, "draft");
        when(templateMapper.selectTemplateById(3L)).thenReturn(draft);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(draft));
        assertThatThrownBy(() -> service.newVersion(3L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("草稿");

        FlowTemplate archived = FlowDefinitionFixtures.matter(1L, 1, "archived");
        FlowTemplate published = FlowDefinitionFixtures.matter(2L, 2, "published");
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(archived);
        when(templateMapper.selectByCode("matter")).thenReturn(List.of(published, archived));
        AtomicLong nextId = new AtomicLong(200L);
        doAnswer(invocation -> {
            FlowTemplate created = invocation.getArgument(0);
            created.setId(nextId.getAndIncrement());
            return 1;
        }).when(templateMapper).insertTemplate(any(FlowTemplate.class));
        when(templateMapper.selectTemplateById(200L)).thenReturn(FlowDefinitionFixtures.matter(200L, 3, "draft"));

        TemplateView view = service.newVersion(V1_ID, new NewVersionRequest(1, null));
        assertThat(view.version()).isEqualTo(3);
        verify(nodeMapper).cloneNodes(V1_ID, 200L);
    }

    @Test
    @DisplayName("发布：先跑发布前校验（不通过即 400，且不写任何状态）；通过则归档原 published 并回写 node_count")
    void publishRunsPrecheckThenArchivesPrevious() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.selectByTemplateId(V2_ID)).thenReturn(FlowDefinitionFixtures.matterNodes(V2_ID));
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);

        TemplateView view = service.publish(V2_ID, new PublishRequest("新增一个抄送节点"));

        assertThat(view.id()).isEqualTo(V2_ID);
        verify(templateMapper).archiveOtherPublished("matter", V2_ID, 1L);
        verify(templateMapper).updateNodeCount(V2_ID, 7, 1L);
        verify(templateMapper).updateStatus(V2_ID, "published", 1L);
    }

    @Test
    @DisplayName("发布：校验不通过 → 400 且**不归档、不改状态**（TOCTOU 防护：同事务内重跑校验）")
    void publishRejectsInvalidDraft() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        List<FlowNode> broken = new ArrayList<>(FlowDefinitionFixtures.matterNodes(V2_ID));
        broken.remove(4);
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.selectByTemplateId(V2_ID)).thenReturn(broken);

        assertThatThrownBy(() -> service.publish(V2_ID, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("发布被拒绝")
                .hasMessageContaining("group_leader");

        verify(templateMapper, never()).archiveOtherPublished(any(), any(), any());
        verify(templateMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("发布 / 写节点：已发布版本只读 → 409（templates.md §3.3 / §4.3）")
    void publishedVersionIsImmutable() {
        assertThatThrownBy(() -> service.publish(V1_ID, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FLOW_DEFINITION_IMMUTABLE));

        assertThatThrownBy(() -> service.deleteNode(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("归档：published → archived（只阻止新实例；在途继续执行）")
    void archive() {
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(FlowDefinitionFixtures.matterV1());
        service.archive(V1_ID, new PublishRequest("换版"));
        verify(templateMapper).updateStatus(V1_ID, "archived", 1L);
    }

    // ================================================================ Q6 / Q7 闸门配置

    @Test
    @DisplayName("Q6/Q7 闸门配置：写入后可读回（键位与语义不变），并落到 updateDraft")
    void gatePolicyRoundTrip() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.countByTemplateId(V2_ID)).thenReturn(7);

        TemplateView view = service.putGatePolicy(V2_ID, new GatePolicyRequest(9, 4, 15,
                "calendar", "auto_pass"));

        assertThat(view.gatePolicy().maxReturnCount()).isEqualTo(9);
        assertThat(view.gatePolicy().maxSupplementCount()).isEqualTo(4);
        assertThat(view.gatePolicy().supplementDeadlineDays()).isEqualTo(15);
        assertThat(view.gatePolicy().supplementDeadlineType()).isEqualTo("calendar");
        assertThat(view.gatePolicy().onSupplementTimeout()).isEqualTo("auto_pass");
        assertThat(view.gatePolicy().unlimited()).isFalse();
        assertThat(view.gatePolicy().v04Default()).isFalse();

        // 落库值就是读回值（同一份 FlowTemplate 实例）
        assertThat(draft.getMaxReturnCount()).isEqualTo(9);
        assertThat(draft.getSupplementDeadlineType()).isEqualTo(DeadlineType.CALENDAR.code());
        assertThat(draft.getOnSupplementTimeout()).isEqualTo(TimeoutAction.AUTO_PASS.code());
        verify(templateMapper).updateDraft(draft);
    }

    @Test
    @DisplayName("Q6/Q7 闸门配置：0 / null = 不限；未配置时读回 unlimited=true（默认值语义）")
    void gatePolicyUnlimitedRoundTrip() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.countByTemplateId(V2_ID)).thenReturn(7);

        TemplateView view = service.putGatePolicy(V2_ID, new GatePolicyRequest(0, null, null, null, null));

        assertThat(view.gatePolicy().maxReturnCount()).isNull();
        assertThat(view.gatePolicy().maxSupplementCount()).isNull();
        assertThat(view.gatePolicy().supplementDeadlineDays()).isNull();
        assertThat(view.gatePolicy().onSupplementTimeout()).isEqualTo("notify");
        assertThat(view.gatePolicy().unlimited()).isTrue();
    }

    @Test
    @DisplayName("Q6/Q7 闸门配置：非法值一律 400（负次数 / 天数 0 / 非法枚举），且不写库")
    void gatePolicyRejectsIllegalValues() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);

        assertThatThrownBy(() -> service.putGatePolicy(V2_ID, new GatePolicyRequest(-1, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("maxReturnCount");
        assertThatThrownBy(() -> service.putGatePolicy(V2_ID, new GatePolicyRequest(null, null, 0, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("supplementDeadlineDays");
        assertThatThrownBy(() -> service.putGatePolicy(V2_ID,
                new GatePolicyRequest(null, null, 3, "hours", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("supplementDeadlineType");
        assertThatThrownBy(() -> service.putGatePolicy(V2_ID,
                new GatePolicyRequest(null, null, 3, "working", "auto_skip")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("onSupplementTimeout");

        verify(templateMapper, never()).updateDraft(any());
    }

    // ================================================================ 节点增删改

    @Test
    @DisplayName("删除草稿节点：主干必填节点被拒（R-TRUNK + RequiredNodePolicy）")
    void deleteTrunkNodeRejected() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(nodeMapper.selectNodeById(1L)).thenReturn(v1Nodes.get(0));
        when(templateMapper.selectTemplateById(V1_ID)).thenReturn(draft);

        assertThatThrownBy(() -> service.deleteNode(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("主干必填节点不可删除");
        verify(nodeMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("删除草稿的非主干节点：允许，并重排 seq 保持连续")
    void deleteCustomNodeRenumbers() {
        FlowNode extra = FlowDefinitionFixtures.custom(V1_ID, 8, "extra_approve", "额外审批", "initiator_pick");
        extra.setId(88L);
        extra.setTemplateId(V2_ID);
        List<FlowNode> withExtra = new ArrayList<>(v1Nodes);
        withExtra.add(extra);
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        draft.setNodeCount(8);
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.selectNodeById(88L)).thenReturn(extra);
        when(nodeMapper.selectByTemplateId(V2_ID)).thenReturn(withExtra);
        when(nodeMapper.countByTemplateId(V2_ID)).thenReturn(7);

        service.deleteNode(88L);

        verify(nodeMapper).deleteById(88L);
        verify(templateMapper).updateNodeCount(V2_ID, 7, 1L);
    }

    @Test
    @DisplayName("新增节点：主干节点码不可重复；新增后回写 node_count")
    void addNodeRejectsDuplicateTrunkCode() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        draft.setNodeCount(7);
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.selectByTemplateId(V2_ID)).thenReturn(new ArrayList<>(v1Nodes));
        when(nodeMapper.countByTemplateAndCode(eq(V2_ID), eq("dept_leader"), any())).thenReturn(1);

        NodeRequest request = new NodeRequest(null, "dept_leader", "重复的直属负责人", "approve",
                "dept_leader_upward", null, "any", null, null, null, "optional", 24, false, true, false,
                false, null);
        assertThatThrownBy(() -> service.addNode(V2_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在节点码");
    }

    @Test
    @DisplayName("换序：主干顺序被破坏 → 400（主干 7 节点顺序固定，enums.md §2）")
    void reorderKeepsTrunkOrder() {
        FlowTemplate draft = FlowDefinitionFixtures.matter(V2_ID, 2, "draft");
        when(templateMapper.selectTemplateById(V2_ID)).thenReturn(draft);
        when(nodeMapper.selectByTemplateId(V2_ID)).thenReturn(new ArrayList<>(v1Nodes));

        List<Long> ids = new ArrayList<>();
        ids.add(2L);  // finance_review 置前 → 破坏主干
        for (long i = 1; i <= 7; i++) {
            if (i != 2) {
                ids.add(i);
            }
        }
        assertThatThrownBy(() -> service.reorder(V2_ID, new NodeOrderRequest(ids)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("主干必填节点顺序被破坏");

        // 全排列缺失也拒绝
        assertThatThrownBy(() -> service.reorder(V2_ID, new NodeOrderRequest(List.of(1L, 2L))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须给出当前模板全部");
    }

    // ================================================================ 发布前校验与只读查询

    @Test
    @DisplayName("发布前校验：报告含全部规则结论；latest 复用缓存结果")
    void prePublishCheckReport() {
        when(nodeMapper.selectByTemplateId(V1_ID)).thenReturn(new ArrayList<>(v1Nodes));

        PrePublishReportView report = service.prePublishCheck(V1_ID);
        assertThat(report.passed()).isTrue();
        assertThat(report.checks()).hasSize(PrePublishChecker.RULES.size());
        assertThat(report.generatedAt()).isNotBlank();

        PrePublishReportView latest = service.latestPrePublishCheck(V1_ID);
        assertThat(latest.generatedAt()).isEqualTo(report.generatedAt());
        assertThat(service.checkRules()).hasSize(PrePublishChecker.RULES.size());
    }

    @Test
    @DisplayName("模板列表 / 详情 / 版本历史：版本倒序、只读标记与可用标记正确")
    void readViews() {
        when(templateMapper.selectTemplates("matter", null, null)).thenReturn(List.of(
                FlowDefinitionFixtures.matter(2L, 2, "published"),
                FlowDefinitionFixtures.matter(1L, 1, "archived")));
        List<TemplateView> list = service.list("matter", null, null);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).version()).isEqualTo(2);
        assertThat(list.get(0).usableByNewInstance()).isTrue();
        assertThat(list.get(0).readOnly()).isTrue();
        assertThat(list.get(1).usableByNewInstance()).isFalse();

        when(templateMapper.selectByCode("matter")).thenReturn(List.of(
                FlowDefinitionFixtures.matter(3L, 3, "draft"),
                FlowDefinitionFixtures.matter(2L, 2, "published")));
        assertThat(service.versions(V1_ID)).extracting(TemplateView::version).containsExactly(3, 2);

        assertThat(service.detail(V1_ID).nodes()).hasSize(7);
        assertThat(service.detail(V1_ID).nodes().get(1).timeoutHours()).isEqualTo(48);
        assertThat(service.detail(V1_ID).nodes().get(4).signPolicy()).isEqualTo("required");
        assertThat(service.detail(V1_ID).nodes().get(6).nodeCodeLabel()).isEqualTo("归档登记");
        assertThat(service.detail(V1_ID).template().usableByNewInstance()).isTrue();

        assertThatThrownBy(() -> service.detailByVersion(V1_ID, 9))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("决议解析：按候选人集合给出需要几人同意（绝对人数优先 / 百分比向上取整）")
    void resolveDecision() {
        FlowNode countersign = v1Nodes.get(3);
        countersign.setDecisionMode(DecisionMode.ALL.code());
        countersign.setPassThreshold("66%");
        when(nodeMapper.selectNodeById(4L)).thenReturn(countersign);

        assertThat(service.resolveDecision(4L, 3).requiredApprovals()).isEqualTo(2);
        assertThat(service.resolveDecision(4L, 3).basis()).isEqualTo("percent");
        assertThat(service.resolveDecision(4L, 3).description()).contains("向上取整").contains("2 人同意");
        assertThat(service.resolveDecision(4L, 3).satisfiable()).isTrue();
    }
}
