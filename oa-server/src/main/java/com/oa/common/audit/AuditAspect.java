package com.oa.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.common.web.TraceIds;
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
 * <p>失败隔离：业务方法抛异常时，先写一条带**结构化错误摘要**的审计记录再原样抛出；审计写入本身的异常
 * 由 {@link AuditLogWriter} 吞掉并记 ERROR，绝不影响业务流程。
 *
 * <p><b>JSON 列铁律</b>：写入 {@code sys_log.before_json/after_json} 的文本**任何路径下都必须是合法 JSON**。
 * 异常路径尤其如此：旧实现用字符串拼接把异常 message 直接塞进 JSON 字面量（只把 {@code "} 换成 {@code '}），
 * 而 message 里的**换行 / 制表符 / 反斜杠**（MyBatis、JDBC、{@code NestedServletException} 的消息里极其常见）
 * 会让文本**不再是合法 JSON**，MySQL 以
 * {@code ERROR 3140 (22032): Invalid JSON text: "Invalid encoding in string." at position N in value for column 'sys_log.after_json'}
 * 拒绝写入（2026-10 运行期实测复现）。现在改为：异常路径只记**结构化摘要**
 * （{@code error}/{@code errorType}/{@code message}/{@code traceId}/{@code cause}/{@code stack}），
 * 由 Jackson 完成转义——堆栈只作为**普通 JSON 字符串字段**出现，绝不当 JSON 结构拼；
 * 长度超限统一走 {@link #truncateJson}（退化为合法 JSON 包装）。
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

    /** 异常 message 落库上限（**先截断再序列化**，避免截断 JSON 本身）。 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 300;

    /** 结构化堆栈摘要的帧数上限与字符上限（堆栈只作 JSON **字符串**字段）。 */
    private static final int MAX_STACK_FRAMES = 25;
    private static final int MAX_STACK_LENGTH = 800;

    /** cause（若有）message 的落库上限。 */
    private static final int MAX_CAUSE_LENGTH = 120;

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
            return truncateJson(json, MAX_JSON_LENGTH);
        } catch (Exception ex) {
            return "{\"serializationError\":\"" + ex.getClass().getSimpleName() + "\"}";
        }
    }

    /**
     * JSON 列的**安全截断**：超长时退化为「合法 JSON 对象」而不是把原文切断。
     *
     * <p>回归背景（2a.4 运行期实测发现）：{@code sys_log.after_json} 是 MySQL {@code JSON} 列，
     * 而旧实现对**序列化后的原文**直接 {@code substring(0, 4000) + "...(truncated)"} —— 被切断的
     * JSON 不再是合法 JSON，MySQL 以 {@code Data truncation: Invalid JSON text ... at position 4476}
     * 拒绝写入；由于该写入在业务事务内，整笔业务随之 500（实测触发点：
     * 终止流程 / 重新提交等**返回体较大**（含审批人快照）的动作）。
     *
     * <p>现在的口径：长度在阈值内原样返回；超长时返回
     * {@code {"truncated":true,"length":N,"preview":"…"}} —— 预览字符串本身按**字符**截断并做 JSON 转义，
     * 因此永远都是合法 JSON。
     */
    private String truncateJson(String json, int max) {
        if (json == null || json.length() <= max) {
            return json;
        }
        String preview = escapeForJsonString(json.substring(0, Math.max(max - 80, 0)));
        return "{\"truncated\":true,\"length\":" + json.length() + ",\"preview\":\"" + preview + "\"}";
    }

    /** 把任意片段转义成可安全放进 JSON 字符串字面量的形态。 */
    private static String escapeForJsonString(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    /**
     * 异常路径的 {@code after_json}：**结构化摘要**，永远合法 JSON。
     *
     * <p>字段：
     * <ul>
     *   <li>{@code error}：异常简单类名（沿用旧键名，下游检索口径不变）；</li>
     *   <li>{@code errorType}：异常全限定名（便于按包定位）；</li>
     *   <li>{@code message}：异常 message（先按**字符**截断到
     *       {@value #MAX_ERROR_MESSAGE_LENGTH}，再交给 Jackson 转义）；</li>
     *   <li>{@code traceId}：链路追踪号（{@link TraceIds#current()}，无则省略）——
     *       与 HTTP 响应体 {@code traceId}、服务端日志可对上；</li>
     *   <li>{@code cause}：直接原因的类型与 message（截断，无则省略）；</li>
     *   <li>{@code stack}：**截断后的堆栈摘要**，只是 JSON 字符串字段（换行照常写入字符串内部，
     *       由 Jackson 转义成 {@code \n}），绝不被当作 JSON 结构拼进文本。</li>
     * </ul>
     *
     * <p>整串最后过 {@link #toJson}：长度超限退化为合法 JSON 包装，因此
     * {@code JSON_VALID(after_json)} 恒为 1。
     */
    private String errorJson(Throwable ex) {
        if (ex == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", ex.getClass().getSimpleName());
        payload.put("errorType", ex.getClass().getName());
        payload.put("message", truncate(ex.getMessage(), MAX_ERROR_MESSAGE_LENGTH));
        String traceId = TraceIds.current();
        if (traceId != null && !traceId.isBlank()) {
            payload.put("traceId", traceId);
        }
        Throwable cause = ex.getCause();
        if (cause != null) {
            payload.put("cause", cause.getClass().getName() + ": "
                    + truncate(cause.getMessage(), MAX_CAUSE_LENGTH));
        }
        payload.put("stack", stackSummary(ex));
        return toJson(payload);
    }

    /**
     * 堆栈摘要（**字符串**，不是 JSON 数组）：只保留前 {@value #MAX_STACK_FRAMES} 帧，
     * 并在 {@value #MAX_STACK_LENGTH} 字符处截断。它写入 {@code after_json.stack}，
     * 由 Jackson 负责把换行转义成 {@code \n}。
     */
    private static String stackSummary(Throwable ex) {
        StackTraceElement[] frames = ex.getStackTrace();
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(frames.length, MAX_STACK_FRAMES);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("\tat ").append(frames[i]);
        }
        if (frames.length > limit) {
            builder.append("\n\t... ").append(frames.length - limit).append(" more");
        }
        return truncate(builder.toString(), MAX_STACK_LENGTH);
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
