package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.identity.app.LeaderPolicy.Binding;
import com.oa.identity.domain.IdentityEnums.Category;
import com.oa.identity.domain.IdentityEnums.LeaderType;
import com.oa.identity.domain.IdentityEnums.OrgType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 负责人规则（**纯逻辑单测**）：正职唯一（按 {@code (org_id, category)} 分组）、
 * 业务线绑定与普通负责人互不冲突、业务线只能绑集团层（import-spec E-LEAD-004 / E-LEAD-008 / T-09）。
 */
class LeaderPolicyTest {

    private static Binding primary(Long id, Long orgId, Long userId, Category category) {
        return new Binding(id, orgId, userId, LeaderType.PRIMARY, category);
    }

    private static Binding deputy(Long id, Long orgId, Long userId, Category category) {
        return new Binding(id, orgId, userId, LeaderType.DEPUTY, category);
    }

    @Test
    @DisplayName("正职唯一：同一 (org, category) 已有正职时再绑正职 → 409")
    void primaryUniqueWithinSameGroup() {
        List<Binding> existing = List.of(primary(1L, 100L, 7L, null));
        Binding candidate = primary(null, 100L, 8L, null);
        assertThatThrownBy(() -> LeaderPolicy.assertPrimaryUnique(existing, candidate, "公司A"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至多 1 名正职")
                .hasMessageContaining("公司A");
    }

    @Test
    @DisplayName("副职不限：同一 (org, category) 可有多名副职，也不与正职冲突")
    void deputyUnlimited() {
        List<Binding> existing = List.of(primary(1L, 100L, 7L, null), deputy(2L, 100L, 8L, null));
        LeaderPolicy.assertPrimaryUnique(existing, deputy(null, 100L, 9L, null), "公司A");
        LeaderPolicy.assertPrimaryUnique(existing, deputy(null, 100L, 7L, null), "公司A");
    }

    @Test
    @DisplayName("按 category 分组：业务线（category 非空）与普通负责人（category 为空）互不冲突")
    void categoryGroupsAreIndependent() {
        List<Binding> existing = List.of(primary(1L, 1L, 7L, null));
        // 集团层「经济」业务线正职与「普通负责人」正职各自独立
        LeaderPolicy.assertPrimaryUnique(existing, primary(null, 1L, 9L, Category.ECONOMY), "集团");
        // 同一业务线内第二个正职被拒
        List<Binding> withEconomy = List.of(primary(1L, 1L, 7L, null), primary(2L, 1L, 9L, Category.ECONOMY));
        assertThatThrownBy(() -> LeaderPolicy.assertPrimaryUnique(withEconomy,
                primary(null, 1L, 10L, Category.ECONOMY), "集团"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("经济");
        // 不同业务线之间互不冲突
        LeaderPolicy.assertPrimaryUnique(withEconomy, primary(null, 1L, 11L, Category.HR), "集团");
    }

    @Test
    @DisplayName("同一行自身更新不算冲突（含改 sort/duty_title）；新增一行给另一个人则冲突")
    void selfUpdateIsNotConflict() {
        List<Binding> existing = List.of(primary(1L, 100L, 7L, null));
        // 同一行（id=1）自身的更新：无论字段怎么改，分组内仍然只有 1 条正职 → 不算冲突
        LeaderPolicy.assertPrimaryUnique(existing, primary(1L, 100L, 7L, null), "公司A");
        LeaderPolicy.assertPrimaryUnique(existing, primary(1L, 100L, 7L, Category.ECONOMY), "公司A");
        // 新增一行（id=null）给另一个人 → 冲突
        assertThatThrownBy(() -> LeaderPolicy.assertPrimaryUnique(existing, primary(null, 100L, 8L, null), "公司A"))
                .isInstanceOf(BizException.class);
        // 新增一行给同一个人（同组同类型）→ 库唯一键会拒，但正职唯一判定本身不算冲突
        LeaderPolicy.assertPrimaryUnique(existing, primary(null, 100L, 7L, null), "公司A");
    }

    @Test
    @DisplayName("不同组织之间互不影响；旧码 operate 归一为 business 后同组")
    void differentOrgsAreIndependent() {
        List<Binding> existing = List.of(primary(1L, 100L, 7L, null));
        LeaderPolicy.assertPrimaryUnique(existing, primary(null, 200L, 7L, null), "公司B");
        assertThat(Category.ofCode("operate")).isEqualTo(Category.BUSINESS);
        assertThat(Category.parse("经营")).isEqualTo(Category.BUSINESS);
        assertThat(Category.parse("invest")).isEqualTo(Category.INVEST);
    }

    @Test
    @DisplayName("primaryOf 精确定位分组；全量体检能抓出同组两个正职")
    void primaryOfAndGroupingAudit() {
        List<Binding> bindings = List.of(
                primary(1L, 100L, 7L, null),
                deputy(2L, 100L, 8L, null),
                primary(3L, 100L, 9L, Category.ECONOMY));
        assertThat(LeaderPolicy.primaryOf(bindings, 100L, null)).get().extracting(Binding::userId).isEqualTo(7L);
        assertThat(LeaderPolicy.primaryOf(bindings, 100L, Category.ECONOMY))
                .get().extracting(Binding::userId).isEqualTo(9L);
        assertThat(LeaderPolicy.primaryOf(bindings, 100L, Category.HR)).isEmpty();
        LeaderPolicy.assertPrimaryGrouping(bindings);

        List<Binding> dirty = List.of(primary(1L, 100L, 7L, null), primary(2L, 100L, 8L, null));
        assertThatThrownBy(() -> LeaderPolicy.assertPrimaryGrouping(dirty))
                .isInstanceOf(BizException.class).hasMessageContaining("多个正职负责人");
    }

    @Test
    @DisplayName("业务线只能绑集团层（E-LEAD-008）：公司/部门/科室一律拒绝，普通负责人不受限")
    void businessLineOnlyOnGroup() {
        LeaderPolicy.assertCategoryAllowed(OrgType.GROUP, Category.ECONOMY);
        LeaderPolicy.assertCategoryAllowed(OrgType.COMPANY, null);
        LeaderPolicy.assertCategoryAllowed(OrgType.DEPT, null);
        assertThatThrownBy(() -> LeaderPolicy.assertCategoryAllowed(OrgType.COMPANY, Category.ECONOMY))
                .isInstanceOf(BizException.class).hasMessageContaining("集团层节点");
        assertThatThrownBy(() -> LeaderPolicy.assertCategoryAllowed(OrgType.DEPT, Category.HR))
                .isInstanceOf(BizException.class);
    }
}
