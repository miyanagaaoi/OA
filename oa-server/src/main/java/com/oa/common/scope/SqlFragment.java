package com.oa.common.scope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据域 SQL 片段（纯值对象）：SQL 文本 + 命名参数。
 *
 * <p>SQL 中的占位符形如 {@code #{scope_uid}}，由 {@link DataScopeInterceptor} 通过
 * MyBatis 的 additionalParameter 机制绑定（不拼字符串、不做字面量替换，杜绝注入）。
 */
public final class SqlFragment {

    /** 无过滤。 */
    public static final String SYSTEM_SQL = "1=1";

    /** 拒绝全表（无任何数据域可用时的安全默认值：宁可看不到，也不越权）。 */
    public static final String DENY_SQL = "1=0";

    private final String sql;
    private final Map<String, Object> params;
    private final boolean unrestricted;
    private final boolean deny;

    private SqlFragment(String sql, Map<String, Object> params, boolean unrestricted, boolean deny) {
        this.sql = sql;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.unrestricted = unrestricted;
        this.deny = deny;
    }

    public static SqlFragment of(String sql, Map<String, Object> params) {
        return new SqlFragment(sql, params == null ? Collections.emptyMap() : params, false, false);
    }

    public static SqlFragment of(String sql) {
        return of(sql, Collections.emptyMap());
    }

    public static SqlFragment all() {
        return new SqlFragment(SYSTEM_SQL, Collections.emptyMap(), true, false);
    }

    public static SqlFragment deny() {
        return new SqlFragment(DENY_SQL, Collections.emptyMap(), false, true);
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    /** 是否无过滤（{@code 1=1}）。 */
    public boolean isUnrestricted() {
        return unrestricted;
    }

    /** 是否全拒绝（{@code 1=0}）。 */
    public boolean isDeny() {
        return deny;
    }

    @Override
    public String toString() {
        return sql + (params.isEmpty() ? "" : " :: " + params);
    }
}
