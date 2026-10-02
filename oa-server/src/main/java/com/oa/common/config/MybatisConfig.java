package com.oa.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * MyBatis-Plus 配置。
 *
 * <p>注意：{@code DataScopeInterceptor} 是独立的 {@code Interceptor} Bean，由 MyBatis-Spring-Boot
 * 自动装载进 {@code SqlSessionFactory}，**不要**再手工 addInnerInterceptor（否则会被织入两次）。
 * 本类只注册分页内拦截器。
 *
 * <p><b>顺序约定</b>：本 Bean 标 {@code HIGHEST_PRECEDENCE}（注册在前 → 处在插件链内层），
 * {@code DataScopeInterceptor} 标 {@code LOWEST_PRECEDENCE}（注册在后 → 处在最外层先执行），
 * 保证数据域标记先被替换、再交给分页拦截器解析 SQL（详见 {@code DataScopeInterceptor} 类注释）。
 */
@Configuration
public class MybatisConfig {

    /** 单页最大条数（防止前端传入超大 size 拖垮库，性能项见 doc/tech-design.md §6）。 */
    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
