package com.oa.authz.visibility;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 金额字段只读策略 —— <b>1.6 字段级限制（{@code oa.authz.visibility.field.amount}）</b>的纯函数实现。
 *
 * <h2>口径（doc/prd-0.1.md §5.3 / AC-18 / 附录 A）</h2>
 * <ul>
 *   <li>「合同金额、资金金额」对**非财务类角色**只读展示：<b>不可写</b>（本类 {@link #assertWritable}），
 *       <b>不可导出</b>（{@link #canExportAmounts}）；</li>
 *   <li>系统管理员与财务角色（{@code admin} / {@code finance_owner}）可写、可导出；</li>
 *   <li>一期**不做字段级白名单配置，是硬编码规则**（PRD §5.3 末注）——因此本类是唯一判定入口，
 *       与三态白名单（{@code com.oa.form.app.FormWritePolicy}）**正交**：
 *       状态允许写 ≠ 角色允许写金额，两者必须都通过。</li>
 * </ul>
 *
 * <p>字段名口径：金额字段的键名统一对齐 doc/forms.md（金额控件字段名为 {@code amount}，
 * 如事项单「涉及金额」、资金单「申请金额」、合同单「合同金额」），因此判定规则为
 * 「等于 {@code amount} 或以 {@code _amount} 结尾」，另外显式列出常见别名以便扩展。
 */
public final class AmountFieldPolicy {

    /** 金额控件的字段名（doc/forms.md 1.x）。 */
    public static final String AMOUNT_FIELD = "amount";

    /** 金额字段名规则：{@code amount} 或 {@code *_amount}（不区分大小写）。 */
    private static final Pattern AMOUNT_NAME = Pattern.compile("^(?:[a-z0-9]+_)*amount$", Pattern.CASE_INSENSITIVE);

    /** 显式别名（不在 {@code *_amount} 规律内、但业务上确属金额的键）。 */
    private static final Set<String> ALIASES = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            "money", "total_money", "contract_money", "fund_money")));

    private AmountFieldPolicy() {
    }

    /** 是否为金额字段。 */
    public static boolean isAmountField(String field) {
        if (field == null) {
            return false;
        }
        String name = field.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return false;
        }
        return AMOUNT_NAME.matcher(name).matches() || ALIASES.contains(name);
    }

    /** 载荷中的全部金额字段（保持入参顺序）。 */
    public static Set<String> amountFields(Map<String, ?> payload) {
        Set<String> result = new LinkedHashSet<>();
        if (payload == null) {
            return result;
        }
        for (String key : payload.keySet()) {
            if (isAmountField(key)) {
                result.add(key);
            }
        }
        return result;
    }

    /** 该角色能否写金额：系统管理员 或 财务角色（PRD §5.3）。 */
    public static boolean canWriteAmounts(CurrentUser principal) {
        if (principal == null) {
            return false;
        }
        return principal.hasRole(VisibilityRoles.ADMIN) || VisibilityRoles.isFinance(principal.roleCodes());
    }

    /** 该角色能否导出金额（同上；导出行为另受 {@code ExportFieldPolicy} 与审计留痕约束）。 */
    public static boolean canExportAmounts(CurrentUser principal) {
        return canWriteAmounts(principal);
    }

    /**
     * 金额字段写入闸门：载荷中出现金额字段且调用人非财务类角色 → 403
     * {@link ErrorCode#AMOUNT_READ_ONLY}（与单据状态无关，草稿态同样拒绝）。
     *
     * @throws BizException 403 金额只读
     */
    public static void assertWritable(CurrentUser principal, Map<String, ?> payload) {
        Set<String> fields = amountFields(payload);
        if (fields.isEmpty() || canWriteAmounts(principal)) {
            return;
        }
        throw deny(fields, "（PRD §5.3：合同金额、资金金额对非财务类角色只读展示）");
    }

    /** 金额字段写入闸门（按字段名集合判定）。 */
    public static void assertWritable(CurrentUser principal, Iterable<String> fields) {
        Set<String> offenders = new LinkedHashSet<>();
        if (fields != null) {
            for (String field : fields) {
                if (isAmountField(field)) {
                    offenders.add(field);
                }
            }
        }
        if (offenders.isEmpty() || canWriteAmounts(principal)) {
            return;
        }
        throw deny(offenders, "（PRD §5.3：合同金额、资金金额对非财务类角色只读展示）");
    }

    private static BizException deny(Set<String> fields, String reason) {
        return new BizException(ErrorCode.AMOUNT_READ_ONLY,
                "金额字段对非财务类角色只读，写入被拒绝：字段 " + String.join("、", fields) + reason);
    }
}
