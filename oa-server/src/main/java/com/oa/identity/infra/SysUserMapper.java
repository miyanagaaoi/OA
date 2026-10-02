package com.oa.identity.infra;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.identity.domain.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 *
 * <p>数据域约定（doc/tech-design.md §5.3）：
 * <ul>
 *   <li>{@link #selectByAccount} 是**认证前**查询，天然自限（按唯一账号取一行），列入免拦截清单；</li>
 *   <li>其余查询（通讯录、人员列表、按 id 取人）必须在
 *       {@code resources/mapper/identity/SysUserMapper.xml} 中带 {@code @dataScope} 标记织入数据域过滤。</li>
 * </ul>
 *
 * <p><b>受控表 Mapper 一律不继承 {@code BaseMapper}</b>（与 {@link SysOrgMapper}、
 * {@link SysUserPositionMapper}、{@link SysOrgLeaderMapper} 同一约定）：MyBatis-Plus 注入的
 * {@code selectById/selectList/selectOne/selectPage} 等语句**不带 {@code @dataScope} 标记**，
 * 在已认证上下文中会被 {@code DataScopeInterceptor} fail-closed 拒绝（40303 DATA_SCOPE_MISSING）。
 * 因此：
 * <ul>
 *   <li>**所有读取**（含按 id 取人 {@link #selectUserById}）一律走本接口显式声明、XML 中带标记的方法；</li>
 *   <li>**所有写入**（{@link #insertUser}、{@link #touchLastLoginAt}、{@link #updatePasswordHash}、
 *       {@link #updateUserProfile}、{@link #updateUserStatus}）也在 XML 中显式声明
 *       —— 拦截器只约束 SELECT，写语句不需要标记，但同样不留在 BaseMapper 上，
 *       以免日后有人顺手用回无标记的注入方法。</li>
 * </ul>
 */
@Mapper
public interface SysUserMapper {

    /** 按账号取用户（登录用；含离职/停用，由调用方判定状态）。 */
    @Select("SELECT * FROM sys_user WHERE account = #{account} AND deleted_at IS NULL LIMIT 1")
    SysUser selectByAccount(@Param("account") String account);

    /** 通讯录分页查询（数据域过滤集中在 XML 中织入）。 */
    IPage<SysUser> selectDirectory(IPage<SysUser> page, @Param("keyword") String keyword);

    /**
     * 人员列表分页（{@code GET /api/v1/identity/users}）。
     *
     * @param orgPathPrefix 组织子树前缀（{@code /1/12/%} 形式，由服务层拼好）；为空表示不按组织过滤
     * @param orgId         精确组织（与 {@code orgPathPrefix} 二选一，同时给时取子集）
     */
    IPage<SysUser> selectUserPage(IPage<SysUser> page,
                                  @Param("keyword") String keyword,
                                  @Param("status") String status,
                                  @Param("orgId") Long orgId,
                                  @Param("orgPathPrefix") String orgPathPrefix,
                                  @Param("companyId") Long companyId);

    /** 按 id 取人员（数据域过滤生效：域外人员返回 {@code null}，调用方按 404 处理）。 */
    SysUser selectUserById(@Param("id") Long id);

    /** 通讯录数据（按组织分组由服务层完成；此处只做数据域过滤 + 关键字 + 归属过滤）。 */
    List<SysUser> selectDirectoryUsers(@Param("keyword") String keyword,
                                       @Param("orgId") Long orgId,
                                       @Param("orgPathPrefix") String orgPathPrefix,
                                       @Param("limit") int limit);

    /**
     * 组织**子树**下的在职人数（组织停用的影响面提示 {@code W-ORG-017} 的子树口径）。
     *
     * @param orgPathPrefix 形如 {@code /1/12/%}（由服务层拼好，含 {@code %}）
     */
    int countActiveByOrgPath(@Param("orgPathPrefix") String orgPathPrefix);

    /**
     * 人员主数据导出（{@code GET /api/v1/identity/users/export}）：与列表同口径、**不分页**。
     *
     * <p>权限（仅系统管理员）在服务层裁决；本语句只负责「数据域 + 筛选」，因此仍带
     * {@code @dataScope} 标记（受控表裸查询会被 fail-closed 拒绝）。
     */
    List<SysUser> selectForExport(@Param("keyword") String keyword,
                                  @Param("status") String status,
                                  @Param("orgId") Long orgId,
                                  @Param("orgPathPrefix") String orgPathPrefix,
                                  @Param("companyId") Long companyId);

    /** 工号唯一性校验（import-spec E-USER-015；{@code excludeId} 用于更新时排除自身）。 */
    int countByEmployeeNo(@Param("employeeNo") String employeeNo, @Param("excludeId") Long excludeId);

    /** 账号唯一性校验（import-spec E-USER-002）。 */
    int countByAccount(@Param("account") String account, @Param("excludeId") Long excludeId);

    /** 更新人员档案（不含口令与状态：状态走 {@link #updateUserStatus}，口令走认证模块）。 */
    int updateUserProfile(SysUser user);

    /** 更新人员状态（{@code active/disabled/resigned}）。 */
    int updateUserStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") Long updatedBy);

    /**
     * 新增人员（自增主键回填到 {@code user.id}，依赖 XML 的 {@code useGeneratedKeys}）。
     *
     * <p>替代 MyBatis-Plus 注入的 {@code insert}：受控表 Mapper 不继承 {@code BaseMapper}。
     */
    int insertUser(SysUser user);

    /** 登录成功时更新最近登录时间（{@code last_login_at = NOW()}；首登判定依据，见 {@link SysUser#mustChangePasswordOnFirstLogin()}）。 */
    int touchLastLoginAt(@Param("id") Long id);

    /** 写入新口令哈希（改密唯一入口；口令只经 {@code PasswordService} 生成哈希）。 */
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                           @Param("updatedBy") Long updatedBy);
}
