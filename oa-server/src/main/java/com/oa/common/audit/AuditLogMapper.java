package com.oa.common.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 审计日志 Mapper —— <b>只提供 INSERT</b>。
 *
 * <p>刻意**不继承** {@code BaseMapper}：编译期就不存在 update/delete 方法，
 * 与数据库触发器（{@code trg_sys_log_no_update} / {@code trg_sys_log_no_delete}）形成双保险（AC-20、§5.5）。
 */
@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO sys_log (user_id, user_name, action, target_type, target_id, before_json, after_json, ip, user_agent, created_at)"
            + " VALUES (#{userId}, #{userName}, #{action}, #{targetType}, #{targetId}, #{beforeJson}, #{afterJson}, #{ip}, #{userAgent}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAppendOnly(SysLog row);
}
