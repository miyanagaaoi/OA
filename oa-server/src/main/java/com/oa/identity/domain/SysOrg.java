package com.oa.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oa.common.scope.DataScopeKind;
import com.oa.common.scope.DataScopeTable;
import java.time.LocalDateTime;

/**
 * 组织架构（{@code sys_org}）—— 列名严格取自 doc/data-model.md §2.1 的 DDL（DDL 为唯一权威）。
 *
 * <p><b>数据域登记</b>：本表以 {@link DataScopeKind#NONE} 登记为**受控表**，含义是
 * 「{@code sys_org} 的 SELECT 必须在 Mapper XML 里显式写 {@code @dataScope} 标记，
 * 否则被 {@code DataScopeInterceptor} fail-closed 拒绝」，但**不做行级过滤**
 * （{@code DataScopeSqlBuilder} 目前只有 INSTANCE / USER 两类口径，没有 ORG 口径）。
 *
 * <p>组织节点可见性因此由**应用层**保证（见 {@code OrgService}）：
 * 树/搜索查询按调用人数据域推导出的可见根集合裁剪，且 {@code directory} 只输出数据域内人员
 * 所归属的节点。这是本阶段明确的**待接入点**：若后续为 {@link com.oa.common.scope.DataScopeSqlBuilder}
 * 增加 ORG 口径（按 {@code sys_org.path} 前缀），此处登记改为 {@code kind = USER} 语义的 ORG 即可，
 * 应用层裁剪可同步移除。
 *
 * <p><b>业务键</b>：{@code path}（id 路径 {@code /1/12/135/}，含自身，全库唯一 {@code uk_sys_org_path}）。
 * DDL **没有** {@code org_code} 列（import-spec T-01 定稿：不新增 {@code org_code}，以 org_path 为唯一业务键；
 * 注意导入模板的 {@code org_path} 是**名称路径**，与 {@code sys_org.path} 的 id 路径不同口径，
 * 见 {@code OrgHierarchy#businessPath}）。
 */
@TableName("sys_org")
@DataScopeTable(table = "sys_org", alias = "o", kind = DataScopeKind.NONE)
public class SysOrg {

    /** DDL 未提供 created_at/updated_at 的 getter 格式化常量时的兜底格式。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级节点；集团根节点为 NULL。 */
    private Long parentId;

    /** {@code group=集团 company=公司 dept=部门 section=科室}（见 {@link IdentityEnums.OrgType}）。 */
    private String orgType;

    private String name;

    /** 祖先路径 {@code /1/12/135/}，**含自身**（DDL 注释口径）。 */
    private String path;

    /** 层级：1集团 2公司 3部门 4科室。 */
    private Integer depth;

    /** 主负责人（冗余，权威数据在 {@code sys_org_leader}）。 */
    private Long leaderId;

    private Integer sortNo;

    /** {@code active=启用 disabled=停用}。 */
    private String status;

    /** 备注（导入模板 remark 列落此处）。 */
    private String remark;

    private LocalDateTime createdAt;

    private Long createdBy;

    private LocalDateTime updatedAt;

    private Long updatedBy;

    /** 基础数据逻辑删除（审批类数据一律不删除）。 */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

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

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    // ---- 便捷判定（口径集中在 {@link IdentityEnums}，此处只是语法糖） ----

    /** 类型枚举（未知 code 返回 {@code null}，避免历史脏数据抛异常）。 */
    public IdentityEnums.OrgType type() {
        return IdentityEnums.OrgType.ofCode(orgType);
    }

    /** 状态枚举（未知 code 返回 {@code null}）。 */
    public IdentityEnums.OrgStatus statusEnum() {
        return IdentityEnums.OrgStatus.ofCode(status);
    }

    /** 是否启用（{@code status = 'active'}）。 */
    public boolean isEnabled() {
        return IdentityEnums.OrgStatus.ACTIVE.code().equals(status);
    }

    /** 自身 id 的 path 段数（与 {@code depth} 应一致；只做纯字符串计数，不反向依赖上层规则类）。 */
    public int pathDepth() {
        if (path == null) {
            return 0;
        }
        int count = 0;
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                count++;
            }
        }
        return count;
    }
}
