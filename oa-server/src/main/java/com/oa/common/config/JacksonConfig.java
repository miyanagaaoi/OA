package com.oa.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化口径（{@code oa.jackson.*}）：**数字精度的最后一道闸**。
 *
 * <h2>为什么必须做（施工要求第 1 条）</h2>
 * <ul>
 *   <li><b>{@code Long}/{@code long} → JSON 字符串</b>：主键是 {@code BIGINT UNSIGNED}，
 *       而 JS 的 {@code Number} 只有 2^53 精度。一旦以数字下发，前端解析 9007199254740993 会变成
 *       …992，出现「同一条记录 id 前后不一致 / 详情查不到」的幽灵缺陷。因此统一用
 *       {@link ToStringSerializer} 输出为**带引号的字符串**，前端一律按 string 处理
 *       （{@code oa-web/src/types/identity-wire.d.ts} 的 {@code WireId = number | string} 已兼容）；</li>
 *   <li><b>不波及 {@code Integer}/{@code int}/{@code short}/{@code double}</b>：只按类型注册
 *       {@code Long.class} / {@code Long.TYPE} / {@code BigInteger.class}，其余数字类型走默认序列化，
 *       页大小、数量、排序号等仍是 JSON number（前端 {@code PageResult.size} 等按 number 断言）；</li>
 *   <li><b>{@code BigDecimal} → 字符串</b>：金额 {@code DECIMAL(18,2)} 禁止浮点（doc/data-model.md §1）；</li>
 *   <li>反序列化**不受影响**：Jackson 默认允许把 {@code "123"} 强制为 {@code Long}，
 *       因此前端回传字符串 id 仍能正常绑定到 {@code Long} 入参。</li>
 * </ul>
 *
 * <p>开关：{@code oa.jackson.serialize-long-as-string}（默认 {@code true}）、
 * {@code oa.jackson.serialize-big-decimal-as-string}（默认 {@code true}）。
 * 日期格式与其它特性见 {@code com.oa.common.web.WebMvcConfig}。
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册数字类型 → 字符串的序列化器。
     *
     * <p>幂等：同一类型重复注册只是覆盖为同一个 {@link ToStringSerializer#instance}，
     * 与其它 {@link Jackson2ObjectMapperBuilderCustomizer}（日期/特性）互不干扰。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer oaNumberAsStringCustomizer(OaProperties properties) {
        OaProperties.Jackson jackson = properties.getJackson();
        return builder -> {
            if (jackson.isSerializeBigDecimalAsString()) {
                builder.serializerByType(BigDecimal.class, ToStringSerializer.instance);
            }
            if (jackson.isSerializeLongAsString()) {
                builder.serializerByType(Long.class, ToStringSerializer.instance);
                builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
                builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
            }
        };
    }
}
