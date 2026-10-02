package com.oa.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.api.ApiResponse;
import com.oa.common.config.OaProperties;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：校验会话 Cookie → 装载 {@link DataScopeContext}（doc/tech-design.md §5.3 第 1 步）。
 *
 * <p>职责边界：
 * <ol>
 *   <li>取 Cookie（{@code OA_SESSION}）→ {@link SessionStore#find}；无/失效 → 401（不区分「未登录」与「已过期」的对外文案外泄）；</li>
 *   <li>续期（{@link SessionStore#touch}）；</li>
 *   <li>装载数据域上下文（{@link DataScopeProvider}）；</li>
 *   <li>首登强制改密闸门：未改密前除「查本人 / 改密 / 登出」外一律 401 {@link ErrorCode#PASSWORD_CHANGE_REQUIRED}；</li>
 *   <li>请求结束**必须**清理 ThreadLocal（{@link #afterCompletion}），防止线程池串号越权。</li>
 * </ol>
 *
 * <p>放行规则：{@link Anonymous} 注解（方法或类）优先；路径白名单由
 * {@code WebMvcConfig} 的 {@code excludePathPatterns} 处理。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final SessionStore sessionStore;
    private final DataScopeProvider dataScopeProvider;
    private final OaProperties properties;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(SessionStore sessionStore, DataScopeProvider dataScopeProvider,
                           OaProperties properties, ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.dataScopeProvider = dataScopeProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isAnonymous(handlerMethod)) {
            return true;
        }

        String token = readToken(request);
        if (token == null) {
            return reject(response, ErrorCode.UNAUTHORIZED);
        }
        Optional<SessionStore.SessionInfo> session = sessionStore.find(token);
        if (session.isEmpty()) {
            return reject(response, ErrorCode.SESSION_EXPIRED);
        }

        sessionStore.touch(token);

        DataScopeContext context;
        try {
            context = dataScopeProvider.resolve(session.get().userId());
        } catch (RuntimeException ex) {
            log.warn("装载数据域上下文失败 userId={}：{}", session.get().userId(), ex.getMessage());
            return reject(response, ErrorCode.UNAUTHORIZED);
        }
        DataScopeContext.set(context);

        if (context.getPrincipal() != null && context.getPrincipal().mustChangePassword()
                && !isPasswordChangeAllowedPath(request.getRequestURI())) {
            return reject(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataScopeContext.clear();
    }

    private boolean isAnonymous(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(Anonymous.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Anonymous.class);
    }

    /** 首登强制改密期间仍可访问的接口。 */
    private boolean isPasswordChangeAllowedPath(String uri) {
        return uri == null
                || uri.startsWith("/api/v1/auth/password")
                || uri.startsWith("/api/v1/auth/logout")
                || uri.startsWith("/api/v1/auth/me")
                || uri.startsWith("/actuator");
    }

    private String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = properties.getSession().getCookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean reject(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.failure(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
