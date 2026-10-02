package com.oa.authz.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 事项类别目录（{@code sys_role_category.category}）—— <b>纯函数</b>。
 *
 * <p>类别为**配置项五值**（doc/data-model.md §3.3、dict-seed §0.3 {@code matter_category}）：
 * {@code business}（经营）/ {@code economy}（经济）/ {@code admin}（行政）/ {@code hr}（人力）/
 * {@code invest}（投资）。旧码 {@code operate} 已作废并迁移为 {@code business}，本目录**不接受**它。
 *
 * <p>类别不参与流程路由；该范围仅用于 {@code data_scope = group_category} 的角色
 * （集团分管领导 / 集团归口负责人按分管业务线限定可见范围）。
 */
public final class CategoryCatalog {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("business", "经营");
        LABELS.put("economy", "经济");
        LABELS.put("admin", "行政");
        LABELS.put("hr", "人力");
        LABELS.put("invest", "投资");
    }

    private CategoryCatalog() {
    }

    /** 五值（有序）。 */
    public static List<String> values() {
        return List.copyOf(LABELS.keySet());
    }

    /** 值 → 中文名。 */
    public static Map<String, String> labels() {
        return Map.copyOf(LABELS);
    }

    public static String labelOf(String category) {
        return category == null ? null : LABELS.get(category.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isValid(String category) {
        return category != null && LABELS.containsKey(category.trim().toLowerCase(Locale.ROOT));
    }

    /** 归一化并校验（400）。 */
    public static String requireValid(String category) {
        String normalized = category == null ? null : category.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || !LABELS.containsKey(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "事项类别取值不合法（仅允许 " + String.join("/", LABELS.keySet()) + "）：" + category);
        }
        return normalized;
    }

    /** 批量归一化 + 去重（保序），任一非法即 400。 */
    public static Set<String> normalizeAll(Collection<String> categories) {
        Set<String> result = new LinkedHashSet<>();
        if (categories == null) {
            return result;
        }
        List<String> invalid = new ArrayList<>();
        for (String category : categories) {
            if (category == null || category.isBlank()) {
                continue;
            }
            String normalized = category.trim().toLowerCase(Locale.ROOT);
            if (!LABELS.containsKey(normalized)) {
                invalid.add(category);
                continue;
            }
            result.add(normalized);
        }
        if (!invalid.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "事项类别取值不合法（仅允许 " + String.join("/", LABELS.keySet()) + "）：" + invalid);
        }
        return result;
    }
}
