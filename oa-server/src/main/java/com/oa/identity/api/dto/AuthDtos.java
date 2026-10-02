package com.oa.identity.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

/**
 * 认证相关 DTO（与 normify {@code oa.identity.session.*} 的 API 契约一一对应）。
 *
 * <p>安全约定：**会话令牌不出现在响应体**，只通过 HttpOnly Cookie 下发（防 XSS 窃取）。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * 登录请求（{@code POST /api/v1/auth/login}）。
     *
     * @param account             登录账号
     * @param password            口令明文（仅本次请求内存中；不落库、不写日志）
     * @param rememberMe          「记住我」7 天（REQ-USER-002）
     * @param deviceFingerprint   设备指纹（可选，与签名记录同源）
     */
    public record LoginRequest(
            @NotBlank(message = "账号不能为空") String account,
            @NotBlank(message = "口令不能为空") String password,
            boolean rememberMe,
            String deviceFingerprint
    ) {
    }

    /**
     * 登录结果（不含令牌）。
     *
     * @param userId             用户 id
     * @param account            账号
     * @param name               姓名
     * @param mustChangePassword 是否必须修改口令（首登强制改密）
     * @param expiresAt          会话过期时间
     * @param maxDevices         当前在线设备上限
     * @param evictedSessionIds  因超上限被踢出的会话 id（最早登录者）
     */
    public record LoginResponse(
            Long userId,
            String account,
            String name,
            boolean mustChangePassword,
            String expiresAt,
            int maxDevices,
            List<Long> evictedSessionIds
    ) {
    }

    /**
     * 当前登录人（{@code GET /api/v1/auth/me}）。
     *
     * @param id                 用户 id
     * @param account            账号
     * @param name               姓名
     * @param employeeNo         工号
     * @param orgId              主归属组织节点
     * @param companyId          归属公司
     * @param roleCodes          角色编码
     * @param dataScopes         数据域取值集合
     * @param permissions        有效权限码（该用户全部角色权限的并集，字典序）
     *                           —— 前端管理入口可见性的**唯一真实来源**，不再用角色码兜底
     * @param isSuperAdmin       是否拥有 {@code admin} 角色（系统管理员兜底权限，REQ-ADMIN-006）
     * @param mustChangePassword 是否必须修改口令
     */
    public record MeResponse(
            Long id,
            String account,
            String name,
            String employeeNo,
            Long orgId,
            Long companyId,
            Set<String> roleCodes,
            Set<String> dataScopes,
            List<String> permissions,
            @JsonProperty("isSuperAdmin") boolean isSuperAdmin,
            boolean mustChangePassword
    ) {
    }

    /** 修改口令请求（{@code PUT /api/v1/auth/password}）。 */
    public record ChangePasswordRequest(
            @NotBlank(message = "原口令不能为空") String oldPassword,
            @NotBlank(message = "新口令不能为空") @Size(min = 8, max = 64, message = "新口令长度需在 8~64 位") String newPassword,
            @NotBlank(message = "确认口令不能为空") String confirmPassword
    ) {
    }

    /**
     * 口令策略（{@code GET /api/v1/auth/password-policy}），供前端做前置提示。
     *
     * @param minLength     最小长度
     * @param requireLetter 必须含字母
     * @param requireDigit  必须含数字
     * @param maxFailures   失败锁定阈值
     * @param lockMinutes   锁定时长（分钟）
     * @param rememberMeDays 「记住我」有效期（天）
     * @param maxDevices    同时在线设备上限
     */
    public record PasswordPolicyResponse(
            int minLength,
            boolean requireLetter,
            boolean requireDigit,
            int maxFailures,
            int lockMinutes,
            long rememberMeDays,
            int maxDevices
    ) {
    }

    /**
     * 锁定状态（{@code GET /api/v1/auth/lock-status}）。
     *
     * @param account          账号
     * @param locked           是否锁定
     * @param failures         窗口内失败次数
     * @param remainingMinutes 剩余锁定分钟数
     */
    public record LockStatusResponse(
            String account,
            boolean locked,
            int failures,
            long remainingMinutes
    ) {
    }

    /** 在线设备条目（{@code GET /api/v1/auth/sessions}）。 */
    public record SessionView(
            Long sessionId,
            String ip,
            String userAgent,
            String deviceFingerprint,
            String loginAt,
            String lastActiveAt,
            String expiresAt
    ) {
    }

    /** 通用提示。 */
    public record MessageResponse(String message) {
    }
}
