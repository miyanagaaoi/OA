package com.oa.common.web;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * 链路追踪号：从日志上下文（MDC）读取，用于把响应体、日志与审计记录关联起来。
 *
 * <p>写入方见 {@link TraceIdFilter}；读不到时返回 {@code null}，绝不抛异常。
 */
public final class TraceIds {

    /** MDC 键名，同时作为响应头 {@code X-Trace-Id} 使用。 */
    public static final String MDC_KEY = "traceId";

    /** HTTP 响应头名。 */
    public static final String HEADER = "X-Trace-Id";

    private TraceIds() {
    }

    /** 当前请求的追踪号，可能为 {@code null}。 */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** 生成一个新的追踪号（无中划线小写十六进制）。 */
    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
