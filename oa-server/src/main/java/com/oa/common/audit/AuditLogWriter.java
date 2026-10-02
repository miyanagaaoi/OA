package com.oa.common.audit;

import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审计日志写入器 —— <b>只追加（append-only）</b>。
 *
 * <p>约束（doc/tech-design.md §5.5、AC-20）：
 * <ul>
 *   <li>本类只调用 {@link AuditLogMapper#insertAppendOnly}，不存在任何 UPDATE/DELETE 路径；</li>
 *   <li>数据库层再由触发器拒绝 UPDATE/DELETE（{@code db/trigger/immutable-triggers.sql}）；</li>
 *   <li>写入失败**不得**影响业务流程，但必须落到 ERROR 日志（审计缺口需可发现）。</li>
 * </ul>
 */
@Component
public class AuditLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditLogWriter.class);

    private final AuditLogMapper auditLogMapper;

    public AuditLogWriter(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /** 写入一条审计记录（追加），失败只记日志。 */
    public void append(AuditRecord record) {
        if (record == null || record.action() == null) {
            return;
        }
        SysLog row = new SysLog();
        row.setUserId(record.userId());
        row.setUserName(record.userName());
        row.setAction(record.action());
        row.setTargetType(record.targetType() == null ? "unknown" : record.targetType());
        row.setTargetId(record.targetId());
        row.setBeforeJson(record.beforeJson());
        row.setAfterJson(record.afterJson());
        row.setIp(record.ip());
        row.setUserAgent(record.userAgent());
        row.setCreatedAt(LocalDateTime.now());
        try {
            auditLogMapper.insertAppendOnly(row);
        } catch (RuntimeException ex) {
            log.error("审计日志写入失败（只追加，不回滚业务）：action={} targetType={} targetId={}",
                    row.getAction(), row.getTargetType(), row.getTargetId(), ex);
        }
    }

    /** 便捷方法：使用当前登录人上下文补全用户信息。 */
    public void appendAsCurrentUser(String action, String targetType, Long targetId, String beforeJson, String afterJson,
                                    String ip, String userAgent) {
        DataScopeContext context = DataScopeContext.current();
        CurrentUser principal = context == null ? null : context.getPrincipal();
        append(new AuditRecord(
                principal == null ? null : principal.id(),
                principal == null ? null : principal.name(),
                action, targetType, targetId, beforeJson, afterJson, ip, userAgent));
    }

    /**
     * 审计记录值对象（不可变）。
     *
     * @param userId     操作人 id（快照）
     * @param userName   操作人姓名（快照）
     * @param action     动作码
     * @param targetType 目标类型
     * @param targetId   目标 id
     * @param beforeJson 变更前 JSON（权限变更必填）
     * @param afterJson  变更后 JSON
     * @param ip         客户端 IP
     * @param userAgent  User-Agent
     */
    public record AuditRecord(
            Long userId,
            String userName,
            String action,
            String targetType,
            Long targetId,
            String beforeJson,
            String afterJson,
            String ip,
            String userAgent
    ) {
    }
}
