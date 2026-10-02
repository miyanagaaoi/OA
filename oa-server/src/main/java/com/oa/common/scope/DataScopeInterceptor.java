package com.oa.common.scope;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据域织入拦截器 —— <b>唯一入口</b>（doc/tech-design.md §5.3）。
 *
 * <p>工作方式（不解析 SQL 语法树，用「标记 + 强校验」保证不可绕过）：
 * <ol>
 *   <li>Mapper XML 在 WHERE 处写标记注释 {@code /* @dataScope(table=flow_instance, alias=i) *}{@code /}；</li>
 *   <li>本拦截器把标记替换为 {@link DataScopeSqlBuilder} 的**纯函数**产物，
 *       片段中的 {@code #{scope_*}} 通过 MyBatis additionalParameter 绑定（不做字符串拼接，杜绝注入）；</li>
 *   <li>受控表（{@link DataScopeTableRegistry}）上的 SELECT 若**没有**标记，直接抛
 *       {@link ErrorCode#DATA_SCOPE_MISSING} 拦截 —— 对应评审阻断项「禁止手写绕过过滤的裸查询」；</li>
 *   <li>未认证上下文（登录前查询）与显式系统上下文（后台任务 {@link DataScopeContext#system()}）不受第 3 条约束，
 *       但仍会打印日志留痕。</li>
 * </ol>
 *
 * <p>实现说明：只拦截 4 参数的 {@code Executor#query}（MyBatis 的公共入口），
 * 通过「重建 MappedStatement + 包装 SqlSource」注入片段与参数，因此分页、缓存键、动态 SQL 均按原语义工作。
 * 已知优化点（阶段 2）：按 (statementId, 数据域签名) 缓存重建结果，避免每次查询都复制 MappedStatement。
 *
 * <p><b>插件顺序（重要）</b>：MyBatis 的 {@code pluginAll} 是「后注册者在外层」，本拦截器标了
 * {@link org.springframework.core.annotation.Order}{@code (LOWEST_PRECEDENCE)}，确保它**先于**
 * MyBatis-Plus 的分页拦截器执行：否则分页拦截器会用 JSqlParser 解析尚未替换的标记注释，
 * 遇到「悬空的 AND」会解析失败。同一原因，新增任何 Executor 级插件时都必须保持本拦截器在最外层。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DataScopeInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DataScopeInterceptor.class);

    /** 标记：/* @dataScope(table=flow_instance, alias=i) *&#47; */
    private static final Pattern MARKER = Pattern.compile("/\\*\\s*@dataScope\\(([^)]*)\\)\\s*\\*/");

    private static final Pattern TABLE_ATTR = Pattern.compile("table\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ALIAS_ATTR = Pattern.compile("alias\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * 框架生成的 MappedStatement 后缀，一律免裸查询拦截。
     *
     * <p>原因：MyBatis-Plus 分页的 count 语句（{@code <id>_mpCount}）由**已完成织入**的 SQL 派生，
     * 语句里不再有 {@code @dataScope} 标记，但仍然是受控表的 SELECT；若不豁免会被误判为裸查询。
     * 若后续升级 MyBatis-Plus 导致命名变化，需同步扩展本清单。
     */
    private static final List<String> GENERATED_STATEMENT_SUFFIXES = List.of(
            "_mpCount", "_count", "_COUNT", "_mpSelect", "!selectKey"
    );

    private final DataScopeTableRegistry registry;

    public DataScopeInterceptor(DataScopeTableRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement statement)) {
            return invocation.proceed();
        }
        Object parameterObject = args.length > 1 ? args[1] : null;
        BoundSql boundSql = statement.getBoundSql(parameterObject);
        String sql = boundSql.getSql();
        DataScopeContext context = DataScopeContext.current();

        Matcher marker = MARKER.matcher(sql);
        if (!marker.find()) {
            assertScopedSelect(statement, sql, context);
            return invocation.proceed();
        }

        String attributes = marker.group(1);
        String table = readAttribute(attributes, TABLE_ATTR);
        String aliasAttribute = readAttribute(attributes, ALIAS_ATTR);
        DataScopeTableRegistry.Entry entry = table == null ? null : registry.find(table).orElse(null);
        DataScopeKind kind = entry == null ? DataScopeKind.INSTANCE : entry.kind();
        String alias = aliasAttribute != null
                ? aliasAttribute
                : (entry == null ? DataScopeSqlBuilder.DEFAULT_INSTANCE_ALIAS : entry.alias());

        SqlFragment fragment = DataScopeSqlBuilder.build(context, kind, alias);
        if (context == null) {
            log.warn("受控查询在未装载数据域上下文时执行，按系统口径（无过滤）：statement={} table={}",
                    statement.getId(), table);
        } else if (context.isBypass()) {
            log.debug("系统/无过滤口径执行：statement={} table={} scopes={}", statement.getId(), table, context.getScopes());
        }

        String scopedSql = marker.replaceFirst(Matcher.quoteReplacement(fragment.getSql()));
        Map<String, Object> additionalParameters = new LinkedHashMap<>(readAdditionalParameters(boundSql));
        additionalParameters.putAll(fragment.getParams());

        SqlSource delegate = statement.getLang().createSqlSource(statement.getConfiguration(), scopedSql, Object.class);
        SqlSource scopedSource = new ScopeAwareSqlSource(delegate, additionalParameters);
        args[0] = copyWithSqlSource(statement, scopedSource);
        return invocation.proceed();
    }

    /** 受控表的 SELECT 必须带标记；否则以「评审阻断项」语义直接拒绝。 */
    private void assertScopedSelect(MappedStatement statement, String sql, DataScopeContext context) {
        if (context == null || context.isSystem() || !registry.isEnforceUnmarkedSelect()) {
            return;
        }
        if (statement.getSqlCommandType() != SqlCommandType.SELECT) {
            return;
        }
        if (registry.isExempt(statement.getId()) || isGeneratedStatement(statement.getId())) {
            return;
        }
        if (!registry.scopedTablePattern().matcher(sql).find()) {
            return;
        }
        throw new BizException(ErrorCode.DATA_SCOPE_MISSING,
                "MappedStatement " + statement.getId() + " 查询了受控表但未织入数据域过滤（缺少 /* @dataScope(...) */ 标记）");
    }

    /** 是否为框架生成语句（分页 count 等）。 */
    private static boolean isGeneratedStatement(String statementId) {
        if (statementId == null) {
            return true;
        }
        for (String suffix : GENERATED_STATEMENT_SUFFIXES) {
            if (statementId.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String readAttribute(String attributes, Pattern pattern) {
        Matcher matcher = pattern.matcher(attributes);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 复制 MappedStatement（保留结果映射/缓存/超时等设置），仅替换 SqlSource。 */
    private static MappedStatement copyWithSqlSource(MappedStatement statement, SqlSource sqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(
                statement.getConfiguration(),
                statement.getId(),
                sqlSource,
                statement.getSqlCommandType());
        builder.resource(statement.getResource());
        builder.fetchSize(statement.getFetchSize());
        builder.timeout(statement.getTimeout());
        builder.statementType(statement.getStatementType());
        builder.keyGenerator(statement.getKeyGenerator());
        if (statement.getKeyProperties() != null && statement.getKeyProperties().length > 0) {
            builder.keyProperty(String.join(",", statement.getKeyProperties()));
        }
        if (statement.getKeyColumns() != null && statement.getKeyColumns().length > 0) {
            builder.keyColumn(String.join(",", statement.getKeyColumns()));
        }
        if (statement.getResultSets() != null && statement.getResultSets().length > 0) {
            builder.resultSets(String.join(",", statement.getResultSets()));
        }
        builder.resultMaps(statement.getResultMaps());
        builder.resultSetType(statement.getResultSetType());
        builder.cache(statement.getCache());
        builder.flushCacheRequired(statement.isFlushCacheRequired());
        builder.useCache(statement.isUseCache());
        builder.resultOrdered(readResultOrdered(statement));
        builder.databaseId(statement.getDatabaseId());
        builder.lang(statement.getLang());
        return builder.build();
    }

    /** {@code resultOrdered} 为 Boolean 包装类型，直接取 getter 可能拆箱 NPE，故反射读取并兜底为 false。 */
    private static boolean readResultOrdered(MappedStatement statement) {
        try {
            Object value = SystemMetaObject.forObject(statement).getValue("resultOrdered");
            return value instanceof Boolean ordered && ordered;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 读取原 BoundSql 的 additionalParameters（foreach 生成的 __frch_* 等），重建后必须保留。 */
    private static Map<String, Object> readAdditionalParameters(BoundSql boundSql) {
        try {
            Object value = SystemMetaObject.forObject(boundSql).getValue("additionalParameters");
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, item) -> copy.put(String.valueOf(key), item));
                return copy;
            }
        } catch (Exception ex) {
            log.debug("读取 BoundSql.additionalParameters 失败（忽略）：{}", ex.getMessage());
        }
        return Collections.emptyMap();
    }

    /** 把数据域参数以 additionalParameter 形式注入（MyBatis 优先读 additionalParameter，再读 parameterObject）。 */
    private static final class ScopeAwareSqlSource implements SqlSource {

        private final SqlSource delegate;
        private final Map<String, Object> parameters;

        private ScopeAwareSqlSource(SqlSource delegate, Map<String, Object> parameters) {
            this.delegate = delegate;
            this.parameters = parameters;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            BoundSql boundSql = delegate.getBoundSql(parameterObject);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
            }
            return boundSql;
        }
    }
}
