package com.oa.identity.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.identity.domain.SysUserSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话注册表 Mapper（{@code sys_user_session}）。
 *
 * <p>软踢出：只写 {@code revoked_at} / {@code revoked_reason}，**不物理删除**
 * （REQ-USER-003；保留期与审计要求）。
 */
@Mapper
public interface SysUserSessionMapper extends BaseMapper<SysUserSession> {
}
