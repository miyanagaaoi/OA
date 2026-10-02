package com.oa.authz.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 权限树「逐级勾选」纯函数单测（REQ-ADMIN-003）。
 *
 * <p>覆盖验收点：
 * <ol>
 *   <li>勾选父节点 ⇒ 其全部后代视为已授予；</li>
 *   <li>取消父节点 ⇒ 后代全部取消；</li>
 *   <li>保存时规范化为**展开为全量**（{@code G = 祖先闭包(⋃ 子树(勾选节点))}）；</li>
 *   <li>查询时能还原半选态（父节点部分子节点被选）；</li>
 *   <li>「父节点未授予时子节点不得单独授予」的校验语义（400）；</li>
 *   <li>未知权限 id、脏数据（孤儿/自指/成环）的健壮性。</li>
 * </ol>
 *
 * <p>纯逻辑单测：不连 DB、不起 Spring 容器。
 */
class PermissionTreePolicyTest {

    /**
     * <pre>
     * 1 oa:dashboard
     * ├── 2 oa:form
     * │   ├── 4 oa:form:create
     * │   └── 5 oa:form:delete
     * └── 3 oa:admin
     *     └── 6 oa:admin:user
     * 10 oa:report
     * └── 11 oa:report:export
     * </pre>
     */
    private static PermissionTreePolicy.Tree tree() {
        return PermissionTreePolicy.Tree.of(List.of(
                node(1L, null, "oa:dashboard"),
                node(2L, 1L, "oa:form"),
                node(3L, 1L, "oa:admin"),
                node(4L, 2L, "oa:form:create"),
                node(5L, 2L, "oa:form:delete"),
                node(6L, 3L, "oa:admin:user"),
                node(10L, null, "oa:report"),
                node(11L, 10L, "oa:report:export")));
    }

    private static PermissionTreePolicy.Node node(Long id, Long parentId, String code) {
        return new PermissionTreePolicy.Node(id, parentId, code, code, "menu", null, id.intValue());
    }

    // ---------------------------------------------------------------- 规则① 勾选父 ⇒ 后代全授予

    @Test
    @DisplayName("勾选父节点 ⇒ 其全部后代视为已授予（子树闭包）")
    void tickingParentGrantsAllDescendants() {
        PermissionTreePolicy.Tree tree = tree();

        assertThat(PermissionTreePolicy.expand(tree, List.of(2L)))
                .containsExactlyInAnyOrder(1L, 2L, 4L, 5L);
        assertThat(PermissionTreePolicy.expand(tree, List.of(1L)))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(PermissionTreePolicy.expand(tree, List.of(10L)))
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("勾选根节点 ⇒ 该根整棵子树已授予（父节点全选态）")
    void tickingRootMakesSubtreeFullyChecked() {
        PermissionTreePolicy.Tree tree = tree();

        PermissionTreePolicy.GrantSet grantSet = PermissionTreePolicy.normalize(tree, List.of(10L));

        assertThat(grantSet.granted()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(grantSet.checked()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(grantSet.halfChecked()).isEmpty();
    }

    // ---------------------------------------------------------------- 规则② 取消父 ⇒ 后代全取消

    @Test
    @DisplayName("取消父节点 ⇒ 后代全部取消（空勾选集 = 全部取消）")
    void untickingParentRemovesDescendants() {
        PermissionTreePolicy.Tree tree = tree();

        assertThat(PermissionTreePolicy.expand(tree, List.of())).isEmpty();
        assertThat(PermissionTreePolicy.normalize(tree, List.of()).granted()).isEmpty();

        // 先全选再全取消：结果与从未勾选一致（幂等）
        assertThat(PermissionTreePolicy.expand(tree, List.of(1L))).isNotEmpty();
        assertThat(PermissionTreePolicy.expand(tree, List.of())).isEmpty();
    }

    @Test
    @DisplayName("保存为 ticks 时可稳定往返：用 checked 集合再次提交结果不变")
    void ticksRoundTripIsStable() {
        PermissionTreePolicy.Tree tree = tree();

        PermissionTreePolicy.GrantSet first = PermissionTreePolicy.normalize(tree, List.of(4L, 5L));
        assertThat(first.granted()).containsExactlyInAnyOrder(1L, 2L, 4L, 5L);

        PermissionTreePolicy.GrantSet second = PermissionTreePolicy.normalize(tree, List.copyOf(first.checked()));
        assertThat(second.granted()).isEqualTo(first.granted());
    }

    // ---------------------------------------------------------------- 规则④ 半选态还原

    @Test
    @DisplayName("半选态还原：父节点在 G 中但存在未授予的后代 ⇒ halfChecked")
    void restoreReconstructsHalfCheckedState() {
        PermissionTreePolicy.Tree tree = tree();

        // 勾选 4（叶子）：祖先 1、2 被补齐，但 2 的另一个子节点 5、1 的另一个子节点 3 未授予
        PermissionTreePolicy.GrantSet grantSet = PermissionTreePolicy.normalize(tree, List.of(4L));

        assertThat(grantSet.granted()).containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(grantSet.checked()).containsExactlyInAnyOrder(4L);
        assertThat(grantSet.halfChecked()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(grantSet.leafGranted()).containsExactlyInAnyOrder(4L);
    }

    @Test
    @DisplayName("半选态还原：兄弟节点全被勾选后父节点升级为全选")
    void parentBecomesFullyCheckedWhenAllChildrenGranted() {
        PermissionTreePolicy.Tree tree = tree();

        PermissionTreePolicy.GrantSet grantSet = PermissionTreePolicy.normalize(tree, List.of(4L, 5L));

        assertThat(grantSet.granted()).containsExactlyInAnyOrder(1L, 2L, 4L, 5L);
        assertThat(grantSet.checked()).containsExactlyInAnyOrder(2L, 4L, 5L);
        assertThat(grantSet.halfChecked()).containsExactlyInAnyOrder(1L);
    }

    @Test
    @DisplayName("restore 直接按落库集合还原半选态（读取路径）")
    void restoreFromStoredGrantedSet() {
        PermissionTreePolicy.Tree tree = tree();

        PermissionTreePolicy.GrantSet grantSet = PermissionTreePolicy.restore(tree, List.of(1L, 2L, 4L));

        assertThat(grantSet.granted()).containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(grantSet.checked()).containsExactlyInAnyOrder(4L);
        assertThat(grantSet.halfChecked()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(grantSet.ancestorViolations()).isEmpty();
    }

    // ---------------------------------------------------------------- 规则⑤ 父未授予则子不得授予

    @Test
    @DisplayName("父节点未授予时子节点不得单独授予 → 400")
    void requireAncestorClosedRejectsChildWithoutParent() {
        PermissionTreePolicy.Tree tree = tree();

        assertThatThrownBy(() -> PermissionTreePolicy.requireAncestorClosed(tree, List.of(2L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("父节点未授予时子节点不得单独授予")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> PermissionTreePolicy.requireAncestorClosed(tree, List.of(4L, 1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("父节点未授予时子节点不得单独授予");
        assertThatThrownBy(() -> PermissionTreePolicy.requireAncestorClosed(tree, List.of(11L)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("ancestorViolations：精确列出「子已授予而父未授予」的节点（升序）")
    void ancestorViolationsListsExactIds() {
        PermissionTreePolicy.Tree tree = tree();

        assertThat(PermissionTreePolicy.ancestorViolations(tree, List.of(4L, 4L, 11L))).containsExactly(4L, 11L);
        assertThat(PermissionTreePolicy.ancestorViolations(tree, List.of(1L, 2L, 4L))).isEmpty();
        assertThat(PermissionTreePolicy.ancestorViolations(tree, List.of())).isEmpty();
    }

    @Test
    @DisplayName("expand 的产物天然祖先闭合（任意勾选组合都不触发违规）")
    void expandAlwaysProducesAncestorClosedSets() {
        PermissionTreePolicy.Tree tree = tree();

        List<Long> candidates = List.of(1L, 2L, 3L, 4L, 5L, 6L, 10L, 11L);
        int combinations = 1 << candidates.size();
        for (int mask = 0; mask < combinations; mask++) {
            List<Long> ticks = new java.util.ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    ticks.add(candidates.get(i));
                }
            }
            Set<Long> granted = PermissionTreePolicy.expand(tree, ticks);
            assertThat(PermissionTreePolicy.ancestorViolations(tree, granted))
                    .as("勾选组合 %s 的展开结果必须祖先闭合", ticks)
                    .isEmpty();
            // 祖先闭合 = 每个被勾选节点自身也在 G 中（勾选即授予）
            assertThat(granted).containsAll(ticks);
        }
    }

    // ---------------------------------------------------------------- 入参校验 / 健壮性

    @Test
    @DisplayName("未知权限 id → 400（不得静默丢弃，避免「看似保存成功实则丢权限」）")
    void unknownPermissionIdIsRejected() {
        PermissionTreePolicy.Tree tree = tree();

        assertThatThrownBy(() -> PermissionTreePolicy.expand(tree, List.of(1L, 999L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("权限节点不存在")
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    @DisplayName("脏数据健壮性：孤儿 / 自指 / 成环一律按根处理，不会死循环")
    void dirtyTreeDoesNotLoopForever() {
        PermissionTreePolicy.Tree tree = PermissionTreePolicy.Tree.of(List.of(
                node(1L, 999L, "orphan"),        // 父不存在 → 根
                node(2L, 2L, "self"),            // 自指 → 根
                node(3L, 4L, "cycle-a"),         // 3 → 4 → 3 成环 → 断边
                node(4L, 3L, "cycle-b")));

        assertThat(tree.roots()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(PermissionTreePolicy.expand(tree, List.of(3L))).containsExactlyInAnyOrder(3L);
        assertThat(tree.descendants(3L)).isEmpty();
        assertThat(tree.ancestors(3L)).isEmpty();
        // 成环的两条边被断开后，任一节点都不会无限向上/向下遍历
        assertThat(tree.ancestors(4L)).isEmpty();
        assertThat(tree.descendants(4L)).isEmpty();
    }

    @Test
    @DisplayName("重复入参去重；父节点 id 为空即根")
    void duplicatesAreDeduplicated() {
        PermissionTreePolicy.Tree tree = tree();

        assertThat(PermissionTreePolicy.expand(tree, java.util.Arrays.asList(4L, 4L, 4L, null)))
                .containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(tree.roots()).containsExactly(1L, 10L);
    }

    @Test
    @DisplayName("granted 口径只做归一不做子树展开（避免普通回传造成静默提权）")
    void resolveGrantedDoesNotCascade() {
        PermissionTreePolicy.Tree tree = tree();

        assertThat(PermissionTreePolicy.resolveGranted(tree, java.util.Arrays.asList(1L, 2L, 4L, 4L, null)))
                .containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(PermissionTreePolicy.resolveGranted(tree, List.of(999L))).isEmpty();
    }

    // ---------------------------------------------------------------- 缺省 auto 口径

    @Test
    @DisplayName("auto 判据：祖先闭合 ⇒ 已授予集合；叶子/局部勾选 ⇒ 全选节点集合")
    void autoInterpretationDetectsAncestorClosure() {
        PermissionTreePolicy.Tree tree = tree();

        // 祖先闭合（= GET 返回的 permissionIds / 前端 checked ∪ halfChecked）
        assertThat(PermissionTreePolicy.looksLikeGrantedSet(tree, List.of(1L, 2L, 4L))).isTrue();
        assertThat(PermissionTreePolicy.looksLikeGrantedSet(tree, List.of())).isTrue();
        assertThat(PermissionTreePolicy.looksLikeGrantedSet(tree, List.of(1L))).isTrue();
        // 不闭合（只提交叶子 / 缺祖先）
        assertThat(PermissionTreePolicy.looksLikeGrantedSet(tree, List.of(4L))).isFalse();
        assertThat(PermissionTreePolicy.looksLikeGrantedSet(tree, List.of(2L, 4L))).isFalse();
    }

    @Test
    @DisplayName("auto：祖先闭合的入参原样保留（半选父节点下的兄弟子树**不会**被连锁授予）")
    void autoKeepsClosedPayloadUntouched() {
        PermissionTreePolicy.Tree tree = tree();

        // 只勾了 4（叶子），G = {1,2,4}；半选父 1 的另一个子节点 3/6、2 的另一个子节点 5 都不该被授予
        assertThat(PermissionTreePolicy.resolveAuto(tree, List.of(1L, 2L, 4L)))
                .containsExactlyInAnyOrder(1L, 2L, 4L);
    }

    @Test
    @DisplayName("auto：不闭合的入参按「勾选父 ⇒ 后代全授予 + 补齐祖先」展开")
    void autoExpandsNonClosedPayload() {
        PermissionTreePolicy.Tree tree = tree();

        assertThat(PermissionTreePolicy.resolveAuto(tree, List.of(4L)))
                .containsExactlyInAnyOrder(1L, 2L, 4L);
        assertThat(PermissionTreePolicy.resolveAuto(tree, List.of(2L)))
                .containsExactlyInAnyOrder(1L, 2L, 4L, 5L);
        assertThat(PermissionTreePolicy.resolveAuto(tree, List.of())).isEmpty();
    }

    @Test
    @DisplayName("不变式：读回 → 原样保存是**不动点**（既不 400 也不提权）")
    void autoIsFixedPointOnEveryClosure() {
        PermissionTreePolicy.Tree tree = tree();
        List<Long> candidates = List.of(1L, 2L, 3L, 4L, 5L, 6L, 10L, 11L);
        int combinations = 1 << candidates.size();

        for (int mask = 0; mask < combinations; mask++) {
            List<Long> ticks = new java.util.ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    ticks.add(candidates.get(i));
                }
            }
            Set<Long> stored = PermissionTreePolicy.expand(tree, ticks);   // 一次保存后的落库集合 G
            // GET 会把 checked ∪ halfChecked（= G）回传，且不传 mode ⇒ auto
            assertThat(PermissionTreePolicy.resolveAuto(tree, List.copyOf(stored)))
                    .as("闭包 %s 在 auto 口径下必须是不动点", stored)
                    .isEqualTo(stored);
            // 也必然通过「父未授予则子不得单独授予」校验（granted 口径同样不 400）
            assertThat(PermissionTreePolicy.ancestorViolations(tree, stored)).isEmpty();
        }
    }
}
