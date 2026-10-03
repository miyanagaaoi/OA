package com.oa.workflow.approver.infra.row;

/**
 * <b>在途实例锁版本</b>读模型（{@code AC-09} 的可见性口径）—— {@code GET /flow-templates/{id}/locked-by} 的行。
 *
 * <p>数据来源：{@code flow_instance i LEFT JOIN sys_user u}（+ 当前节点名的关联子查询），
 * 由 {@code FlowInstanceMapper#selectInFlightByTemplate} 在**调用人的数据域**下执行 —— 域外实例
 * 查不到（不是报错），因此本行只承载「我已经有权看见的在途单据」。
 *
 * <p>字段刻意只含「管理员判断影响面」所需的最小集：单号 / 发起人 / 当前节点 / 发起时间；
 * 不含表单正文、审批人快照与任何单据字段值（那是 2b 的数据域视图，不在本接口的口径内）。
 */
public class LockedInstanceRow {

    private Long instanceId;
    private String bizNo;
    private Long templateId;
    private Integer templateVersion;
    private String status;
    private String subStatus;
    private Long initiatorId;
    private String initiatorName;
    private Integer currentNodeSeq;
    private String currentNodeName;
    private String submittedAt;

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
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

    public Long getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public Integer getCurrentNodeSeq() {
        return currentNodeSeq;
    }

    public void setCurrentNodeSeq(Integer currentNodeSeq) {
        this.currentNodeSeq = currentNodeSeq;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }
}
