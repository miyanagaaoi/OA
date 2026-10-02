package com.oa.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 用户会话与设备（{@code sys_user_session}）—— 列名取自 doc/data-model.md §2.7 的 DDL。
 *
 * <p>多设备上限默认 3，超出对 {@code login_at} 最早的有效会话写 {@code revoked_at}
 * （**软踢出，不物理删除**，REQ-USER-003 / REQ-NFR-006）。
 * {@code token_hash} 只存 SHA-256 十六进制，禁止存明文令牌。
 */
@TableName("sys_user_session")
public class SysUserSession {

    /** 失效原因值域（与 DDL 的 CHECK 约束一致）。 */
    public static final String REASON_LOGOUT = "logout";
    public static final String REASON_KICKED = "kicked";
    public static final String REASON_EXPIRED = "expired";
    public static final String REASON_PASSWORD_CHANGED = "password_changed";
    public static final String REASON_DISABLED = "disabled";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String deviceFingerprint;

    private String ip;

    private String userAgent;

    /** 会话令牌哈希（只存哈希）。 */
    private String tokenHash;

    private LocalDateTime loginAt;

    private LocalDateTime lastActiveAt;

    private LocalDateTime expiresAt;

    /** NULL=有效。 */
    private LocalDateTime revokedAt;

    private String revokedReason;

    private LocalDateTime createdAt;

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }

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

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(LocalDateTime loginAt) {
        this.loginAt = loginAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
