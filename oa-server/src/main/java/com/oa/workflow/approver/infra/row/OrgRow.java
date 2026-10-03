package com.oa.workflow.approver.infra.row;

/**
 * 组织节点行（{@code sys_org}）。
 *
 * <p>字段与 {@code com.oa.workflow.approver.app.OrgNodeView} 一一对应，
 * 由 {@code JdbcApproverDirectory} 做一层薄映射（infra → app 的方向）。
 */
public class OrgRow {

    private Long id;
    private Long parentId;
    private String orgType;
    private String name;
    private String path;
    private Integer depth;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getOrgType() {
        return orgType;
    }

    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
