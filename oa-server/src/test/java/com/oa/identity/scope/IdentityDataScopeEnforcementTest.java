package com.oa.identity.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeInterceptor;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Set;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 受控表「裸查询 fail-closed」与「带标记即织入」的**行为验证**（无 DB / 无 Spring 容器）。
 *
 * <p>用真实的 {@link DataScopeInterceptor} + {@link DataScopeTableRegistry} 驱动两个分支：
 * <ol>
 *   <li>受控表（{@code sys_user}）的 SELECT **无** {@code @dataScope} 标记 → 40303 被拒；
 *       这正是「人员读取一律走带标记的 XML 方法、禁用 MyBatis-Plus 注入的
 *       {@code selectById/selectList}」这条约定的机器可验证证据；</li>
 *   <li>同一 SQL **带**标记 → 标记被替换为数据域片段（本人口径 {@code u.id = ?}），
 *       且替换后不再残留标记。</li>
 * </ol>
 */
class IdentityDataScopeEnforcementTest {

    private static final String SELECT_BY_ID = "com.oa.identity.infra.SysUserMapper.selectById";

    private final OaProperties properties = new OaProperties();
    private final DataScopeTableRegistry registry = new DataScopeTableRegistry(properties);
    private final DataScopeInterceptor interceptor = new DataScopeInterceptor(registry);

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    /** 自我口径（普通员工）：片段为 {@code (u.id = #{scope_uid})}。 */
    private void authenticateAsSelf() {
        CurrentUser principal = CurrentUser.of(7L, "u07", "赵庚", "A0007", 1351L, 12L,
                Set.of("employee"), EnumSet.of(DataScopeType.SELF), false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .scopes(EnumSet.of(DataScopeType.SELF))
                .primaryOrgId(1351L)
                .companyId(12L)
                .build());
    }

    private static Executor nullExecutor() {
        return (Executor) Proxy.newProxyInstance(
                IdentityDataScopeEnforcementTest.class.getClassLoader(),
                new Class<?>[]{Executor.class},
                (proxy, method, args) -> null);
    }

    private static Invocation invocation(MappedStatement statement) throws Exception {
        Method query = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        return new Invocation(nullExecutor(), query, new Object[]{statement, null, RowBounds.DEFAULT, null});
    }

    private MappedStatement statement(String id, String sql) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        StaticSqlSource sqlSource = new StaticSqlSource(configuration, sql);
        return new MappedStatement.Builder(configuration, id, sqlSource, SqlCommandType.SELECT).build();
    }

    @Test
    @DisplayName("受控表裸查询（缺 @dataScope 标记）→ 40303 被 fail-closed 拒绝")
    void unmarkedControlledSelectIsRejected() throws Throwable {
        assertThat(registry.isScoped("sys_user")).isTrue();
        // MyBatis-Plus 注入的 selectById 不在豁免清单里（豁免只有 selectByAccount / selectOne 等）
        assertThat(registry.isExempt(SELECT_BY_ID)).isFalse();

        authenticateAsSelf();
        MappedStatement bare = statement(SELECT_BY_ID, "SELECT id, name FROM sys_user WHERE id = ?");
        assertThatThrownBy(() -> interceptor.intercept(invocation(bare)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DATA_SCOPE_MISSING))
                .hasMessageContaining("未织入数据域过滤");
    }

    @Test
    @DisplayName("带标记的受控表查询：标记被替换为数据域片段，替换后无标记残留")
    void markedSelectIsWoven() throws Throwable {
        authenticateAsSelf();
        MappedStatement marked = statement(
                "com.oa.identity.infra.SysUserMapper.selectUserById",
                "SELECT id, name FROM sys_user u WHERE 1 = 1 AND /* @dataScope(table=sys_user, alias=u) */ AND u.id = ?");

        Invocation invocation = invocation(marked);
        interceptor.intercept(invocation);

        MappedStatement woven = (MappedStatement) invocation.getArgs()[0];
        String sql = woven.getBoundSql(null).getSql();
        assertThat(sql).doesNotContain("@dataScope");
        // 本人口径：self 分支为 (u.id = #{scope_uid})
        assertThat(sql).contains("(u.id = ?)");
        assertThat(woven.getId()).isEqualTo(marked.getId());
    }

    @Test
    @DisplayName("系统口径（DataScopeContext.system()）下的受控表查询不织入过滤，也不被裸查询拦截")
    void systemContextBypassesFilter() throws Throwable {
        DataScopeContext.set(DataScopeContext.system());
        MappedStatement bare = statement(SELECT_BY_ID, "SELECT id FROM sys_user WHERE id = ?");
        interceptor.intercept(invocation(bare));

        MappedStatement marked = statement(SELECT_BY_ID,
                "SELECT id FROM sys_user u WHERE 1 = 1 AND /* @dataScope(table=sys_user, alias=u) */");
        Invocation invocation = invocation(marked);
        interceptor.intercept(invocation);
        assertThat(((MappedStatement) invocation.getArgs()[0]).getBoundSql(null).getSql()).contains("1=1");
    }
}
