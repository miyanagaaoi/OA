package com.oa.authz.app;

import com.oa.authz.domain.SysRole;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据域目录（{@code sys_role.data_scope}）—— <b>纯函数</b>。
 *
 * <p>五值与 {@link DataScopeType} / DDL 的 {@code chk_sys_role_scope} CHECK 约束**完全一致**，
 * 禁止新增取值；口径见 doc/prd-0.1.md §5.3 与 doc/data-model.md §7.2。
 *
 * <p>{@code group_category} 的角色**必须同时配置类别范围**（{@code sys_role_category}），
 * 否则该口径无法收敛（会退化成「全集团无过滤」），因此一律 400。
 */
public final class DataScopeCatalog {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        // 顺序逐字对齐 data-model.md §3.1 sys_role.data_scope 的列注释：
        // self 本人 | dept 本部门 | company 本公司 | group_all 全集团 | group_category 全集团按归口类别
        LABELS.put(DataScopeType.SELF.getCode(), "本人");
        LABELS.put(DataScopeType.DEPT.getCode(), "本部门");
        LABELS.put(DataScopeType.COMPANY.getCode(), "本公司");
        LABELS.put(DataScopeType.GROUP_ALL.getCode(), "全集团");
        LABELS.put(DataScopeType.GROUP_CATEGORY.getCode(), "全集团按归口类别");

        DESCRIPTIONS.put(DataScopeType.SELF.getCode(), "本人发起的单据 + 本人作为审批人/抄送人的单据");
        DESCRIPTIONS.put(DataScopeType.DEPT.getCode(), "self ∪ 发起人组织路径前缀（本部门/科室子树）");
        DESCRIPTIONS.put(DataScopeType.COMPANY.getCode(), "发起人所属公司 = 本公司");
        DESCRIPTIONS.put(DataScopeType.GROUP_ALL.getCode(), "无过滤（仅集团董事长与系统管理员）");
        DESCRIPTIONS.put(DataScopeType.GROUP_CATEGORY.getCode(),
                "归口类别（资金/合同/印鉴）+ 涉及费用的事项单 + 流转链可见（须同时配置事项类别）");
    }

    private DataScopeCatalog() {
    }

    /** 目录条目（{@code GET /api/v1/authz/data-scopes}）。 */
    public record Entry(String value, String label, String description) {
    }

    /** 五值（有序，与 data-model.md §3.1 列注释的书写顺序一致）。 */
    public static List<String> values() {
        return new ArrayList<>(LABELS.keySet());
    }

    /** 目录（含中文名与口径说明）。 */
    public static List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        for (String value : LABELS.keySet()) {
            result.add(new Entry(value, LABELS.get(value), DESCRIPTIONS.get(value)));
        }
        return result;
    }

    /** 校验取值属于五值（400）。 */
    public static String requireValid(String dataScope) {
        String normalized = dataScope == null ? null : dataScope.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || DataScopeType.of(normalized).isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "data_scope 取值不合法（仅允许 " + String.join("/", LABELS.keySet()) + "）：" + dataScope);
        }
        return normalized;
    }

    /**
     * {@code group_category} 必须同时配置类别范围，否则 400。
     *
     * @param dataScope         已校验的数据域取值
     * @param configuredCategories 该角色当前（或本次提交）的类别集合
     */
    public static void requireCategoriesForGroupCategory(String dataScope, java.util.Collection<String> configuredCategories) {
        if (SysRole.DATA_SCOPE_GROUP_CATEGORY.equals(dataScope)
                && (configuredCategories == null || configuredCategories.isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "data_scope=group_category 的角色必须同时配置事项类别范围（sys_role_category），"
                            + "否则该口径会退化为全集团无过滤");
        }
    }
}
