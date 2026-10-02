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

    /**
     * 客户端运行期配置（{@code GET /api/v1/auth/client-config}）——
     * normify 模块 {@code oa.identity.session.client}。
     *
     * <p><b>安全边界</b>：本响应只含**非敏感**的运行期参数（标题、环境名、相对 API 前缀、
     * Cookie 名、会话/口令阈值、水印透明度）。**严禁**把数据源 URL/账号、Redis 地址、
     * 装配口令（{@code oa.db.*}、{@code spring.datasource.*}、{@code spring.data.redis.*}）、
     * {@code bcryptStrength}、任何密钥或令牌写进本结构 —— 该接口在登录前即可访问
     * （{@link com.oa.common.security.Anonymous}）。字段名与前端
     * {@code oa-web/src/types/api.d.ts} 的 {@code ClientConfig} / {@code oa-web/src/api/auth.ts}
     * 的 {@code demoClientConfig} 对齐。
     *
     * @param title             系统标题（{@code oa.web.title}）
     * @param env               运行环境（激活 profile，如 {@code dev} / {@code prod}）
     * @param apiBaseUrl        前端统一 API 前缀（相对路径，如 {@code /api/v1}）
     * @param sessionCookieName 会话 Cookie 名（HttpOnly；前端只读其存在性）
     * @param forceHttps        是否强制 HTTPS（取 {@code oa.session.cookie-secure}；dev=false / prod=true）
     * @param watermarkOpacity  水印透明度（已夹到 5%–8%）
     * @param session           会话上限（多设备上限、「记住我」天数）
     * @param password          口令策略（长度、字符要求、失败锁定阈值与时长）
     */
    public record ClientConfigResponse(
            String title,
            String env,
            String apiBaseUrl,
            String sessionCookieName,
            boolean forceHttps,
            double watermarkOpacity,
            SessionLimits session,
            PasswordLimits password
    ) {
    }

    /**
     * 会话上限。
     *
     * <p>两个字段都用 {@code int}（不是 {@code long}）：本工程的
     * {@code JacksonConfig} 会把 {@code Long}/{@code long}/{@code BigInteger} 序列化成**字符串**
     * （防 JS 53 位精度丢失）。会话天数与设备数是小整数，没有精度风险，因此刻意用 {@code int}
     * 让它们在 JSON 里保持 number —— 前端 {@code ClientConfig.maxDevices / rememberMeDays}
     * 就是 {@code number}，不必再脱一层字符串。
     *
     * @param maxDevices     同时在线设备上限（REQ-USER-003，默认 3）
     * @param rememberMeDays 「记住我」有效期（天，REQ-USER-002，默认 7）
     */
    public record SessionLimits(int maxDevices, int rememberMeDays) {
    }

    /**
     * 口令策略（登录前提示用；与 {@code GET /auth/password-policy} 同源、不另立口径）。
     *
     * @param minLength      最小长度（≥8）
     * @param requireLetter  必须含字母
     * @param requireDigit   必须含数字
     * @param lockThreshold  连续失败锁定阈值（REQ-NFR-005，默认 5）
     * @param lockMinutes    锁定时长（分钟，默认 15）
     */
    public record PasswordLimits(
            int minLength,
            boolean requireLetter,
            boolean requireDigit,
            int lockThreshold,
            int lockMinutes
    ) {
    }
}
