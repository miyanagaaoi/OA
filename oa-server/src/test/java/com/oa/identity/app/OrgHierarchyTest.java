package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.identity.app.OrgHierarchy.SubtreeNode;
import com.oa.identity.domain.IdentityEnums.OrgType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 组织层级与 path/depth 级联重算（**纯逻辑单测，不依赖 DB/Redis/Spring 容器**）。
 *
 * <p>覆盖施工要求点 1 与 2：
 * <ul>
 *   <li>四级层级约束（集团→公司→部门→科室；公司必挂集团、科室必挂部门、部门可挂公司或集团）；</li>
 *   <li>path/depth 一致性；</li>
 *   <li>move 的整棵子树 path 级联重算 + depth 按类型重推。</li>
 * </ul>
 */
class OrgHierarchyTest {

    // ---------------------------------------------------------------- path 结构

    @Test
    @DisplayName("path：根/子节点拼装与归一化都按 /1/12/135/ 口径（含自身）")
    void pathConventions() {
        assertThat(OrgHierarchy.rootPath(1L)).isEqualTo("/1/");
        assertThat(OrgHierarchy.childPath("/1/", 12L)).isEqualTo("/1/12/");
        assertThat(OrgHierarchy.childPath("/1/12/", 135L)).isEqualTo("/1/12/135/");
        // 归一化：容忍不带斜杠的写法
        assertThat(OrgHierarchy.childPath("1/12", 135L)).isEqualTo("/1/12/135/");
        assertThat(OrgHierarchy.normalize("1/12/135")).isEqualTo("/1/12/135/");
        // 非法 id 直接拒绝
        assertThatThrownBy(() -> OrgHierarchy.childPath("/1/", 0L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("path 解析：自身 id、祖先链、直接父级、路段数")
    void pathParsing() {
        String path = "/1/12/135/1351/";
        assertThat(OrgHierarchy.selfId(path)).isEqualTo(1351L);
        assertThat(OrgHierarchy.ancestorIds(path)).containsExactly(1L, 12L, 135L);
        assertThat(OrgHierarchy.parentPath(path)).isEqualTo("/1/12/135/");
        assertThat(OrgHierarchy.parentPath("/1/")).isNull();
        assertThat(OrgHierarchy.depthOf(path)).isEqualTo(4);
        assertThat(OrgHierarchy.isDescendantOrSelf("/1/12/135/1351/", "/1/12/")).isTrue();
        assertThat(OrgHierarchy.isDescendantOrSelf("/1/12/", "/1/12/135/")).isFalse();
    }

    // ---------------------------------------------------------------- 层级约束

    @Nested
    @DisplayName("四级层级约束（import-spec E-ORG-006）")
    class Hierarchy {

        @Test
        @DisplayName("合法组合：集团→公司→部门→科室、部门挂集团（集团职能部门）、部门挂公司")
        void legalCombinations() {
            OrgHierarchy.assertParentChild(null, OrgType.GROUP);
            OrgHierarchy.assertParentChild(OrgType.GROUP, OrgType.COMPANY);
            OrgHierarchy.assertParentChild(OrgType.COMPANY, OrgType.DEPT);
            OrgHierarchy.assertParentChild(OrgType.GROUP, OrgType.DEPT);
            OrgHierarchy.assertParentChild(OrgType.DEPT, OrgType.SECTION);
        }

        @Test
        @DisplayName("非法组合一律 BizException：公司挂公司/部门、科室挂公司/集团、部门挂科室、集团挂父级")
        void illegalCombinations() {
            // 公司必须挂在集团下
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(OrgType.COMPANY, OrgType.COMPANY))
                    .isInstanceOf(BizException.class).hasMessageContaining("公司必须挂在集团下");
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(OrgType.DEPT, OrgType.COMPANY))
                    .isInstanceOf(BizException.class);
            // 科室必须挂在部门下
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(OrgType.COMPANY, OrgType.SECTION))
                    .isInstanceOf(BizException.class).hasMessageContaining("科室必须挂在部门下");
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(OrgType.GROUP, OrgType.SECTION))
                    .isInstanceOf(BizException.class);
            // 部门不能挂科室
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(OrgType.SECTION, OrgType.DEPT))
                    .isInstanceOf(BizException.class);
            // 集团不能有上级；非集团不能作根
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(OrgType.GROUP, OrgType.GROUP))
                    .isInstanceOf(BizException.class).hasMessageContaining("集团节点必须是根节点");
            assertThatThrownBy(() -> OrgHierarchy.assertParentChild(null, OrgType.COMPANY))
                    .isInstanceOf(BizException.class).hasMessageContaining("只有集团节点可以作为根节点");
        }

        @Test
        @DisplayName("org_type 与 depth 必须自洽：depth 由类型决定（1集团 2公司 3部门 4科室）")
        void depthConsistency() {
            for (OrgType type : OrgType.values()) {
                OrgHierarchy.assertDepthConsistent(type, type.depth());
            }
            assertThatThrownBy(() -> OrgHierarchy.assertDepthConsistent(OrgType.DEPT, 2))
                    .isInstanceOf(BizException.class).hasMessageContaining("组织类型与层级不自洽");
            assertThat(OrgType.GROUP.depth()).isEqualTo(1);
            assertThat(OrgType.COMPANY.depth()).isEqualTo(2);
            assertThat(OrgType.DEPT.depth()).isEqualTo(3);
            assertThat(OrgType.SECTION.depth()).isEqualTo(4);
        }

        @Test
        @DisplayName("集团职能部门：部门直挂集团时 path 只有 2 段，但 depth 仍为 3（depth ≠ path 段数）")
        void groupFunctionalDept() {
            String path = OrgHierarchy.childPath("/1/", 135L);
            assertThat(path).isEqualTo("/1/135/");
            assertThat(OrgHierarchy.depthOf(path)).isEqualTo(2);
            assertThat(OrgHierarchy.depthOfType(OrgType.DEPT)).isEqualTo(3);
        }
    }

    // ---------------------------------------------------------------- move 级联

    @Nested
    @DisplayName("move：级联重算整棵子树的 path")
    class Move {

        /** 基线树：集团1 → 公司12（部门135 → 科室1351/1352）、公司20。 */
        private List<SubtreeNode> deptSubtree() {
            return List.of(
                    new SubtreeNode(135L, "/1/12/135/", 3),
                    new SubtreeNode(1351L, "/1/12/135/1351/", 4),
                    new SubtreeNode(1352L, "/1/12/135/1352/", 4));
        }

        @Test
        @DisplayName("科室连带的部门整枝挂到另一家公司：3 个节点的 path 全部前缀替换，顺序与 depth 保持")
        void rebaseWholeSubtree() {
            List<SubtreeNode> rebased = OrgHierarchy.rebaseSubtree(deptSubtree(), "/1/12/135/", "/1/20/135/");
            assertThat(rebased).hasSize(3);
            assertThat(rebased.get(0).path()).isEqualTo("/1/20/135/");
            assertThat(rebased.get(0).depth()).isEqualTo(3);
            assertThat(rebased.get(1).path()).isEqualTo("/1/20/135/1351/");
            assertThat(rebased.get(1).depth()).isEqualTo(4);
            assertThat(rebased.get(2).path()).isEqualTo("/1/20/135/1352/");
            // 自身 id 不变（path 尾段的 id 是节点自身）
            assertThat(OrgHierarchy.selfId(rebased.get(1).path())).isEqualTo(1351L);
        }

        @Test
        @DisplayName("部门从公司下移到集团下（集团职能部门）：path 2 段，depth 仍为类型的 3")
        void rebaseUnderGroupKeepsTypeDepth() {
            List<SubtreeNode> rebased = OrgHierarchy.rebaseSubtree(deptSubtree(), "/1/12/135/", "/1/135/");
            assertThat(rebased.get(0).path()).isEqualTo("/1/135/");
            assertThat(rebased.get(1).path()).isEqualTo("/1/135/1351/");
            // 服务层会用类型重推 depth（部门=3、科室=4），这里断言类型口径
            assertThat(OrgHierarchy.depthOfType(OrgType.DEPT)).isEqualTo(3);
            assertThat(OrgHierarchy.depthOfType(OrgType.SECTION)).isEqualTo(4);
        }

        @Test
        @DisplayName("move 前置校验：不能移到自身/自身子树（环路）")
        void cycleGuards() {
            assertThatThrownBy(() -> OrgHierarchy.assertMovable(135L, "/1/12/135/", OrgType.DEPT,
                    135L, OrgType.DEPT, "/1/12/135/"))
                    .isInstanceOf(BizException.class).hasMessageContaining("自身之下");
            assertThatThrownBy(() -> OrgHierarchy.assertMovable(12L, "/1/12/", OrgType.COMPANY,
                    1351L, OrgType.SECTION, "/1/12/135/1351/"))
                    .isInstanceOf(BizException.class).hasMessageContaining("自己的子树内");
            // 合法移动：部门挂到另一家公司
            OrgHierarchy.assertMovable(135L, "/1/12/135/", OrgType.DEPT, 20L, OrgType.COMPANY, "/1/20/");
        }

        @Test
        @DisplayName("子树外的节点不参与重算（前缀不匹配即拒绝，防止误改其它分支）")
        void rejectsPathOutsidePrefix() {
            assertThatThrownBy(() -> OrgHierarchy.rebaseSubtree(
                    List.of(new SubtreeNode(999L, "/1/30/999/", 3)), "/1/12/135/", "/1/20/135/"))
                    .isInstanceOf(BizException.class).hasMessageContaining("不在重算前缀内");
        }
    }

    // ---------------------------------------------------------------- 业务键（名称路径）

    @Test
    @DisplayName("业务键 org_path：名称路径（集团/公司A/部门1），与 sys_org.path 的 id 路径不同口径")
    void businessPath() {
        String path = OrgHierarchy.businessPath(List.of("集团", "公司A"), "部门1");
        assertThat(path).isEqualTo("集团/公司A/部门1");
        assertThat(OrgHierarchy.parentBusinessPath(path)).isEqualTo("集团/公司A");
        assertThat(OrgHierarchy.parentBusinessPath("集团")).isNull();
        assertThat(OrgHierarchy.businessPath(List.of(), "集团")).isEqualTo("集团");
        assertThatThrownBy(() -> OrgHierarchy.businessPath(List.of("集团"), " "))
                .isInstanceOf(BizException.class);
    }

    // ---------------------------------------------------------------- 临时 path（唯一口径）

    @Test
    @DisplayName("临时 path：格式固定 /pending-<32hex>/，且不可能与真实 path（纯数字段）冲突")
    void temporaryPathFormat() {
        String temp = OrgHierarchy.temporaryPath();
        assertThat(temp).matches("^/pending-[0-9a-f]{32}/$");
        assertThat(temp).isEqualTo(OrgHierarchy.normalize(temp));
        // 真实 path 全是纯数字段（/1/12/135/）；占位 path 的唯一段是 pending-<hex>，非数字 → 字符集上不可能相等
        assertThat(OrgHierarchy.segments(temp)).hasSize(1);
        assertThat(OrgHierarchy.segments(temp).get(0)).doesNotMatch("\\d+");
        // 长度必须远小于 sys_org.path VARCHAR(255)
        assertThat(temp.length()).isLessThan(255);
    }

    @Test
    @DisplayName("临时 path：并发调用全局唯一（uk_sys_org_path 的并发安全前提）")
    void temporaryPathIsUniqueUnderConcurrency() throws Exception {
        int threads = 8;
        int perThread = 250;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.Set<String> all = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    all.add(OrgHierarchy.temporaryPath());
                }
            }));
        }
        for (java.util.concurrent.Future<?> future : futures) {
            future.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(all).hasSize(threads * perThread);
    }
}
