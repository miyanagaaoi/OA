package com.oa.authz.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.authz.api.dto.AuthzDtos;
import com.oa.common.config.JacksonConfig;
import com.oa.common.config.OaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * {@code PUT /api/v1/authz/roles/{id}/permissions} 的**请求体绑定契约**单测。
 *
 * <p>前端实际提交（无 {@code mode}）：
 * <pre>{@code
 * { "permissionIds": ["1","2","4"], "leafIds": ["4"], "halfCheckedIds": ["1","2"] }
 * }</pre>
 * 本测试锁死三件事：
 * <ol>
 *   <li>{@code permissionIds} 的**字符串 id** 能绑定到 {@code List<Long>}
 *       （{@code JacksonConfig} 只把 Long <b>序列化</b>成字符串，反序列化兼容两种形态）；</li>
 *   <li>额外字段 {@code leafIds}/{@code halfCheckedIds} **不会**被拒绝——
 *       {@code JacksonConfig#oaDateTimeCustomizer} 已关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}
 *       （Spring Boot 默认即关闭，本工程显式再关一次；本测试即其回归防线）；</li>
 *   <li>{@code mode} 缺省绑定为 {@code null}，由服务层归一为 {@code auto}
 *       （见 {@code RolePermissionService#normalizeMode}）。</li>
 * </ol>
 */
class RolePermissionRequestBindingTest {

    /** 与运行时同口径的 ObjectMapper：JacksonConfig 的两个 customizer（数字转字符串 + 时间与未知字段）。 */
    private static ObjectMapper runtimeMapper() {
        OaProperties properties = new OaProperties();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        JacksonConfig jacksonConfig = new JacksonConfig();
        jacksonConfig.oaNumberAsStringCustomizer(properties).customize(builder);
        jacksonConfig.oaDateTimeCustomizer(properties).customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("前端真实请求体：字符串 id + 额外字段 leafIds/halfCheckedIds 均可绑定，mode 缺省为 null")
    void frontendPayloadBindsCleanly() throws Exception {
        String json = "{\"permissionIds\":[\"1\",\"2\",\"4\"],\"leafIds\":[\"4\"],"
                + "\"halfCheckedIds\":[\"1\",\"2\"]}";

        AuthzDtos.RolePermissionSaveRequest request =
                runtimeMapper().readValue(json, AuthzDtos.RolePermissionSaveRequest.class);

        assertThat(request.permissionIds()).containsExactly(1L, 2L, 4L);
        assertThat(request.mode()).isNull();
    }

    @Test
    @DisplayName("数字 id 与显式 mode 同样可绑定（前端两种写法都兼容）")
    void numericIdsAndExplicitModeBind() throws Exception {
        AuthzDtos.RolePermissionSaveRequest request = runtimeMapper().readValue(
                "{\"permissionIds\":[1,2,4],\"mode\":\"ticks\"}", AuthzDtos.RolePermissionSaveRequest.class);

        assertThat(request.permissionIds()).containsExactly(1L, 2L, 4L);
        assertThat(request.mode()).isEqualTo("ticks");
    }

    @Test
    @DisplayName("空数组可绑定（= 取消全部权限），null 由 @NotNull 拦截")
    void emptyArrayIsAllowedButNullIsNot() throws Exception {
        AuthzDtos.RolePermissionSaveRequest empty = runtimeMapper().readValue(
                "{\"permissionIds\":[]}", AuthzDtos.RolePermissionSaveRequest.class);
        assertThat(empty.permissionIds()).isEmpty();

        AuthzDtos.RolePermissionSaveRequest missing = runtimeMapper().readValue(
                "{}", AuthzDtos.RolePermissionSaveRequest.class);
        assertThat(missing.permissionIds()).isNull();
    }

    @Test
    @DisplayName("未改过未知字段开关：完整前端请求体（含 leafIds/halfCheckedIds）反序列化不抛异常")
    void unknownPropertiesAreNotRejected() throws Exception {
        String json = "{\"permissionIds\":[\"4\"],\"leafIds\":[\"4\"],\"halfCheckedIds\":[\"1\",\"2\"],"
                + "\"futureField\":\"x\"}";

        assertThat(runtimeMapper().readValue(json, AuthzDtos.RolePermissionSaveRequest.class).permissionIds())
                .isNotNull();
    }
}
