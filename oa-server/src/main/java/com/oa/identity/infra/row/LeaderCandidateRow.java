package com.oa.identity.infra.row;

/**
 * 负责人候选人查询投影（{@code sys_user} LEFT JOIN {@code sys_user_position} LEFT JOIN {@code sys_org_leader}）。
 *
 * <p>候选人口径（{@code GET /orgs/{id}/leader-candidates}）：**该组织成员**——
 * 主归属在该节点（{@code sys_user.org_id}）或在岗位于该节点（{@code sys_user_position.org_id}）；
 * 并标注其在该节点**是否已任职**（{@code leaderId}/{@code leaderType}/{@code category} 非空即已任职）。
 *
 * <p>注：审批人解析所需的「科室无负责人则上溯部门」由流程侧在**发起时**快照完成
 * （PRD §5.4），本接口只返回**本节点**的候选人与任职状态，不做上溯合并。
 */
public class LeaderCandidateRow {

    private Long userId;
    private String name;
    private String account;
    private String employeeNo;
    private String userStatus;
    private Long orgId;
    private String position;
    private Integer isPrimary;
    private Long leaderId;
    private String leaderType;
    private String category;
    private String dutyTitle;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Integer getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Integer isPrimary) {
        this.isPrimary = isPrimary;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }

    public String getLeaderType() {
        return leaderType;
    }

    public void setLeaderType(String leaderType) {
        this.leaderType = leaderType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDutyTitle() {
        return dutyTitle;
    }

    public void setDutyTitle(String dutyTitle) {
        this.dutyTitle = dutyTitle;
    }

    /** 是否已在本节点任职负责人。 */
    public boolean leader() {
        return leaderId != null;
    }
}
