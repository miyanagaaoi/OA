package com.oa.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常：只承载**可对外**的错误码与文案，内部细节一律走日志。
 *
 * <p>{@link #getDetails()} 仅用于服务端日志与排查，不写入 HTTP 响应（AC-41）。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null, Collections.emptyMap());
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, Collections.emptyMap());
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, Collections.emptyMap());
    }

    private BizException(ErrorCode errorCode, String message, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.errorCode = errorCode == null ? ErrorCode.INTERNAL_ERROR : errorCode;
        this.details = details == null ? Collections.emptyMap() : details;
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    /** 带格式化文案（{@link ErrorCode#getMessage()} 中可含 {@code %s} / {@code %d} 占位符）。 */
    public static BizException of(ErrorCode errorCode, String message, Object... args) {
        return new BizException(errorCode, args == null || args.length == 0 ? message : String.format(message, args));
    }

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException unauthorized() {
        return new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static BizException forbidden() {
        return new BizException(ErrorCode.FORBIDDEN);
    }

    public static BizException dataScopeDenied() {
        return new BizException(ErrorCode.DATA_SCOPE_DENIED);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    /** 追加排查用上下文（不进 HTTP 响应）。 */
    public BizException withDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    /** 是否为客户端错误（4xx），日志可用 WARN 级别。 */
    public boolean isClientError() {
        return errorCode.getHttpStatus() >= 400 && errorCode.getHttpStatus() < 500;
    }

    /** 便于测试断言的静态构造（不可变细节）。 */
    public static BizException withDetails(ErrorCode errorCode, String message, Map<String, Object> details) {
        return new BizException(errorCode, message, null, new LinkedHashMap<>(details));
    }
}
