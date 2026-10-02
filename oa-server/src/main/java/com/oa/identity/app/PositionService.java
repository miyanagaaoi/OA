package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.identity.api.dto.PositionDtos;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.domain.SysUserPosition;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import com.oa.identity.infra.row.PositionRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位任职（一人多岗）应用服务（阶段 1.2 的岗位部分）。
 *
 * <p>契约：{@code GET/POST /api/v1/identity/users/{id}/positions}、
 * {@code DELETE /api/v1/identity/users/{id}/positions/{positionId}}。
 *
 * <h2>硬性规则落点</h2>
 * <ul>
 *   <li><b>一人多岗</b>：同一人可挂多组织；{@code (user_id, org_id)} 唯一（{@code uk_user_org}，
 *       import-spec E-POS-004），重复即 409。</li>
 *   <li><b>主岗每人至多 1 个</b>：置主岗时在**同一事务**内把旧主岗置 0
 *       （{@link PositionPolicy#demotionsFor}）。</li>
 *   <li><b>主岗回填</b>：主岗的 {@code org_id}/{@code position} 回填 {@code sys_user.org_id}/{@code position}
 *       （import-spec §2.2 第 ④ 步、T-12「主岗应与 dept_path 一致」）。</li>
 *   <li><b>停用组织不可新增任职</b>：口径源自 import-spec {@code W-ORG-016}「父组织已停用，
 *       子节点不可作为发起归属」。</li>
 * </ul>
 */
@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final SysUserPositionMapper positionMapper;
    private final SysUserMapper userMapper;
    private final OrgService orgService;

    public PositionService(SysUserPositionMapper positionMapper, SysUserMapper userMapper, OrgService orgService) {
        this.positionMapper = positionMapper;
        this.userMapper = userMapper;
        this.orgService = orgService;
    }

    /** 该员工的全部任职（主岗优先）。 */
    public List<PositionDtos.PositionView> positions(Long userId) {
        requireUser(userId);
        return views(positionMapper.selectRowsByUserId(userId));
    }

    /** 新增任职（{@code isPrimary=true} 或该人尚无主岗时，自动切换主岗）。 */
    @Transactional(rollbackFor = Exception.class)
    public PositionDtos.PositionView add(Long userId, PositionDtos.PositionCreateRequest request) {
        Long operator = currentUserId();
        SysUser user = requireUser(userId);
        SysOrg org = orgService.requireVisibleOrg(request.orgId());
        if (!org.isEnabled()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "组织「" + org.getName() + "」已停用，不可作为任职组织（W-ORG-016）");
        }
        if (positionMapper.selectByUserAndOrg(userId, org.getId()) != null) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "该员工在此组织已有任职（uk_user_org：(user_id, org_id) 唯一，import-spec E-POS-004）");
        }
        List<SysUserPosition> existing = positionMapper.selectByUserId(userId);
        PositionPolicy.assertSinglePrimary(assignments(existing));

        boolean primary = Boolean.TRUE.equals(request.isPrimary());
        if (!primary && PositionPolicy.primaryOf(assignments(existing), userId).isEmpty()) {
            // 该人尚无主岗：首个岗位默认主岗，维持「有岗位则有且仅有 1 个主岗」
            primary = true;
        }

        SysUserPosition row = new SysUserPosition();
        row.setUserId(userId);
        row.setOrgId(org.getId());
        row.setIsPrimary(primary ? 1 : 0);
        row.setPosition(request.position());
        row.setRemark(request.remark());
        positionMapper.insertPosition(row);

        if (primary) {
            for (Long demoteId : PositionPolicy.demotionsFor(assignments(existing), userId, row.getId())) {
                positionMapper.updatePrimary(demoteId, 0);
                log.info("主岗切换：旧主岗 positionId={} 已置 0（userId={}）", demoteId, userId);
            }
            syncUserPrimary(user, org, request.position(), operator);
        }
        return view(positionMapper.selectRowsByUserId(userId).stream()
                .filter(item -> item.getId().equals(row.getId()))
                .findFirst()
                .orElse(null));
    }

    /** 解除一条任职；若解除的是主岗且仍有其它任职，则把最早的任职提升为主岗。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long positionId) {
        Long operator = currentUserId();
        SysUser user = requireUser(userId);
        SysUserPosition row = positionMapper.selectById(positionId);
        if (row == null || !userId.equals(row.getUserId())) {
            throw BizException.notFound("岗位任职");
        }
        List<SysUserPosition> before = positionMapper.selectByUserId(userId);
        positionMapper.deleteById(positionId);

        Optional<Long> promote = PositionPolicy.promotionAfterRemoval(assignments(before), userId, positionId);
        if (promote.isPresent()) {
            positionMapper.updatePrimary(promote.get(), 1);
            log.info("主岗兜底：解除主岗 positionId={} 后提升 positionId={}（userId={}）",
                    positionId, promote.get(), userId);
        }
        List<SysUserPosition> after = positionMapper.selectByUserId(userId);
        Optional<PositionPolicy.Assignment> primary = PositionPolicy.primaryOf(assignments(after), userId);
        if (primary.isPresent()) {
            // 注意：这里用 findOrg（不做数据域校验）—— 兼职可能落在其它公司（W-POS-009 跨公司兼职），
            // 主岗回填属于系统口径的内部一致性维护，不应因为调用人数据域而失败。
            SysOrg org = orgService.findOrg(primary.get().orgId());
            if (org != null) {
                syncUserPrimary(user, org, primary.get().position(), operator);
            }
        }
    }

    /**
     * 修改任职 / 设为主岗（{@code PUT /api/v1/identity/users/{id}/positions/{positionId}}）。
     *
     * <p>口径（施工要求第 5 条；import-spec §2.2 第 ④ 步 / T-12 / E-POS-005）：
     * <ol>
     *   <li><b>设为主岗</b>（{@code isPrimary=true}）：同一事务内用
     *       {@link PositionPolicy#demotionsFor} 把该人的**旧主岗置 0**，再把本行置 1，
     *       并把本行的 {@code org_id}/{@code position} **回填** {@code sys_user.org_id}/{@code position}
     *       —— 与 {@link #add} / {@link #remove} 复用同一套纯函数与回填路径，不另写一份判定；</li>
     *   <li><b>置为副岗</b>（{@code isPrimary=false}）：若本行是此人**唯一**主岗则 409
     *       （否则会出现「有岗位但无主岗」，与「首个岗位默认主岗」的服务约定冲突）；
     *       否则复用 {@link PositionPolicy#promotionAfterRemoval} 把剩余岗位中 id 最小的一条提升为主岗；</li>
     *   <li><b>岗位名</b>：{@code postName} 为规范名，{@code position} 为旧别名（前端历史实现），
     *       两者都为空则不改；<b>备注</b>传 {@code null} 表示不变更。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public PositionDtos.PositionView update(Long userId, Long positionId, PositionDtos.PositionUpdateRequest request) {
        Long operator = currentUserId();
        SysUser user = requireUser(userId);
        SysUserPosition row = positionMapper.selectById(positionId);
        if (row == null || !userId.equals(row.getUserId())) {
            throw BizException.notFound("岗位任职");
        }
        String postName = firstNonBlank(request.postName(), request.position());
        List<SysUserPosition> existing = positionMapper.selectByUserId(userId);
        PositionPolicy.assertSinglePrimary(assignments(existing));

        Integer primaryFlag = null;
        Long promoteId = null;
        if (Boolean.TRUE.equals(request.isPrimary())) {
            primaryFlag = 1;
            for (Long demoteId : PositionPolicy.demotionsFor(assignments(existing), userId, positionId)) {
                positionMapper.updatePrimary(demoteId, 0);
                log.info("主岗切换：旧主岗 positionId={} 已置 0（userId={}）", demoteId, userId);
            }
        } else if (Boolean.FALSE.equals(request.isPrimary()) && row.primary()) {
            promoteId = PositionPolicy.promotionAfterRemoval(assignments(existing), userId, positionId)
                    .orElseThrow(() -> new BizException(ErrorCode.CONFLICT,
                            "该岗位是此人唯一的主岗，不能置否（会出现无主岗状态）；"
                                    + "请先新增其它岗位，或直接解除本岗位"));
            primaryFlag = 0;
        }

        positionMapper.updatePosition(positionId, postName, request.remark(), primaryFlag);
        if (promoteId != null) {
            positionMapper.updatePrimary(promoteId, 1);
            log.info("主岗兜底：把 positionId={} 提升为主岗（userId={}）", promoteId, userId);
        }
        if (primaryFlag != null && primaryFlag == 1) {
            // 主岗回填：用 findOrg（不做数据域校验）—— 兼职可能落在其它公司（W-POS-009 跨公司兼职），
            // 回填属系统口径的内部一致性维护，不应因调用人数据域而失败（与 remove 同一口径）。
            SysOrg org = orgService.findOrg(row.getOrgId());
            if (org != null) {
                syncUserPrimary(user, org, postName == null ? row.getPosition() : postName, operator);
            }
        }
        log.info("岗位任职已修改 userId={} positionId={} postName={} isPrimary={} operator={}",
                userId, positionId, postName, primaryFlag, operator);
        return view(positionMapper.selectRowsByUserId(userId).stream()
                .filter(item -> item.getId().equals(positionId))
                .findFirst()
                .orElse(null));
    }

    /** 主岗回填 {@code sys_user.org_id} 与 {@code sys_user.position}（T-12）。 */
    private void syncUserPrimary(SysUser user, SysOrg org, String position, Long operator) {
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setOrgId(org.getId());
        Long companyId = deriveCompanyId(org);
        if (companyId != null) {
            update.setCompanyId(companyId);
        }
        if (position != null && !position.isBlank()) {
            update.setPosition(position);
        }
        update.setUpdatedBy(operator);
        userMapper.updateUserProfile(update);
    }

    /**
     * 归属公司：最近的「公司」祖先；没有公司祖先时取集团根（import-spec E-USER-005：集团本部人员填集团）。
     *
     * <p>{@code sys_org} **没有** {@code company_id} 列（DDL 只有 {@code parent_id}），
     * 因此归属公司只能按 {@code path} 里的祖先链推导；链上既无公司也无集团时返回 {@code null}
     * （调用方保持既有值不变，绝不臆造公司 id）。
     */
    private Long deriveCompanyId(SysOrg org) {
        List<SysOrg> chain = new ArrayList<>();
        chain.add(org);
        for (Long ancestorId : OrgHierarchy.ancestorIds(org.getPath())) {
            SysOrg ancestor = orgService.findOrg(ancestorId);
            if (ancestor != null) {
                chain.add(ancestor);
            }
        }
        for (SysOrg node : chain) {
            if (IdentityEnums.OrgType.COMPANY.code().equals(node.getOrgType())) {
                return node.getId();
            }
        }
        for (SysOrg node : chain) {
            if (IdentityEnums.OrgType.GROUP.code().equals(node.getOrgType())) {
                return node.getId();
            }
        }
        return null;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectUserById(userId);
        if (user == null) {
            throw BizException.notFound("人员");
        }
        return user;
    }

    /** 岗位名兼容取值：规范名 {@code postName} 优先，旧别名 {@code position} 兜底（都空即不变更）。 */
    private static String firstNonBlank(String canonical, String alias) {
        if (canonical != null && !canonical.isBlank()) {
            return canonical.trim();
        }
        if (alias != null && !alias.isBlank()) {
            return alias.trim();
        }
        return null;
    }

    private Long currentUserId() {
        return DataScopeContext.require().getUserId();
    }

    private static List<PositionPolicy.Assignment> assignments(List<SysUserPosition> rows) {
        List<PositionPolicy.Assignment> result = new ArrayList<>(rows.size());
        for (SysUserPosition row : rows) {
            result.add(new PositionPolicy.Assignment(row.getId(), row.getUserId(), row.getOrgId(), row.primary(), row.getPosition()));
        }
        return result;
    }

    private static List<PositionDtos.PositionView> views(List<PositionRow> rows) {
        List<PositionDtos.PositionView> result = new ArrayList<>(rows.size());
        for (PositionRow row : rows) {
            result.add(view(row));
        }
        return result;
    }

    private static PositionDtos.PositionView view(PositionRow row) {
        if (row == null) {
            throw BizException.notFound("岗位任职");
        }
        return new PositionDtos.PositionView(
                row.getId(),
                row.getUserId(),
                row.getOrgId(),
                row.getOrgName(),
                row.getOrgPath(),
                row.getOrgType(),
                OrgService.labelOfOrgType(row.getOrgType()),
                row.getPosition(),
                row.primary(),
                row.getRemark(),
                row.getCreatedAt() == null ? null : row.getCreatedAt().toString().replace('T', ' '));
    }
}
