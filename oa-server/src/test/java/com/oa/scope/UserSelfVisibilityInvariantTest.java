package com.oa.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeSqlBuilder;
import com.oa.common.scope.DataScopeType;
import com.oa.common.scope.SqlFragment;
import com.oa.common.security.CurrentUser;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自读不变式（安全不变量）：**任何登录用户都必须能看到「自己」这一行**，且不依赖角色数据域能否解析。
 *
 * <p>为什么单独守这条：{@code GET /api/v1/auth/me}、改密、水印（AC-44）、首登强制改密都走
 * {@code SysUserMapper.selectUserById}，其 WHERE 由 {@link DataScopeSqlBuilder#buildUserScope} 织入。
 * 若数据域无法解析（例：角色 {@code data_scope='company'} 而 {@code sys_user.company_id} 为 NULL——
 * DDL 允许），片段会退化为 deny({@code 1=0})，导致这些路径整体 401；
 * 而 sys_user 的读语句按评审阻断项必须带标记、不允许豁免，所以必须在片段层保证「含本人」。
 */
class UserSelfVisibilityInvariantTest {

    private static final long UID = 1001L;
    private static final long COMPANY_ID = 12L;
    private static final String DEPT_PATH = "/1/12/135/";
    private static final String SELF_BRANCH = "u.id = #{scope_uid}";

    private static DataScopeContext ctx(Set<DataScopeType> scopes, Long companyId, String deptPath) {
        CurrentUser principal = CurrentUser.of(UID, "zhangsan", "张三", "E1024", 135L, companyId,
                new LinkedHashSet<>(Set.of("employee")), scopes, false);
        return DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(scopes)
                .deptPathPrefix(deptPath)
                .financeDeptPathPrefix(null)
                .primaryOrgId(135L)
                .companyId(companyId)
                .financeDeptId(null)
                .build();
    }

    @Test
    @DisplayName("无任何数据域 → 片段仍含本人且不是 deny(1=0)")
    void emptyScopeStillSeesSelf() {
        SqlFragment fragment = DataScopeSqlBuilder.buildUserScope(
                ctx(EnumSet.noneOf(DataScopeType.class), null, null), "u");

        assertThat(fragment.isDeny()).isFalse();
        assertThat(fragment.getSql()).contains(SELF_BRANCH);
        assertThat(fragment.getParams()).containsEntry("scope_uid", UID);
    }

    @Test
    @DisplayName("company 口径但 company_id 为空 → 保持可见本人（并在文档中说明该退化场景）")
    void companyScopeWithoutCompanyIdStillSeesSelf() {
        SqlFragment fragment = DataScopeSqlBuilder.buildUserScope(
                ctx(EnumSet.of(DataScopeType.COMPANY), null, null), "u");

        assertThat(fragment.isDeny()).isFalse();
        assertThat(fragment.getSql()).contains(SELF_BRANCH);
        assertThat(fragment.getParams()).containsEntry("scope_uid", UID);
    }

    @Test
    @DisplayName("group_category 且归口部门路径与 company_id 均缺失 → 保持可见本人")
    void groupCategoryUnresolvedStillSeesSelf() {
        SqlFragment fragment = DataScopeSqlBuilder.buildUserScope(
                ctx(EnumSet.of(DataScopeType.GROUP_CATEGORY), null, null), "u");

        assertThat(fragment.isDeny()).isFalse();
        assertThat(fragment.getSql()).contains(SELF_BRANCH);
    }

    @Test
    @DisplayName("self/dept 同时存在时，本人分支只拼接一次（不产生重复条件）")
    void selfBranchIsNotDuplicated() {
        SqlFragment fragment = DataScopeSqlBuilder.buildUserScope(
                ctx(EnumSet.of(DataScopeType.SELF, DataScopeType.DEPT), COMPANY_ID, DEPT_PATH), "u");

        String sql = fragment.getSql();
        int first = sql.indexOf(SELF_BRANCH);
        assertThat(first).isGreaterThanOrEqualTo(0);
        assertThat(sql.indexOf(SELF_BRANCH, first + 1)).isEqualTo(-1);
        // USER 口径的部门分支按组织子树过滤（列名与实例口径的 initiator_org_path 不同）
        assertThat(sql).contains("u.org_id IN (SELECT so.id FROM sys_org so WHERE so.path LIKE #{scope_dept_path_prefix})");
    }

    @Test
    @DisplayName("group_all 仍是全放行（不受本不变式影响）")
    void groupAllRemainsUnrestricted() {
        SqlFragment fragment = DataScopeSqlBuilder.buildUserScope(
                ctx(EnumSet.of(DataScopeType.GROUP_ALL), COMPANY_ID, DEPT_PATH), "u");

        assertThat(fragment.isUnrestricted()).isTrue();
    }
}
