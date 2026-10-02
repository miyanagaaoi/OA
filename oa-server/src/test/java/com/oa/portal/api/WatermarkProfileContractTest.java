package com.oa.portal.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.config.JacksonConfig;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeProvider;
import com.oa.common.security.Anonymous;
import com.oa.common.security.AuthInterceptor;
import com.oa.common.security.CurrentUser;
import com.oa.common.security.SessionStore;
import com.oa.portal.api.dto.PortalDtos;
import com.oa.portal.app.WatermarkProfileService;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * {@code GET /api/v1/portal/watermark/profile} 的**契约测试**（收口要求 3 / 参照
 * {@code UserControllerContractTest} 风格）。
 *
 * <p>锁死四件事：
 * <ol>
 *   <li><b>字段形状</b>：{@code {enabled,text,opacity,employeeNo,name}}，{@code opacity} 是 JSON number；</li>
 *   <li><b>文案与空值</b>：{@code text} = 「姓名 + 工号」；工号为空 → 退化只显示姓名且
 *       {@code employeeNo} 显式为 {@code null}（不是空串，也不是字符串 "null"）；</li>
 *   <li><b>透明度区间</b>：5%–8%（DESIGN.md / AC-44），越界一律夹紧；</li>
 *   <li><b>未登录 401</b>：既断言实现层（无上下文 / 无主体 → 401），也断言拦截器层
 *       （无会话 Cookie 访问本路径 → HTTP 401 + code 40101），并断言本接口**没有** {@code @Anonymous}。</li>
 * </ol>
 */
class WatermarkProfileContractTest {

    private static final Long USER_ID = 1L;

    private final WatermarkProfileService service = new WatermarkProfileService(new OaProperties());
    private final WatermarkController controller = new WatermarkController(service);

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static void login(String name, String employeeNo) {
        DataScopeContext.set(DataScopeContext.builder()
                .principal(CurrentUser.of(USER_ID, "admin", name, employeeNo, 1L, 1L,
                        Set.of("admin"), Set.of(), false))
                .build());
    }

    @Test
    @DisplayName("已登录：text = 「姓名 工号」、opacity 落在 0.05–0.08、employeeNo/name 原样下发")
    void profileForLoggedInUser() {
        login("系统管理员", "10086");

        PortalDtos.WatermarkProfileView view = controller.watermarkProfile().getData();

        assertThat(view.enabled()).isTrue();
        assertThat(view.name()).isEqualTo("系统管理员");
        assertThat(view.employeeNo()).isEqualTo("10086");
        assertThat(view.text()).isEqualTo("系统管理员 10086");
        assertThat(view.opacity()).isBetween(0.05, 0.08);
        assertThat(view.opacity()).isEqualTo(0.06);
    }

    @Test
    @DisplayName("工号为 null / 空串 / 空白 → employeeNo=null 且 text 只有姓名（无多余空格）")
    void missingEmployeeNoDegradesToNameOnly() {
        for (String employeeNo : new String[] {null, "", "   "}) {
            login("张三", employeeNo);

            PortalDtos.WatermarkProfileView view = controller.watermarkProfile().getData();

            assertThat(view.employeeNo()).as("工号缺失必须显式 null（而不是空串）").isNull();
            assertThat(view.text()).isEqualTo("张三");
            assertThat(view.text()).doesNotContain("null");
        }
    }

    @Test
    @DisplayName("透明度越界一律夹紧到 0.05–0.08（0.6→0.08、0.01→0.05、NaN→区间中值 0.065），绝不下发越界值")
    void opacityIsClampedIntoDesignRange() {
        assertThat(opacityOf(0.6)).isEqualTo(0.08);
        assertThat(opacityOf(0.01)).isEqualTo(0.05);
        assertThat(opacityOf(Double.NaN)).isEqualTo(0.065);
        assertThat(opacityOf(0.05)).isEqualTo(0.05);
        assertThat(opacityOf(0.08)).isEqualTo(0.08);
    }

    private double opacityOf(double configured) {
        OaProperties properties = new OaProperties();
        properties.getWatermark().setOpacity(configured);
        login("系统管理员", "10086");
        return new WatermarkProfileService(properties).currentProfile().opacity();
    }

    @Test
    @DisplayName("未登录（无上下文）→ 401；有上下文但无主体 → 401")
    void unauthenticatedIsUnauthorized() {
        assertThatThrownBy(() -> controller.watermarkProfile())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        DataScopeContext.set(DataScopeContext.builder().build());
        assertThatThrownBy(() -> controller.watermarkProfile())
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("拦截器层：无会话 Cookie 访问本路径 → HTTP 401（code 40101），且本接口未标注 @Anonymous")
    void interceptorRejectsAnonymousRequest() throws Exception {
        Method handlerMethod = WatermarkController.class.getMethod("watermarkProfile");
        assertThat(handlerMethod.isAnnotationPresent(Anonymous.class))
                .as("水印画像必须要求登录：不得标注 @Anonymous")
                .isFalse();
        assertThat(WatermarkController.class.isAnnotationPresent(Anonymous.class))
                .as("控制器类也不得整体放行")
                .isFalse();

        AuthInterceptor interceptor = new AuthInterceptor(
                new SessionStore.InMemory(), mock(DataScopeProvider.class), new OaProperties(), objectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/portal/watermark/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new HandlerMethod(controller, handlerMethod));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"code\":40101");
    }

    @Test
    @DisplayName("只能取当前会话用户：接口不接受任何用户标识参数（防「按 id 取他人水印」）")
    void endpointTakesNoUserParameter() throws Exception {
        Method handlerMethod = WatermarkController.class.getMethod("watermarkProfile");
        assertThat(handlerMethod.getParameterCount())
                .as("水印画像端点不得有 userId 之类的入参 —— 只能取当前会话用户")
                .isZero();
    }

    @Test
    @DisplayName("JSON 字段名与类型逐字：{enabled,text,opacity,employeeNo,name}，opacity 是 number")
    void jsonShapeIsExact() throws Exception {
        ObjectMapper mapper = objectMapper();

        String json = mapper.writeValueAsString(new PortalDtos.WatermarkProfileView(
                true, "系统管理员 10086", 0.06, "10086", "系统管理员"));
        assertThat(json).isEqualTo("{\"enabled\":true,\"text\":\"系统管理员 10086\","
                + "\"opacity\":0.06,\"employeeNo\":\"10086\",\"name\":\"系统管理员\"}");

        // 工号缺失：non_null 口径下 employeeNo 被省略（前端按 undefined 处理为「工号缺失」）
        String missing = mapper.writeValueAsString(new PortalDtos.WatermarkProfileView(
                true, "张三", 0.05, null, "张三"));
        assertThat(missing).isEqualTo("{\"enabled\":true,\"text\":\"张三\",\"opacity\":0.05,\"name\":\"张三\"}");
    }

    private static ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json()
                .serializationInclusion(JsonInclude.Include.NON_NULL);
        new JacksonConfig().oaNumberAsStringCustomizer(new OaProperties()).customize(builder);
        return builder.build();
    }
}
