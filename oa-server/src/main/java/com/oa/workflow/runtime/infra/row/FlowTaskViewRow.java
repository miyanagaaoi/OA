package com.oa.workflow.runtime.infra.row;

/**
 * 待办 / 已办列表行（{@code flow_task} ⋈ {@code flow_instance}，2a.5）。
 *
 * <p>列表出参只需要「够用即可」的展示字段（单号 / 类型 / 节点 / 起单人 / 状态 / 时间）；
 * 单据正文与审批人快照不在列表下发（详情另取），减少域外信息暴露面。
 */
public class FlowTaskViewRow {

    private Long taskId;
    private Long instanceId;
    private String bizNo;
    private String formType;
    private String category;
    private Integer nodeSeq;
    private String nodeName;
    private String nodeCode;
    private Long assigneeId;
    private Long originAssigneeId;
    private Long delegateFrom;
    private String addSignType;
    private String taskStatus;
    private String opinion;
    private String handoverReason;
    private String taskCreatedAt;
    private String decidedAt;
    private Long initiatorId;
    private String initiatorName;
    private Integer currentNodeSeq;
    private String instanceStatus;
    private String subStatus;
    /** 单据标题（{@code form_data.fields_json.title}；列表追加字段，关键字筛选的命中项之一）。 */
    private String title;
    /** 分页总数（{@code COUNT(*) OVER()} 或独立 count 语句回填；列表查询为 {@code null}）。 */
    private Long total;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
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

    public Integer getNodeSeq() {
        return nodeSeq;
    }

    public void setNodeSeq(Integer nodeSeq) {
        this.nodeSeq = nodeSeq;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
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

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public String getHandoverReason() {
        return handoverReason;
    }

    public void setHandoverReason(String handoverReason) {
        this.handoverReason = handoverReason;
    }

    public String getTaskCreatedAt() {
        return taskCreatedAt;
    }

    public void setTaskCreatedAt(String taskCreatedAt) {
        this.taskCreatedAt = taskCreatedAt;
    }

    public String getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(String decidedAt) {
        this.decidedAt = decidedAt;
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

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
