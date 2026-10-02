package com.oa.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeSqlBuilder;
import com.oa.common.scope.DataScopeType;
import com.oa.common.scope.SqlFragment;
import com.oa.common.security.CurrentUser;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DataScopeSqlBuilder} 纯函数单测（无 DB / 无 Spring 容器）。
 *
 * <p>覆盖口径：self / dept / company / group_all / group_category（财务部五路并集），
 * 以及「不涉及费用且未经流转的事项单不可见」这一条最关键的越权红线。
 */
class DataScopeSqlBuilderTest {

    private static final long UID = 1024L;
    private static final long COMPANY_ID = 12L;
    private static final long FINANCE_DEPT_ID = 210L;
    private static final String DEPT_PATH = "/1/12/135/";
    private static final String FINANCE_DEPT_PATH = "/1/30/210/";

    private static DataScopeContext context(DataScopeType... scopes) {
        Set<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
        Collections.addAll(set, scopes);
        CurrentUser principal = CurrentUser.of(UID, "zhangsan", "张三", "E1024", 135L, COMPANY_ID,
                new LinkedHashSet<>(Set.of("employee")), set, false);
        return DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(set)
                .deptPathPrefix(DEPT_PATH)
                .financeDeptPathPrefix(FINANCE_DEPT_PATH)
                .primaryOrgId(135L)
                .companyId(COMPANY_ID)
                .financeDeptId(FINANCE_DEPT_ID)
                .build();
    }

    @Test
    @DisplayName("self：本人发起 ∪ 本人作为审批人（含 origin_assignee_id）∪ 抄送人")
    void selfScope() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.SELF));

        assertThat(fragment.getSql()).isEqualTo("(" + DataScopeSqlBuilder.selfBranch("i") + ")");
        assertThat(fragment.getSql())
                .contains("i.initiator_id = #{scope_uid}")
                .contains("t.assignee_id = #{scope_uid}")
                .contains("t.origin_assignee_id = #{scope_uid}")
                .contains("flow_cc c WHERE c.instance_id = i.id");
        assertThat(fragment.getParams()).containsEntry(DataScopeSqlBuilder.P_UID, UID);
        assertThat(fragment.isUnrestricted()).isFalse();
        assertThat(fragment.isDeny()).isFalse();
    }

    @Test
    @DisplayName("dept：self ∪ initiator_org_path LIKE :dept_path_prefix（前缀带 % ）")
    void deptScope() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.DEPT));

        String expected = "(" + DataScopeSqlBuilder.selfBranch("i") + " OR " + DataScopeSqlBuilder.deptBranch("i") + ")";
        assertThat(fragment.getSql()).isEqualTo(expected);
        assertThat(fragment.getSql()).contains("i.initiator_org_path LIKE #{scope_dept_path_prefix}");
        assertThat(fragment.getParams())
                .containsEntry(DataScopeSqlBuilder.P_UID, UID)
                .containsEntry(DataScopeSqlBuilder.P_DEPT_PATH, "/1/12/135/%");
    }

    @Test
    @DisplayName("company：initiator_company_id = :company_id")
    void companyScope() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.COMPANY));

        assertThat(fragment.getSql()).isEqualTo("(" + DataScopeSqlBuilder.companyBranch("i") + ")");
        assertThat(fragment.getParams()).containsEntry(DataScopeSqlBuilder.P_COMPANY, COMPANY_ID);
    }

    @Test
    @DisplayName("group_all：无过滤（1=1）")
    void groupAllScope() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.GROUP_ALL));

        assertThat(fragment.getSql()).isEqualTo(SqlFragment.SYSTEM_SQL);
        assertThat(fragment.isUnrestricted()).isTrue();
        assertThat(fragment.getParams()).isEmpty();
    }

    @Test
    @DisplayName("group_category（财务部）：五路并集 = self ∪ 归口类别 ∪ 涉及费用事项单 ∪ 流转链 ∪ 在途承接")
    void financeFiveWayUnion() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.GROUP_CATEGORY));

        String expected = "(" + String.join(" OR ",
                DataScopeSqlBuilder.selfBranch("i"),
                DataScopeSqlBuilder.financeCategoryBranch("i"),
                DataScopeSqlBuilder.financeInvolveCostBranch("i"),
                DataScopeSqlBuilder.financeRoutingBranch("i"),
                DataScopeSqlBuilder.financeInFlightBranch("i")) + ")";
        assertThat(fragment.getSql()).isEqualTo(expected);

        // 五路逐条核对
        assertThat(fragment.getSql()).contains("i.initiator_id = #{scope_uid}");                                       // ① self
        assertThat(fragment.getSql()).contains("i.form_type IN ('fund','contract','seal')");                           // ② 归口类别
        assertThat(fragment.getSql()).contains("EXISTS (SELECT 1 FROM form_data f WHERE f.id = i.form_data_id");       // ③ 涉费用事项单
        assertThat(fragment.getSql()).contains("EXISTS (SELECT 1 FROM flow_routing r WHERE r.instance_id = i.id AND r.to_dept_id = #{scope_finance_dept_id})"); // ④ 流转链
        assertThat(fragment.getSql()).contains("i.current_dept_id = #{scope_finance_dept_id}");                        // ⑤ 在途承接

        assertThat(fragment.getParams())
                .containsEntry(DataScopeSqlBuilder.P_UID, UID)
                .containsEntry(DataScopeSqlBuilder.P_FINANCE_DEPT, FINANCE_DEPT_ID);
    }

    @Test
    @DisplayName("财务部红线：『不涉及费用』且未经流转的事项单不可见（matter 只出现在带 involve_cost 的子查询里）")
    void financeMatterWithoutCostIsInvisible() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.GROUP_CATEGORY));
        String sql = fragment.getSql();

        assertThat(sql).contains("JSON_UNQUOTE(JSON_EXTRACT(f.fields_json, '$.involve_cost')) IN ('true','1')");
        // 唯一出现 form_type = 'matter' 的地方必须紧跟 involve_cost 子查询；不存在「matter 就可见」的分支
        assertThat(sql).doesNotContain("i.form_type = 'matter' OR");
        assertThat(sql).doesNotContain("OR i.form_type = 'matter')");
        int matterIndex = sql.indexOf("i.form_type = 'matter'");
        assertThat(matterIndex).isGreaterThanOrEqualTo(0);
        assertThat(sql.substring(matterIndex))
                .startsWith("i.form_type = 'matter' AND EXISTS (SELECT 1 FROM form_data f WHERE f.id = i.form_data_id");
    }

    @Test
    @DisplayName("财务部：归口部门分支默认关闭；显式开启后才织入 owner_dept_id")
    void financeOwnerDeptBranchIsOptIn() {
        SqlFragment defaultFragment = DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.GROUP_CATEGORY));
        assertThat(defaultFragment.getSql()).doesNotContain(DataScopeSqlBuilder.financeOwnerDeptBranch("i"));

        DataScopeContext enabled = context(DataScopeType.GROUP_CATEGORY).toBuilder()
                .financeOwnerDeptBranchEnabled(true)
                .build();
        SqlFragment enabledFragment = DataScopeSqlBuilder.buildInstanceScope(enabled);
        assertThat(enabledFragment.getSql()).contains(DataScopeSqlBuilder.financeOwnerDeptBranch("i"));
        // 说明：flow_instance.owner_dept_id 恒为集团财务部，该分支一旦开启等于放开全部单据，
        // 因此默认关闭，需先修正 data-model 语义后再启用（已在交付说明中列为文档不一致项）。
    }

    @Test
    @DisplayName("多口径：并集（角色合并取最宽）")
    void multiScopeUnion() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(
                context(DataScopeType.SELF, DataScopeType.COMPANY));
        String expected = "(" + DataScopeSqlBuilder.selfBranch("i") + " OR "
                + DataScopeSqlBuilder.companyBranch("i") + ")";
        assertThat(fragment.getSql()).isEqualTo(expected);
    }

    @Test
    @DisplayName("缺少必要参数 / 无口径：拒绝全表（1=0），绝不静默放行")
    void failClosed() {
        SqlFragment noScope = DataScopeSqlBuilder.buildInstanceScope(
                DataScopeContext.builder().scopes(EnumSet.noneOf(DataScopeType.class)).build());
        assertThat(noScope.getSql()).isEqualTo(SqlFragment.DENY_SQL);
        assertThat(noScope.isDeny()).isTrue();

        // dept 口径但没有组织路径
        DataScopeContext noPath = DataScopeContext.builder()
                .principal(CurrentUser.of(UID, "a", "甲", null, null, COMPANY_ID, Set.of(), Set.of(DataScopeType.DEPT), false))
                .scopes(EnumSet.of(DataScopeType.DEPT))
                .companyId(COMPANY_ID)
                .build();
        assertThat(DataScopeSqlBuilder.buildInstanceScope(noPath).getSql()).isEqualTo(SqlFragment.DENY_SQL);

        // company 口径但没有公司 id
        DataScopeContext noCompany = DataScopeContext.builder()
                .principal(CurrentUser.of(UID, "a", "甲", null, 135L, null, Set.of(), Set.of(DataScopeType.COMPANY), false))
                .scopes(EnumSet.of(DataScopeType.COMPANY))
                .build();
        assertThat(DataScopeSqlBuilder.buildInstanceScope(noCompany).getSql()).isEqualTo(SqlFragment.DENY_SQL);
    }

    @Test
    @DisplayName("用户表口径：本人 ∪ 本部门子树 ∪ 本公司")
    void userScope() {
        SqlFragment fragment = DataScopeSqlBuilder.buildUserScope(
                context(DataScopeType.SELF, DataScopeType.DEPT, DataScopeType.COMPANY), "u");

        assertThat(fragment.getSql())
                .contains("u.id = #{scope_uid}")
                .contains("u.org_id IN (SELECT so.id FROM sys_org so WHERE so.path LIKE #{scope_dept_path_prefix})")
                .contains("u.company_id = #{scope_company_id}");
        assertThat(fragment.getParams())
                .containsEntry(DataScopeSqlBuilder.P_UID, UID)
                .containsEntry(DataScopeSqlBuilder.P_DEPT_PATH, "/1/12/135/%")
                .containsEntry(DataScopeSqlBuilder.P_COMPANY, COMPANY_ID);
    }

    @Test
    @DisplayName("片段整体加括号：以 AND 织入时不会击穿前置条件")
    void fragmentIsParenthesized() {
        SqlFragment fragment = DataScopeSqlBuilder.buildInstanceScope(
                context(DataScopeType.SELF, DataScopeType.DEPT, DataScopeType.COMPANY, DataScopeType.GROUP_CATEGORY));
        assertThat(fragment.getSql()).startsWith("(").endsWith(")");
        // 直接拼接到 WHERE 的真实形态
        String where = "WHERE 1 = 1 AND " + fragment.getSql();
        assertThat(where).startsWith("WHERE 1 = 1 AND ((");
    }

    @Test
    @DisplayName("非法别名直接拒绝（防注入）")
    void rejectIllegalAlias() {
        assertThatThrownBy(() -> DataScopeSqlBuilder.buildInstanceScope(context(DataScopeType.SELF), "i; DROP TABLE sys_user"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
