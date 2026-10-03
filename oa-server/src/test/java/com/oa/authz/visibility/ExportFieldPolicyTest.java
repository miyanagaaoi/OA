package com.oa.authz.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 导出字段级限制单测（阶段 1.6，{@code oa.authz.visibility.export}）。
 *
 * <p>覆盖「金额不可导出」的三处落点：
 * <ol>
 *   <li><b>单据列表</b>（{@code instance_list}）：默认剔除 {@code amount} 列；</li>
 *   <li><b>审计日志</b>（{@code audit_log}）：JSON 列内**任意层级**的金额键被剔除，解析失败整列剔除（fail-closed）；</li>
 *   <li><b>组织/主数据</b>：列清单由本策略统一给出，天然不含金额列；主数据导出仅系统管理员。</li>
 * </ol>
 */
class ExportFieldPolicyTest {

    private static CurrentUser user(long id, String... roles) {
        return CurrentUser.of(id, "u" + id, "用户" + id, "A000" + id, 1L, 1L, Set.of(roles), Set.of(), false);
    }

    @Test
    @DisplayName("默认（amount-enabled=false）：单据列表导出列不含 amount，任何人都不例外")
    void instanceListNeverExportsAmountByDefault() {
        List<String> columns = ExportFieldPolicy.columnsFor(ExportTarget.INSTANCE_LIST, false);

        assertThat(columns).doesNotContain("amount");
        assertThat(columns).contains("biz_no", "form_type", "current_node", "status");
        assertThat(ExportFieldPolicy.excludedColumns(ExportTarget.INSTANCE_LIST, false)).containsExactly("amount");
        assertThat(ExportFieldPolicy.amountExportable(user(1L, "admin"), ExportTarget.INSTANCE_LIST, false)).isFalse();
        assertThat(ExportFieldPolicy.amountExportable(user(2L, "finance_owner"), ExportTarget.INSTANCE_LIST, true))
                .isTrue();
    }

    @Test
    @DisplayName("组织导出列与 import-spec §9.1 一致，且不含任何金额列")
    void orgColumnsMatchTemplate() {
        assertThat(ExportFieldPolicy.columnsFor(ExportTarget.ORG, false))
                .containsExactly("org_path", "org_name", "org_type", "parent_path", "status", "remark");
        assertThat(ExportFieldPolicy.columnsFor(ExportTarget.USER, false))
                .containsExactly("account", "employee_no", "name", "phone", "email", "company_path", "dept_path",
                        "status", "remark");
        assertThat(ExportFieldPolicy.columnsFor(ExportTarget.ORG_LEADER, false))
                .containsExactly("org_path", "user_account", "leader_type", "sort", "business_line", "remark");
        assertThat(ExportFieldPolicy.columnsFor(ExportTarget.USER_POSITION, false))
                .containsExactly("user_account", "org_path", "post_name", "is_primary", "remark");
        assertThat(ExportFieldPolicy.columnsFor(ExportTarget.USER_ROLE, false))
                .containsExactly("user_account", "role_code", "scope_org_path", "remark");
    }

    @Test
    @DisplayName("审计日志：JSON 列保留在生效列内（金额靠键级剔除，不做整列删除，避免表头/数据行错位）")
    void auditLogColumnsKeepJsonColumns() {
        List<String> columns = ExportFieldPolicy.columnsFor(ExportTarget.AUDIT_LOG, false);
        assertThat(columns).containsExactly("id", "created_at", "user_name", "action", "target_type", "target_id",
                "before_json", "after_json", "ip");
        assertThat(ExportFieldPolicy.excludedColumns(ExportTarget.AUDIT_LOG, false)).isEmpty();
        assertThat(ExportFieldPolicy.redactAmountKeys("{\"amount\":\"1.00\"}"))
                .isEqualTo("{\"amount\":\"" + ExportFieldPolicy.REDACTED + "\"}");
    }

    @Test
    @DisplayName("审计日志导出：JSON 列内任意层级的金额键被剔除（含嵌套对象与数组）")
    void auditLogRedactsAmountKeys() {
        String json = "{\"counterparty\":\"乙公司\",\"amount\":\"100000.00\","
                + "\"items\":[{\"name\":\"甲\",\"unit_price_amount\":1},{\"amount\":2}],"
                + "\"nested\":{\"contract_amount\":\"9.99\",\"dept\":\"财务部\"}}";

        String redacted = ExportFieldPolicy.redactAmountKeys(json);

        assertThat(redacted).doesNotContain("100000.00").doesNotContain("9.99");
        assertThat(redacted).contains("counterparty").contains("乙公司").contains(ExportFieldPolicy.REDACTED);
        assertThat(redacted).contains("unit_price_amount");
        assertThat(redacted).doesNotContain("\"amount\":\"2\"").doesNotContain(":2");
    }

    @Test
    @DisplayName("审计日志导出：JSON 无法解析时整列替换为占位值（fail-closed，绝不原样带出）")
    void unparseableJsonIsFullyRedacted() {
        assertThat(ExportFieldPolicy.redactAmountKeys("{不是 JSON")).isEqualTo(ExportFieldPolicy.UNPARSEABLE);
        assertThat(ExportFieldPolicy.redactAmountKeys(null)).isNull();
    }

    @Test
    @DisplayName("导出前鉴权：主数据仅系统管理员；单据/审计类需系统管理员或财务角色")
    void exportAuthorisation() {
        ExportFieldPolicy.assertAllowed(user(1L, "admin"), ExportTarget.ORG);
        assertThatThrownBy(() -> ExportFieldPolicy.assertAllowed(user(9L, "company_admin"), ExportTarget.ORG))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.EXPORT_DENIED));

        ExportFieldPolicy.assertAllowed(user(1L, "admin"), ExportTarget.AUDIT_LOG);
        ExportFieldPolicy.assertAllowed(user(2L, "finance_owner"), ExportTarget.INSTANCE_LIST);
        assertThatThrownBy(() -> ExportFieldPolicy.assertAllowed(user(9L, "employee"), ExportTarget.INSTANCE_LIST))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.EXPORT_DENIED));
    }

    @Test
    @DisplayName("字段越界：伪造 fields=[\"amount\"] → 403 EXPORT_FIELD_DENIED（直连接口也无法绕过）")
    void forgedFieldsAreRejected() {
        assertThatThrownBy(() -> ExportFieldPolicy.assertFieldsExportable(ExportTarget.INSTANCE_LIST,
                List.of("biz_no", "amount"), false))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXPORT_FIELD_DENIED))
                .hasMessageContaining("amount");

        // 合法列通过
        ExportFieldPolicy.assertFieldsExportable(ExportTarget.INSTANCE_LIST, List.of("biz_no", "status"), false);
    }

    @Test
    @DisplayName("策略快照：每个目标都给出生效列与被剔除列（前端据此隐藏「导出金额」入口）")
    void policySnapshot() {
        Map<String, Object> policy = ExportFieldPolicy.describe(user(9L, "company_admin"), false);

        assertThat(policy).containsEntry("amountExportEnabled", false);
        assertThat(policy.get("targets")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(ExportTarget.values().length);
        ExportFieldPolicy.ExportDecision decision = ExportFieldPolicy.decide(user(1L, "admin"),
                ExportTarget.INSTANCE_LIST, false);
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.amountExported()).isFalse();
        assertThat(decision.effectiveColumns()).doesNotContain("amount");
    }
}
