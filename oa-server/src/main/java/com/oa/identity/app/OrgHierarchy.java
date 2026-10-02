package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.domain.IdentityEnums.OrgType;
import java.util.ArrayList;
import java.util.List;

/**
 * 组织层级与路径规则 —— <b>纯函数</b>（不依赖 Spring / DB，可全量单测）。
 *
 * <p>权威依据：
 * <ul>
 *   <li>doc/prd-0.1.md §5.1「集团-公司-部门-科室 四级架构」；</li>
 *   <li>doc/import-spec.md §4.2 {@code E-ORG-006}：层级必须为 集团→公司→部门→科室；
 *       <b>公司必须挂在集团下</b>；<b>科室必须挂在部门下</b>；<b>部门可挂公司或集团</b>（集团职能部门）；</li>
 *   <li>doc/data-model.md §2.1 {@code path} 注释「祖先路径 {@code /1/12/135/}，含自身」，
 *       {@code depth} 注释「1集团 2公司 3部门 4科室」。</li>
 * </ul>
 *
 * <h2>path 口径（唯一口径，勿在别处重写）</h2>
 * <pre>
 *   根（集团，id=1）        path = "/1/"
 *   公司（id=12，父 1）     path = "/1/12/"
 *   部门（id=135，父 12）   path = "/1/12/135/"
 * </pre>
 * 即：{@code path = parent.path + selfId + "/"}，根节点 {@code path = "/" + selfId + "/"}。
 *
 * <h2>depth 口径（重要：depth ≠ path 段数）</h2>
 * {@code sys_org.depth} 由 **{@code org_type}** 决定（DDL 注释「层级：1集团 2公司 3部门 4科室」、
 * import-spec §2.2「{@code depth}（1 集团 / 2 公司 / 3 部门 / 4 科室）」、§3.2「每行另决定
 * {@code sys_org.depth}：1/2/3/4」），**不是** path 的 id 段数。反例：
 * <b>集团职能部门</b>（部门直挂集团，PRD §5.1 / import-spec E-ORG-006 明确允许）的
 * {@code path = /1/135/}（2 段），但 {@code depth = 3}。
 * 因此：
 * <ul>
 *   <li>{@link #depthOf(String)} 返回的是「**路径段数**」（用于解析/校验 path 结构），
 *       与 {@link OrgType#depth()} 是两个口径，业务代码一律用后者写 {@code sys_org.depth}；</li>
 *   <li>{@link #rebaseSubtree} 只重算 **path**；{@code depth} 在 move 后仍等于其类型的层级
 *       （type 未变），应用服务在同一事务里按 {@code org_type} **重新推导**并逐条回写，
 *       以落实「move 级联重算整棵子树 path/depth」的语义。</li>
 * </ul>
 *
 * <p><b>move 的级联语义</b>：改父级后必须重算**整棵子树**的 path（并按类型重推 depth），
 * 且在同一事务内完成（见 {@code OrgService#move}）；本类只负责纯计算，事务边界由应用服务控制。
 */
public final class OrgHierarchy {

    private OrgHierarchy() {
    }

    // ================================================================ 层级约束

    /**
     * 校验「父类型 + 子类型」组合是否合法；非法直接 {@link BizException}（400）。
     *
     * @param parentType 父节点类型；{@code null} 表示挂到根（无父）
     * @param childType  待新增/移动的节点类型
     */
    public static void assertParentChild(OrgType parentType, OrgType childType) {
        if (childType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组织类型不能为空");
        }
        if (parentType == null) {
            if (childType != OrgType.GROUP) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "只有集团节点可以作为根节点（无上级），当前类型为「" + childType.label() + "」");
            }
            return;
        }
        switch (childType) {
            case GROUP -> throw new BizException(ErrorCode.PARAM_INVALID,
                    "集团节点必须是根节点，不能再指定上级（import-spec E-ORG-015）");
            case COMPANY -> {
                if (parentType != OrgType.GROUP) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "公司必须挂在集团下：上级为「" + parentType.label() + "」不合法（import-spec E-ORG-006）");
                }
            }
            case DEPT -> {
                // 部门可挂公司（常规）或集团（集团职能部门：经发部/财务部/人力资源部/集团办，PRD 5.1）
                if (parentType != OrgType.COMPANY && parentType != OrgType.GROUP) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "部门只能挂在公司或集团下：上级为「" + parentType.label() + "」不合法（import-spec E-ORG-006）");
                }
            }
            case SECTION -> {
                if (parentType != OrgType.DEPT) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "科室必须挂在部门下：上级为「" + parentType.label() + "」不合法（import-spec E-ORG-006）");
                }
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "未知组织类型：" + childType);
        }
    }

    /** 校验 {@code org_type} 与 {@code depth} 是否自洽（DDL 注释与枚举 depth 的硬约束）。 */
    public static void assertDepthConsistent(OrgType type, int depth) {
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组织类型不能为空");
        }
        if (type.depth() != depth) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "组织类型与层级不自洽：" + type.label() + " 的层级必须为 " + type.depth() + "，实际为 " + depth);
        }
    }

    /** 类型对应的 {@code sys_org.depth}（1集团 / 2公司 / 3部门 / 4科室）。 */
    public static int depthOfType(OrgType type) {
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组织类型不能为空");
        }
        return type.depth();
    }

    // ================================================================ path / depth

    /** 子节点 path：{@code parentPath + id + "/"}；{@code parentPath} 为 {@code null} 时即根。 */
    public static String childPath(String parentPath, long id) {
        if (id <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组织 id 非法：" + id);
        }
        String normalizedParent = normalize(parentPath);
        return normalizedParent + id + "/";
    }

    /** 根节点 path：{@code /id/}。 */
    public static String rootPath(long id) {
        return childPath(null, id);
    }

    /** 把任意写法（{@code 1/12}、{@code /1/12}、{@code /1/12/}）归一为 {@code /1/12/}。 */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    /** 路径中的 id 段（顺序：祖先 → 自身）。 */
    public static List<Long> ids(String path) {
        List<Long> result = new ArrayList<>();
        for (String segment : segments(path)) {
            try {
                result.add(Long.valueOf(segment));
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "组织路径含非法 id 段：" + path);
            }
        }
        return result;
    }

    /** 路径中的 id 段（字符串形式）。 */
    public static List<String> segments(String path) {
        List<String> result = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return result;
        }
        for (String segment : normalize(path).split("/")) {
            if (!segment.isBlank()) {
                result.add(segment);
            }
        }
        return result;
    }

    /** depth = 路径中 id 段数（**路径段数**，注意与 {@code sys_org.depth} 的「类型层级」不同口径，见类注释）。 */
    public static int depthOf(String path) {
        return segments(path).size();
    }

    /** 自身 id（路径最后一段）。 */
    public static Long selfId(String path) {
        List<Long> ids = ids(path);
        return ids.isEmpty() ? null : ids.get(ids.size() - 1);
    }

    /** 祖先 id 列表（不含自身，顺序：根 → 直接父级）。 */
    public static List<Long> ancestorIds(String path) {
        List<Long> ids = ids(path);
        if (ids.isEmpty()) {
            return ids;
        }
        return List.copyOf(ids.subList(0, ids.size() - 1));
    }

    /** 直接父级 path；根节点返回 {@code null}。 */
    public static String parentPath(String path) {
        List<String> segments = segments(path);
        if (segments.size() <= 1) {
            return null;
        }
        StringBuilder builder = new StringBuilder("/");
        for (int i = 0; i < segments.size() - 1; i++) {
            builder.append(segments.get(i)).append('/');
        }
        return builder.toString();
    }

    /** {@code candidate} 是否为 {@code ancestorPath} 的后代**或自身**（用于子树判定与环路防护）。 */
    public static boolean isDescendantOrSelf(String candidate, String ancestorPath) {
        if (candidate == null || ancestorPath == null) {
            return false;
        }
        return normalize(candidate).startsWith(normalize(ancestorPath));
    }

    // ================================================================ 子树重算（move 的核心）

    /** 子树节点快照（{@code sys_org} 的最小投影，便于纯函数测试）。 */
    public record SubtreeNode(long id, String path, int depth) {
    }

    /**
     * 重算整棵子树的 path（**move 的核心纯函数**）。
     *
     * <p>语义：{@code oldRootPath} 是移动前**子树根**的 path，{@code newRootPath} 是移动后子树根的 path；
     * 子树内每个节点只做「前缀替换」。
     *
     * <p>{@code depth} 说明：{@code sys_org.depth} 由 {@code org_type} 决定（见类注释），
     * move 不改 type，故 depth 数值不变；返回结果仍带 {@code depth} 字段，调用方
     * （{@code OrgService#move}）会在同一事务里按 {@code org_type} **重新推导**后逐条回写，
     * 保证「path/depth 一致重算」可核对、不依赖旧值。
     *
     * @param subtree     子树节点（**必须含子树根自身**）
     * @param oldRootPath 移动前子树根 path（形如 {@code /1/12/}）
     * @param newRootPath 移动后子树根 path（形如 {@code /1/20/12/}）
     * @return 与入参同序的新快照
     */
    public static List<SubtreeNode> rebaseSubtree(List<SubtreeNode> subtree, String oldRootPath, String newRootPath) {
        if (subtree == null || subtree.isEmpty()) {
            return List.of();
        }
        String oldPrefix = normalize(oldRootPath);
        String newPrefix = normalize(newRootPath);
        List<SubtreeNode> result = new ArrayList<>(subtree.size());
        for (SubtreeNode node : subtree) {
            String nodePath = normalize(node.path());
            if (!nodePath.startsWith(oldPrefix)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "子树节点路径不在重算前缀内：node=" + nodePath + " prefix=" + oldPrefix);
            }
            String rebased = newPrefix + nodePath.substring(oldPrefix.length());
            result.add(new SubtreeNode(node.id(), rebased, node.depth()));
        }
        return result;
    }

    /**
     * move 的前置校验（纯逻辑，应用服务在事务内先调用它再落库）。
     *
     * <p>层级合法性由 {@link #assertParentChild} 统一判定（公司必挂集团、科室必挂部门、
     * 部门可挂公司或集团）；由于类型链本身已封顶 4 级，无需再按「层级数字」额外限制，
     * 唯一的物理约束是 {@code sys_org.path VARCHAR(255)}（由应用服务按子树最长路径预检）。
     *
     * @param nodeId        被移动节点 id
     * @param nodePath      被移动节点当前 path
     * @param nodeType      被移动节点类型
     * @param newParentId   新父节点 id；{@code null} 表示挂为根
     * @param newParentType 新父节点类型；{@code null} 表示挂为根
     * @param newParentPath 新父节点 path；{@code null} 表示挂为根
     */
    public static void assertMovable(long nodeId, String nodePath, OrgType nodeType,
                                     Long newParentId, OrgType newParentType, String newParentPath) {
        if (newParentId != null && newParentId == nodeId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能把节点移动到自身之下");
        }
        // 环路防护：新父节点若位于本节点子树内（含自身），会造成路径自包含
        if (newParentPath != null && isDescendantOrSelf(newParentPath, nodePath)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能把节点移动到自己的子树内（会造成环路）");
        }
        assertParentChild(newParentType, nodeType);
    }

    // ================================================================ 业务路径（导入/导出的 org_path）

    /**
     * 业务键 {@code org_path}（导入/导出用）—— 形如 {@code 集团/公司A/部门1}。
     *
     * <p><b>与 {@code sys_org.path} 的区别（重要，勿混用）</b>：{@code sys_org.path} 是 **id 路径**
     * （{@code /1/12/135/}，DDL 注释口径），而 import-spec §1.3 的业务键 {@code org_path} 是
     * **名称路径**（{@code 集团/公司A/部门1/科室1-1}）。DDL 中**没有**存名称路径的列，
     * 因此导入/导出必须由本方法按组织树实时拼装（详见交付说明「文档与 DDL 不一致」）。
     *
     * @param ancestorNames 祖先名称（根 → 直接父级）
     * @param selfName      自身名称
     */
    public static String businessPath(List<String> ancestorNames, String selfName) {
        if (selfName == null || selfName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "组织名称不能为空");
        }
        StringBuilder builder = new StringBuilder();
        if (ancestorNames != null) {
            for (String name : ancestorNames) {
                if (name != null && !name.isBlank()) {
                    builder.append(name).append('/');
                }
            }
        }
        return builder.append(selfName).toString();
    }

    /** 名称路径去掉最后一段（导入模板 {@code parent_path}）。 */
    public static String parentBusinessPath(String businessPath) {
        if (businessPath == null || businessPath.isBlank()) {
            return null;
        }
        int index = businessPath.lastIndexOf('/');
        return index <= 0 ? null : businessPath.substring(0, index);
    }
}
