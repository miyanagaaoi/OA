package com.oa.common.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.oa.common.config.OaProperties;
import com.oa.common.security.AuthInterceptor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
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

    /**
     * Jackson 配置（时间与解析特性）：
     * <ul>
     *   <li>时间统一 {@code yyyy-MM-dd HH:mm:ss}（库内存 UTC、展示 Asia/Shanghai）；</li>
     *   <li>反序列化忽略未知字段（前端多传字段不得导致 400）。</li>
     * </ul>
     *
     * <p><b>数字精度</b>（{@code Long}/{@code long} → 字符串、{@code BigDecimal} → 字符串）
     * 已收敛到 {@code com.oa.common.config.JacksonConfig#oaNumberAsStringCustomizer}——
     * 序列化口径集中在 {@code common/config} 一处，避免两个 customizer 各写一半。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer oaJacksonCustomizer(OaProperties properties) {
        OaProperties.Jackson jackson = properties.getJackson();
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(jackson.getTimeZone()));
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(jackson.getDateFormat());
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
        };
    }
}
