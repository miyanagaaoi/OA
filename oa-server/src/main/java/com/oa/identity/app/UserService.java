package com.oa.identity.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.authz.visibility.ExportFieldPolicy;
import com.oa.authz.visibility.ExportTarget;
import com.oa.authz.visibility.PhoneVisibilityService;
import com.oa.common.api.PageResult;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.common.security.PasswordService;
import com.oa.common.security.SessionStore;
import com.oa.identity.api.dto.DirectoryDtos;
import com.oa.identity.api.dto.InFlightDtos;
import com.oa.identity.api.dto.LeaderDtos;
import com.oa.identity.api.dto.UserDtos;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.IdentityEnums.OrgType;
import com.oa.identity.domain.IdentityEnums.UserStatus;
import com.oa.identity.domain.SysOrg;
import com.oa.identity.domain.SysUser;
import com.oa.identity.domain.SysUserPosition;
import com.oa.identity.domain.SysUserSession;
import com.oa.identity.infra.SysOrgMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import com.oa.platform.security.crypto.PhoneCryptoService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人员应用服务（阶段 1.1 的人员部分 + 1.2 的岗位协作）。
 *
 * <p>契约（{@code normify-oa} 基线 {@code oa.identity.user.*} / {@code position.*}）：
 * <ul>
 *   <li>{@code GET/POST /api/v1/identity/users}、{@code PUT /api/v1/identity/users/{id}}</li>
 *   <li>{@code POST /api/v1/identity/users/{id}/resign}、{@code /transfer}、{@code /handover}</li>
 *   <li>{@code GET  /api/v1/identity/users/{id}/pending-tasks}</li>
 *   <li>{@code GET  /api/v1/identity/directory}</li>
 *   <li>{@code GET  /api/v1/identity/watermark-policy}、{@code /users/me/watermark}</li>
 * </ul>
 *
 * <h2>硬性规则落点</h2>
 * <ol>
 *   <li><b>数据域</b>：所有人员读取走 {@code SysUserMapper} 的**带标记 XML 语句**
 *       （本人 ∪ 本部门子树 ∪ 本公司 ∪ 归口部门子树），域外人员一律 404；</li>
 *   <li><b>离职前清空待办</b>（AC-12 / PRD §5.5 / import-spec §8.1）：{@code resign} 先走
 *       {@link InFlightChecker#checkUser} 与 {@link InFlightGuard}，命中时按
 *       {@code oa.identity.block-on-inflight} 拒绝（409，含数量与单号）或仅告警；
 *       系统管理员可用 {@code force=true} + 必填 {@code reason} **覆盖**该阻断放行（AC-52 双留痕），
 *       授权校验在控制器 {@link ForceReasonPolicy#assertAllowed}，服务层只负责放行与运行日志；</li>
 *   <li><b>调岗</b>：换主归属组织（{@code sys_user.org_id/company_id}），主岗随迁，
 *       其它任职默认保留；DDL **没有**任职历史表，历史以 {@code sys_log} 留痕；</li>
 *   <li><b>交接</b>：本期只返回待办清单 + 审计留痕，**不联动流程引擎**
 *       （{@code flow_task} 尚无完整实现，接入点见 {@code DefaultInFlightChecker}）。</li>
 * </ol>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** 单页上限（与 MybatisConfig 的 {@code MAX_PAGE_SIZE} 对齐：500）。 */
    private static final long MAX_PAGE_SIZE = 500L;

    /** 通讯录单次返回上限（数据域已收窄，此处再兜一层，防大集团全量拉取）。 */
    private static final int DIRECTORY_LIMIT = 2000;

    /** 账号格式（import-spec E-USER-001）：8–64 位、字母开头、仅字母/数字/下划线/点/连字符。 */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{7,63}$");

    /** 工号格式（import-spec E-USER-014）：仅字母、数字与 {@code -}，≤32。 */
    private static final Pattern EMPLOYEE_NO_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,32}$");

    /** 口令字符集与随机源见 {@link InitialPasswordGenerator}（与批量导入共用同一规则）。 */
    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserPositionMapper positionMapper;
    private final OrgService orgService;
    private final OrgLeaderService leaderService;
    private final InFlightChecker inFlightChecker;
    private final PasswordService passwordService;
    private final SessionStore sessionStore;
    private final OaProperties properties;
    /** 手机号密文读写（阶段 1.7：写加密、读解密、兼容历史明文）。 */
    private final PhoneCryptoService phoneCrypto;
    /** 手机号可见性（阶段 1.6：脱敏与「本人/系统管理员可见完整值」的**唯一实现**）。 */
    private final PhoneVisibilityService phoneVisibility;

    public UserService(SysUserMapper userMapper, SysOrgMapper orgMapper, SysUserPositionMapper positionMapper,
                       OrgService orgService, OrgLeaderService leaderService, InFlightChecker inFlightChecker,
                       PasswordService passwordService, SessionStore sessionStore, OaProperties properties,
                       PhoneCryptoService phoneCrypto, PhoneVisibilityService phoneVisibility) {
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.positionMapper = positionMapper;
        this.orgService = orgService;
        this.leaderService = leaderService;
        this.inFlightChecker = inFlightChecker;
        this.passwordService = passwordService;
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.phoneCrypto = phoneCrypto;
        this.phoneVisibility = phoneVisibility;
    }

    // ================================================================ 查询

    /**
     * 人员列表（分页 + 关键字 + 组织/状态筛选），数据域过滤在 SQL 织入。
     *
     * <p>分页参数规范名是 {@code size}（控制器同时兼容旧别名 {@code pageSize}）；
     * 每行的 {@code pendingTaskCount} 来自 {@link InFlightChecker#pendingTaskCounts}
     * ——**一次批量**取数（缺省实现逐条回退），不做逐行查询。
     */
    public PageResult<UserDtos.UserView> page(String keyword, Long orgId, Boolean includeSubOrg, String status,
                                              Long companyId, long page, long size) {
        long current = page <= 0 ? 1 : page;
        long pageSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        String statusCode = null;
        if (status != null && !status.isBlank()) {
            statusCode = UserStatus.parse(status).code();
        }
        OrgFilter filter = resolveOrgFilter(orgId, includeSubOrg);
        IPage<SysUser> result = userMapper.selectUserPage(new Page<>(current, pageSize),
                blankToNull(keyword), statusCode, filter.orgId(), filter.pathPrefix(), companyId);
        List<SysUser> records = result.getRecords() == null ? List.of() : result.getRecords();
        Map<Long, SysOrg> orgIndex = indexOrgs(records);
        Map<Long, Integer> pendingCounts = pendingTaskCounts(records);
        List<UserDtos.UserView> views = new ArrayList<>(records.size());
        CurrentUser principal = currentPrincipal();
        for (SysUser user : records) {
            views.add(view(user, orgIndex, principal, pendingCounts));
        }
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 通讯录：按组织分组的可见人员（数据域过滤 + 手机号脱敏）。 */
    public List<DirectoryDtos.DirectoryGroup> directory(String keyword, Long orgId, Boolean includeSubOrg) {
        OrgFilter filter = resolveOrgFilter(orgId, includeSubOrg);
        List<SysUser> users = userMapper.selectDirectoryUsers(blankToNull(keyword), filter.orgId(),
                filter.pathPrefix(), DIRECTORY_LIMIT);
        Map<Long, SysOrg> orgIndex = indexOrgs(users);
        CurrentUser principal = currentPrincipal();
        Map<Long, List<SysUser>> grouped = new LinkedHashMap<>();
        for (SysUser user : users) {
            grouped.computeIfAbsent(user.getOrgId(), key -> new ArrayList<>()).add(user);
        }
        List<DirectoryDtos.DirectoryGroup> groups = new ArrayList<>(grouped.size());
        for (Map.Entry<Long, List<SysUser>> entry : grouped.entrySet()) {
            SysOrg org = entry.getKey() == null ? null : orgIndex.get(entry.getKey());
            List<DirectoryDtos.DirectoryUser> items = new ArrayList<>(entry.getValue().size());
            for (SysUser user : entry.getValue()) {
                // 手机号：解密（1.7）+ 脱敏（1.6）都在 PhoneVisibilityService 内完成，此处不手写 substring
                PhoneVisibilityService.PhoneDisplay phone =
                        phoneVisibility.display(principal, user.getId(), user.getPhone());
                items.add(new DirectoryDtos.DirectoryUser(
                        user.getId(),
                        user.getName(),
                        user.getAccount(),
                        user.getEmployeeNo(),
                        phone.value(),
                        phone.masked(),
                        user.getPosition(),
                        user.getEmail(),
                        org == null ? null : org.getName()));
            }
            groups.add(new DirectoryDtos.DirectoryGroup(
                    entry.getKey(),
                    org == null ? null : org.getName(),
                    org == null ? null : org.getPath(),
                    org == null ? null : org.getOrgType(),
                    org == null ? null : OrgService.labelOfOrgType(org.getOrgType()),
                    org == null ? null : org.getDepth(),
                    items.size(),
                    items));
        }
        return groups;
    }

    /** 水印策略（常量集中处见 {@link WatermarkPolicy}）。 */
    public DirectoryDtos.WatermarkPolicyView watermarkPolicy() {
        return WatermarkPolicy.view();
    }

    /** 当前登录人的水印载荷（「姓名 + 工号」，REQ-USER-004 / AC-44）。 */
    public DirectoryDtos.WatermarkView myWatermark() {
        CurrentUser principal = currentPrincipal();
        return WatermarkPolicy.payload(principal.id(), principal.name(), principal.employeeNo());
    }

    /** 名下待办清单（AC-12 的「提示未处理任务数量」）。 */
    public List<UserDtos.PendingTaskView> pendingTasks(Long userId) {
        requireUser(userId);
        return pendingViews(inFlightChecker.pendingTasksOf(userId));
    }

    /**
     * 人员影响清单（{@code GET /api/v1/identity/users/{id}/in-flight-check}，施工要求第 3 条）。
     *
     * <p>比 {@link #pendingTasks} 更全：除待办数外还给出在途单据数与明细行
     * （单据类型 / 发起人 / 当前节点 / 状态），离职、调岗、停用前的二次确认弹窗用它。
     *
     * <p><b>数据来源仍是同一个端口</b>（{@link InFlightChecker}）：数量走
     * {@link InFlightChecker#checkUser}，明细走 {@link InFlightChecker#inFlightItems}——
     * 两者在实现方必须共用同一套过滤条件，不得另造第二套判定。
     * 流程表尚未落地时缺省实现返回 0 与空数组（前端据此直接渲染「无影响」）。
     */
    public InFlightDtos.UserInFlightCheckView inFlightCheck(Long userId) {
        requireUser(userId);
        InFlightChecker.InFlightSummary summary = inFlightChecker.checkUser(userId);
        return new InFlightDtos.UserInFlightCheckView(
                summary.pendingTasks(),
                summary.inFlightInstances(),
                InFlightViews.views(inFlightChecker.inFlightItems(userId)));
    }

    // ================================================================ 变更

    /**
     * 新增人员与账号。
     *
     * <p>初始口令由服务端随机生成（≥8 位、含字母与数字，REQ-NFR-005），**只在本次响应返回一次**；
     * 落库只存 BCrypt 哈希（{@code password_hash}），首登由认证模块强制改密（import-spec T-03）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.UserCreatedView create(UserDtos.UserCreateRequest request) {
        Long operator = currentUserId();
        String account = request.account().trim().toLowerCase(Locale.ROOT);
        if (!ACCOUNT_PATTERN.matcher(account).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "账号需 8–64 位、以字母开头，仅含字母/数字/下划线/点/连字符（import-spec E-USER-001）");
        }
        if (userMapper.countByAccount(account, null) > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "登录账号已存在（import-spec E-USER-002）");
        }
        String employeeNo = blankToNull(request.employeeNo());
        if (employeeNo != null) {
            if (!EMPLOYEE_NO_PATTERN.matcher(employeeNo).matches()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "工号仅允许字母、数字与连字符且不超过 32 字符（import-spec E-USER-014）");
            }
            if (userMapper.countByEmployeeNo(employeeNo, null) > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "工号已存在，需全库唯一（import-spec E-USER-015）");
            }
        }
        SysOrg company = requireCompany(request.companyId());
        SysOrg dept = null;
        if (request.orgId() != null) {
            dept = requireDept(request.orgId(), company);
        }
        UserStatus status = request.status() == null || request.status().isBlank()
                ? UserStatus.ACTIVE
                : UserStatus.parse(request.status());

        String initialPassword = randomPassword(12);
        SysUser user = new SysUser();
        user.setAccount(account);
        user.setName(request.name().trim());
        user.setEmployeeNo(employeeNo);
        user.setPasswordHash(passwordService.encode(initialPassword));
        // 手机号写加密（阶段 1.7）：落库一律密文，禁止明文入库（PRD §5.3 / REQ-NFR-005）
        user.setPhone(phoneCrypto.encryptForStore(blankToNull(request.phone())));
        user.setEmail(blankToNull(request.email()));
        user.setOrgId(dept == null ? null : dept.getId());
        user.setCompanyId(company.getId());
        user.setPosition(blankToNull(request.position()));
        user.setStatus(status.code());
        user.setRemark(request.remark());
        user.setCreatedBy(operator);
        user.setUpdatedBy(operator);
        userMapper.insertUser(user);

        log.info("人员已创建 id={} account={} companyId={} deptId={} operator={}",
                user.getId(), account, company.getId(), user.getOrgId(), operator);
        Map<Long, SysOrg> orgIndex = indexOrgs(List.of(user));
        return new UserDtos.UserCreatedView(view(user, orgIndex, currentPrincipal(), pendingTaskCounts(List.of(user))),
                initialPassword,
                "初始口令仅本次返回，请通过线下渠道分发；首次登录必须修改口令（REQ-NFR-005 / import-spec T-03）");
    }

    /**
     * 修改人员档案（含 {@code active ⇄ disabled} 状态切换）。
     *
     * <p><b>状态口径</b>（施工要求第 8 条）：{@code status} 只接受 {@code active}/{@code disabled}
     * （中文 {@code 在职}/{@code 停用} 亦可）；{@code 离职} 必须走 {@code POST /users/{id}/resign}
     * （本接口传离职即 400），已是离职状态的人员也不经本接口改状态（409）。
     * 切到 {@code disabled} 时与离职**同一口径**：先 {@link InFlightChecker#checkUser} +
     * {@link InFlightGuard}，命中且 {@code oa.identity.block-on-inflight=true}（默认）时 409，
     * 文案含待办数量与单号；仅告警模式下放行并留痕（审计由 {@code @Audited} 承担）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.UserView update(Long id, UserDtos.UserUpdateRequest request) {
        return update(id, request, null, null);
    }

    /**
     * 修改人员档案（带 AC-52 的 {@code force}/{@code reason}）。
     *
     * <p>{@code force=true}（仅系统管理员 + 必填原因，授权校验在控制器
     * {@link ForceReasonPolicy#assertAllowed}）可**覆盖**「切到停用」时的在途/待办阻断；
     * 服务层只负责放行语义 + 运行日志，**不重复做角色判断**，也不读取请求体里的 {@code force}
     * （只能经显式参数传入，避免绕过准入）。
     *
     * @param force  已获授权的强制继续标记；{@code null}/{@code false} → 保持默认阻断
     * @param reason 强制继续原因（仅用于运行日志；审计留痕由 {@code @Audited} 承担）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.UserView update(Long id, UserDtos.UserUpdateRequest request, Boolean force, String reason) {
        Long operator = currentUserId();
        SysUser existing = requireUser(id);
        UserStatus targetStatus = editableStatus(request.status());
        if (targetStatus != null && UserStatus.RESIGNED.code().equals(existing.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "该人员已是离职状态，不可通过本接口变更状态（离职状态仅由 /users/{id}/resign 产生）");
        }
        SysOrg company = request.companyId() == null ? null : requireCompany(request.companyId());
        SysOrg dept = null;
        if (request.orgId() != null) {
            SysOrg base = company == null ? orgService.findOrg(existing.getCompanyId()) : company;
            dept = requireDept(request.orgId(), base);
        }
        String employeeNo = blankToNull(request.employeeNo());
        if (employeeNo != null) {
            if (!EMPLOYEE_NO_PATTERN.matcher(employeeNo).matches()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "工号仅允许字母、数字与连字符且不超过 32 字符（import-spec E-USER-014）");
            }
            if (userMapper.countByEmployeeNo(employeeNo, id) > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "工号已存在，需全库唯一（import-spec E-USER-015）");
            }
        }
        if (targetStatus == UserStatus.DISABLED && !UserStatus.DISABLED.code().equals(existing.getStatus())) {
            // 停用同样会让名下待办无人处理：与离职共用同一拦截口径（AC-12 / PRD §5.5）
            String subject = existing.getName() + "（" + existing.getAccount() + "）";
            InFlightChecker.InFlightSummary summary = inFlightChecker.checkUser(id);
            InFlightGuard.Outcome outcome = InFlightGuard.assertClear(InFlightGuard.Subject.USER_DISABLE, subject,
                    summary, properties.getIdentity().isBlockOnInflight(), Boolean.TRUE.equals(force));
            // 强制放行时补运行日志（双留痕的第二道；第一道由 @Audited 把 reason/force 落 sys_log）
            InFlightGuard.logForceOverride(outcome, force, reason);
        }

        SysUser update = new SysUser();
        update.setId(id);
        update.setName(request.name().trim());
        update.setEmployeeNo(employeeNo);
        // 手机号整体覆盖语义保持不变：传 null/空白 = 清空；非空值先加密再落库（阶段 1.7）
        String phoneInput = blankToNull(request.phone());
        update.setPhone(phoneInput == null ? "" : phoneCrypto.encryptForStore(phoneInput));
        update.setEmail(request.email() == null ? "" : request.email());
        update.setRemark(request.remark() == null ? "" : request.remark());
        if (company != null) {
            update.setCompanyId(company.getId());
        }
        if (dept != null) {
            update.setOrgId(dept.getId());
        }
        update.setPosition(request.position() == null ? "" : request.position());
        update.setUpdatedBy(operator);
        userMapper.updateUserProfile(update);
        if (targetStatus != null && !targetStatus.code().equals(existing.getStatus())) {
            userMapper.updateUserStatus(id, targetStatus.code(), operator);
            log.info("人员状态已变更 id={} {} → {} operator={}", id, existing.getStatus(), targetStatus.code(), operator);
        }
        SysUser reloaded = requireUser(id);
        return view(reloaded, indexOrgs(List.of(reloaded)), currentPrincipal(),
                pendingTaskCounts(List.of(reloaded)));
    }

    /**
     * 离职（AC-12 / PRD §5.5）：**先校验名下待办与在途**。
     *
     * <p>命中且 {@code oa.identity.block-on-inflight=true}（默认）→ 409，文案含数量与单号；
     * 置 {@code false} → 仅告警放行，并在出参返回影响清单。
     * 离职成功后**撤销该人全部会话**（{@code revoked_reason=disabled}），避免离职账号仍在线。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.ResignResult resign(Long id, UserDtos.ResignRequest request) {
        return resign(id, request, null, null);
    }

    /**
     * 离职（带 AC-52 的 {@code force}/{@code reason}）。
     *
     * <p>业务裁定：AC-12 的默认阻断不变，但系统管理员可用 {@code force=true} + 必填 {@code reason}
     * **覆盖**它放行；授权校验在控制器 {@link ForceReasonPolicy#assertAllowed}，
     * 服务层只负责「放行语义 + 运行日志」，**不重复做角色判断**，也不读取请求体里的 {@code force}
     * （只能经显式参数传入，避免绕过准入）。
     *
     * @param force  已获授权的强制继续标记；{@code null}/{@code false} → 保持默认阻断
     * @param reason 强制继续原因（仅用于运行日志与出参文案；审计留痕由 {@code @Audited} 承担）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.ResignResult resign(Long id, UserDtos.ResignRequest request, Boolean force, String reason) {
        Long operator = currentUserId();
        SysUser user = requireUser(id);
        if (UserStatus.RESIGNED.code().equals(user.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "该人员已是离职状态，无需重复操作");
        }
        String subject = user.getName() + "（" + user.getAccount() + "）";
        InFlightChecker.InFlightSummary summary = inFlightChecker.checkUser(id);
        InFlightGuard.Outcome outcome = InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, subject, summary,
                properties.getIdentity().isBlockOnInflight(), Boolean.TRUE.equals(force));
        // 强制放行时补运行日志（双留痕的第二道；第一道由 @Audited 把 reason/force 落 sys_log）
        InFlightGuard.logForceOverride(outcome, force, reason);
        boolean forced = InFlightGuard.forced(outcome, force);

        userMapper.updateUserStatus(id, UserStatus.RESIGNED.code(), operator);
        int revoked = sessionStore.revokeAll(id, SysUserSession.REASON_DISABLED);

        List<LeaderDtos.LeaderOfView> leaderOf = leaderService.leaderOf(id);
        StringBuilder message = new StringBuilder();
        if (forced) {
            // 被管理员强制覆盖：出参必须说清「原阻断是什么 + 已放行 + 走的是 AC-52 留痕口径」
            message.append("离职办理完成（系统管理员「强制继续」放行原阻断：").append(outcome.message())
                    .append("；AC-52：reason/force 已留痕）");
        } else {
            message.append(outcome.rejected() ? outcome.message() : "离职办理完成");
            if (outcome.blocked()) {
                message.append("（block-on-inflight=false，已按告警放行；影响清单见出参）");
            }
        }
        if (!leaderOf.isEmpty()) {
            message.append("；该员工仍担任 ").append(leaderOf.size())
                    .append(" 个组织的负责人，请在「负责人配置」中补设（import-spec W-ORG-014 / AC-11）");
        }
        if (revoked > 0) {
            message.append("；已注销在线会话 ").append(revoked).append(" 个");
        }
        log.info("人员离职完成 id={} 撤销会话={} 待办={} 在途={} force={} reason={} operator={}",
                id, revoked, summary.pendingTasks(), summary.inFlightInstances(), force,
                reason != null ? reason : (request == null ? null : request.reason()), operator);
        return new UserDtos.ResignResult(id, UserStatus.RESIGNED.code(), UserStatus.RESIGNED.label(),
                summary.pendingTasks(), summary.inFlightInstances(), summary.bizNos(), leaderOf, message.toString());
    }

    /**
     * 调岗（换主归属组织）。
     *
     * <p>按 data-model 语义实现（{@code sys_user_position} **没有**生效区间/历史表，一期不做任职历史）：
     * <ol>
     *   <li>{@code sys_user.org_id} 指向目标组织；{@code company_id} 取入参或按目标组织推导
     *       （最近的「公司」祖先，无公司祖先时取集团根——import-spec E-USER-005「集团本部人员填集团」）；</li>
     *   <li><b>主岗随迁</b>：目标组织已有任职则直接置为主岗；否则把原主岗迁到目标组织
     *       （{@code uk_user_org} 唯一，故为「删旧 + 插新」，变更以 {@code sys_log} 留痕）；</li>
     *   <li>{@code keepOtherPositions=false} 时一并解除其它任职（默认保留）。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.TransferResult transfer(Long id, UserDtos.TransferRequest request) {
        Long operator = currentUserId();
        SysUser user = requireUser(id);
        SysOrg target = orgService.requireVisibleOrg(request.targetOrgId());
        OrgType targetType = IdentityEnums.OrgType.ofCode(target.getOrgType());
        if (targetType == OrgType.GROUP) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "集团节点不可作为主归属组织（dept_path 必须是部门/科室，import-spec E-USER-006；"
                            + "集团本部人员的 company_path 才填集团）");
        }
        if (!target.isEnabled()) {
            throw new BizException(ErrorCode.CONFLICT, "目标组织「" + target.getName() + "」已停用，不可作为主归属");
        }
        SysOrg company = request.targetCompanyId() == null
                ? deriveCompany(target)
                : requireCompany(request.targetCompanyId());
        if (company != null && !OrgHierarchy.isDescendantOrSelf(target.getPath(), company.getPath())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "目标组织不在目标公司子树内（import-spec E-USER-011）");
        }

        Long oldOrgId = user.getOrgId();
        Long oldCompanyId = user.getCompanyId();
        SysUser update = new SysUser();
        update.setId(id);
        update.setOrgId(target.getId());
        if (company != null) {
            update.setCompanyId(company.getId());
        }
        update.setUpdatedBy(operator);
        userMapper.updateUserProfile(update);

        List<SysUserPosition> positions = positionMapper.selectByUserId(id);
        List<Long> removed = new ArrayList<>();
        Long primaryPositionId = null;
        SysUserPosition atTarget = null;
        PositionPolicy.Assignment previousPrimary = PositionPolicy
                .primaryOf(assignments(positions), id).orElse(null);
        for (SysUserPosition row : positions) {
            if (Objects.equals(row.getOrgId(), target.getId())) {
                atTarget = row;
                break;
            }
        }
        if (atTarget != null) {
            positionMapper.updatePrimary(atTarget.getId(), 1);
            primaryPositionId = atTarget.getId();
            for (Long demoteId : PositionPolicy.demotionsFor(assignments(positions), id, atTarget.getId())) {
                positionMapper.updatePrimary(demoteId, 0);
            }
        } else {
            SysUserPosition inserted = new SysUserPosition();
            inserted.setUserId(id);
            inserted.setOrgId(target.getId());
            inserted.setIsPrimary(1);
            inserted.setPosition(previousPrimary == null ? user.getPosition() : previousPrimary.position());
            if (previousPrimary != null) {
                SysUserPosition old = positionMapper.selectById(previousPrimary.id());
                if (old != null) {
                    positionMapper.deleteById(old.getId());
                    removed.add(old.getId());
                }
            }
            positionMapper.insertPosition(inserted);
            primaryPositionId = inserted.getId();
            for (Long demoteId : PositionPolicy.demotionsFor(assignments(positions), id, inserted.getId())) {
                positionMapper.updatePrimary(demoteId, 0);
            }
        }

        if (Boolean.FALSE.equals(request.keepOtherPositions())) {
            for (SysUserPosition row : positionMapper.selectByUserId(id)) {
                if (!Objects.equals(row.getOrgId(), target.getId())) {
                    positionMapper.deleteById(row.getId());
                    removed.add(row.getId());
                }
            }
        }
        if (previousPrimary != null && previousPrimary.position() != null && !previousPrimary.position().isBlank()) {
            SysUser positionUpdate = new SysUser();
            positionUpdate.setId(id);
            positionUpdate.setPosition(previousPrimary.position());
            positionUpdate.setUpdatedBy(operator);
            userMapper.updateUserProfile(positionUpdate);
        }

        String note = "主岗已随调岗迁至目标组织；其它任职默认保留（keepOtherPositions="
                + !Boolean.FALSE.equals(request.keepOtherPositions())
                + "）。sys_user_position 无生效区间/历史表，任职变更历史以 sys_log 留痕"
                + "（normify 基线 GET /users/{id}/assignment-history 未实现，见交付说明）。";
        log.info("人员调岗 id={} {} → {}，主岗={} 解除岗位={} operator={}",
                id, oldOrgId, target.getId(), primaryPositionId, removed, operator);
        return new UserDtos.TransferResult(id, oldOrgId, target.getId(), oldCompanyId,
                company == null ? null : company.getId(), primaryPositionId, removed, note);
    }

    /**
     * 工作交接（{@code POST /users/{id}/handover}）。
     *
     * <p><b>本期不联动流程引擎</b>：只返回该人名下待办清单并写审计留痕
     * （{@code @Audited(action="handover", targetType="user")}）。真正的转办/改派在流程模块接入后
     * 由 {@link InFlightChecker} 的实现方执行（PRD §5.5：管理员改派兜底）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserDtos.HandoverResult handover(Long id, UserDtos.HandoverRequest request) {
        SysUser from = requireUser(id);
        SysUser to = requireUser(request.toUserId());
        if (Objects.equals(from.getId(), to.getId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收人不能是本人");
        }
        List<InFlightChecker.PendingTask> tasks = inFlightChecker.pendingTasksOf(id);
        boolean transferTasks = Boolean.TRUE.equals(request.transferTasks());
        int transferred = 0;
        if (transferTasks && !tasks.isEmpty()) {
            // 待办转交依赖 flow_task 写能力（本期未落地）→ 明确告知调用方，不静默假装成功
            log.warn("工作交接请求转办 {} 条待办，但流程引擎尚未接入（flow_task 未实现），本期不执行转办：from={} to={}",
                    tasks.size(), from.getId(), to.getId());
        }
        String note = "本期只返回待办清单并记录审计留痕（不联动流程引擎）；"
                + "转办/改派待流程模块接入后由 InFlightChecker 实现方执行（PRD §5.5、AC-12）";
        return new UserDtos.HandoverResult(from.getId(), to.getId(), tasks.size(), transferred,
                pendingViews(tasks), note);
    }

    // ================================================================ 导出

    /**
     * 人员主数据导出（CSV）：**仅系统管理员**（import-spec §9.2 T-11 定稿）。
     *
     * <p>列与顺序逐字对齐 import-spec §9.1 的 {@code user.csv}（**九列**）：
     * {@code account,employee_no,name,phone,email,company_path,dept_path,status,remark}；
     * UTF-8 **带 BOM**（§4.1，否则校验器报 {@code E-ENC-001}），CRLF 行尾，RFC4180 转义。
     *
     * <p>路径列取**名称路径**（业务键 {@code org_path}），由
     * {@link OrgService#businessPathIndex()} 一次性算好 —— 直接按行内 org 拼装会缺祖先名，
     * 「导出 → 再导入」的往返校验（§9.1 往返约束 / AC-57）会失败。
     *
     * <p>口径说明：{@code phone} 对系统管理员**不脱敏**（§9.2「导出物即用于数据维护」）；
     * 取值经 {@link PhoneVisibilityService#exportPlain} 解密（库中为密文），
     * 并由该方法**内部再次校验系统管理员** —— 本方法无法绕过该约束，也不会把密文写进 CSV。
     * {@code status} 输出中文标签，其中 {@code 停用} 行按 T-05 定稿本就不参与导入往返。
     *
     * @param keyword      姓名/账号/工号关键字
     * @param orgId        归属组织（{@code includeSubOrg=true} 时按子树）
     * @param includeSubOrg 是否含子组织
     * @param status       状态筛选（code 或中文标签）
     * @param companyId    归属公司筛选
     */
    public String exportCsv(String keyword, Long orgId, Boolean includeSubOrg, String status, Long companyId) {
        CurrentUser principal = currentPrincipal();
        if (!principal.hasRole(ForceReasonPolicy.ADMIN_ROLE)) {
            throw new BizException(ErrorCode.EXPORT_DENIED,
                    "主数据（人员）导出仅系统管理员可用（import-spec §9.2）");
        }
        String statusCode = status == null || status.isBlank() ? null : UserStatus.parse(status).code();
        OrgFilter filter = resolveOrgFilter(orgId, includeSubOrg);
        List<SysUser> users = userMapper.selectForExport(blankToNull(keyword), statusCode,
                filter.orgId(), filter.pathPrefix(), companyId);
        Map<Long, String> pathIndex = orgService.businessPathIndex();
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        // 列清单取自 ExportFieldPolicy 的 user.csv 目标（import-spec §9.1，九列；金额列天然不存在）
        CsvSupport.appendLine(builder, ExportFieldPolicy.columnsFor(ExportTarget.USER, false).toArray(new String[0]));
        for (SysUser user : users) {
            UserStatus userStatus = UserStatus.ofCode(user.getStatus());
            builder.append(CsvSupport.line(
                    user.getAccount(),
                    user.getEmployeeNo(),
                    user.getName(),
                    phoneVisibility.exportPlain(principal, user.getPhone()),
                    user.getEmail(),
                    user.getCompanyId() == null ? null : pathIndex.get(user.getCompanyId()),
                    user.getOrgId() == null ? null : pathIndex.get(user.getOrgId()),
                    userStatus == null ? user.getStatus() : userStatus.label(),
                    user.getRemark()));
            builder.append(CsvSupport.CRLF);
        }
        log.info("人员主数据导出：{} 行，operator={}", users.size(), principal.id());
        return builder.toString();
    }

    // ================================================================ 内部

    /** 组织过滤条件：{@code includeSubOrg=true} 用 path 前缀（子树），否则精确到节点。 */
    private record OrgFilter(Long orgId, String pathPrefix) {
    }

    private OrgFilter resolveOrgFilter(Long orgId, Boolean includeSubOrg) {
        if (orgId == null) {
            return new OrgFilter(null, null);
        }
        SysOrg org = orgService.requireVisibleOrg(orgId);
        if (Boolean.FALSE.equals(includeSubOrg)) {
            return new OrgFilter(org.getId(), null);
        }
        return new OrgFilter(null, OrgHierarchy.normalize(org.getPath()) + "%");
    }

    /** 归属公司：类型必须是「公司」，或「集团」（集团本部人员，import-spec E-USER-005）。 */
    private SysOrg requireCompany(Long companyId) {
        SysOrg org = orgService.requireVisibleOrg(companyId);
        OrgType type = IdentityEnums.OrgType.ofCode(org.getOrgType());
        if (type != OrgType.COMPANY && type != OrgType.GROUP) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "归属公司必须是公司节点（集团本部人员填集团）：当前为「"
                            + (type == null ? org.getOrgType() : type.label()) + "」（import-spec E-USER-005）");
        }
        return org;
    }

    /** 主归属部门/科室：类型 ∈ {部门, 科室}，且必须位于归属公司子树内（E-USER-006 / E-USER-011）。 */
    private SysOrg requireDept(Long orgId, SysOrg company) {
        SysOrg org = orgService.requireVisibleOrg(orgId);
        OrgType type = IdentityEnums.OrgType.ofCode(org.getOrgType());
        if (type != OrgType.DEPT && type != OrgType.SECTION) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "主归属组织必须是部门或科室：当前为「"
                            + (type == null ? org.getOrgType() : type.label()) + "」（import-spec E-USER-006）");
        }
        if (company != null && !OrgHierarchy.isDescendantOrSelf(org.getPath(), company.getPath())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "主归属组织必须位于归属公司子树内（import-spec E-USER-011）");
        }
        return org;
    }

    /** 由组织推导归属公司：最近的「公司」祖先，否则集团根。 */
    private SysOrg deriveCompany(SysOrg org) {
        SysOrg current = org;
        while (current != null) {
            OrgType type = IdentityEnums.OrgType.ofCode(current.getOrgType());
            if (type == OrgType.COMPANY) {
                return current;
            }
            current = current.getParentId() == null ? null : orgService.findOrg(current.getParentId());
        }
        for (Long ancestorId : OrgHierarchy.ancestorIds(org.getPath())) {
            SysOrg ancestor = orgService.findOrg(ancestorId);
            if (ancestor != null && OrgType.GROUP.code().equals(ancestor.getOrgType())) {
                return ancestor;
            }
        }
        return OrgType.GROUP.code().equals(org.getOrgType()) ? org : null;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectUserById(id);
        if (user == null) {
            throw BizException.notFound("人员");
        }
        return user;
    }

    /**
     * 手机号完整值可见性 —— <b>已下线</b>（阶段 1.6/1.7 收口）。
     *
     * <p>判定与脱敏的唯一实现在 {@code com.oa.authz.visibility.PhoneVisibilityService}
     * （先解密、再按「本人 or 系统管理员」决定是否脱敏）。此处曾是本类的第二份实现，
     * 保留任一份都会造成口径漂移，故删除；调用点已改用该服务。
     * 同理，口令字符集与随机源已迁到 {@link InitialPasswordGenerator}（见文件末尾的
     * {@code randomPassword}）。
     */
    private Long currentUserId() {
        return DataScopeContext.require().getUserId();
    }

    /**
     * 解析「可经本接口修改」的人员状态（施工要求第 8 条）。
     *
     * @return {@code null} 表示不变更；否则只可能是 {@link UserStatus#ACTIVE} / {@link UserStatus#DISABLED}
     * @throws BizException 400：取值非法，或传了 {@code 离职}（必须走 {@code /users/{id}/resign}）
     */
    private static UserStatus editableStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        UserStatus parsed = UserStatus.parse(status);
        if (parsed == UserStatus.RESIGNED) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "离职状态不可通过本接口设置，请调用 POST /api/v1/identity/users/{id}/resign（AC-12 的离职前待办校验）");
        }
        return parsed;
    }

    /** 批量待办数（列表页 {@code pendingTaskCount}）：一次批量取数，避免逐行 N+1。 */
    private Map<Long, Integer> pendingTaskCounts(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(users.size());
        for (SysUser user : users) {
            if (user != null && user.getId() != null) {
                ids.add(user.getId());
            }
        }
        return ids.isEmpty() ? Map.of() : inFlightChecker.pendingTaskCounts(ids);
    }

    private CurrentUser currentPrincipal() {
        DataScopeContext context = DataScopeContext.require();
        CurrentUser principal = context.getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /** 批量取组织（避免列表 N+1；只读展示用，不做授权判定）。 */
    private Map<Long, SysOrg> indexOrgs(List<SysUser> users) {
        List<Long> ids = new ArrayList<>();
        for (SysUser user : users) {
            if (user.getOrgId() != null && !ids.contains(user.getOrgId())) {
                ids.add(user.getOrgId());
            }
            if (user.getCompanyId() != null && !ids.contains(user.getCompanyId())) {
                ids.add(user.getCompanyId());
            }
        }
        Map<Long, SysOrg> index = new HashMap<>();
        if (!ids.isEmpty()) {
            for (SysOrg org : orgMapper.selectByIds(ids)) {
                index.put(org.getId(), org);
            }
        }
        return index;
    }

    private UserDtos.UserView view(SysUser user, Map<Long, SysOrg> orgIndex, CurrentUser principal,
                                   Map<Long, Integer> pendingTaskCounts) {
        SysOrg org = user.getOrgId() == null ? null : orgIndex.get(user.getOrgId());
        SysOrg company = user.getCompanyId() == null ? null : orgIndex.get(user.getCompanyId());
        UserStatus status = UserStatus.ofCode(user.getStatus());
        // 手机号：解密 + 脱敏一次完成（唯一实现，禁止在本类与别处手写 substring）
        PhoneVisibilityService.PhoneDisplay phone =
                phoneVisibility.display(principal, user.getId(), user.getPhone());
        Integer pending = pendingTaskCounts == null ? null : pendingTaskCounts.get(user.getId());
        return new UserDtos.UserView(
                user.getId(),
                user.getAccount(),
                user.getName(),
                user.getEmployeeNo(),
                phone.value(),
                phone.masked(),
                user.getEmail(),
                user.getOrgId(),
                org == null ? null : org.getName(),
                org == null ? null : org.getPath(),
                user.getCompanyId(),
                company == null ? null : company.getName(),
                user.getPosition(),
                user.getStatus(),
                status == null ? null : status.label(),
                user.getRemark(),
                text(user.getLastLoginAt()),
                // 施工要求第 11 条：待办数缺失（桩/端口未实现）时给 0，**不给 null**
                pending == null ? Integer.valueOf(0) : pending);
    }

    private static List<UserDtos.PendingTaskView> pendingViews(List<InFlightChecker.PendingTask> tasks) {
        List<UserDtos.PendingTaskView> views = new ArrayList<>();
        if (tasks != null) {
            for (InFlightChecker.PendingTask task : tasks) {
                views.add(new UserDtos.PendingTaskView(task.taskId(), task.bizNo(), task.nodeName(), task.createdAt()));
            }
        }
        return views;
    }

    private static List<PositionPolicy.Assignment> assignments(List<SysUserPosition> rows) {
        List<PositionPolicy.Assignment> result = new ArrayList<>(rows.size());
        for (SysUserPosition row : rows) {
            result.add(new PositionPolicy.Assignment(row.getId(), row.getUserId(), row.getOrgId(), row.primary(), row.getPosition()));
        }
        return result;
    }

    /** 随机初始口令：≥8 位且**必然**同时含字母与数字（REQ-NFR-005），并用口令策略自校验。 */
    private String randomPassword(int length) {
        return InitialPasswordGenerator.generate(passwordService, length);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(LocalDateTime time) {
        return time == null ? null : time.toString().replace('T', ' ');
    }
}
