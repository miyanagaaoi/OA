package com.oa.workflow.runtime.infra.row;

import java.time.LocalDateTime;

/**
 * 审批任务行（{@code flow_task}，doc/data-model.md §5.3）。
 *
 * <p>会签 = 同一 {@code node_instance_id} 下多条；依次签 = 同一节点实例下**逐条产生**。
 * {@code add_sign_type IS NULL} 的是「主任务」（参与阈值计数），加签任务不参与计数。
 */
public class FlowTaskRow {

    private Long id;
    private Long instanceId;
    private Long nodeInstanceId;
    private Long assigneeId;
    private Long originAssigneeId;
    private Long delegateFrom;
    private String addSignType;
    private String status;
    private String opinion;
    private LocalDateTime decidedAt;
    private String handoverReason;
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

    public Long getNodeInstanceId() {
        return nodeInstanceId;
    }

    public void setNodeInstanceId(Long nodeInstanceId) {
        this.nodeInstanceId = nodeInstanceId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Long getOriginAssigneeId() {
        return originAssigneeId;
    }

    public void setOriginAssigneeId(Long originAssigneeId) {
        this.originAssigneeId = originAssigneeId;
    }

    public Long getDelegateFrom() {
        return delegateFrom;
    }

    public void setDelegateFrom(Long delegateFrom) {
        this.delegateFrom = delegateFrom;
    }

    public String getAddSignType() {
        return addSignType;
    }

    public void setAddSignType(String addSignType) {
        this.addSignType = addSignType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getHandoverReason() {
        return handoverReason;
    }

    public void setHandoverReason(String handoverReason) {
        this.handoverReason = handoverReason;
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

    /** 是否加签任务（不参与阈值计数）。 */
    public boolean addSignTask() {
        return addSignType != null && !addSignType.isBlank();
    }

    /** 是否待处理。 */
    public boolean pending() {
        return "pending".equals(status);
    }
}
