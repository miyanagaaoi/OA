package com.oa.authz.app;

import com.oa.authz.domain.SysRole;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 角色码目录 —— <b>纯函数</b>。
 *
 * <p><b>权威源</b>：{@code doc/data-model.md} §3.1 {@code sys_role.code} 的**列注释**
 * （import-spec §2.3 / §3.6 / T-16 已定稿「该列注释即角色码的唯一权威定义」）：
 * {@code admin}/{@code company_admin}/{@code employee}/{@code dept_leader}/{@code branch_leader}/
 * {@code subsidiary_gm}/{@code finance_owner}/{@code group_leader}/{@code chairman}。
 *
 * <p>这 9 个码是**受保护码**：
 * <ul>
 *   <li>{@code POST /api/v1/authz/roles} 不得用它们新建（库中已由初始化脚本创建，重复即 409）；</li>
 *   <li>不可删除（系统管理员也不可）；</li>
 *   <li>不可修改 {@code code} 与 {@code role_scope}（可改名称 / 数据域 / 权限 / 类别）。</li>
 * </ul>
 *
 * <p>注意：normify 基线 {@code oa.authz.rbac.role} 的模块描述里还残留旧码
 * （{@code gm} / {@code group_dept_leader} / {@code group_exec}）—— 已作废，以 DDL 列注释为准
 * （见交付说明的「文档不一致」清单，只列不改）。
 */
public final class RoleCatalog {

    /** 9 个内置角色码（顺序与 DDL 列注释一致）。 */
    public static final List<String> BUILT_IN_CODES = List.of(
            "admin",
            "company_admin",
            "employee",
            "dept_leader",
            "branch_leader",
            "subsidiary_gm",
            "finance_owner",
            "group_leader",
            "chairman");

    /** 角色码格式（import-spec `enums.md` §1.1：小写蛇形）。 */
    public static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");

    private static final Set<String> BUILT_IN = Set.copyOf(new LinkedHashSet<>(BUILT_IN_CODES));

    private RoleCatalog() {
    }

    public static Set<String> builtInCodes() {
        return BUILT_IN;
    }

    public static boolean isBuiltIn(String code) {
        return code != null && BUILT_IN.contains(code.trim().toLowerCase(Locale.ROOT));
    }

    /** 角色码格式校验（400）；空值、大写、连字符、超长一律拒绝。 */
    public static String requireValidCode(String code) {
        String normalized = code == null ? null : code.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || !CODE_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "角色码格式不合法（小写蛇形 ^[a-z][a-z0-9_]{1,31}$）：" + code);
        }
        return normalized;
    }

    /** {@code role_scope} 取值校验（400）。 */
    public static String requireValidRoleScope(String roleScope) {
        String normalized = roleScope == null ? null : roleScope.trim().toLowerCase(Locale.ROOT);
        if (!SysRole.SCOPE_GROUP.equals(normalized) && !SysRole.SCOPE_COMPANY.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "role_scope 仅允许 group|company：" + roleScope);
        }
        return normalized;
    }

    /** 内置码不得通过接口新建（409，指向 uk_sys_role_code）。 */
    public static void requireNotBuiltInForCreate(String code) {
        if (isBuiltIn(code)) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "内置角色码 " + code + " 由系统初始化，不得通过接口创建（uk_sys_role_code 冲突）");
        }
    }
}
