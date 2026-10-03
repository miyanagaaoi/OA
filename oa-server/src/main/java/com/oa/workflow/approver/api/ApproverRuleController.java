package com.oa.workflow.approver.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.common.api.ApiResponse;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.json.JsonText;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.api.dto.ApproverDtos;
import com.oa.workflow.approver.api.dto.ApproverDtos.CandidateView;
import com.oa.workflow.approver.api.dto.ApproverDtos.DedupRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.DedupView;
import com.oa.workflow.approver.api.dto.ApproverDtos.FinanceHealthView;
import com.oa.workflow.approver.api.dto.ApproverDtos.MergePolicyView;
import com.oa.workflow.approver.api.dto.ApproverDtos.RuleResolveRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.RuleResolveView;
import com.oa.workflow.approver.api.dto.ApproverDtos.RuleView;
import com.oa.workflow.approver.app.ApproverDirectory;
import com.oa.workflow.approver.app.ApproverPrecheckService;
import com.oa.workflow.approver.app.ApproverResolutionRule;
import com.oa.workflow.approver.app.ApproverRuleRegistry;
import com.oa.workflow.approver.app.Candidate;
import com.oa.workflow.approver.app.CandidateDedupPolicy;
import com.oa.workflow.approver.app.NodeConfig;
import com.oa.workflow.approver.app.RuleOutcome;
import com.oa.workflow.approver.app.RuleRequest;
import com.oa.workflow.definition.app.WorkflowPermissionService;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import com.oa.common.config.OaProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批人解析规则接口（2a.3，{@code oa.workflow.approver.rule-table} / {@code dedup}）。
 *
 * <h2>路由清单（全部在 {@code /api/v1} 之下）</h2>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>所需权限</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>{@code /approver-rules}</td><td>admin:flow:node</td><td>9 条解析规则清单（含出处章节）</td></tr>
 *   <tr><td>POST</td><td>{@code /approver-rules/{ruleCode}/resolve}</td><td>admin:flow:node</td><td>单规则解析（{@code ruleCode} 为 enums.md §3 规则码，亦接受基线短名别名）</td></tr>
 *   <tr><td>POST</td><td>{@code /approver-rules/dedup}</td><td>admin:flow:node</td><td>节点内去重策略（同一节点重复出现自动去重）</td></tr>
 *   <tr><td>GET</td><td>{@code /approver-rules/merge-policy}</td><td>admin:flow:node</td><td>连续节点是否合并（默认不合并）</td></tr>
 *   <tr><td>GET</td><td>{@code /approver-rules/finance-leader/health}</td><td>admin:flow:node</td><td>集团财务部负责人空缺预警（为空则全系统发不出单）</td></tr>
 * </table>
 *
 * <p>本组接口是**配置/诊断面**（不是发起点），因此统一要求 {@code admin:flow:node}；
 * 发起点（预检 / 建实例 / 提交）在 {@code FlowInstanceController}，用的是 {@code flow} 权限。
 */
@RestController
@RequestMapping("/api/v1/approver-rules")
public class ApproverRuleController {

    private final ApproverRuleRegistry registry;
    private final ApproverDirectory directory;
    private final ApproverPrecheckService precheckService;
    private final FlowTemplateMapper templateMapper;
    private final FlowNodeMapper nodeMapper;
    private final WorkflowPermissionService permissionService;
    private final OaProperties properties;

    public ApproverRuleController(ApproverRuleRegistry registry, ApproverDirectory directory,
                                  ApproverPrecheckService precheckService, FlowTemplateMapper templateMapper,
                                  FlowNodeMapper nodeMapper, WorkflowPermissionService permissionService,
                                  OaProperties properties) {
        this.registry = registry;
        this.directory = directory;
        this.precheckService = precheckService;
        this.templateMapper = templateMapper;
        this.nodeMapper = nodeMapper;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    /** 9 条解析规则清单（逐条给出权威出处章节，便于审计对照文档）。 */
    @GetMapping
    public ApiResponse<List<RuleView>> rules() {
        permissionService.requireNodeWrite();
        List<RuleView> views = new ArrayList<>();
        for (ApproverResolutionRule rule : registry.all()) {
            com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule meta =
                    com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule.of(rule.code()).orElse(null);
            views.add(new RuleView(rule.code(), rule.label(), rule.source(),
                    meta != null && meta.trunkUsable(), meta != null && meta.requiresParam()));
        }
        return ApiResponse.success(views);
    }

    /**
     * 单规则解析（{@code POST /approver-rules/{ruleCode}/resolve}）。
     *
     * <p>两种用法：
     * <ol>
     *   <li>给 {@code templateId + nodeSeq}：用**真实节点配置**解析（含 approver_param），
     *       用于设计期自检「这条规则现在能不能取到人」；</li>
     *   <li>只给发起上下文 + {@code approverParam}：用临时节点配置解析（联调/排障用）。</li>
     * </ol>
     */
    @PostMapping("/{ruleCode}/resolve")
    public ApiResponse<RuleResolveView> resolve(@PathVariable("ruleCode") String ruleCode,
                                                @RequestBody(required = false) RuleResolveRequest request) {
        permissionService.requireNodeWrite();
        ApproverResolutionRule rule = registry.find(ruleCode).orElseThrow(() ->
                new BizException(ErrorCode.NOT_FOUND,
                        "未知解析规则：" + ruleCode + "（enums.md §3 的 9 条规则：" + registry.codes() + "）"));

        NodeConfig node = nodeOf(ruleCode, request);
        RuleRequest context = contextOf(request);
        RuleOutcome outcome = rule.resolve(node, context, directory);
        boolean resolved = outcome.hasCandidates();
        return ApiResponse.success(new RuleResolveView(rule.code(), rule.label(), rule.source(), resolved,
                outcome.evidence(), ApproverPrecheckService.candidates(outcome.candidates()),
                ApproverPrecheckService.candidateGroups(outcome.groups()), outcome.missingConfig()));
    }

    /** 节点内去重策略（同一节点重复出现自动去重；prd §5.4）。 */
    @PostMapping("/dedup")
    public ApiResponse<DedupView> dedup(@RequestBody(required = false) DedupRequest request) {
        permissionService.requireNodeWrite();
        List<Candidate> candidates = request == null || request.userIds() == null
                ? List.of() : directory.users(request.userIds());
        // 演示「一人多岗」造成的重复：按入参顺序取人并**保留重复**
        List<Candidate> raw = new ArrayList<>();
        if (request != null && request.userIds() != null) {
            for (Long id : request.userIds()) {
                directory.user(id).ifPresent(raw::add);
            }
        }
        CandidateDedupPolicy.DedupResult result = CandidateDedupPolicy.dedupWithinNode(
                raw.isEmpty() ? candidates : raw);
        return ApiResponse.success(new DedupView(result.total(), result.candidates().size(),
                ApproverPrecheckService.candidates(result.candidates()),
                ApproverPrecheckService.candidates(result.removed()),
                result.evidence()));
    }

    /** 连续节点合并策略（默认不合并；文档未给出「设计器显式配置」的落点，故为系统级开关）。 */
    @GetMapping("/merge-policy")
    public ApiResponse<MergePolicyView> mergePolicy() {
        permissionService.requireNodeWrite();
        Boolean configured = properties.getWorkflow().getApprover().isMergeConsecutiveNodes();
        return ApiResponse.success(new MergePolicyView(
                CandidateDedupPolicy.mergeConsecutiveNodes(configured),
                CandidateDedupPolicy.describeMergePolicy(configured)));
    }

    /** 集团财务部负责人空缺预警。 */
    @GetMapping("/finance-leader/health")
    public ApiResponse<FinanceHealthView> financeHealth() {
        permissionService.requireNodeWrite();
        return ApiResponse.success(precheckService.financeHealth());
    }

    // ================================================================ 内部

    private NodeConfig nodeOf(String ruleCode, RuleResolveRequest request) {
        if (request != null && request.templateId() != null && request.nodeSeq() != null) {
            FlowTemplate template = templateMapper.selectTemplateById(request.templateId());
            if (template == null) {
                throw BizException.notFound("流程模板");
            }
            for (FlowNode node : nodeMapper.selectByTemplateId(template.getId())) {
                if (request.nodeSeq().equals(node.getSeq())) {
                    return NodeConfig.of(template, node);
                }
            }
            throw BizException.notFound("流程节点 seq=" + request.nodeSeq());
        }
        JsonNode param = request == null ? null : request.approverParam();
        return new NodeConfig(null, null, null, null, "approve", ruleCode, param, "any", null,
                "optional", 24, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, null);
    }

    private RuleRequest contextOf(RuleResolveRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空：initiatorId 为必填字段");
        }
        final RuleResolveRequest effective;
        if (request.initiatorId() == null) {
            CurrentUser principal = permissionService.requirePrincipal();
            effective = new RuleResolveRequest(principal.id(), request.initiatorOrgId(),
                    request.initiatorCompanyId(), request.category(), request.initiatorPicks(),
                    request.collabDeptIds(), request.collabSelfExcludeDeptIds(), request.formValues(),
                    request.approverParam(), request.templateId(), request.nodeSeq());
        } else {
            effective = request;
        }
        Map<String, Object> formValues = new LinkedHashMap<>();
        if (effective.formValues() != null) {
            formValues.putAll(effective.formValues());
        }
        Long initiatorId = effective.initiatorId();
        Candidate initiator = directory.user(initiatorId).orElseThrow(() ->
                new BizException(ErrorCode.NOT_FOUND, "发起人不存在：" + initiatorId));
        Long orgId = effective.initiatorOrgId() != null ? effective.initiatorOrgId() : initiator.orgId();
        String orgPath = orgId == null ? null : directory.org(orgId).map(o -> o.path()).orElse(null);
        Long companyId = effective.initiatorCompanyId() != null
                ? effective.initiatorCompanyId() : initiator.companyId();
        return new RuleRequest(initiatorId, orgId, companyId, orgPath, effective.category(),
                effective.initiatorPicks(), effective.collabDeptIds(), formValues,
                effective.collabSelfExcludeDeptIds() == null ? java.util.Set.of()
                        : new java.util.LinkedHashSet<>(effective.collabSelfExcludeDeptIds()));
    }
}
