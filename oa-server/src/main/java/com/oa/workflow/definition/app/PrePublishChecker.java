package com.oa.workflow.definition.app;

import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布前 dry-run 校验（{@code oa.workflow.designer.validation}）—— <b>纯函数</b>。
 *
 * <p>把 {@link NodeDefinitionValidator} 的逐条问题按**规则 id**聚合成一份人可读报告：
 * 每条规则一条结论（{@code pass} / {@code fail}），另有不阻止发布的 {@code warn} 提示。
 * 「任一项不通过即阻止发布」由 {@link PrePublishReport#passed()} 承载，
 * 由 {@code FlowDefinitionService#publish} 在**同一事务内重新执行**（不信任上一次的缓存结果）。
 */
public final class PrePublishChecker {

    /** 规则 id：模板元数据（code / form_type / version / node_count）。 */
    public static final String R_METADATA = "R-METADATA";
    /** 规则 id：主干 7 节点齐备且顺序固定（必填节点不可删）。 */
    public static final String R_TRUNK = "R-TRUNK";
    /** 规则 id：节点 seq 从 1 连续递增、不重复。 */
    public static final String R_SEQ = "R-SEQ";
    /** 规则 id：节点码合法、主干节点 seq 正确、不用废弃值。 */
    public static final String R_NODE_CODE = "R-NODE-CODE";
    /** 规则 id：节点类型合法（condition 属二期，一律拒绝）。 */
    public static final String R_NODE_TYPE = "R-NODE-TYPE";
    /** 规则 id：解析规则必填且取值合法（9 条），designated 参数齐备。 */
    public static final String R_APPROVER_RULE = "R-APPROVER-RULE";
    /** 规则 id：决议模式完整；⑦ 归档登记不适用决议。 */
    public static final String R_DECISION = "R-DECISION";
    /** 规则 id：会签阈值合法（绝对人数优先、百分比向上取整）。 */
    public static final String R_THRESHOLD = "R-THRESHOLD";
    /** 规则 id：签名策略合法。 */
    public static final String R_SIGN = "R-SIGN";
    /** 规则 id：超时 ≥24h。 */
    public static final String R_TIMEOUT = "R-TIMEOUT";
    /** 规则 id：跳过条件字段存在、仅事项单②可跳过。 */
    public static final String R_SKIP = "R-SKIP";
    /** 规则 id：Q6/Q7 闸门配置取值范围（2026-10-03 裁定新增）。 */
    public static final String R_GATE = "R-GATE";

    /**
     * 规则清单（{@code GET /flow-designs/check-rules} 的权威顺序）。
     *
     * <p><b>读序即声明序</b>：{@code R-METADATA → R-TRUNK → R-SEQ → R-NODE-CODE → R-NODE-TYPE →
     * R-APPROVER-RULE → R-DECISION → R-THRESHOLD → R-SIGN → R-TIMEOUT → R-SKIP → R-GATE}。
     * 该顺序既决定 {@code GET /flow-designs/check-rules} 的出参顺序，也决定
     * {@code POST /flow-designs/{id}/pre-publish-check} 报告里 {@code checks[]} 的顺序，
     * 设计器按它渲染「校验清单」。
     *
     * <p><b>为什么不是 {@code Map.copyOf}</b>（2026-10-04 修复）：{@code Map.copyOf} 返回的是
     * {@code ImmutableCollections.MapN}，其迭代顺序由**元素哈希与内部 SALT** 决定，与插入顺序无关
     * —— 运行期实测出参顺序为 {@code R-METADATA, R-THRESHOLD, R-SEQ, …}（同一进程内稳定、
     * 跨进程/跨 JDK 不可复现）。改用「{@code LinkedHashMap} 保序 + {@code unmodifiableMap} 只读」
     * 后顺序与声明逐行一致，且仍然拒绝任何写入。
     */
    public static final Map<String, String> RULES = rules();

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PrePublishChecker() {
    }

    private static Map<String, String> rules() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(R_METADATA, "模板元数据完整（code 与 form_type 一致、version ≥1、node_count 与节点数一致）");
        map.put(R_TRUNK, "主干 7 节点齐备且顺序固定（必填节点不可删）");
        map.put(R_SEQ, "节点顺序从 1 连续递增且不重复");
        map.put(R_NODE_CODE, "节点码合法（主干码与 seq 对应；不使用 enums.md §14 的废弃值）");
        map.put(R_NODE_TYPE, "节点类型合法（condition 为二期预留，一期拒绝）");
        map.put(R_APPROVER_RULE, "审批人解析规则必填且取值合法（9 条），designated 参数齐备");
        map.put(R_DECISION, "决议模式完整；⑦ 归档登记为登记节点，不适用决议与阈值");
        map.put(R_THRESHOLD, "会签阈值合法（绝对人数优先，百分比向上取整）");
        map.put(R_SIGN, "签名策略合法（⑤⑥ 默认强制、⑦ 默认不签名）");
        map.put(R_TIMEOUT, "超时 ≥24h；开启抄送上级时必须配置超时时长");
        map.put(R_SKIP, "跳过条件字段存在于表单模板，且仅事项审批单②可跳过");
        map.put(R_GATE, "Q6/Q7 闸门配置取值范围合法（次数 0~99 / 天数 1~365）");
        // 保序只读视图：Map.copyOf 会丢插入顺序（见 RULES 的类注释），此处刻意用 LinkedHashMap
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /** 执行校验并产出报告。 */
    public static PrePublishReport run(FlowTemplate template, List<FlowNode> nodes) {
        List<DefinitionProblem> problems = NodeDefinitionValidator.violations(template, nodes);
        List<String> messages = new ArrayList<>();
        Map<String, List<String>> byRule = new LinkedHashMap<>();
        for (DefinitionProblem problem : problems) {
            messages.add(problem.describe());
            byRule.computeIfAbsent(problem.rule(), key -> new ArrayList<>()).add(problem.describe());
        }

        List<PrePublishReport.Check> checks = new ArrayList<>();
        for (Map.Entry<String, String> rule : RULES.entrySet()) {
            List<String> details = byRule.get(rule.getKey());
            checks.add(PrePublishReport.Check.of(rule.getKey(), rule.getValue(), details));
        }
        List<String> warnings = warnings(template, nodes);

        return new PrePublishReport(
                template == null ? null : template.getId(),
                template == null ? null : template.getCode(),
                template == null ? null : template.getVersion(),
                template == null ? null : template.getStatus(),
                messages.isEmpty(),
                LocalDateTime.now().format(TIME),
                checks,
                messages,
                warnings);
    }

    /**
     * 提示项（**不阻止发布**）：需要流程管理员知情、但不是配置错误的情况。
     */
    public static List<String> warnings(FlowTemplate template, List<FlowNode> nodes) {
        List<String> warnings = new ArrayList<>();
        if (nodes != null) {
            for (FlowNode node : nodes) {
                if (node == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(node.getAllowJump())) {
                    warnings.add(NodeDefinitionValidator.nodeLabel(node)
                            + "：已开启自由跳转（默认关闭，templates.md T-08）；跳转必须填写原因并写入审计日志与审批轨迹（AC-46）");
                }
            }
            long nonTrunk = nodes.stream()
                    .filter(node -> node != null && node.getNodeCode() != null)
                    .filter(node -> NodeCode.of(node.getNodeCode()).isEmpty())
                    .count();
            if (nonTrunk > 0) {
                warnings.add("模板包含 " + nonTrunk + " 个非主干节点（如抄送/额外审批）：主干仍固定 7 个，二者互不影响");
            }
        }
        if (template != null && template.gatePolicy().isUnlimited()) {
            warnings.add("Q6/Q7 未配置：流转/回退与补件次数**不限**、补件**不设时限**"
                    + "（templates.md §1.7；如需与 V0.4 默认值一致请配 5 / 3 / 3 工作日 / notify）");
        }
        return warnings;
    }
}
