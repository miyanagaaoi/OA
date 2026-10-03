package com.oa.workflow.definition.domain;

import java.time.LocalDateTime;

/**
 * 流程节点定义（表 {@code flow_node}，doc/data-model.md §4.2）。
 *
 * <p>节点配置的权威契约是 doc/templates.md §1（四类单据 × 7 节点配置表）与 §1.6（种子 JSON）。
 * 本类**只承载列值**，合法性判定一律在
 * {@code com.oa.workflow.definition.app.NodeDefinitionValidator}（纯函数，可穷举单测）。
 *
 * <p>注意 {@code decision_mode = NULL} 是**合法**的：仅 ⑦ {@code archive_register}
 * 归档登记节点（默认「仅登记不审批」）使用，此时 {@code pass_threshold} 也为 {@code NULL}
 * （templates.md B-01 / §1.0）。
 */
public class FlowNode {

    private Long id;
    private Long templateId;
    private Integer seq;
    private String nodeCode;
    private String name;
    private String nodeType;
    private String approverRule;
    /** {@code designated} 时填 {@code {"user_ids":[...]}} 或 {@code {"role_code":"..."}}。 */
    private String approverParam;
    private String decisionMode;
    private String passThreshold;
    private String signPolicy;
    private Integer timeoutHours;
    private Boolean timeoutCcSuperior;
    private Boolean allowAddSign;
    private Boolean allowJump;
    private Boolean allowRoute;
    private String skipCondition;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getApproverRule() {
        return approverRule;
    }

    public void setApproverRule(String approverRule) {
        this.approverRule = approverRule;
    }

    public String getApproverParam() {
        return approverParam;
    }

    public void setApproverParam(String approverParam) {
        this.approverParam = approverParam;
    }

    public String getDecisionMode() {
        return decisionMode;
    }

    public void setDecisionMode(String decisionMode) {
        this.decisionMode = decisionMode;
    }

    public String getPassThreshold() {
        return passThreshold;
    }

    public void setPassThreshold(String passThreshold) {
        this.passThreshold = passThreshold;
    }

    public String getSignPolicy() {
        return signPolicy;
    }

    public void setSignPolicy(String signPolicy) {
        this.signPolicy = signPolicy;
    }

    public Integer getTimeoutHours() {
        return timeoutHours;
    }

    public void setTimeoutHours(Integer timeoutHours) {
        this.timeoutHours = timeoutHours;
    }

    public Boolean getTimeoutCcSuperior() {
        return timeoutCcSuperior;
    }

    public void setTimeoutCcSuperior(Boolean timeoutCcSuperior) {
        this.timeoutCcSuperior = timeoutCcSuperior;
    }

    public Boolean getAllowAddSign() {
        return allowAddSign;
    }

    public void setAllowAddSign(Boolean allowAddSign) {
        this.allowAddSign = allowAddSign;
    }

    public Boolean getAllowJump() {
        return allowJump;
    }

    public void setAllowJump(Boolean allowJump) {
        this.allowJump = allowJump;
    }

    public Boolean getAllowRoute() {
        return allowRoute;
    }

    public void setAllowRoute(Boolean allowRoute) {
        this.allowRoute = allowRoute;
    }

    public String getSkipCondition() {
        return skipCondition;
    }

    public void setSkipCondition(String skipCondition) {
        this.skipCondition = skipCondition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
