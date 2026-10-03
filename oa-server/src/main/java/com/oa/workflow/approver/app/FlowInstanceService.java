package com.oa.workflow.approver.app;

import com.oa.common.audit.AuditLogWriter;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.api.dto.ApproverDtos.CreateInstanceRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.ReparseView;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.approver.infra.FlowInstanceMapper;
import com.oa.workflow.approver.infra.row.FlowInstanceRow;
import com.oa.workflow.approver.infra.row.FormDataRow;
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
 *   <li><b>提交</b>（{@code POST /flow-instances/{id}/submit}）：{@code draft → approving}，
 *       写 {@code submitted_at} 与 {@code current_node_seq}。**仅此一条迁移**；</li>
 *   <li><b>读快照</b> / <b>重解析</b>：重解析按 templates.md V-06「驳回后重新提交，重新解析审批人快照与
 *       流程版本（按最新模板）」，并把**旧快照写进审计日志**（{@code sys_log}，只追加）。</li>
 * </ol>
 *
 * <h2>TODO（明确不做，避免与后续工作包冲突）</h2>
 * <ul>
 *   <li>{@code TODO(2a.4)}：节点实例（{@code flow_node_instance}）与任务（{@code flow_task}）的生成、
 *       状态迁移矩阵、7.2 联动规则、四个计数闸门的判定；</li>
 *   <li>{@code TODO(2a.4): 按 maxReturnCount / maxSupplementCount 判定} ——
 *       Q6 配置已在 {@code flow_template} 落库并可读回，本服务只把实例的三个计数列初始化为 0；</li>
 *   <li>{@code TODO(2a.5)}：或签/会签/依次的决议执行与驳回（意见 ≥5 字）；</li>
 *   <li>{@code TODO(2b)}：表单字段的服务端二次校验与写入白名单（本服务只落最小的
 *       {@code form_data} 行，{@code fields_json} 原样保存）。</li>
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
    private final Long financeDeptId;
    private final String financeDeptName;

    public FlowInstanceService(ApproverPrecheckService precheckService,
                               ApproverDirectory directory,
                               FlowInstanceMapper instanceMapper,
                               FlowTemplateMapper templateMapper,
                               WorkflowPermissionService permissionService,
                               AuditLogWriter auditLogWriter,
                               com.oa.common.config.OaProperties properties) {
        this.precheckService = precheckService;
        this.directory = directory;
        this.instanceMapper = instanceMapper;
        this.templateMapper = templateMapper;
        this.permissionService = permissionService;
        this.auditLogWriter = auditLogWriter;
        this.financeDeptId = properties.getScope().getFinanceDeptId();
        this.financeDeptName = properties.getScope().getFinanceDeptName() == null
                ? "财务部" : properties.getScope().getFinanceDeptName();
    }

    // ================================================================ 发起

    /**
     * 建草稿实例：预检 → 锁定模板版本 → 固化审批人快照 → 落 {@code form_data} + {@code flow_instance}。
     *
     * <p>预检不通过时抛 400（{@link ErrorCode#APPROVER_RESOLUTION_BLOCKED}），
     * 消息里逐条给出「哪个节点、命中哪条规则、缺什么配置」（AC-11 / AC-19）。
     */
    @Transactional
    public InstanceView create(CreateInstanceRequest request, CurrentUser principal) {
        CurrentUser operator = permissionService.requireInitiator("发起审批单");
        PrecheckRequest precheckRequest = toPrecheckRequest(request);
        // 预检 + 解析一次完成：快照必须与预检结果**同源**（否则会出现「预检通过但快照为空」）
        ApproverPrecheckService.Resolved resolved = precheckService.resolveForSubmit(precheckRequest, operator);
        if (!resolved.report().allowed()) {
            precheckService.logBlocked(resolved.report(), operator);
            StringBuilder builder = new StringBuilder("发起被拒绝：")
                    .append(resolved.report().blockers().size()).append(" 个节点无有效审批人");
            resolved.report().blockers().forEach(blocker -> builder.append(" —— ")
                    .append(ApproverPrecheckService.describeBlocker(blocker)));
            throw new BizException(ErrorCode.APPROVER_RESOLUTION_BLOCKED, builder.toString())
                    .withDetail("blockers", resolved.report().blockers());
        }

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

        FormDataRow formData = new FormDataRow();
        formData.setBizNo(bizNo);
        formData.setFormType(template.getFormType());
        formData.setFieldsJson(fieldsJson(request, context));
        formData.setSchemaVersion(template.getVersion());
        formData.setCreatorId(context.initiatorId());
        instanceMapper.insertFormData(formData);

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

    /** 提交：{@code draft → approving}（**本工作包唯一的实例状态迁移**）。 */
    @Transactional
    public InstanceView submit(Long instanceId, String reason) {
        CurrentUser operator = permissionService.requireInitiator("提交审批单");
        FlowInstanceRow instance = requireInstance(instanceId);
        requireInitiatorOrAdmin(instance, operator);
        if (!"draft".equals(instance.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "只有草稿状态的单据可以提交，当前状态：" + instance.getStatus()
                            + "（其余状态迁移属 2a.4 运行时状态机）");
        }
        Integer firstSeq = firstActiveSeq(instance);
        int updated = instanceMapper.markSubmitted(instanceId, firstSeq);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "单据状态已被他人变更，请刷新后重试");
        }
        auditLogWriter.appendAsCurrentUser("submit", "instance", instanceId, null,
                "{\"reason\":" + JsonText.write(reason == null ? "" : reason) + "}", null, null);
        log.info("流程实例提交：operator={} instanceId={} bizNo={} currentNodeSeq={} reason={}"
                        + "（节点实例与任务生成 TODO(2a.4)）",
                operator.account(), instanceId, instance.getBizNo(), firstSeq, reason);
        return toView(requireInstance(instanceId));
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

    /** 首个未跳过节点的 seq（提交后的当前节点；跳过判断用快照，避免再查模板）。 */
    private Integer firstActiveSeq(FlowInstanceRow instance) {
        ApproverSnapshot snapshot = ApproverSnapshotCodec.read(instance.getApproverSnapshotJson());
        if (snapshot == null || snapshot.nodes().isEmpty()) {
            return 1;
        }
        for (ApproverSnapshot.SnapshotNode node : snapshot.nodes()) {
            if (!Boolean.TRUE.equals(node.skipped()) && !node.approvers().isEmpty()) {
                return node.nodeSeq();
            }
        }
        return snapshot.nodes().get(0).nodeSeq();
    }

    /** 归口部门恒为集团财务部（PRD §6.3：即使②被跳过也记财务部）。 */
    private Long ownerDeptId() {
        Long configuredId = financeDeptId;
        if (configuredId != null) {
            return configuredId;
        }
        return directory.orgByName(financeDeptName).map(OrgNodeView::id).orElse(null);
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

    private static String fieldsJson(CreateInstanceRequest request, RuleRequest context) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (request != null && request.formValues() != null) {
            fields.putAll(request.formValues());
        }
        if (request != null && request.fields() != null) {
            fields.putAll(request.fields());
        }
        return fields.isEmpty() ? "{}" : JsonText.write(fields);
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
