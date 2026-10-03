package com.oa.workflow.approver.infra.row;

import java.time.LocalDateTime;

/**
 * 流程实例行（{@code flow_instance}，doc/data-model.md §5.1）。
 *
 * <p>{@code approver_snapshot_json} 用 {@code String} 承载原始 JSON 文本：**运行时权威是该文本本身**
 * （不做「读出来再写回去」的往返，避免任何序列化差异破坏不可变性）。
 */
public class FlowInstanceRow {

    private Long id;
    private String bizNo;
    private Long templateId;
    private Integer templateVersion;
    private Long formDataId;
    private String formType;
    private String category;
    private Long initiatorId;
    private Long initiatorOrgId;
    private Long initiatorCompanyId;
    private String initiatorOrgPath;
    private String approverSnapshotJson;
    private String status;
    private String subStatus;
    private Integer currentNodeSeq;
    private Long currentDeptId;
    private Long ownerDeptId;
    private Integer routingSeq;
    private Integer routingCount;
    private Integer supplementCount;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Integer getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(Integer templateVersion) {
        this.templateVersion = templateVersion;
    }

    public Long getFormDataId() {
        return formDataId;
    }

    public void setFormDataId(Long formDataId) {
        this.formDataId = formDataId;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public Long getInitiatorOrgId() {
        return initiatorOrgId;
    }

    public void setInitiatorOrgId(Long initiatorOrgId) {
        this.initiatorOrgId = initiatorOrgId;
    }

    public Long getInitiatorCompanyId() {
        return initiatorCompanyId;
    }

    public void setInitiatorCompanyId(Long initiatorCompanyId) {
        this.initiatorCompanyId = initiatorCompanyId;
    }

    public String getInitiatorOrgPath() {
        return initiatorOrgPath;
    }

    public void setInitiatorOrgPath(String initiatorOrgPath) {
        this.initiatorOrgPath = initiatorOrgPath;
    }

    public String getApproverSnapshotJson() {
        return approverSnapshotJson;
    }

    public void setApproverSnapshotJson(String approverSnapshotJson) {
        this.approverSnapshotJson = approverSnapshotJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(String subStatus) {
        this.subStatus = subStatus;
    }

    public Integer getCurrentNodeSeq() {
        return currentNodeSeq;
    }

    public void setCurrentNodeSeq(Integer currentNodeSeq) {
        this.currentNodeSeq = currentNodeSeq;
    }

    public Long getCurrentDeptId() {
        return currentDeptId;
    }

    public void setCurrentDeptId(Long currentDeptId) {
        this.currentDeptId = currentDeptId;
    }

    public Long getOwnerDeptId() {
        return ownerDeptId;
    }

    public void setOwnerDeptId(Long ownerDeptId) {
        this.ownerDeptId = ownerDeptId;
    }

    public Integer getRoutingSeq() {
        return routingSeq;
    }

    public void setRoutingSeq(Integer routingSeq) {
        this.routingSeq = routingSeq;
    }

    public Integer getRoutingCount() {
        return routingCount;
    }

    public void setRoutingCount(Integer routingCount) {
        this.routingCount = routingCount;
    }

    public Integer getSupplementCount() {
        return supplementCount;
    }

    public void setSupplementCount(Integer supplementCount) {
        this.supplementCount = supplementCount;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
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
}
