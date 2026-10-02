package com.oa.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面：把 {@link Audited} 标注的方法调用写成 {@code sys_log} 记录（只追加）。
 *
 * <p>记录内容：操作人（当前登录人快照）、动作、目标、变更前/后 JSON、客户端 IP、User-Agent。
 * 敏感键（口令、手机号、收款账号、令牌）一律以 {@code ***} 落库，**明文永不出现在审计日志**。
 *
 * <p>失败隔离：业务方法抛异常时，先写一条带错误摘要的审计记录再原样抛出；审计写入本身的异常
 * 由 {@link AuditLogWriter} 吞掉并记 ERROR，绝不影响业务流程。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    /** 命中即脱敏的键名（小写、去下划线比较）。 */
    private static final Set<String> MASKED_KEYS = new LinkedHashSet<>(Arrays.asList(
            "password", "newpassword", "oldpassword", "confirmpassword", "passwordhash",
            "phone", "mobile", "payeeaccount", "token", "tokenhash", "idcard", "secrethash"
    ));

    private static final int MAX_JSON_LENGTH = 4000;

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public AuditAspect(AuditLogWriter auditLogWriter, ObjectMapper objectMapper) {
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String argsJson = (audited.recordBefore() || audited.recordArgs()) ? toJson(argumentMap(joinPoint)) : null;
        String beforeJson = audited.recordBefore() ? argsJson : (audited.recordArgs() ? argsJson : null);
        Long targetId = resolveTargetId(audited.targetId(), joinPoint);
        try {
            Object result = joinPoint.proceed();
            write(audited, targetId, beforeJson, audited.recordAfter() ? toJson(result) : null);
            return result;
        } catch (Throwable ex) {
            write(audited, targetId, beforeJson, errorJson(ex));
            throw ex;
        }
    }

    private void write(Audited audited, Long targetId, String beforeJson, String afterJson) {
        try {
            HttpServletRequest request = currentRequest();
            DataScopeContext context = DataScopeContext.current();
            CurrentUser principal = context == null ? null : context.getPrincipal();
            auditLogWriter.append(new AuditLogWriter.AuditRecord(
                    principal == null ? null : principal.id(),
                    principal == null ? null : principal.name(),
                    audited.action(),
                    audited.targetType(),
                    targetId,
                    beforeJson,
                    afterJson,
                    clientIp(request),
                    request == null ? null : truncate(request.getHeader("User-Agent"), 255)));
        } catch (RuntimeException ex) {
            log.error("审计切面写入失败（不影响业务）：action={} targetType={}", audited.action(), audited.targetType(), ex);
        }
    }

    private Map<String, Object> argumentMap(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = (names != null && i < names.length && names[i] != null) ? names[i] : ("arg" + i);
            map.put(name, sanitize(args[i]));
        }
        return map;
    }

    private Object sanitize(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Enum) {
            return value;
        }
        if (value instanceof HttpServletRequest) {
            return "HttpServletRequest";
        }
        try {
            Map<?, ?> raw = objectMapper.convertValue(value, Map.class);
            return mask(raw);
        } catch (RuntimeException ex) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> mask(Map<?, ?> raw) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (isMaskedKey(key)) {
                masked.put(key, "***");
            } else if (value instanceof Map<?, ?> nested) {
                masked.put(key, mask(nested));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    private static boolean isMaskedKey(String key) {
        return MASKED_KEYS.contains(key.toLowerCase(Locale.ROOT).replace("_", ""));
    }

    private Long resolveTargetId(String expression, ProceedingJoinPoint joinPoint) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] names = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            StandardEvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                if (names != null && i < names.length && names[i] != null) {
                    context.setVariable(names[i], args[i]);
                }
            }
            context.setVariable("args", args);
            Object value = expressionParser.parseExpression(expression).getValue(context);
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.valueOf(String.valueOf(value));
        } catch (RuntimeException ex) {
            log.warn("解析审计目标 id 表达式失败：expression={} error={}", expression, ex.getMessage());
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return truncate(json, MAX_JSON_LENGTH);
        } catch (Exception ex) {
            return "{\"serializationError\":\"" + ex.getClass().getSimpleName() + "\"}";
        }
    }

    private static String errorJson(Throwable ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().replace("\"", "'");
        return "{\"error\":\"" + ex.getClass().getSimpleName() + "\",\"message\":\"" + truncate(message, 300) + "\"}";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...(truncated)";
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /** 反向代理后取真实客户端 IP（Nginx 会写入 X-Forwarded-For）。 */
    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }
}
