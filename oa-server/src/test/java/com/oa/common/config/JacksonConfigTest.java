package com.oa.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Long 精度单测（施工要求第 1 条）：{@code Long}/{@code long} 必须序列化为**带引号的 JSON 字符串**，
 * 且**不影响** {@code Integer} 与其它数字类型。
 *
 * <p>回归背景：主键是 {@code BIGINT UNSIGNED}，一旦以 JSON number 下发，
 * 超过 2^53 的 id 会被 JS 静默改写（9007199254740993 → …992），
 * 前端出现「列表能看见、详情查不到」的幽灵缺陷。
 *
 * <p>断言口径：
 * <ol>
 *   <li>包装类型 {@code Long} 与基本类型 {@code long} **都**带引号；</li>
 *   <li>{@code Integer}/{@code int}/{@code BigDecimal} 的既有口径不变（Integer 仍是 number）；</li>
 *   <li>嵌套对象（如 DTO 的 {@code id} 字段）同样带引号；</li>
 *   <li>反序列化不受影响：前端把字符串 id 回传过来仍能绑定到 {@code Long}；</li>
 *   <li>开关 {@code oa.jackson.serialize-long-as-string=false} 可关闭（降级为 number）。</li>
 * </ol>
 */
class JacksonConfigTest {

    /** 顶层 id 用 {@code Long}、{@code rawId} 用基本类型 {@code long}，覆盖两种注册键。 */
    private record Sample(Long id, long rawId, Integer count, int plain, BigDecimal amount, String name) {
    }

    private static ObjectMapper objectMapper(OaProperties properties) {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().oaNumberAsStringCustomizer(properties).customize(builder);
        return builder.build();
    }

    private static ObjectMapper defaultMapper() {
        return objectMapper(new OaProperties());
    }

    @Test
    @DisplayName("Long/long 序列化为带引号的字符串（2^53 精度防线）")
    void longIsSerializedAsQuotedString() throws Exception {
        Sample sample = new Sample(9007199254740993L, 9007199254740993L, 7, 8,
                new BigDecimal("1234.56"), "张三");

        String json = defaultMapper().writeValueAsString(sample);

        assertThat(json).contains("\"id\":\"9007199254740993\"");
        assertThat(json).contains("\"rawId\":\"9007199254740993\"");
        // 反向断言：绝不能以 JSON number 形式出现
        assertThat(json).doesNotContain("\"id\":9007199254740993");
        assertThat(json).doesNotContain("\"rawId\":9007199254740993");
    }

    @Test
    @DisplayName("不影响 Integer/int（页大小、数量等仍是 JSON number）")
    void integerStaysNumber() throws Exception {
        String json = defaultMapper().writeValueAsString(new Sample(1L, 2L, 7, 8, null, null));

        assertThat(json).contains("\"count\":7");
        assertThat(json).contains("\"plain\":8");
        assertThat(json).doesNotContain("\"count\":\"7\"");
        assertThat(json).doesNotContain("\"plain\":\"8\"");
    }

    @Test
    @DisplayName("BigDecimal（金额）仍按既有口径序列化为字符串")
    void bigDecimalStaysString() throws Exception {
        String json = defaultMapper().writeValueAsString(new Sample(1L, 2L, 3, 4, new BigDecimal("1234.50"), null));
        assertThat(json).contains("\"amount\":\"1234.50\"");
    }

    @Test
    @DisplayName("嵌套结构中的 id 同样是带引号字符串（出参 DTO 的实际形态）")
    void nestedIdsAreQuoted() throws Exception {
        record Row(Long id, String name, List<Long> orgIds) {
        }
        String json = defaultMapper().writeValueAsString(new Row(42L, "公司A", List.of(1L, 2L)));

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"orgIds\":[\"1\",\"2\"]");
    }

    @Test
    @DisplayName("反序列化兼容：字符串 id 仍能绑定到 Long 入参（前端统一按 string 传）")
    void stringIdDeserializesIntoLong() throws Exception {
        Sample parsed = defaultMapper()
                .readValue("{\"id\":\"9007199254740993\",\"rawId\":\"5\",\"count\":7,\"plain\":8}", Sample.class);

        assertThat(parsed.id()).isEqualTo(9007199254740993L);
        assertThat(parsed.rawId()).isEqualTo(5L);
        assertThat(parsed.count()).isEqualTo(7);
    }

    @Test
    @DisplayName("开关 serialize-long-as-string=false 时降级为 JSON number（保持可配置）")
    void switchOffKeepsLongAsNumber() throws Exception {
        OaProperties properties = new OaProperties();
        properties.getJackson().setSerializeLongAsString(false);

        String json = objectMapper(properties).writeValueAsString(new Sample(42L, 43L, 1, 2, null, null));

        assertThat(json).contains("\"id\":42");
        assertThat(json).contains("\"rawId\":43");
    }
}
