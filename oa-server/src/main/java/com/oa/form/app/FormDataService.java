package com.oa.form.app;

import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.common.security.CurrentUser;
import com.oa.authz.visibility.VisibilityRoles;
import com.oa.form.document.FormRuleContext;
import com.oa.form.document.FormRuleRegistry;
import com.oa.form.document.FormTypeRules;
import com.oa.form.infra.FormDataMapper;
import com.oa.form.infra.row.FormDataFullRow;
import com.oa.form.template.schema.FormFieldDef;
import com.oa.form.template.schema.FormSchema;
import com.oa.form.template.schema.FormSchemaService;
import com.oa.form.template.snapshot.FormSnapshotService;
import com.oa.form.template.validate.ConditionEvaluator;
import com.oa.form.template.validate.FormPayloadValidator;
import com.oa.form.template.validate.FormValidationReport;
import com.oa.form.template.validate.ValidationMode;
import com.oa.form.template.writemodel.FormStateWriteGuard;
import com.oa.form.template.writemodel.WriteContext;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.runtime.app.FlowThreadWriter;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import com.oa.workflow.runtime.infra.FlowNodeInstanceMapper;
import com.oa.workflow.runtime.infra.row.FlowNodeInstanceRow;
import com.oa.platform.security.crypto.PhoneCipher;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>2b.1 / 2b.2 / 2b.3 / 2b.4 的编排层</b>：表单数据的校验、归一化、快照与读取。
 *
 * <h2>固定的四步顺序（顺序本身是口径）</h2>
 * <ol>
 *   <li><b>未知字段扫描</b> → 403 {@link ErrorCode#FIELD_NOT_IN_SCHEMA}
 *       （{@code doc/templates.md} §2.5「未知键」：一律拒绝，不允许静默透传）；</li>
 *   <li><b>三态白名单 + 角色金额规则</b> → 403 {@link ErrorCode#FIELD_WRITE_DENIED} /
 *       {@link ErrorCode#AMOUNT_READ_ONLY}（{@code doc/forms.md} §1.2 末段：服务端必须校验，
 *       不能仅依赖前端置灰）；</li>
 *   <li><b>schema 驱动二次校验</b> → 400 {@link ErrorCode#FORM_VALIDATION_FAILED}
 *       （一次返回**全部**失败项）；</li>
 *   <li><b>单据专属规则</b>（{@code oa.form.{matter,fund,contract,seal}}）→ 40011 或
 *       403 {@link ErrorCode#CATEGORY_IMMUTABLE}。</li>
 * </ol>
 * 先白名单后校验的理由：越权写入属**安全**问题，必须在「字段值合不合法」之前拒；
 * 先未知字段再白名单的理由：未登记的键连「能不能写」都无从谈起。
 *
 * <h2>合并语义（保存是增量）</h2>
 * <p>{@code PUT} 保存把「已落库值 ⊕ 本次提交值」合并后再校验：条件必填与跨字段规则
 * 必须判**最终态**，否则「分两次保存」就能绕过校验（先存 {@code involve_cost=true} 不存金额，
 * 再单独存金额为空）。
 */
@Service
public class FormDataService {

    private static final Logger log = LoggerFactory.getLogger(FormDataService.class);

    /** 收款账号字段码（**敏感**：单独密文落列，不进 {@code fields_json}）。 */
    public static final String FIELD_PAYEE_ACCOUNT = "payee_account";

    private final FormSchemaService schemaService;
    private final FormDataMapper formDataMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeInstanceMapper nodeInstanceMapper;
    private final FormSnapshotService snapshotService;
    private final FormPayloadValidator validator;
    private final FormRuleRegistry ruleRegistry;
    private final FormStateWriteGuard writeGuard;
    private final com.oa.form.dict.FormDictService dictService;
    private final PhoneCipher phoneCipher;
    private final AuditLogWriter auditLogWriter;
    private final FlowThreadWriter threadWriter;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public FormDataService(FormSchemaService schemaService,
                           FormDataMapper formDataMapper,
                           FlowInstanceMapper instanceMapper,
                           FlowNodeInstanceMapper nodeInstanceMapper,
                           FormSnapshotService snapshotService,
                           FormPayloadValidator validator,
                           FormRuleRegistry ruleRegistry,
                           FormStateWriteGuard writeGuard,
                           com.oa.form.dict.FormDictService dictService,
                           PhoneCipher phoneCipher,
                           AuditLogWriter auditLogWriter,
                           FlowThreadWriter threadWriter) {
        this.schemaService = schemaService;
        this.formDataMapper = formDataMapper;
        this.instanceMapper = instanceMapper;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.snapshotService = snapshotService;
        this.validator = validator;
        this.ruleRegistry = ruleRegistry;
        this.writeGuard = writeGuard;
        this.dictService = dictService;
        this.phoneCipher = phoneCipher;
        this.auditLogWriter = auditLogWriter;
        this.threadWriter = threadWriter;
    }

    // ================================================================ 校验（无实例 / 有实例）

    /**
     * 按单据类型干跑校验（**新建前的预检**；用**当前已发布**版本的 schema）。
     *
     * <p>{@code doc/templates.md} §3.3：只有 {@code published} 版本可被新实例使用。
     */
    public Map<String, Object> validateByFormType(String formType, Map<String, Object> payload, ValidationMode mode) {
        FormSchema schema = schemaService.publishedFor(formType);
        Map<String, Object> values = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        applyDefaults(schema, values);
        canonicalizeAmounts(schema, values);
        canonicalizePickers(schema, values);
        FormValidationReport report = validator.validate(schema, values, mode, LocalDate.now(), null);
        FormTypeRules rules = ruleRegistry.require(schema.formType());
        FormValidationReport.Collector collector = collectorOf(report);
        rules.validate(ruleContext(schema, values, payload, Map.of(), mode, null), collector);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("formType", schema.formType());
        view.put("templateCode", schema.templateCode());
        view.put("schemaVersion", schema.schemaVersion());
        view.put("mode", mode.name());
        view.put("report", collector.build().view());
        view.put("normalized", values);
        return view;
    }

    /**
     * 实例级干跑校验（**不改库**）：按实例锁定版本校验合并后的最终态。
     *
     * @throws BizException 403 未知字段 / 越权字段
     */
    public Map<String, Object> validateInstance(Long instanceId, Map<String, Object> payload, ValidationMode mode) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        Set<String> incoming = keysOf(payload);
        rejectUnknownFields(schema, incoming);
        WriteContext context = writeContext(instance, schema);
        if (payload != null && !payload.isEmpty()) {
            writeGuard.assertWritable(payload, context);
        }
        FormDataFullRow row = snapshotService.readRow(instance.getFormDataId());
        Map<String, Object> stored = snapshotService.readFields(row == null ? null : row.getFieldsJson());
        Map<String, Object> merged = merge(stored, payload);
        applyDefaults(schema, merged);
        canonicalizeAmounts(schema, merged);
        canonicalizePickers(schema, merged);
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.addAll(validator.validate(schema, merged, mode, LocalDate.now(), instance.getSubmittedAt()));
        FormTypeRules rules = ruleRegistry.require(schema.formType());
        rules.validate(ruleContext(schema, merged, payload, stored, mode, instance), collector);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("instanceId", instanceId);
        view.put("bizNo", instance.getBizNo());
        view.put("formType", schema.formType());
        view.put("schemaVersion", schema.schemaVersion());
        view.put("templateVersion", instance.getTemplateVersion());
        view.put("state", context.view());
        view.put("mode", mode.name());
        view.put("report", collector.build().view());
        Map<String, Object> sanitized = new LinkedHashMap<>(merged);
        sanitized.remove(FIELD_PAYEE_ACCOUNT);
        view.put("merged", sanitized);
        return view;
    }

    // ================================================================ 保存

    /**
     * 保存（写入 {@code form_data}）：白名单 → schema 校验 → 专属规则 → 归一化 → 快照固化。
     *
     * <p>调用人身份：仅**发起人**（或系统管理员）可写草稿/补件；印鉴单在**审批中**的归还登记
     * 另放行节点⑦归档登记人（{@code doc/forms.md} §5 例外边界表）。
     */
    @Transactional
    public Map<String, Object> save(Long instanceId, Map<String, Object> payload, ValidationMode mode) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        Set<String> incoming = keysOf(payload);
        rejectUnknownFields(schema, incoming);
        WriteContext context = writeContext(instance, schema);
        requireWriter(instance, context, schema);

        if (payload != null && !payload.isEmpty()) {
            writeGuard.assertWritable(payload, context);
        }

        FormDataFullRow row = snapshotService.readRow(instance.getFormDataId());
        if (row == null && instance.getFormDataId() != null) {
            throw BizException.notFound("表单数据#" + instance.getFormDataId());
        }
        Map<String, Object> stored = snapshotService.readFields(row == null ? null : row.getFieldsJson());
        Map<String, Object> merged = merge(stored, payload);
        applyDefaults(schema, merged);
        canonicalizeAmounts(schema, merged);
        canonicalizePickers(schema, merged);
        Map<String, Object> forValidation = new LinkedHashMap<>(merged);
        injectSensitiveForValidation(schema, forValidation, instance.getFormDataId());

        FormRuleContext ruleContext = ruleContext(schema, forValidation, payload, stored, mode, instance);
        FormTypeRules rules = ruleRegistry.require(schema.formType());
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.addAll(validator.validate(schema, forValidation, mode, LocalDate.now(), instance.getSubmittedAt()));
        rules.validate(ruleContext, collector);
        collector.build().fail();

        Map<String, Object> patch = rules.normalize(ruleContext);
        applyPatch(merged, patch, schema, context);

        persist(instance, schema, merged, context);
        auditLogWriter.appendAsCurrentUser("form_save", "form_data", instance.getFormDataId(),
                JsonText.write(stored), JsonText.write(merged), null, null);
        log.info("表单保存：operator 提交 instanceId={} formType={} mode={} 字段数={}",
                instanceId, schema.formType(), mode, merged.size());
        return readView(instance, schema, merged, context);
    }

    /**
     * 印鉴单归还登记（**三态读写模型的唯一例外**的专用写入通道）。
     *
     * <p>只接受 {@code return_status} / {@code return_date} 两个键（其余键一律 40304/40308），
     * 校验闭环后落库，并按 {@code doc/forms.md} §5 末「只写入审批轨迹与审计日志」写
     * {@code sys_thread}。
     *
     * <p><b>轨迹动作 = {@link ThreadAction#RETURN_REGISTER}（{@code return_register}）</b>——
     * 2026-10-04 裁定：16 → 17 值新增该动作码，**不再**复用 {@code archive_register}。
     * 理由：轨迹是**用户可见**的，「归还登记」显示成「归档登记」是误导；节点⑦的
     * 「归档登记」动作本身仍用 {@code archive_register}（见 {@code FlowAction.ARCHIVE_REGISTER}）。
     * 登记主体可以是**发起人**（审批中）或**节点⑦归档登记人**，两者落同一个动作码、不同 actor。
     * 真源：{@code doc/enums.md} §9（{@code return_register} 行）、{@code doc/forms.md} §5、AC-28。
     */
    @Transactional
    public Map<String, Object> registerSealReturn(Long instanceId, Map<String, Object> payload) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        if (!"seal".equalsIgnoreCase(schema.formType())) {
            throw new BizException(ErrorCode.FIELD_WRITE_DENIED,
                    "归还状态登记只适用于印鉴证照审批单（form_type=seal），当前为 " + schema.formType());
        }
        Set<String> incoming = keysOf(payload);
        rejectUnknownFields(schema, incoming);
        for (String field : incoming) {
            if (!FormWritePolicy.SEAL_RETURN_FIELDS.contains(field)) {
                throw new BizException(ErrorCode.FIELD_WRITE_DENIED,
                        String.format(ErrorCode.FIELD_WRITE_DENIED.getMessage(), field)
                                + "（归还登记通道只接受 return_status / return_date）");
            }
        }
        WriteContext context = writeContext(instance, schema);
        requireWriter(instance, context, schema);
        writeGuard.assertWritable(payload, context);

        FormDataFullRow row = snapshotService.readRow(instance.getFormDataId());
        Map<String, Object> stored = snapshotService.readFields(row == null ? null : row.getFieldsJson());
        Map<String, Object> merged = merge(stored, payload);
        applyDefaults(schema, merged);
        canonicalizeAmounts(schema, merged);
        canonicalizePickers(schema, merged);
        Map<String, Object> forValidation = new LinkedHashMap<>(merged);
        injectSensitiveForValidation(schema, forValidation, instance.getFormDataId());
        FormRuleContext ruleContext = ruleContext(schema, forValidation, payload, stored, ValidationMode.DRAFT, instance);
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.addAll(validator.validate(schema, forValidation, ValidationMode.DRAFT, LocalDate.now(),
                instance.getSubmittedAt()));
        ruleRegistry.require(schema.formType()).validate(ruleContext, collector);
        collector.build().fail();

        persist(instance, schema, merged, context);
        String before = describeReturn(stored);
        String after = describeReturn(merged);
        threadWriter.appendAsCurrentUser(instanceId, currentNodeInstanceId(instance),
                ThreadAction.RETURN_REGISTER,
                String.format("归还登记：%s → %s；归还状态变更不触发流程推进（doc/forms.md §5）", before, after));
        auditLogWriter.appendAsCurrentUser("seal_return_register", "form_data", instance.getFormDataId(),
                JsonText.write(Map.of("return", before)), JsonText.write(Map.of("return", after)), null, null);
        log.info("印鉴单归还登记：instanceId={} {} → {}", instanceId, before, after);
        return readView(instance, schema, merged, context);
    }

    // ================================================================ 读取

    /** 读取单据（schema + 字段值 + 名称快照 + 可写字段 + 单据专属派生信息）。 */
    public Map<String, Object> read(Long instanceId) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        FormDataFullRow row = snapshotService.readRow(instance.getFormDataId());
        Map<String, Object> stored = snapshotService.readFields(row == null ? null : row.getFieldsJson());
        if (row != null) {
            snapshotService.discloseVersionDrift(row.getId(), row.getSchemaVersion(), instance.getTemplateVersion());
        }
        WriteContext context = writeContext(instance, schema);
        return readView(instance, schema, stored, context);
    }

    /** 可写字段（前端置灰提示；**边界仍在 {@link #save}**）。 */
    public Map<String, Object> writableFields(Long instanceId) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        WriteContext context = writeContext(instance, schema);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("instanceId", instanceId);
        view.put("bizNo", instance.getBizNo());
        view.put("formType", schema.formType());
        view.put("schemaVersion", schema.schemaVersion());
        view.put("templateVersion", instance.getTemplateVersion());
        view.put("state", context.view());
        view.put("fieldCodes", schema.fieldCodes());
        return view;
    }

    /** 实例锁定的表单 schema（AC-09：**不读当前 published**）。 */
    public Map<String, Object> schemaOfInstance(Long instanceId) {
        FlowInstanceRow instance = requireInstance(instanceId);
        FormSchema schema = schemaService.forInstance(instance);
        Map<String, Object> view = new LinkedHashMap<>(schema.view());
        view.put("instanceId", instanceId);
        view.put("templateId", instance.getTemplateId());
        view.put("templateVersion", instance.getTemplateVersion());
        view.put("lockedVersionEvidence",
                "doc/templates.md §3.2 V-02：实例发起时锁定 template_version，剩余节点与表单均按该版本执行（AC-09）");
        return view;
    }

    // ================================================================ 建草稿链路（供 FlowInstanceService 调用）

    /**
     * 校验**已落库**的表单是否满足某档口径（不读入参、不改库）。
     *
     * <p>用途：提交动作的前置闸门（{@link FormSubmitGate}）——「提交前必须通过」的落点。
     */
    public FormValidationReport validateStored(FlowInstanceRow instance, ValidationMode mode) {
        FormSchema schema = schemaService.forInstance(instance);
        FormDataFullRow row = snapshotService.readRow(instance.getFormDataId());
        Map<String, Object> stored = snapshotService.readFields(row == null ? null : row.getFieldsJson());
        applyDefaults(schema, stored);
        canonicalizeAmounts(schema, stored);
        canonicalizePickers(schema, stored);
        Map<String, Object> forValidation = new LinkedHashMap<>(stored);
        injectSensitiveForValidation(schema, forValidation, instance.getFormDataId());
        FormRuleContext context = ruleContext(schema, forValidation, Map.of(), stored, mode, instance);
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.addAll(validator.validate(schema, forValidation, mode,
                LocalDate.now(), instance.getSubmittedAt()));
        ruleRegistry.require(schema.formType()).validate(context, collector);
        return collector.build();
    }

    /**
     * <b>提交前闸门</b>：不通过即抛 400 {@link ErrorCode#FORM_VALIDATION_FAILED}（含全部失败项）。
     *
     * <p>由 {@code FlowEngineService#submit} 在状态迁移**之前**调用 —— 校验失败时一行都不落库
     * （{@code markSubmitted} 还没执行），因此不会出现「拒了但状态已经变了」的半成品。
     */
    public void assertSubmitReady(FlowInstanceRow instance) {
        validateStored(instance, ValidationMode.SUBMIT).fail();
    }

    /** 建草稿前的准备结果（已校验、已归一化的字段 + 敏感字段明文）。 */
    public record PreparedPayload(String fieldsJson, int schemaVersion, Map<String, Object> fields,
                                  String payeeAccountPlain) {
    }

    /**
     * 建草稿前的准备：**DRAFT 档**校验 + 归一化 + 敏感字段剥离。
     *
     * <p>由 {@code FlowInstanceService#create} 在落 {@code form_data} 之前调用，
     * 使「建草稿」这条既有入口同样受 2b.1 的服务端二次校验约束（{@code doc/forms.md} §11.2）。
     * 草稿档不强制无条件必填（见 {@link ValidationMode} 的类注释），但**未知字段、类型、长度、
     * 枚举、金额定点**一律在此拒绝。
     */
    public PreparedPayload prepareDraft(FormSchema schema, Map<String, Object> payload) {
        Set<String> incoming = keysOf(payload);
        rejectUnknownFields(schema, incoming);
        Map<String, Object> values = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        applyDefaults(schema, values);
        canonicalizeAmounts(schema, values);
        canonicalizePickers(schema, values);
        FormRuleContext ruleContext = ruleContext(schema, values, payload, Map.of(), ValidationMode.DRAFT, null);
        FormTypeRules rules = ruleRegistry.require(schema.formType());
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.addAll(validator.validate(schema, values, ValidationMode.DRAFT, LocalDate.now(), null));
        rules.validate(ruleContext, collector);
        collector.build().fail();
        applyPatch(values, rules.normalize(ruleContext), schema, null);
        String payeeAccount = extractSensitive(schema, values);
        return new PreparedPayload(values.isEmpty() ? "{}" : JsonText.write(values), schema.schemaVersion(),
                values, payeeAccount);
    }

    /** 建草稿落库后的敏感字段写入（收款账号单独密文落列，**永不进 fields_json**）。 */
    public void afterDraftCreated(Long formDataId, PreparedPayload prepared) {
        if (formDataId == null || prepared == null) {
            return;
        }
        if (prepared.payeeAccountPlain() != null && !prepared.payeeAccountPlain().isBlank()) {
            formDataMapper.updatePayeeAccountCipher(formDataId, cipherOf(prepared.payeeAccountPlain()));
        }
    }

    // ================================================================ 内部：读取实例 / 上下文

    private FlowInstanceRow requireInstance(Long instanceId) {
        if (instanceId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "instanceId 不能为空");
        }
        // 数据域过滤：域外实例查不到（按 404 处理；AC-02 / AC-17）
        FlowInstanceRow instance = instanceMapper.selectInstanceById(instanceId);
        if (instance == null) {
            throw BizException.notFound("流程实例");
        }
        return instance;
    }

    private WriteContext writeContext(FlowInstanceRow instance, FormSchema schema) {
        List<FlowNodeInstanceRow> nodes = instance.getId() == null ? List.of()
                : nodeInstanceMapper.selectByInstance(instance.getId());
        return writeGuard.resolve(instance, currentPrincipal(), schema.fieldCodes(), nodes);
    }

    private static CurrentUser currentPrincipal() {
        com.oa.common.scope.DataScopeContext context = com.oa.common.scope.DataScopeContext.current();
        return context == null ? null : context.getPrincipal();
    }

    /**
     * 写权限：发起人 / 系统管理员；印鉴单在**审批中**的归还登记另放行节点⑦归档登记人。
     */
    private void requireWriter(FlowInstanceRow instance, WriteContext context, FormSchema schema) {
        CurrentUser principal = currentPrincipal();
        if (principal == null || principal.id() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (principal.hasRole(VisibilityRoles.ADMIN)) {
            return;
        }
        if (principal.id().equals(instance.getInitiatorId())) {
            return;
        }
        boolean sealArchive = "seal".equalsIgnoreCase(schema.formType())
                && context.state() == FormWritePolicy.FormState.APPROVING
                && context.isArchiveNode();
        if (sealArchive) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "只有发起人本人（印鉴单归还登记还可由节点⑦归档登记人，或系统管理员）可以修改该单据的表单字段")
                .withDetail("instanceId", instance.getId())
                .withDetail("state", context.state().name());
    }

    private void rejectUnknownFields(FormSchema schema, Set<String> fields) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String field : fields) {
            if (field == null || field.isBlank()) {
                unknown.add("(空字段名)");
                continue;
            }
            // 系统预留字段（补件说明，doc/forms.md §8 的跨表单共用字段）不属于任何 schema，但合法
            if (FormPayloadValidator.SYSTEM_FIELDS.contains(field)) {
                continue;
            }
            if (!schema.knows(field)) {
                unknown.add(field);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BizException(ErrorCode.FIELD_NOT_IN_SCHEMA,
                    String.format("字段 %s 未在表单模板 %s v%d 中登记，请求已拒绝（禁止夹带未登记字段；"
                                    + "doc/templates.md §2.5「未知键」）",
                            String.join("、", unknown), schema.templateCode(), schema.schemaVersion()))
                    .withDetail("unknownFields", unknown)
                    .withDetail("formType", schema.formType())
                    .withDetail("schemaVersion", schema.schemaVersion());
        }
    }

    /**
     * 把「单独密文落列」的字段回填进**待校验值**（不落库）。
     *
     * <p>为什么必须回填：{@code payee_account} 在 schema 里是**必填**（{@code doc/forms.md} §3），
     * 但按 {@code doc/data-model.md} §8.2 它**不进 {@code fields_json}**、只落
     * {@code form_data.payee_account_cipher}。若不回填，每次保存都会把「收款账号必填」误报成缺项。
     */
    private void injectSensitiveForValidation(FormSchema schema, Map<String, Object> values, Long formDataId) {
        if (values == null || formDataId == null || !schema.knows(FIELD_PAYEE_ACCOUNT)) {
            return;
        }
        if (!ConditionEvaluator.isEmpty(values.get(FIELD_PAYEE_ACCOUNT))) {
            return;
        }
        String cipher = formDataMapper.selectPayeeAccountCipher(formDataId);
        if (cipher == null || cipher.isBlank()) {
            return;
        }
        try {
            values.put(FIELD_PAYEE_ACCOUNT, phoneCipher.decrypt(cipher));
        } catch (RuntimeException ex) {
            log.warn("收款账号解密失败（formDataId={}），本次校验按「未填」处理：{}", formDataId, ex.getMessage());
        }
    }

    // ================================================================ 内部：合并 / 默认值 / 归一化 / 落库

    private static Map<String, Object> merge(Map<String, Object> stored, Map<String, Object> payload) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (stored != null) {
            merged.putAll(stored);
        }
        if (payload != null) {
            payload.forEach((key, value) -> {
                if (value == null) {
                    merged.remove(key);
                } else {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    /**
     * 模板默认值补全（{@code doc/templates.md} §2.2 的扩展键 {@code defaultValue}）。
     *
     * <p>只对两类字段自动补：{@code boolean}（模板用 {@code true}/{@code false} 表达，见
     * {@code doc/dict-seed.md} §6/§7）与 {@code select}（默认值必须是该字段绑定字典的**合法启用项**）。
     * 其余类型与「符号型占位值」（如 {@code cost_bearer} 的 {@code "initiator_company"}，
     * 它是界面预填指令、不是可落库取值）**不补** —— 宁可留空由必填闸门拦，也不写入非法值。
     */
    void applyDefaults(FormSchema schema, Map<String, Object> values) {
        if (values == null) {
            return;
        }
        for (FormFieldDef field : schema.fields()) {
            if (!field.hasDefaultValue() || field.defaultValue() == null || field.defaultValue().isNull()) {
                continue;
            }
            if (values.containsKey(field.code()) && !ConditionEvaluator.isEmpty(values.get(field.code()))) {
                continue;
            }
            switch (field.type()) {
                case BOOLEAN -> {
                    if (field.defaultValue().isBoolean()) {
                        values.put(field.code(), field.defaultValue().asBoolean());
                    }
                }
                case SELECT -> {
                    if (!field.defaultValue().isTextual()) {
                        break;
                    }
                    String code = field.defaultValue().asText();
                    if (field.dictType() == null || validatorDictKnows(field.dictType(), code)) {
                        values.put(field.code(), code);
                    }
                }
                default -> {
                    // 其余类型不自动补（避免把符号型占位值写进 fields_json）
                }
            }
        }
    }

    private boolean validatorDictKnows(String dictType, String code) {
        try {
            return dictService.isValidOption(dictType, code);
        } catch (RuntimeException ex) {
            log.debug("字典 {} 默认值判定失败：{}", dictType, ex.getMessage());
            return false;
        }
    }

    /**
     * 金额落库前的**定点规范化**：把合法金额统一写成 {@code scale = 2} 的字符串
     * （{@code "1250000"} → {@code "1250000.00"}）。
     *
     * <p>依据：{@code doc/forms.md} §1.5「存储：以 {@code DECIMAL(18,2)} 存储，**不使用浮点数**」；
     * {@code doc/data-model.md} §4.4 的示例值写作 {@code "amount": "1250000.00"}。
     * 只做「字符串层面的补零」，全程经 {@link java.math.BigDecimal} 定点（**不出现 double/float**）；
     * 非法金额原样保留，交由校验环节报错（规范化不掩盖错误）。
     */
    void canonicalizeAmounts(FormSchema schema, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (FormFieldDef field : schema.fields()) {
            if (field.type() != com.oa.form.template.schema.FormFieldType.AMOUNT) {
                continue;
            }
            Object raw = values.get(field.code());
            if (raw == null || ConditionEvaluator.isEmpty(raw)) {
                continue;
            }
            var rule = field.rule("amountRange");
            String min = rule != null && rule.hasNonNull("min") ? rule.get("min").asText() : null;
            String max = rule != null && rule.hasNonNull("max") ? rule.get("max").asText() : null;
            Integer scale = rule != null && rule.path("scale").isNumber() ? rule.path("scale").asInt() : 2;
            var parsed = com.oa.form.template.validate.AmountText.parse(raw, min, max, scale);
            if (parsed.ok()) {
                values.put(field.code(), parsed.canonical());
            }
        }
    }

    /**
     * 人员 / 组织选择字段落库前的**规范化**（{@code doc/forms.md} §2 cc_users 行「去重」）。
     *
     * <p>三条不可回退的口径：
     * <ol>
     *   <li><b>单值原样保留</b>：既有草稿/单据的 {@code fields_json} 里 {@code user}/{@code org}
     *       存的是**字符串 id**（与前端单值控件一致），本次改动**不改写**它 ——
     *       历史数据读法（{@code FormSnapshotService#readFields}）与打印/详情一律不受影响；</li>
     *   <li><b>数组去重（保序）</b>：数组形态先 trim、去空、按首次出现顺序去重后再落库，
     *       因此「同一个 id 提交 21 次」既不会被上限拦，也不会在库里留下重复项；</li>
     *   <li>与校验同源：去重口径 = {@link FormPayloadValidator#pickerElements(Object)}，
     *       避免「校验按去重计数、落库却存了重复项」的两套口径。</li>
     * </ol>
     *
     * <p>为什么在**校验之前**调用（与 {@code canonicalizeAmounts} 同一位置）：
     * 上限与存在性都必须判**最终落库形态**，否则「先存 21 条、再单独存 20 条」这类
     * 分两次保存的写法就能绕过上限。
     */
    void canonicalizePickers(FormSchema schema, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (FormFieldDef field : schema.fields()) {
            com.oa.form.template.schema.FormFieldType type = field.type();
            if (type != com.oa.form.template.schema.FormFieldType.USER
                    && type != com.oa.form.template.schema.FormFieldType.ORG) {
                continue;
            }
            Object raw = values.get(field.code());
            if (!(raw instanceof java.util.Collection<?>) && !(raw instanceof Object[])) {
                continue;
            }
            values.put(field.code(), FormPayloadValidator.pickerElements(raw));
        }
    }

    /** 应用归一化补丁（{@code null} 值 = 删除该键）。 */
    void applyPatch(Map<String, Object> values, Map<String, Object> patch, FormSchema schema, WriteContext context) {
        if (patch == null || patch.isEmpty() || values == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (!schema.knows(entry.getKey())) {
                continue;
            }
            if (context != null && !context.writable(entry.getKey())) {
                continue;
            }
            if (entry.getValue() == null) {
                values.remove(entry.getKey());
            } else {
                values.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /** 敏感字段剥离：{@code payee_account} 从 {@code fields_json} 移出，返回明文交调用方加密落列。 */
    String extractSensitive(FormSchema schema, Map<String, Object> values) {
        if (values == null || !schema.knows(FIELD_PAYEE_ACCOUNT)) {
            return null;
        }
        Object raw = values.remove(FIELD_PAYEE_ACCOUNT);
        return raw == null ? null : String.valueOf(raw);
    }

    private void persist(FlowInstanceRow instance, FormSchema schema, Map<String, Object> merged,
                         WriteContext context) {
        Map<String, Object> fields = new LinkedHashMap<>(merged);
        String payeeAccount = extractSensitive(schema, fields);
        snapshotService.write(instance.getFormDataId(), schema, fields, schema.schemaVersion());
        if (payeeAccount == null) {
            return;
        }
        if (payeeAccount.isBlank()) {
            formDataMapper.clearPayeeAccountCipher(instance.getFormDataId());
        } else {
            formDataMapper.updatePayeeAccountCipher(instance.getFormDataId(), cipherOf(payeeAccount));
        }
    }

    private String cipherOf(String plain) {
        // 密文本身就是 ASCII（v1:<keyId>:<base64>），原样落 VARBINARY(255)：
        // 不再套一层 Base64 —— 多一层编码只会让「读回来是什么」更难核对（2026-10-03 收敛）
        return phoneCipher.encrypt(plain);
    }

    // ================================================================ 内部：出参

    private Map<String, Object> readView(FlowInstanceRow instance, FormSchema schema, Map<String, Object> values,
                                         WriteContext context) {
        // 敏感字段（收款账号）**不进** snapshot.fields：它由 `sensitive` 分区按角色脱敏输出，
        // 明文若落进 fields 出参就等于绕过了「仅财务角色与系统管理员可见全值」的口径（PRD §5.3）。
        Map<String, Object> visible = new LinkedHashMap<>(values == null ? Map.of() : values);
        visible.remove(FIELD_PAYEE_ACCOUNT);
        FormRuleContext ruleContext = ruleContext(schema, visible, Map.of(), visible, ValidationMode.DRAFT, instance);
        FormTypeRules rules = ruleRegistry.require(schema.formType());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("instanceId", instance.getId());
        view.put("bizNo", instance.getBizNo());
        view.put("status", instance.getStatus());
        view.put("subStatus", instance.getSubStatus());
        view.put("templateId", instance.getTemplateId());
        view.put("templateVersion", instance.getTemplateVersion());
        view.put("snapshot", snapshotService.snapshotView(schema, visible));
        view.put("state", context.view());
        view.put("formRules", rules.describe(ruleContext));
        view.put("sensitive", sensitiveView(instance, schema));
        return view;
    }

    /**
     * 敏感字段的读取视图：完整值仅财务角色与系统管理员可见，其余角色脱敏
     * （{@code doc/forms.md} §3「payee_account 属敏感字段，列表与详情中默认脱敏（{@code ****1234}），
     * 仅财务角色与系统管理员可见全值」）。
     */
    private Map<String, Object> sensitiveView(FlowInstanceRow instance, FormSchema schema) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (!schema.knows(FIELD_PAYEE_ACCOUNT) || instance.getFormDataId() == null) {
            return view;
        }
        String cipher = formDataMapper.selectPayeeAccountCipher(instance.getFormDataId());
        if (cipher == null || cipher.isBlank()) {
            return view;
        }
        CurrentUser principal = currentPrincipal();
        boolean full = principal != null
                && (principal.hasRole(VisibilityRoles.ADMIN) || VisibilityRoles.isFinance(principal.roleCodes()));
        try {
            String plain = phoneCipher.decrypt(cipher);
            view.put(FIELD_PAYEE_ACCOUNT, full ? plain : maskAccount(plain));
            view.put("payeeAccountMasked", !full);
        } catch (RuntimeException ex) {
            log.warn("收款账号解密失败（instanceId={}）：{}", instance.getId(), ex.getMessage());
            view.put(FIELD_PAYEE_ACCOUNT, maskAccount(null));
            view.put("payeeAccountMasked", true);
        }
        return view;
    }

    /** 账号脱敏（{@code ****1234} 口径；{@code doc/forms.md} §3）。 */
    public static String maskAccount(String plain) {
        if (plain == null || plain.isBlank()) {
            return "****";
        }
        String trimmed = plain.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    // ================================================================ 内部：杂项

    private FormRuleContext ruleContext(FormSchema schema, Map<String, Object> values, Map<String, Object> incoming,
                                        Map<String, Object> stored, ValidationMode mode, FlowInstanceRow instance) {
        return new FormRuleContext(schema, values, keysOf(incoming), stored, mode, instance, LocalDate.now());
    }

    private static Set<String> keysOf(Map<String, Object> payload) {
        return payload == null ? Set.of() : new LinkedHashSet<>(payload.keySet());
    }

    private static FormValidationReport.Collector collectorOf(FormValidationReport report) {
        FormValidationReport.Collector collector = FormValidationReport.collector();
        collector.addAll(report);
        return collector;
    }

    private Long currentNodeInstanceId(FlowInstanceRow instance) {
        if (instance.getId() == null || instance.getCurrentNodeSeq() == null) {
            return null;
        }
        List<FlowNodeInstanceRow> nodes = nodeInstanceMapper.selectByInstance(instance.getId());
        for (FlowNodeInstanceRow node : nodes) {
            if (node.getNodeSeq() != null && node.getNodeSeq().equals(instance.getCurrentNodeSeq())) {
                return node.getId();
            }
        }
        return null;
    }

    private static String describeReturn(Map<String, Object> values) {
        Object status = values == null ? null : values.get("return_status");
        Object date = values == null ? null : values.get("return_date");
        return String.format("归还状态=%s，归还日期=%s", status == null ? "（空）" : status,
                date == null ? "（空）" : date);
    }

    /**
     * 金额角色的只读披露 —— <b>已删除（2026-10-04）</b>。
     *
     * <p>它曾是 {@code FormDataService} 里的一份**副本**（只有 writable/exportable/writableRoles
     * 三个键），与 {@code com.oa.authz.visibility.FormFieldWriteGuard#amountPolicy}（六个键，
     * 另含 {@code amountFieldNames / readOnly / reason}）口径重复；唯一的调用方
     * {@code FormRuleController#fieldGroups} 还硬编码传 {@code null} 主体，导致
     * 「读路径说金额只读、写路径却放行」的展示缺陷。
     *
     * <p>现在读路径直接调用 {@code FormFieldWriteGuard#amountPolicy(当前登录人)} ——
     * 与写路径同一实现、同一主体来源。删除副本是为了让「两套口径」在编译期就不可能再出现。
     */

    /** 选项类字段的合法取值（下拉接口复用）。 */
    public List<String> optionCodes(FormFieldDef field) {
        List<String> codes = new ArrayList<>();
        for (FormFieldDef.Option option : field.options()) {
            codes.add(option.code());
        }
        return codes;
    }
}
