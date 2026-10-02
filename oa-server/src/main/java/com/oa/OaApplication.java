package com.oa;

import com.oa.common.config.OaProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 集团OA审批系统 · 后端入口（模块化单体）。
 *
 * <p>模块划分与 {@code normify-oa} 的 13 个 L1 领域同名，每个领域内部固定
 * {@code api → app → domain → infra} 四层：{@code domain} 不依赖 Web，
 * 跨领域只允许调用对方的 {@code app} 接口（doc/tech-design.md §4.1）。
 */
@SpringBootApplication
@EnableConfigurationProperties(OaProperties.class)
@MapperScan({
        "com.oa.identity.infra",
        "com.oa.authz.infra",
        "com.oa.common.audit"
})
public class OaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OaApplication.class, args);
    }
}
