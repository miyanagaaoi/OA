package com.oa.form.template.writemodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.authz.visibility.FormFieldWriteGuard;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormWritePolicy;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.2 三态读写模型的服务端强制</b>（doc/forms.md §1.2 / §5 / §7；doc/templates.md §2.5；
 * doc/prd-0.1.md AC-28；doc/test-cases.md TC-FORM-011/012/013、TC-FLOW-062）。
 *
 * <p>重点是「客户端会放行、服务端必须拒绝」的那几类：审批中改主字段、待补件改只读字段、
 * 非发起人/非节点⑦改归还字段、非财务角色写金额。
 */
class FormStateWriteGuardTest {

    private static final Set<String> MATTER_FIELDS = Set.of(
            "title", "category", "description", "involve_cost", "amount", "cost_bearer",
            "expect_date", "cc_users", "attachments");

    private static final Set<String> SEAL_FIELDS = Set.of(
            "title", "category", "seal_type", "cert_name", "purpose", "usage_start", "usage_end",
            "seal_count", "is_external", "return_status", "return_date", "attachments");

    private final FormStateWriteGuard guard = new FormStateWriteGuard(new FormFieldWriteGuard(), null);

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static FlowInstanceRow instance(String formType, String status, String subStatus, Integer currentNodeSeq) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(5001L);
        row.setFormType(formType);
        row.setStatus(status);
        row.setSubStatus(subStatus);
        row.setInitiatorId(304L);
        row.setCurrentNodeSeq(currentNodeSeq);
        return row;
    }

    private static CurrentUser user(long id, String... roles) {
        return CurrentUser.of(id, "u" + id, "用户" + id, "T" + id, 135L, 12L,
                new java.util.LinkedHashSet<>(List.of(roles)), Set.of(), false);
    }

    private static FlowNodeInstanceRow archiveNode(boolean withApprover) {
        FlowNodeInstanceRow node = new FlowNodeInstanceRow();
        node.setId(9001L);
        node.setInstanceId(5001L);
        node.setNodeSeq(Integer.valueOf(7));
        node.setNodeCode(FormStateWriteGuard.ARCHIVE_NODE_CODE);
        node.setStatus("active");
        node.setApproverIdsJson(withApprover ? "[100,308]" : "[100]");
        return node;
    }

    // ================================================================ 状态层

    @Test
    @DisplayName("草稿：全部字段可写；未登记字段仍会被 FormDataService 的未知字段闸门拦（此处只判状态层）")
    void draftAllWritable() {
        WriteContext context = guard.resolve(instance("matter", "draft", null, null), user(304L, "employee"),
                MATTER_FIELDS, List.of());
        assertThat(context.state()).isEqualTo(FormWritePolicy.FormState.DRAFT);
        assertThat(context.writableFields()).containsExactlyInAnyOrderElementsOf(MATTER_FIELDS);
        assertThatCode(() -> guard.assertStateWritable(Set.of("amount", "title"), context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("审批中：非印鉴单全只读 → 改任何字段都是 40304（AC-28 / TC-FLOW-062）")
    void approvingIsReadOnly() {
        WriteContext context = guard.resolve(instance("matter", "approving", null, 3), user(304L, "employee"),
                MATTER_FIELDS, List.of());
        assertThat(context.state()).isEqualTo(FormWritePolicy.FormState.APPROVING);
        assertThat(context.writableFields()).isEmpty();
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("title"), context))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED)
                .hasMessageContaining("审批中主字段一律只读");
    }

    @Test
    @DisplayName("待补件（无字段类型信息）：退化为 attachments + supplement_note；改金额等主字段 → 40304")
    void pendingSupplementOnlyTwoFields() {
        WriteContext context = guard.resolve(instance("matter", "approving", "pending_supplement", 2),
                user(304L, "employee"), MATTER_FIELDS, List.of());
        assertThat(context.state()).isEqualTo(FormWritePolicy.FormState.PENDING_SUPPLEMENT);
        assertThat(context.writableFields()).containsExactlyInAnyOrder("attachments", "supplement_note");
        assertThatCode(() -> guard.assertStateWritable(Set.of("attachments", "supplement_note"), context))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("amount"), context))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待补件期仅附件类字段（type=file/files）与补件说明可写");
    }

    @Test
    @DisplayName("待补件（合同单）：按**字段类型**放行 counterparty_docs；title/amount 仍 40304（B 项裁定）")
    void pendingSupplementAllowsAttachmentTypedFields() {
        Set<String> contractFields = Set.of("title", "amount", "contract_type", "attachments", "counterparty_docs");
        Set<String> attachmentFields = Set.of("attachments", "counterparty_docs");
        WriteContext context = guard.resolve(instance("contract", "approving", "pending_supplement", 2),
                user(304L, "employee"), contractFields, List.of(), attachmentFields);

        assertThat(context.state()).isEqualTo(FormWritePolicy.FormState.PENDING_SUPPLEMENT);
        assertThat(context.attachmentFields()).containsExactlyInAnyOrderElementsOf(attachmentFields);
        assertThat(context.writableFields()).as("待补件期 = 所有附件类字段 + 补件说明")
                .containsExactlyInAnyOrder("attachments", "counterparty_docs", "supplement_note");
        assertThatCode(() -> guard.assertStateWritable(Set.of("counterparty_docs"), context))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("title"), context))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("contract_type"), context))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("附件类型信息不得放宽审批中窗口：合同单在审批中连附件字段也只读")
    void attachmentTypedFieldsDoNotWidenApprovingWindow() {
        Set<String> contractFields = Set.of("title", "attachments", "counterparty_docs");
        WriteContext context = guard.resolve(instance("contract", "approving", null, 1),
                user(304L, "employee"), contractFields, List.of(), Set.of("attachments", "counterparty_docs"));

        assertThat(context.writableFields()).isEmpty();
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("counterparty_docs"), context))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("待补件：印鉴单归还字段**同样只读**（TC-FORM-013，例外仅「审批中」生效）")
    void pendingSupplementSealReturnReadOnly() {
        WriteContext context = guard.resolve(instance("seal", "approving", "pending_supplement", 2),
                user(304L, "employee"), SEAL_FIELDS, List.of());
        assertThat(context.writableFields()).doesNotContain("return_status", "return_date");
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("return_status"), context))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("已完结 / 已归档：一律只读（含印鉴单，无例外）")
    void closedIsReadOnly() {
        for (String status : List.of("approved", "rejected", "terminated")) {
            WriteContext context = guard.resolve(instance("seal", status, null, null),
                    user(304L, "employee"), SEAL_FIELDS, List.of(archiveNode(true)));
            assertThat(context.state()).isEqualTo(FormWritePolicy.FormState.CLOSED);
            assertThat(context.writableFields()).isEmpty();
        }
    }

    // ================================================================ 印鉴单归还例外

    @Test
    @DisplayName("唯一例外：审批中仅发起人与节点⑦归档登记人可写归还字段")
    void sealReturnException() {
        FlowInstanceRow seal = instance("seal", "approving", null, 7);

        WriteContext initiator = guard.resolve(seal, user(304L, "employee"), SEAL_FIELDS,
                List.of(archiveNode(true)));
        assertThat(initiator.writableFields()).containsExactlyInAnyOrder("return_status", "return_date");
        assertThatCode(() -> guard.assertStateWritable(Set.of("return_status", "return_date"), initiator))
                .doesNotThrowAnyException();

        WriteContext archiveActor = guard.resolve(seal, user(308L, "admin"), SEAL_FIELDS,
                List.of(archiveNode(true)));
        assertThat(archiveActor.isArchiveNode()).isTrue();
        assertThat(archiveActor.writableFields()).containsExactlyInAnyOrder("return_status", "return_date");

        // 既非发起人、也不在节点⑦候选人内 → 全只读（TC-FORM-010 的②）
        WriteContext outsider = guard.resolve(seal, user(399L, "finance_owner"), SEAL_FIELDS,
                List.of(archiveNode(true)));
        assertThat(outsider.isArchiveNode()).isFalse();
        assertThat(outsider.writableFields()).isEmpty();
        assertThatThrownBy(() -> guard.assertStateWritable(Set.of("return_status"), outsider))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("节点⑦身份判定：节点码非 archive_register / 状态已终态 / 候选人里没有我 → 都不算")
    void archiveNodeIdentityRules() {
        FlowInstanceRow seal = instance("seal", "approving", null, 7);

        FlowNodeInstanceRow wrongCode = archiveNode(true);
        wrongCode.setNodeCode("finance_review");
        assertThat(guard.resolve(seal, user(308L, "admin"), SEAL_FIELDS, List.of(wrongCode)).isArchiveNode())
                .isFalse();

        FlowNodeInstanceRow cancelled = archiveNode(true);
        cancelled.setStatus("cancelled");
        assertThat(guard.resolve(seal, user(308L, "admin"), SEAL_FIELDS, List.of(cancelled)).isArchiveNode())
                .isFalse();

        assertThat(guard.resolve(seal, user(307L, "employee"), SEAL_FIELDS, List.of(archiveNode(true)))
                .isArchiveNode()).isFalse();

        // 实例当前节点不在⑦ → 不算（⑦ 尚未轮到）
        FlowInstanceRow atNode3 = instance("seal", "approving", null, 3);
        assertThat(guard.resolve(atNode3, user(308L, "admin"), SEAL_FIELDS, List.of(archiveNode(true)))
                .isArchiveNode()).isFalse();
    }

    @Test
    @DisplayName("approver_ids_json 形状容错：数组与对象包裹都能识别")
    void approverJsonShapes() {
        assertThat(FormStateWriteGuard.containsApprover("[100,308]", 308L)).isTrue();
        assertThat(FormStateWriteGuard.containsApprover("{\"userIds\":[100,308]}", 308L)).isTrue();
        assertThat(FormStateWriteGuard.containsApprover("[100]", 308L)).isFalse();
        assertThat(FormStateWriteGuard.containsApprover(null, 308L)).isFalse();
    }

    // ================================================================ 与 1.6 字段级限制正交

    @Test
    @DisplayName("正交性：草稿态下非财务角色写金额 → 40306（状态允许 ≠ 角色允许，PRD §5.3）")
    void amountRoleRuleIsOrthogonal() {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(user(304L, "employee"))
                .roleCodes(Set.of("employee"))
                .build());
        WriteContext draft = guard.resolve(instance("matter", "draft", null, null), user(304L, "employee"),
                MATTER_FIELDS, List.of());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", "100.00");

        assertThatThrownBy(() -> guard.assertWritable(payload, draft))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AMOUNT_READ_ONLY);
    }

    @Test
    @DisplayName("正交性：财务角色在草稿态可写金额；系统管理员同理")
    void financeCanWriteAmount() {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(user(307L, "finance_owner"))
                .roleCodes(Set.of("finance_owner"))
                .build());
        WriteContext draft = guard.resolve(instance("fund", "draft", null, null), user(307L, "finance_owner"),
                MATTER_FIELDS, List.of());
        assertThatCode(() -> guard.assertWritable(Map.of("amount", "100.00"), draft))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("宽容过滤（增量保存口径）：剥掉不可写字段，保留可写字段")
    void filterWritable() {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(user(304L, "finance_owner"))
                .roleCodes(Set.of("finance_owner"))
                .build());
        WriteContext pending = guard.resolve(instance("matter", "approving", "pending_supplement", 2),
                user(304L, "employee"), MATTER_FIELDS, List.of());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachments", List.of(Map.of("fileName", "a.pdf")));
        payload.put("supplement_note", "已补充发票");
        payload.put("amount", "999999.00");
        assertThat(guard.filterWritable(payload, pending))
                .containsOnlyKeys("attachments", "supplement_note");
    }

    @Test
    @DisplayName("上下文出参：状态中文名 + 只读字段 + 取证句（前端置灰与排障共用）")
    @SuppressWarnings("unchecked")
    void contextView() {
        WriteContext context = guard.resolve(instance("matter", "approving", null, 2), user(304L), MATTER_FIELDS,
                List.of());
        Map<String, Object> view = context.view();
        assertThat(view).containsEntry("state", "APPROVING");
        assertThat(view.get("stateLabel").toString()).contains("审批中");
        assertThat((List<String>) view.get("readonlyFields")).containsExactlyInAnyOrderElementsOf(MATTER_FIELDS);
        assertThat(view.get("evidence").toString()).contains("forms.md");
    }
}
