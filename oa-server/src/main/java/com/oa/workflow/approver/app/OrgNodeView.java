package com.oa.workflow.approver.app;

/**
 * 组织节点视图（解析规则读取组织链所需的最小字段集）。
 *
 * @param id       节点 id
 * @param parentId 上级节点（集团根为 {@code null}）
 * @param orgType  集团 / 公司 / 部门 / 科室（{@code group} / {@code company} / {@code dept} / {@code section}）
 * @param name     名称
 * @param path     祖先路径（含自身，形如 {@code /1/12/135/}）
 * @param depth    层级（1 集团 / 2 公司 / 3 部门 / 4 科室）
 * @param status   启用状态（{@code active} / {@code disabled}）
 */
public record OrgNodeView(
        Long id,
        Long parentId,
        String orgType,
        String name,
        String path,
        Integer depth,
        String status
) {

    public boolean isGroup() {
        return "group".equalsIgnoreCase(orgType);
    }

    public boolean isCompany() {
        return "company".equalsIgnoreCase(orgType);
    }

    public boolean active() {
        return status == null || "active".equalsIgnoreCase(status);
    }
}
