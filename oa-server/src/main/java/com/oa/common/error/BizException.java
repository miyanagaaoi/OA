package com.oa.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常：只承载**可对外**的错误码与文案，内部细节一律走日志。
 *
 * <p>{@link #getDetails()} 仅用于服务端日志与排查，**不写入 HTTP 响应**（AC-41）。
 *
 * <p><b>两个 details 的区别（2026-10-04 补记，务必分清）</b>：
 * <ul>
 *   <li>{@link #withDetail}：**排查用**上下文（如 {@code requiredPermissions}、
 *       {@code unknownFields}）—— 只进服务端日志。AC-41「错误信息不泄露」的落点，
 *       {@code FlowWithdrawCcGateTest} 与 {@code FlowAutoScopeTest} 一族用例把它锁死了；</li>
 *   <li>{@link #withPublicDetail}：**可对外**的结构化明细 —— 会出现在 HTTP 响应体的
 *       {@code details} 里（{@link com.oa.common.api.ApiResponse}）。只允许放
 *       「字段码 / 标签 / 规则名 / 已可读文案」这类前端展示必需、且不含内部实现的信息，
 *       因此它是**逐键显式声明**的（默认空 Map → 响应体里连 {@code details} 字段都不出现）。</li>
 * </ul>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;

    /** 可对外的结构化明细（进 HTTP 响应体；默认空 = 不出现该字段）。 */
    private final transient Map<String, Object> publicDetails = new LinkedHashMap<>();

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
        // 必须拷贝为**可变** Map：{@link #withDetail} 会在构造后追加排查上下文。
        // （原实现直接持有 Collections.emptyMap()，导致 withDetail 抛 UnsupportedOperationException。）
        this.details = details == null || details.isEmpty()
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(details);
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

    /**
     * 追加**可对外**的结构化明细（进 HTTP 响应体的 {@code details}）。
     *
     * <p>与 {@link #withDetail} 的区别见类注释：本方法的内容**会被前端看到**，
     * 因此只允许放字段码 / 标签 / 规则名 / 已可读文案（例如 40011 的
     * {@code details.errors[] = [{field,label,rule,message}]}）。
     *
     * @throws IllegalArgumentException key 为空（防止「匿名明细」污染响应结构）
     */
    public BizException withPublicDetail(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("publicDetail 的 key 不能为空");
        }
        this.publicDetails.put(key, value);
        return this;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    /** 可对外的结构化明细（{@code null} 表示无 —— 响应体里省略整个 {@code details} 字段）。 */
    public Map<String, Object> getPublicDetails() {
        return publicDetails.isEmpty() ? null : Collections.unmodifiableMap(publicDetails);
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
