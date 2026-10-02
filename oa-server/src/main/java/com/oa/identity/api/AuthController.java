package com.oa.identity.api;

import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.config.OaProperties;
import com.oa.common.security.Anonymous;
import com.oa.identity.api.dto.AuthDtos;
import com.oa.identity.app.AuthService;
import com.oa.identity.app.ClientConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（{@code /api/v1/auth/**}），路径与 normify {@code oa.identity.session.*} 契约一致：
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} 登录并签发会话（写 HttpOnly Cookie）</li>
 *   <li>{@code POST /api/v1/auth/logout} 登出（软撤销会话）</li>
 *   <li>{@code GET  /api/v1/auth/me} 当前登录人 + 角色 + 数据域</li>
 *   <li>{@code PUT  /api/v1/auth/password} 修改口令（首登强制改密走同一接口）</li>
 *   <li>{@code GET  /api/v1/auth/password-policy} 口令策略（前端前置提示）</li>
 *   <li>{@code GET  /api/v1/auth/client-config} 客户端运行期配置（非敏感；登录前可取）</li>
 *   <li>{@code GET  /api/v1/auth/lock-status} 锁定状态</li>
 *   <li>{@code POST /api/v1/auth/unlock} 管理员解锁（留痕）</li>
 *   <li>{@code GET  /api/v1/auth/sessions}、{@code DELETE /api/v1/auth/sessions/{id}} 在线设备</li>
 * </ul>
 *
 * <p>Cookie 口径（doc/tech-design.md §6）：{@code HttpOnly + Secure + SameSite=Lax}；
 * 「记住我」7 天，未勾选为会话级 Cookie（关浏览器即失效）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientConfigService clientConfigService;
    private final OaProperties properties;

    public AuthController(AuthService authService, ClientConfigService clientConfigService,
                          OaProperties properties) {
        this.authService = authService;
        this.clientConfigService = clientConfigService;
        this.properties = properties;
    }

    @PostMapping("/login")
    @Anonymous
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        AuthService.LoginOutcome outcome = authService.login(request, httpRequest);
        writeSessionCookie(httpResponse, outcome.token(), outcome.expiresAt(), request.rememberMe());
        return ApiResponse.success(outcome.response());
    }

    @PostMapping("/logout")
    @Audited(action = "logout", targetType = "session", recordArgs = true)
    public ApiResponse<AuthDtos.MessageResponse> logout(
            @CookieValue(name = "${oa.session.cookie-name:OA_SESSION}", required = false) String token,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.logout(token, httpRequest);
        clearSessionCookie(httpResponse);
        return ApiResponse.success(new AuthDtos.MessageResponse("已退出登录"));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.MeResponse> me() {
        return ApiResponse.success(authService.me());
    }

    @PutMapping("/password")
    @Audited(action = "update", targetType = "user")
    public ApiResponse<AuthDtos.MessageResponse> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,
                                                                HttpServletResponse httpResponse) {
        authService.changePassword(request);
        clearSessionCookie(httpResponse);
        return ApiResponse.success(new AuthDtos.MessageResponse("口令已修改，请重新登录"));
    }

    @GetMapping("/password-policy")
    @Anonymous
    public ApiResponse<AuthDtos.PasswordPolicyResponse> passwordPolicy() {
        return ApiResponse.success(authService.passwordPolicy());
    }

    /**
     * {@code GET /api/v1/auth/client-config} —— 前端启动所需**非敏感**运行期配置。
     *
     * <p>{@link Anonymous}：前端在**登录页/登录后布局挂载时**都要取它（标题、API 前缀、
     * Cookie 名、口令提示、会话上限、水印透明度），必须登录前可用。
     * 放行方式刻意用 {@code @Anonymous} 注解而**不是** {@code oa.web.permit-all} 路径白名单：
     * 白名单条目是「拦截器级」的整体放行（任何方法/将来新增的方法都自动免认证），
     * 而注解只对**这一个方法**生效，安全边界更小、可读性更强。因此
     * {@code oa.web.permit-all} **未新增任何条目**。
     *
     * <p>响应内容与「禁止泄露密钥/口令/数据库信息」的边界见
     * {@link AuthDtos.ClientConfigResponse} 与 {@link ClientConfigService} 的类注释。
     */
    @GetMapping("/client-config")
    @Anonymous
    public ApiResponse<AuthDtos.ClientConfigResponse> clientConfig() {
        return ApiResponse.success(clientConfigService.current());
    }

    @GetMapping("/lock-status")
    @Anonymous
    public ApiResponse<AuthDtos.LockStatusResponse> lockStatus(@RequestParam("account") String account) {
        return ApiResponse.success(authService.lockStatus(account));
    }

    @PostMapping("/unlock")
    @Audited(action = "update", targetType = "user", recordArgs = true)
    public ApiResponse<AuthDtos.MessageResponse> unlock(@RequestParam("account") String account) {
        authService.unlock(account);
        return ApiResponse.success(new AuthDtos.MessageResponse("账号已解锁"));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AuthDtos.SessionView>> sessions() {
        return ApiResponse.success(authService.listSessions());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Audited(action = "delete", targetType = "session", targetId = "#sessionId")
    public ApiResponse<AuthDtos.MessageResponse> revokeSession(@PathVariable("sessionId") Long sessionId) {
        authService.revokeSession(sessionId);
        return ApiResponse.success(new AuthDtos.MessageResponse("该设备会话已注销"));
    }

    // ------------------------------------------------------------------ Cookie

    private void writeSessionCookie(HttpServletResponse response, String token, LocalDateTime expiresAt, boolean rememberMe) {
        OaProperties.Session session = properties.getSession();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(session.getCookieName(), token)
                .httpOnly(true)
                .secure(session.isCookieSecure())
                .sameSite(session.getCookieSameSite())
                .path(session.getCookiePath());
        if (rememberMe && expiresAt != null) {
            builder.maxAge(Duration.between(LocalDateTime.now(), expiresAt));
        } else {
            // 会话级 Cookie：不设 Max-Age/Expires，浏览器关闭即失效
            builder.maxAge(-1);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        OaProperties.Session session = properties.getSession();
        ResponseCookie cookie = ResponseCookie.from(session.getCookieName(), "")
                .httpOnly(true)
                .secure(session.isCookieSecure())
                .sameSite(session.getCookieSameSite())
                .path(session.getCookiePath())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
