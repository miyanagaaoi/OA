package com.oa.authz.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormWritePolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 金额字段只读单测（阶段 1.6，{@code oa.authz.visibility.field.amount}）。
 *
 * <p>口径（PRD §5.3 / AC-18 / AC-28）：
 * <ul>
 *   <li>金额字段名识别（{@code amount} / {@code *_amount} / 显式别名）；</li>
 *   <li>非财务类角色写金额 → 403 {@code AMOUNT_READ_ONLY}，**与单据状态无关**（草稿态同样拒绝）；</li>
 *   <li>系统管理员与财务角色（{@code finance_owner}）可写；</li>
 *   <li>与三态白名单正交：状态允许写 ≠ 角色允许写金额（草稿 + 员工 → 金额仍被拒）；</li>
 *   <li>宽容口径（filter）会把金额字段**剥掉**而不是放行。</li>
 * </ul>
 */
class AmountFieldPolicyTest {

    private static CurrentUser user(long id, String... roles) {
        return CurrentUser.of(id, "u" + id, "用户" + id, "A000" + id, 1L, 1L, Set.of(roles), Set.of(), false);
    }

    @Test
    @DisplayName("金额字段识别：amount / *_amount / 显式别名命中，普通字段不命中")
    void amountFieldDetection() {
        assertThat(AmountFieldPolicy.isAmountField("amount")).isTrue();
        assertThat(AmountFieldPolicy.isAmountField("contract_amount")).isTrue();
        assertThat(AmountFieldPolicy.isAmountField("FUND_AMOUNT")).isTrue();
        assertThat(AmountFieldPolicy.isAmountField("total_money")).isTrue();
        assertThat(AmountFieldPolicy.isAmountField("counterparty")).isFalse();
        assertThat(AmountFieldPolicy.isAmountField("amount_unit")).isFalse();
        assertThat(AmountFieldPolicy.isAmountField(null)).isFalse();
    }

    @Test
    @DisplayName("非财务角色写金额 → 403 AMOUNT_READ_ONLY（草稿态同样拒绝，与状态正交）")
    void nonFinanceCannotWriteAmounts() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("counterparty", "某某公司");
        payload.put("contract_amount", "100000.00");

        assertThatThrownBy(() -> AmountFieldPolicy.assertWritable(user(9L, "employee"), payload))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_READ_ONLY);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(403);
                })
                .hasMessageContaining("contract_amount");
    }

    @Test
    @DisplayName("系统管理员与财务角色可写金额；只含非金额字段时任何角色都放行")
    void financeAndAdminCanWriteAmounts() {
        Map<String, Object> payload = Map.of("amount", "100.00");

        AmountFieldPolicy.assertWritable(user(1L, "admin"), payload);
        AmountFieldPolicy.assertWritable(user(2L, "finance_owner"), payload);
        AmountFieldPolicy.assertWritable(user(9L, "employee"), Map.of("counterparty", "乙"));
        assertThat(AmountFieldPolicy.canWriteAmounts(user(1L, "admin"))).isTrue();
        assertThat(AmountFieldPolicy.canWriteAmounts(user(2L, "finance_owner"))).isTrue();
        assertThat(AmountFieldPolicy.canWriteAmounts(user(3L, "dept_leader"))).isFalse();
    }

    @Test
    @DisplayName("组合闸门：草稿态状态允许写全集，但金额仍按角色被拒（金额规则先于状态规则）")
    void amountRuleIsOrthogonalToStateWhitelist() {
        Set<String> allFields = Set.of("counterparty", "amount", "attachments");

        // 员工 + 草稿：状态允许全部字段，但金额必须被拒
        assertThatThrownBy(() -> FormWritePolicy.assertWritable(allFields, FormWritePolicy.FormState.DRAFT,
                FormWritePolicy.FormType.CONTRACT, true, false, allFields, false))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.AMOUNT_READ_ONLY));

        // 财务角色 + 草稿：通过
        FormWritePolicy.assertWritable(allFields, FormWritePolicy.FormState.DRAFT,
                FormWritePolicy.FormType.CONTRACT, true, false, allFields, true);

        // 员工 + 审批中：状态规则也会拒绝（此处仍先命中金额规则，两者都是拒绝）
        assertThatThrownBy(() -> FormWritePolicy.assertWritable(allFields, FormWritePolicy.FormState.APPROVING,
                FormWritePolicy.FormType.CONTRACT, true, false, allFields, false))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("宽容口径：filterWritable 把金额字段剥掉（不落库），其余字段保留")
    void filterStripsAmountFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("counterparty", "某某公司");
        payload.put("amount", "100000.00");
        payload.put("attachments", "[1,2]");
        Set<String> allFields = payload.keySet();

        Map<String, Object> filtered = FormWritePolicy.filterWritable(payload, FormWritePolicy.FormState.DRAFT,
                FormWritePolicy.FormType.CONTRACT, true, false, allFields, false);

        assertThat(filtered).containsOnlyKeys("counterparty", "attachments");
        assertThat(AmountFieldPolicy.isAmountField("amount")).isTrue();
    }
}
