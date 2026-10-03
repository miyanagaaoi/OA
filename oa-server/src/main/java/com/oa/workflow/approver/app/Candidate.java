package com.oa.workflow.approver.app;

import java.util.Objects;

/**
 * 审批人候选（解析规则的输出单元）。
 *
 * <p>字段覆盖快照要求的「姓名 / 工号 / 组织」（doc/data-model.md §7.1 的
 * {@code approvers[{user_id,name,org_id}]} 的超集：额外带 {@code employee_no} 与 {@code org_path}，
 * 便于快照到期后仍能还原「当时是谁、在哪个组织」）。
 *
 * @param userId     用户 id（{@code sys_user.id}）
 * @param name       姓名
 * @param account    登录账号（便于运维核对）
 * @param employeeNo 工号（水印与打印用）
 * @param orgId      主归属组织节点
 * @param orgName    主归属组织名称（快照可读性）
 * @param orgPath    主归属组织路径（形如 {@code /1/12/135/}）
 * @param companyId  归属公司
 * @param position   职务名
 * @param userStatus {@code sys_user.status}（active / disabled / resigned）
 */
public record Candidate(
        Long userId,
        String name,
        String account,
        String employeeNo,
        Long orgId,
        String orgName,
        String orgPath,
        Long companyId,
        String position,
        String userStatus
) {

    /** 是否可用于审批：在职（{@code active}）且有用户 id。 */
    public boolean assignable() {
        return userId != null && (userStatus == null || "active".equalsIgnoreCase(userStatus));
    }

    /** 去重键：用户 id。 */
    public Long dedupKey() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Candidate candidate)) {
            return false;
        }
        return Objects.equals(userId, candidate.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
