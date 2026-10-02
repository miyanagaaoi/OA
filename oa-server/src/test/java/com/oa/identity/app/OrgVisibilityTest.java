package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.OrgVisibility.OrgRef;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 组织节点可见性（**纯逻辑单测**）：数据域 → 可见组织 id 集合，fail-closed。
 *
 * <p>基线树：
 * <pre>
 *   1  集团        /1/
 *   12 公司A       /1/12/
 *   20 公司B       /1/20/
 *   135 部门1      /1/12/135/
 *   1351 科室1-1   /1/12/135/1351/
 *   200 部门B1     /1/20/200/
 * </pre>
 */
class OrgVisibilityTest {

    private static final List<OrgRef> TREE = List.of(
            new OrgRef(1L, null, "/1/"),
            new OrgRef(12L, 1L, "/1/12/"),
            new OrgRef(20L, 1L, "/1/20/"),
            new OrgRef(135L, 12L, "/1/12/135/"),
            new OrgRef(1351L, 135L, "/1/12/135/1351/"),
            new OrgRef(200L, 20L, "/1/20/200/"));

    private static DataScopeContext context(Set<DataScopeType> scopes, Long primaryOrgId, Long companyId,
                                            String deptPath, String financePath) {
        return DataScopeContext.builder()
                .principal(CurrentUser.of(7L, "u07", "赵庚", "A0007", primaryOrgId, companyId,
                        Set.of("employee"), scopes, false))
                .scopes(scopes)
                .primaryOrgId(primaryOrgId)
                .companyId(companyId)
                .deptPathPrefix(deptPath)
                .financeDeptPathPrefix(financePath)
                .build();
    }

    @Test
    @DisplayName("未装载数据域上下文 → 空集（fail-closed，不得看到任何节点）")
    void nullContextSeesNothing() {
        assertThat(OrgVisibility.visibleIds(null, TREE)).isEmpty();
        assertThat(OrgVisibility.visibleIds(DataScopeContext.system(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("group_all（集团董事长 / 系统管理员）→ 全部节点")
    void groupAllSeesEverything() {
        DataScopeContext ctx = context(EnumSet.of(DataScopeType.GROUP_ALL), 1351L, 12L, null, null);
        assertThat(OrgVisibility.visibleIds(ctx, TREE)).containsExactlyInAnyOrder(1L, 12L, 20L, 135L, 1351L, 200L);
        assertThat(ctx.isBypass()).isTrue();
    }

    @Test
    @DisplayName("company（子公司总经理 / 分公司管理员）→ 本公司子树，看不到公司B与集团节点")
    void companyScope() {
        DataScopeContext ctx = context(EnumSet.of(DataScopeType.COMPANY), 1351L, 12L, null, null);
        // 口径：非 self 口径只给**子树本身**，不给祖先（最小权限）；
        // 树上「公司A」会成为顶层节点（OrgService.buildTree 对父不可见的节点按根挂载）
        assertThat(OrgVisibility.visibleIds(ctx, TREE)).containsExactlyInAnyOrder(12L, 135L, 1351L);
        assertThat(OrgVisibility.visibleIds(ctx, TREE)).doesNotContain(1L, 20L, 200L);
    }

    @Test
    @DisplayName("dept（部门/科室负责人）→ 本部门子树")
    void deptScope() {
        DataScopeContext ctx = context(EnumSet.of(DataScopeType.DEPT), 1351L, 12L, "/1/12/135/", null);
        assertThat(OrgVisibility.visibleIds(ctx, TREE)).containsExactlyInAnyOrder(135L, 1351L);
    }

    @Test
    @DisplayName("self（普通员工）→ 本人节点 + 祖先链（否则树无法渲染出路径）")
    void selfScopeKeepsAncestors() {
        DataScopeContext ctx = context(EnumSet.of(DataScopeType.SELF), 1351L, 12L, null, null);
        assertThat(OrgVisibility.visibleIds(ctx, TREE)).containsExactlyInAnyOrder(1L, 12L, 135L, 1351L);
    }

    @Test
    @DisplayName("group_category（财务部/集团分管领导）→ 归口部门子树；路径缺失时退化为本公司")
    void groupCategoryScope() {
        DataScopeContext withFinance = context(EnumSet.of(DataScopeType.GROUP_CATEGORY), 1351L, 12L, null, "/1/12/135/");
        assertThat(OrgVisibility.visibleIds(withFinance, TREE)).containsExactlyInAnyOrder(135L, 1351L);

        DataScopeContext withoutFinance = context(EnumSet.of(DataScopeType.GROUP_CATEGORY), 1351L, 12L, null, null);
        assertThat(OrgVisibility.visibleIds(withoutFinance, TREE)).containsExactlyInAnyOrder(12L, 135L, 1351L);
    }

    @Test
    @DisplayName("多角色取并集（与 SQL 侧 OR 口径一致）；无任何口径 → 空集")
    void unionOfScopes() {
        DataScopeContext ctx = DataScopeContext.builder()
                .principal(CurrentUser.of(7L, "u07", "赵庚", "A0007", 1351L, 12L, Set.of("employee"), EnumSet.of(
                        DataScopeType.SELF, DataScopeType.COMPANY), false))
                .scopes(EnumSet.of(DataScopeType.SELF, DataScopeType.COMPANY))
                .primaryOrgId(1351L)
                .companyId(12L)
                .build();
        // COMPANY 子树 ∪ SELF 的祖先链 → 集团也在内
        assertThat(OrgVisibility.visibleIds(ctx, TREE)).containsExactlyInAnyOrder(1L, 12L, 135L, 1351L);

        DataScopeContext empty = DataScopeContext.builder()
                .principal(CurrentUser.of(7L, "u07", "赵庚", "A0007", 1351L, 12L, Set.of(), EnumSet.noneOf(DataScopeType.class), false))
                .build();
        assertThat(OrgVisibility.visibleIds(empty, TREE)).isEmpty();
    }

    @Test
    @DisplayName("父链裁剪（W-ORG-016）：父节点被停用过滤掉后，子孙整枝丢弃；显式 rootId 视为子树根")
    void reachableIdsPruning() {
        // includeDisabled=false 的结果集：部门 135 被过滤，只剩它的科室 → 科室整枝丢弃
        List<OrgRef> activeOnly = List.of(
                new OrgRef(1L, null, "/1/"),
                new OrgRef(12L, 1L, "/1/12/"),
                new OrgRef(1351L, 135L, "/1/12/135/1351/"));
        assertThat(OrgVisibility.reachableIds(activeOnly)).containsExactlyInAnyOrder(1L, 12L);

        // 若把 1351 作为本次查询的子树根（rootId=1351），则它自身可见
        assertThat(OrgVisibility.reachableIds(activeOnly, Set.of(1351L)))
                .containsExactlyInAnyOrder(1L, 12L, 1351L);
    }
}
