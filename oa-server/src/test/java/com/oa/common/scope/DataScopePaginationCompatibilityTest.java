package com.oa.common.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.config.OaProperties;
import com.oa.common.security.CurrentUser;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 数据域织入 × MyBatis-Plus 分页的兼容性单测（无 DB、无 Spring 容器）。
 *
 * <p>真实性来自「原装插件链」：真实的 {@link MybatisPlusInterceptor} + {@link PaginationInnerInterceptor}
 * 与真实的 {@link DataScopeInterceptor} 按生产顺序（数据域在最外层）串联，末端挂一个只做记录的
 * {@link Executor}，捕获分页 **count 语句**与**主查询**真正要执行的 {@link BoundSql}。
 *
 * <p>断言两条语句都必须：{@code ?} 数量 == {@code parameterMappings} 数量；数据域过滤仍然存在；
 * 用 {@link DefaultParameterHandler} 逐位绑定时 1..N 每一号（数据域参数、业务参数、分页参数
 * {@code mybatis_plus_first}）都能取到值 —— 否则就是 {@code No value specified for parameter N}。
 */
class DataScopePaginationCompatibilityTest {

    private static final String MARKER = "/* @dataScope(table=sys_user, alias=u) */";
    private static final long UID = 7L;
    private static final long COMPANY_ID = 12L;
    private static final String DEPT_PATH = "/1/12/1351/";

    private final MybatisConfiguration configuration = new MybatisConfiguration();
    private final DataScopeTableRegistry registry = new DataScopeTableRegistry(new OaProperties());
    private final DataScopeInterceptor dataScopeInterceptor = new DataScopeInterceptor(registry);

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("IPage 分页：count 与主查询的占位符/映射全对齐，数据域参数与 mybatis_plus_first 均可取值")
    void pagedSelectAndGeneratedCountStayAligned() throws Throwable {
        authenticate(DataScopeType.SELF, DataScopeType.DEPT);

        String script = "<script>SELECT u.id, u.name FROM sys_user u WHERE 1 = 1 AND " + MARKER
                + "<if test=\"keyword != null\"> AND u.name LIKE CONCAT('%', #{keyword}, '%')</if>"
                + " ORDER BY u.id ASC</script>";
        Page<Object> page = new Page<>(1, 10);
        HashMap<String, Object> parameter = new StrictParamMap();
        parameter.put("keyword", "张");
        parameter.put("param1", page);

        List<Captured> executed = runPagedQuery(script, page, parameter);

        assertThat(executed).hasSize(2);
        Captured count = executed.get(0);
        Captured records = executed.get(1);

        // ---- count 语句（同样被织入，不能变成无过滤全表计数）
        assertThat(count.statement().getId()).endsWith("_mpCount");
        assertThat(count.boundSql().getSql()).containsIgnoringCase("count(");
        assertThat(count.boundSql().getSql()).contains("u.id = ?").contains("u.org_id IN");
        assertThat(properties(count.boundSql()))
                .containsExactly("scope_uid", "scope_dept_path_prefix", "keyword");
        assertThat(placeholders(count.boundSql().getSql())).isEqualTo(count.boundSql().getParameterMappings().size());
        assertThat(count.boundSql().getAdditionalParameter("scope_uid")).isEqualTo(UID);
        assertThat(bind(count, parameter)).containsExactly(1, 2, 3);

        // ---- 主查询（分页 LIMIT ? 由分页插件追加，必须与既有映射并存）
        assertThat(records.boundSql().getSql()).contains("u.id = ?").containsIgnoringCase("limit ?");
        assertThat(properties(records.boundSql()))
                .containsExactly("scope_uid", "scope_dept_path_prefix", "keyword", "mybatis_plus_first");
        assertThat(placeholders(records.boundSql().getSql()))
                .isEqualTo(records.boundSql().getParameterMappings().size());
        assertThat(records.boundSql().hasAdditionalParameter("mybatis_plus_first")).isTrue();
        assertThat(records.boundSql().getAdditionalParameter("mybatis_plus_first")).isEqualTo(10L);
        assertThat(bind(records, parameter)).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("深分页（offset != 0）：LIMIT ?,? 的两个分页参数与数据域参数共存且顺序正确")
    void deepPagingKeepsBothLimitParameters() throws Throwable {
        authenticate(DataScopeType.SELF);

        String script = "<script>SELECT u.id FROM sys_user u WHERE 1 = 1 AND " + MARKER + "</script>";
        Page<Object> page = new Page<>(3, 10);
        HashMap<String, Object> parameter = new StrictParamMap();
        parameter.put("param1", page);

        List<Captured> executed = runPagedQuery(script, page, parameter);
        Captured records = executed.get(1);

        assertThat(properties(records.boundSql()))
                .containsExactly("scope_uid", "mybatis_plus_first", "mybatis_plus_second");
        assertThat(placeholders(records.boundSql().getSql()))
                .isEqualTo(records.boundSql().getParameterMappings().size());
        assertThat(records.boundSql().getAdditionalParameter("mybatis_plus_first")).isEqualTo(20L);
        assertThat(records.boundSql().getAdditionalParameter("mybatis_plus_second")).isEqualTo(10L);
        assertThat(bind(records, parameter)).containsExactly(1, 2, 3);
    }

    // ------------------------------------------------------------------ 夹具

    /** 按生产顺序串联插件：数据域拦截器在最外层（后注册者在外层），分页拦截器在内层。 */
    private List<Captured> runPagedQuery(String script, Page<Object> page, HashMap<String, Object> parameter)
            throws Exception {
        Recorder recorder = new Recorder();
        MybatisPlusInterceptor mybatisPlus = new MybatisPlusInterceptor();
        mybatisPlus.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        Executor chained = (Executor) dataScopeInterceptor.plugin((Executor) mybatisPlus.plugin(recorder.executor));

        MappedStatement statement = new MappedStatement.Builder(configuration,
                "com.oa.identity.infra.SysUserMapper.selectUserPage",
                new XMLLanguageDriver().createSqlSource(configuration, script, Object.class),
                SqlCommandType.SELECT).build();
        chained.query(statement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
        return recorder.executed;
    }

    private void authenticate(DataScopeType... scopes) {
        Set<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
        Collections.addAll(set, scopes);
        CurrentUser principal = CurrentUser.of(UID, "u07", "赵庚", "A0007", 1351L, COMPANY_ID,
                new LinkedHashSet<>(Set.of("employee")), set, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(set)
                .deptPathPrefix(DEPT_PATH)
                .companyId(COMPANY_ID)
                .primaryOrgId(1351L)
                .build());
    }

    private static List<String> properties(BoundSql boundSql) {
        List<String> names = new ArrayList<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            names.add(mapping.getProperty());
        }
        return names;
    }

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

    private static List<Integer> bind(Captured captured, Object parameter) {
        List<Integer> boundIndexes = new ArrayList<>();
        PreparedStatement preparedStatement = (PreparedStatement) Proxy.newProxyInstance(
                DataScopePaginationCompatibilityTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("set") && args != null && args.length >= 2
                            && args[0] instanceof Integer index) {
                        boundIndexes.add(index);
                    }
                    return null;
                });
        new DefaultParameterHandler(captured.statement(), parameter, captured.boundSql())
                .setParameters(preparedStatement);
        return boundIndexes;
    }

    private record Captured(MappedStatement statement, BoundSql boundSql) {
    }

    /** 只做记录的 Executor：捕获分页插件与主流程真正下发的 BoundSql。 */
    private static final class Recorder {

        private final List<Captured> executed = new ArrayList<>();

        private final Executor executor = (Executor) Proxy.newProxyInstance(
                DataScopePaginationCompatibilityTest.class.getClassLoader(),
                new Class<?>[]{Executor.class},
                (proxy, method, args) -> {
                    if ("query".equals(method.getName()) && args != null && args.length == 6) {
                        MappedStatement statement = (MappedStatement) args[0];
                        executed.add(new Captured(statement, (BoundSql) args[5]));
                        // count 语句返回非空，否则 MP 的 continuePage 会短路掉主查询（等于生产上的空页）
                        return statement.getId().endsWith("_mpCount")
                                ? new ArrayList<>(List.of(100L))
                                : new ArrayList<>();
                    }
                    if ("query".equals(method.getName()) && args != null && args.length == 4) {
                        MappedStatement statement = (MappedStatement) args[0];
                        executed.add(new Captured(statement, statement.getBoundSql(args[1])));
                        return new ArrayList<>();
                    }
                    if ("createCacheKey".equals(method.getName())) {
                        return new CacheKey();
                    }
                    return null;
                });
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
