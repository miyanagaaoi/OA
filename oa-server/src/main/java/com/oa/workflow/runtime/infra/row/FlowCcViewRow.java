package com.oa.workflow.runtime.infra.row;

/**
 * 「抄送我的一览」行（{@code flow_cc c ⋈ flow_instance i}，数据域过滤主体恒为 {@code i}）。
 *
 * <p>出参覆盖任务书要求的七项：单号、单据类型、标题、发起人、发起时间、当前状态、
 * 抄送时间 / 是否已读。{@code flow_cc} 表里只有 {@code created_at} 与 {@code read_at}
 * （doc/data-model.md §6.3），因此「已读」= {@code readAt != null}。
 *
 * <p>与 {@link FlowTaskViewRow} 一样：单据正文与审批人快照**不下发**到列表
 * （列表只给展示必需的窄字段，减少域外信息暴露面）。
 */
public class FlowCcViewRow {

    /** {@code flow_cc.id}（抄送行 id，列表行主键）。 */
    private Long ccId;
    private Long instanceId;
    private String bizNo;
    private String formType;
    private String category;
    /** 单据标题（{@code form_data.fields_json.title}，四类单据的标题字段码相同）。 */
    private String title;
    private Long initiatorId;
    private String initiatorName;
    /** 单据发起时间（{@code flow_instance.created_at}）。 */
    private String instanceCreatedAt;
    private Integer currentNodeSeq;
    private String instanceStatus;
    private String subStatus;
    /** 抄送时间（{@code flow_cc.created_at}）。 */
    private String ccCreatedAt;
    /** 抄送来源：{@code initiator}（发起人自选）/ {@code template}（模板固定）。 */
    private String ccSource;
    /** 已读时间；{@code null} = 未读。 */
    private String readAt;

    public Long getCcId() {
        return ccId;
    }

    public void setCcId(Long ccId) {
        this.ccId = ccId;
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getInstanceCreatedAt() {
        return instanceCreatedAt;
    }

    public void setInstanceCreatedAt(String instanceCreatedAt) {
        this.instanceCreatedAt = instanceCreatedAt;
    }

    public Integer getCurrentNodeSeq() {
        return currentNodeSeq;
    }

    public void setCurrentNodeSeq(Integer currentNodeSeq) {
        this.currentNodeSeq = currentNodeSeq;
    }

    public String getInstanceStatus() {
        return instanceStatus;
    }

    public void setInstanceStatus(String instanceStatus) {
        this.instanceStatus = instanceStatus;
    }

    public String getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(String subStatus) {
        this.subStatus = subStatus;
    }

    public String getCcCreatedAt() {
        return ccCreatedAt;
    }

    public void setCcCreatedAt(String ccCreatedAt) {
        this.ccCreatedAt = ccCreatedAt;
    }

    public String getCcSource() {
        return ccSource;
    }

    public void setCcSource(String ccSource) {
        this.ccSource = ccSource;
    }

    public String getReadAt() {
        return readAt;
    }

    public void setReadAt(String readAt) {
        this.readAt = readAt;
    }
}
