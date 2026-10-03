package com.oa.identity.app;

import com.oa.authz.visibility.ExportFieldPolicy;
import com.oa.authz.visibility.ExportTarget;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.api.dto.InFlightDtos;
import com.oa.identity.api.dto.OrgDtos;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.IdentityEnums.OrgStatus;
import com.oa.identity.domain.IdentityEnums.OrgType;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织架构应用服务（阶段 1.1 的 1.1 组织树）。
 *
 * <p>契约（与 {@code normify-oa} 基线 {@code oa.identity.org.*} 逐字对齐）：
 * <ul>
 *   <li>{@code GET  /api/v1/identity/orgs/tree}</li>
 *   <li>{@code GET  /api/v1/identity/orgs/search}、{@code GET /orgs/selector}</li>
 *   <li>{@code POST /api/v1/identity/orgs}、{@code PUT /orgs/{id}}</li>
 *   <li>{@code POST /api/v1/identity/orgs/{id}/move}（级联重算子树 path/depth，同事务）</li>
 *   <li>{@code POST /api/v1/identity/orgs/{id}/disable}、{@code /enable}</li>
 *   <li>{@code GET  /api/v1/identity/orgs/{id}/path}、{@code /ancestors}、{@code /descendants}</li>
 *   <li>{@code GET  /api/v1/identity/orgs/{id}/in-flight-check}</li>
 *   <li>{@code GET  /api/v1/identity/orgs/export}（仅系统管理员，import-spec §9.2）</li>
 * </ul>
 *
 * <h2>硬性规则落点</h2>
 * <ol>
 *   <li><b>四级层级约束</b>：{@link OrgHierarchy#assertParentChild}（集团→公司→部门→科室，
 *       公司必挂集团、科室必挂部门、部门可挂公司或集团）；非法组合抛 {@link BizException}（400）。</li>
 *   <li><b>path/depth 一致性</b>：新增/移动只由 {@link OrgHierarchy} 计算，
 *       移动时对**整棵子树**前缀替换并逐条落库，全部在 {@link #move} 的同一事务内。</li>
 *   <li><b>停用前清空在途</b>：{@link #disable} 先走 {@link InFlightChecker} +
 *       {@link InFlightGuard}，命中时按 {@code oa.identity.block-on-inflight} 拒绝或告警；
 *       系统管理员可用 {@code force=true} + 必填 {@code reason} 覆盖该阻断（AC-52 双留痕），
 *       授权校验在控制器 {@link ForceReasonPolicy#assertAllowed}，服务层只负责放行与日志。</li>
 *   <li><b>数据域</b>：树/搜索/详情/导出前的可见性由 {@link OrgVisibility} 按调用人数据域裁剪；
 *       {@code sys_org} 的 SELECT 全部带 {@code @dataScope} 标记（fail-closed）。</li>
 * </ol>
 */
@Service
public class OrgService {

    private static final Logger log = LoggerFactory.getLogger(OrgService.class);

    /** {@code sys_org.path VARCHAR(255)}。 */
    private static final int MAX_PATH_LENGTH = 255;

    private final SysOrgMapper orgMapper;
    private final SysUserPositionMapper positionMapper;
    private final SysOrgLeaderMapper leaderMapper;
    private final SysUserMapper userMapper;
    private final InFlightChecker inFlightChecker;
    private final OaProperties properties;

    public OrgService(SysOrgMapper orgMapper, SysUserPositionMapper positionMapper,
                      SysOrgLeaderMapper leaderMapper, SysUserMapper userMapper,
                      InFlightChecker inFlightChecker, OaProperties properties) {
        this.orgMapper = orgMapper;
        this.positionMapper = positionMapper;
        this.leaderMapper = leaderMapper;
        this.userMapper = userMapper;
        this.inFlightChecker = inFlightChecker;
        this.properties = properties;
    }

    // ================================================================ 查询

    /**
     * 组织树（四级；支持 {@code rootId} 与 {@code includeDisabled}）。
     *
     * @param rootId          子树根；为空则返回调用人可见的全部根
     * @param includeDisabled 是否包含停用节点（管理视图默认 {@code true}；
     *                        级联选择用 {@code false}，此时父链不完整的整枝会被丢弃，
     *                        口径依据 import-spec {@code W-ORG-016}「父组织已停用，子节点不可作为发起归属」）
     */
    public List<OrgDtos.OrgView> tree(Long rootId, boolean includeDisabled) {
        List<SysOrg> all = orgMapper.selectAll(includeDisabled);
        Set<Long> visible = visibleIds(all);
        SysOrg root = null;
        if (rootId != null) {
            // 子树根必须可见（不可见即 403，口径见 requireVisible）
            SysOrg candidate = orgMapper.selectById(rootId);
            if (candidate == null) {
                throw BizException.notFound("组织节点");
            }
            requireVisible(candidate, visible);
            root = candidate;
        }
        String prefix = root == null ? null : OrgHierarchy.normalize(root.getPath());
        List<SysOrg> scoped = new ArrayList<>();
        for (SysOrg org : all) {
            if (!visible.contains(org.getId())) {
                continue;
            }
            if (prefix != null && !OrgHierarchy.normalize(org.getPath()).startsWith(prefix)) {
                continue;
            }
            scoped.add(org);
        }
        // 父链裁剪：rootId 指定的子树根视为根（其父不参与本次结果集）
        Set<Long> allowedRoots = root == null ? Set.of() : Set.of(root.getId());
        List<SysOrg> kept = OrgVisibility.filter(scoped, OrgVisibility.reachableIds(refs(scoped), allowedRoots));
        return buildTree(kept, all, root == null ? null : root.getId(), primaryLeaderOrgIds());
    }

    /** 按名称/路径关键字搜索可见组织节点。 */
    public List<OrgDtos.OrgView> search(String keyword, boolean includeDisabled) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<SysOrg> all = orgMapper.selectAll(true);
        Set<Long> visible = visibleIds(all);
        List<SysOrg> hits = orgMapper.search(keyword.trim(), includeDisabled);
        List<SysOrg> kept = new ArrayList<>();
        for (SysOrg hit : hits) {
            if (visible.contains(hit.getId())) {
                kept.add(hit);
            }
        }
        Map<Long, SysOrg> index = indexById(all);
        Set<Long> primaryOrgs = primaryLeaderOrgIds();
        List<OrgDtos.OrgView> result = new ArrayList<>(kept.size());
        for (SysOrg org : kept) {
            result.add(view(org, index, null, primaryOrgs));
        }
        return result;
    }

    /**
     * 下拉/级联用的精简树。
     *
     * <p>{@code keyword} 为**服务端过滤**（施工要求第 9 条），返回结构仍是**树形**：
     * 命中节点连同其**祖先链**一起保留，父链不断，前端可直接渲染/级联，无需自己做过滤。
     */
    public List<OrgDtos.OrgOption> selector(Long rootId, boolean includeDisabled, String keyword) {
        List<SysOrg> all = orgMapper.selectAll(includeDisabled);
        Set<Long> visible = visibleIds(all);
        SysOrg root = null;
        if (rootId != null) {
            SysOrg candidate = orgMapper.selectById(rootId);
            if (candidate == null) {
                throw BizException.notFound("组织节点");
            }
            requireVisible(candidate, visible);
            root = candidate;
        }
        String prefix = root == null ? null : OrgHierarchy.normalize(root.getPath());
        List<SysOrg> scoped = new ArrayList<>();
        for (SysOrg org : all) {
            if (!visible.contains(org.getId())) {
                continue;
            }
            if (prefix != null && !OrgHierarchy.normalize(org.getPath()).startsWith(prefix)) {
                continue;
            }
            scoped.add(org);
        }
        Set<Long> allowedRoots = root == null ? Set.of() : Set.of(root.getId());
        List<SysOrg> kept = OrgVisibility.filter(scoped, OrgVisibility.reachableIds(refs(scoped), allowedRoots));
        return buildOptions(keepMatchesWithAncestors(kept, keyword), root == null ? null : root.getId());
    }

    /** 节点路径与层级（根 → 自身）。 */
    public OrgDtos.OrgPathView path(Long id) {
        SysOrg org = requireVisibleOrg(id);
        List<SysOrg> chain = chainOf(org);
        Map<Long, SysOrg> index = indexById(chain);
        List<OrgDtos.OrgPathSegment> segments = new ArrayList<>(chain.size());
        for (SysOrg node : chain) {
            segments.add(new OrgDtos.OrgPathSegment(node.getId(), node.getName(), node.getOrgType(),
                    labelOfOrgType(node.getOrgType())));
        }
        return new OrgDtos.OrgPathView(org.getId(), org.getPath(), businessPath(org, index), org.getDepth(), segments);
    }

    /** 祖先节点（根 → 直接父级）。 */
    public List<OrgDtos.OrgView> ancestors(Long id) {
        SysOrg org = requireVisibleOrg(id);
        List<SysOrg> chain = chainOf(org);
        if (chain.size() <= 1) {
            return List.of();
        }
        List<SysOrg> ancestors = chain.subList(0, chain.size() - 1);
        Map<Long, SysOrg> index = indexById(chain);
        Set<Long> primaryOrgs = primaryLeaderOrgIds();
        List<OrgDtos.OrgView> result = new ArrayList<>(ancestors.size());
        for (SysOrg node : ancestors) {
            result.add(view(node, index, null, primaryOrgs));
        }
        return result;
    }

    /** 后代节点（不含自身；支持 {@code includeDisabled}）。 */
    public List<OrgDtos.OrgView> descendants(Long id, boolean includeDisabled) {
        SysOrg org = requireVisibleOrg(id);
        // 祖先名索引需要全量（含停用），否则 businessPath 会缺段
        List<SysOrg> all = orgMapper.selectAll(true);
        Set<Long> visible = visibleIds(all);
        List<SysOrg> subtree = orgMapper.selectSubtree(org.getPath(), includeDisabled);
        List<SysOrg> scoped = new ArrayList<>();
        for (SysOrg node : subtree) {
            if (Objects.equals(node.getId(), org.getId()) || !visible.contains(node.getId())) {
                continue;
            }
            scoped.add(node);
        }
        Map<Long, SysOrg> index = indexById(all);
        Set<Long> primaryOrgs = primaryLeaderOrgIds();
        List<OrgDtos.OrgView> result = new ArrayList<>(scoped.size());
        for (SysOrg node : scoped) {
            result.add(view(node, index, null, primaryOrgs));
        }
        return result;
    }

    /** 在途/待办检查（不抛异常，仅返回影响清单）。 */
    public OrgDtos.InFlightCheckView inFlightCheck(Long id) {
        SysOrg org = requireVisibleOrg(id);
        String namePath = businessPath(org, indexById(orgMapper.selectAll(true)));
        InFlightChecker.InFlightSummary summary = inFlightChecker.checkOrgSubtree(org.getPath());
        InFlightGuard.Outcome outcome = InFlightGuard.evaluate(
                InFlightGuard.Subject.ORG_DISABLE, namePath, summary, blockOnInflight());
        return impact(outcome, org.getPath());
    }

    // ================================================================ 变更

    /** 新增节点：类型与层级自洽 + 父级合法性 + 集团根唯一 + path 回填（自增 id 生成后）。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.OrgView create(OrgDtos.OrgCreateRequest request) {
        Long operator = currentUserId();
        OrgType type = IdentityEnums.OrgType.parse(request.orgType());
        OrgStatus status = request.status() == null || request.status().isBlank()
                ? OrgStatus.ACTIVE
                : IdentityEnums.OrgStatus.parse(request.status());

        SysOrg parent = null;
        if (request.parentId() != null) {
            parent = requireVisibleOrg(request.parentId());
        }
        OrgType parentType = parent == null ? null : IdentityEnums.OrgType.ofCode(parent.getOrgType());
        OrgHierarchy.assertParentChild(parentType, type);

        if (type == OrgType.GROUP && orgMapper.countByType(OrgType.GROUP.code(), null) > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "整棵组织树有且仅有 1 个集团根节点（import-spec E-ORG-015），已存在集团节点");
        }

        String parentPath = parent == null ? null : parent.getPath();
        // depth 由 org_type 决定（DDL 注释「1集团 2公司 3部门 4科室」）—— 不是 path 段数：
        // 集团职能部门（部门直挂集团）的 path 只有 2 段，但 depth 必须是 3。
        int depth = OrgHierarchy.depthOfType(type);
        OrgHierarchy.assertDepthConsistent(type, depth);

        SysOrg org = new SysOrg();
        org.setParentId(request.parentId());
        org.setOrgType(type.code());
        org.setName(request.name().trim());
        org.setDepth(depth);
        org.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        org.setStatus(status.code());
        org.setRemark(request.remark());
        org.setCreatedBy(operator);
        // path 依赖自增 id：先以临时唯一 path 落库（同一事务内不可见），拿到 id 后立即回填真实 path
        orgMapper.insertOrg(org);

        String path = OrgHierarchy.childPath(parentPath, org.getId());
        if (path.length() > MAX_PATH_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "组织路径长度超过 " + MAX_PATH_LENGTH + " 字符（sys_org.path 列宽），请调整层级");
        }
        orgMapper.updatePathAndDepth(org.getId(), path, depth, operator);

        warnSiblingName(request.parentId(), org.getName(), org.getId());
        log.info("组织节点已创建 id={} type={} path={} operator={}", org.getId(), type.code(), path, operator);
        SysOrg created = requireVisibleOrg(org.getId());
        return view(created, indexById(orgMapper.selectAll(true)), null, primaryLeaderOrgIds());
    }

    /** 改名 / 排序 / 备注（类型不可改；改类型必须走 move 重新校验层级）。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.OrgView update(Long id, OrgDtos.OrgUpdateRequest request) {
        Long operator = currentUserId();
        SysOrg existing = requireVisibleOrg(id);
        SysOrg update = new SysOrg();
        update.setId(id);
        update.setName(request.name().trim());
        update.setSortNo(request.sortNo());
        update.setRemark(request.remark() == null ? "" : request.remark());
        update.setUpdatedBy(operator);
        orgMapper.updateOrg(update);
        warnSiblingName(existing.getParentId(), request.name().trim(), id);
        SysOrg reloaded = requireVisibleOrg(id);
        Map<Long, SysOrg> index = indexById(orgMapper.selectAll(true));
        return view(reloaded, index, null, primaryLeaderOrgIds());
    }

    /**
     * 换父级并**级联重算整棵子树**的 path 与 depth（同一事务）。
     *
     * <p>环路防护、层级约束、path 长度（{@code sys_org.path VARCHAR(255)}）都在落库前校验；
     * 在途单据的快照（{@code flow_instance.initiator_org_path}）**不随本次变更调整**
     * ——PRD §5.4 定稿「在途单据一律不变」。
     *
     * <p>{@code force}/{@code reason}（AC-52）：移动**不做**在途阻断（PRD §5.4 在途单据不变），
     * 因此 {@code force} 不改变任何判定，仅随完成日志留痕；授权校验在控制器
     * （{@link ForceReasonPolicy#assertAllowed}），服务层不重复做角色判断。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.MoveResult move(Long id, OrgDtos.OrgMoveRequest request) {
        return move(id, request, null, null);
    }

    /**
     * 移动节点（带 AC-52 的 {@code force}/{@code reason}）。
     *
     * <p>2 参重载等价于本方法（{@code force=null}）——{@code force} 只能经**显式参数**传入，
     * 即请求体里的 {@code force} 不会被服务层自行解读为「已授权」。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.MoveResult move(Long id, OrgDtos.OrgMoveRequest request, Boolean force, String reason) {
        Long operator = currentUserId();
        SysOrg node = requireVisibleOrg(id);
        OrgType nodeType = IdentityEnums.OrgType.ofCode(node.getOrgType());
        if (nodeType == null) {
            throw new BizException(ErrorCode.CONFLICT, "组织类型非法，无法移动：" + node.getOrgType());
        }
        SysOrg newParent = null;
        if (request.newParentId() != null) {
            newParent = requireVisibleOrg(request.newParentId());
        }
        OrgHierarchy.assertMovable(node.getId(), node.getPath(), nodeType,
                newParent == null ? null : newParent.getId(),
                newParent == null ? null : IdentityEnums.OrgType.ofCode(newParent.getOrgType()),
                newParent == null ? null : newParent.getPath());

        String oldRootPath = node.getPath();
        String newRootPath = OrgHierarchy.childPath(newParent == null ? null : newParent.getPath(), node.getId());

        List<SysOrg> subtree = orgMapper.selectSubtree(oldRootPath, true);
        if (subtree.isEmpty()) {
            throw BizException.notFound("组织子树");
        }
        // path 列宽预检：最深的子孙路径最长
        int maxSuffix = 0;
        for (SysOrg item : subtree) {
            int suffix = OrgHierarchy.normalize(item.getPath()).length() - OrgHierarchy.normalize(oldRootPath).length();
            maxSuffix = Math.max(maxSuffix, suffix);
        }
        if (newRootPath.length() + maxSuffix > MAX_PATH_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "移动后子树路径将超过 " + MAX_PATH_LENGTH + " 字符（sys_org.path 列宽），请调整层级");
        }

        List<OrgHierarchy.SubtreeNode> snapshot = new ArrayList<>(subtree.size());
        for (SysOrg item : subtree) {
            snapshot.add(new OrgHierarchy.SubtreeNode(item.getId(),
                    item.getPath(), item.getDepth() == null ? OrgHierarchy.depthOf(item.getPath()) : item.getDepth()));
        }
        List<OrgHierarchy.SubtreeNode> rebased = OrgHierarchy.rebaseSubtree(snapshot, oldRootPath, newRootPath);

        orgMapper.updateParent(node.getId(), request.newParentId(), operator);
        List<OrgDtos.MoveItem> items = new ArrayList<>(rebased.size());
        for (OrgHierarchy.SubtreeNode item : rebased) {
            // depth 按**类型**重新推导后回写（同一事务），而不是沿用旧值：
            // 这样「move 级联重算整棵子树 path/depth」有明确的、可核对的来源。
            SysOrg current = findInSubtree(subtree, item.id());
            int depth = current == null || current.type() == null
                    ? item.depth()
                    : OrgHierarchy.depthOfType(current.type());
            orgMapper.updatePathAndDepth(item.id(), item.path(), depth, operator);
            String oldPath = null;
            for (OrgHierarchy.SubtreeNode before : snapshot) {
                if (before.id() == item.id()) {
                    oldPath = before.path();
                    break;
                }
            }
            items.add(new OrgDtos.MoveItem(item.id(), oldPath, item.path(), depth));
        }
        log.info("组织节点移动完成 id={} {} → {}，级联重算 {} 个节点，operator={} force={} reason={}",
                id, oldRootPath, newRootPath, rebased.size(), operator, force, reason);
        Integer newDepth = OrgHierarchy.depthOfType(nodeType);
        return new OrgDtos.MoveResult(id, request.newParentId(), oldRootPath, newRootPath,
                newDepth, items.size(), items);
    }

    /**
     * 停用节点：**停用前清空在途**（AC-11/AC-12/PRD §5.5、import-spec §8.2）。
     *
     * <p>命中在途且 {@code oa.identity.block-on-inflight=true}（默认）时抛 409 并在文案中给出
     * 在途数量与单号；置为 {@code false} 时仅告警放行并把影响清单放进出参。
     *
     * <p>业务裁定：系统管理员可用 {@code force=true} + 必填 {@code reason} **覆盖**该阻断放行
     * （AC-11/AC-12 的默认阻断不变，仅被显式覆盖），覆盖时输出运行日志并保留影响清单。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.StateResult disable(Long id) {
        return disable(id, null, null);
    }

    /**
     * 停用节点（带 AC-52 的 {@code force}/{@code reason}）。
     *
     * <p><b>职责边界</b>：{@code force} 的授权（系统管理员 + 必填原因）在控制器侧由
     * {@link ForceReasonPolicy#assertAllowed} 完成；本方法只负责「放行语义 + 运行日志」，
     * **不重复做角色判断**，也不读取请求体里的 {@code force}（只能经显式参数传入，避免绕过准入）。
     *
     * @param force  已获授权的强制继续标记；{@code null}/{@code false} → 保持默认阻断
     * @param reason 强制继续原因（仅用于运行日志；审计留痕由 {@code @Audited} 承担）
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.StateResult disable(Long id, Boolean force, String reason) {
        Long operator = currentUserId();
        SysOrg org = requireVisibleOrg(id);
        List<String> warnings = new ArrayList<>();
        String namePath = businessPath(org, indexById(orgMapper.selectAll(true)));
        InFlightChecker.InFlightSummary summary = inFlightChecker.checkOrgSubtree(org.getPath());
        InFlightGuard.Outcome outcome = InFlightGuard.evaluate(
                InFlightGuard.Subject.ORG_DISABLE, namePath, summary, blockOnInflight());
        OrgDtos.InFlightCheckView check = impact(outcome, org.getPath());

        if (!org.isEnabled()) {
            warnings.add("该节点已是停用状态，本次操作未产生变更");
            return new OrgDtos.StateResult(id, org.getStatus(), labelOfOrgStatus(org.getStatus()), warnings, check);
        }
        if (outcome.blocked()) {
            // 开关为「拒绝」时这里抛 409（文案含在途数量与单号）；开关为「仅告警」时返回后继续放行；
            // 系统管理员显式 force=true（已过 ForceReasonPolicy 准入）时不抛异常，改为「覆盖放行 + 双留痕」
            InFlightGuard.assertClear(
                    InFlightGuard.Subject.ORG_DISABLE, namePath, summary, blockOnInflight(), Boolean.TRUE.equals(force));
            InFlightGuard.logForceOverride(outcome, force, reason);
            warnings.add(InFlightGuard.forced(outcome, force)
                    ? "已按系统管理员「强制继续」放行原阻断：" + outcome.message() + "（AC-52：reason/force 已留痕）"
                    : outcome.message());
        }
        int children = orgMapper.countChildren(id);
        if (children > 0) {
            warnings.add("该节点下仍有 " + children + " 个直接子节点，停用后其子孙不可作为发起归属（W-ORG-016）");
        }
        int positions = positionMapper.countByOrgId(id);
        if (positions > 0) {
            // W-ORG-017「停用组织下仍有在职人员」的口径提示。当前只统计**本节点**的岗位任职
            // （子树口径需要 sys_user × sys_org path 的联合统计，待人员统计口径统一后接入）。
            warnings.add("该节点下仍有 " + positions + " 条岗位任职（在职人员），停用后不可作为发起归属（W-ORG-017）");
        }
        orgMapper.updateStatus(id, OrgStatus.DISABLED.code(), operator);
        log.info("组织节点已停用 id={} operator={} warnings={}", id, operator, warnings);
        return new OrgDtos.StateResult(id, OrgStatus.DISABLED.code(), OrgStatus.DISABLED.label(), warnings, check);
    }

    /**
     * 启用节点。
     *
     * <p>启用**不做**在途阻断（恢复可用只会让归属可发起），因此 {@code force} 不改变任何判定，
     * 仅随完成日志留痕（口径与 {@link #disable(Long, Boolean, String)} 一致：授权在控制器）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.StateResult enable(Long id) {
        return enable(id, null, null);
    }

    /** 启用节点（带 AC-52 的 {@code force}/{@code reason}；语义见 1 参重载的类注释）。 */
    @Transactional(rollbackFor = Exception.class)
    public OrgDtos.StateResult enable(Long id, Boolean force, String reason) {
        Long operator = currentUserId();
        SysOrg org = requireVisibleOrg(id);
        List<String> warnings = new ArrayList<>();
        if (org.isEnabled()) {
            warnings.add("该节点已是启用状态，本次操作未产生变更");
        }
        orgMapper.updateStatus(id, OrgStatus.ACTIVE.code(), operator);
        if (org.getParentId() != null) {
            SysOrg parent = orgMapper.selectById(org.getParentId());
            if (parent != null && !parent.isEnabled()) {
                warnings.add("上级节点「" + parent.getName() + "」处于停用状态，该节点仍不可作为发起归属（W-ORG-016）");
            }
        }
        if (Boolean.TRUE.equals(force)) {
            log.warn("启用组织节点声明了强制继续（无在途阻断可覆盖，仅留痕）：id={} force={} reason={} operator={}",
                    id, true, reason, operator);
        }
        return new OrgDtos.StateResult(id, OrgStatus.ACTIVE.code(), OrgStatus.ACTIVE.label(), warnings, null);
    }

    // ================================================================ 导出

    /**
     * 主数据导出（组织）：**仅系统管理员**（import-spec §9.2 T-11 定稿）。
     *
     * <p>列与顺序逐字对齐 import-spec §9.1 的 {@code org.csv}：
     * {@code org_path,org_name,org_type,parent_path,status,remark}；
     * 编码 UTF-8 **带 BOM**（§4.1，否则校验器报 {@code E-ENC-001}）。
     * 导出物必须能被 {@code tools/check-import-csv.js} 直接通过（§9.1 往返约束）。
     */
    public String exportCsv() {
        CurrentUser principal = currentPrincipal();
        if (!principal.hasRole("admin")) {
            throw new BizException(ErrorCode.EXPORT_DENIED,
                    "主数据（组织）导出仅系统管理员可用（import-spec §9.2）");
        }
        List<SysOrg> all = orgMapper.selectAll(true);
        Map<Long, SysOrg> index = indexById(all);
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        // 列清单取自 ExportFieldPolicy（导出列的唯一来源）：org.csv 六列，天然不含任何金额列
        CsvSupport.appendLine(builder, ExportFieldPolicy.columnsFor(ExportTarget.ORG, false).toArray(new String[0]));
        for (SysOrg org : exportOrder(all)) {
            String businessPath = businessPath(org, index);
            builder.append(CsvSupport.line(
                    businessPath,
                    org.getName(),
                    labelOfOrgType(org.getOrgType()),
                    OrgHierarchy.parentBusinessPath(businessPath),
                    labelOfOrgStatus(org.getStatus()),
                    org.getRemark()));
            builder.append(CsvSupport.CRLF);
        }
        log.info("组织主数据导出：{} 行，operator={}", all.size(), principal.id());
        return builder.toString();
    }

    // ================================================================ 供其它服务复用

    /** 当前登录人可见的组织 id 集合（组织树、通讯录分组、负责人绑定校验共用同一口径）。 */
    public Set<Long> currentVisibleOrgIds() {
        return visibleIds(orgMapper.selectAll(true));
    }

    /** 取可见节点，不存在抛 404，越权抛 403（{@link ErrorCode#DATA_SCOPE_DENIED}）。 */
    public SysOrg requireVisibleOrg(Long id) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw BizException.notFound("组织节点");
        }
        requireVisible(org, visibleIds(orgMapper.selectAll(true)));
        return org;
    }

    /** 取节点（不做数据域校验，仅用于系统内部口径，如导出与回填）。 */
    public SysOrg requireOrg(Long id) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw BizException.notFound("组织节点");
        }
        return org;
    }

    /** 取节点，不存在返回 {@code null}（供回填/推导等容错场景，不做数据域校验）。 */
    public SysOrg findOrg(Long id) {
        return id == null ? null : orgMapper.selectById(id);
    }

    /** 节点名称路径（import-spec 业务键 {@code org_path}）。 */
    public String businessPathOf(SysOrg org) {
        return businessPath(org, indexById(orgMapper.selectAll(true)));
    }

    /** 单一集团根节点（集团层业务线绑定用；不存在即 409）。 */
    public SysOrg requireGroupRoot() {
        List<SysOrg> roots = orgMapper.selectRoots(true);
        for (SysOrg root : roots) {
            if (OrgType.GROUP.code().equals(root.getOrgType())) {
                return root;
            }
        }
        throw new BizException(ErrorCode.CONFLICT,
                "尚未配置集团根节点，无法绑定集团层分管领导（import-spec E-ORG-015）");
    }

    /** 是否命中「在途/待办即拒绝」开关。 */
    public boolean blockOnInflight() {
        return properties.getIdentity().isBlockOnInflight();
    }

    /**
     * 把在途检查结论转为出参（含提示文案与影响清单）。
     *
     * <p><b>向后兼容</b>：{@code blocked}/{@code rejected}/{@code blockOnInflight}/
     * {@code inFlightInstances}/{@code pendingTasks}/{@code total}/{@code bizNos}/{@code message}
     * 全部保持原义；本次**追加**同值规范名 {@code inFlightInstanceCount}/{@code pendingTaskCount}、
     * 子树在职人数 {@code activeStaffCount}（W-ORG-017）与明细行 {@code items}
     * （来自 {@link InFlightChecker#orgInFlightItems(String)}，与数量口径同源）。
     *
     * @param orgPath 节点 id 路径（{@code /1/12/}），用于子树在职人数与在途明细的口径对齐
     */
    private OrgDtos.InFlightCheckView impact(InFlightGuard.Outcome outcome, String orgPath) {
        InFlightChecker.InFlightSummary summary = outcome.summary();
        String pathPrefix = orgPath == null || orgPath.isBlank() ? null : OrgHierarchy.normalize(orgPath) + "%";
        int activeStaff = pathPrefix == null ? 0 : userMapper.countActiveByOrgPath(pathPrefix);
        List<InFlightDtos.InFlightItemView> items =
                InFlightViews.views(inFlightChecker.orgInFlightItems(orgPath));
        return new OrgDtos.InFlightCheckView(
                outcome.blocked(),
                outcome.rejected(),
                outcome.blockOnInflight(),
                summary.inFlightInstances(),
                summary.pendingTasks(),
                summary.total(),
                summary.bizNos(),
                outcome.message(),
                summary.inFlightInstances(),
                summary.pendingTasks(),
                activeStaff,
                items);
    }

    /**
     * 全部组织节点的「名称路径」索引（{@code org_path} 业务键 → 展示值）。
     *
     * <p>用途：人员主数据导出（import-spec §9.1 的 {@code company_path}/{@code dept_path} 列）
     * 需要把每行的 {@code company_id}/{@code org_id} 还原为**完整名称路径**；若只按行内 org 拼装，
     * 祖先名会缺失（退化成 id 路径），导致「导出 → 再导入」往返校验失败。
     * 因此这里**一次性**取全量节点并算出每条路径，供调用方按 id 直接取用（O(1)，无 N+1）。
     */
    public Map<Long, String> businessPathIndex() {
        List<SysOrg> all = orgMapper.selectAll(true);
        return businessPathIndex(all);
    }

    /**
     * 名称路径索引（**入参为既有节点集**的纯函数重载）。
     *
     * <p>用途：批量导入需要在**系统口径**下一次取全量节点后反复复用同一份索引
     * （人员/负责人/岗位/角色的路径解析都依赖它），避免每类导入各查一次表。
     */
    public static Map<Long, String> businessPathIndex(List<SysOrg> all) {
        Map<Long, SysOrg> index = indexById(all);
        Map<Long, String> result = new HashMap<>();
        if (all != null) {
            for (SysOrg org : all) {
                if (org.getId() != null) {
                    result.put(org.getId(), businessPath(org, index));
                }
            }
        }
        return result;
    }

    /** 组织类型 code → 中文标签（集中走 {@code IdentityEnums}，禁止散落 magic string）。 */
    public static String labelOfOrgType(String code) {
        OrgType type = OrgType.ofCode(code);
        return type == null ? code : type.label();
    }

    /** 组织状态 code → 中文标签。 */
    public static String labelOfOrgStatus(String code) {
        OrgStatus status = OrgStatus.ofCode(code);
        return status == null ? code : status.label();
    }

    // ================================================================ 内部

    private Set<Long> visibleIds(List<SysOrg> all) {
        return OrgVisibility.visibleIds(DataScopeContext.current(), refs(all));
    }

    private static List<OrgVisibility.OrgRef> refs(List<SysOrg> all) {
        List<OrgVisibility.OrgRef> refs = new ArrayList<>();
        if (all != null) {
            for (SysOrg org : all) {
                refs.add(OrgVisibility.OrgRef.of(org));
            }
        }
        return refs;
    }

    private void requireVisible(SysOrg org, Set<Long> visible) {
        if (!visible.contains(org.getId())) {
            throw new BizException(ErrorCode.DATA_SCOPE_DENIED,
                    "无权访问该组织节点（数据域校验未通过）：id=" + org.getId());
        }
    }

    private Long currentUserId() {
        return DataScopeContext.require().getUserId();
    }

    private CurrentUser currentPrincipal() {
        DataScopeContext context = DataScopeContext.require();
        CurrentUser principal = context.getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /** 同父同名仅提示（import-spec §3.2：唯一性由 org_path 保证，允许同父重名）。 */
    private void warnSiblingName(Long parentId, String name, Long excludeId) {
        if (orgMapper.countSiblingName(parentId, name, excludeId) > 0) {
            log.warn("同父节点下已存在同名组织（仅提示，不阻断）：parentId={} name={}", parentId, name);
        }
    }

    /** 自身 + 祖先链（根 → 自身）。 */
    private List<SysOrg> chainOf(SysOrg org) {
        List<Long> ids = OrgHierarchy.ids(org.getPath());
        if (ids.isEmpty()) {
            return List.of(org);
        }
        List<SysOrg> loaded = orgMapper.selectByIds(ids);
        Map<Long, SysOrg> index = indexById(loaded);
        List<SysOrg> chain = new ArrayList<>(ids.size());
        for (Long id : ids) {
            SysOrg node = index.get(id);
            if (node != null) {
                chain.add(node);
            }
        }
        return chain.isEmpty() ? List.of(org) : chain;
    }

    private static Map<Long, SysOrg> indexById(List<SysOrg> all) {
        Map<Long, SysOrg> index = new HashMap<>();
        if (all != null) {
            for (SysOrg org : all) {
                if (org.getId() != null) {
                    index.put(org.getId(), org);
                }
            }
        }
        return index;
    }

    /**
     * 名称路径（业务键 {@code org_path}）：由祖先链的名字 + 自身名拼装。
     *
     * <p>{@code sys_org} **没有**存名称路径的列（DDL 的 {@code path} 是 id 路径），
     * 因此这里按 {@code path} 里的 id 顺序回溯祖先名（缺失段以 id 兜底，保证不静默丢段）。
     */
    private static String businessPath(SysOrg org, Map<Long, SysOrg> index) {
        List<Long> ids = OrgHierarchy.ids(org.getPath());
        List<String> names = new ArrayList<>(Math.max(0, ids.size() - 1));
        for (int i = 0; i < ids.size() - 1; i++) {
            SysOrg ancestor = index == null ? null : index.get(ids.get(i));
            names.add(ancestor == null ? String.valueOf(ids.get(i)) : ancestor.getName());
        }
        return OrgHierarchy.businessPath(names, org.getName());
    }

    private List<OrgDtos.OrgView> buildTree(List<SysOrg> nodes, List<SysOrg> allForNames, Long rootId,
                                            Set<Long> primaryOrgs) {
        Map<Long, SysOrg> index = indexById(allForNames);
        Map<Long, List<SysOrg>> children = new HashMap<>();
        List<SysOrg> roots = new ArrayList<>();
        for (SysOrg node : nodes) {
            Long parentId = node.getParentId();
            boolean isRoot = parentId == null || Objects.equals(parentId, rootId) || !containsId(nodes, parentId);
            if (isRoot) {
                roots.add(node);
            } else {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
            }
        }
        Comparator<SysOrg> order = Comparator
                .comparing((SysOrg item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
        roots.sort(order);
        List<OrgDtos.OrgView> result = new ArrayList<>(roots.size());
        for (SysOrg root : roots) {
            result.add(buildNode(root, children, index, order, primaryOrgs));
        }
        return result;
    }

    private OrgDtos.OrgView buildNode(SysOrg node, Map<Long, List<SysOrg>> children,
                                      Map<Long, SysOrg> index, Comparator<SysOrg> order,
                                      Set<Long> primaryOrgs) {
        List<SysOrg> childNodes = children.getOrDefault(node.getId(), List.of());
        List<OrgDtos.OrgView> childViews = null;
        if (!childNodes.isEmpty()) {
            List<SysOrg> sorted = new ArrayList<>(childNodes);
            sorted.sort(order);
            childViews = new ArrayList<>(sorted.size());
            for (SysOrg child : sorted) {
                childViews.add(buildNode(child, children, index, order, primaryOrgs));
            }
        }
        return view(node, index, childViews, primaryOrgs);
    }

    private List<OrgDtos.OrgOption> buildOptions(List<SysOrg> nodes, Long rootId) {
        Map<Long, List<SysOrg>> children = new HashMap<>();
        List<SysOrg> roots = new ArrayList<>();
        for (SysOrg node : nodes) {
            Long parentId = node.getParentId();
            boolean isRoot = parentId == null || Objects.equals(parentId, rootId) || !containsId(nodes, parentId);
            if (isRoot) {
                roots.add(node);
            } else {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(node);
            }
        }
        Comparator<SysOrg> order = Comparator
                .comparing((SysOrg item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
        roots.sort(order);
        List<OrgDtos.OrgOption> result = new ArrayList<>(roots.size());
        for (SysOrg root : roots) {
            result.add(buildOption(root, children, order));
        }
        return result;
    }

    private OrgDtos.OrgOption buildOption(SysOrg node, Map<Long, List<SysOrg>> children, Comparator<SysOrg> order) {
        List<SysOrg> childNodes = children.getOrDefault(node.getId(), List.of());
        List<OrgDtos.OrgOption> childViews = null;
        if (!childNodes.isEmpty()) {
            List<SysOrg> sorted = new ArrayList<>(childNodes);
            sorted.sort(order);
            childViews = new ArrayList<>(sorted.size());
            for (SysOrg child : sorted) {
                childViews.add(buildOption(child, children, order));
            }
        }
        return new OrgDtos.OrgOption(node.getId(), node.getName(), node.getOrgType(),
                labelOfOrgType(node.getOrgType()), node.getPath(), node.getDepth(), !node.isEnabled(), childViews);
    }

    private static boolean containsId(List<SysOrg> nodes, Long id) {
        for (SysOrg node : nodes) {
            if (Objects.equals(node.getId(), id)) {
                return true;
            }
        }
        return false;
    }

    private static SysOrg findInSubtree(List<SysOrg> subtree, long id) {
        for (SysOrg node : subtree) {
            if (node.getId() != null && node.getId() == id) {
                return node;
            }
        }
        return null;
    }

    private static OrgDtos.OrgView view(SysOrg org, Map<Long, SysOrg> index, List<OrgDtos.OrgView> children,
                                        Set<Long> primaryOrgs) {
        return new OrgDtos.OrgView(
                org.getId(),
                org.getParentId(),
                org.getOrgType(),
                labelOfOrgType(org.getOrgType()),
                org.getName(),
                org.getPath(),
                businessPath(org, index),
                org.getDepth(),
                org.getLeaderId(),
                org.getSortNo(),
                org.getStatus(),
                labelOfOrgStatus(org.getStatus()),
                org.getRemark(),
                children,
                hasPrimaryLeader(org, primaryOrgs));
    }

    /** 是否已设正职（AC-11）：来自批量查询结果，不做逐节点查询（避免 N+1）。 */
    private static boolean hasPrimaryLeader(SysOrg org, Set<Long> primaryOrgs) {
        return org.getId() != null && primaryOrgs != null && primaryOrgs.contains(org.getId());
    }

    /**
     * 已设正职的组织 id 集合（一次批量查询，见 {@code SysOrgLeaderMapper#selectPrimaryOrgIds}）。
     *
     * <p>口径：{@code sys_org_leader} 中 {@code leader_type='primary' AND category IS NULL}
     * ——业务线分管领导不算正职。
     */
    private Set<Long> primaryLeaderOrgIds() {
        List<Long> ids = leaderMapper.selectPrimaryOrgIds();
        return ids == null || ids.isEmpty() ? Set.of() : new HashSet<>(ids);
    }

    /**
     * 服务端关键字过滤（保留树形）：命中节点**连同其祖先链**一起返回。
     *
     * <p>为什么保留祖先：级联/树选择器要求父链完整（否则前端拿到的是一堆孤儿节点，
     * 既画不出层级也无法回显路径）。祖先只从**本次已按数据域裁剪过的集合**里取，
     * 不会因为关键字把域外节点带出来。
     */
    private static List<SysOrg> keepMatchesWithAncestors(List<SysOrg> nodes, String keyword) {
        if (keyword == null || keyword.isBlank() || nodes == null || nodes.isEmpty()) {
            return nodes;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        Set<Long> byId = new HashSet<>();
        for (SysOrg node : nodes) {
            if (node.getId() != null) {
                byId.add(node.getId());
            }
        }
        Set<Long> keep = new LinkedHashSet<>();
        for (SysOrg node : nodes) {
            if (node.getName() == null || !node.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            for (Long id : OrgHierarchy.ids(node.getPath())) {
                if (byId.contains(id)) {
                    keep.add(id);
                }
            }
            if (node.getId() != null) {
                keep.add(node.getId());
            }
        }
        List<SysOrg> result = new ArrayList<>(keep.size());
        for (SysOrg node : nodes) {
            if (node.getId() != null && keep.contains(node.getId())) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * 导出顺序：树的**先序**（父先于子，同级按 sortNo、id）。
     *
     * <p>为什么不用 SQL 的 {@code ORDER BY path}：{@code path} 是 id 路径，字符串序不等于层级序
     * （{@code /1/12/} 会排在 {@code /1/2/} 之前），而导入校验器要求父行先于子行（import-spec §2.2）。
     */
    static List<SysOrg> exportOrder(List<SysOrg> all) {
        Map<Long, List<SysOrg>> children = new LinkedHashMap<>();
        List<SysOrg> roots = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (SysOrg org : all) {
            ids.add(org.getId());
        }
        for (SysOrg org : all) {
            if (org.getParentId() == null || !ids.contains(org.getParentId())) {
                roots.add(org);
            } else {
                children.computeIfAbsent(org.getParentId(), key -> new ArrayList<>()).add(org);
            }
        }
        Comparator<SysOrg> order = Comparator
                .comparing((SysOrg item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(item -> item.getId() == null ? 0L : item.getId());
        roots.sort(order);
        List<SysOrg> result = new ArrayList<>(all.size());
        for (SysOrg root : roots) {
            appendPreOrder(root, children, order, result);
        }
        return result;
    }

    private static void appendPreOrder(SysOrg node, Map<Long, List<SysOrg>> children,
                                       Comparator<SysOrg> order, List<SysOrg> sink) {
        sink.add(node);
        List<SysOrg> childNodes = children.get(node.getId());
        if (childNodes == null || childNodes.isEmpty()) {
            return;
        }
        List<SysOrg> sorted = new ArrayList<>(childNodes);
        sorted.sort(order);
        for (SysOrg child : sorted) {
            appendPreOrder(child, children, order, sink);
        }
    }
}
