package com.oa.workflow.runtime.infra.row;

import java.time.LocalDateTime;

/**
 * 节点实例行（{@code flow_node_instance}，doc/data-model.md §5.2）。
 *
 * <p>{@code node_key} 的唯一键口径由**引擎写入时计算**：
 * {@code concat(node_seq, ':', node_code, ':', IFNULL(dept_id, 0))}
 * —— DDL 明确「MySQL 唯一键对 NULL 不去重，故用本列替代裸 dept_id」（§8.2）。
 *
 * <p>{@code approver_ids_json} 是**本节点实例的运行时候选人**：
 * 主干节点从快照派生（doc/data-model.md §7.1 实现要求第 2 条）；
 * 协同组 / 流转承接部门 / 加签产生的实例行由运行时解析结果填充。
 */
public class FlowNodeInstanceRow {

    private Long id;
    private Long instanceId;
    private Integer nodeSeq;
    private String nodeCode;
    private String nodeName;
    private String nodeKey;
    private Long deptId;
    private String decisionMode;
    private String passThreshold;
    private String approverIdsJson;
    private String status;
    private Integer returnedCount;
    private Boolean supplementRequested;
    private String addSignChainJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public Integer getNodeSeq() {
        return nodeSeq;
    }

    public void setNodeSeq(Integer nodeSeq) {
        this.nodeSeq = nodeSeq;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
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

    public String getApproverIdsJson() {
        return approverIdsJson;
    }

    public void setApproverIdsJson(String approverIdsJson) {
        this.approverIdsJson = approverIdsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getReturnedCount() {
        return returnedCount;
    }

    public void setReturnedCount(Integer returnedCount) {
        this.returnedCount = returnedCount;
    }

    public Boolean getSupplementRequested() {
        return supplementRequested;
    }

    public void setSupplementRequested(Boolean supplementRequested) {
        this.supplementRequested = supplementRequested;
    }

    public String getAddSignChainJson() {
        return addSignChainJson;
    }

    public void setAddSignChainJson(String addSignChainJson) {
        this.addSignChainJson = addSignChainJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
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
