package com.oa.identity.app;

import com.oa.common.config.OaProperties;
import com.oa.identity.api.dto.AuthDtos;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 客户端运行期配置装配（{@code GET /api/v1/auth/client-config}）——
 * normify 模块 {@code oa.identity.session.client}。
 *
 * <p>为什么单独一个类（而不是塞进 {@link AuthService}）：本响应是**登录前**就要可取的
 * 静态运行期参数，与登录/会话生命周期无关；独立出来便于契约测试直接构造，
 * 也便于它的安全边界（只读 {@code oa.web.*} / {@code oa.session.*} / {@code oa.security.*} /
 * {@code oa.watermark.*}）在代码上一眼可查。
 *
 * <h2>只读这些配置，别的一律不下发</h2>
 * <ul>
 *   <li>读：{@code oa.web.title}、{@code oa.web.api-base-url}、{@code oa.session.cookie-name}、
 *       {@code oa.session.cookie-secure}（= {@code forceHttps}）、{@code oa.session.max-devices}、
 *       {@code oa.session.remember-me-days}、{@code oa.security.password-min-length}、
 *       {@code oa.security.login-max-failures}、{@code oa.security.login-lock-minutes}、
 *       {@code oa.watermark.opacity}；</li>
 *   <li>不读也不下发：{@code spring.datasource.*} → {@code oa.db.*}（库名/账号/口令）、
 *       {@code spring.data.redis.*}、{@code oa.security.bcrypt-strength}、任何密钥/令牌，
 *       以及请求上下文里的主机、IP、Cookie 值 —— 本响应是**匿名可取**的。</li>
 * </ul>
 *
 * <p>口令子对象与 {@code GET /api/v1/auth/password-policy} **同源**（同一批
 * {@code oa.security.*} 键、同一「≥8 位且含字母+数字」口径）；两处若将来要合并，
 * 应保留本响应中已经对前端生效的字段名。
 */
@Service
public class ClientConfigService {

    /** 口令最小长度的绝对下限（REQ-NFR-005：≥8 位）。 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final OaProperties properties;
    private final Environment environment;

    public ClientConfigService(OaProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /** 当前运行期配置（非敏感）。 */
    public AuthDtos.ClientConfigResponse current() {
        OaProperties.Web web = properties.getWeb();
        OaProperties.Session session = properties.getSession();
        OaProperties.Security security = properties.getSecurity();
        return new AuthDtos.ClientConfigResponse(
                web.getTitle(),
                activeEnv(),
                web.getApiBaseUrl(),
                session.getCookieName(),
                session.isCookieSecure(),
                // 水印透明度：与 /auth/me、/portal/watermark/profile 同源（oa.watermark.opacity）
                WatermarkPolicy.clampOpacity(properties.getWatermark().getOpacity()),
                new AuthDtos.SessionLimits(session.getMaxDevices(), rememberMeDaysAsInt(session)),
                new AuthDtos.PasswordLimits(
                        Math.max(MIN_PASSWORD_LENGTH, security.getPasswordMinLength()),
                        true,
                        true,
                        security.getLoginMaxFailures(),
                        security.getLoginLockMinutes()));
    }

    /**
     * 运行环境名：取**激活 profile**（多个时按声明顺序用 {@code ,} 连接；缺省时取默认 profile，
     * 再缺省为 {@code default}）。
     *
     * <p>用 profile 而不是另配一个 {@code oa.web.env}：前者是应用**实际**运行的环境，
     * 后者是人工填的字符串，二者不一致时前端会显示错误的环境标识（属于数据误导）。
     */
    String activeEnv() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        if (profiles == null || profiles.length == 0) {
            return "default";
        }
        return String.join(",", profiles);
    }

    /**
     * 「记住我」天数：{@code oa.session.remember-me-days} 是 {@code long}（配置层口径），
     * 下发时收窄为 {@code int} —— 见 {@link AuthDtos.SessionLimits} 的说明（避免被序列化成字符串）。
     * 负数按 0 处理（配置写错时宁可显示 0 天，也不下发负数）。
     */
    private static int rememberMeDaysAsInt(OaProperties.Session session) {
        long days = session.getRememberMeDays();
        if (days <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, days);
    }
}
