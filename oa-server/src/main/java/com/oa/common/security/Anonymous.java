package com.oa.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 免登录访问标记（登录、口令策略、健康检查、H5 扫码落地页等）。
 *
 * <p>与 {@code oa.web.permit-all} 路径白名单互为补充：白名单管「路径」，本注解管「方法」。
 * 其余 {@code /api/**} 一律要求有效会话（{@code AuthInterceptor}）。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {
}
