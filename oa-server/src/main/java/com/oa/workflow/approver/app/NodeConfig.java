package com.oa.workflow.approver.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.oa.workflow.definition.domain.FlowNode;
import com.oa.workflow.definition.domain.FlowTemplate;

/**
 * 解析一个节点所需的**节点配置切片**（发起时从模板版本读出，随后随快照固化）。
 *
 * <p>它同时是「节点配置快照」的载体：{@code flow_instance.approver_snapshot_json} 会把
 * {@link #decisionMode()} / {@link #passThreshold()} / {@link #signPolicy()} / {@link #timeoutHours()}
 * 等行为字段一并写进快照（templates.md V-07「节点配置冻结」），
 * 因此发布新版本（改决议模式、阈值、增删节点）**不会**影响在途实例。
 *
 * @param nodeId            节点 id（{@code flow_node.id}，仅作溯源）
 * @param seq               节点序号
 * @param nodeCode          节点码（{@code enums.md} §2）
 * @param name              节点名称
 * @param nodeType          {@code approve} / {@code cc} / {@code archive}
 * @param approverRule      解析规则码（{@code enums.md} §3 共 9 条）
 * @param approverParam     {@code designated} / {@code initiator_pick} 的声明参数
 * @param decisionMode      决议模式（⑦ 为 {@code null} = 不适用）
 * @param passThreshold     通过阈值（{@code null} = 过半）
 * @param signPolicy        签名策略
 * @param timeoutHours      超时时长（仅催办）
 * @param timeoutCcSuperior 超时是否抄送上级
 * @param allowAddSign      是否允许加签
 * @param allowJump         是否允许自由跳转（默认关闭）
 * @param allowRoute        是否允许流转/回退（集团层开启）
 * @param skipCondition     跳过条件（仅事项单②）
 */
public record NodeConfig(
        Long nodeId,
        Integer seq,
        String nodeCode,
        String name,
        String nodeType,
        String approverRule,
        JsonNode approverParam,
        String decisionMode,
        String passThreshold,
        String signPolicy,
        Integer timeoutHours,
        Boolean timeoutCcSuperior,
        Boolean allowAddSign,
        Boolean allowJump,
        Boolean allowRoute,
        JsonNode skipCondition
) {

    /** 由模板 + 节点实体构造（发起时读取锁定版本的那一行）。 */
    public static NodeConfig of(FlowTemplate template, FlowNode node) {
        return new NodeConfig(
                node.getId(),
                node.getSeq(),
                node.getNodeCode(),
                node.getName(),
                node.getNodeType(),
                node.getApproverRule(),
                com.oa.common.json.JsonText.read(node.getApproverParam()),
                node.getDecisionMode(),
                node.getPassThreshold(),
                node.getSignPolicy(),
                node.getTimeoutHours(),
                node.getTimeoutCcSuperior(),
                node.getAllowAddSign(),
                node.getAllowJump(),
                node.getAllowRoute(),
                com.oa.common.json.JsonText.read(node.getSkipCondition()));
    }

    /** 跳过条件 JSON 文本（求值用，保留原字面量）。 */
    public String skipConditionText() {
        return skipCondition == null || skipCondition.isNull() ? null : skipCondition.toString();
    }
}
