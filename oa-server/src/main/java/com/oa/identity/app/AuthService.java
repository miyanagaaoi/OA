package com.oa.identity.app;

import com.oa.common.audit.AuditLogWriter;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.common.security.LoginAttemptGuard;
import com.oa.common.security.PasswordService;
import com.oa.common.security.SessionStore;
import com.oa.identity.api.dto.AuthDtos;
import com.oa.identity.domain.SysLoginLog;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysLoginLogMapper;
import com.oa.identity.infra.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务：登录 / 登出 / 当前登录人 / 修改口令（含首登强制改密标记）。
 *
 * <p>对齐契约：{@code POST /api/v1/auth/login}、{@code POST /api/v1/auth/logout}、
 * {@code GET /api/v1/auth/me}、{@code PUT /api/v1/auth/password}、
 * {@code GET /api/v1/auth/password-policy}、{@code GET /api/v1/auth/lock-status}。
 *
 * <p>安全口径：口令只做 BCrypt 比对；失败计数走 {@link LoginAttemptGuard}（5 次 / 15 分钟）；
 * 会话令牌只在返回值中交给 Controller 写 HttpOnly Cookie，**不落库、不进响应体**
 * （{@code sys_user_session.token_hash} 只存 SHA-256）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SessionStore sessionStore;
    private final PasswordService passwordService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final AuditLogWriter auditLogWriter;
    private final OaProperties properties;

    public AuthService(SysUserMapper userMapper, SysLoginLogMapper loginLogMapper, SessionStore sessionStore,
                       PasswordService passwordService, LoginAttemptGuard loginAttemptGuard,
                       AuditLogWriter auditLogWriter, OaProperties properties) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.sessionStore = sessionStore;
        this.passwordService = passwordService;
        this.loginAttemptGuard = loginAttemptGuard;
        this.auditLogWriter = auditLogWriter;
        this.properties = properties;
    }

    /**
     * 登录。
     *
     * @param request     登录请求
     * @param httpRequest 原始请求（取 IP / User-Agent）
     * @return 登录结果（含**仅本次返回**的原始令牌，由 Controller 写 Cookie）
     */
    public LoginOutcome login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        String account = normalizeAccount(request.account());
        String ip = clientIp(httpRequest);
        String userAgent = truncate(httpRequest == null ? null : httpRequest.getHeader("User-Agent"), 255);
        String fingerprint = truncate(request.deviceFingerprint(), 128);

        LoginAttemptGuard.LockState state = loginAttemptGuard.check(account);
        if (state.locked()) {
            writeLoginLog(null, account, SysLoginLog.RESULT_FAIL, SysLoginLog.FAIL_LOCKED, ip, userAgent, fingerprint);
            throw lockedException(state);
        }

        SysUser user = userMapper.selectByAccount(account);
        if (user == null) {
            LoginAttemptGuard.LockState after = loginAttemptGuard.recordFailure(account);
            writeLoginLog(null, account, SysLoginLog.RESULT_FAIL, SysLoginLog.FAIL_BAD_PASSWORD, ip, userAgent, fingerprint);
            throw badCredentialsOrLocked(after);
        }
        if (!user.isActive()) {
            writeLoginLog(user.getId(), account, SysLoginLog.RESULT_FAIL, SysLoginLog.FAIL_DISABLED, ip, userAgent, fingerprint);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            LoginAttemptGuard.LockState after = loginAttemptGuard.recordFailure(account);
            writeLoginLog(user.getId(), account, SysLoginLog.RESULT_FAIL, SysLoginLog.FAIL_BAD_PASSWORD, ip, userAgent, fingerprint);
            throw badCredentialsOrLocked(after);
        }

        loginAttemptGuard.reset(account);

        boolean mustChangePassword = user.mustChangePasswordOnFirstLogin();
        SessionStore.IssuedSession issued = sessionStore.create(
                user.getId(), fingerprint, ip, userAgent, request.rememberMe());

        SysUser touch = new SysUser();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touch);

        writeLoginLog(user.getId(), account, SysLoginLog.RESULT_SUCCESS, null, ip, userAgent, fingerprint);
        auditLogWriter.append(new AuditLogWriter.AuditRecord(user.getId(), user.getName(), "login", "user", user.getId(),
                null, "{\"result\":\"success\",\"sessionId\":\"" + issued.session().sessionId() + "\"}", ip, userAgent));

        List<Long> evicted = new ArrayList<>();
        for (SessionStore.SessionInfo info : issued.evicted()) {
            evicted.add(info.sessionId());
        }
        AuthDtos.LoginResponse response = new AuthDtos.LoginResponse(
                user.getId(), user.getAccount(), user.getName(), mustChangePassword,
                issued.session().expiresAt().toString(),
                properties.getSession().getMaxDevices(), evicted);
        return new LoginOutcome(issued.token(), issued.session().expiresAt(), response);
    }

    /** 登出：软撤销当前会话（保留设备历史）。 */
    public void logout(String rawToken, HttpServletRequest httpRequest) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessionStore.revoke(rawToken, com.oa.identity.domain.SysUserSession.REASON_LOGOUT);
    }

    /** 当前登录人（含角色与数据域，供前端菜单/按钮控制）。 */
    public AuthDtos.MeResponse me() {
        CurrentUser principal = currentPrincipal();
        Set<String> scopes = new LinkedHashSet<>();
        principal.dataScopes().forEach(scope -> scopes.add(scope.getCode()));
        return new AuthDtos.MeResponse(principal.id(), principal.account(), principal.name(), principal.employeeNo(),
                principal.orgId(), principal.companyId(), principal.roleCodes(), scopes, principal.mustChangePassword());
    }

    /**
     * 修改口令（首登强制改密走同一接口）。
     *
     * <p>成功后**撤销该用户全部会话**（含当前会话，{@code revoked_reason=password_changed}），
     * 强制重新登录 —— 这是「改密即失效」的会话安全口径。
     */
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        CurrentUser principal = currentPrincipal();
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BizException(ErrorCode.PASSWORD_MISMATCH);
        }
        passwordService.assertStrong(request.newPassword());

        SysUser user = userMapper.selectById(principal.id());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordService.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "原口令不正确");
        }
        if (passwordService.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordService.encode(request.newPassword()));
        userMapper.updateById(update);

        int revoked = sessionStore.revokeAll(user.getId(), com.oa.identity.domain.SysUserSession.REASON_PASSWORD_CHANGED);
        auditLogWriter.append(new AuditLogWriter.AuditRecord(user.getId(), user.getName(), "update", "user", user.getId(),
                null, "{\"field\":\"password_hash\",\"revokedSessions\":" + revoked + "}", null, null));
        log.info("用户 {} 修改口令成功，已撤销 {} 个会话", user.getId(), revoked);
    }

    public AuthDtos.PasswordPolicyResponse passwordPolicy() {
        OaProperties.Security security = properties.getSecurity();
        OaProperties.Session session = properties.getSession();
        return new AuthDtos.PasswordPolicyResponse(
                Math.max(8, security.getPasswordMinLength()), true, true,
                security.getLoginMaxFailures(), security.getLoginLockMinutes(),
                session.getRememberMeDays(), session.getMaxDevices());
    }

    public AuthDtos.LockStatusResponse lockStatus(String account) {
        String normalized = normalizeAccount(account);
        LoginAttemptGuard.LockState state = loginAttemptGuard.check(normalized);
        return new AuthDtos.LockStatusResponse(normalized, state.locked(), state.failures(), state.remainingMinutes());
    }

    /** 管理员解锁（留痕由 Controller 的 {@code @Audited} 负责）。 */
    public void unlock(String account) {
        CurrentUser principal = currentPrincipal();
        if (!principal.hasRole("admin")) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅系统管理员可解锁账号");
        }
        loginAttemptGuard.reset(normalizeAccount(account));
        auditLogWriter.append(new AuditLogWriter.AuditRecord(principal.id(), principal.name(), "update", "user", null,
                null, "{\"action\":\"unlock\",\"account\":\"" + normalizeAccount(account) + "\"}", null, null));
    }

    /** 在线设备列表（本人或管理员查看本人；跨人查看留给管理后台）。 */
    public List<AuthDtos.SessionView> listSessions() {
        CurrentUser principal = currentPrincipal();
        List<AuthDtos.SessionView> views = new ArrayList<>();
        for (SessionStore.SessionInfo info : sessionStore.listActive(principal.id())) {
            views.add(new AuthDtos.SessionView(info.sessionId(), info.ip(), info.userAgent(), info.deviceFingerprint(),
                    String.valueOf(info.loginAt()), String.valueOf(info.lastActiveAt()), String.valueOf(info.expiresAt())));
        }
        return views;
    }

    /** 远程注销指定设备会话（仅限本人会话）。 */
    public void revokeSession(Long sessionId) {
        CurrentUser principal = currentPrincipal();
        sessionStore.revokeById(sessionId, principal.id(), com.oa.identity.domain.SysUserSession.REASON_LOGOUT);
    }

    // ------------------------------------------------------------------ 内部

    private CurrentUser currentPrincipal() {
        DataScopeContext context = DataScopeContext.require();
        CurrentUser principal = context.getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    private BizException lockedException(LoginAttemptGuard.LockState state) {
        return new BizException(ErrorCode.ACCOUNT_LOCKED,
                String.format(ErrorCode.ACCOUNT_LOCKED.getMessage(), state.remainingMinutes()));
    }

    private BizException badCredentialsOrLocked(LoginAttemptGuard.LockState state) {
        return state.locked() ? lockedException(state) : new BizException(ErrorCode.BAD_CREDENTIALS);
    }

    private void writeLoginLog(Long userId, String account, String result, String failReason,
                               String ip, String userAgent, String fingerprint) {
        SysLoginLog row = new SysLoginLog();
        row.setUserId(userId);
        row.setAccount(truncate(account, 64));
        row.setResult(result);
        row.setFailReason(failReason);
        row.setIp(ip);
        row.setUserAgent(userAgent);
        row.setDeviceFingerprint(fingerprint);
        row.setCreatedAt(LocalDateTime.now());
        try {
            loginLogMapper.insertAppendOnly(row);
        } catch (RuntimeException ex) {
            log.error("登录日志写入失败（不影响登录）：account={} result={}", account, result, ex);
        }
    }

    private static String normalizeAccount(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 登录结果。
     *
     * @param token     原始会话令牌（**只在此处返回一次**，用于写 HttpOnly Cookie）
     * @param expiresAt 过期时间
     * @param response  响应体
     */
    public record LoginOutcome(String token, LocalDateTime expiresAt, AuthDtos.LoginResponse response) {
    }
}
