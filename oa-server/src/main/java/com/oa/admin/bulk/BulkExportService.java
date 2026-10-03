package com.oa.admin.bulk;

import com.oa.authz.infra.SysUserRoleMapper;
import com.oa.authz.infra.row.UserRoleExportRow;
import com.oa.authz.visibility.ExportFieldPolicy;
import com.oa.authz.visibility.ExportTarget;
import com.oa.authz.visibility.VisibilityRoles;
import com.oa.common.audit.AuditLogMapper;
import com.oa.common.audit.SysLog;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.CsvSupport;
import com.oa.identity.app.OrgService;
import com.oa.identity.domain.IdentityEnums;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.SysOrgLeaderMapper;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.SysUserPositionMapper;
import com.oa.identity.infra.row.LeaderRow;
import com.oa.identity.infra.row.PositionRow;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 主数据导出补齐（阶段 1.8，import-spec §9）—— 负责人 / 岗位任职 / 角色分配三张模板 + 审计日志导出。
 *
 * <p>共同口径：
 * <ol>
 *   <li><b>列清单唯一来源</b>：{@link ExportFieldPolicy#columnsFor}（金额列天然不在主数据列内；审计日志额外做金额键剔除）；</li>
 *   <li><b>编码</b>：UTF-8 带 BOM + CRLF（§4.1，否则校验器报 {@code E-ENC-001}）；</li>
 *   <li><b>往返</b>：{@code org_path} 一律输出**名称路径**（业务键），保证「导出 → 再导入」0 error（§9.1）；</li>
 *   <li><b>权限</b>：主数据导出仅系统管理员（§9.2 T-11）；审计日志导出同样仅系统管理员。</li>
 * </ol>
 */
@Service
public class BulkExportService {

    private static final Logger log = LoggerFactory.getLogger(BulkExportService.class);

    /** 审计日志导出的最大行数（防止一次导出拖垮库）。 */
    private static final int AUDIT_LIMIT = 5000;

    private final SysOrgLeaderMapper leaderMapper;

    private final SysUserPositionMapper positionMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final AuditLogMapper auditLogMapper;

    private final OrgService orgService;

    private final SysUserMapper userMapper;

    private final OaProperties properties;

    public BulkExportService(SysOrgLeaderMapper leaderMapper, SysUserPositionMapper positionMapper,
                             SysUserRoleMapper userRoleMapper, AuditLogMapper auditLogMapper,
                             OrgService orgService, SysUserMapper userMapper, OaProperties properties) {
        this.leaderMapper = leaderMapper;
        this.positionMapper = positionMapper;
        this.userRoleMapper = userRoleMapper;
        this.auditLogMapper = auditLogMapper;
        this.orgService = orgService;
        this.userMapper = userMapper;
        this.properties = properties;
    }

    /** 负责人主数据导出（{@code org_leader.csv}）。 */
    public String exportOrgLeaders() {
        CurrentUser principal = requireAdmin(ExportTarget.ORG_LEADER);
        Map<Long, String> pathIndex = orgService.businessPathIndex();
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        CsvSupport.appendLine(builder, ExportFieldPolicy
                .columnsFor(ExportTarget.ORG_LEADER, amountExportable(principal, ExportTarget.ORG_LEADER))
                .toArray(new String[0]));
        for (LeaderRow row : leaderMapper.selectAllForExport()) {
            IdentityEnums.LeaderType type = IdentityEnums.LeaderType.ofCode(row.getLeaderType());
            IdentityEnums.Category category = IdentityEnums.Category.ofCode(row.getCategory());
            builder.append(CsvSupport.line(
                    row.getOrgId() == null ? null : pathIndex.get(row.getOrgId()),
                    row.getAccount(),
                    type == null ? row.getLeaderType() : type.label(),
                    row.getSortNo() == null ? "0" : String.valueOf(row.getSortNo()),
                    category == null ? null : category.label(),
                    row.getRemark()));
            builder.append(CsvSupport.CRLF);
        }
        log.info("负责人主数据导出，operator={}", principal.id());
        return builder.toString();
    }

    /** 岗位任职主数据导出（{@code user_position.csv}）。 */
    public String exportUserPositions() {
        CurrentUser principal = requireAdmin(ExportTarget.USER_POSITION);
        Map<Long, String> pathIndex = orgService.businessPathIndex();
        Map<Long, String> accountIndex = accountIndex();
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        CsvSupport.appendLine(builder, ExportFieldPolicy
                .columnsFor(ExportTarget.USER_POSITION, amountExportable(principal, ExportTarget.USER_POSITION))
                .toArray(new String[0]));
        for (PositionRow row : positionMapper.selectAllForExport()) {
            builder.append(CsvSupport.line(
                    accountIndex.get(row.getUserId()),
                    row.getOrgId() == null ? null : pathIndex.get(row.getOrgId()),
                    row.getPosition(),
                    IdentityEnums.PrimaryFlag.of(row.primary()).label(),
                    row.getRemark()));
            builder.append(CsvSupport.CRLF);
        }
        log.info("岗位任职主数据导出，operator={}", principal.id());
        return builder.toString();
    }

    /** 角色分配主数据导出（{@code user_role.csv}）。 */
    public String exportUserRoles() {
        CurrentUser principal = requireAdmin(ExportTarget.USER_ROLE);
        Map<Long, String> pathIndex = orgService.businessPathIndex();
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        CsvSupport.appendLine(builder, ExportFieldPolicy
                .columnsFor(ExportTarget.USER_ROLE, amountExportable(principal, ExportTarget.USER_ROLE))
                .toArray(new String[0]));
        for (UserRoleExportRow row : userRoleMapper.selectAllForExport()) {
            builder.append(CsvSupport.line(
                    row.getAccount(),
                    row.getRoleCode(),
                    row.getScopeOrgId() == null ? null : pathIndex.get(row.getScopeOrgId()),
                    row.getRemark()));
            builder.append(CsvSupport.CRLF);
        }
        log.info("角色分配主数据导出，operator={}", principal.id());
        return builder.toString();
    }

    /**
     * 审计日志导出（{@code GET /api/v1/admin/audit-logs/export}）。
     *
     * <p><b>金额列剔除</b>：日志本身没有金额列，但 {@code before_json} / {@code after_json}
     * 会带金额键 → 逐行按 {@link ExportFieldPolicy#redactAmountKeys} 做**任意层级**的金额键剔除
     * （解析失败整列替换为占位值，fail-closed）。
     */
    public String exportAuditLogs() {
        CurrentUser principal = requireAdmin(ExportTarget.AUDIT_LOG);
        boolean amountExported = amountExportable(principal, ExportTarget.AUDIT_LOG);
        StringBuilder builder = new StringBuilder(CsvSupport.UTF8_BOM);
        CsvSupport.appendLine(builder, ExportFieldPolicy.columnsFor(ExportTarget.AUDIT_LOG, amountExported)
                .toArray(new String[0]));
        List<SysLog> rows = auditLogMapper.selectForExport(AUDIT_LIMIT);
        for (SysLog row : rows) {
            builder.append(CsvSupport.line(
                    row.getId() == null ? null : String.valueOf(row.getId()),
                    row.getCreatedAt() == null ? null : row.getCreatedAt().toString().replace('T', ' '),
                    row.getUserName(),
                    row.getAction(),
                    row.getTargetType(),
                    row.getTargetId() == null ? null : String.valueOf(row.getTargetId()),
                    amountExported ? row.getBeforeJson() : ExportFieldPolicy.redactAmountKeys(row.getBeforeJson()),
                    amountExported ? row.getAfterJson() : ExportFieldPolicy.redactAmountKeys(row.getAfterJson()),
                    row.getIp()));
            builder.append(CsvSupport.CRLF);
        }
        log.info("审计日志导出：{} 行，operator={}，金额列导出={}", rows.size(), principal.id(), amountExported);
        return builder.toString();
    }

    /**
     * 账号索引（{@code user_id → account}）：导出岗位表时把代理键还原为业务键。
     *
     * <p>复用 {@code SysUserMapper.selectForExport}（带 {@code @dataScope} 标记的既有语句）：
     * 系统管理员为全量口径；本方法只取 {@code id}/{@code account}，不触碰手机号等敏感列。
     */
    private Map<Long, String> accountIndex() {
        Map<Long, String> index = new java.util.HashMap<>();
        for (SysUser user : userMapper.selectForExport(null, null, null, null, null)) {
            if (user.getId() != null) {
                index.put(user.getId(), user.getAccount());
            }
        }
        return index;
    }

    private boolean amountExportable(CurrentUser principal, ExportTarget target) {
        return ExportFieldPolicy.amountExportable(principal, target,
                properties.getAuthz().getExport().isAmountEnabled());
    }

    private static CurrentUser requireAdmin(ExportTarget target) {
        DataScopeContext context = DataScopeContext.current();
        CurrentUser principal = context == null ? null : context.getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!principal.hasRole(VisibilityRoles.ADMIN)) {
            throw new BizException(ErrorCode.EXPORT_DENIED,
                    "主数据/审计日志导出仅系统管理员可用（import-spec §9.2 T-11）：" + target.code());
        }
        return principal;
    }
}
