package com.oa.admin.bulk;

import com.oa.common.scope.DataScopeContext;
import com.oa.identity.domain.SysOrg;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入的**逐行数据域闸门**（fail-closed）。
 *
 * <p>口径（施工要求的硬约束）：系统管理员可导入全量；分公司管理员的数据域由
 * {@link DataScopeContext} 描述（本公司子树 / 本部门子树 / 归口部门子树），
 * **数据域外的组织或人员行一律拒绝**（错误码 {@code E-XXX-020}），而不是"跳过该行继续导入"
 * —— 跳过会让调用方以为导入成功，属静默放行。
 *
 * <p>实现要点：导入的数据库读取在**系统口径**下执行（否则分公司管理员根本看不到域外组织，
 * 会得到"路径不存在"这类误导性错误）；授权判定集中在本类，逐行显式执行。
 */
public final class ImportScopeGuard {

    private final boolean allowAll;

    private final List<String> pathPrefixes;

    private ImportScopeGuard(boolean allowAll, List<String> pathPrefixes) {
        this.allowAll = allowAll;
        this.pathPrefixes = List.copyOf(pathPrefixes);
    }

    /** 允许一切（系统管理员 / 系统口径）。 */
    public static ImportScopeGuard allowAll() {
        return new ImportScopeGuard(true, List.of());
    }

    /**
     * 由请求上下文构造。
     *
     * @param context 请求上下文（**原始**，不是系统口径）
     * @param orgsById 组织索引（用于把 {@code companyId} 折算为路径前缀）
     */
    public static ImportScopeGuard of(DataScopeContext context, Map<Long, SysOrg> orgsById) {
        if (context == null) {
            return new ImportScopeGuard(false, List.of());
        }
        if (context.isBypass()) {
            return new ImportScopeGuard(true, List.of());
        }
        Set<String> prefixes = new LinkedHashSet<>();
        addPrefix(prefixes, context.getDeptPathPrefix());
        if (context.getCompanyId() != null && orgsById != null) {
            SysOrg company = orgsById.get(context.getCompanyId());
            if (company != null) {
                addPrefix(prefixes, company.getPath());
            }
        }
        addPrefix(prefixes, context.getFinanceDeptPathPrefix());
        return new ImportScopeGuard(false, new ArrayList<>(prefixes));
    }

    /** 该组织路径是否在导入人数据域内。 */
    public boolean allowsPath(String orgPath) {
        if (allowAll) {
            return true;
        }
        if (orgPath == null || orgPath.isBlank()) {
            return false;
        }
        String path = normalize(orgPath);
        for (String prefix : pathPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 该节点是否在导入人数据域内。 */
    public boolean allows(SysOrg org) {
        return org != null && allowsPath(org.getPath());
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    /** 命中的数据域前缀（报告与日志用）。 */
    public List<String> pathPrefixes() {
        return pathPrefixes;
    }

    /**
     * 逐行断言：数据域外 → 追加 error 发现（错误码 {@code E-XXX-020}）。
     *
     * @return {@code true} 表示在域内（可继续后续校验）
     */
    public boolean require(ImportKind kind, String orgPath, int line, String column, String value,
                           List<ImportFinding> findings) {
        if (allowsPath(orgPath)) {
            return true;
        }
        findings.add(ImportFinding.error(kind.error("020"), kind.fileName(), line, column, value,
                "该行超出当前导入人的数据域（组织路径 " + orgPath + "），已按 fail-closed 拒绝整批导入"));
        return false;
    }

    private static void addPrefix(Set<String> prefixes, String path) {
        if (path != null && !path.isBlank()) {
            prefixes.add(normalize(path));
        }
    }

    /** 归一为 {@code /1/12/} 形态（含首尾斜杠），与 {@code DataScopeContext} 的口径一致。 */
    static String normalize(String path) {
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }
}
