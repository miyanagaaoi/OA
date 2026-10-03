package com.oa.workflow.approver.app;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.api.dto.ApproverDtos.CandidateView;
import com.oa.workflow.approver.api.dto.ApproverDtos.FinanceHealthView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckBlockerView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckNodeView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckReportView;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRequest;
import com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckRuleView;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import com.oa.workflow.definition.infra.FlowNodeMapper;
import com.oa.workflow.definition.infra.FlowTemplateMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 发起前预检（2a.3，{@code oa.workflow.approver.precheck}）—— <b>AC-11 / AC-19 的落点</b>。
 *
 * <h2>硬口径</h2>
 * <ol>
 *   <li>逐节点解析候选人；<b>任一节点候选人为空 → 禁止发起</b>（REQ-FLOW-012 / AC-11）；</li>
 *   <li>拦截文案必须指出**是哪个节点、命中哪条规则、缺什么配置**
 *       （prd §5.4「提示『XX 节点无有效审批人，请联系管理员配置』」）；
 *       结构化清单走 {@code details}（服务端日志），可读清单走 message（进 HTTP 响应体，
 *       因为 {@code BizException.details} 不出响应，口径同 {@code InFlightGuard}）；</li>
 *   <li>**不允许静默跳过**：只有命中 {@code skip_condition} 的节点（一期仅事项单②）才可不产生候选人；</li>
 *   <li>预检**不写库**：{@code POST /flow-instances/precheck} 是只读干跑，
 *       与建实例共用同一份解析实现（{@link #resolveForSubmit}），避免两处口径分叉。</li>
 * </ol>
 *
 * <h2>与 2a.4 的边界</h2>
 * <p>本服务只负责「能不能发起」，不负责发起之后的状态流转（那是 2a.4 运行时状态机）。
 * 「必填字段缺失」「无权限组织节点」两类拦截（normify {@code oa.workflow.approver.precheck}
 * 的另两条规则）依赖 2b 的表单校验与 1.x 的数据域判定，本工作包只实现**空候选人**这一条
 * （它是 AC-11/AC-19 的验收对象），另两条以 {@link #PRECHECK_RULES} 显式登记为「后续工作包负责」，
 * **不做静默放行**。
 */
@Service
public class ApproverPrecheckService {

    private static final Logger log = LoggerFactory.getLogger(ApproverPrecheckService.class);

    /** 预检规则清单（出参用；未实现项显式标注负责工作包，避免「看起来已经校验了」）。 */
    public static final List<PrecheckRuleView> PRECHECK_RULES = List.of(
            new PrecheckRuleView("empty_candidate",
                    "任一节点候选人集合为空 → 禁止发起并指出节点/规则/缺什么配置（AC-11、AC-19；本工作包实现）"),
            new PrecheckRuleView("missing_required_field",
                    "必填字段缺失 → 禁止发起（REQ-FLOW-012；服务端二次校验属 2b 表单模板引擎）"),
            new PrecheckRuleView("unauthorized_org",
                    "无权限组织节点 → 禁止发起（REQ-FLOW-012；归属组织可见性判定属 1.x 数据域 + 2b 发起入口）"));

    private final ApproverResolutionService resolutionService;
    private final ApproverDirectory directory;
    private final FlowTemplateMapper templateMapper;
    private final FlowNodeMapper nodeMapper;
    private final OaProperties properties;

    public ApproverPrecheckService(ApproverResolutionService resolutionService, ApproverDirectory directory,
                                   FlowTemplateMapper templateMapper, FlowNodeMapper nodeMapper,
                                   OaProperties properties) {
        this.resolutionService = resolutionService;
        this.directory = directory;
        this.templateMapper = templateMapper;
        this.nodeMapper = nodeMapper;
        this.properties = properties;
    }

    // ================================================================ 预检

    /**
     * 执行预检（**只读**）。
     *
     * @param request   预检入参
     * @param principal 当前登录人（{@code initiatorId} 为空时以他作为发起人）
     */
    public PrecheckReportView precheck(PrecheckRequest request, CurrentUser principal) {
        return resolveForSubmit(request, principal).report();
    }

    /**
     * 一次完成「预检 + 解析」——**建实例与预检必须同源**。
     *
     * <p>若两处各跑一遍解析，可能出现「预检通过、固化的快照却为空」（中间有人改了组织配置）。
     * 因此 {@code FlowInstanceService#create} 直接复用本方法的结果：
     * 报告用于拦截判定，{@code resolutions} 用于固化快照。
     */
    public Resolved resolveForSubmit(PrecheckRequest request, CurrentUser principal) {
        FlowTemplate template = resolveTemplate(request);
        RuleRequest context = buildContext(request, principal);
        List<NodeConfig> nodes = lockedNodes(template);
        List<ApproverResolutionService.NodeResolution> resolutions = resolutionService.resolveAll(nodes,
                context, directory);
        return new Resolved(template, context, resolutions, report(template, context, resolutions));
    }

    /**
     * 一次解析的完整产物。
     *
     * @param template    锁定版本的模板行（发起时写入 {@code flow_instance.template_id/template_version}）
     * @param context     解析上下文（写进快照 basis）
     * @param resolutions 逐节点解析结果（写进快照 nodes）
     * @param report      预检报告（拦截判定与出参）
     */
    public record Resolved(
            FlowTemplate template,
            RuleRequest context,
            List<ApproverResolutionService.NodeResolution> resolutions,
            PrecheckReportView report
    ) {
    }

    /**
     * 断言「可发起」：命中拦截项即抛 400（{@link ErrorCode#APPROVER_RESOLUTION_BLOCKED}）。
     *
     * <p>消息形如：
     * <pre>
     * 发起被拒绝：2 个节点无有效审批人 —— 3 分公司分管领导（branch_leader，规则 branch_leader）：...
     * </pre>
     */
    public PrecheckReportView assertSubmittable(PrecheckRequest request, CurrentUser principal) {
        PrecheckReportView report = precheck(request, principal);
        if (!report.allowed()) {
            StringBuilder builder = new StringBuilder("发起被拒绝：")
                    .append(report.blockers().size()).append(" 个节点无有效审批人");
            for (PrecheckBlockerView blocker : report.blockers()) {
                builder.append(" —— ").append(describeBlocker(blocker));
            }
            throw new BizException(ErrorCode.APPROVER_RESOLUTION_BLOCKED, builder.toString())
                    .withDetail("blockers", report.blockers())
                    .withDetail("templateId", report.templateId())
                    .withDetail("templateVersion", report.templateVersion())
                    .withDetail("initiatorId", report.initiatorId());
        }
        return report;
    }

    /** 单条拦截项的可读文案（**哪个节点、哪条规则、缺什么配置**）。 */
    public static String describeBlocker(PrecheckBlockerView blocker) {
        StringBuilder builder = new StringBuilder();
        builder.append(blocker.nodeSeq()).append(' ').append(blocker.nodeName())
                .append("（").append(blocker.nodeCode()).append("，规则 ").append(blocker.rule());
        if (blocker.ruleLabel() != null) {
            builder.append(' ').append(blocker.ruleLabel());
        }
        builder.append("）：").append(blocker.reason());
        if (blocker.missingConfig() != null && !blocker.missingConfig().isEmpty()) {
            builder.append("；缺少配置：").append(String.join("；", blocker.missingConfig()));
        }
        return builder.toString();
    }

    // ================================================================ 财务部健康度

    /**
     * 集团财务部负责人空缺预警（{@code GET /approver-rules/finance-leader/health}）。
     *
     * <p>财务部负责人空缺时，**所有**单据的②节点都会解析为空 → 全系统发不出单据，
     * 因此值得单列一个探针（normify {@code oa.workflow.approver.rule-table.finance}）。
     */
    public FinanceHealthView financeHealth() {
        Long configuredId = properties.getScope().getFinanceDeptId();
        String configuredName = properties.getScope().getFinanceDeptName();
        Optional<OrgNodeView> dept = configuredId != null
                ? directory.org(configuredId)
                : directory.orgByName(configuredName == null ? "财务部" : configuredName);
        if (dept.isEmpty()) {
            return new FinanceHealthView(configuredName, configuredId, null, null, null, 0, List.of(), false,
                    "未找到集团财务部组织（oa.scope.finance-dept-id / finance-dept-name），"
                            + "所有单据的②财务部复核节点都会解析为空 → 发起将被拦截");
        }
        OrgNodeView org = dept.get();
        List<Candidate> leaders = directory.primaryLeaders(org.id());
        List<CandidateView> views = candidates(leaders);
        boolean healthy = !views.isEmpty();
        return new FinanceHealthView(configuredName, configuredId, org.id(), org.name(), org.path(),
                views.size(), views, healthy,
                healthy ? "财务部负责人已配置（" + views.size() + " 人）"
                        : "集团财务部（" + org.name() + " id=" + org.id() + "）未配置正职负责人 → "
                        + "所有单据的②节点会解析为空，发起将被拦截（AC-11）");
    }

    // ================================================================ 内部

    /** 解析锁定的模板（未给 id 时按 formType 取当前已发布版本）。 */
    public FlowTemplate resolveTemplate(PrecheckRequest request) {
        if (request != null && request.templateId() != null) {
            FlowTemplate template = templateMapper.selectTemplateById(request.templateId());
            if (template == null) {
                throw BizException.notFound("流程模板");
            }
            return template;
        }
        String formType = request == null ? null : request.formType();
        if (formType == null || formType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "必须给出 templateId 或 formType（matter / fund / contract / seal）");
        }
        List<FlowTemplate> published = templateMapper.selectByCodeAndStatus(formType,
                TemplateStatus.PUBLISHED.code());
        if (published.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    "单据类型「" + formType + "」没有已发布（published）的流程模板版本，无法发起");
        }
        return published.get(0);
    }

    /** 锁定版本的节点配置（**按模板行读取**：新版本发布不会改变已锁定版本的行）。 */
    public List<NodeConfig> lockedNodes(FlowTemplate template) {
        List<NodeConfig> nodes = new ArrayList<>();
        for (FlowNode node : nodeMapper.selectByTemplateId(template.getId())) {
            nodes.add(NodeConfig.of(template, node));
        }
        nodes.sort(Comparator.comparing(NodeConfig::seq,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return nodes;
    }

    /** 构造解析上下文（发起人事实 + 表单事实 + 自选/协同输入）。 */
    public RuleRequest buildContext(PrecheckRequest request, CurrentUser principal) {
        Long initiatorId = request != null && request.initiatorId() != null
                ? request.initiatorId()
                : (principal == null ? null : principal.id());
        if (initiatorId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Optional<Candidate> initiator = directory.user(initiatorId);
        if (initiator.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "发起人不存在或已离职：" + initiatorId);
        }
        Candidate user = initiator.get();
        Optional<OrgNodeView> org = user.orgId() == null ? Optional.empty() : directory.org(user.orgId());
        String orgPath = org.map(OrgNodeView::path).orElse(null);
        Long companyId = user.companyId();
        if (companyId == null && org.isPresent()) {
            companyId = companyOf(org.get()).orElse(null);
        }
        Map<String, Object> formValues = new LinkedHashMap<>();
        if (request != null && request.formValues() != null) {
            formValues.putAll(request.formValues());
        }
        // 表单事实的标准键（与 doc/forms.md 的字段 code 一致）
        if (request != null && request.involveCost() != null) {
            formValues.put("involve_cost", request.involveCost());
        }
        return new RuleRequest(initiatorId, user.orgId(), companyId, orgPath,
                request == null ? null : request.category(),
                request == null ? List.of() : request.initiatorPicks(),
                request == null ? List.of() : request.collabDeptIds(),
                formValues,
                request == null || request.collabSelfExcludeDeptIds() == null
                        ? java.util.Set.of()
                        : new LinkedHashSet<>(request.collabSelfExcludeDeptIds()));
    }

    /** 从组织链上溯取所属公司（发起人 {@code company_id} 为空时的兜底）。 */
    private Optional<Long> companyOf(OrgNodeView org) {
        OrgNodeView current = org;
        int guard = 0;
        while (current != null && guard++ < 10) {
            if (current.isCompany()) {
                return Optional.of(current.id());
            }
            if (current.isGroup() || current.parentId() == null) {
                return Optional.empty();
            }
            current = directory.org(current.parentId()).orElse(null);
        }
        return Optional.empty();
    }

    /** 组装报告。 */
    public PrecheckReportView report(FlowTemplate template, RuleRequest context,
                                     List<ApproverResolutionService.NodeResolution> resolutions) {
        List<PrecheckNodeView> nodeViews = new ArrayList<>();
        List<PrecheckBlockerView> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ApproverResolutionService.NodeResolution resolution : resolutions) {
            NodeConfig node = resolution.node();
            boolean blocker = resolution.blocker();
            ApproverRule rule = ApproverRule.of(resolution.rule()).orElse(null);
            String reason = blocker ? "候选人集合为空（解析规则未能取到任何在职审批人）" : null;
            nodeViews.add(new PrecheckNodeView(
                    resolution.seq(), resolution.nodeCode(), resolution.nodeName(), node.nodeType(),
                    resolution.rule(), resolution.skipped(), resolution.skipReason(), blocker,
                    candidates(resolution.candidates()), candidateGroups(resolution.groups()),
                    resolution.requiredApprovals(),
                    resolution.threshold() == null ? null : resolution.threshold().basis(),
                    resolution.threshold() == null ? null : resolution.threshold().satisfiable(),
                    resolution.evidence(), resolution.missingConfig()));
            if (blocker) {
                blockers.add(new PrecheckBlockerView(
                        resolution.seq(), resolution.nodeCode(), resolution.nodeName(), resolution.rule(),
                        rule == null ? null : rule.label(), reason, resolution.missingConfig()));
            } else if (!resolution.skipped() && resolution.threshold() != null
                    && !resolution.threshold().satisfiable()) {
                warnings.add("节点 " + resolution.seq() + " " + resolution.nodeName()
                        + "：会签阈值需 " + resolution.threshold().requiredApprovals()
                        + " 人同意，但候选人只有 " + resolution.candidates().size()
                        + " 人 → 该节点在当前配置下无法通过（请调整阈值或补充候选人）");
            }
            if (resolution.skipped()) {
                warnings.add("节点 " + resolution.seq() + " " + resolution.nodeName() + " 已跳过："
                        + resolution.skipReason());
            }
        }
        return new PrecheckReportView(
                blockers.isEmpty(), template.getId(), template.getCode(), template.getVersion(),
                context.initiatorId(),
                context.initiatorId() == null ? null : nameOf(context.initiatorId()),
                blockers, nodeViews, warnings);
    }

    private String nameOf(Long userId) {
        return directory.user(userId).map(Candidate::name).orElse(null);
    }

    /** 候选人 → 出参。 */
    public static List<CandidateView> candidates(List<Candidate> candidates) {
        List<CandidateView> views = new ArrayList<>();
        if (candidates == null) {
            return views;
        }
        for (Candidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            views.add(new CandidateView(candidate.userId(), candidate.name(), candidate.account(),
                    candidate.employeeNo(), candidate.orgId(), candidate.orgName(), candidate.orgPath(),
                    candidate.companyId(), candidate.position()));
        }
        return views;
    }

    /** 多组候选人 → 出参（协同部门会签）。 */
    public static List<List<CandidateView>> candidateGroups(List<List<Candidate>> groups) {
        List<List<CandidateView>> views = new ArrayList<>();
        if (groups == null) {
            return views;
        }
        for (List<Candidate> group : groups) {
            views.add(candidates(group));
        }
        return views;
    }

    /** 发起被拦截时的统一日志（便于按 traceId 取证）。 */
    public void logBlocked(PrecheckReportView report, CurrentUser principal) {
        if (!log.isWarnEnabled()) {
            return;
        }
        List<String> details = new ArrayList<>();
        for (PrecheckBlockerView blocker : report.blockers()) {
            details.add(describeBlocker(blocker));
        }
        log.warn("发起前预检拦截：operator={} initiator={} template={} v{} 拦截项={}",
                principal == null ? null : principal.account(), report.initiatorId(), report.templateCode(),
                report.templateVersion(), details);
    }
}
