package com.oa.common.audit;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审计日志 Mapper —— <b>只提供 INSERT 与（只读的）导出查询</b>。
 *
 * <p>刻意**不继承** {@code BaseMapper}：编译期就不存在 update/delete 方法，
 * 与数据库触发器（{@code trg_sys_log_no_update} / {@code trg_sys_log_no_delete}）形成双保险（AC-20、§5.5）。
 *
 * <p>{@code sys_log} 未登记为受控表（它是审计留痕，不含业务数据域字段），
 * 因此其 SELECT 不需要 {@code @dataScope} 标记；导出权限在服务层按
 * {@code oa.authz.visibility.ExportFieldPolicy} 裁决（仅系统管理员）。
 */
@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO sys_log (user_id, user_name, action, target_type, target_id, before_json, after_json, ip, user_agent, created_at)"
            + " VALUES (#{userId}, #{userName}, #{action}, #{targetType}, #{targetId}, #{beforeJson}, #{afterJson}, #{ip}, #{userAgent}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAppendOnly(SysLog row);

    /**
     * 审计日志导出（{@code GET /api/v1/admin/audit-logs/export}）。
     *
     * <p>列清单由服务层按 {@code ExportFieldPolicy.columnsFor(AUDIT_LOG, ...)} 决定；
     * 本语句按时间倒序取最近 {@code limit} 条，避免一次导出把库拖垮。
     */
    @Select("SELECT id, user_id AS userId, user_name AS userName, action, target_type AS targetType,"
            + " target_id AS targetId, before_json AS beforeJson, after_json AS afterJson,"
            + " ip, user_agent AS userAgent, created_at AS createdAt"
            + " FROM sys_log ORDER BY id DESC LIMIT #{limit}")
    List<SysLog> selectForExport(@Param("limit") int limit);
}
