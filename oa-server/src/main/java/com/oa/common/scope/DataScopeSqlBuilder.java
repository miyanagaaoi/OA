package com.oa.common.scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据域 SQL 片段生成器 —— <b>纯函数</b>：输入 {@link DataScopeContext} + 表别名，输出
 * {@link SqlFragment}（SQL 文本 + 命名参数）。不依赖 Spring 容器、不依赖数据库、无副作用，
 * 因此可以被 {@code DataScopeSqlBuilderTest} 全量断言（doc/tech-design.md §5.3 第 2 条）。
 *
 * <p>口径出处：doc/data-model.md §7.2 伪 SQL + doc/prd-0.1.md §5.3。
 * 生成的片段一律用一层括号包住，调用方以 {@code AND <片段>} 织入（见 {@link DataScopeInterceptor}）。
 *
 * <h2>实例类（flow_instance 及其从表）</h2>
 * <ul>
 *   <li><b>self</b>：本人发起 ∪ 本人作为审批人（含 {@code origin_assignee_id}）∪ 抄送人；</li>
 *   <li><b>dept</b>：self ∪ {@code initiator_org_path LIKE :deptPathPrefix}；</li>
 *   <li><b>company</b>：{@code initiator_company_id = :companyId}；</li>
 *   <li><b>group_category</b>（财务部）：self ∪ 归口类别（fund/contract/seal）∪
 *       事项单且 {@code involve_cost = true} ∪ {@code EXISTS(flow_routing → 本部门)} ∪ 在途承接本部门。
 *       <b>「不涉及费用」且未经流转的事项单不可见</b>；</li>
 *   <li><b>group_all</b>：无过滤。</li>
 * </ul>
 *
 * <h2>用户类（sys_user / 通讯录）</h2>
 * <p>见 {@link #buildUserScope(DataScopeContext, String)}：本人、本部门子树（按
 * {@code sys_org.path} 前缀）、本公司、归口部门子树。
 */
public final class DataScopeSqlBuilder {

    /** 实例类默认别名（与 data-model.md §7.2 的 {@code i} 一致）。 */
    public static final String DEFAULT_INSTANCE_ALIAS = "i";

    /** 用户类默认别名。 */
    public static final String DEFAULT_USER_ALIAS = "u";

    // ---- 命名参数键（前缀 scope_ 便于 MyBatis 以 additionalParameter 绑定，避免与业务参数撞名）----
    public static final String P_UID = "scope_uid";
    public static final String P_DEPT_PATH = "scope_dept_path_prefix";
    public static final String P_COMPANY = "scope_company_id";
    public static final String P_FINANCE_DEPT = "scope_finance_dept_id";
    public static final String P_FINANCE_DEPT_PATH = "scope_finance_dept_path_prefix";

    // ---------------------------------------------------------------- 实例类片段模板

    /** self：本人发起 ∪ 本人审批（含转办/改派前的原处理人）∪ 抄送人。 */
    static final String SELF_SQL_TEMPLATE =
            "(%1$s.initiator_id = #{" + P_UID + "}"
                    + " OR EXISTS (SELECT 1 FROM flow_task t WHERE t.instance_id = %1$s.id"
                    + " AND (t.assignee_id = #{" + P_UID + "} OR t.origin_assignee_id = #{" + P_UID + "}))"
                    + " OR EXISTS (SELECT 1 FROM flow_cc c WHERE c.instance_id = %1$s.id AND c.user_id = #{" + P_UID + "}))";

    /** dept：self ∪ 发起人组织路径前缀（子树）。 */
    static final String DEPT_SQL_TEMPLATE =
            "(%1$s.initiator_org_path LIKE #{" + P_DEPT_PATH + "})";

    /** company：发起人所属公司。 */
    static final String COMPANY_SQL_TEMPLATE =
            "%1$s.initiator_company_id = #{" + P_COMPANY + "}";

    /** 财务部 ①：归口部门（默认关闭，见 DataScopeContext#isFinanceOwnerDeptBranchEnabled 的说明）。 */
    static final String FINANCE_OWNER_DEPT_SQL_TEMPLATE =
            "%1$s.owner_dept_id = #{" + P_FINANCE_DEPT + "}";

    /** 财务部 ②：归口类别（资金 / 合同 / 印鉴证照，恒经节点②）。 */
    static final String FINANCE_CATEGORY_SQL_TEMPLATE =
            "%1$s.form_type IN ('fund','contract','seal')";

    /**
     * 财务部 ③：事项单且「涉及费用＝是」（经节点②）。
     *
     * <p>用相关子查询而非直接引用 {@code f.fields_json}，避免强依赖外层查询是否 JOIN 了
     * {@code form_data}；{@code JSON_UNQUOTE(...) IN ('true','1')} 兼容 JSON 布尔 true 与数字 1
     * （doc/forms.md §1 中 {@code involve_cost} 为 boolean）。
     */
    static final String FINANCE_INVOLVE_COST_SQL_TEMPLATE =
            "(%1$s.form_type = 'matter' AND EXISTS (SELECT 1 FROM form_data f WHERE f.id = %1$s.form_data_id"
                    + " AND JSON_UNQUOTE(JSON_EXTRACT(f.fields_json, '$.involve_cost')) IN ('true','1')))";

    /** 财务部 ④：流转链承接给本部门（仅限该张单据，AC-22 不得折算为类别可见）。 */
    static final String FINANCE_ROUTING_SQL_TEMPLATE =
            "EXISTS (SELECT 1 FROM flow_routing r WHERE r.instance_id = %1$s.id AND r.to_dept_id = #{" + P_FINANCE_DEPT + "})";

    /** 财务部 ⑤：当前承接部门为本部门（在途）。 */
    static final String FINANCE_IN_FLIGHT_SQL_TEMPLATE =
            "%1$s.current_dept_id = #{" + P_FINANCE_DEPT + "}";

    // ---------------------------------------------------------------- 用户类片段模板

    static final String USER_SELF_SQL_TEMPLATE = "%1$s.id = #{" + P_UID + "}";

    static final String USER_DEPT_SQL_TEMPLATE =
            "%1$s.org_id IN (SELECT so.id FROM sys_org so WHERE so.path LIKE #{" + P_DEPT_PATH + "})";

    static final String USER_FINANCE_DEPT_SQL_TEMPLATE =
            "%1$s.org_id IN (SELECT so.id FROM sys_org so WHERE so.path LIKE #{" + P_FINANCE_DEPT_PATH + "})";

    static final String USER_COMPANY_SQL_TEMPLATE = "%1$s.company_id = #{" + P_COMPANY + "}";

    private DataScopeSqlBuilder() {
    }

    // ================================================================ 公共入口

    /** 按表类型分发（{@link DataScopeInterceptor} 的唯一调用入口）。 */
    public static SqlFragment build(DataScopeContext context, DataScopeKind kind, String alias) {
        DataScopeKind effective = kind == null ? DataScopeKind.INSTANCE : kind;
        if (effective == DataScopeKind.USER) {
            return buildUserScope(context, alias);
        }
        if (effective == DataScopeKind.NONE) {
            return SqlFragment.all();
        }
        return buildInstanceScope(context, alias);
    }

    /**
     * 实例类数据域片段。
     *
     * @param context 上下文；{@code null} 表示未装载（预认证查询 / 启动期），按无过滤处理，
     *                调用方（拦截器）会打印 WARN 并要求后台任务显式使用 {@link DataScopeContext#system()}
     * @param alias   外层 flow_instance 的别名
     */
    public static SqlFragment buildInstanceScope(DataScopeContext context, String alias) {
        if (context == null) {
            return SqlFragment.all();
        }
        if (context.isBypass()) {
            return SqlFragment.all();
        }
        String a = normalizeAlias(alias, DEFAULT_INSTANCE_ALIAS);
        List<String> parts = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        for (DataScopeType type : DataScopeType.values()) {
            if (!context.getScopes().contains(type)) {
                continue;
            }
            if (type == DataScopeType.SELF) {
                parts.add(selfBranch(a));
                params.put(P_UID, context.requireUserId());
            } else if (type == DataScopeType.DEPT) {
                String like = context.deptPathLike();
                if (like == null) {
                    // 组织路径缺失 → 该口径无法安全生效，不静默放宽为全量
                    continue;
                }
                // data-model.md §7.2「部门负责人 = 上述 OR 路径前缀」：self 与路径同属该口径
                parts.add(selfBranch(a));
                parts.add(deptBranch(a));
                params.put(P_UID, context.requireUserId());
                params.put(P_DEPT_PATH, like);
            } else if (type == DataScopeType.COMPANY) {
                if (context.getCompanyId() == null) {
                    continue;
                }
                parts.add(companyBranch(a));
                params.put(P_COMPANY, context.getCompanyId());
            } else if (type == DataScopeType.GROUP_CATEGORY) {
                FinanceParts finance = financeBranch(context, a);
                parts.addAll(finance.sqlParts);
                params.putAll(finance.params);
            } else if (type == DataScopeType.GROUP_ALL) {
                return SqlFragment.all();
            }
        }
        if (parts.isEmpty()) {
            // 无任何可用口径 → 拒绝全表（安全默认值）
            return SqlFragment.deny();
        }
        // 必须整体加括号：调用方以 `AND <片段>` 织入，否则 OR 的优先级会击穿前置条件
        return SqlFragment.of("(" + String.join(" OR ", parts) + ")", params);
    }

    /** 实例类数据域片段（默认别名 {@code i}）。 */
    public static SqlFragment buildInstanceScope(DataScopeContext context) {
        return buildInstanceScope(context, DEFAULT_INSTANCE_ALIAS);
    }

    /**
     * 用户类（通讯录 {@code sys_user}）数据域片段：本人 ∪ 本部门子树 ∪ 本公司 ∪ 归口部门子树。
     */
    public static SqlFragment buildUserScope(DataScopeContext context, String alias) {
        if (context == null) {
            return SqlFragment.all();
        }
        if (context.isBypass()) {
            return SqlFragment.all();
        }
        String a = normalizeAlias(alias, DEFAULT_USER_ALIAS);
        List<String> parts = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        // 自读不变式（安全不变量）：任何登录用户都必须能看到「自己」这一行，且**不依赖角色数据域能否解析**。
        // 反例：角色 data_scope='company' 而 sys_user.company_id 为 NULL（DDL 允许）时，只按数据域拼装会退化为
        // deny(1=0)，导致 /auth/me、改密、水印等「读自己」的路径整体 401（对应 AC-44 水印与首登强制改密）。
        parts.add(userSelfBranch(a));
        params.put(P_UID, context.requireUserId());

        for (DataScopeType type : DataScopeType.values()) {
            if (!context.getScopes().contains(type)) {
                continue;
            }
            if (type == DataScopeType.SELF) {
                // 已在循环外无条件加入（自读不变式）
                continue;
            } else if (type == DataScopeType.DEPT) {
                String like = context.deptPathLike();
                if (like == null) {
                    continue;
                }
                parts.add(userDeptBranch(a));
                params.put(P_DEPT_PATH, like);
            } else if (type == DataScopeType.COMPANY) {
                if (context.getCompanyId() == null) {
                    continue;
                }
                parts.add(userCompanyBranch(a));
                params.put(P_COMPANY, context.getCompanyId());
            } else if (type == DataScopeType.GROUP_CATEGORY) {
                String like = context.financeDeptPathLike();
                if (like != null) {
                    parts.add(userFinanceDeptBranch(a));
                    params.put(P_FINANCE_DEPT_PATH, like);
                } else if (context.getCompanyId() != null) {
                    // 归口部门路径未知时退化为本公司，避免通讯录整页空白
                    parts.add(userCompanyBranch(a));
                    params.put(P_COMPANY, context.getCompanyId());
                }
            } else if (type == DataScopeType.GROUP_ALL) {
                return SqlFragment.all();
            }
        }
        if (parts.isEmpty()) {
            return SqlFragment.deny();
        }
        // 必须整体加括号（同上：调用方以 AND 织入）
        return SqlFragment.of("(" + String.join(" OR ", parts) + ")", params);
    }

    // ================================================================ 单口径片段（文档/测试可直接引用）

    public static String selfBranch(String alias) {
        return String.format(Locale.ROOT, SELF_SQL_TEMPLATE, alias);
    }

    public static String deptBranch(String alias) {
        return String.format(Locale.ROOT, DEPT_SQL_TEMPLATE, alias);
    }

    public static String companyBranch(String alias) {
        return String.format(Locale.ROOT, COMPANY_SQL_TEMPLATE, alias);
    }

    public static String financeCategoryBranch(String alias) {
        return String.format(Locale.ROOT, FINANCE_CATEGORY_SQL_TEMPLATE, alias);
    }

    public static String financeInvolveCostBranch(String alias) {
        return String.format(Locale.ROOT, FINANCE_INVOLVE_COST_SQL_TEMPLATE, alias);
    }

    public static String financeRoutingBranch(String alias) {
        return String.format(Locale.ROOT, FINANCE_ROUTING_SQL_TEMPLATE, alias);
    }

    public static String financeInFlightBranch(String alias) {
        return String.format(Locale.ROOT, FINANCE_IN_FLIGHT_SQL_TEMPLATE, alias);
    }

    public static String financeOwnerDeptBranch(String alias) {
        return String.format(Locale.ROOT, FINANCE_OWNER_DEPT_SQL_TEMPLATE, alias);
    }

    public static String userSelfBranch(String alias) {
        return String.format(Locale.ROOT, USER_SELF_SQL_TEMPLATE, alias);
    }

    public static String userDeptBranch(String alias) {
        return String.format(Locale.ROOT, USER_DEPT_SQL_TEMPLATE, alias);
    }

    public static String userFinanceDeptBranch(String alias) {
        return String.format(Locale.ROOT, USER_FINANCE_DEPT_SQL_TEMPLATE, alias);
    }

    public static String userCompanyBranch(String alias) {
        return String.format(Locale.ROOT, USER_COMPANY_SQL_TEMPLATE, alias);
    }

    // ================================================================ 内部

    private static String normalizeAlias(String alias, String fallback) {
        if (alias == null || alias.isBlank()) {
            return fallback;
        }
        String value = alias.trim();
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("非法的表别名：" + alias);
        }
        return value;
    }

    /** 财务部五路并集（self + 归口类别 + 涉及费用事项单 + 流转链 + 在途承接）。 */
    private static FinanceParts financeBranch(DataScopeContext context, String alias) {
        FinanceParts parts = new FinanceParts();
        parts.sqlParts.add(selfBranch(alias));
        parts.params.put(P_UID, context.requireUserId());
        parts.sqlParts.add(financeCategoryBranch(alias));
        parts.sqlParts.add(financeInvolveCostBranch(alias));
        Long financeDeptId = context.getFinanceDeptId();
        if (financeDeptId != null) {
            if (context.isFinanceOwnerDeptBranchEnabled()) {
                parts.sqlParts.add(financeOwnerDeptBranch(alias));
            }
            parts.sqlParts.add(financeRoutingBranch(alias));
            parts.sqlParts.add(financeInFlightBranch(alias));
            parts.params.put(P_FINANCE_DEPT, financeDeptId);
        }
        return parts;
    }

    private static final class FinanceParts {
        private final List<String> sqlParts = new ArrayList<>();
        private final Map<String, Object> params = new LinkedHashMap<>();
    }
}
