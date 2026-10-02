package com.oa.common.scope;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 数据域上下文（线程绑定）：鉴权阶段装载，SQL 织入阶段读取。
 *
 * <p>装载方：{@code AuthInterceptor}（HTTP 请求）→ {@code DataScopeResolver}；
 * 后台任务必须显式使用 {@link #system()} 或 {@link #withSystem()}，否则后台查询会
 * 命中 {@link DataScopeSqlBuilder#buildInstanceScope} 的「无上下文 → 无过滤」分支，
 * 属于受控行为但必须显式声明（见 {@code DataScopeInterceptor} 的告警日志）。
 *
 * <p>清理方：{@code AuthInterceptor#afterCompletion} 必须调用 {@link #clear()}，防止线程池串号。
 */
public final class DataScopeContext {

    private static final ThreadLocal<DataScopeContext> HOLDER = new ThreadLocal<>();

    private final CurrentUser principal;
    private final Set<String> roleCodes;
    private final Set<DataScopeType> scopes;
    private final Set<Long> roleScopeOrgIds;
    private final Set<String> categories;
    private final String deptPathPrefix;
    private final String financeDeptPathPrefix;
    private final Long primaryOrgId;
    private final Long companyId;
    private final Long financeDeptId;
    private final boolean financeOwnerDeptBranchEnabled;
    private final boolean system;

    private DataScopeContext(Builder builder) {
        this.principal = builder.principal;
        this.roleCodes = immutable(builder.roleCodes);
        this.scopes = builder.scopes == null || builder.scopes.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(DataScopeType.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.scopes));
        this.roleScopeOrgIds = immutable(builder.roleScopeOrgIds);
        this.categories = immutable(builder.categories);
        this.deptPathPrefix = normalizePath(builder.deptPathPrefix);
        this.financeDeptPathPrefix = normalizePath(builder.financeDeptPathPrefix);
        this.primaryOrgId = builder.primaryOrgId;
        this.companyId = builder.companyId;
        this.financeDeptId = builder.financeDeptId;
        this.financeOwnerDeptBranchEnabled = builder.financeOwnerDeptBranchEnabled;
        this.system = builder.system;
    }

    private static <T> Set<T> immutable(Set<T> source) {
        return source == null || source.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /** 组织路径统一形如 {@code /1/12/135/}（含首尾斜杠），便于 LIKE 前缀匹配。 */
    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    // ------------------------------------------------------------------ ThreadLocal

    public static void set(DataScopeContext context) {
        HOLDER.set(context);
    }

    /** 当前上下文，可能为 {@code null}（未认证请求 / MyBatis 初始化阶段）。 */
    public static DataScopeContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前上下文，缺失即抛 401（业务代码需上下文时应使用本方法）。 */
    public static DataScopeContext require() {
        DataScopeContext context = HOLDER.get();
        if (context == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return context;
    }

    /** 后台任务用的系统口径（无过滤 + 不做裸查询拦截）。 */
    public static DataScopeContext system() {
        return builder().system(true).scopes(EnumSet.of(DataScopeType.GROUP_ALL)).build();
    }

    /** 在现有上下文基础上追加系统标记（返回新对象，不修改 ThreadLocal）。 */
    public DataScopeContext withSystem() {
        return toBuilder().system(true).build();
    }

    public Builder toBuilder() {
        return builder()
                .principal(principal)
                .roleCodes(roleCodes)
                .scopes(scopes)
                .roleScopeOrgIds(roleScopeOrgIds)
                .categories(categories)
                .deptPathPrefix(deptPathPrefix)
                .financeDeptPathPrefix(financeDeptPathPrefix)
                .primaryOrgId(primaryOrgId)
                .companyId(companyId)
                .financeDeptId(financeDeptId)
                .financeOwnerDeptBranchEnabled(financeOwnerDeptBranchEnabled)
                .system(system);
    }

    // ------------------------------------------------------------------ 语义

    /** 是否跳过数据域过滤（系统任务，或含 group_all 口径）。 */
    public boolean isBypass() {
        return system || scopes.contains(DataScopeType.GROUP_ALL);
    }

    public Optional<DataScopeType> widestScope() {
        return DataScopeType.widest(scopes);
    }

    /** 当前用户 id；缺失为 {@code null}。 */
    public Long getUserId() {
        return principal == null ? null : principal.id();
    }

    /** 当前用户 id，缺失即抛 401。 */
    public Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public String getUserName() {
        return principal == null ? null : principal.name();
    }

    /** 组织路径前缀的 LIKE 实参：{@code /1/12/135/%}；无前缀返回 {@code null}。 */
    public String deptPathLike() {
        return deptPathPrefix == null ? null : deptPathPrefix + "%";
    }

    /** 归口部门（集团财务部）组织路径的 LIKE 实参。 */
    public String financeDeptPathLike() {
        return financeDeptPathPrefix == null ? null : financeDeptPathPrefix + "%";
    }

    // ------------------------------------------------------------------ getters

    public CurrentUser getPrincipal() {
        return principal;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public Set<DataScopeType> getScopes() {
        return scopes;
    }

    public Set<Long> getRoleScopeOrgIds() {
        return roleScopeOrgIds;
    }

    /** 集团分管领导按分管业务线绑定的类别集合（{@code sys_role_category}）。 */
    public Set<String> getCategories() {
        return categories;
    }

    public String getDeptPathPrefix() {
        return deptPathPrefix;
    }

    public String getFinanceDeptPathPrefix() {
        return financeDeptPathPrefix;
    }

    public Long getPrimaryOrgId() {
        return primaryOrgId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getFinanceDeptId() {
        return financeDeptId;
    }

    /**
     * 是否启用财务部「归口部门」分支（{@code owner_dept_id = :finance_dept_id}）。
     *
     * <p><b>默认关闭</b>：{@code flow_instance.owner_dept_id} 在 data-model.md 中定义为
     * 「归口部门，恒为集团财务部」，若无条件织入该分支，等于对财务部放开全部单据，
     * 与 PRD §5.3「不涉及费用且未经流转的事项单不可见」冲突。故作为可配置开关保留，
     * 仅在确认 owner_dept_id 承载了「真正归口类别」语义后才可打开。
     */
    public boolean isFinanceOwnerDeptBranchEnabled() {
        return financeOwnerDeptBranchEnabled;
    }

    public boolean isSystem() {
        return system;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 建造者。 */
    public static final class Builder {

        private CurrentUser principal;
        private Set<String> roleCodes;
        private Set<DataScopeType> scopes;
        private Set<Long> roleScopeOrgIds;
        private Set<String> categories;
        private String deptPathPrefix;
        private String financeDeptPathPrefix;
        private Long primaryOrgId;
        private Long companyId;
        private Long financeDeptId;
        private boolean financeOwnerDeptBranchEnabled;
        private boolean system;

        public Builder principal(CurrentUser principal) {
            this.principal = principal;
            return this;
        }

        public Builder roleCodes(Set<String> roleCodes) {
            this.roleCodes = roleCodes;
            return this;
        }

        public Builder scopes(Set<DataScopeType> scopes) {
            // 注意：EnumSet.copyOf 对「空且非 EnumSet」的集合会抛 IllegalArgumentException，必须先判空
            this.scopes = (scopes == null || scopes.isEmpty()) ? null : EnumSet.copyOf(scopes);
            return this;
        }

        public Builder roleScopeOrgIds(Set<Long> roleScopeOrgIds) {
            this.roleScopeOrgIds = roleScopeOrgIds;
            return this;
        }

        public Builder categories(Set<String> categories) {
            this.categories = categories;
            return this;
        }

        public Builder deptPathPrefix(String deptPathPrefix) {
            this.deptPathPrefix = deptPathPrefix;
            return this;
        }

        public Builder financeDeptPathPrefix(String financeDeptPathPrefix) {
            this.financeDeptPathPrefix = financeDeptPathPrefix;
            return this;
        }

        public Builder primaryOrgId(Long primaryOrgId) {
            this.primaryOrgId = primaryOrgId;
            return this;
        }

        public Builder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public Builder financeDeptId(Long financeDeptId) {
            this.financeDeptId = financeDeptId;
            return this;
        }

        public Builder financeOwnerDeptBranchEnabled(boolean financeOwnerDeptBranchEnabled) {
            this.financeOwnerDeptBranchEnabled = financeOwnerDeptBranchEnabled;
            return this;
        }

        public Builder system(boolean system) {
            this.system = system;
            return this;
        }

        public DataScopeContext build() {
            return new DataScopeContext(this);
        }
    }
}
