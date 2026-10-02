package com.oa.identity.domain;

import java.time.LocalDateTime;

/**
 * 登录日志（{@code sys_login_log}）—— 列名取自 doc/data-model.md §2.6 的 DDL，保留 1 年（REQ-LOG-005）。
 *
 * <p>只追加：仅通过 {@code SysLoginLogMapper.insertAppendOnly} 写入。
 */
public class SysLoginLog {

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAIL = "fail";

    /** 失败原因值域（与 DDL 注释一致）。 */
    public static final String FAIL_BAD_PASSWORD = "bad_password";
    public static final String FAIL_LOCKED = "locked";
    public static final String FAIL_DISABLED = "disabled";
    public static final String FAIL_BAD_REQUEST = "bad_request";

    private Long id;

    /** 失败时也记录尝试的账号。 */
    private Long userId;

    private String account;

    private String result;

    private String failReason;

    private String ip;

    private String userAgent;

    private String deviceFingerprint;

    private LocalDateTime createdAt;

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

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
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

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
