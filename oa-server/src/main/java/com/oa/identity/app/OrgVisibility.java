package com.oa.identity.app;

import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeType;
import com.oa.identity.domain.SysOrg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织节点可见性 —— <b>纯函数</b>（输入数据域上下文 + 组织快照，输出可见 id 集合）。
 *
 * <h2>为什么需要它</h2>
 * {@code DataScopeSqlBuilder} 目前只有 **INSTANCE / USER** 两类口径，没有 ORG 口径，
 * 因此 {@code sys_org} 以 {@code DataScopeKind.NONE} 登记（标记只用于「禁止裸查询」）。
 * 组织树/搜索/详情的可见性因此必须由应用层按调用人的数据域裁出来 —— 本类就是这个裁剪口径的**唯一实现**，
 * 与 {@link com.oa.common.scope.DataScopeSqlBuilder#buildUserScope} 的用户口径**同源**：
 *
 * <table>
 *   <tr><th>数据域</th><th>可见组织节点</th></tr>
 *   <tr><td>{@code self}</td><td>本人主归属节点 + 其**祖先链**（否则树无法渲染出路径）</td></tr>
 *   <tr><td>{@code dept}</td><td>本部门/科室子树（{@code dept_path_prefix} 前缀）∪ self</td></tr>
 *   <tr><td>{@code company}</td><td>本公司子树（按 {@code companyId} 节点的 path 前缀）</td></tr>
 *   <tr><td>{@code group_category}</td><td>归口部门子树（{@code financeDeptPathPrefix}）；缺失时退化为本公司</td></tr>
 *   <tr><td>{@code group_all}</td><td>全部（{@code isBypass()}）</td></tr>
 * </table>
 *
 * <p>多角色取**并集**（与 SQL 侧 {@code OR} 拼装一致）；
 * 上下文缺失或无任何可用口径 → **空集**（fail-closed，宁可看不到也不越权）。
 */
public final class OrgVisibility {

    /** 组织快照（只取可见性判定需要的三列）。 */
    public record OrgRef(long id, Long parentId, String path) {

        public static OrgRef of(SysOrg org) {
            return new OrgRef(org.getId() == null ? 0L : org.getId(), org.getParentId(), org.getPath());
        }
    }

    private OrgVisibility() {
    }

    /** 计算可见组织 id 集合；上下文为 {@code null} 时返回空集（未认证上下文不得看到任何节点）。 */
    public static Set<Long> visibleIds(DataScopeContext context, List<OrgRef> all) {
        Set<Long> result = new LinkedHashSet<>();
        if (all == null || all.isEmpty()) {
            return result;
        }
        if (context == null) {
            return result;
        }
        if (context.isBypass()) {
            for (OrgRef ref : all) {
                result.add(ref.id());
            }
            return result;
        }
        for (DataScopeType type : context.getScopes()) {
            switch (type) {
                case SELF -> addSelf(result, context, all);
                case DEPT -> addSubtree(result, all, normalize(context.getDeptPathPrefix()));
                case COMPANY -> addSubtree(result, all, pathOf(all, context.getCompanyId()));
                case GROUP_CATEGORY -> {
                    String prefix = normalize(context.getFinanceDeptPathPrefix());
                    if (prefix == null) {
                        // 归口部门路径未知时退化为本公司（与 DataScopeSqlBuilder 的用户口径一致）
                        addSubtree(result, all, pathOf(all, context.getCompanyId()));
                    } else {
                        addSubtree(result, all, prefix);
                    }
                }
                case GROUP_ALL -> {
                    for (OrgRef ref : all) {
                        result.add(ref.id());
                    }
                }
                default -> {
                    // 其它口径不涉及组织节点可见性
                }
            }
        }
        return result;
    }

    /**
     * 计算「父链完整」的可见节点 id（用于 {@code includeDisabled=false} 的场景）。
     *
     * <p>口径依据 import-spec {@code W-ORG-016}「父组织已停用，子节点不可作为发起归属」：
     * 若某节点的**任一祖先**不在结果集中，则该节点整枝丢弃（不做「上提挂到最近可见祖先」，
     * 避免出现「父已停用但子孙仍可选」的越权选择项）。
     *
     * @param nodes 已按状态过滤后的节点快照
     * @return 父链完整（走到 {@code parentId == null}）的节点 id
     */
    public static Set<Long> reachableIds(List<OrgVisibility.OrgRef> nodes) {
        return reachableIds(nodes, Set.of());
    }

    /**
     * 同上，但把 {@code allowedRoots} 中的节点视为「子树根」（其父不参与本次结果集）。
     *
     * <p>用途：{@code GET /orgs/tree?rootId=…} 只查询子树的场景——子树根的 {@code parent_id} 非空，
     * 若按全局父链处理会被误判为不可见。
     */
    public static Set<Long> reachableIds(List<OrgVisibility.OrgRef> nodes, Set<Long> allowedRoots) {
        Set<Long> result = new LinkedHashSet<>();
        if (nodes == null || nodes.isEmpty()) {
            return result;
        }
        Set<Long> roots = allowedRoots == null ? Set.of() : allowedRoots;
        Map<Long, OrgRef> index = new HashMap<>();
        for (OrgRef ref : nodes) {
            index.put(ref.id(), ref);
        }
        for (OrgRef ref : nodes) {
            if (roots.contains(ref.id()) || rooted(index, ref)) {
                result.add(ref.id());
            }
        }
        return result;
    }

    /** 父链是否完整（根节点视为完整）。 */
    private static boolean rooted(Map<Long, OrgRef> index, OrgRef ref) {
        Set<Long> visited = new LinkedHashSet<>();
        OrgRef current = ref;
        while (current != null) {
            if (current.parentId() == null) {
                return true;
            }
            if (!visited.add(current.id())) {
                // 环路（数据异常）：按不可见处理，避免死循环
                return false;
            }
            current = index.get(current.parentId());
        }
        return false;
    }

    private static void addSelf(Set<Long> result, DataScopeContext context, List<OrgRef> all) {
        Long primaryOrgId = context.getPrimaryOrgId();
        if (primaryOrgId == null) {
            return;
        }
        OrgRef self = find(all, primaryOrgId);
        if (self == null) {
            return;
        }
        result.add(self.id());
        // 祖先链：树渲染需要知道路径
        for (Long ancestorId : OrgHierarchy.ancestorIds(self.path())) {
            result.add(ancestorId);
        }
    }

    private static void addSubtree(Set<Long> result, List<OrgRef> all, String prefix) {
        if (prefix == null) {
            return;
        }
        for (OrgRef ref : all) {
            if (ref.path() != null && OrgHierarchy.normalize(ref.path()).startsWith(prefix)) {
                result.add(ref.id());
            }
        }
    }

    private static OrgRef find(List<OrgRef> all, Long id) {
        if (id == null) {
            return null;
        }
        for (OrgRef ref : all) {
            if (id.equals(ref.id())) {
                return ref;
            }
        }
        return null;
    }

    private static String pathOf(List<OrgRef> all, Long id) {
        OrgRef ref = find(all, id);
        return ref == null ? null : normalize(ref.path());
    }

    private static String normalize(String path) {
        return path == null || path.isBlank() ? null : OrgHierarchy.normalize(path);
    }

    /** 便捷方法：把 id 集合映射为节点列表（保持入参顺序）。 */
    public static List<SysOrg> filter(List<SysOrg> all, Set<Long> visibleIds) {
        List<SysOrg> result = new ArrayList<>();
        if (all == null) {
            return result;
        }
        for (SysOrg org : all) {
            if (visibleIds != null && visibleIds.contains(org.getId())) {
                result.add(org);
            }
        }
        return result;
    }
}
