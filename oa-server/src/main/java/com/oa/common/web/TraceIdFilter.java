package com.oa.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 追踪号过滤器：为每个请求生成/透传 {@code X-Trace-Id}，写入 MDC 与响应头。
 *
 * <p>用途：响应体 {@code traceId} 字段、服务端日志、审计记录三者可用同一个号码串联，
 * 支撑 AC-41「错误信息不泄露内部细节」——对外只给追踪号，取证靠日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TraceIds.HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            traceId = TraceIds.next();
        }
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
