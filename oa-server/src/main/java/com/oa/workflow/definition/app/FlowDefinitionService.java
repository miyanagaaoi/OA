package com.oa.workflow.definition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.CheckItemView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.CheckRuleView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.DecisionResolveView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.GatePolicyRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.GatePolicyView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NewVersionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeApproverRuleRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeDecisionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeOrderRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodePolicyRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeSkipConditionRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.NodeView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PrePublishReportView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.PublishRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateDetailView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateUpdateRequest;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.TemplateView;
import com.oa.workflow.definition.api.dto.FlowDefinitionDtos.ValidationView;
import com.oa.workflow.definition.domain.FlowDefinitionEnums;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeType;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.SignPolicy;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowGateEnums;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import com.oa.workflow.definition.domain.FlowGatePolicy;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程模板与节点定义应用服务（2a.2，{@code oa.workflow.definition.*}）。
 *
 * <h2>本服务负责的硬约束</h2>
 * <ol>
 *   <li><b>版本化与在途锁版本</b>（REQ-FLOW-006 / AC-09）：新版本**累积**不覆盖；
 *       开新草稿 → 改配置 → 发布（原 published 转 archived）；在途实例只认发起时写入的
 *       {@code flow_instance.template_id}，因此改模板天然不影响它们（{@code TemplateVersionPolicy}）；</li>
 *   <li><b>写只发生在草稿上</b>：{@code published} / {@code archived} 一律 409
 *       （templates.md §3.3 / §4.3）；</li>
 *   <li><b>校验只有一套</b>：写接口的即时校验与发布前 dry-run 共用
 *       {@link NodeDefinitionValidator}（避免「能存进去但发布不了」）；</li>
 *   <li><b>主干必填节点不可删</b>：{@link RequiredNodePolicy}；</li>
 *   <li><b>Q6/Q7 闸门配置</b>：模板级读写 + 取值范围校验（{@link FlowGatePolicy}），
 *       **不做**计数判定与超时调度（消费点是 2a.4 / 阶段 3，见
 *       {@link FlowGateEnums#COUNTER_TODO} / {@link FlowGateEnums#DEADLINE_TODO}）。</li>
 * </ol>
 *
 * <h2>签名策略与超时只做「配置与校验」</h2>
 * <p>本服务**不**产生签名记录、**不**注册定时器（属阶段 3）；{@code timeout_hours} 只校验取值
 * （≥24h）并随模板/快照下发。
 */
@Service
public class FlowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionService.class);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 换序时的临时 seq 偏移（避开 {@code uk_flow_node_seq} 的唯一键冲突）。 */
    private static final int TEMP_SEQ_OFFSET = 1000;

    private final FlowTemplateMapper templateMapper;
    private final FlowNodeMapper nodeMapper;
    private final WorkflowPermissionService permissionService;

    /** 「最近一次发布前校验」的进程内缓存（{@code GET .../pre-publish-check/latest}）。 */
    private final Map<Long, PrePublishReportView> latestChecks = new ConcurrentHashMap<>();

    public FlowDefinitionService(FlowTemplateMapper templateMapper, FlowNodeMapper nodeMapper,
                                 WorkflowPermissionService permissionService) {
        this.templateMapper = templateMapper;
        this.nodeMapper = nodeMapper;
        this.permissionService = permissionService;
    }

    // ================================================================ 查询

    /** 模板列表（{@code GET /flow-templates}）：按 code / formType / status 过滤，全部为空即全量。 */
    public List<TemplateView> list(String code, String formType, String status) {
        permissionService.requireTemplateRead();
        List<FlowTemplate> templates = templateMapper.selectTemplates(blankToNull(code), blankToNull(formType),
                normalizeStatus(status));
        List<TemplateView> views = new ArrayList<>(templates.size());
        for (FlowTemplate template : templates) {
            views.add(toView(template));
        }
        return views;
    }

    /** 模板详情（含节点，按 seq）。 */
    public TemplateDetailView detail(Long templateId) {
        permissionService.requireTemplateRead();
        FlowTemplate template = requireTemplate(templateId);
        return new TemplateDetailView(toView(template), nodesOf(templateId));
    }

    /** 按版本查询（{@code GET /flow-templates/{id}/versions/{version}}）。 */
    public TemplateDetailView detailByVersion(Long templateId, Integer version) {
        permissionService.requireTemplateRead();
        FlowTemplate base = requireTemplate(templateId);
        FlowTemplate template = templateMapper.selectByCodeAndVersion(base.getCode(), version);
        if (template == null) {
            throw BizException.notFound("流程模板版本 " + base.getCode() + " v" + version);
        }
        return new TemplateDetailView(toView(template), nodesOf(template.getId()));
    }

    /** 版本历史（同一 {@code code} 的全部版本，倒序）。 */
    public List<TemplateView> versions(Long templateId) {
        permissionService.requireTemplateRead();
        FlowTemplate base = requireTemplate(templateId);
        List<TemplateView> views = new ArrayList<>();
        for (FlowTemplate template : TemplateVersionPolicy.byVersionDesc(templateMapper.selectByCode(base.getCode()))) {
            views.add(toView(template));
        }
        return views;
    }

    /** 模板节点清单。 */
    public List<NodeView> nodes(Long templateId) {
        permissionService.requireTemplateRead();
        requireTemplate(templateId);
        return nodesOf(templateId);
    }

    /** 单个节点视图（{@code GET /flow-nodes/{id}/decision|policy|skip-condition|approver-rule} 的共用读取）。 */
    public NodeView node(Long nodeId) {
        permissionService.requireTemplateRead();
        FlowNode node = requireNode(nodeId);
        return toView(node, requireTemplate(node.getTemplateId()));
    }

    /** 发布前校验规则清单。 */
    public List<CheckRuleView> checkRules() {
        permissionService.requireTemplateRead();
        List<CheckRuleView> rules = new ArrayList<>();
        PrePublishChecker.RULES.forEach((rule, title) -> rules.add(new CheckRuleView(rule, title)));
        return rules;
    }

    // ================================================================ 版本管理

    /**
     * 基于已发布（或已归档）版本开新草稿版本（{@code version + 1}）。
     *
     * <p>依据 templates.md §4.1 第 4 步、V-01（回滚 = 把旧版本重新发布为新版本）、
     * V-08（版本号单调递增、不复用）。
     */
    @Transactional
    public TemplateView newVersion(Long templateId, NewVersionRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate source = requireTemplate(templateId);
        List<FlowTemplate> sameCode = templateMapper.selectByCode(source.getCode());

        FlowTemplate draft = TemplateVersionPolicy.draft(sameCode);
        if (draft != null) {
            throw new BizException(ErrorCode.FLOW_DRAFT_ALREADY_EXISTS,
                    "单据类型「" + source.getCode() + "」已存在草稿版本 v" + draft.getVersion()
                            + "（id=" + draft.getId() + "），请先发布或归档该草稿")
                    .withDetail("draftTemplateId", draft.getId());
        }

        FlowTemplate base = source;
        if (request != null && request.fromVersion() != null) {
            base = TemplateVersionPolicy.requireLocked(sameCode, request.fromVersion(), source.getCode());
        }
        if (base.statusEnum() == TemplateStatus.DRAFT) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "不能基于草稿版本 v" + base.getVersion() + " 开新版本；请基于已发布/已归档版本（templates.md §4.1）");
        }
        if (source.statusEnum() == TemplateStatus.DRAFT) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "当前模板本身是草稿（v" + source.getVersion() + "），请直接编辑它或先发布");
        }

        int nextVersion = TemplateVersionPolicy.nextVersion(sameCode);
        TemplateVersionPolicy.assertVersionFresh(sameCode, nextVersion);
        int nodeCount = nodeMapper.countByTemplateId(base.getId());

        FlowTemplate created = new FlowTemplate();
        created.setCode(base.getCode());
        created.setName(request != null && request.name() != null && !request.name().isBlank()
                ? request.name() : base.getName());
        created.setFormType(base.getFormType());
        created.setVersion(nextVersion);
        created.setStatus(TemplateStatus.DRAFT.code());
        created.setNodeCount(nodeCount);
        created.setFormSchemaJson(base.getFormSchemaJson());
        created.applyGatePolicy(base.gatePolicy());
        created.setCreatedBy(operator.id());
        created.setUpdatedBy(operator.id());
        templateMapper.insertTemplate(created);
        nodeMapper.cloneNodes(base.getId(), created.getId());

        log.info("流程模板开新草稿版本：operator={} code={} from={} v{} -> v{} templateId={}",
                operator.account(), base.getCode(), base.getVersion(), base.getVersion(), nextVersion, created.getId());
        return toView(requireTemplate(created.getId()));
    }

    /**
     * 发布草稿版本：**先跑发布前校验**（不通过即 400），再把同单据类型的原 published 转 archived。
     *
     * <p>校验在**同一事务内**重新执行，不信任 {@code /pre-publish-check} 的缓存结果 ——
     * 避免「校验通过后又被别人改了配置」的 TOCTOU。
     */
    @Transactional
    public TemplateView publish(Long templateId, PublishRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate template = requireTemplate(templateId);
        TemplateVersionPolicy.assertEditable(template);

        List<FlowNode> nodes = nodeMapper.selectByTemplateId(templateId);
        List<DefinitionProblem> problems = NodeDefinitionValidator.violations(template, nodes);
        if (!problems.isEmpty()) {
            String message = String.join("；", problems.stream().map(DefinitionProblem::describe).toList());
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "发布被拒绝（发布前校验未通过）：" + message)
                    .withDetail("templateId", templateId)
                    .withDetail("problems", problems.stream().map(DefinitionProblem::describe).toList());
        }

        templateMapper.archiveOtherPublished(template.getCode(), templateId, operator.id());
        templateMapper.updateNodeCount(templateId, nodes.size(), operator.id());
        templateMapper.updateStatus(templateId, TemplateStatus.PUBLISHED.code(), operator.id());

        log.info("流程模板发布：operator={} code={} v{} templateId={} reason={} 原 published 版本已转 archived（在途实例不受影响）",
                operator.account(), template.getCode(), template.getVersion(), templateId,
                request == null ? null : request.reason());
        return toView(requireTemplate(templateId));
    }

    /** 归档版本：只阻止**新实例**使用，在途实例继续执行（templates.md V-05）。 */
    @Transactional
    public TemplateView archive(Long templateId, PublishRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate template = requireTemplate(templateId);
        if (template.statusEnum() == TemplateStatus.ARCHIVED) {
            return toView(template);
        }
        templateMapper.updateStatus(templateId, TemplateStatus.ARCHIVED.code(), operator.id());
        log.info("流程模板归档：operator={} code={} v{} templateId={} reason={}（在途实例继续执行）",
                operator.account(), template.getCode(), template.getVersion(), templateId,
                request == null ? null : request.reason());
        return toView(requireTemplate(templateId));
    }

    /** 更新草稿模板元数据（名称 / 表单定义 / Q6-Q7 闸门配置）。 */
    @Transactional
    public TemplateView updateTemplate(Long templateId, TemplateUpdateRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate template = requireTemplate(templateId);
        TemplateVersionPolicy.assertEditable(template);
        if (request == null) {
            return toView(template);
        }
        if (request.name() != null && !request.name().isBlank()) {
            template.setName(request.name());
        }
        if (request.formSchemaJson() != null && !request.formSchemaJson().isNull()) {
            template.setFormSchemaJson(JsonText.write(request.formSchemaJson()));
        }
        if (request.gatePolicy() != null) {
            template.applyGatePolicy(toGatePolicy(request.gatePolicy()));
        }
        template.setUpdatedBy(operator.id());
        template.setNodeCount(nodeMapper.countByTemplateId(templateId));
        templateMapper.updateDraft(template);
        return toView(requireTemplate(templateId));
    }

    /** 写入 Q6/Q7 闸门配置（{@code PUT /flow-templates/{id}/gate-policy}）。 */
    @Transactional
    public TemplateView putGatePolicy(Long templateId, GatePolicyRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate template = requireTemplate(templateId);
        TemplateVersionPolicy.assertEditable(template);
        FlowGatePolicy policy = toGatePolicy(request);
        policy.assertValid("模板 " + template.getCode() + " v" + template.getVersion());
        template.applyGatePolicy(policy);
        template.setUpdatedBy(operator.id());
        template.setNodeCount(nodeMapper.countByTemplateId(templateId));
        templateMapper.updateDraft(template);
        log.info("Q6/Q7 闸门配置写入：operator={} templateId={} policy={}", operator.account(), templateId, policy);
        return toView(requireTemplate(templateId));
    }

    // ================================================================ 节点增删改

    /** 新增节点（草稿可写；主干节点码不可重复）。 */
    @Transactional
    public NodeView addNode(Long templateId, NodeRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate template = requireTemplate(templateId);
        TemplateVersionPolicy.assertEditable(template);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        List<FlowNode> existing = nodeMapper.selectByTemplateId(templateId);
        String nodeCode = request.nodeCode().trim().toLowerCase(java.util.Locale.ROOT);
        if (nodeMapper.countByTemplateAndCode(templateId, nodeCode, null) > 0) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "该模板已存在节点码「" + nodeCode + "」；主干节点固定 7 个、不可重复（enums.md §2）");
        }

        FlowNode node = new FlowNode();
        node.setTemplateId(templateId);
        applyRequest(node, request, existing);
        NodeDefinitionValidator.assertNode(node, template);

        int insertAt = request.seq() == null ? existing.size() + 1 : request.seq();
        if (insertAt < 1 || insertAt > existing.size() + 1) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "插入位置 seq 必须在 1~" + (existing.size() + 1) + " 之间，实际 " + insertAt);
        }
        List<FlowNode> ordered = NodeDefinitionValidator.ordered(existing);
        shiftForInsert(ordered, insertAt, node);
        templateMapper.updateNodeCount(templateId, ordered.size(), operator.id());
        log.info("流程节点新增：operator={} templateId={} seq={} code={}", operator.account(), templateId,
                node.getSeq(), node.getNodeCode());
        return toView(findNodeByTemplateAndSeq(templateId, node.getSeq()), template);
    }

    /** 全量更新节点（草稿可写）。 */
    @Transactional
    public NodeView updateNode(Long nodeId, NodeRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        TemplateVersionPolicy.assertEditable(template);
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        boolean wasTrunk = NodeCode.of(node.getNodeCode()).isPresent();
        List<FlowNode> siblings = nodeMapper.selectByTemplateId(node.getTemplateId());

        if (wasTrunk) {
            String requested = request.nodeCode() == null ? node.getNodeCode()
                    : request.nodeCode().trim().toLowerCase(java.util.Locale.ROOT);
            if (!requested.equals(node.getNodeCode())) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "主干必填节点不可改码（" + node.getNodeCode() + " → " + requested
                                + "）：主干 7 节点固定（enums.md §2）");
            }
            if (request.seq() != null && !request.seq().equals(node.getSeq())) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "主干必填节点不可改序（seq " + node.getSeq() + " → " + request.seq() + "）：主干顺序固定");
            }
        } else {
            String requested = request.nodeCode() == null ? node.getNodeCode()
                    : request.nodeCode().trim().toLowerCase(java.util.Locale.ROOT);
            if (!requested.equals(node.getNodeCode())) {
                if (nodeMapper.countByTemplateAndCode(node.getTemplateId(), requested, nodeId) > 0) {
                    throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                            "该模板已存在节点码「" + requested + "」");
                }
            }
        }

        applyRequest(node, request, siblings);
        NodeDefinitionValidator.assertNode(node, template);
        nodeMapper.updateNode(node);

        if (!wasTrunk && request.seq() != null && !request.seq().equals(node.getSeq())) {
            renumber(node.getTemplateId());
        }
        templateMapper.updateNodeCount(node.getTemplateId(), nodeMapper.countByTemplateId(node.getTemplateId()),
                operator.id());
        log.info("流程节点更新：operator={} nodeId={} code={} seq={}", operator.account(), nodeId,
                node.getNodeCode(), node.getSeq());
        return toView(requireNode(nodeId), template);
    }

    /** 删除节点（**主干必填节点一律拒绝**）。 */
    @Transactional
    public void deleteNode(Long nodeId) {
        CurrentUser operator = permissionService.requirePublish();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        TemplateVersionPolicy.assertEditable(template);
        RequiredNodePolicy.assertDeletable(node);
        nodeMapper.deleteById(nodeId);
        renumber(node.getTemplateId());
        templateMapper.updateNodeCount(node.getTemplateId(), nodeMapper.countByTemplateId(node.getTemplateId()),
                operator.id());
        log.info("流程节点删除：operator={} nodeId={} code={} templateId={}", operator.account(), nodeId,
                node.getNodeCode(), node.getTemplateId());
    }

    /**
     * 节点换序（草稿可写）：给出**目标顺序的节点 id 全量列表**。
     *
     * <p>规则：{@code nodeIds} 必须是当前模板节点的**全排列**；换序后主干 7 节点仍须落在
     * 它们的固定 seq 上（否则 400）—— 主干顺序固定是 enums.md §2 的硬约束。
     */
    @Transactional
    public List<NodeView> reorder(Long templateId, NodeOrderRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowTemplate template = requireTemplate(templateId);
        TemplateVersionPolicy.assertEditable(template);
        List<FlowNode> nodes = nodeMapper.selectByTemplateId(templateId);
        List<Long> ids = request == null ? null : request.nodeIds();
        if (ids == null || ids.size() != nodes.size()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "换序必须给出当前模板全部 " + nodes.size() + " 个节点的 nodeIds（按目标顺序）");
        }
        Map<Long, FlowNode> byId = new LinkedHashMap<>();
        for (FlowNode node : nodes) {
            byId.put(node.getId(), node);
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || !byId.containsKey(id) || !seen.add(id)) {
                throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "换序列表必须是当前模板节点的全排列（存在未知/重复 id：" + id + "）");
            }
        }
        Map<Integer, FlowNode> newOrder = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            FlowNode node = byId.get(ids.get(i));
            newOrder.put(i + 1, node);
        }
        List<FlowNode> projected = new ArrayList<>();
        for (Map.Entry<Integer, FlowNode> entry : newOrder.entrySet()) {
            FlowNode copy = entry.getValue();
            copy.setSeq(entry.getKey());
            projected.add(copy);
        }
        List<String> trunkProblems = RequiredNodePolicy.violations(projected);
        if (!trunkProblems.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "换序后主干必填节点顺序被破坏：" + String.join("；", trunkProblems));
        }
        // 两阶段落库：先统一挪到临时序号（避开 uk_flow_node_seq），再落到目标序号
        for (FlowNode node : projected) {
            nodeMapper.updateSeq(node.getId(), node.getSeq() + TEMP_SEQ_OFFSET);
        }
        for (FlowNode node : projected) {
            nodeMapper.updateSeq(node.getId(), node.getSeq());
        }
        templateMapper.updateNodeCount(templateId, nodes.size(), operator.id());
        log.info("流程节点换序：operator={} templateId={} order={}", operator.account(), templateId, ids);
        return nodesOf(templateId);
    }

    // ================================================================ 行为配置（决议 / 策略 / 跳过 / 规则）

    /** 写入决议模式与阈值（草稿可写）。 */
    @Transactional
    public NodeView putDecision(Long nodeId, NodeDecisionRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        TemplateVersionPolicy.assertEditable(template);
        applyDecision(node, request);
        NodeDefinitionValidator.assertNode(node, template);
        nodeMapper.updateNode(node);
        log.info("节点决议配置写入：operator={} nodeId={} mode={} threshold={}", operator.account(), nodeId,
                node.getDecisionMode(), node.getPassThreshold());
        return toView(requireNode(nodeId), template);
    }

    /**
     * 按候选人集合解析通过条件（{@code POST /flow-nodes/{id}/decision/resolve}）。
     *
     * <p><b>纯计算，不写库、不产生任务</b>：用于设计器「这个阈值在 N 个候选人下要几个人同意」
     * 的即时预览，以及发起前预检的解释字段。
     */
    public DecisionResolveView resolveDecision(Long nodeId, Integer candidateCount) {
        permissionService.requireTemplateRead();
        FlowNode node = requireNode(nodeId);
        int candidates = candidateCount == null ? 0 : Math.max(candidateCount, 0);
        DecisionMode mode = node.getDecisionMode() == null ? null
                : DecisionMode.of(node.getDecisionMode()).orElse(null);
        ThresholdPolicy.Threshold threshold = ThresholdPolicy.resolve(mode, node.getPassThreshold(), candidates);
        return new DecisionResolveView(node.getId(), node.getDecisionMode(), node.getPassThreshold(), candidates,
                threshold.requiredApprovals(), threshold.basis(), threshold.satisfiable(),
                describeThreshold(node.getPassThreshold(), mode, candidates, threshold));
    }

    /** 写入签名策略 / 超时 / 开关（草稿可写；**不产生签名与定时器**）。 */
    @Transactional
    public NodeView putPolicy(Long nodeId, NodePolicyRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        TemplateVersionPolicy.assertEditable(template);
        applyPolicy(node, request);
        NodeDefinitionValidator.assertNode(node, template);
        nodeMapper.updateNode(node);
        log.info("节点签名/超时/开关写入：operator={} nodeId={} sign={} timeout={}h", operator.account(), nodeId,
                node.getSignPolicy(), node.getTimeoutHours());
        return toView(requireNode(nodeId), template);
    }

    /** 校验签名策略 / 超时 / 开关（**不落库**，供设计器即时反馈）。 */
    public ValidationView validatePolicy(Long nodeId, NodePolicyRequest request) {
        permissionService.requireTemplateRead();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        FlowNode candidate = copyOf(node);
        applyPolicy(candidate, request);
        List<DefinitionProblem> problems = NodeDefinitionValidator.violations(candidate, template);
        return new ValidationView(problems.isEmpty(),
                problems.stream().map(DefinitionProblem::describe).toList(),
                PrePublishChecker.warnings(template, List.of(candidate)));
    }

    /** 写入跳过条件（草稿可写；仅事项单②可跳过）。 */
    @Transactional
    public NodeView putSkipCondition(Long nodeId, NodeSkipConditionRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        TemplateVersionPolicy.assertEditable(template);
        node.setSkipCondition(request == null || request.skipCondition() == null
                || request.skipCondition().isNull() ? null : JsonText.write(request.skipCondition()));
        NodeDefinitionValidator.assertNode(node, template);
        nodeMapper.updateNode(node);
        log.info("节点跳过条件写入：operator={} nodeId={} skip={}", operator.account(), nodeId, node.getSkipCondition());
        return toView(requireNode(nodeId), template);
    }

    /** 校验跳过条件（**不落库**；含字段存在性与操作符白名单）。 */
    public ValidationView validateSkipCondition(Long nodeId, NodeSkipConditionRequest request) {
        permissionService.requireTemplateRead();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        FlowNode candidate = copyOf(node);
        candidate.setSkipCondition(request == null || request.skipCondition() == null
                || request.skipCondition().isNull() ? null : JsonText.write(request.skipCondition()));
        List<DefinitionProblem> problems = NodeDefinitionValidator.violations(candidate, template);
        return new ValidationView(problems.isEmpty(),
                problems.stream().map(DefinitionProblem::describe).toList(), List.of());
    }

    /** 写入审批人解析规则与参数（草稿可写）。 */
    @Transactional
    public NodeView putApproverRule(Long nodeId, NodeApproverRuleRequest request) {
        CurrentUser operator = permissionService.requirePublish();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        TemplateVersionPolicy.assertEditable(template);
        if (request == null || request.approverRule() == null || request.approverRule().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "approverRule 不能为空");
        }
        node.setApproverRule(request.approverRule().trim().toLowerCase(java.util.Locale.ROOT));
        node.setApproverParam(request.approverParam() == null || request.approverParam().isNull()
                ? null : JsonText.write(request.approverParam()));
        NodeDefinitionValidator.assertNode(node, template);
        nodeMapper.updateNode(node);
        log.info("节点解析规则写入：operator={} nodeId={} rule={}", operator.account(), nodeId, node.getApproverRule());
        return toView(requireNode(nodeId), template);
    }

    /** 校验解析规则与参数（**不落库**）。 */
    public ValidationView validateApproverRule(Long nodeId, NodeApproverRuleRequest request) {
        permissionService.requireTemplateRead();
        FlowNode node = requireNode(nodeId);
        FlowTemplate template = requireTemplate(node.getTemplateId());
        FlowNode candidate = copyOf(node);
        if (request != null && request.approverRule() != null) {
            candidate.setApproverRule(request.approverRule().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request != null) {
            candidate.setApproverParam(request.approverParam() == null || request.approverParam().isNull()
                    ? null : JsonText.write(request.approverParam()));
        }
        List<DefinitionProblem> problems = NodeDefinitionValidator.violations(candidate, template);
        return new ValidationView(problems.isEmpty(),
                problems.stream().map(DefinitionProblem::describe).toList(), List.of());
    }

    // ================================================================ 发布前 dry-run

    /** 执行发布前校验并缓存「最近一次」结果。 */
    public PrePublishReportView prePublishCheck(Long templateId) {
        permissionService.requireTemplateRead();
        FlowTemplate template = requireTemplate(templateId);
        PrePublishReport report = PrePublishChecker.run(template, nodeMapper.selectByTemplateId(templateId));
        PrePublishReportView view = toView(report);
        latestChecks.put(templateId, view);
        return view;
    }

    /** 读取最近一次校验结果；从未执行过则即时执行一次（口径与 {@link #prePublishCheck} 相同）。 */
    public PrePublishReportView latestPrePublishCheck(Long templateId) {
        permissionService.requireTemplateRead();
        PrePublishReportView cached = latestChecks.get(templateId);
        return cached != null ? cached : prePublishCheck(templateId);
    }

    // ================================================================ 内部

    private FlowTemplate requireTemplate(Long templateId) {
        if (templateId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "templateId 不能为空");
        }
        FlowTemplate template = templateMapper.selectTemplateById(templateId);
        if (template == null) {
            throw BizException.notFound("流程模板");
        }
        return template;
    }

    private FlowNode requireNode(Long nodeId) {
        if (nodeId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "nodeId 不能为空");
        }
        FlowNode node = nodeMapper.selectNodeById(nodeId);
        if (node == null) {
            throw BizException.notFound("流程节点");
        }
        return node;
    }

    private List<NodeView> nodesOf(Long templateId) {
        FlowTemplate template = requireTemplate(templateId);
        List<NodeView> views = new ArrayList<>();
        for (FlowNode node : nodeMapper.selectByTemplateId(templateId)) {
            views.add(toView(node, template));
        }
        return views;
    }

    private FlowNode findNodeByTemplateAndSeq(Long templateId, Integer seq) {
        for (FlowNode node : nodeMapper.selectByTemplateId(templateId)) {
            if (node.getSeq() != null && node.getSeq().equals(seq)) {
                return node;
            }
        }
        throw BizException.notFound("流程节点 seq=" + seq);
    }

    /** 插入前的顺延：从尾部往前挪，避开 {@code uk_flow_node_seq} 冲突。 */
    private void shiftForInsert(List<FlowNode> ordered, int insertAt, FlowNode inserting) {
        List<FlowNode> tail = new ArrayList<>();
        for (FlowNode node : ordered) {
            if (node.getSeq() != null && node.getSeq() >= insertAt) {
                tail.add(node);
            }
        }
        tail.sort((left, right) -> Integer.compare(right.getSeq(), left.getSeq()));
        for (FlowNode node : tail) {
            nodeMapper.updateSeq(node.getId(), node.getSeq() + 1);
        }
        inserting.setSeq(insertAt);
        nodeMapper.insertNode(inserting);
    }

    /** 重排 seq 使其从 1 连续（删除节点后补洞）。 */
    private void renumber(Long templateId) {
        List<FlowNode> ordered = nodeMapper.selectByTemplateId(templateId);
        for (int i = 0; i < ordered.size(); i++) {
            FlowNode node = ordered.get(i);
            if (node.getSeq() == null || node.getSeq() != i + 1) {
                nodeMapper.updateSeq(node.getId(), i + 1 + TEMP_SEQ_OFFSET);
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            FlowNode node = ordered.get(i);
            if (node.getSeq() == null || node.getSeq() != i + 1) {
                nodeMapper.updateSeq(node.getId(), i + 1);
            }
        }
    }

    /** 把请求映射到节点实体（未给出的字段按「保持原值」；PUT 显式传 null 表示清空）。 */
    private void applyRequest(FlowNode node, NodeRequest request, List<FlowNode> siblings) {
        if (request.nodeCode() != null) {
            node.setNodeCode(request.nodeCode().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request.name() != null) {
            node.setName(request.name());
        }
        if (request.nodeType() != null) {
            node.setNodeType(request.nodeType().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request.approverRule() != null) {
            node.setApproverRule(request.approverRule().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request.approverParam() != null) {
            node.setApproverParam(request.approverParam().isNull() ? null : JsonText.write(request.approverParam()));
        }
        applyDecision(node, new NodeDecisionRequest(request.decisionMode(), request.passThreshold(),
                request.thresholdAbsolute(), request.thresholdPercent()));
        if (request.signPolicy() != null) {
            node.setSignPolicy(request.signPolicy().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request.timeoutHours() != null) {
            node.setTimeoutHours(request.timeoutHours());
        }
        if (request.timeoutCcSuperior() != null) {
            node.setTimeoutCcSuperior(request.timeoutCcSuperior());
        }
        if (request.allowAddSign() != null) {
            node.setAllowAddSign(request.allowAddSign());
        }
        if (request.allowJump() != null) {
            node.setAllowJump(request.allowJump());
        }
        if (request.allowRoute() != null) {
            node.setAllowRoute(request.allowRoute());
        }
        if (request.skipCondition() != null) {
            node.setSkipCondition(request.skipCondition().isNull() ? null : JsonText.write(request.skipCondition()));
        }
        if (request.seq() != null && NodeCode.of(node.getNodeCode()).isEmpty()) {
            // 非主干节点允许直接改 seq（主干节点由 updateNode 提前拦下）
            node.setSeq(request.seq());
        }
        if (node.getSeq() == null && siblings != null && NodeCode.of(node.getNodeCode()).isPresent()) {
            node.setSeq(NodeCode.of(node.getNodeCode()).get().seq());
        }
        if (node.getName() == null || node.getName().isBlank()) {
            NodeCode.of(node.getNodeCode()).ifPresent(code -> node.setName(code.label()));
        }
        if (node.getNodeType() == null) {
            node.setNodeType(NodeCode.ARCHIVE_REGISTER.code().equals(node.getNodeCode())
                    ? NodeType.ARCHIVE.code() : NodeType.APPROVE.code());
        }
        if (node.getSignPolicy() == null) {
            node.setSignPolicy(SignPolicy.OPTIONAL.code());
        }
        if (node.getAllowAddSign() == null) {
            node.setAllowAddSign(Boolean.TRUE);
        }
        if (node.getAllowJump() == null) {
            node.setAllowJump(Boolean.FALSE);
        }
        if (node.getAllowRoute() == null) {
            node.setAllowRoute(Boolean.FALSE);
        }
        if (node.getTimeoutCcSuperior() == null) {
            node.setTimeoutCcSuperior(Boolean.FALSE);
        }
        if (node.getDecisionMode() == null && node.getNodeType() != null
                && !NodeType.ARCHIVE.code().equals(node.getNodeType()) && node.getSeq() != null) {
            if (NodeCode.of(node.getNodeCode()).filter(code -> code != NodeCode.ARCHIVE_REGISTER).isPresent()) {
                node.setDecisionMode(DecisionMode.ANY.code());
            }
        }
    }

    /** 决议模式与阈值：{@code thresholdAbsolute} 优先于 {@code thresholdPercent}（templates.md T-07）。 */
    private void applyDecision(FlowNode node, NodeDecisionRequest request) {
        if (request == null) {
            return;
        }
        if (request.decisionMode() != null) {
            node.setDecisionMode(request.decisionMode().trim().toLowerCase(java.util.Locale.ROOT));
        }
        boolean hasAbsolute = request.thresholdAbsolute() != null;
        boolean hasPercent = request.thresholdPercent() != null;
        boolean hasLiteral = request.passThreshold() != null;
        if (hasAbsolute || hasPercent) {
            ThresholdPolicy.Composed composed = ThresholdPolicy.compose(request.thresholdAbsolute(),
                    request.thresholdPercent());
            node.setPassThreshold(composed.value());
        } else if (hasLiteral) {
            node.setPassThreshold(request.passThreshold().isBlank() ? null : request.passThreshold().trim());
        }
    }

    private void applyPolicy(FlowNode node, NodePolicyRequest request) {
        if (request == null) {
            return;
        }
        if (request.signPolicy() != null) {
            node.setSignPolicy(request.signPolicy().trim().toLowerCase(java.util.Locale.ROOT));
        }
        if (request.timeoutHours() != null) {
            node.setTimeoutHours(request.timeoutHours() == 0 ? null : request.timeoutHours());
        }
        if (request.timeoutCcSuperior() != null) {
            node.setTimeoutCcSuperior(request.timeoutCcSuperior());
        }
        if (request.allowAddSign() != null) {
            node.setAllowAddSign(request.allowAddSign());
        }
        if (request.allowJump() != null) {
            node.setAllowJump(request.allowJump());
        }
        if (request.allowRoute() != null) {
            node.setAllowRoute(request.allowRoute());
        }
    }

    private FlowGatePolicy toGatePolicy(GatePolicyRequest request) {
        if (request == null) {
            return FlowGatePolicy.unlimited();
        }
        DeadlineType type = request.supplementDeadlineType() == null ? null
                : DeadlineType.of(request.supplementDeadlineType()).orElseThrow(() ->
                new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "supplementDeadlineType 非法（calendar / working）：" + request.supplementDeadlineType()));
        TimeoutAction action = request.onSupplementTimeout() == null ? null
                : TimeoutAction.of(request.onSupplementTimeout()).orElseThrow(() ->
                new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                        "onSupplementTimeout 非法（notify / auto_pass / auto_return）："
                                + request.onSupplementTimeout()));
        FlowGatePolicy policy = new FlowGatePolicy(request.maxReturnCount(), request.maxSupplementCount(),
                request.supplementDeadlineDays(), type, action);
        policy.assertValid("Q6/Q7 闸门配置");
        return policy;
    }

    private static String describeThreshold(String threshold, DecisionMode mode, int candidates,
                                            ThresholdPolicy.Threshold resolved) {
        if (mode == DecisionMode.ANY) {
            return "或签：任一人通过即节点通过（阈值不参与判定）";
        }
        String source = switch (resolved.basis()) {
            case "absolute" -> "绝对人数 " + resolved.absolute();
            case "percent" -> "百分比 " + resolved.percent() + "%（向上取整）";
            default -> "未配置 → 过半（向下取整(候选人数/2)+1）";
        };
        String satisfiable = resolved.satisfiable() ? "" : "（⚠ 阈值大于候选人数，该节点在当前候选人下无法通过）";
        return source + "；候选人 " + candidates + " 人 → 需 " + resolved.requiredApprovals() + " 人同意"
                + satisfiable;
    }

    private static FlowNode copyOf(FlowNode node) {
        FlowNode copy = new FlowNode();
        copy.setId(node.getId());
        copy.setTemplateId(node.getTemplateId());
        copy.setSeq(node.getSeq());
        copy.setNodeCode(node.getNodeCode());
        copy.setName(node.getName());
        copy.setNodeType(node.getNodeType());
        copy.setApproverRule(node.getApproverRule());
        copy.setApproverParam(node.getApproverParam());
        copy.setDecisionMode(node.getDecisionMode());
        copy.setPassThreshold(node.getPassThreshold());
        copy.setSignPolicy(node.getSignPolicy());
        copy.setTimeoutHours(node.getTimeoutHours());
        copy.setTimeoutCcSuperior(node.getTimeoutCcSuperior());
        copy.setAllowAddSign(node.getAllowAddSign());
        copy.setAllowJump(node.getAllowJump());
        copy.setAllowRoute(node.getAllowRoute());
        copy.setSkipCondition(node.getSkipCondition());
        return copy;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        return TemplateStatus.of(value).orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID,
                "status 非法（draft / published / archived）：" + status)).code();
    }

    // ================================================================ 视图装配

    private TemplateView toView(FlowTemplate template) {
        if (template == null) {
            return null;
        }
        FlowGatePolicy policy = template.gatePolicy();
        GatePolicyView gate = new GatePolicyView(
                policy.effectiveMaxReturnCount(),
                policy.effectiveMaxSupplementCount(),
                policy.effectiveSupplementDeadlineDays(),
                policy.effectiveDeadlineType() == null ? null : policy.effectiveDeadlineType().code(),
                policy.effectiveTimeoutAction().code(),
                policy.isUnlimited(),
                policy.isV04Default());
        TemplateStatus status = template.statusEnum();
        return new TemplateView(
                template.getId(), template.getCode(), template.getName(), template.getFormType(),
                template.getVersion(), template.getStatus(), template.getNodeCount(),
                format(template.getPublishedAt()), format(template.getCreatedAt()), format(template.getUpdatedAt()),
                gate,
                status != null && status.usableByNewInstance(),
                status != null && status.readOnly());
    }

    private NodeView toView(FlowNode node, FlowTemplate template) {
        if (node == null) {
            return null;
        }
        DecisionMode mode = node.getDecisionMode() == null ? null
                : DecisionMode.of(node.getDecisionMode()).orElse(null);
        return new NodeView(
                node.getId(), node.getTemplateId(), node.getSeq(), node.getNodeCode(),
                NodeCode.of(node.getNodeCode()).map(NodeCode::label).orElse(null),
                node.getName(), node.getNodeType(),
                node.getApproverRule(),
                ApproverRule.of(node.getApproverRule()).map(ApproverRule::label).orElse(null),
                JsonText.read(node.getApproverParam()),
                node.getDecisionMode(), node.getPassThreshold(),
                ThresholdPolicy.isBlank(node.getPassThreshold())
                        ? (mode == DecisionMode.ANY ? "或签：任一人通过" : "未配置（会签按下过半）")
                        : describeThreshold(node.getPassThreshold(), mode, 0,
                        ThresholdPolicy.resolve(mode, node.getPassThreshold(), 0)),
                node.getSignPolicy(), node.getTimeoutHours(), node.getTimeoutCcSuperior(),
                node.getAllowAddSign(), node.getAllowJump(), node.getAllowRoute(),
                JsonText.read(node.getSkipCondition()),
                format(node.getCreatedAt()), format(node.getUpdatedAt()));
    }

    private static PrePublishReportView toView(PrePublishReport report) {
        List<CheckItemView> checks = new ArrayList<>();
        for (PrePublishReport.Check check : report.checks()) {
            checks.add(new CheckItemView(check.rule(), check.title(), check.status(), check.details()));
        }
        return new PrePublishReportView(report.templateId(), report.code(), report.version(), report.status(),
                report.passed(), report.generatedAt(), checks, report.problems(), report.warnings());
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME);
    }
}
