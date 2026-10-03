package com.oa.workflow.approver.app;

import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormDataService;
import com.oa.form.template.schema.FormSchema;
import com.oa.workflow.approver.api.dto.ApproverDtos.CreateInstanceRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseView;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.approver.infra.row.FormDataRow;
import com.oa.workflow.definition.app.FlowConfigPermission;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程实例的「发起」切片（2a.3，{@code oa.workflow.approver.snapshot}）。
 *
 * <h2>本服务只做发起链路，不碰运行时状态机</h2>
 * <ol>
 *   <li><b>建草稿实例</b>（{@code POST /flow-instances}）：先做发起前预检（空候选人即拒绝），
 *       然后**一次性**解析全部节点并固化为快照，同时把
 *       {@code template_id + template_version} **锁成发起时的那一行**
 *       —— 这就是 REQ-FLOW-006 / AC-09 的「在途实例锁版本」；</li>
 *   <li><b>提交</b>（{@code POST /flow-instances/{id}/submit}）：{@code draft → approving}。
 *       <b>2a.4 起由 {@code com.oa.workflow.runtime.app.FlowEngineService} 接管</b>
 *       —— 提交不再是「一条迁移」，而要同时建节点实例、跳过命中跳过条件的节点、为首个节点产生待办；
 *       本类的 {@link #submit} 只保留为**显式失败**的兼容入口（避免有人误用旧路径）。</li>
 *   <li><b>读快照</b> / <b>重解析</b>：重解析按 templates.md V-06「驳回后重新提交，重新解析审批人快照与
 *       流程版本（按最新模板）」，并把**旧快照写进审计日志**（{@code sys_log}，只追加）。</li>
 * </ol>
 *
 * <h2>TODO（明确不做，避免与后续工作包冲突）</h2>
 * <ul>
 *   <li>{@code TODO(2a.4)}：节点实例与任务的生成、状态迁移矩阵、7.2 联动规则、四个计数闸门的判定
 *       —— <b>已由 {@code com.oa.workflow.runtime.app.FlowEngineService} 完成</b>；</li>
 *   <li>{@code TODO(2a.5)}：或签/会签/依次的决议执行与驳回（意见 ≥5 字）
 *       —— <b>已由 {@code com.oa.workflow.task.domain.TaskDecisionPolicy} + 引擎完成</b>；</li>
 *   <li>{@code TODO(2b)}：表单字段的服务端二次校验与写入白名单
 *       —— <b>已由 {@code com.oa.form.app.FormDataService#prepareDraft} 在 {@link #create} 内消费</b>：
 *       建草稿前按**该模板版本**的 {@code form_schema_json} 做一次服务端校验（未知字段/类型/长度/
 *       枚举/金额定点），并把归一化后的字段与 {@code schema_version} 一并固化
 *       （doc/templates.md §3.2 V-03 / doc/forms.md §11.2）。</li>
 * </ul>
 */
@Service
public class FlowInstanceService {

    private static final Logger log = LoggerFactory.getLogger(FlowInstanceService.class);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApproverPrecheckService precheckService;
    private final ApproverDirectory directory;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTemplateMapper templateMapper;
    private final WorkflowPermissionService permissionService;
    private final AuditLogWriter auditLogWriter;
    private final com.oa.form.template.schema.FormSchemaService formSchemaService;
    private final com.oa.form.app.FormDataService formDataService;
    private final Long financeDeptId;
    private final String financeDeptName;

    public FlowInstanceService(ApproverPrecheckService precheckService,
                               ApproverDirectory directory,
                               FlowInstanceMapper instanceMapper,
                               FlowTemplateMapper templateMapper,
                               WorkflowPermissionService permissionService,
                               AuditLogWriter auditLogWriter,
                               com.oa.form.template.schema.FormSchemaService formSchemaService,
                               com.oa.form.app.FormDataService formDataService,
                               com.oa.common.config.OaProperties properties) {
        this.precheckService = precheckService;
        this.directory = directory;
        this.instanceMapper = instanceMapper;
        this.templateMapper = templateMapper;
        this.permissionService = permissionService;
        this.auditLogWriter = auditLogWriter;
        this.formSchemaService = formSchemaService;
        this.formDataService = formDataService;
        this.financeDeptId = properties.getScope().getFinanceDeptId();
        this.financeDeptName = properties.getScope().getFinanceDeptName() == null
                ? "财务部" : properties.getScope().getFinanceDeptName();
    }

    // ================================================================ 发起

    /**
     * 建**草稿**实例：解析（不阻断）→ 锁定模板版本 → 固化审批人快照 → 落 {@code form_data} + {@code flow_instance}。
     *
     * <h2>空候选人预检<b>不在</b>这里（2026-10-04 收敛，AC-19 移到提交）</h2>
     * <p>改前本方法在预检不通过时抛 400 {@link ErrorCode#APPROVER_RESOLUTION_BLOCKED}，
     * 于是「保存草稿」被要求「审批人此刻就能全部解析出来」。三条反证：
     * <ol>
     *   <li>预检的目的（AC-11 / AC-19 / REQ-FLOW-012）是<b>不允许带着空审批人启动审批流</b>——
     *       那是**提交发起**（{@code submit}）的职责，草稿还没有任何审批流；</li>
     *   <li>组织负责人尚未配好时，用户<b>连草稿都存不下来</b>，填了一半的内容无处安放；</li>
     *   <li>更硬的一条：⑤集团分管领导按**事项类别**解析（{@code group_leader} 规则读
     *       {@code request.category}），而类别正是用户在表单里正在填的字段 →
     *       「填类别才能存草稿、存草稿才能填类别」的鸡生蛋。</li>
     * </ol>
     * <p>因此本方法只做：模板解析 + 发起人事实快照（{@code context}，写 {@code flow_instance}
     * 的组织/公司列）+ 表单二次校验（{@code prepareDraft}）+ 落库。预检报告仍会被算出并
     * **记 WARN 留痕**（不静默），但<b>不阻断</b>。
     *
     * <p><b>AC-19 未削弱</b>：拦截点在 {@link #prepareSubmitSnapshot}（提交与重提都必经），
     * 那里的空候选人一律 40007，且文案仍是「哪个节点、命中哪条规则、缺什么配置」。
     *
     * <p><b>闸门 = {@code flow}（与控制器入口同源，2026-10-04 收紧）</b>：本方法作**第二层**兜底，
     * 判据与 {@code FlowInstanceController#create} 逐字一致 —— 防的是「绕过控制器直调服务」把
     * 「只持 {@code admin:flow}」的主体放进来（该主体在动作面 {@code submit} 处本就会被拒）。
     */
    @Transactional
    public InstanceView create(CreateInstanceRequest request, CurrentUser principal) {
        CurrentUser operator = permissionService.requirePermission("发起审批单",
                FlowConfigPermission.FLOW_USE);
        PrecheckRequest precheckRequest = toPrecheckRequest(request);
        // 解析一次：快照与（可能的）预检报告同源；`context` 提供 initiator_org/company_path 等列值
        ApproverPrecheckService.Resolved resolved = precheckService.resolveForSubmit(precheckRequest, operator);
        // 草稿期不阻断，但必须留痕（改前这里是 40007 —— 见类注释的三条反证）
        precheckService.logDraftNotReady(resolved.report(), operator);

        FlowTemplate template = resolved.template();
        RuleRequest context = resolved.context();
        String bizNo = request != null && request.bizNo() != null && !request.bizNo().isBlank()
                ? request.bizNo().trim()
                : generateBizNo(template.getCode());
        if (countByBizNo(bizNo) > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "单号已存在：" + bizNo);
        }

        ApproverSnapshot snapshot = ApproverSnapshotCodec.assemble(template.getId(), template.getCode(),
                template.getVersion(), context, resolved.resolutions());
        String snapshotJson = ApproverSnapshotCodec.write(snapshot);

        // ------------------------------------------------------------------
        // 2b.1 服务端二次校验（**建草稿这条既有入口同样受约束**）：
        //   · 按**该模板版本**的 form_schema_json 逐字段校验（未知字段/类型/长度/枚举/金额定点）；
        //   · 归一化（模板默认值 + linkage.clearWhen）后固化，并把 schema_version 一并写入
        //     （doc/templates.md §3.2 V-03：「提交时固化字段定义与值」）；
        //   · 校验不通过 → 400 FORM_VALIDATION_FAILED，**一行都不落库**。
        // ------------------------------------------------------------------
        Map<String, Object> payload = payloadOf(request);
        FormSchema schema = formSchemaService.forTemplate(template.getId(), template.getVersion());
        FormDataService.PreparedPayload prepared = formDataService.prepareDraft(schema, payload);

        FormDataRow formData = new FormDataRow();
        formData.setBizNo(bizNo);
        formData.setFormType(template.getFormType());
        formData.setFieldsJson(prepared.fieldsJson());
        formData.setSchemaVersion(prepared.schemaVersion());
        formData.setCreatorId(context.initiatorId());
        instanceMapper.insertFormData(formData);
        // 敏感字段（资金单收款账号）单独密文落列，**不进 fields_json**（doc/data-model.md §8.2）
        formDataService.afterDraftCreated(formData.getId(), prepared);

        FlowInstanceRow instance = new FlowInstanceRow();
        instance.setBizNo(bizNo);
        instance.setTemplateId(template.getId());
        instance.setTemplateVersion(template.getVersion());
        instance.setFormDataId(formData.getId());
        instance.setFormType(template.getFormType());
        instance.setCategory(context.category());
        instance.setInitiatorId(context.initiatorId());
        instance.setInitiatorOrgId(context.initiatorOrgId());
        instance.setInitiatorCompanyId(context.initiatorCompanyId());
        instance.setInitiatorOrgPath(context.initiatorOrgPath());
        instance.setApproverSnapshotJson(snapshotJson);
        instance.setStatus("draft");
        instance.setCurrentNodeSeq(null);
        instance.setOwnerDeptId(ownerDeptId());
        instanceMapper.insertInstance(instance);

        log.info("流程实例建草稿：operator={} bizNo={} template={} v{} 锁定版本与快照已固化（AC-09）",
                operator.account(), bizNo, template.getCode(), template.getVersion());
        return toView(requireInstance(instance.getId()));
    }

    /** 提交：{@code draft → approving} —— **2a.4 起由运行时引擎接管**（见类注释 TODO 说明）。 */
    public InstanceView submit(Long instanceId, String reason) {
        throw new BizException(ErrorCode.INTERNAL_ERROR,
                "提交已由 FlowEngineService 接管（2a.4 运行时状态机）：请在 FlowInstanceController 走引擎入口");
    }

    /**
     * <b>提交发起的预检闸门 + 快照固化</b>（AC-11 / AC-19 / REQ-FLOW-011）。
     *
     * <h2>为什么在提交而不是建草稿</h2>
     * <p>见 {@link #create} 的类注释：草稿是填写中的内容，**发起**才需要「审批人全部可解析」。
     * 本方法由 {@code FlowEngineService#submit} 在状态迁移（{@code markSubmitted}）**之前**调用，
     * 因此拦截时一行都不落库（不会出现「拒了但状态已经变了」）。
     *
     * <h2>三条不可回退的口径</h2>
     * <ol>
     *   <li><b>锁定版本解析</b>：预检按 {@code instance.templateId}（= 发起时锁定的模板行）
     *       解析，而不是「当前已发布」—— AC-09「在途实例按其发起时版本执行」。
     *       （重提走 {@code reparse}，它显式换到最新已发布版本后再回到这里。）</li>
     *   <li><b>拦截不削弱</b>：命中空候选人 → 400 {@link ErrorCode#APPROVER_RESOLUTION_BLOCKED}，
     *       文案仍逐条给出「哪个节点、命中哪条规则、缺什么配置」（{@code ApproverPrecheckService#assertAllowed}）；</li>
     *   <li><b>同源固化</b>：通过后把**同一次解析**的快照写回 {@code flow_instance.approver_snapshot_json}
     *       （REQ-FLOW-011「发起时解析并固化」）—— 草稿期冻结的那份可能是在组织负责人尚未配好时算出的，
     *       提交时必须刷新，否则会带着空/陈旧快照进入审批流。</li>
     * </ol>
     *
     * @return 刷新后的快照（调用方必须使用**本返回值**，不要回读入参行里的旧 JSON）
     */
    @Transactional
    public ApproverSnapshot prepareSubmitSnapshot(FlowInstanceRow instance, CurrentUser operator) {
        if (instance == null || instance.getId() == null) {
            throw BizException.notFound("流程实例");
        }
        Map<String, Object> formValues = formValuesOf(instance);
        // 事项类别以**表单当前值**为准（缺省回落实例已锁定的值），并在提交这一刻固化回实例列 ——
        // 理由见 categoryForSubmit 与 FlowInstanceMapper#updateCategory 的注释。
        String category = categoryForSubmit(instance, formValues);
        if (category != null && !category.equals(instance.getCategory())) {
            instanceMapper.updateCategory(instance.getId(), category);
            log.info("提交发起：事项类别取表单值并固化 instanceId={} {} → {}",
                    instance.getId(), instance.getCategory(), category);
        }
        PrecheckRequest request = new PrecheckRequest(instance.getTemplateId(), instance.getTemplateVersion(),
                instance.getInitiatorId(), instance.getFormType(), category,
                null, null, null, null, formValues);
        ApproverPrecheckService.Resolved resolved = precheckService.resolveForSubmit(request, operator);
        // 空候选人 → 40007（AC-19 的唯一拦截点）。日志与判定分开：判定是**纯函数**（可被单测真实覆盖），
        // 日志走协作者（生产=WARN 留痕，替身=可断言「确实记了」）。
        if (resolved.report() != null && !resolved.report().allowed()) {
            precheckService.logBlocked(resolved.report(), operator);
        }
        ApproverPrecheckService.assertAllowed(resolved.report());

        ApproverSnapshot snapshot = ApproverSnapshotCodec.assemble(resolved.template().getId(),
                resolved.template().getCode(), resolved.template().getVersion(),
                resolved.context(), resolved.resolutions());
        String json = ApproverSnapshotCodec.write(snapshot);
        instanceMapper.updateSnapshot(instance.getId(), json);
        log.info("提交发起：审批人快照已按锁定版本 v{} 重新解析并固化 instanceId={} initiator={} 节点数={}",
                resolved.template().getVersion(), instance.getId(), instance.getInitiatorId(),
                resolved.resolutions() == null ? 0 : resolved.resolutions().size());
        return snapshot;
    }

    // ================================================================ 快照

    /** 读取快照（**数据域过滤**：域外实例按 404 处理）。 */
    public ApproverSnapshot snapshot(Long instanceId) {
        permissionService.requireInitiator("查看审批人快照");
        FlowInstanceRow instance = requireInstance(instanceId);
        return ApproverSnapshotCodec.read(instance.getApproverSnapshotJson());
    }

    /** 实例详情（含快照）。 */
    public InstanceView detail(Long instanceId) {
        permissionService.requireInitiator("查看审批单");
        return toView(requireInstance(instanceId));
    }

    /** 实例列表（可按模板/版本/状态/发起人过滤；**数据域过滤**）。 */
    public List<InstanceView> list(Long templateId, Integer templateVersion, String status, Long initiatorId,
                                   Integer limit) {
        permissionService.requireInitiator("查看审批单列表");
        List<InstanceView> views = new ArrayList<>();
        for (FlowInstanceRow row : instanceMapper.selectInstances(templateId, templateVersion, blankToNull(status),
                initiatorId, limit == null ? 100 : Math.max(limit, 1))) {
            views.add(toView(row));
        }
        return views;
    }

    /**
     * 重新解析快照（驳回重提，templates.md V-06）。
     *
     * <p>语义：
     * <ol>
     *   <li>按**当前已发布**模板版本重新解析（新单据走新版本；重提同样走最新版本）；</li>
     *   <li>把 {@code template_id + template_version} 一并更新为新的锁定版本；</li>
     *   <li>**旧快照写进审计日志**（{@code sys_log}，只追加），留痕可回溯；</li>
     *   <li>重新解析后若出现空候选人 → 拒绝重提（与首次发起同一闸门，AC-11 不因重提而放宽）。</li>
     * </ol>
     */
    @Transactional
    public ReparseView reparse(Long instanceId, ReparseRequest request) {
        CurrentUser operator = permissionService.requireInitiator("重新解析审批人快照");
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, operator);
        if (!"draft".equals(instance.getStatus()) && !"rejected".equals(instance.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "只有草稿或已驳回的单据可以重新解析审批人快照，当前状态：" + instance.getStatus());
        }

        FlowTemplate latest = latestPublished(instance.getFormType());
        PrecheckRequest precheckRequest = new PrecheckRequest(latest.getId(), null, instance.getInitiatorId(),
                latest.getFormType(), instance.getCategory(), null, null, null, null,
                formValuesOf(instance));
        ApproverPrecheckService.Resolved resolved = precheckService.resolveForSubmit(precheckRequest, operator);

        Integer previousVersion = instance.getTemplateVersion();
        if (!resolved.report().allowed()) {
            auditLogWriter.appendAsCurrentUser("reparse_snapshot_rejected", "instance", instanceId, null,
                    JsonText.write(Map.of("blockers", resolved.report().blockers())), null, null);
            return new ReparseView(instanceId, previousVersion, latest.getVersion(), false,
                    resolved.report().blockers(), "重新解析后有节点无有效审批人，已拒绝重提（AC-11 不因重提而放宽）");
        }

        ApproverSnapshot snapshot = ApproverSnapshotCodec.assemble(latest.getId(), latest.getCode(),
                latest.getVersion(), resolved.context(), resolved.resolutions());
        String newJson = ApproverSnapshotCodec.write(snapshot);
        instanceMapper.updateSnapshot(instanceId, newJson);
        // 旧快照留审计（templates.md V-06「旧快照在审计日志中保留」）
        auditLogWriter.appendAsCurrentUser("reparse_snapshot", "instance", instanceId,
                instance.getApproverSnapshotJson(), newJson, null, null);
        log.info("审批人快照重新解析：operator={} instanceId={} v{} → v{} reason={}",
                operator.account(), instanceId, previousVersion, latest.getVersion(),
                request == null ? null : request.reason());
        return new ReparseView(instanceId, previousVersion, latest.getVersion(), true, List.of(),
                "快照已按最新已发布版本 v" + latest.getVersion() + " 重新解析，旧快照已写入审计日志");
    }

    // ================================================================ 内部

    /** 单号唯一性（全局口径：包在系统上下文里执行，避免域外重号）。 */
    private int countByBizNo(String bizNo) {
        return systemScope(() -> instanceMapper.countByBizNo(bizNo));
    }

    /** 该模板（可指定版本）下的在途实例数（AC-09 的可观测口径）。 */
    public int inFlightCount(Long templateId, Integer templateVersion) {
        return systemScope(() -> instanceMapper.countInFlightByTemplate(templateId, templateVersion));
    }

    private FlowTemplate latestPublished(String formType) {
        List<FlowTemplate> published = templateMapper.selectByCodeAndStatus(formType, TemplateStatus.PUBLISHED.code());
        if (published.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "单据类型「" + formType + "」没有已发布的流程模板版本");
        }
        return published.get(0);
    }

    /** 实例读取：走调用人数据域（域外 → 404）。 */
    private FlowInstanceRow requireInstance(Long instanceId) {
        if (instanceId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "instanceId 不能为空");
        }
        FlowInstanceRow instance = instanceMapper.selectInstanceById(instanceId);
        if (instance == null) {
            throw BizException.notFound("流程实例");
        }
        return instance;
    }

    private void requireInitiatorOrAdmin(FlowInstanceRow instance, CurrentUser operator) {
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (permissionService.isSuperAdmin(operator)) {
            return;
        }
        if (!operator.id().equals(instance.getInitiatorId())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "只有发起人本人（或系统管理员）可以对该单据执行本操作");
        }
    }

    /** 归口部门恒为集团财务部（PRD §6.3：即使②被跳过也记财务部）。 */
    private Long ownerDeptId() {
        Long configuredId = financeDeptId;
        if (configuredId != null) {
            return configuredId;
        }
        return directory.orgByName(financeDeptName).map(OrgNodeView::id).orElse(null);
    }

    /**
     * 提交发起时的**事项类别**口径：以**表单当前值**（{@code fields_json.category}）为准，
     * 缺省回落到实例上已锁定的值。
     *
     * <p>为什么不是直接读 {@code flow_instance.category}：草稿期该列允许为空（见 {@link #create}），
     * 而事项单的类别正是用户在表单里选的字段。若提交时仍用建草稿那一刻的值，用户后来在草稿里选的
     * 类别**不参与 ⑤集团分管领导的解析** → 「填了类别仍被 40007 拦住」的死路。
     * 类别在**发起后不可改判**（{@code doc/forms.md} §2），所以「发起 = 提交」这一刻的表单值才是
     * 正确的快照来源（{@code doc/data-model.md} §7.1 的 {@code basis.category} 同此口径）。
     *
     * <p>四类模板都有 {@code category} 字段（合同/印鉴为 locked 的固定值，资金默认 {@code economy}），
     * 因此本方法对四类单据都取得到值；表单里确实没有该键时回落实例列（可能是 {@code null}，
     * 由预检闸门按「请在发起时确定事项类别」拦截）。
     */
    private static String categoryForSubmit(FlowInstanceRow instance, Map<String, Object> formValues) {
        Object raw = formValues == null ? null : formValues.get("category");
        String fromForm = raw == null ? null : String.valueOf(raw).trim();
        return fromForm == null || fromForm.isEmpty() ? instance.getCategory() : fromForm;
    }

    /** 读回该实例的表单字段值（重解析时用于跳过条件求值）；读不到则返回空表。 */
    private Map<String, Object> formValuesOf(FlowInstanceRow instance) {
        String json = instance.getFormDataId() == null ? null
                : systemScope(() -> instanceMapper.selectFormDataFields(instance.getFormDataId()));
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = JsonText.read(json);
            if (node == null || !node.isObject()) {
                return Map.of();
            }
            Map<String, Object> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(),
                    entry.getValue() == null || entry.getValue().isNull() ? null
                            : entry.getValue().isBoolean() ? entry.getValue().asBoolean()
                            : entry.getValue().isNumber() ? entry.getValue().decimalValue()
                            : entry.getValue().asText()));
            return values;
        } catch (IllegalArgumentException ex) {
            log.warn("实例 {} 的表单字段值无法解析，重解析将按「未涉及费用以外」的默认口径：{}",
                    instance.getId(), ex.getMessage());
            return Map.of();
        }
    }

    private static Map<String, Object> payloadOf(CreateInstanceRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (request != null && request.formValues() != null) {
            fields.putAll(request.formValues());
        }
        if (request != null && request.fields() != null) {
            fields.putAll(request.fields());
        }
        return fields;
    }

    private static PrecheckRequest toPrecheckRequest(CreateInstanceRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        return new PrecheckRequest(request.templateId(), request.templateVersion(), request.initiatorId(),
                request.formType(), request.category(), request.involveCost(), request.initiatorPicks(),
                request.collabDeptIds(), request.collabSelfExcludeDeptIds(), request.formValues());
    }

    /** 单号生成：{@code OA-YYYY-NNNNNN}（doc/data-model.md §4.3 的 biz_no 口径）。 */
    private String generateBizNo(String code) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        for (int attempt = 0; attempt < 50; attempt++) {
            long sequence = System.nanoTime() % 1_000_000L;
            String candidate = "OA-" + year + "-" + String.format("%06d", sequence);
            if (countByBizNo(candidate) == 0) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.CONFLICT, "无法生成唯一单号，请稍后重试");
    }

    private <T> T systemScope(Supplier<T> action) {
        DataScopeContext previous = DataScopeContext.current();
        DataScopeContext.set(DataScopeContext.system());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(previous);
            }
        }
    }

    /** 行 → 出参（快照按原文本解析，保证出参与库中内容一致）。 */
    public static InstanceView toView(FlowInstanceRow row) {
        if (row == null) {
            return null;
        }
        ApproverSnapshot snapshot = null;
        try {
            snapshot = ApproverSnapshotCodec.read(row.getApproverSnapshotJson());
        } catch (IllegalArgumentException ex) {
            log.warn("实例 {} 的审批人快照无法解析：{}", row.getId(), ex.getMessage());
        }
        return new InstanceView(row.getId(), row.getBizNo(), row.getTemplateId(), row.getTemplateVersion(),
                snapshot == null ? null : snapshot.templateCode(), row.getFormType(), row.getCategory(),
                row.getInitiatorId(), row.getInitiatorOrgId(), row.getInitiatorCompanyId(),
                row.getInitiatorOrgPath(), row.getStatus(), row.getSubStatus(), row.getCurrentNodeSeq(),
                row.getOwnerDeptId(), format(row.getSubmittedAt()), format(row.getCreatedAt()), snapshot);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
