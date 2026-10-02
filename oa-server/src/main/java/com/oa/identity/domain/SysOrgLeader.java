package com.oa.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oa.common.scope.DataScopeKind;
import com.oa.common.scope.DataScopeTable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 组织负责人（{@code sys_org_leader}）—— 列名严格取自 doc/data-model.md §2.3 的 DDL。
 *
 * <p>本表是**审批人解析的唯一权威来源**（{@code sys_org.leader_id} 只是冗余回填）。
 *
 * <p>唯一键 {@code uk_org_leader (org_id, user_id, leader_type, category)}；
 * 业务口径「同一 {@code (org_id, category)} 下正职至多 1 人」由
 * {@code LeaderPolicy} 在应用层强制（库唯一键抓不住「不同 user 的两个正职」）。
 */
@TableName("sys_org_leader")
@DataScopeTable(table = "sys_org_leader", alias = "ol", kind = DataScopeKind.NONE)
public class SysOrgLeader {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orgId;

    private Long userId;

    /** {@code primary=正职 deputy=副职}（见 {@link IdentityEnums.LeaderType}）。 */
    private String leaderType;

    /** 岗位名，如「财务分管领导」。 */
    private String dutyTitle;

    /**
     * 事项类别（配置项五值）= 业务线：{@code business/economy/admin/hr/invest}；
     * 按业务线绑定时填，仅用于限定范围，**不参与流程路由**；旧码 {@code operate} 作废并迁移为
     * {@code business}（enums.md §14）。为空表示「普通负责人」。
     */
    private String category;

    private Integer sortNo;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String remark;

    private LocalDateTime createdAt;

    private Long createdBy;

    private LocalDateTime updatedAt;

    private Long updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLeaderType() {
        return leaderType;
    }

    public void setLeaderType(String leaderType) {
        this.leaderType = leaderType;
    }

    public String getDutyTitle() {
        return dutyTitle;
    }

    public void setDutyTitle(String dutyTitle) {
        this.dutyTitle = dutyTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** 负责人类型枚举（未知 code 返回 {@code null}）。 */
    public IdentityEnums.LeaderType leaderTypeEnum() {
        return IdentityEnums.LeaderType.ofCode(leaderType);
    }

    /** 业务线枚举（{@code null} 表示普通负责人组）。 */
    public IdentityEnums.Category categoryEnum() {
        return IdentityEnums.Category.ofCode(category);
    }

    /** 是否正职。 */
    public boolean isPrimary() {
        return IdentityEnums.LeaderType.PRIMARY.code().equals(leaderType);
    }
}
