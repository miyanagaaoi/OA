package com.oa.common.web;

import com.oa.common.config.OaProperties;
import com.oa.common.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配：认证拦截器、CORS（生产关闭）、Jackson（金额用字符串、禁止浮点）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final OaProperties properties;

    public WebMvcConfig(AuthInterceptor authInterceptor, OaProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(properties.getWeb().getPermitAll().toArray(new String[0]))
                .order(10);
    }

    /**
     * CORS：**生产必须关闭**（前后端同源经 Nginx 反代）。
     * dev 下允许本机 Vite 端口，且必须 {@code allowCredentials=true}（会话 Cookie 方案）。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!properties.getWeb().isCorsEnabled()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.getWeb().getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(TraceIds.HEADER)
                .allowCredentials(true)
                .maxAge(1800);
    }

    /*
     * 注意（2026-10-02 修复记录）：本类原有一个 @Bean oaJacksonCustomizer(...)。
     * 由于本类**构造器注入 AuthInterceptor**，而 AuthInterceptor 构造器又注入 ObjectMapper，
     * 一旦本类再产出 Jackson2ObjectMapperBuilderCustomizer，就会形成**纯构造器注入的环**：
     *   jacksonObjectMapperBuilder → webMvcConfig → authInterceptor → jacksonObjectMapper → (builder)
     * Spring 无法用 early-reference 打破该环（`spring.main.allow-circular-references=true` 也无效），
     * 结果是**应用在任何 profile（含 prod/Docker）都无法启动**。
     * 因此该 customizer 已整体迁到 `com.oa.common.config.JacksonConfig#oaDateTimeCustomizer`，
     * 与数字精度 customizer 并列，序列化口径集中在 common/config 一处。
     */
}
