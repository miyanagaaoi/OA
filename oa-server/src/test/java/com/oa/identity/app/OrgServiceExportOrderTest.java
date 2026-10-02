package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.identity.domain.SysOrg;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 导出顺序（**纯逻辑单测**）：树的先序（父先于子，同级按 sort_no、id）。
 *
 * <p>依据 import-spec §2.2 的前置条件「父路径必须已在文件内或库内存在」与 §9.1 的往返约束：
 * 导出的 {@code org.csv} 必须能被校验器直接通过，因此**父行必须排在子行之前**。
 * SQL 的 {@code ORDER BY path} 做不到这点（{@code path} 是 id 路径，字符串序 ≠ 层级序：
 * {@code /1/12/} 会排在 {@code /1/2/} 之前），故在应用层按树先序排序。
 */
class OrgServiceExportOrderTest {

    private static SysOrg org(long id, Long parentId, String path, String name, int sortNo) {
        SysOrg org = new SysOrg();
        org.setId(id);
        org.setParentId(parentId);
        org.setPath(path);
        org.setName(name);
        org.setSortNo(sortNo);
        return org;
    }

    @Test
    @DisplayName("先序：父先于子；同级按 sort_no 再按 id（与 SQL 的字符串 path 序不同）")
    void preOrderParentBeforeChild() {
        List<SysOrg> all = new ArrayList<>(List.of(
                org(1351L, 135L, "/1/12/135/1351/", "科室1-1", 0),
                org(2L, 1L, "/1/2/", "公司B", 2),
                org(135L, 12L, "/1/12/135/", "部门1", 1),
                org(1L, null, "/1/", "集团", 0),
                org(12L, 1L, "/1/12/", "公司A", 1),
                org(1352L, 135L, "/1/12/135/1352/", "科室1-2", 0)));

        List<Long> order = ids(OrgService.exportOrder(all));
        // 深度优先先序：集团 → 公司A（sort 1）→ 其整棵子树（部门1 → 科室1-1、科室1-2）→ 公司B（sort 2）
        assertThat(order).containsExactly(1L, 12L, 135L, 1351L, 1352L, 2L);
        // 每个节点的父节点都出现在它之前
        for (SysOrg item : OrgService.exportOrder(all)) {
            if (item.getParentId() != null) {
                assertThat(order.indexOf(item.getParentId()))
                        .as("父节点必须先于子节点：%s", item.getName())
                        .isLessThan(order.indexOf(item.getId()));
            }
        }
    }

    @Test
    @DisplayName("孤儿节点（父不在结果集内）也不会丢：作为根节点排在最后，避免导出漏行")
    void orphanNodesAreNotDropped() {
        List<SysOrg> all = List.of(
                org(1L, null, "/1/", "集团", 0),
                org(12L, 1L, "/1/12/", "公司A", 1),
                org(900L, 899L, "/1/899/900/", "孤儿部门", 0));
        assertThat(ids(OrgService.exportOrder(all))).containsExactly(1L, 12L, 900L);
    }

    private static List<Long> ids(List<SysOrg> orgs) {
        List<Long> ids = new ArrayList<>(orgs.size());
        for (SysOrg org : orgs) {
            ids.add(org.getId());
        }
        return ids;
    }
}
