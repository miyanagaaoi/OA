package com.oa.common.scope;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
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
 * <h2>参数映射为什么必须「插入」而不是「重新解析 SQL」</h2>
 * <p>MyBatis 的 {@code SqlSource} 在构建 {@link BoundSql} 时，早已把 {@code #{...}} 解析成 JDBC 的 {@code ?}，
 * 并在 {@code parameterMappings} 里按出现顺序留下列表。因此执行期拿到的 {@code boundSql.getSql()}
 * 里只有 {@code ?}，没有 {@code #{}}。若把这段 SQL 当成脚本文本重新交给
 * {@code LanguageDriver#createSqlSource(...)} 解析，只有**新片段**的 {@code #{scope_*}} 会生成映射，
 * 原有业务参数（{@code #{limit}}、{@code #{keyword}}…）的映射会全部丢失；片段带参时更会出现
 * 「{@code ?} 数量 ≠ {@code parameterMappings} 数量」的错位，执行期表现为
 * {@code java.sql.SQLException: No value specified for parameter N}（MySQL 驱动 {@code checkParameterSet}）。
 *
 * <p>本实现因此只做三件事，且都在**原始 SqlSource 的产物**上增量完成：
 * <ol>
 *   <li>取原始 {@code BoundSql}（含全部既有 {@code parameterMappings} 与 additionalParameters）；
 *   <li>把标记置换成「{@code ?} 形态」的片段文本，并按「标记之前已有几个 {@code ?}」把片段的
 *       {@link ParameterMapping}（{@code Mode.IN}，property 名与 {@code #{scope_*}} 一致）**插入到映射列表的同一偏移**；
 *   <li>把 {@code scope_*} 以 {@code setAdditionalParameter} 注册（{@code DefaultParameterHandler} 优先读 additionalParameters）。
 * </ol>
 * 织入产物是一份全新的 {@link BoundSql}：既有映射列表（可能被 {@code RawSqlSource} 在所有调用间共享）不被就地修改，
 * 因此分页插件（{@code IPage} / {@code _mpCount} count 语句）继续在正确的 SQL 与映射上工作。
 *
 * <p>实现说明：只拦截 4 参数的 {@code Executor#query}（MyBatis 的公共入口），通过「重建 MappedStatement +
 * 包装**原始** SqlSource」注入片段与参数，因此分页、缓存键、动态 SQL 均按原语义工作。代价是包装后的 SqlSource
 * 每次求值都会让原始 SqlSource 再求值一次（动态 SQL 多算一遍，换来的是「每次都能拿到与本次 parameterObject
 * 完全对应的参数映射」）；已知优化点（阶段 2）：按 (statementId, 数据域签名) 缓存重建结果。
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

    /** 片段中的命名占位符（只接受 {@code #{...}}；{@code ${...}} 一律拒绝，杜绝拼接注入）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{\\s*([^},]*?)\\s*(?:,[^}]*)?}");

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
        ScopeFragment scopeFragment = ScopeFragment.parse(fragment);
        if (context == null) {
            log.warn("受控查询在未装载数据域上下文时执行，按系统口径（无过滤）：statement={} table={}",
                    statement.getId(), table);
        } else if (context.isBypass()) {
            log.debug("系统/无过滤口径执行：statement={} table={} scopes={}", statement.getId(), table, context.getScopes());
        }

        // 包装**原始** SqlSource：每次求值都在真实 BoundSql 上做插入式织入，完整保留既有参数映射与分页语义。
        SqlSource scopedSource = new ScopeAwareSqlSource(
                statement.getSqlSource(), statement.getConfiguration(), scopeFragment);
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

    /**
     * 统计数据域标记**之前**的 JDBC 占位符（{@code ?}）个数 —— 即片段映射应插入的偏移。
     *
     * <p>跳过字符串/标识符引用与注释：字面量里的 {@code ?} 不是占位符（MySQL 驱动自己的
     * `checkParameterSet` 也按语句语义计数），若计入会导致插入位置整体偏移。
     * 已知边界：MyBatis 是按文本把 {@code #{...}} 换成 {@code ?}，因此「写在字符串字面量里的
     * {@code #{...}}」属于本方法不计入、而 MyBatis 会计数的病态写法，本工程 Mapper 不使用。
     */
    static int countPlaceholdersBefore(String sql, int end) {
        int count = 0;
        int limit = Math.min(end, sql.length());
        int index = 0;
        while (index < limit) {
            char ch = sql.charAt(index);
            if (ch == '\'' || ch == '"' || ch == '`') {
                index = skipQuoted(sql, index, limit);
            } else if (ch == '-' && index + 1 < limit && sql.charAt(index + 1) == '-') {
                index += 2;
                while (index < limit && sql.charAt(index) != '\n') {
                    index++;
                }
            } else if (ch == '/' && index + 1 < limit && sql.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < limit && !(sql.charAt(index) == '*' && sql.charAt(index + 1) == '/')) {
                    index++;
                }
                index = Math.min(index + 2, limit);
            } else {
                if (ch == '?') {
                    count++;
                }
                index++;
            }
        }
        return count;
    }

    /** 跳过一段引号包裹的字面量/标识符，返回其后第一个下标（不越过 {@code limit}）。 */
    private static int skipQuoted(String sql, int start, int limit) {
        char quote = sql.charAt(start);
        int index = start + 1;
        while (index < limit) {
            char ch = sql.charAt(index);
            if (ch == '\\') {
                index += 2;
                continue;
            }
            if (ch == quote) {
                // SQL 里用双写表示转义的引号（''、""、``）
                if (index + 1 < limit && sql.charAt(index + 1) == quote) {
                    index += 2;
                    continue;
                }
                return index + 1;
            }
            index++;
        }
        return limit;
    }

    /**
     * 把数据域片段转成「{@code ?} 形态 + 有序参数名」，供执行期插入映射列表。
     *
     * <p>片段的 {@code #{...}} 必须逐个能在 {@link SqlFragment#getParams()} 里找到值：
     * 找不到就抛错（fail-closed），绝不放行一条带未绑定占位符的 SQL。
     */
    private record ScopeFragment(String sql, List<String> names, Map<String, Object> values) {

        static ScopeFragment parse(SqlFragment fragment) {
            String sql = fragment.getSql() == null ? "" : fragment.getSql();
            if (sql.contains("${")) {
                throw new IllegalStateException("数据域片段不允许使用 ${...} 拼接：" + sql);
            }
            Map<String, Object> params = fragment.getParams();
            List<String> names = new ArrayList<>();
            Map<String, Object> values = new LinkedHashMap<>();
            StringBuilder builder = new StringBuilder(sql.length());
            Matcher matcher = PLACEHOLDER.matcher(sql);
            int last = 0;
            while (matcher.find()) {
                String name = matcher.group(1) == null ? "" : matcher.group(1).trim();
                if (name.isEmpty() || !params.containsKey(name)) {
                    throw new IllegalStateException("数据域片段占位符缺少绑定值：#{" + name + "} in " + sql);
                }
                names.add(name);
                values.put(name, params.get(name));
                builder.append(sql, last, matcher.start()).append('?');
                last = matcher.end();
            }
            builder.append(sql, last, sql.length());
            return new ScopeFragment(builder.toString(), List.copyOf(names), Collections.unmodifiableMap(values));
        }
    }

    /**
     * 执行期织入：在原始 {@link BoundSql} 基础上替换标记、同偏移插入片段映射、注册 {@code scope_*} 参数。
     */
    private static final class ScopeAwareSqlSource implements SqlSource {

        private final SqlSource delegate;
        private final Configuration configuration;
        private final ScopeFragment fragment;

        private ScopeAwareSqlSource(SqlSource delegate, Configuration configuration, ScopeFragment fragment) {
            this.delegate = delegate;
            this.configuration = configuration;
            this.fragment = fragment;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            BoundSql original = delegate.getBoundSql(parameterObject);
            String sql = original.getSql();
            Matcher marker = MARKER.matcher(sql);
            if (!marker.find()) {
                // 拦截阶段已用同一 parameterObject 确认标记存在；这里再消失说明 SQL 求值不确定，
                // 按 fail-closed 拒绝，避免放出一条未经数据域过滤的受控表查询。
                throw new BizException(ErrorCode.DATA_SCOPE_MISSING,
                        "数据域标记在执行期消失（SqlSource 两次求值不一致），已按 fail-closed 拒绝执行");
            }

            int insertAt = countPlaceholdersBefore(sql, marker.start());
            // StaticSqlSource 允许 parameterMappings 为 null（MyBatis 自身也如此兜底），这里同样按空处理
            List<ParameterMapping> mappings = original.getParameterMappings() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(original.getParameterMappings());
            if (insertAt > mappings.size()) {
                throw new BizException(ErrorCode.DATA_SCOPE_MISSING,
                        "SQL 占位符与参数映射数量不一致（占位符 " + insertAt + " > 映射 " + mappings.size() + "），已拒绝执行");
            }
            for (int i = 0; i < fragment.names().size(); i++) {
                String name = fragment.names().get(i);
                Object value = fragment.values().get(name);
                ParameterMapping mapping = new ParameterMapping.Builder(
                        configuration, name, value == null ? Object.class : value.getClass())
                        .mode(ParameterMode.IN)
                        .build();
                mappings.add(insertAt + i, mapping);
            }

            String scopedSql = sql.substring(0, marker.start()) + fragment.sql() + sql.substring(marker.end());
            BoundSql scoped = new BoundSql(configuration, scopedSql, mappings, original.getParameterObject());
            // foreach 生成的 __frch_*、DynamicSqlSource 的 _parameter/_databaseId 等必须原样保留
            original.getAdditionalParameters().forEach(scoped::setAdditionalParameter);
            fragment.values().forEach(scoped::setAdditionalParameter);
            return scoped;
        }
    }
}
