package com.oa.authz.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限树「逐级勾选」策略 —— <b>纯函数</b>（无 Spring、无 IO、无副作用），承载 REQ-ADMIN-003
 * 的勾选语义，因此可被 {@code PermissionTreePolicyTest} 全量穷举断言。
 *
 * <h2>存储策略（二选一，本项目选「展开为全量」）</h2>
 * <ol>
 *   <li><b>只存叶子 + 必要的父节点</b>：行数少，但每次读都必须重新遍历树才能得到
 *       「用户实际拥有的权限码」，而有效权限并集（{@code /auth/me}、{@code /effective-permissions}）
 *       正是**按权限码做集合并**——若存叶子，并集前必须先展开，多个角色的展开结果还要去重合并，
 *       缓存里存的就不是最终形态，容易漏算；</li>
 *   <li><b>展开为全量（本项目采用）</b>：{@code sys_role_permission} 中直接落
 *       {@code G = 祖先闭包( ⋃ 子树(被勾选节点) )}，即「被勾选的节点 + 其全部后代 + 到达这些节点的全部祖先」。
 *       好处是 <b>G 就是该角色的有效权限集合本身</b>：多角色并集 = 集合求并，
 *       {@code /auth/me} 的 {@code permissions} 直接来自它，无需任何树遍历；
 *       代价是行数偏多（权限树规模为数十至数百节点，可接受）。</li>
 * </ol>
 *
 * <h2>语义（REQ-ADMIN-003「逐级勾选」）</h2>
 * <ul>
 *   <li><b>勾选父节点 ⇒ 其全部后代视为已授予</b>：{@link #expand} 对每个被勾选节点取整棵子树；</li>
 *   <li><b>取消父节点 ⇒ 后代全部取消</b>：被取消的节点不出现在入参里，其子树自然不在 G 中
 *       （前端取消父节点时同样不应提交其任何后代；即便提交了，也会因为父节点不在入参中而只被当作
 *       「单独勾选子节点」——见下一条的校验）；</li>
 *   <li><b>父节点未授予时子节点不得单独授予</b>：{@link #requireAncestorClosed} 是这条规则的
 *       机器可验证形式——任何「子 ∈ G 而父 ∉ G」的集合一律 400；{@link #expand} 的产物天然满足它
 *       （因为它做了祖先闭包），{@link #restore} 会把违规项作为体检结果返回；</li>
 *   <li><b>半选态可还原</b>：{@link #restore} 把 G 划分成
 *       {@code checked}（自身及全部后代都在 G 中）/ {@code halfChecked}（自身在 G 中但存在
 *       未授予的后代——即「仅为连通被勾选节点而补上的祖先」）/ 未勾选三类。</li>
 * </ul>
 *
 * <p><b>不变式</b>（{@link #ancestorViolations} 可验证）：G 一定是祖先闭包，
 * 因此 `子 ∈ G ⇒ 父 ∈ G`；再加上子树闭包，G 的「极大已勾选节点」集合完全决定了它。
 */
public final class PermissionTreePolicy {

    private PermissionTreePolicy() {
    }

    // ================================================================ 值对象

    /** 权限树节点（值对象；{@code parentId} 为空即根）。 */
    public record Node(Long id, Long parentId, String code, String name, String permType, String url, Integer sortNo) {

        public Node {
            sortNo = sortNo == null ? 0 : sortNo;
        }

        public static Node of(Long id, Long parentId, String code) {
            return new Node(id, parentId, code, code, "menu", null, 0);
        }
    }

    /**
     * 树快照（父子索引 + 后代/祖先查询）。
     *
     * <p>健壮性：父节点不存在（孤儿）、自指、成环的节点一律**按根节点处理**（断开该边），
     * 保证任何脏数据都不会让展开/还原进入死循环。
     */
    public static final class Tree {

        private final Map<Long, Node> byId;
        private final Map<Long, Long> parentOf;
        private final Map<Long, List<Long>> childrenOf;

        private Tree(Map<Long, Node> byId, Map<Long, Long> parentOf, Map<Long, List<Long>> childrenOf) {
            this.byId = byId;
            this.parentOf = parentOf;
            this.childrenOf = childrenOf;
        }

        public static Tree of(Collection<Node> nodes) {
            Map<Long, Node> byId = new LinkedHashMap<>();
            if (nodes != null) {
                for (Node node : nodes) {
                    if (node == null || node.id() == null) {
                        continue;
                    }
                    byId.putIfAbsent(node.id(), node);
                }
            }
            Map<Long, Long> parentOf = new LinkedHashMap<>();
            for (Node node : byId.values()) {
                parentOf.put(node.id(), effectiveParent(node, byId));
            }
            Map<Long, List<Long>> children = new LinkedHashMap<>();
            for (Map.Entry<Long, Long> entry : parentOf.entrySet()) {
                children.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
            }
            for (List<Long> ids : children.values()) {
                ids.sort((left, right) -> {
                    Node a = byId.get(left);
                    Node b = byId.get(right);
                    int bySort = Integer.compare(a.sortNo(), b.sortNo());
                    return bySort != 0 ? bySort : Long.compare(left, right);
                });
            }
            return new Tree(byId, parentOf, children);
        }

        /** 断环：父不存在/自指/成环一律视为根。 */
        private static Long effectiveParent(Node node, Map<Long, Node> byId) {
            Long parent = node.parentId();
            if (parent == null || parent.equals(node.id()) || !byId.containsKey(parent)) {
                return null;
            }
            Set<Long> seen = new HashSet<>();
            seen.add(node.id());
            Long cursor = parent;
            int guard = 0;
            while (cursor != null && guard++ <= byId.size()) {
                if (!seen.add(cursor)) {
                    return null;
                }
                Long next = byId.get(cursor).parentId();
                cursor = (next != null && !next.equals(cursor) && byId.containsKey(next)) ? next : null;
            }
            return parent;
        }

        public Node node(Long id) {
            return id == null ? null : byId.get(id);
        }

        public boolean contains(Long id) {
            return id != null && byId.containsKey(id);
        }

        public Set<Long> ids() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(byId.keySet()));
        }

        public int size() {
            return byId.size();
        }

        /** 有效的父节点（已断环/去孤儿）；根返回 {@code null}。 */
        public Long parentOf(Long id) {
            return parentOf.get(id);
        }

        /** 直接子节点（按 {@code sort_no, id} 升序）。 */
        public List<Long> childrenOf(Long id) {
            return List.copyOf(childrenOf.getOrDefault(id, List.of()));
        }

        /** 根节点 id（按 {@code sort_no, id} 升序）。 */
        public List<Long> roots() {
            return childrenOf(null);
        }

        public boolean isLeaf(Long id) {
            return childrenOf(id).isEmpty();
        }

        /** 全部后代（**不含自身**），深度优先、按排序稳定。 */
        public List<Long> descendants(Long id) {
            List<Long> result = new ArrayList<>();
            if (!contains(id)) {
                return result;
            }
            Set<Long> visited = new HashSet<>();
            visited.add(id);
            Deque<Long> stack = new ArrayDeque<>();
            List<Long> firstLevel = childrenOf(id);
            for (int i = firstLevel.size() - 1; i >= 0; i--) {
                stack.push(firstLevel.get(i));
            }
            while (!stack.isEmpty()) {
                Long current = stack.pop();
                if (!visited.add(current)) {
                    continue;
                }
                result.add(current);
                List<Long> children = childrenOf(current);
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
            return result;
        }

        /** 全部祖先（**不含自身**），由近及远。 */
        public List<Long> ancestors(Long id) {
            List<Long> result = new ArrayList<>();
            if (!contains(id)) {
                return result;
            }
            Set<Long> visited = new HashSet<>();
            visited.add(id);
            Long cursor = parentOf(id);
            int guard = 0;
            while (cursor != null && guard++ <= byId.size()) {
                if (!visited.add(cursor)) {
                    break;
                }
                result.add(cursor);
                cursor = parentOf(cursor);
            }
            return result;
        }
    }

    /**
     * 一次勾选的还原结果。
     *
     * @param ticked      入参中「被显式勾选」的节点（去重、保序、已校验存在）
     * @param granted     落库集合 G（展开为全量的结果）
     * @param checked     读取态：自身及全部后代都在 G 中
     * @param halfChecked 读取态：自身在 G 中但存在未授予的后代（半选）
     * @param leafGranted G 中的叶子节点（导出为「权限码」时最常用的形态）
     * @param ancestorViolations G 的祖先闭合违规项（正常恒为空；用于脏数据体检）
     */
    public record GrantSet(
            Set<Long> ticked,
            Set<Long> granted,
            Set<Long> checked,
            Set<Long> halfChecked,
            Set<Long> leafGranted,
            List<Long> ancestorViolations
    ) {
    }

    // ================================================================ 写路径

    /**
     * 展开：{@code G = 祖先闭包( ⋃ 子树(被勾选节点) )}。
     *
     * <p>入参中的未知 id 一律 400（防止前端提交已删除节点而静默丢权限）。
     *
     * @param tree      当前权限树
     * @param tickedIds 被显式勾选的节点 id（可为空 = 全部取消）
     * @return 落库集合（有序，祖先闭包）
     */
    public static Set<Long> expand(Tree tree, Collection<Long> tickedIds) {
        List<Long> ticked = distinct(tickedIds);
        requireKnown(tree, ticked);
        Set<Long> granted = new LinkedHashSet<>();
        for (Long id : ticked) {
            granted.add(id);
            granted.addAll(tree.descendants(id));   // 规则①：勾选父节点 ⇒ 其全部后代视为已授予
        }
        for (Long id : List.copyOf(granted)) {
            granted.addAll(tree.ancestors(id));     // 逐级勾选：被勾选节点的祖先链必须一并授予
        }
        Set<Long> ordered = new LinkedHashSet<>();
        for (Long id : tree.ids()) {
            if (granted.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    /** 规范化（= 展开 + 还原），ticks 口径的统一入口。 */
    public static GrantSet normalize(Tree tree, Collection<Long> tickedIds) {
        List<Long> ticked = distinct(tickedIds);
        Set<Long> granted = expand(tree, ticked);
        return restore(tree, granted, ticked);
    }

    /**
     * granted 口径的归一：去重 + 剔除不在树中的 id（**不做子树展开**）。
     *
     * <p>为什么必须不展开：{@code granted} 的入参是「完整已授予集合」，
     * 若对它再做子树展开，一次「原样回传 {@code GET} 结果」的普通保存就会把半选父节点下的
     * **全部兄弟子树**一起授予——那是静默的权限提升。祖先闭合校验由
     * {@link #requireAncestorClosed} 单独负责。
     */
    public static Set<Long> resolveGranted(Tree tree, Collection<Long> grantedIds) {
        Set<Long> granted = new LinkedHashSet<>();
        for (Long id : distinct(grantedIds)) {
            if (tree.contains(id)) {
                granted.add(id);
            }
        }
        return granted;
    }

    /**
     * 入参是「完整已授予集合」还是「全选节点集合」的自判定：判据只有**祖先闭合**一条。
     *
     * <ul>
     *   <li>{@code GET /roles/{id}/permissions} 返回的 {@code permissionIds} 一定是祖先闭包
     *       （写路径强制），前端把它（checked ∪ halfChecked）原样回传时必然闭合
     *       → 视为「已授予集合」，原样落库（幂等）；</li>
     *   <li>复选框只提交叶子（例如仅 {@code {4}}）时父节点缺失 → 不闭合
     *       → 视为「全选节点集合」，按「勾选父 ⇒ 后代全授予」展开并补齐祖先。</li>
     * </ul>
     *
     * <p><b>为什么缺省不能让 ticks 兜底</b>：G 是**祖先闭合**但**不是子树闭合**的
     * （半选父节点在 G 中，其未勾选的子树不在 G 中）。对 G 再做子树展开会把半选父节点下的
     * 兄弟子树一并授权 —— 一次普通回传就变成静默提权。
     */
    public static boolean looksLikeGrantedSet(Tree tree, Collection<Long> ids) {
        return ancestorViolations(tree, ids).isEmpty();
    }

    /**
     * 缺省口径（{@code mode=auto}）：祖先闭合 ⇒ 视为已授予集合；否则 ⇒ 视为全选节点集合。
     *
     * <p>两个分支都不会放宽权限：闭合分支只做去重、绝不新增行；非闭合分支按
     * 「勾选父 ⇒ 后代全授予」补齐——这正是调用方声明「这些是我勾选的节点」时该有的语义。
     *
     * <p>不变式（由测试穷举验证）：
     * {@code resolveAuto(tree, expand(tree, ticks)) == expand(tree, ticks)}，
     * 即「读回 → 原样保存」是**不动点**（既不 400，也不提权）。
     */
    public static Set<Long> resolveAuto(Tree tree, Collection<Long> ids) {
        requireKnown(tree, ids);
        return looksLikeGrantedSet(tree, ids) ? resolveGranted(tree, ids) : expand(tree, ids);
    }

    // ================================================================ 读路径

    /** 还原（不携带原始勾选集）。 */
    public static GrantSet restore(Tree tree, Collection<Long> grantedIds) {
        return restore(tree, grantedIds, List.of());
    }

    /**
     * 还原：把 G 划分为 checked / halfChecked，并给出叶子集合与祖先闭合体检结果。
     *
     * <p>不在树中的 id 会被忽略（权限节点被删除后遗留的脏行不应导致整次读取失败）。
     */
    public static GrantSet restore(Tree tree, Collection<Long> grantedIds, Collection<Long> tickedIds) {
        Set<Long> granted = new LinkedHashSet<>();
        for (Long id : distinct(grantedIds)) {
            if (tree.contains(id)) {
                granted.add(id);
            }
        }
        Set<Long> checked = new LinkedHashSet<>();
        Set<Long> half = new LinkedHashSet<>();
        Set<Long> leaves = new LinkedHashSet<>();
        for (Long id : granted) {
            List<Long> descendants = tree.descendants(id);
            if (granted.containsAll(descendants)) {
                checked.add(id);
            } else {
                half.add(id);
            }
            if (descendants.isEmpty()) {
                leaves.add(id);
            }
        }
        Set<Long> ticked = new LinkedHashSet<>();
        for (Long id : distinct(tickedIds)) {
            if (tree.contains(id)) {
                ticked.add(id);
            }
        }
        return new GrantSet(
                Collections.unmodifiableSet(ticked),
                Collections.unmodifiableSet(granted),
                Collections.unmodifiableSet(checked),
                Collections.unmodifiableSet(half),
                Collections.unmodifiableSet(leaves),
                ancestorViolations(tree, granted));
    }

    // ================================================================ 校验

    /**
     * 「父节点未授予时子节点不得单独授予」的校验语义。
     *
     * @throws BizException 400，消息中列出全部违规子节点
     */
    public static void requireAncestorClosed(Tree tree, Collection<Long> grantedIds) {
        List<Long> violations = ancestorViolations(tree, grantedIds);
        if (!violations.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "父节点未授予时子节点不得单独授予：违规权限 id " + violations);
        }
    }

    /** 祖先闭合违规项（{@code 子 ∈ G 而父 ∉ G}），升序；空列表 = 合法。 */
    public static List<Long> ancestorViolations(Tree tree, Collection<Long> grantedIds) {
        Set<Long> granted = new LinkedHashSet<>(distinct(grantedIds));
        List<Long> violations = new ArrayList<>();
        for (Long id : granted) {
            if (!tree.contains(id)) {
                continue;
            }
            Long parent = tree.parentOf(id);
            if (parent != null && !granted.contains(parent)) {
                violations.add(id);
            }
        }
        violations.sort(Long::compareTo);
        return violations;
    }

    /** 入参中的未知权限 id 一律 400。 */
    public static void requireKnown(Tree tree, Collection<Long> ids) {
        List<Long> unknown = new ArrayList<>();
        for (Long id : distinct(ids)) {
            if (!tree.contains(id)) {
                unknown.add(id);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "权限节点不存在（可能已被删除）：" + unknown);
        }
    }

    private static List<Long> distinct(Collection<Long> ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
