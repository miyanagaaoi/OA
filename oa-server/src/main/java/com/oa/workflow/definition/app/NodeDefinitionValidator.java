package com.oa.workflow.definition.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.ApproverRule;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeType;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.SignPolicy;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 节点 / 模板配置校验器 —— <b>纯函数</b>（无 IO，可穷举单测）。
 *
 * <p>它同时服务两个入口（**同一套规则，不写两遍**）：
 * <ol>
 *   <li>写接口的即时校验（{@code POST /flow-templates/{id}/nodes}、{@code PUT /flow-nodes/{id}} 等）
 *       → {@link #assertNode(FlowNode, FlowTemplate)}；</li>
 *   <li>发布前 dry-run 报告（{@code POST /flow-designs/{id}/pre-publish-check}）
 *       → {@link #violations(FlowTemplate, List)}，逐条带**规则 id**返回。</li>
 * </ol>
 *
 * <h2>逐条依据</h2>
 * <ul>
 *   <li>主干 7 节点齐备、顺序固定 → doc/templates.md §1.0 / doc/enums.md §2 / doc/prd-0.1.md §6.3；</li>
 *   <li>必填节点不可删、seq 连续 → {@link RequiredNodePolicy}；</li>
 *   <li>解析规则必填且取值合法（9 条）、{@code designated} 参数 → doc/enums.md §3、doc/prd-0.1.md §5.4；</li>
 *   <li>决议模式完整、⑦ 归档登记不适用决议与阈值 → doc/templates.md §1.0 / B-01；</li>
 *   <li>会签阈值合法、绝对人数优先、百分比向上取整 → doc/templates.md §0 T-07 / §1.0；</li>
 *   <li>超时 ≥24h、超时仅催办 → doc/prd-0.1.md §6.4 REQ-FLOW-007、doc/templates.md T-01；</li>
 *   <li>签名策略取值、⑤⑥ 强制 / ⑦ 不签名 → doc/templates.md §1.0、REQ-SIGN-003；</li>
 *   <li>自由跳转（默认关闭，仅取值校验） → doc/templates.md T-08；</li>
 *   <li>跳过条件字段存在、操作符白名单、仅事项单②可跳过 → doc/templates.md §1.5、doc/prd-0.1.md §6.1；</li>
 *   <li>Q6/Q7 闸门取值范围 → doc/templates.md §1.7（2026-10-03 裁定新增）。</li>
 * </ul>
 */
public final class NodeDefinitionValidator {

    /** 自定义（非主干）节点码的形态：小写字母开头，允许数字与下划线。 */
    private static final Pattern CUSTOM_CODE = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");

    /** 超时上限（一年），防止误填把节点永久挂起。 */
    private static final int MAX_TIMEOUT_HOURS = 24 * 365;

    private NodeDefinitionValidator() {
    }

    // ================================================================ 写接口即时校验

    /** 单节点写入校验：任一问题即抛 400。 */
    public static void assertNode(FlowNode node, FlowTemplate template) {
        List<DefinitionProblem> problems = violations(node, template);
        if (!problems.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.join("；", problems.stream().map(DefinitionProblem::describe).toList()))
                    .withDetail("nodeId", node == null ? null : node.getId())
                    .withDetail("nodeCode", node == null ? null : node.getNodeCode());
        }
    }

    /** 模板级写入校验：任一问题即抛 400。 */
    public static void assertTemplate(FlowTemplate template, List<FlowNode> nodes) {
        List<DefinitionProblem> problems = violations(template, nodes);
        if (!problems.isEmpty()) {
            throw new BizException(ErrorCode.FLOW_DEFINITION_INVALID,
                    String.join("；", problems.stream().map(DefinitionProblem::describe).toList()))
                    .withDetail("templateId", template == null ? null : template.getId());
        }
    }

    // ================================================================ 单节点校验

    /** 单节点校验（**不抛异常**）。 */
    public static List<DefinitionProblem> violations(FlowNode node, FlowTemplate template) {
        List<DefinitionProblem> problems = new ArrayList<>();
        if (node == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_CODE, "节点", "节点为空"));
            return problems;
        }
        String label = nodeLabel(node);

        // ---- 1. 节点码与序号 ----
        String code = node.getNodeCode() == null ? null : node.getNodeCode().trim().toLowerCase(Locale.ROOT);
        if (code == null || code.isBlank()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_CODE, label, "缺少 node_code"));
        } else {
            String legacy = FlowDefinitionEnums.legacyHint(code);
            if (legacy != null) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_CODE, label, legacy));
            } else if (NodeCode.of(code).isEmpty() && !CUSTOM_CODE.matcher(code).matches()) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_CODE, label,
                        "node_code 非法：" + code));
            } else if (NodeCode.of(code).isPresent()
                    && !Integer.valueOf(NodeCode.of(code).get().seq()).equals(node.getSeq())) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_CODE, label,
                        "主干节点 seq 必须为 " + NodeCode.of(code).get().seq() + "（主干顺序固定），实际 " + node.getSeq()));
            } else if (NodeCode.of(code).isEmpty() && node.getSeq() != null
                    && node.getSeq() <= NodeCode.values().length) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_CODE, label,
                        "非主干节点不得占用主干序号 1–" + NodeCode.values().length + "，实际 seq=" + node.getSeq()));
            }
        }
        if (node.getSeq() == null || node.getSeq() < 1) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SEQ, label, "seq 必须为正整数"));
        }

        // ---- 2. 节点类型 ----
        NodeType nodeType = NodeType.of(node.getNodeType()).orElse(null);
        if (nodeType == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_TYPE, label,
                    "node_type 非法（允许 approve / cc / archive；condition 为二期预留）：" + node.getNodeType()));
        } else if (nodeType == NodeType.CONDITION) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_NODE_TYPE, label,
                    "node_type=condition 属二期条件路由，一期一律拒绝（prd §6.1「一期没有条件分支」）"));
        }

        // ---- 3. 解析规则 ----
        ApproverRule rule = node.getApproverRule() == null
                ? null
                : ApproverRule.of(node.getApproverRule()).orElse(null);
        if (node.getApproverRule() == null || node.getApproverRule().isBlank()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_APPROVER_RULE, label,
                    "缺少 approver_rule（enums.md §3 共 9 条解析规则）"));
        } else if (rule == null) {
            String legacy = FlowDefinitionEnums.legacyHint(node.getApproverRule());
            problems.add(DefinitionProblem.of(PrePublishChecker.R_APPROVER_RULE, label,
                    legacy != null ? legacy
                            : "approver_rule 非法：" + node.getApproverRule() + "（允许 "
                            + FlowDefinitionEnums.approverRuleCodes() + "）"));
        } else {
            boolean trunkNode = NodeCode.of(code).isPresent();
            if (trunkNode && !rule.trunkUsable()) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_APPROVER_RULE, label,
                        "规则「" + rule.code() + "」只用于②的并行子任务组，不可作为主干节点规则"));
            }
            if (rule.requiresParam() && (node.getApproverParam() == null || node.getApproverParam().isBlank())) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_APPROVER_RULE, label,
                        "规则「" + rule.code() + "」必须给出 approver_param（如 {\"user_ids\":[1001]} 或 "
                                + "{\"role_code\":\"finance_clerk\"}）"));
            }
            if (rule == ApproverRule.DESIGNATED) {
                problems.addAll(validateDesignatedParam(node.getApproverParam(), label));
            }
        }

        // ---- 4. 决议模式 / 阈值 ----
        DecisionMode mode = node.getDecisionMode() == null
                ? null
                : DecisionMode.of(node.getDecisionMode()).orElse(null);
        if (node.getDecisionMode() != null && mode == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_DECISION, label,
                    "decision_mode 非法（允许 any / all / sequence）：" + node.getDecisionMode()));
        }
        boolean archiveNode = nodeType == NodeType.ARCHIVE
                || NodeCode.ARCHIVE_REGISTER.code().equals(code);
        if (archiveNode) {
            if (node.getDecisionMode() != null) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_DECISION, label,
                        "归档登记节点（⑦）默认「仅登记不审批」，decision_mode 必须为空"));
            }
            if (!ThresholdPolicy.isBlank(node.getPassThreshold())) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_THRESHOLD, label,
                        "归档登记节点（⑦）不适用通过阈值，pass_threshold 必须为空"));
            }
        } else if (mode == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_DECISION, label,
                    "审批节点必须配置 decision_mode（any 或签 / all 会签 / sequence 依次）"));
        }
        for (String problem : ThresholdPolicy.violations(null, mode, node.getPassThreshold())) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_THRESHOLD, label, problem));
        }

        // ---- 5. 签名策略 ----
        SignPolicy sign = node.getSignPolicy() == null
                ? null
                : SignPolicy.of(node.getSignPolicy()).orElse(null);
        if (node.getSignPolicy() == null || node.getSignPolicy().isBlank()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SIGN, label,
                    "缺少 sign_policy（required / optional / none）"));
        } else if (sign == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SIGN, label,
                    "sign_policy 非法：" + node.getSignPolicy()));
        } else if (archiveNode && sign == SignPolicy.REQUIRED) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SIGN, label,
                    "归档登记节点（⑦）默认不签名（sign_policy=none）；如需强制签名请先把节点改为需审批"));
        }

        // ---- 6. 超时 ----
        if (node.getTimeoutHours() != null) {
            if (node.getTimeoutHours() < 24) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_TIMEOUT, label,
                        "timeout_hours 不得小于 24（REQ-FLOW-007：默认 ≥24h 才允许配置），实际 " + node.getTimeoutHours()));
            } else if (node.getTimeoutHours() > MAX_TIMEOUT_HOURS) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_TIMEOUT, label,
                        "timeout_hours 不得超过 " + MAX_TIMEOUT_HOURS + "，实际 " + node.getTimeoutHours()));
            }
        }
        if (Boolean.TRUE.equals(node.getTimeoutCcSuperior()) && node.getTimeoutHours() == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_TIMEOUT, label,
                    "timeout_cc_superior=1 但未配置 timeout_hours，抄送上级不会发生"));
        }

        // ---- 7. 跳过条件 ----
        problems.addAll(validateSkipCondition(node, template, label, code));

        return problems;
    }

    private static List<DefinitionProblem> validateDesignatedParam(String param, String label) {
        List<DefinitionProblem> problems = new ArrayList<>();
        if (param == null || param.isBlank()) {
            return problems;
        }
        boolean hasUsers = param.contains("\"user_ids\"");
        boolean hasRole = param.contains("\"role_code\"");
        if (!hasUsers && !hasRole) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_APPROVER_RULE, label,
                    "approver_param 必须含 user_ids 或 role_code（enums.md §3 designated）"));
        }
        if (hasUsers && hasRole) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_APPROVER_RULE, label,
                    "approver_param 不得同时给出 user_ids 与 role_code（口径二选一）"));
        }
        return problems;
    }

    private static List<DefinitionProblem> validateSkipCondition(FlowNode node, FlowTemplate template,
                                                                 String label, String code) {
        List<DefinitionProblem> problems = new ArrayList<>();
        String raw = node.getSkipCondition();
        boolean blank = raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim());
        if (blank) {
            return problems;
        }
        if (!NodeCode.FINANCE_REVIEW.code().equals(code)) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SKIP, label,
                    "跳过条件只能配置在②财务部复核节点（templates.md §1.5「② 是否可跳过」是四类模板的唯一差异项）"));
        }
        String formType = template == null ? null : template.getFormType();
        if (formType != null && !"matter".equalsIgnoreCase(formType)) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SKIP, label,
                    "仅事项审批单（matter）的②可跳过；当前模板 form_type=" + formType
                            + " 恒为「涉及」，skip_condition 必须为空（templates.md §1.5）"));
        }
        SkipConditionEvaluator.Condition condition;
        try {
            condition = SkipConditionEvaluator.parse(raw);
        } catch (IllegalArgumentException ex) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SKIP, label, ex.getMessage()));
            return problems;
        }
        if (condition == null) {
            return problems;
        }
        if (template != null) {
            for (String problem : SkipConditionEvaluator.validateField(template.getFormSchemaJson(),
                    condition.field())) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_SKIP, label, problem));
            }
        }
        if (!"involve_cost".equals(condition.field())) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_SKIP, label,
                    "一期唯一的跳过判据是「是否涉及费用」（字段 involve_cost），实际字段「"
                            + condition.field() + "」（prd §6.1）"));
        }
        return problems;
    }

    // ================================================================ 模板级校验（发布前 dry-run）

    /**
     * 模板级校验（**不抛异常**）：模板元数据 + 主干完整性 + seq 连续性 + 逐节点 + Q6/Q7 闸门。
     */
    public static List<DefinitionProblem> violations(FlowTemplate template, List<FlowNode> nodes) {
        List<DefinitionProblem> problems = new ArrayList<>();
        if (template == null) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, "模板", "模板不存在"));
            return problems;
        }
        String label = "模板 " + template.getCode() + " v" + template.getVersion();

        // ---- 模板元数据 ----
        if (template.getCode() == null || template.getCode().isBlank()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, label,
                    "缺少 code（matter / fund / contract / seal）"));
        } else if (FlowDefinitionEnums.TemplateStatus.of(template.getStatus()).isEmpty()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, label,
                    "status 非法（draft / published / archived）"));
        }
        if (template.getFormType() == null || template.getFormType().isBlank()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, label, "缺少 form_type"));
        } else if (template.getCode() != null && !template.getCode().equalsIgnoreCase(template.getFormType())) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, label,
                    "code 与 form_type 必须一致（templates.md §1.0 模板版本行）"));
        }
        if (template.getVersion() == null || template.getVersion() < 1) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, label,
                    "version 必须 ≥1（templates.md V-08 版本号单调递增）"));
        }

        // ---- 主干与必填节点 ----
        for (String problem : RequiredNodePolicy.violations(nodes)) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_TRUNK, label, problem));
        }

        // ---- seq 连续性 ----
        List<FlowNode> ordered = ordered(nodes);
        Set<Integer> seen = new LinkedHashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            FlowNode node = ordered.get(i);
            if (node.getSeq() == null) {
                continue;
            }
            if (!seen.add(node.getSeq())) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_SEQ, label,
                        "seq " + node.getSeq() + " 重复"));
            }
            if (node.getSeq() != i + 1) {
                problems.add(DefinitionProblem.of(PrePublishChecker.R_SEQ, label,
                        "节点 seq 必须从 1 连续递增（第 " + (i + 1) + " 个节点的 seq 为 " + node.getSeq() + "）"));
            }
        }
        if (ordered.isEmpty()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_TRUNK, label, "模板没有任何节点"));
        }
        if (template.getNodeCount() != null && template.getNodeCount() != ordered.size()) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_METADATA, label,
                    "node_count=" + template.getNodeCount() + " 与实际节点数 " + ordered.size() + " 不一致"));
        }

        // ---- 逐节点 ----
        for (FlowNode node : ordered) {
            problems.addAll(violations(node, template));
        }

        // ---- Q6 / Q7 闸门配置 ----
        for (String problem : template.gatePolicy().violations(label)) {
            problems.add(DefinitionProblem.of(PrePublishChecker.R_GATE, label, problem));
        }
        return problems;
    }

    /** 按 seq 排序（{@code null} 排最后，保证报告顺序稳定）。 */
    public static List<FlowNode> ordered(List<FlowNode> nodes) {
        List<FlowNode> ordered = new ArrayList<>(nodes == null ? List.of() : nodes);
        ordered.sort(Comparator.comparing(FlowNode::getSeq,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ordered;
    }

    /** 供报告与日志使用的节点标签。 */
    public static String nodeLabel(FlowNode node) {
        if (node == null) {
            return "节点";
        }
        StringBuilder builder = new StringBuilder("节点 ");
        builder.append(node.getSeq() == null ? "?" : node.getSeq());
        if (node.getNodeCode() != null) {
            builder.append(' ').append(node.getNodeCode());
        }
        if (node.getName() != null && !node.getName().isBlank()) {
            builder.append('（').append(node.getName()).append('）');
        }
        return builder.toString();
    }
}
