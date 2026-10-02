package com.oa.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oa.common.scope.DataScopeKind;
import com.oa.common.scope.DataScopeTable;
import java.time.LocalDateTime;

/**
 * 用户（{@code sys_user}）—— 列名严格取自 doc/data-model.md §2.2 的 DDL，禁止臆造。
 *
 * <p>安全约定：{@code phone} 加密存储、展示按角色脱敏（{@link #maskedPhone()}）；
 * {@code password_hash} 只允许通过 {@code PasswordService} 读写，永不进响应体。
 *
 * <p><b>本实体不再经 BaseMapper 读写</b>：{@code sys_user} 是受控表，其 Mapper
 * （{@code com.oa.identity.infra.SysUserMapper}）**不继承 {@code BaseMapper}**，
 * 读写一律走该接口在 XML 中显式声明的语句（写入含 {@code useGeneratedKeys} 回填 id）。
 * 下面的 {@code @TableName}/{@code @TableId}/{@code @TableLogic} 注解**保留**：
 * 它们仍表达列名/主键/逻辑删除语义，且对 XML 中的 {@code useGeneratedKeys} 无影响。
 */
@TableName("sys_user")
@DataScopeTable(table = "sys_user", alias = "u", kind = DataScopeKind.USER)
public class SysUser {

    /** active=在职 disabled=停用 resigned=离职 */
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String STATUS_RESIGNED = "resigned";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，唯一。 */
    private String account;

    private String name;

    /** 工号（水印使用）。 */
    private String employeeNo;

    /** BCrypt 哈希，禁止明文。 */
    private String passwordHash;

    /** 加密存储（AES-256-GCM，V1 骨架尚未接入密钥轮换）。 */
    private String phone;

    private String email;

    /** 主归属组织节点。 */
    private Long orgId;

    /** 归属公司（数据域判定用）。 */
    private Long companyId;

    private String position;

    private String status;

    private String remark;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private Long createdBy;

    private LocalDateTime updatedAt;

    private Long updatedBy;

    /** 基础数据逻辑删除（审批类数据一律不删除）。 */
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

    /** 手机号脱敏：{@code 138****8888}（PRD §5.3 字段级限制）。 */
    public String maskedPhone() {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /**
     * 是否必须强制改密。
     *
     * <p>口径说明：{@code sys_user} DDL 中**没有** {@code must_change_password} 列，
     * 因此骨架以「从未登录过（{@code last_login_at IS NULL}）」作为首登判定；
     * 生产口径需要 data-model 增加一列（或独立的强制改密标记表），已在交付说明中列出该差异。
     */
    public boolean mustChangePasswordOnFirstLogin() {
        return lastLoginAt == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
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

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
}
