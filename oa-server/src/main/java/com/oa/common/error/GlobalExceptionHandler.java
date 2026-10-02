package com.oa.common.error;

import com.oa.common.api.ApiResponse;
import com.oa.common.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器。
 *
 * <p>安全基线 AC-41「错误信息不泄露」的落点：对外只输出 {@link ErrorCode} 的文案与追踪号，
 * 堆栈、SQL、内部类名只写服务端日志（含 traceId，便于按追踪号取证）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (ex.isClientError()) {
            log.warn("业务异常 code={} {} {} traceId={} detail={}",
                    code.getCode(), request.getMethod(), request.getRequestURI(), TraceIds.current(), ex.getDetails());
        } else {
            log.error("业务异常(服务端) code={} {} {} traceId={}",
                    code.getCode(), request.getMethod(), request.getRequestURI(), TraceIds.current(), ex);
        }
        return build(code, ex.getMessage());
    }

    /** {@code @Valid} 请求体校验失败（MethodArgumentNotValidException 继承自 BindException）。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        List<String> messages = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            messages.add(error.getField() + ": " + error.getDefaultMessage());
        }
        String detail = messages.isEmpty() ? ErrorCode.PARAM_INVALID.getMessage() : String.join("; ", messages);
        log.warn("参数校验失败 traceId={} detail={}", TraceIds.current(), detail);
        return build(ErrorCode.PARAM_INVALID, detail);
    }

    /** {@code @Validated} 方法参数（含路径/查询参数）校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 traceId={} detail={}", TraceIds.current(), detail);
        return build(ErrorCode.PARAM_INVALID, detail.isEmpty() ? ErrorCode.PARAM_INVALID.getMessage() : detail);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.warn("请求不合法 traceId={} type={} message={}", TraceIds.current(), ex.getClass().getSimpleName(), ex.getMessage());
        return build(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    /** 数据库访问异常：唯一键/乐观锁等可归因，其余一律泛化。 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        log.error("数据库访问异常 traceId={}", TraceIds.current(), ex);
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        if (message.contains("duplicate")) {
            return build(ErrorCode.DUPLICATE, ErrorCode.DUPLICATE.getMessage());
        }
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("未捕获异常 traceId={}", TraceIds.current(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message) {
        ApiResponse<Void> body = ApiResponse.failure(code.getCode(), message);
        HttpStatus status = HttpStatus.resolve(code.getHttpStatus());
        return ResponseEntity.status(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status).body(body);
    }
}
