package com.oa.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 人员（{@code oa.identity.user.*}）接口出入参。
 *
 * <p>安全口径（PRD §5.3 / TC-AUTH-007）：{@code phone} 返回时**默认脱敏**
 * （{@code 138****8888}），仅本人与系统管理员可见完整值；
 * {@code passwordHash} 永不出现在任何出参中。
 */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * 新增人员（{@code POST /api/v1/identity/users}）。
     *
     * <p>初始口令由服务端随机生成（import-spec §3.3 / T-03：随机口令 + 加密清单线下分发 +
     * 首登强制改密），**只在本次响应中返回一次**，不落明文、不进审计日志。
     */
    public record UserCreateRequest(
            @NotBlank(message = "账号不能为空") @Size(max = 64, message = "账号不得超过 64 字符") String account,
            @NotBlank(message = "姓名不能为空") @Size(max = 50, message = "姓名不得超过 50 字符") String name,
            @Size(max = 32, message = "工号不得超过 32 字符") String employeeNo,
            @Size(max = 255, message = "手机号密文不得超过 255 字符") String phone,
            @Size(max = 128, message = "邮箱不得超过 128 字符") String email,
            @NotNull(message = "归属公司不能为空") Long companyId,
            Long orgId,
            @Size(max = 50, message = "职务名称不得超过 50 字符") String position,
            String status,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark
    ) {
    }

    /**
     * 修改人员档案（{@code PUT /api/v1/identity/users/{id}}）。
     *
     * <p><b>覆盖语义</b>：{@code name/phone/email/position/remark} 为**整体覆盖**
     * （传 {@code null} 即清空该字段）；{@code employeeNo/companyId/orgId} 为**条件更新**
     * （传 {@code null} 表示不变更，避免误清工号与归属）。
     *
     * <p><b>状态</b>（施工要求第 8 条）：{@code status} 只接受 {@code active}/{@code disabled}
     * （中文 {@code 在职}/{@code 停用} 亦可）；传 {@code null} 表示不变更。
     * 切到 {@code disabled} 时与 {@code /resign} **同一口径**：先走
     * {@code InFlightChecker#checkUser} + {@code InFlightGuard}（命中且
     * {@code oa.identity.block-on-inflight=true} 时 409，文案含数量与单号）。
     * {@code resigned}（离职）**只能**走 {@code POST /users/{id}/resign}，
     * 本接口传 {@code 离职} 一律 400；已是离职状态的人员其状态不可经本接口回改（409）。
     *
     * <p><b>{@code reason}/{@code force}（AC-52）</b>：与 {@link ResignRequest} 同一口径 ——
     * {@code force=true} 时必须非空 {@code reason}（否则 400）且调用人须为系统管理员（否则 403）；
     * 两者随 {@code @Audited(recordArgs=true)} 落 {@code sys_log}。{@code force} 只覆盖
     * 「切到停用」时的在途/待办阻断（AC-12 的默认阻断不变），其它字段的修改不受影响。
     * 两个字段**追加在末尾**：旧请求体不带它们依然可用（向后兼容）。
     */
    public record UserUpdateRequest(
            @NotBlank(message = "姓名不能为空") @Size(max = 50, message = "姓名不得超过 50 字符") String name,
            @Size(max = 32, message = "工号不得超过 32 字符") String employeeNo,
            @Size(max = 255, message = "手机号密文不得超过 255 字符") String phone,
            @Size(max = 128, message = "邮箱不得超过 128 字符") String email,
            Long companyId,
            Long orgId,
            @Size(max = 50, message = "职务名称不得超过 50 字符") String position,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark,
            String status,
            @Size(max = 500, message = "原因不得超过 500 字符") String reason,
            Boolean force
    ) {
    }

    /**
     * 离职（{@code POST /users/{id}/resign}）。
     *
     * <p>{@code force}/{@code reason} 为 AC-52 的**危险操作留痕**字段：{@code force=true} 时必须非空
     * {@code reason}（否则 400）且调用人须为系统管理员（否则 403）；两者随 {@code @Audited} 写进
     * {@code sys_log}。{@code force} **不改变** {@code oa.identity.block-on-inflight} 的拦截判定
     * （AC-12 的硬阻断口径由该开关决定）。
     */
    public record ResignRequest(
            @Size(max = 500, message = "原因不得超过 500 字符") String reason,
            Boolean force
    ) {
    }

    /**
     * 调岗（{@code POST /users/{id}/transfer}）：换主归属组织。
     *
     * @param targetOrgId          目标组织（新主归属，{@code sys_user.org_id}）
     * @param targetCompanyId      目标公司；为空时按目标组织的最近公司祖先推导
     * @param keepOtherPositions   是否保留其它任职（兼职）；{@code false} 时一并解除。
     *                             DDL 无「任职历史」表，历史变更以 {@code sys_log} 留痕
     * @param reason               调岗原因（AC-52 留痕；{@code force=true} 时必填，否则 400）
     * @param force                强制继续标记（{@code true} 时须为系统管理员，否则 403）
     */
    public record TransferRequest(
            @NotNull(message = "目标组织不能为空") Long targetOrgId,
            Long targetCompanyId,
            Boolean keepOtherPositions,
            @Size(max = 500, message = "原因不得超过 500 字符") String reason,
            Boolean force
    ) {
    }

    /** 工作交接（{@code POST /users/{id}/handover}）。 */
    public record HandoverRequest(
            @NotNull(message = "接收人不能为空") Long toUserId,
            @Size(max = 255, message = "原因不得超过 255 字符") String reason,
            Boolean transferTasks
    ) {
    }

    /**
     * 人员视图。
     *
     * <p><b>{@code pendingTaskCount}</b>（施工要求第 11 条）：名下待处理待办数，来自
     * {@code InFlightChecker} 端口；流程表未落地时桩返回 0，此处**回 0 而不是 {@code null}**
     * （前端列表直接展示数字，不需要额外的空值分支）。
     */
    public record UserView(
            Long id,
            String account,
            String name,
            String employeeNo,
            String phone,
            boolean phoneMasked,
            String email,
            Long orgId,
            String orgName,
            String orgPath,
            Long companyId,
            String companyName,
            String position,
            String status,
            String statusLabel,
            String remark,
            String lastLoginAt,
            Integer pendingTaskCount
    ) {
    }

    /** 新增结果：人员视图 + **仅本次返回**的初始口令。 */
    public record UserCreatedView(
            UserView user,
            String initialPassword,
            String note
    ) {
    }

    /** 离职结果（含影响清单与仍担任负责人的组织，便于管理员补配置）。 */
    public record ResignResult(
            Long userId,
            String status,
            String statusLabel,
            int pendingTasks,
            int inFlightInstances,
            List<String> bizNos,
            List<LeaderDtos.LeaderOfView> leaderOf,
            String message
    ) {
    }

    /** 调岗结果。 */
    public record TransferResult(
            Long userId,
            Long oldOrgId,
            Long newOrgId,
            Long oldCompanyId,
            Long newCompanyId,
            Long primaryPositionId,
            List<Long> removedPositionIds,
            String note
    ) {
    }

    /** 工作交接结果（本期只返回待办清单 + 审计留痕，不做流程引擎联动）。 */
    public record HandoverResult(
            Long fromUserId,
            Long toUserId,
            int taskCount,
            int transferred,
            List<PendingTaskView> pendingTasks,
            String note
    ) {
    }

    /** 待办条目。 */
    public record PendingTaskView(
            Long taskId,
            String bizNo,
            String nodeName,
            String createdAt
    ) {
    }
}
