package com.oa.identity.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 岗位与负责人（{@code oa.identity.position.*}）接口出入参。
 *
 * <p>负责人绑定的枚举：{@code leaderType} 接受 {@code primary/deputy} 或 {@code 正职/副职}；
 * {@code category}（＝业务线）接受事项类别五值 code 或中文标签（{@code 经营/经济/行政/人力/投资}），
 * 旧码 {@code operate} 解析时归一为 {@code business}（enums.md §14）。
 */
public final class LeaderDtos {

    private LeaderDtos() {
    }

    /** 新增负责人绑定（{@code POST /orgs/{id}/leaders}）。 */
    public record LeaderCreateRequest(
            @NotNull(message = "负责人不能为空") Long userId,
            String leaderType,
            @Size(max = 50, message = "岗位名不得超过 50 字符") String dutyTitle,
            String category,
            Integer sortNo,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    /** 调整负责人（正/副职、岗位名、业务线、排序、生效区间、备注）（{@code PUT /orgs/{id}/leaders/{leaderId}}）。 */
    public record LeaderUpdateRequest(
            String leaderType,
            @Size(max = 50, message = "岗位名不得超过 50 字符") String dutyTitle,
            String category,
            Integer sortNo,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    /** 负责人视图。 */
    public record LeaderView(
            Long id,
            Long orgId,
            String orgPath,
            String orgName,
            Long userId,
            String userName,
            String account,
            String employeeNo,
            String leaderType,
            String leaderTypeLabel,
            String dutyTitle,
            String category,
            String categoryLabel,
            Integer sortNo,
            String remark,
            String userStatus
    ) {
    }

    /** 负责人候选人（该组织成员 + 是否已任职标注）。 */
    public record LeaderCandidateView(
            Long userId,
            String name,
            String account,
            String employeeNo,
            String userStatus,
            Long orgId,
            String position,
            boolean primaryPosition,
            boolean leader,
            Long leaderId,
            String leaderType,
            String leaderTypeLabel,
            String category,
            String categoryLabel
    ) {
    }

    /**
     * 集团层某业务线的分管领导（{@code GET /leaders/lines}）。
     *
     * <p>五个事项类别**全部返回**（未配置时 {@code configured = false} 且 {@code leaders} 为空），
     * 便于前端直接渲染五行配置位。
     */
    public record LeaderLineView(
            String category,
            String categoryLabel,
            Long orgId,
            String orgName,
            boolean configured,
            List<LeaderView> leaders
    ) {
    }

    /**
     * 设置某业务线的分管领导（{@code PUT /leaders/lines/{category}}）。
     *
     * <p>语义为**替换**：该业务线原有的正职分管领导会被解绑并由 {@code userId} 顶替
     * （业务线绑定与普通负责人互不冲突，见 {@code LeaderPolicy}）。
     *
     * <p>{@code reason}/{@code force} 为**危险操作留痕**字段（AC-52，绑/解绑分管领导属高影响变更）：
     * {@code force=true} 时必须非空 {@code reason}（否则 400）且调用人须为系统管理员（否则 403）；
     * 两者随 {@code @Audited} 写进 {@code sys_log} 的入参 JSON。
     */
    public record LeaderLineUpsertRequest(
            Long orgId,
            @NotNull(message = "分管领导不能为空") Long userId,
            String leaderType,
            @Size(max = 50, message = "岗位名不得超过 50 字符") String dutyTitle,
            Integer sortNo,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark,
            @Size(max = 500, message = "原因不得超过 500 字符") String reason,
            Boolean force
    ) {
    }

    /** 该人担任负责人的组织清单（{@code GET /users/{id}/leader-of}）。 */
    public record LeaderOfView(
            Long orgId,
            String orgPath,
            String orgName,
            String orgType,
            String leaderType,
            String leaderTypeLabel,
            String category,
            String categoryLabel,
            Integer sortNo,
            String dutyTitle
    ) {
    }
}
