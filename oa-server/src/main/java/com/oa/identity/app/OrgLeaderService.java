package com.oa.identity.app;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.identity.api.dto.LeaderDtos;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.IdentityEnums.Category;
import com.oa.identity.domain.IdentityEnums.LeaderType;
import com.oa.identity.domain.IdentityEnums.UserStatus;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysOrgLeader;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.row.LeaderCandidateRow;
import com.oa.identity.infra.row.LeaderRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 岗位与负责人（阶段 1.2 的负责人部分）—— 应用服务。
 *
 * <p>契约（{@code normify-oa} 基线 {@code oa.identity.position.*}）：
 * <ul>
 *   <li>{@code GET/POST /api/v1/identity/orgs/{id}/leaders}、{@code PUT/DELETE .../leaders/{leaderId}}</li>
 *   <li>{@code GET /api/v1/identity/orgs/{id}/leader-candidates}</li>
 *   <li>{@code GET /api/v1/identity/leaders/lines}、{@code PUT /api/v1/identity/leaders/lines/{category}}</li>
 *   <li>{@code GET /api/v1/identity/users/{id}/leader-of}</li>
 * </ul>
 *
 * <h2>硬性规则落点</h2>
 * <ul>
 *   <li><b>正职唯一</b>（按 {@code (org_id, category)} 分组）：{@link LeaderPolicy#assertPrimaryUnique}；
 *       业务线绑定（{@code category} 非空）与普通负责人（{@code category} 为空）互不冲突；</li>
 *   <li><b>业务线仅集团层可绑</b>：{@link LeaderPolicy#assertCategoryAllowed}（import-spec E-LEAD-008）；</li>
 *   <li><b>离职人员不得任负责人</b>：{@link #requireBindableUser}（import-spec E-LEAD-006）；</li>
 *   <li>{@code sys_org.leader_id} 是**冗余**列：普通负责人组（{@code category IS NULL}）的正职变更后
 *       由 {@link #syncOrgLeaderId} 同步（import-spec §2.2 第 ③ 步「取该组织正职」）。</li>
 * </ul>
 */
@Service
public class OrgLeaderService {

    private static final Logger log = LoggerFactory.getLogger(OrgLeaderService.class);

    private final SysOrgLeaderMapper leaderMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final OrgService orgService;

    public OrgLeaderService(SysOrgLeaderMapper leaderMapper, SysOrgMapper orgMapper,
                            SysUserMapper userMapper, OrgService orgService) {
        this.leaderMapper = leaderMapper;
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.orgService = orgService;
    }

    // ================================================================ 查询

    /** 该组织的负责人列表（正职优先、再按 sort_no）。 */
    public List<LeaderDtos.LeaderView> leaders(Long orgId) {
        orgService.requireVisibleOrg(orgId);
        return views(leaderMapper.selectByOrgId(orgId));
    }

    /**
     * 负责人候选人（该组织成员 + 是否已任职标注）。
     *
     * @param keyword 姓名/账号/工号关键字（**服务端过滤**，大小写不敏感；为空即不过滤）
     */
    public List<LeaderDtos.LeaderCandidateView> candidates(Long orgId, String keyword) {
        orgService.requireVisibleOrg(orgId);
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<LeaderCandidateRow> rows = leaderMapper.selectCandidates(orgId, trimmed);
        List<LeaderDtos.LeaderCandidateView> result = new ArrayList<>(rows.size());
        for (LeaderCandidateRow row : rows) {
            LeaderType type = LeaderType.ofCode(row.getLeaderType());
            Category category = Category.ofCode(row.getCategory());
            result.add(new LeaderDtos.LeaderCandidateView(
                    row.getUserId(),
                    row.getName(),
                    row.getAccount(),
                    row.getEmployeeNo(),
                    row.getUserStatus(),
                    row.getOrgId(),
                    row.getPosition(),
                    row.getIsPrimary() != null && row.getIsPrimary() == 1,
                    row.leader(),
                    row.getLeaderId(),
                    row.getLeaderType(),
                    type == null ? null : type.label(),
                    row.getCategory(),
                    category == null ? null : category.label()));
        }
        return result;
    }

    /** 集团层按业务线绑定的分管领导（五个事项类别全部返回，未配置时 {@code configured=false}）。 */
    public List<LeaderDtos.LeaderLineView> lines() {
        List<LeaderRow> rows = leaderMapper.selectGroupLines();
        Map<String, List<LeaderRow>> grouped = new LinkedHashMap<>();
        Long groupOrgId = null;
        String groupOrgName = null;
        for (LeaderRow row : rows) {
            grouped.computeIfAbsent(row.getCategory(), key -> new ArrayList<>()).add(row);
            if (groupOrgId == null) {
                groupOrgId = row.getOrgId();
                groupOrgName = row.getOrgName();
            }
        }
        List<LeaderDtos.LeaderLineView> result = new ArrayList<>();
        for (Category category : Category.values()) {
            List<LeaderRow> items = grouped.getOrDefault(category.code(), List.of());
            result.add(new LeaderDtos.LeaderLineView(
                    category.code(),
                    category.label(),
                    groupOrgId,
                    groupOrgName,
                    !items.isEmpty(),
                    views(items)));
        }
        return result;
    }

    /** 该人担任负责人的组织清单。 */
    public List<LeaderDtos.LeaderOfView> leaderOf(Long userId) {
        SysUser user = userMapper.selectUserById(userId);
        if (user == null) {
            throw BizException.notFound("人员");
        }
        List<LeaderRow> rows = leaderMapper.selectByUserId(userId);
        List<LeaderDtos.LeaderOfView> result = new ArrayList<>(rows.size());
        for (LeaderRow row : rows) {
            LeaderType type = LeaderType.ofCode(row.getLeaderType());
            Category category = Category.ofCode(row.getCategory());
            result.add(new LeaderDtos.LeaderOfView(
                    row.getOrgId(),
                    row.getOrgPath(),
                    row.getOrgName(),
                    row.getOrgType(),
                    row.getLeaderType(),
                    type == null ? null : type.label(),
                    row.getCategory(),
                    category == null ? null : category.label(),
                    row.getSortNo(),
                    row.getDutyTitle()));
        }
        return result;
    }

    // ================================================================ 变更

    /** 新增负责人绑定（正职唯一 + 业务线仅集团层 + 离职人员不可绑）。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaderDtos.LeaderView bind(Long orgId, LeaderDtos.LeaderCreateRequest request) {
        Long operator = currentUserId();
        SysOrg org = orgService.requireVisibleOrg(orgId);
        LeaderType leaderType = request.leaderType() == null || request.leaderType().isBlank()
                ? LeaderType.PRIMARY
                : LeaderType.parse(request.leaderType());
        Category category = parseCategory(request.category());
        LeaderPolicy.assertCategoryAllowed(IdentityEnums.OrgType.ofCode(org.getOrgType()), category);
        SysUser user = requireBindableUser(request.userId());

        String categoryCode = category == null ? null : category.code();
        SysOrgLeader duplicated = leaderMapper.selectBinding(orgId, user.getId(), leaderType.code(), categoryCode);
        if (duplicated != null) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "该负责人绑定已存在（uk_org_leader：(org_id, user_id, leader_type, category) 唯一）");
        }
        LeaderPolicy.Binding candidate = new LeaderPolicy.Binding(null, orgId, user.getId(), leaderType, category);
        LeaderPolicy.assertPrimaryUnique(bindings(orgId, category), candidate, org.getName());

        SysOrgLeader row = new SysOrgLeader();
        row.setOrgId(orgId);
        row.setUserId(user.getId());
        row.setLeaderType(leaderType.code());
        row.setDutyTitle(request.dutyTitle());
        row.setCategory(categoryCode);
        row.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        row.setEffectiveFrom(request.effectiveFrom());
        row.setEffectiveTo(request.effectiveTo());
        row.setRemark(request.remark());
        row.setCreatedBy(operator);
        leaderMapper.insertLeader(row);
        syncOrgLeaderId(orgId, operator);
        log.info("负责人绑定新增 orgId={} leaderId={} userId={} type={} category={} operator={}",
                orgId, row.getId(), user.getId(), leaderType.code(), categoryCode, operator);
        return view(leaderMapper.selectRowById(row.getId()));
    }

    /** 调整负责人（正/副职、岗位名、业务线、排序、生效区间、备注）；{@code category} 传空即清除业务线。 */
    @Transactional(rollbackFor = Exception.class)
    public LeaderDtos.LeaderView update(Long orgId, Long leaderId, LeaderDtos.LeaderUpdateRequest request) {
        Long operator = currentUserId();
        SysOrg org = orgService.requireVisibleOrg(orgId);
        SysOrgLeader existing = requireLeaderOfOrg(orgId, leaderId);
        LeaderType leaderType = request.leaderType() == null || request.leaderType().isBlank()
                ? (existing.leaderTypeEnum() == null ? LeaderType.PRIMARY : existing.leaderTypeEnum())
                : LeaderType.parse(request.leaderType());
        Category category = parseCategory(request.category());
        LeaderPolicy.assertCategoryAllowed(IdentityEnums.OrgType.ofCode(org.getOrgType()), category);

        String categoryCode = category == null ? null : category.code();
        SysOrgLeader duplicated = leaderMapper.selectBinding(orgId, existing.getUserId(), leaderType.code(), categoryCode);
        if (duplicated != null && !Objects.equals(duplicated.getId(), leaderId)) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "调整后会与既有绑定重复（uk_org_leader：(org_id, user_id, leader_type, category) 唯一）");
        }
        LeaderPolicy.Binding candidate = new LeaderPolicy.Binding(leaderId, orgId, existing.getUserId(), leaderType, category);
        LeaderPolicy.assertPrimaryUnique(bindings(orgId, category), candidate, org.getName());

        SysOrgLeader update = new SysOrgLeader();
        update.setId(leaderId);
        update.setLeaderType(leaderType.code());
        update.setDutyTitle(request.dutyTitle());
        update.setCategory(categoryCode);
        update.setSortNo(request.sortNo());
        update.setEffectiveFrom(request.effectiveFrom());
        update.setEffectiveTo(request.effectiveTo());
        update.setRemark(request.remark());
        update.setUpdatedBy(operator);
        leaderMapper.updateLeader(update);
        syncOrgLeaderId(orgId, operator);
        return view(leaderMapper.selectRowById(leaderId));
    }

    /** 解除负责人绑定（若解绑的是普通负责人组的正职，同步清空 {@code sys_org.leader_id}）。 */
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long orgId, Long leaderId) {
        Long operator = currentUserId();
        orgService.requireVisibleOrg(orgId);
        requireLeaderOfOrg(orgId, leaderId);
        leaderMapper.deleteById(leaderId);
        syncOrgLeaderId(orgId, operator);
        log.info("负责人绑定解除 orgId={} leaderId={} operator={}", orgId, leaderId, operator);
    }

    /**
     * 设置某业务线的分管领导（{@code PUT /leaders/lines/{category}}）——**替换语义**。
     *
     * <p>该业务线原有的正职分管领导会被解绑，由 {@code userId} 顶替；
     * 副职不受影响；普通负责人组（{@code category IS NULL}）不受任何影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaderDtos.LeaderView setLine(String categoryValue, LeaderDtos.LeaderLineUpsertRequest request) {
        Long operator = currentUserId();
        Category category = Category.parse(categoryValue);
        Long orgId = request.orgId() == null ? orgService.requireGroupRoot().getId() : request.orgId();
        SysOrg org = orgService.requireVisibleOrg(orgId);
        LeaderPolicy.assertCategoryAllowed(IdentityEnums.OrgType.ofCode(org.getOrgType()), category);
        SysUser user = requireBindableUser(request.userId());
        LeaderType leaderType = request.leaderType() == null || request.leaderType().isBlank()
                ? LeaderType.PRIMARY
                : LeaderType.parse(request.leaderType());

        List<SysOrgLeader> existing = leaderMapper.selectBindings(orgId, category.code());
        SysOrgLeader mine = null;
        for (SysOrgLeader row : existing) {
            if (Objects.equals(row.getUserId(), user.getId())) {
                mine = row;
                continue;
            }
            if (row.isPrimary() && leaderType == LeaderType.PRIMARY) {
                // 替换：同一业务线只保留一个正职
                leaderMapper.deleteById(row.getId());
                log.info("业务线分管领导替换：解绑旧正职 leaderId={} category={}", row.getId(), category.code());
            }
        }
        if (mine == null) {
            SysOrgLeader row = new SysOrgLeader();
            row.setOrgId(orgId);
            row.setUserId(user.getId());
            row.setLeaderType(leaderType.code());
            row.setDutyTitle(request.dutyTitle());
            row.setCategory(category.code());
            row.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
            row.setRemark(request.remark());
            row.setCreatedBy(operator);
            leaderMapper.insertLeader(row);
            mine = row;
        } else {
            SysOrgLeader update = new SysOrgLeader();
            update.setId(mine.getId());
            update.setLeaderType(leaderType.code());
            update.setDutyTitle(request.dutyTitle());
            update.setCategory(category.code());
            update.setSortNo(request.sortNo());
            update.setRemark(request.remark());
            update.setUpdatedBy(operator);
            leaderMapper.updateLeader(update);
        }
        log.info("业务线分管领导已设置 category={} orgId={} userId={} operator={}",
                category.code(), orgId, user.getId(), operator);
        return view(leaderMapper.selectRowById(mine.getId()));
    }

    // ================================================================ 内部

    private Category parseCategory(String value) {
        return value == null || value.isBlank() ? null : Category.parse(value);
    }

    /** 被绑人必须存在、非离职，且落在调用人数据域内（域外返回 404，防跨域绑定）。 */
    private SysUser requireBindableUser(Long userId) {
        SysUser user = userMapper.selectUserById(userId);
        if (user == null) {
            throw BizException.notFound("人员");
        }
        if (UserStatus.RESIGNED.code().equals(user.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "离职人员不得担任负责人（import-spec E-LEAD-006）：" + user.getName());
        }
        return user;
    }

    private SysOrgLeader requireLeaderOfOrg(Long orgId, Long leaderId) {
        SysOrgLeader row = leaderMapper.selectEntityById(leaderId);
        if (row == null || !Objects.equals(row.getOrgId(), orgId)) {
            throw BizException.notFound("负责人绑定");
        }
        return row;
    }

    /** 该组织该业务线（{@code category} 为 {@code null} 即普通负责人组）的既有绑定，供纯函数判定正职唯一。 */
    private List<LeaderPolicy.Binding> bindings(Long orgId, Category category) {
        List<SysOrgLeader> rows = leaderMapper.selectBindings(orgId, category == null ? null : category.code());
        List<LeaderPolicy.Binding> result = new ArrayList<>(rows.size());
        for (SysOrgLeader row : rows) {
            result.add(new LeaderPolicy.Binding(row.getId(), row.getOrgId(), row.getUserId(),
                    row.leaderTypeEnum(), row.categoryEnum()));
        }
        return result;
    }

    /** 冗余回填：{@code sys_org.leader_id} = 普通负责人组（category IS NULL）的正职。 */
    private void syncOrgLeaderId(Long orgId, Long operator) {
        List<SysOrgLeader> rows = leaderMapper.selectBindings(orgId, null);
        Long primaryUserId = null;
        for (SysOrgLeader row : rows) {
            if (row.isPrimary()) {
                primaryUserId = row.getUserId();
                break;
            }
        }
        orgMapper.updateLeaderId(orgId, primaryUserId, operator);
    }

    private List<LeaderDtos.LeaderView> views(List<LeaderRow> rows) {
        List<LeaderDtos.LeaderView> result = new ArrayList<>(rows.size());
        for (LeaderRow row : rows) {
            result.add(view(row));
        }
        return result;
    }

    private LeaderDtos.LeaderView view(LeaderRow row) {
        if (row == null) {
            throw BizException.notFound("负责人绑定");
        }
        LeaderType type = LeaderType.ofCode(row.getLeaderType());
        Category category = Category.ofCode(row.getCategory());
        return new LeaderDtos.LeaderView(
                row.getId(),
                row.getOrgId(),
                row.getOrgPath(),
                row.getOrgName(),
                row.getUserId(),
                row.getUserName(),
                row.getAccount(),
                row.getEmployeeNo(),
                row.getLeaderType(),
                type == null ? null : type.label(),
                row.getDutyTitle(),
                row.getCategory(),
                category == null ? null : category.label(),
                row.getSortNo(),
                row.getRemark(),
                row.getUserStatus());
    }

    private Long currentUserId() {
        return DataScopeContext.require().getUserId();
    }
}
