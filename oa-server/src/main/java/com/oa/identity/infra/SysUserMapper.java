package com.oa.identity.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.identity.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 *
 * <p>数据域约定（doc/tech-design.md §5.3）：
 * <ul>
 *   <li>{@link #selectByAccount} 是**认证前**查询，天然自限（按唯一账号取一行），列入免拦截清单；</li>
 *   <li>{@link #selectDirectory} 是通讯录列表，必须在
 *       {@code resources/mapper/identity/SysUserMapper.xml} 中带 {@code @dataScope} 标记织入数据域过滤。</li>
 * </ul>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按账号取用户（登录用；含离职/停用，由调用方判定状态）。 */
    @Select("SELECT * FROM sys_user WHERE account = #{account} AND deleted_at IS NULL LIMIT 1")
    SysUser selectByAccount(@Param("account") String account);

    /** 通讯录分页查询（数据域过滤集中在 XML 中织入）。 */
    IPage<SysUser> selectDirectory(IPage<SysUser> page, @Param("keyword") String keyword);
}
