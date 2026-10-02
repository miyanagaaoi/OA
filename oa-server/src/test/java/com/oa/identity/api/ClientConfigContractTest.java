package com.oa.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.config.JacksonConfig;
import com.oa.common.config.OaProperties;
import com.oa.common.security.Anonymous;
import com.oa.identity.api.dto.AuthDtos;
import com.oa.identity.app.ClientConfigService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * {@code GET /api/v1/auth/client-config} 的**契约测试**（收口要求 2 / 参照
 * {@code UserControllerContractTest} 风格）。
 *
 * <p>锁死四件事：
 * <ol>
 *   <li><b>字段形状</b>：{@code title/env/apiBaseUrl/sessionCookieName/forceHttps/watermarkOpacity/
 *       session{maxDevices,rememberMeDays}/password{minLength,requireLetter,requireDigit/
 *       lockThreshold,lockMinutes}} —— 与前端 {@code ClientConfig} 的形状逐字一致；</li>
 *   <li><b>登录前可取</b>：方法级 {@code @Anonymous}（未登录也不 401），且未加入路径白名单；</li>
 *   <li><b>不泄露敏感信息</b>：序列化后的响应里不得出现数据库/Redis 的连接串、口令、bcrypt cost、令牌；</li>
 *   <li><b>越界兜底</b>：{@code oa.watermark.opacity} 被配成越界值时下发值仍落在 0.05–0.08。</li>
 * </ol>
 */
class ClientConfigContractTest {

    private static ClientConfigService service(OaProperties properties, String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        when(environment.getDefaultProfiles()).thenReturn(new String[] {"default"});
        return new ClientConfigService(properties, environment);
    }

    @Test
    @DisplayName("出参字段与默认值：标题/环境/API 前缀/Cookie 名/会话与口令阈值全部就位")
    void clientConfigFieldsAndDefaults() {
        AuthDtos.ClientConfigResponse view = service(new OaProperties(), "dev").current();

        assertThat(view.title()).isEqualTo("集团OA审批系统");
        assertThat(view.env()).isEqualTo("dev");
        assertThat(view.apiBaseUrl()).isEqualTo("/api/v1");
        assertThat(view.sessionCookieName()).isEqualTo("OA_SESSION");
        // OaProperties 默认 cookie-secure=true（生产口径）；dev profile 由 yml 覆盖为 false
        assertThat(view.forceHttps()).isTrue();
        assertThat(view.session().maxDevices()).isEqualTo(3);
        assertThat(view.session().rememberMeDays()).isEqualTo(7);
        assertThat(view.password().minLength()).isEqualTo(8);
        assertThat(view.password().requireLetter()).isTrue();
        assertThat(view.password().requireDigit()).isTrue();
        assertThat(view.password().lockThreshold()).isEqualTo(5);
        assertThat(view.password().lockMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("env 取激活 profile（多个按声明顺序连接）；无激活 profile 时退回默认 profile")
    void envFollowsActiveProfiles() {
        assertThat(service(new OaProperties(), "dev", "local").current().env()).isEqualTo("dev,local");

        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(environment.getDefaultProfiles()).thenReturn(new String[] {"dev"});
        assertThat(new ClientConfigService(new OaProperties(), environment).current().env()).isEqualTo("dev");
    }

    @Test
    @DisplayName("口令最小长度按「≥8 位」夹紧：配成 6 时下发 8（与 /auth/password-policy 同口径）")
    void passwordMinLengthIsFlooredToEight() {
        OaProperties properties = new OaProperties();
        properties.getSecurity().setPasswordMinLength(6);

        assertThat(service(properties, "dev").current().password().minLength()).isEqualTo(8);
    }

    @Test
    @DisplayName("水印透明度越界被夹紧：0.6→0.08、0.01→0.05（与非敏感配置齐口径）")
    void watermarkOpacityIsClamped() {
        OaProperties tooHigh = new OaProperties();
        tooHigh.getWatermark().setOpacity(0.6);
        assertThat(service(tooHigh, "dev").current().watermarkOpacity()).isEqualTo(0.08);

        OaProperties tooLow = new OaProperties();
        tooLow.getWatermark().setOpacity(0.01);
        assertThat(service(tooLow, "dev").current().watermarkOpacity()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("登录前可取：方法标注 @Anonymous（放行只限这一个方法，不走路径白名单）")
    void endpointIsAnonymousButNotWhitelisted() throws Exception {
        Method method = AuthController.class.getMethod("clientConfig");
        assertThat(method.isAnnotationPresent(Anonymous.class))
                .as("client-config 必须在登录前可用（@Anonymous），否则登录页拿不到标题与口令提示")
                .isTrue();

        // 反向断言：白名单（OaProperties.Web#permitAll 默认值 + application.yml）里**没有**这条路径，
        // 保证放行面停留在「单个方法」而不是「一个 URL 前缀下的所有方法」
        OaProperties defaults = new OaProperties();
        assertThat(defaults.getWeb().getPermitAll()).doesNotContain("/api/v1/auth/client-config");
    }

    @Test
    @DisplayName("JSON 字段名逐字 + 不泄露库/Redis/口令/哈希成本等敏感信息")
    void jsonShapeIsExactAndCarriesNoSecrets() throws Exception {
        String json = objectMapper().writeValueAsString(service(new OaProperties(), "dev").current());

        assertThat(json).contains("\"title\":", "\"env\":\"dev\"", "\"apiBaseUrl\":\"/api/v1\"",
                "\"sessionCookieName\":\"OA_SESSION\"", "\"forceHttps\":true", "\"watermarkOpacity\":0.06",
                "\"session\":{", "\"maxDevices\":3", "\"rememberMeDays\":7",
                "\"password\":{", "\"minLength\":8", "\"requireLetter\":true", "\"requireDigit\":true",
                "\"lockThreshold\":5", "\"lockMinutes\":15");

        // 敏感面：连接串 / 端口 / 账号 / 哈希成本 / 令牌字段一律不得出现
        assertThat(json)
                .as("client-config 是匿名接口：不得泄露任何数据源/Redis/口令/hash 成本信息")
                .doesNotContainIgnoringCase("jdbc", "mysql", "3306", "redis", "6379", "datasource",
                        "passwordHash", "bcrypt", "secret", "token", "host");
    }

    /** 与前端同口径的映射模拟：确认前端能从本响应里取到 title / session / password 三组值。 */
    @Test
    @DisplayName("前端取值路径可用：session.maxDevices / session.rememberMeDays / password.* 都不是 null")
    void nestedObjectsAreNeverNull() {
        AuthDtos.ClientConfigResponse view = service(new OaProperties(), "prod").current();

        assertThat(view.session()).isNotNull();
        assertThat(view.password()).isNotNull();
        assertThat(view.session().maxDevices()).isPositive();
        assertThat(view.session().rememberMeDays()).isPositive();
        assertThat(view.password().lockThreshold()).isPositive();
        assertThat(view.password().lockMinutes()).isPositive();
    }

    private static ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL);
        new JacksonConfig().oaNumberAsStringCustomizer(new OaProperties()).customize(builder);
        return builder.build();
    }
}
