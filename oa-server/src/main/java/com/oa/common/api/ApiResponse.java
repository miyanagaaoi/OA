package com.oa.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oa.common.error.ErrorCode;
import com.oa.common.web.TraceIds;
import java.io.Serializable;
import java.util.Map;

/**
 * 统一响应体：{@code {code, message, data, traceId, details?}}。
 *
 * <p>{@code code = 0} 表示成功，非 0 与 {@link ErrorCode} 一一对应；HTTP 状态码由
 * {@code GlobalExceptionHandler} 按错误码映射，业务体始终是本结构（便于前端统一处理）。
 *
 * <p><b>{@code details}（2026-10-04 追加，纯追加不改既有字段语义）</b>：
 * 承载**结构化**的错误明细，只在错误码显式声明了「可对外明细」时出现
 * （见 {@code BizException#withPublicDetail}）。默认 {@code null} 且标注
 * {@link JsonInclude.Include#NON_NULL}，因此既有响应（成功体、未声明明细的错误体）
 * 的 JSON 形状<b>一个字节都不变</b> —— 前端老的解析逻辑不受影响。
 *
 * <p>首个使用方：表单二次校验失败（40011）的 {@code details.errors[]}，
 * 与干跑接口 {@code POST /forms/{formType}/validate} 的 {@code report.issues[]} **同源同形**
 * （同一个 {@code FormValidationReport#issueViews()}），前端不再需要从
 * 「字段码（标签）：原因」的文本里尽力还原。
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码。 */
    public static final int SUCCESS_CODE = 0;

    private int code;
    private String message;
    private T data;
    private String traceId;

    /** 结构化明细（仅错误响应、且错误码显式声明时才出现；{@code null} 时字段整体省略）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> details;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data, String traceId) {
        this(code, message, data, traceId, null);
    }

    public ApiResponse(int code, String message, T data, String traceId, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.details = details == null || details.isEmpty() ? null : details;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "OK", data, TraceIds.current());
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceIds.current());
    }

    /** 带**可对外结构化明细**的失败响应（{@code details} 为 {@code null}/空时字段被省略）。 */
    public static <T> ApiResponse<T> failure(int code, String message, Map<String, Object> details) {
        return new ApiResponse<>(code, message, null, TraceIds.current(), details);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return failure(errorCode.getCode(), message);
    }

    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
