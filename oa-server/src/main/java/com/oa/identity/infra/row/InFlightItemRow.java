package com.oa.identity.infra.row;

/**
 * 在途/待办明细行（影响清单：人员口径与组织口径共用）。
 *
 * <p>字段与 doc/import-spec.md §7.2「受影响在途单据清单」对齐；
 * 组织口径不涉及单条任务时 {@code nodeName}/{@code status} 可为 {@code null}。
 */
public class InFlightItemRow {

    private Long instanceId;
    private String bizNo;
    private String formType;
    private String nodeName;
    private String initiatorName;
    private String currentNodeName;
    private String status;

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

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
