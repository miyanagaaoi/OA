package com.oa.common.scope;

import java.util.Collection;
import java.util.Optional;

/**
 * 数据域取值（与 {@code sys_role.data_scope} 的 CHECK 约束完全一致，禁止新增取值）。
 *
 * <p>口径见 doc/prd-0.1.md §5.3 与 doc/data-model.md §7.2：
 * <ul>
 *   <li>{@link #SELF} 本人发起 ∪ 本人作为审批人（含 {@code origin_assignee_id}）∪ 抄送人；</li>
 *   <li>{@link #DEPT} self ∪ 发起人组织路径前缀；</li>
 *   <li>{@link #COMPANY} 发起人公司 = 本公司；</li>
 *   <li>{@link #GROUP_CATEGORY} 全集团按归口类别（财务部口径，见 {@link DataScopeSqlBuilder}）；</li>
 *   <li>{@link #GROUP_ALL} 无过滤（董事长 / 系统管理员）。</li>
 * </ul>
 *
 * <p>多角色合并规则：**取最宽口径**（{@link #widest(Collection)}），SQL 层再按并集拼装。
 */
public enum DataScopeType {

    SELF("self", 10),
    DEPT("dept", 20),
    COMPANY("company", 30),
    GROUP_CATEGORY("group_category", 40),
    GROUP_ALL("group_all", 50);

    private final String code;
    private final int width;

    DataScopeType(String code, int width) {
        this.code = code;
        this.width = width;
    }

    public String getCode() {
        return code;
    }

    /** 口径宽度权重，越大越宽（多角色合并用）。 */
    public int getWidth() {
        return width;
    }

    /** 是否为「无过滤」口径（董事长 / 系统管理员）。 */
    public boolean isBypass() {
        return this == GROUP_ALL;
    }

    public static Optional<DataScopeType> of(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase();
        for (DataScopeType type : values()) {
            if (type.code.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 多角色取最宽；空集合返回 {@link Optional#empty()}（调用方按「无可见数据」处理）。 */
    public static Optional<DataScopeType> widest(Collection<DataScopeType> types) {
        if (types == null || types.isEmpty()) {
            return Optional.empty();
        }
        DataScopeType result = null;
        for (DataScopeType type : types) {
            if (type == null) {
                continue;
            }
            if (result == null || type.width > result.width) {
                result = type;
            }
        }
        return Optional.ofNullable(result);
    }
}
