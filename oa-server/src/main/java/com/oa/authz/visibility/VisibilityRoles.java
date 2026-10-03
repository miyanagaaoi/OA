package com.oa.authz.visibility;

/**
 * 字段级限制用到的角色码常量（口径来源 doc/prd-0.1.md §5.3「字段级限制（一期）」）。
 *
 * <p>一期**不做字段级白名单配置，为硬编码规则**（PRD §5.3 末注），因此这些角色码是
 * 「金额可写/可导出」「手机号完整值可见」三条硬编码规则的唯一判定依据；
 * 后续若升级为字段级权限模型（P2），只需替换本类的判定实现。
 */
public final class VisibilityRoles {

    /** 系统管理员（{@code sys_role.code}）。 */
    public static final String ADMIN = "admin";

    /** 集团归口（财务部）负责人 = PRD 所称「财务角色」。 */
    public static final String FINANCE_OWNER = "finance_owner";

    private VisibilityRoles() {
    }

    /** 是否为「财务类角色」（金额可写 / 可导出）。 */
    public static boolean isFinance(java.util.Set<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(FINANCE_OWNER);
    }
}
