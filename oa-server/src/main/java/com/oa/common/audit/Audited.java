package com.oa.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要写审计日志（{@code sys_log}，只追加）的敏感动作。
 *
 * <p>一期必须留痕的动作（PRD REQ-LOG-001/004、AC-41）：
 * 登录/登出、导出、权限与数据域变更、模板发布、归档登记、管理员改派等。
 *
 * <p>用法：
 * <pre>{@code
 * @Audited(action = "logout", targetType = "session", targetId = "#sessionId")
 * @PostMapping("/api/v1/auth/logout")
 * public ApiResponse<Void> logout(...) { ... }
 * }</pre>
 *
 * <p>说明：登录成功/失败由 {@code AuthService} 显式调用 {@code AuditLogWriter}（此时会话尚未建立，
 * 没有当前登录人上下文），其余动作统一走本注解 + {@link AuditAspect}。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 动作码，写入 {@code sys_log.action}，如 {@code login/logout/export/grant/publish_template}。 */
    String action();

    /** 目标类型，写入 {@code sys_log.target_type}，如 {@code user/role/permission/template/instance/session}。 */
    String targetType();

    /** 目标 id 的 SpEL 表达式（如 {@code "#id"} 或 {@code "#request.userId"}）；为空则不记录。 */
    String targetId() default "";

    /** 是否记录变更前值（权限变更必填，见 data-model.md 8.1）。 */
    boolean recordBefore() default false;

    /** 是否记录变更后值。 */
    boolean recordAfter() default true;

    /** 记录入参；默认关闭，避免把整页表单写进日志。 */
    boolean recordArgs() default false;
}
