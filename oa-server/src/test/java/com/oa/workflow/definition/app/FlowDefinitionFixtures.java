package com.oa.workflow.definition.app;

import com.oa.workflow.definition.domain.FlowDefinitionEnums.DecisionMode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeCode;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.NodeType;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.SignPolicy;
import com.oa.workflow.definition.domain.FlowDefinitionEnums.TemplateStatus;
import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import com.oa.workflow.definition.domain.FlowGateEnums.TimeoutAction;
import com.oa.workflow.definition.domain.FlowGatePolicy;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程模板测试夹具：**逐字复刻** doc/templates.md §1.1（事项审批单 × 7 节点配置表）与 §1.6（种子 JSON）。
 *
 * <p>它的价值在于「文档口径 == 代码口径 == 测试口径」三者对齐：
 * 任何人改了 §1 的默认值（超时 48/24、签名策略 ⑤⑥ required、② 跳过条件、allow_route ②⑤⑥），
 * 本夹具必须同步改，否则预检与校验测试会立刻红。
 */
public final class FlowDefinitionFixtures {

    /** 事项单表单字段定义（doc/templates.md §2.4 的最小子集，够跳过条件校验用）。 */
    public static final String MATTER_FORM_SCHEMA = """
            {"form_type":"matter","template_code":"matter","schema_version":1,"fields":[
              {"code":"title","type":"text","required":true},
              {"code":"category","type":"select","required":true},
              {"code":"involve_cost","type":"boolean","required":true},
              {"code":"amount","type":"amount"}
            ]}""";

    private FlowDefinitionFixtures() {
    }

    /** 已发布的 v1 事项单模板（gate policy 取 V0.4 默认值）。 */
    public static FlowTemplate matterV1() {
        return matter(1L, 1, TemplateStatus.PUBLISHED.code());
    }

    /** 指定 id / 版本 / 状态的模板。 */
    public static FlowTemplate matter(Long id, int version, String status) {
        FlowTemplate template = new FlowTemplate();
        template.setId(id);
        template.setCode("matter");
        template.setName("事项审批单流程");
        template.setFormType("matter");
        template.setVersion(version);
        template.setStatus(status);
        template.setNodeCount(7);
        template.setFormSchemaJson(MATTER_FORM_SCHEMA);
        template.applyGatePolicy(FlowGatePolicy.v04Defaults());
        return template;
    }

    /** 7 个主干节点（逐字对照 templates.md §1.1）。 */
    public static List<FlowNode> matterNodes(Long templateId) {
        List<FlowNode> nodes = new ArrayList<>();
        nodes.add(node(templateId, NodeCode.DEPT_LEADER, "直属部门负责人", "dept_leader_upward", null,
                DecisionMode.ANY.code(), null, SignPolicy.OPTIONAL, 24, true, false, null));
        nodes.add(node(templateId, NodeCode.FINANCE_REVIEW, "财务部复核", "finance_owner", null,
                DecisionMode.ANY.code(), null, SignPolicy.OPTIONAL, 48, true, true,
                "{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}"));
        nodes.add(node(templateId, NodeCode.BRANCH_LEADER, "分公司分管领导", "branch_leader", null,
                DecisionMode.ANY.code(), null, SignPolicy.OPTIONAL, 24, true, false, null));
        nodes.add(node(templateId, NodeCode.SUBSIDIARY_GM, "子公司总经理", "subsidiary_gm", null,
                DecisionMode.ANY.code(), null, SignPolicy.OPTIONAL, 24, true, false, null));
        nodes.add(node(templateId, NodeCode.GROUP_LEADER, "集团分管领导", "group_leader", null,
                DecisionMode.ANY.code(), null, SignPolicy.REQUIRED, 24, true, true, null));
        nodes.add(node(templateId, NodeCode.CHAIRMAN, "集团董事长", "chairman", null,
                DecisionMode.ANY.code(), null, SignPolicy.REQUIRED, 24, true, true, null));
        FlowNode archive = node(templateId, NodeCode.ARCHIVE_REGISTER, "归档登记", "designated",
                "{\"role_code\":\"finance_clerk\"}", null, null, SignPolicy.NONE, 24, false, false, null);
        archive.setNodeType(NodeType.ARCHIVE.code());
        nodes.add(archive);
        return nodes;
    }

    /**
     * 单个节点（默认 approve 类型、允许加签、禁止跳转）。
     *
     * @param allowRoute 是否允许流转/回退（templates.md T-02：仅 ②⑤⑥ 开启）
     */
    public static FlowNode node(Long templateId, NodeCode code, String name, String rule, String param,
                                String decisionMode, String threshold, SignPolicy sign, Integer timeoutHours,
                                boolean allowAddSign, boolean allowRoute, String skipCondition) {
        FlowNode node = new FlowNode();
        node.setId((long) code.seq());
        node.setTemplateId(templateId);
        node.setSeq(code.seq());
        node.setNodeCode(code.code());
        node.setName(name);
        node.setNodeType(NodeType.APPROVE.code());
        node.setApproverRule(rule);
        node.setApproverParam(param);
        node.setDecisionMode(decisionMode);
        node.setPassThreshold(threshold);
        node.setSignPolicy(sign.code());
        node.setTimeoutHours(timeoutHours);
        node.setTimeoutCcSuperior(Boolean.FALSE);
        node.setAllowAddSign(allowAddSign);
        node.setAllowJump(Boolean.FALSE);
        node.setAllowRoute(allowRoute);
        node.setSkipCondition(skipCondition);
        return node;
    }

    /** 一个新的空节点（新增用）。 */
    public static FlowNode custom(Long templateId, int seq, String nodeCode, String name, String rule) {
        FlowNode node = new FlowNode();
        node.setTemplateId(templateId);
        node.setSeq(seq);
        node.setNodeCode(nodeCode);
        node.setName(name);
        node.setNodeType(NodeType.APPROVE.code());
        node.setApproverRule(rule);
        node.setDecisionMode(DecisionMode.ANY.code());
        node.setSignPolicy(SignPolicy.OPTIONAL.code());
        node.setTimeoutHours(24);
        node.setTimeoutCcSuperior(Boolean.FALSE);
        node.setAllowAddSign(Boolean.TRUE);
        node.setAllowJump(Boolean.FALSE);
        node.setAllowRoute(Boolean.FALSE);
        return node;
    }

    /** 「未配置闸门」的模板（Q6/Q7 均为不限）。 */
    public static FlowTemplate unlimitedGateTemplate(Long id, int version, String status) {
        FlowTemplate template = matter(id, version, status);
        template.applyGatePolicy(new FlowGatePolicy(null, null, null, null, TimeoutAction.NOTIFY));
        return template;
    }

    /** 带工作日的闸门配置（用于读回断言）。 */
    public static FlowGatePolicy customGate() {
        return new FlowGatePolicy(9, 4, 15, DeadlineType.CALENDAR, TimeoutAction.AUTO_PASS);
    }
}
