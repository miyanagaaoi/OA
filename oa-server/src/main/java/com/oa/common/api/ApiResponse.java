package com.oa.common.api;

import com.oa.common.error.ErrorCode;
import com.oa.common.web.TraceIds;
import java.io.Serializable;

/**
 * 统一响应体：{@code {code, message, data, traceId}}。
 *
 * <p>{@code code = 0} 表示成功，非 0 与 {@link ErrorCode} 一一对应；HTTP 状态码由
 * {@code GlobalExceptionHandler} 按错误码映射，业务体始终是本结构（便于前端统一处理）。
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

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
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
}
