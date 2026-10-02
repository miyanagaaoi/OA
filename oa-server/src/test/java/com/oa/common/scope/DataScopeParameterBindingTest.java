package com.oa.common.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据域织入后的**参数绑定一致性**单测（无 DB、无 Spring 容器）。
 *
 * <p>回归缺陷：{@code DataScopeInterceptor} 曾把执行期 SQL（此时 {@code #{...}} 早已被解析成 JDBC 的 {@code ?}）
 * 当成脚本重新交给 {@code LanguageDriver#createSqlSource} 解析，导致原有业务参数映射全部丢失；
 * 片段带参时更会出现「{@code ?} 数量 ≠ {@code parameterMappings} 数量」的错位，运行期表现为
 * {@code java.sql.SQLException: No value specified for parameter N}（MySQL 驱动 {@code checkParameterSet}）。
 *
 * <p>本测试用真实的 MyBatis {@link ParameterMapping}/{@link BoundSql} 与
 * {@link DefaultParameterHandler} 复刻执行期绑定动作，断言：
 * <ol>
 *   <li>织入后 SQL 里的 {@code ?} 数量 == {@code parameterMappings} 数量；</li>
 *   <li>片段参数 {@code scope_*} 位于**标记所在偏移**（其前的业务参数不被顶掉，其后保持相对顺序）；</li>
 *   <li>每个 {@code scope_*} 都能从 additionalParameters 取到值，业务参数仍能从 parameterObject 取到值；</li>
 *   <li>用 {@link DefaultParameterHandler} 逐位绑定时，1..N 每一号参数都被真正设置（= MySQL 的判定口径）。</li>
 * </ol>
 */
class DataScopeParameterBindingTest {

    private static final String MARKER = "/* @dataScope(table=sys_user, alias=u) */";
    private static final long UID = 7L;
    private static final long COMPANY_ID = 12L;
    private static final long ORG_ID = 1351L;
    private static final String DEPT_PATH = "/1/12/1351/";

    private final MybatisConfiguration configuration = new MybatisConfiguration();
    private final DataScopeTableRegistry registry = new DataScopeTableRegistry(new OaProperties());
    private final DataScopeInterceptor interceptor = new DataScopeInterceptor(registry);

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("片段参数插入到标记偏移：? 数量 == parameterMappings 数量，业务参数不丢失")
    void fragmentMappingsAreInsertedAtMarkerOffset() throws Throwable {
        authenticate(DataScopeType.SELF);
        String sql = "SELECT u.id, u.name FROM sys_user u WHERE 1 = 1 AND " + MARKER
                + " AND u.deleted_at IS NULL ORDER BY u.id ASC LIMIT #{limit}";
        Object parameter = param("limit", 50);

        MappedStatement woven = weave(sql, parameter);
        BoundSql boundSql = woven.getBoundSql(parameter);

        assertThat(boundSql.getSql()).doesNotContain("@dataScope");
        assertThat(boundSql.getSql()).contains("(u.id = ?)").contains("LIMIT ?");
        assertThat(properties(boundSql)).containsExactly("scope_uid", "limit");
        assertThat(placeholders(boundSql.getSql())).isEqualTo(boundSql.getParameterMappings().size());
        assertThat(boundSql.hasAdditionalParameter("scope_uid")).isTrue();
        assertThat(boundSql.getAdditionalParameter("scope_uid")).isEqualTo(UID);
        // MySQL 驱动的判定口径：execute 时 1..N 每一号都必须被显式 set
        assertThat(bind(woven, boundSql, parameter)).containsExactly(1, 2);
    }

    @Test
    @DisplayName("标记之前的业务参数仍在原位：映射顺序为「业务参数 → 片段参数 → 其余业务参数」")
    void businessParameterBeforeMarkerKeepsItsPosition() throws Throwable {
        authenticate(DataScopeType.SELF, DataScopeType.DEPT, DataScopeType.COMPANY);
        String sql = "SELECT u.id FROM sys_user u WHERE 1 = 1 AND u.company_id = #{companyId} AND " + MARKER
                + " AND u.status = #{status} LIMIT #{limit}";
        Object parameter = param("companyId", COMPANY_ID, "status", "active", "limit", 20);

        MappedStatement woven = weave(sql, parameter);
        BoundSql boundSql = woven.getBoundSql(parameter);
        String scoped = boundSql.getSql();

        assertThat(properties(boundSql)).containsExactly("companyId", "scope_uid", "scope_dept_path_prefix",
                "scope_company_id", "status", "limit");
        assertThat(placeholders(scoped)).isEqualTo(boundSql.getParameterMappings().size());
        // 偏移正确：company_id 的 ? 在片段之前，片段的 ? 在 status 之前
        assertThat(scoped.indexOf("u.company_id = ?")).isLessThan(scoped.indexOf("(u.id = ?"));
        assertThat(scoped.indexOf("(u.id = ?")).isLessThan(scoped.indexOf("u.status = ?"));
        // 每个片段参数都能从 additionalParameters 取到（DefaultParameterHandler 优先读 additionalParameters）
        assertThat(boundSql.getAdditionalParameter("scope_uid")).isEqualTo(UID);
        assertThat(boundSql.getAdditionalParameter("scope_dept_path_prefix")).isEqualTo(DEPT_PATH + "%");
        assertThat(boundSql.getAdditionalParameter("scope_company_id")).isEqualTo(COMPANY_ID);
        assertThat(bind(woven, boundSql, parameter)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("动态 SQL（<script>/<if>）路径：分支命中的业务参数与片段参数同样不错位")
    void dynamicSqlBranchesKeepAlignment() throws Throwable {
        authenticate(DataScopeType.SELF);
        String script = "<script>SELECT u.id FROM sys_user u WHERE 1 = 1 AND " + MARKER
                + "<if test=\"keyword != null\"> AND u.name LIKE CONCAT('%', #{keyword}, '%')</if>"
                + " LIMIT #{limit}</script>";

        // 分支未命中：只有 scope_uid + limit（Mapper 方法参数始终在参数表里，只是值为 null）
        Object withoutKeyword = param("keyword", null, "limit", 5);
        MappedStatement wovenWithout = weave(script, withoutKeyword);
        BoundSql without = wovenWithout.getBoundSql(withoutKeyword);
        assertThat(properties(without)).containsExactly("scope_uid", "limit");
        assertThat(placeholders(without.getSql())).isEqualTo(without.getParameterMappings().size());

        // 分支命中：keyword 插在片段之后、limit 之前
        Object withKeyword = param("keyword", "张", "limit", 5);
        MappedStatement woven = weave(script, withKeyword);
        BoundSql boundSql = woven.getBoundSql(withKeyword);
        assertThat(properties(boundSql)).containsExactly("scope_uid", "keyword", "limit");
        assertThat(placeholders(boundSql.getSql())).isEqualTo(boundSql.getParameterMappings().size());
        assertThat(bind(woven, boundSql, withKeyword)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("重复求值互不影响：不会在原 SqlSource 的共享映射列表上累加（每次都是全新的 BoundSql）")
    void repeatedEvaluationDoesNotAccumulateMappings() throws Throwable {
        authenticate(DataScopeType.SELF);
        String sql = "SELECT u.id FROM sys_user u WHERE 1 = 1 AND " + MARKER + " AND u.deleted_at IS NULL";
        Object parameter = param();
        MappedStatement woven = weave(sql, parameter);

        BoundSql first = woven.getBoundSql(parameter);
        BoundSql second = woven.getBoundSql(parameter);

        assertThat(first).isNotSameAs(second);
        assertThat(properties(first)).containsExactly("scope_uid");
        assertThat(properties(second)).containsExactly("scope_uid");
        assertThat(placeholders(first.getSql())).isEqualTo(1);
    }

    @Test
    @DisplayName("执行期标记消失 → fail-closed（拒绝执行，而不是放过未过滤查询）")
    void missingMarkerAtExecutionIsRejected() throws Throwable {
        authenticate(DataScopeType.SELF);
        String script = "<script>SELECT u.id FROM sys_user u WHERE 1 = 1"
                + "<if test=\"withMarker\"> AND " + MARKER + "</if></script>";
        // 拦截期带标记（决定织入）
        MappedStatement woven = weave(script, param("withMarker", true));

        // 执行期同一 statement 求值出「没有标记」的 SQL → 必须拒绝
        assertThatThrownBy(() -> woven.getBoundSql(param("withMarker", false)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DATA_SCOPE_MISSING));
    }

    @Test
    @DisplayName("偏移统计：字符串字面量 / 注释 / 引用标识符里的 ? 不计入占位符")
    void placeholderCountingIgnoresLiteralsAndComments() {
        assertThat(DataScopeInterceptor.countPlaceholdersBefore("WHERE a = ? AND b = 'x?y' AND c = ?", 50))
                .isEqualTo(2);
        assertThat(DataScopeInterceptor.countPlaceholdersBefore("WHERE a = ? /* what? */ AND b = ?", 50))
                .isEqualTo(2);
        assertThat(DataScopeInterceptor.countPlaceholdersBefore("WHERE a = ? -- what?\n AND b = ?", 50))
                .isEqualTo(2);
        assertThat(DataScopeInterceptor.countPlaceholdersBefore("WHERE a = 'it''s ?' AND b = ?", 50))
                .isEqualTo(1);
        assertThat(DataScopeInterceptor.countPlaceholdersBefore("WHERE a = \"?\" AND b = ?", 50))
                .isEqualTo(1);
        assertThat(DataScopeInterceptor.countPlaceholdersBefore("WHERE a = `?` AND b = ?", 50))
                .isEqualTo(1);
        // 只统计 end 之前的占位符（标记位置）
        String sql = "WHERE a = ? AND /* @dataScope(table=sys_user, alias=u) */ AND b = ?";
        assertThat(DataScopeInterceptor.countPlaceholdersBefore(sql, sql.indexOf("/*"))).isEqualTo(1);
    }

    @Test
    @DisplayName("系统口径（1=1 片段）：不引入任何参数，原有业务参数照旧绑定")
    void unrestrictedFragmentKeepsBusinessParameters() throws Throwable {
        DataScopeContext.set(DataScopeContext.system());
        String sql = "SELECT u.id FROM sys_user u WHERE 1 = 1 AND " + MARKER + " AND u.id = #{id} LIMIT #{limit}";
        Object parameter = param("id", 9L, "limit", 1);

        MappedStatement woven = weave(sql, parameter);
        BoundSql boundSql = woven.getBoundSql(parameter);

        assertThat(boundSql.getSql()).contains("AND 1=1 AND");
        assertThat(properties(boundSql)).containsExactly("id", "limit");
        assertThat(placeholders(boundSql.getSql())).isEqualTo(boundSql.getParameterMappings().size());
        assertThat(bind(woven, boundSql, parameter)).containsExactly(1, 2);
    }

    // ------------------------------------------------------------------ 夹具

    /** 与默认配置一致：admin 类账号为 group_all；员工为 self/dept/company。 */
    private void authenticate(DataScopeType... scopes) {
        Set<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
        Collections.addAll(set, scopes);
        CurrentUser principal = CurrentUser.of(UID, "u07", "赵庚", "A0007", ORG_ID, COMPANY_ID,
                new LinkedHashSet<>(Set.of("employee")), set, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(set)
                .deptPathPrefix(DEPT_PATH)
                .companyId(COMPANY_ID)
                .primaryOrgId(ORG_ID)
                .build());
    }

    /** 模拟 Mapper 已经解析过的 SQL（执行期 SqlSource 产物就是 {@code ?} 形态）并完成织入。 */
    private MappedStatement weave(String sql, Object parameter) throws Throwable {
        Method query = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        Invocation invocation = new Invocation(nullExecutor(), query,
                new Object[]{statement(sql), parameter, RowBounds.DEFAULT, null});
        interceptor.intercept(invocation);
        return (MappedStatement) invocation.getArgs()[0];
    }

    private MappedStatement statement(String sql) {
        return new MappedStatement.Builder(configuration, "com.oa.identity.infra.SysUserMapper.selectUserPage",
                new XMLLanguageDriver().createSqlSource(configuration, sql, Object.class), SqlCommandType.SELECT)
                .build();
    }

    private static Executor nullExecutor() {
        return (Executor) Proxy.newProxyInstance(DataScopeParameterBindingTest.class.getClassLoader(),
                new Class<?>[]{Executor.class}, (proxy, method, args) -> null);
    }

    /** 严格参数表：取不存在的参数即抛错（与 MyBatis 的 {@code MapperMethod.ParamMap} 同口径）。 */
    private static Object param(Object... keysAndValues) {
        HashMap<String, Object> map = new StrictParamMap();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static List<String> properties(BoundSql boundSql) {
        List<String> names = new ArrayList<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            names.add(mapping.getProperty());
        }
        return names;
    }

    /** SQL 文本里的 {@code ?} 个数（跳过单引号字面量；本测试的 SQL 只有 {@code '%'} 这类无 ? 字面量）。 */
    private static int placeholders(String sql) {
        int count = 0;
        int index = 0;
        while (index < sql.length()) {
            char ch = sql.charAt(index);
            if (ch == '\'') {
                index++;
                while (index < sql.length() && sql.charAt(index) != '\'') {
                    index++;
                }
            } else if (ch == '?') {
                count++;
            }
            index++;
        }
        return count;
    }

    /** 复刻执行期绑定：{@link DefaultParameterHandler} 逐位调用 PreparedStatement 的 setXxx。 */
    private static List<Integer> bind(MappedStatement statement, BoundSql boundSql, Object parameter) {
        List<Integer> boundIndexes = new ArrayList<>();
        PreparedStatement preparedStatement = (PreparedStatement) Proxy.newProxyInstance(
                DataScopeParameterBindingTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set") && args != null && args.length >= 2
                            && args[0] instanceof Integer index) {
                        boundIndexes.add(index);
                    }
                    return null;
                });
        new DefaultParameterHandler(statement, parameter, boundSql).setParameters(preparedStatement);
        return boundIndexes;
    }

    private static final class StrictParamMap extends HashMap<String, Object> {

        @Override
        public Object get(Object key) {
            if (!containsKey(key)) {
                throw new IllegalStateException("Parameter '" + key + "' not found. Available parameters are " + keySet());
            }
            return super.get(key);
        }
    }
}
