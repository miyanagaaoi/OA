package com.oa.identity.infra;

import com.oa.identity.domain.SysLoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 登录日志 Mapper —— 只提供 INSERT（登录日志同样只追加，保留 1 年）。
 */
@Mapper
public interface SysLoginLogMapper {

    @Insert("INSERT INTO sys_login_log (user_id, account, result, fail_reason, ip, user_agent, device_fingerprint, created_at)"
            + " VALUES (#{userId}, #{account}, #{result}, #{failReason}, #{ip}, #{userAgent}, #{deviceFingerprint}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAppendOnly(SysLoginLog row);
}
