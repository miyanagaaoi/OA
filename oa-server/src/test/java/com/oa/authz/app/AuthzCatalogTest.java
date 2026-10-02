package com.oa.authz.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 角色码 / 数据域 / 类别三个目录的纯函数单测。
 *
 * <p>验收点：
 * <ol>
 *   <li>角色码权威源 = {@code sys_role.code} 列注释的 9 个码，且格式为小写蛇形；</li>
 *   <li>数据域只允许五值（与 DDL 的 {@code chk_sys_role_scope} 一致）；</li>
 *   <li>{@code group_category} 角色**必须同时配置类别范围**，否则 400；</li>
 *   <li>类别只允许五值 {@code business/economy/admin/hr/invest}，旧码 {@code operate} 已作废。</li>
 * </ol>
 */
class AuthzCatalogTest {

    // ---------------------------------------------------------------- 角色码

    @Test
    @DisplayName("内置角色码 = data-model.md 3.1 sys_role.code 列注释的 9 个码（逐字）")
    void builtInRoleCodesMatchDdlComment() {
        assertThat(RoleCatalog.BUILT_IN_CODES).containsExactly(
                "admin", "company_admin", "employee", "dept_leader", "branch_leader",
                "subsidiary_gm", "finance_owner", "group_leader", "chairman");
        assertThat(RoleCatalog.isBuiltIn("chairman")).isTrue();
        assertThat(RoleCatalog.isBuiltIn("group_exec")).isFalse();
        assertThat(RoleCatalog.isBuiltIn("gm")).isFalse();
        assertThat(RoleCatalog.isBuiltIn(null)).isFalse();
    }

    @Test
    @DisplayName("角色码格式：小写蛇形；大写/连字符/首字符数字/超长一律 400")
    void roleCodeFormatIsEnforced() {
        assertThat(RoleCatalog.requireValidCode("custom_role")).isEqualTo("custom_role");
        assertThat(RoleCatalog.requireValidCode("Custom_Role")).isEqualTo("custom_role");
        for (String invalid : List.of("", "A", "1abc", "a-b", "a".repeat(33))) {
            assertThatThrownBy(() -> RoleCatalog.requireValidCode(invalid))
                    .isInstanceOf(BizException.class)
                    .isInstanceOfSatisfying(BizException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        }
    }

    @Test
    @DisplayName("内置码不得通过接口新建（409，指向 uk_sys_role_code）")
    void builtInCodesCannotBeCreated() {
        assertThatThrownBy(() -> RoleCatalog.requireNotBuiltInForCreate("admin"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内置角色码")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE));
        assertThatCode(() -> RoleCatalog.requireNotBuiltInForCreate("custom_role")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("role_scope 只允许 group|company")
    void roleScopeIsEnforced() {
        assertThat(RoleCatalog.requireValidRoleScope("GROUP")).isEqualTo("group");
        assertThat(RoleCatalog.requireValidRoleScope("company")).isEqualTo("company");
        assertThatThrownBy(() -> RoleCatalog.requireValidRoleScope("dept"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("role_scope");
    }

    // ---------------------------------------------------------------- 数据域

    @Test
    @DisplayName("数据域只允许五值（与 DataScopeType / DDL CHECK 约束一致）")
    void dataScopeAllowsExactlyFiveValues() {
        assertThat(DataScopeCatalog.values())
                .containsExactly("self", "dept", "company", "group_all", "group_category");
        assertThat(DataScopeCatalog.entries()).hasSize(5);
        assertThat(DataScopeCatalog.requireValid(" Company ")).isEqualTo("company");
        for (String invalid : List.of("", "all", "group", "self_dept")) {
            assertThatThrownBy(() -> DataScopeCatalog.requireValid(invalid))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("data_scope")
                    .isInstanceOfSatisfying(BizException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        }
    }

    @Test
    @DisplayName("data_scope=group_category 必须同时配置类别范围，否则 400")
    void groupCategoryRequiresCategories() {
        assertThatThrownBy(() -> DataScopeCatalog.requireCategoriesForGroupCategory("group_category", List.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("sys_role_category")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> DataScopeCatalog.requireCategoriesForGroupCategory("group_category", null))
                .isInstanceOf(BizException.class);
        assertThatCode(() -> DataScopeCatalog.requireCategoriesForGroupCategory("group_category", List.of("business")))
                .doesNotThrowAnyException();
        // 其余四值不要求类别
        assertThatCode(() -> DataScopeCatalog.requireCategoriesForGroupCategory("self", List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> DataScopeCatalog.requireCategoriesForGroupCategory("group_all", null))
                .doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- 类别

    @Test
    @DisplayName("类别只允许五值 business/economy/admin/hr/invest；旧码 operate 已作废")
    void categoryAllowsExactlyFiveValues() {
        assertThat(CategoryCatalog.values()).containsExactly("business", "economy", "admin", "hr", "invest");
        assertThat(CategoryCatalog.requireValid("BUSINESS")).isEqualTo("business");
        assertThat(CategoryCatalog.isValid("operate")).isFalse();
        assertThatThrownBy(() -> CategoryCatalog.requireValid("operate"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("事项类别取值不合法");
    }

    @Test
    @DisplayName("类别批量归一化：去重保序、空值忽略、任一非法即 400")
    void categoryBatchNormalization() {
        assertThat(CategoryCatalog.normalizeAll(java.util.Arrays.asList("invest", "business", "INVEST", " ", null)))
                .isEqualTo(Set.of("invest", "business"));
        assertThat(CategoryCatalog.normalizeAll(null)).isEmpty();
        assertThatThrownBy(() -> CategoryCatalog.normalizeAll(List.of("business", "operate")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("operate");
    }
}
