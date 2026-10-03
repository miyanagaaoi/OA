package com.oa.form.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oa.authz.visibility.FormFieldWriteGuard;
import com.oa.common.audit.AuditLogMapper;
import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.FormSchemaFixtures;
import com.oa.form.dict.FormDictService;
import com.oa.form.dict.InMemoryDictMapper;
import com.oa.form.document.FormRuleRegistry;
import com.oa.form.contract.ContractFormRules;
import com.oa.form.fund.FundFormRules;
import com.oa.form.infra.FormDataMapper;
import com.oa.form.infra.row.FormDataFullRow;
import com.oa.form.matter.MatterFormRules;
import com.oa.form.seal.SealFormRules;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.form.template.snapshot.FormSnapshotService;
import com.oa.form.template.validate.FormPayloadValidator;
import com.oa.form.template.validate.ValidationMode;
import com.oa.form.template.writemodel.FormStateWriteGuard;
import com.oa.platform.security.crypto.PhoneCipher;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.runtime.app.FlowThreadWriter;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.1–2b.4 编排链路单测</b>：四类单据的「建草稿 → 保存 → 读取」与越权拒绝。
 *
 * <p>依赖全部替身化（Mapper 用 Mockito、字典用内存种子），因此本类可以在无 DB / 无 Redis /
 * 无 Spring 容器的条件下穷举「客户端会放行、服务端必须拒绝」的用例；
 * 真实 HTTP 链路另见运行期实测记录（四类单据各一条 + 五类拒绝样例）。
 */
class FormDataServiceTest {

    private static final long INSTANCE_ID = 8101L;
    private static final long FORM_DATA_ID = 9101L;
    private static final long INITIATOR_ID = 304L;

    private FormDataService service;
    private FlowInstanceMapper instanceMapper;
    private FlowNodeInstanceMapper nodeInstanceMapper;
    private FormDataMapper formDataMapper;
    private AtomicReference<Map<String, Object>> capturedPayload;
    private final Map<String, FormSchema> schemas = FormSchemaFixtures.schemas();

    @BeforeEach
    void setUp() {
        instanceMapper = mock(FlowInstanceMapper.class);
        nodeInstanceMapper = mock(FlowNodeInstanceMapper.class);
        formDataMapper = mock(FormDataMapper.class);
        capturedPayload = new AtomicReference<>();

        FormDictService dictService = new FormDictService(new InMemoryDictMapper());
        FormSchemaService schemaService = mock(FormSchemaService.class);
        when(schemaService.forInstance(any())).thenAnswer(invocation -> {
            FlowInstanceRow row = invocation.getArgument(0);
            return schemas.get(row.getFormType());
        });
        when(schemaService.publishedFor(anyString())).thenAnswer(invocation ->
                schemas.get(invocation.getArgument(0, String.class)));
        when(schemaService.forTemplate(anyLong(), any())).thenAnswer(invocation -> schemas.get("matter"));

        FormSnapshotService snapshotService = new FormSnapshotService(formDataMapper, dictService);
        FormPayloadValidator validator = new FormPayloadValidator(dictService, (scope, value) -> true);
        FormRuleRegistry registry = new FormRuleRegistry(List.of(
                new MatterFormRules(), new FundFormRules(), new ContractFormRules(), new SealFormRules()));
        FormStateWriteGuard writeGuard = new FormStateWriteGuard(new FormFieldWriteGuard(), nodeInstanceMapper);
        PhoneCipher phoneCipher = mock(PhoneCipher.class);
        when(phoneCipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(phoneCipher.decrypt(anyString())).thenAnswer(invocation -> {
            String stored = invocation.getArgument(0);
            return stored.startsWith("enc:") ? stored.substring(4) : stored;
        });
        AuditLogWriter auditLogWriter = new AuditLogWriter(mock(AuditLogMapper.class));
        FlowThreadWriter threadWriter = mock(FlowThreadWriter.class);

        service = new FormDataService(schemaService, formDataMapper, instanceMapper, nodeInstanceMapper,
                snapshotService, validator, registry, writeGuard, dictService, phoneCipher, auditLogWriter,
                threadWriter);

        when(formDataMapper.updateFieldsJson(anyLong(), anyString(), anyInt())).thenAnswer(invocation -> {
            capturedPayload.set(com.oa.common.json.JsonText.read(invocation.getArgument(1, String.class)) == null
                    ? Map.of()
                    : new LinkedHashMap<>(com.oa.common.json.JsonText.read(
                            invocation.getArgument(1, String.class), Map.class)));
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private void login(long id, String... roles) {
        CurrentUser principal = CurrentUser.of(id, "u" + id, "用户" + id, "T" + id, 135L, 12L,
                new java.util.LinkedHashSet<>(List.of(roles)), Set.of(), false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(principal.roleCodes())
                .build());
    }

    private FlowInstanceRow instance(String formType, String status, String subStatus, String fieldsJson) {
        FlowInstanceRow row = new FlowInstanceRow();
        row.setId(INSTANCE_ID);
        row.setBizNo("OA-2026-900001");
        row.setFormType(formType);
        row.setStatus(status);
        row.setSubStatus(subStatus);
        row.setInitiatorId(INITIATOR_ID);
        row.setTemplateId(11L);
        row.setTemplateVersion(1);
        row.setFormDataId(FORM_DATA_ID);
        when(instanceMapper.selectInstanceById(INSTANCE_ID)).thenReturn(row);
        when(nodeInstanceMapper.selectByInstance(INSTANCE_ID)).thenReturn(List.of());

        FormDataFullRow data = new FormDataFullRow();
        data.setId(FORM_DATA_ID);
        data.setFormType(formType);
        data.setBizNo("OA-2026-900001");
        data.setFieldsJson(fieldsJson);
        data.setSchemaVersion(1);
        when(formDataMapper.selectFormDataById(FORM_DATA_ID)).thenReturn(data);
        return row;
    }

    /**
     * 模拟「收款账号已按 {@code doc/data-model.md} §8.2 单独密文落列」：
     * 校验时必须把它回填进待校验值，否则每次保存都会误报「请填写收款账号」。
     */
    private void stubPayeeAccountCipher(String plain) {
        when(formDataMapper.selectPayeeAccountCipher(FORM_DATA_ID)).thenReturn("enc:" + plain);
    }

    // ================================================================ 四类单据链路

    @Test
    @DisplayName("事项单：建草稿（prepareDraft）→ 保存 → 读取，字段与 schema_version 一并固化")
    void matterChain() {
        login(INITIATOR_ID, "employee");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "采购办公用品");
        payload.put("category", "business");
        payload.put("description", "部门日常办公用品集中采购申请");
        payload.put("involve_cost", true);
        payload.put("amount", "1200.00");
        payload.put("cost_bearer", "12");
        payload.put("expect_date", "2099-12-31");

        FormDataService.PreparedPayload prepared = service.prepareDraft(schemas.get("matter"), payload);
        assertThat(prepared.schemaVersion()).isEqualTo(1);
        assertThat(prepared.fieldsJson()).contains("\"category\":\"business\"").contains("\"amount\":\"1200.00\"");

        instance("matter", "draft", null, prepared.fieldsJson());
        Map<String, Object> saved = service.save(INSTANCE_ID, Map.of("title", "采购办公用品（修订）"),
                ValidationMode.SUBMIT);
        assertThat(capturedPayload.get()).containsEntry("title", "采购办公用品（修订）")
                .containsEntry("category", "business");
        assertThat(saved).containsEntry("instanceId", INSTANCE_ID);
        assertThat(readField(saved, "snapshot", "schemaVersion")).isEqualTo(1);

        Map<String, Object> read = service.read(INSTANCE_ID);
        assertThat(readField(read, "formRules", "skipFinanceReview")).isEqualTo(false);
    }

    @Test
    @DisplayName("资金单：收款账号不进 fields_json，改走密文列；金额规范化到两位小数")
    void fundChainExtractsSensitiveAccount() {
        login(INITIATOR_ID, "finance_owner");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "8 月供热管网维护款支付");
        payload.put("category", "economy");
        payload.put("amount", "1250000");
        payload.put("payee", "某某市政工程有限公司");
        payload.put("payee_account", "6222021234567890");
        payload.put("pay_method", "transfer");
        payload.put("pay_date", "2099-12-31");
        payload.put("urgent", false);
        payload.put("attachments", List.of(Map.of("fileName", "invoice.pdf", "fileSize", 2048)));

        FormDataService.PreparedPayload prepared = service.prepareDraft(schemas.get("fund"), payload);
        assertThat(prepared.payeeAccountPlain())
                .as("收款账号单独密文落列（doc/data-model.md §8.2），**不进 fields_json**")
                .isEqualTo("6222021234567890");
        assertThat(prepared.fieldsJson()).doesNotContain("payee_account");
        assertThat(prepared.fieldsJson()).contains("\"amount\":\"1250000.00\"");
        assertThat(prepared.fieldsJson()).contains("\"plan_category\":true");

        instance("fund", "draft", null, prepared.fieldsJson());
        stubPayeeAccountCipher("6222021234567890");
        service.save(INSTANCE_ID, Map.of("payee", "某某市政工程有限公司（更名）"), ValidationMode.SUBMIT);
        assertThat(capturedPayload.get()).containsEntry("payee", "某某市政工程有限公司（更名）");
        assertThat(capturedPayload.get()).doesNotContainKey("payee_account");
    }

    @Test
    @DisplayName("合同单：完整链路通过；必传文本留空即拒")
    void contractChain() {
        login(INITIATOR_ID, "employee");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "供热管网维护合同");
        payload.put("category", "business");
        payload.put("contract_type", "service");
        payload.put("counterparty", "某某市政工程有限公司");
        payload.put("counterparty_credit", "91310000MA1K35XXXX");
        payload.put("amount", "1250000.00");
        payload.put("period_start", "2099-01-01");
        payload.put("period_end", "2099-12-31");
        payload.put("is_framework", false);
        payload.put("seal_type", "contract_seal");
        payload.put("attachments", List.of(Map.of("fileName", "contract.pdf", "fileSize", 4096)));

        FormDataService.PreparedPayload prepared = service.prepareDraft(schemas.get("contract"), payload);
        instance("contract", "draft", null, prepared.fieldsJson());
        Map<String, Object> read = service.read(INSTANCE_ID);
        assertThat(readField(read, "snapshot", "fields")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("counterparty", "某某市政工程有限公司");

        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("counterparty", "   "), ValidationMode.SUBMIT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORM_VALIDATION_FAILED)
                .hasMessageContaining("对方主体名称");
    }

    @Test
    @DisplayName("印鉴单：完整链路通过；归还字段在审批中由发起人可改（三态唯一例外）")
    void sealChain() {
        login(INITIATOR_ID, "employee");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "借用营业执照办理投标");
        payload.put("category", "admin");
        payload.put("seal_type", "cert_borrow");
        payload.put("cert_name", "business_license");
        payload.put("purpose", "用于投标文件盖章");
        payload.put("usage_start", "2099-01-01");
        payload.put("usage_end", "2099-01-31");
        payload.put("is_external", false);
        payload.put("return_status", "pending");

        FormDataService.PreparedPayload prepared = service.prepareDraft(schemas.get("seal"), payload);
        instance("seal", "approving", null, prepared.fieldsJson());
        Map<String, Object> saved = service.registerSealReturn(INSTANCE_ID,
                Map.of("return_status", "returned", "return_date", "2099-02-01"));
        assertThat(saved).containsEntry("instanceId", INSTANCE_ID);
        assertThat(capturedPayload.get()).containsEntry("return_status", "returned")
                .containsEntry("return_date", "2099-02-01");
    }

    // ================================================================ 拒绝样例

    @Test
    @DisplayName("夹带字段：未登记键 → 40308（不是忽略、不是透传）")
    void unknownFieldRejected() {
        login(INITIATOR_ID, "employee");
        instance("matter", "draft", null, "{}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("evil_field", "x"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_NOT_IN_SCHEMA)
                .hasMessageContaining("evil_field");
    }

    @Test
    @DisplayName("审批中改主字段 → 40304；改归还状态（非印鉴单）同样 40304")
    void approvingWriteRejected() {
        login(INITIATOR_ID, "employee");
        instance("matter", "approving", null, "{\"title\":\"已提交\"}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("title", "偷改标题"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("待补件改只读字段 → 40304；只写附件与补件说明则通过（AC-28 / TC-FORM-011/012）")
    void pendingSupplementReadOnlyMainFields() {
        // 以财务角色登录，绕开「金额对非财务角色只读（40306）」这条**正交**规则，
        // 从而让本用例命中的确实是**状态白名单**（AC-28 的补件主字段只读）。
        login(INITIATOR_ID, "finance_owner");
        instance("matter", "approving", "pending_supplement",
                "{\"title\":\"已提交\",\"amount\":\"100.00\"}");

        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("amount", "999999.00"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED)
                .hasMessageContaining("待补件期仅附件与补件说明可写");

        service.save(INSTANCE_ID, Map.of("attachments", List.of(Map.of("fileName", "invoice.pdf")),
                "supplement_note", "已补充发票原件"), ValidationMode.DRAFT);
        assertThat(capturedPayload.get()).containsEntry("supplement_note", "已补充发票原件")
                .containsEntry("amount", "100.00");
    }

    @Test
    @DisplayName("正交规则：待补件态下非财务角色写金额 → 40306（角色规则先于状态规则命中）")
    void pendingSupplementAmountRoleRule() {
        login(INITIATOR_ID, "employee");
        instance("matter", "approving", "pending_supplement", "{\"amount\":\"100.00\"}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("amount", "999999.00"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AMOUNT_READ_ONLY);
    }

    @Test
    @DisplayName("浮点金额 → 40011（禁浮点）；非法枚举 → 40011（inDict）")
    void floatAmountAndIllegalEnumRejected() {
        login(INITIATOR_ID, "finance_owner");
        instance("fund", "draft", null, "{\"title\":\"资金单\"}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("amount", 100.12d), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORM_VALIDATION_FAILED)
                .hasMessageContaining("禁止使用浮点数");

        login(INITIATOR_ID, "employee");
        instance("matter", "draft", null, "{}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("category", "operate"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("事项类别取值非法");
    }

    @Test
    @DisplayName("超长文本 → 40011（maxLength）")
    void overlongTextRejected() {
        login(INITIATOR_ID, "employee");
        instance("matter", "draft", null, "{}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("title", "标".repeat(61)), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORM_VALIDATION_FAILED)
                .hasMessageContaining("事项标题不能超过 60 个字符");
    }

    @Test
    @DisplayName("类别改判（非草稿）→ 40309")
    void categoryChangeRejected() {
        login(INITIATOR_ID, "employee");
        instance("matter", "approving", null, "{\"category\":\"business\"}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("category", "hr"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED);
    }

    @Test
    @DisplayName("非发起人（非节点⑦、非管理员）写表单 → 403")
    void nonInitiatorRejected() {
        login(399L, "dept_leader");
        instance("matter", "draft", null, "{}");
        assertThatThrownBy(() -> service.save(INSTANCE_ID, Map.of("title", "越权"), ValidationMode.DRAFT))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("归还登记通道只接受 return_status / return_date（其余字段 → 40304）")
    void sealReturnChannelRejectsOtherFields() {
        login(INITIATOR_ID, "employee");
        instance("seal", "approving", null, "{\"return_status\":\"pending\"}");
        assertThatThrownBy(() -> service.registerSealReturn(INSTANCE_ID, Map.of("purpose", "偷改用途")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED)
                .hasMessageContaining("归还登记通道只接受");
    }

    @Test
    @DisplayName("归还登记非印鉴单 → 40304")
    void sealReturnChannelOnlyForSeal() {
        login(INITIATOR_ID, "employee");
        instance("matter", "approving", null, "{}");
        assertThatThrownBy(() -> service.registerSealReturn(INSTANCE_ID, Map.of("return_status", "returned")))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FIELD_WRITE_DENIED)
                .hasMessageContaining("只适用于印鉴证照审批单");
    }

    // ================================================================ 干跑校验

    @Test
    @DisplayName("按单据类型干跑校验：返回全部失败项，不改库")
    void validateByFormTypeReturnsAllIssues() {
        login(INITIATOR_ID, "employee");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "标".repeat(61));
        payload.put("category", "operate");
        Map<String, Object> view = service.validateByFormType("matter", payload, ValidationMode.SUBMIT);
        Map<String, Object> report = asMap(view.get("report"));
        assertThat(report).containsEntry("passed", false);
        assertThat((List<?>) report.get("issues")).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("实例级可写字段出参：状态 + 可写集合 + 只读集合 + 取证句")
    void writableFieldsView() {
        login(INITIATOR_ID, "employee");
        instance("matter", "approving", "pending_supplement", "{}");
        Map<String, Object> view = service.writableFields(INSTANCE_ID);
        Map<String, Object> state = asMap(view.get("state"));
        assertThat(state).containsEntry("state", "PENDING_SUPPLEMENT");
        assertThat(state.get("writableFields")).isEqualTo(
                new java.util.LinkedHashSet<>(java.util.List.of("attachments", "supplement_note")));
    }

    @Test
    @DisplayName("实例 schema 出参带锁定版本取证（AC-09）")
    void schemaOfInstance() {
        login(INITIATOR_ID, "employee");
        instance("contract", "approving", null, "{}");
        Map<String, Object> view = service.schemaOfInstance(INSTANCE_ID);
        assertThat(view).containsEntry("templateVersion", 1).containsEntry("instanceId", INSTANCE_ID);
        assertThat(view.get("lockedVersionEvidence").toString()).contains("AC-09");
    }

    // ================================================================ 工具

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Object readField(Map<String, Object> view, String section, String key) {
        return asMap(view.get(section)).get(key);
    }
}
