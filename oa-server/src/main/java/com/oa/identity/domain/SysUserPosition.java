package com.oa.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oa.common.scope.DataScopeKind;
import com.oa.common.scope.DataScopeTable;
import java.time.LocalDateTime;

/**
 * 岗位任职（{@code sys_user_position}）—— 列名严格取自 doc/data-model.md §2.4 的 DDL。
 *
 * <p>「一人多岗」的落地表：一名员工可同时挂职多个组织节点（PRD §5.1）；
 * 唯一键 {@code uk_user_org (user_id, org_id)}；
 * <b>每人至多 1 个 {@code is_primary=1}</b>，切换主岗时必须在同一事务内把旧主岗置 0
 * （见 {@code PositionPolicy#demotionsFor} 与 {@code PositionService}）。
 *
 * <p>数据域登记为 {@link DataScopeKind#NONE}（受控表 + 必须写标记 + 不做行级过滤）：
 * 岗位行的可见性随其 {@code user_id} 的可见性收敛，用户类查询已在 {@code sys_user} 一侧
 * 织入 USER 口径（{@code sys_user} 与 {@code sys_user_position} 的 join 条件即 {@code user_id}）。
 */
@TableName("sys_user_position")
@DataScopeTable(table = "sys_user_position", alias = "up", kind = DataScopeKind.NONE)
public class SysUserPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long orgId;

    /** 是否主岗（TINYINT(1)）；同一用户至多一条为 1。 */
    private Integer isPrimary;

    /** 岗位名称（主岗同时回填 {@code sys_user.position}）。 */
    private String position;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Integer getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Integer isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 是否主岗（{@code is_primary = 1}）。 */
    public boolean primary() {
        return isPrimary != null && isPrimary == 1;
    }
}
