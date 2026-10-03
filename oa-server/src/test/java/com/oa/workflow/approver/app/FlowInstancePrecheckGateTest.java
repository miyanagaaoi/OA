package com.oa.workflow.approver.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.authz.app.EffectivePermissionService;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.FormSchemaFixtures;
import com.oa.form.app.FormDataService;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.workflow.approver.api.dto.ApproverDtos.CreateInstanceRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckBlockerView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckReportView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRequest;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.approver.infra.row.FormDataRow;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>AC-19（发起前拦截空候选人）的拦截点从「建草稿」移到「提交」</b>（2026-10-04 收敛）。
 *
 * <h2>为什么移</h2>
 * <p>改前 {@code POST /flow-instances}（保存草稿）自己就跑预检并在空候选人时 40007，
 * 于是：① 组织负责人尚未配好时连草稿都存不下来；② 预检里的 ⑤集团分管领导依赖
 * <b>表单内容</b>（事项类别），而类别正是用户正在填的字段 → 鸡生蛋。
 *
 * <h2>本类断言的四条</h2>
 * <ol>
 *   <li>建草稿：预检<b>不通过也成功</b>（且实现里对「未通过」只记 WARN，不抛 40007）；</li>
 *   <li>提交：同一条件下<b>仍被 40007 拦</b>，文案保留「节点/规则/缺什么配置」；</li>
 *   <li>提交：通过时快照按**锁定版本**重新解析并固化（REQ-FLOW-011），返回的是刷新后的快照；</li>
 *   <li>提交：拦截发生在固化之前（不写库）—— 「拒了但状态/快照已经变了」这种半成品不存在。</li>
 * </ol>
 */
class FlowInstancePrecheckGateTest {

    private static final long INITIATOR_ID = 304L;
    private static final long TEMPLATE_ID = 11L;
    private static final int TEMPLATE_VERSION = 1;

    private ApproverPrecheckService precheckService;
    private FlowInstanceMapper instanceMapper;
    private FlowInstanceService service;

    @BeforeEach
    void setUp() {
        precheckService = mock(ApproverPrecheckService.class);
        instanceMapper = mock(FlowInstanceMapper.class);

        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(INITIATOR_ID))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(FlowConfigPermission.FLOW_USE)));
        WorkflowPermissionService gate = new WorkflowPermissionService(permissions);

        service = new FlowInstanceService(precheckService, mock(ApproverDirectory.class), instanceMapper,
                mock(FlowTemplateMapper.class), gate, mock(AuditLogWriter.class),
                mock(FormSchemaService.class), mock(FormDataService.class),
                mock(com.oa.workflow.runtime.infra.FlowRuntimeMapper.class), new OaProperties());

        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(INITIATOR_ID, "dev_em01", "员工甲", "D0001", 135L, 12L,
                        Set.of("employee"), Set.of(), false))
                .roleCodes(Set.of("employee"))
                .build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ================================================================ 夹具

    private static FlowTemplate template() {
        FlowTemplate template = new FlowTemplate();
        template.setId(TEMPLATE_ID);
        template.setCode("matter");
        template.setName("事项审批单");
        template.setFormType("matter");
        template.setVersion(TEMPLATE_VERSION);
        template.setStatus("published");
        return template;
    }

    private static RuleRequest context() {
        return RuleRequest.of(INITIATOR_ID, 135L, 12L, "/1/12/135/", "business", Map.of("involve_cost", true));
    }

    /** ⑤ 空候选人 + ③ 空候选人（与运行期实测的 40007 文案同形）。 */
    private static PrecheckReportView blockedReport() {
        PrecheckBlockerView branchLeader = new PrecheckBlockerView(3, "branch_leader", "分公司分管领导",
                "branch_leader", "分公司分管领导", "候选人集合为空（解析规则未能取到任何在职审批人）",
                List.of("请为公司配置副职负责人（leader_type=deputy）"));
        PrecheckBlockerView groupLeader = new PrecheckBlockerView(5, "group_leader", "集团分管领导",
                "group_leader", "集团分管领导", "候选人集合为空（解析规则未能取到任何在职审批人）",
                List.of("请在发起时确定事项类别（category）"));
        return new PrecheckReportView(false, TEMPLATE_ID, "matter", TEMPLATE_VERSION, INITIATOR_ID, "员工甲",
                List.of(branchLeader, groupLeader), List.of(), List.of());
    }

    private static PrecheckReportView allowedReport() {
        return new PrecheckReportView(true, TEMPLATE_ID, "matter", TEMPLATE_VERSION, INITIATOR_ID, "员工甲",
                List.of(), List.of(), List.of());
    }

    private void stubResolve(PrecheckReportView report) {
        when(precheckService.resolveForSubmit(any(PrecheckRequest.class), any()))
                .thenReturn(new ApproverPrecheckService.Resolved(template(), context(), List.of(), report));
    }

    private static FlowInstanceRow draftInstance() {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(9101L);
        row.setBizNo("OA-2026-000001");
        row.setFormType("matter");
        row.setCategory("business");
        row.setInitiatorId(INITIATOR_ID);
        row.setTemplateId(TEMPLATE_ID);
        row.setTemplateVersion(TEMPLATE_VERSION);
        row.setFormDataId(9201L);
        row.setStatus("draft");
        row.setApproverSnapshotJson("{}");
        return row;
    }

    // ================================================================ ① 建草稿不拦截

    @Test
    @DisplayName("建草稿：预检不通过（2 个节点空候选人）**仍然成功**，只记 WARN 留痕（不再 40007）")
    void createSucceedsEvenWhenPrecheckIsBlocked() {
        stubResolve(blockedReport());
        FormSchema schema = FormSchemaFixtures.schema("matter");
        FormSchemaService schemaService = mock(FormSchemaService.class);
        when(schemaService.forTemplate(anyLong(), any())).thenReturn(schema);
        FormDataService formDataService = mock(FormDataService.class);
        when(formDataService.prepareDraft(any(), any())).thenReturn(
                new FormDataService.PreparedPayload("{}", TEMPLATE_VERSION, Map.of(), null));
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        when(permissions.permissionCodes(INITIATOR_ID))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(FlowConfigPermission.FLOW_USE)));
        ApproverDirectory directory = mock(ApproverDirectory.class);
        when(directory.orgByName(anyString())).thenReturn(java.util.Optional.empty());
        FlowInstanceService realService = new FlowInstanceService(precheckService, directory,
                instanceMapper, mock(FlowTemplateMapper.class), new WorkflowPermissionService(permissions),
                mock(AuditLogWriter.class), schemaService, formDataService,
                mock(com.oa.workflow.runtime.infra.FlowRuntimeMapper.class), new OaProperties());

        when(instanceMapper.insertFormData(any(FormDataRow.class))).thenAnswer(invocation -> {
            FormDataRow row = invocation.getArgument(0);
            row.setId(9201L);
            return 1;
        });
        when(instanceMapper.insertInstance(any(FlowInstanceRow.class))).thenAnswer(invocation -> {
            FlowInstanceRow row = invocation.getArgument(0);
            row.setId(9101L);
            return 1;
        });
        when(instanceMapper.countByBizNo(anyString())).thenReturn(0);
        FlowInstanceRow stored = draftInstance();
        when(instanceMapper.selectInstanceById(9101L)).thenReturn(stored);

        // 断言 1：不抛 40007
        assertThat(realService.create(new CreateInstanceRequest(TEMPLATE_ID, null, null, null, "matter",
                "business", null, null, null, null, Map.of(), Map.of()), null).bizNo())
                .isEqualTo("OA-2026-000001");
        // 断言 2：拦截项只进日志（披露不静默），不阻断落库
        verify(instanceMapper).insertInstance(any(FlowInstanceRow.class));
        verify(precheckService).logDraftNotReady(any(), any());
    }

    // ================================================================ ② 提交仍被 40007 拦

    @Test
    @DisplayName("提交：同一条件下被 40007 拦，文案含逐节点/规则/缺配（AC-19 不得削弱）")
    void submitIsStillBlockedWithPerNodeMessage() {
        stubResolve(blockedReport());

        assertThatThrownBy(() -> service.prepareSubmitSnapshot(draftInstance(), null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.APPROVER_RESOLUTION_BLOCKED))
                .hasMessageContaining("发起被拒绝：2 个节点无有效审批人")
                .hasMessageContaining("3 分公司分管领导")
                .hasMessageContaining("branch_leader")
                .hasMessageContaining("请为公司配置副职负责人")
                .hasMessageContaining("5 集团分管领导")
                .hasMessageContaining("请在发起时确定事项类别");

        // 拦截发生在固化之前：一行都不写
        verify(instanceMapper, never()).updateSnapshot(anyLong(), anyString());
        verify(precheckService, never()).logDraftNotReady(any(), any());
        verify(precheckService).logBlocked(any(), any());
    }

    // ================================================================ ③④ 通过则同源固化

    @Test
    @DisplayName("提交：通过时按**锁定版本**解析并固化快照，返回刷新后的快照（REQ-FLOW-011）")
    void submitFreezesFreshSnapshotWhenAllowed() {
        stubResolve(allowedReport());
        FlowInstanceRow instance = draftInstance();

        com.oa.workflow.approver.domain.ApproverSnapshot snapshot = service.prepareSubmitSnapshot(instance, null);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.templateVersion()).isEqualTo(TEMPLATE_VERSION);
        org.mockito.ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateSnapshot(org.mockito.ArgumentMatchers.eq(instance.getId()), json.capture());
        assertThat(json.getValue()).as("固化的快照 JSON（REQ-FLOW-011：发起时解析并固化）")
                .contains("template_version").contains("matter");
        // 预检请求必须带**实例锁定的模板 id**（不是「当前已发布」）—— AC-09
        org.mockito.ArgumentCaptor<PrecheckRequest> captor =
                org.mockito.ArgumentCaptor.forClass(PrecheckRequest.class);
        verify(precheckService).resolveForSubmit(captor.capture(), any());
        assertThat(captor.getValue().templateId()).isEqualTo(TEMPLATE_ID);
        assertThat(captor.getValue().initiatorId()).isEqualTo(INITIATOR_ID);
        assertThat(captor.getValue().formType()).isEqualTo("matter");
    }

    @Test
    @DisplayName("提交：事项类别取**表单当前值**并固化（否则草稿里新选的类别进了不解析 → 死路）")
    void submitTakesCategoryFromFormData() {
        stubResolve(allowedReport());
        FlowInstanceRow instance = draftInstance();
        instance.setCategory(null);
        when(instanceMapper.selectFormDataFields(instance.getFormDataId()))
                .thenReturn("{\"title\":\"采购办公用品\",\"category\":\"business\"}");

        service.prepareSubmitSnapshot(instance, null);

        verify(instanceMapper).updateCategory(instance.getId(), "business");
        org.mockito.ArgumentCaptor<PrecheckRequest> captor =
                org.mockito.ArgumentCaptor.forClass(PrecheckRequest.class);
        verify(precheckService).resolveForSubmit(captor.capture(), any());
        assertThat(captor.getValue().category())
                .as("⑤集团分管领导按类别解析：类别必须是**表单里选的**那个")
                .isEqualTo("business");
    }
}
