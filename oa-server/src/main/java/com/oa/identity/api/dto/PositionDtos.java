package com.oa.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 岗位任职（一人多岗）接口出入参。
 *
 * <p>主岗口径（{@code sys_user_position.is_primary}）：**每人至多 1 个主岗**；
 * 新增或改为主岗时，服务端在**同一事务**内把该人的旧主岗置 0
 * （见 {@code PositionPolicy#demotionsFor} 与 {@code PositionService}）。
 */
public final class PositionDtos {

    private PositionDtos() {
    }

    /** 新增任职（{@code POST /users/{id}/positions}）。 */
    public record PositionCreateRequest(
            @NotNull(message = "任职组织不能为空") Long orgId,
            @NotBlank(message = "岗位名称不能为空") @Size(max = 50, message = "岗位名称不得超过 50 字符") String position,
            Boolean isPrimary,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark
    ) {
    }

    /**
     * 修改任职 / 设为主岗（{@code PUT /users/{id}/positions/{positionId}}）。
     *
     * <p>字段全部可选，{@code null} 表示不变更：
     * <ul>
     *   <li>{@code postName}：**规范名**（岗位名称，≤50 字符）；{@code position} 是同一字段的
     *       **旧别名**（前端 {@code updateUserPosition} 曾按 {@code position} 发送），
     *       两者同时出现时以 {@code postName} 为准；都为空则不改岗位名；</li>
     *   <li>{@code isPrimary=true}：设为主岗 —— 同一事务内把该人的旧主岗置 0，并回填
     *       {@code sys_user.org_id}/{@code position}（import-spec §2.2 第 ④ 步 / T-12）；</li>
     *   <li>{@code isPrimary=false}：置为副岗 —— 若该岗位是此人**唯一**主岗则 409
     *       （避免出现「有岗位但无主岗」）；否则把剩余岗位中 id 最小的一条提升为主岗
     *       （复用 {@code PositionPolicy#promotionAfterRemoval} 的既有兜底口径）。</li>
     * </ul>
     */
    public record PositionUpdateRequest(
            @Size(max = 50, message = "岗位名称不得超过 50 字符") String postName,
            @Size(max = 50, message = "岗位名称不得超过 50 字符") String position,
            Boolean isPrimary,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark
    ) {
    }

    /** 岗位视图。 */
    public record PositionView(
            Long id,
            Long userId,
            Long orgId,
            String orgName,
            String orgPath,
            String orgType,
            String orgTypeLabel,
            String position,
            boolean isPrimary,
            String remark,
            String createdAt
    ) {
    }
}
