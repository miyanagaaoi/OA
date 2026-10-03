package com.oa.identity.infra;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.identity.domain.SysUser;
import com.oa.identity.infra.row.PhoneRow;
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

    /**
     * 工号唯一性校验（**数据域口径**：只查调用人可见范围，import-spec E-USER-015）。
     *
     * <p><b>本语句不是「唯一性判定的正确口径」</b>：工号唯一性是**全系统**约束，与数据域无关。
     * 需要判重时请用 {@link #countByEmployeeNoSystem}（系统口径）；本语句只保留给
     * 「按数据域逐行裁决」的调用点（批量导入的行级校验，越域行已先被
     * {@code ImportScopeGuard} 以 {@code E-*-020} 整批拒绝）。
     *
     * @param excludeId 更新时排除自身（{@code null} = 不排除）
     */
    int countByEmployeeNo(@Param("employeeNo") String employeeNo, @Param("excludeId") Long excludeId);

    /**
     * 账号唯一性校验（**数据域口径**：只查调用人可见范围，import-spec E-USER-002）。
     *
     * <p>同上：判重请用 {@link #countByAccountSystem}。本语句保留的价值是「域外账号查不到」
     * 这一**可断言的证据**（越权矩阵测试用它证明数据域读取限制没有被放宽）。
     */
    int countByAccount(@Param("account") String account, @Param("excludeId") Long excludeId);

    // ---------------------------------------------------------------- 唯一性判重（系统口径，阶段 1 收口）

    /**
     * 账号唯一性判重（<b>系统口径</b>：不受数据域裁剪，{@code sys_user.account} 为全库唯一）。
     *
     * <p><b>为什么必须是系统口径</b>（2026-10 收口修复的缺陷）：唯一性是**全局约束**
     * （库内 {@code uk_sys_user_account}），与「调用人能看到哪些人」无关。若用带数据域的
     * {@link #countByAccount} 判重，分公司管理员对**数据域外**的账号判重会得到 0 —— 重复账号
     * 只能由数据库唯一键在 INSERT 时兜住：错误来自数据库层（文案泛化、不带 import-spec 错误码），
     * 而**工号没有库唯一键**（见 {@code 01-schema.sql} 的 {@code sys_user}），越域重复更会被静默写入。
     *
     * <p><b>{@code @dataScope} 豁免理由</b>：本语句<b>故意不写</b>
     * {@code /* @dataScope(table=sys_user, alias=u) *}{@code /} 标记 —— 判重的语义就是
     * 「全库有没有这个账号」，织入数据域片段会直接改变判重结果（即缺陷本身）。因此它必须列入
     * {@code oa.scope.exempt-statement-ids}（{@code application.yml} 与 {@code OaProperties} 默认值
     * **两处**都要有，由 {@code DataScopeMapperGuardTest} 守住）。
     *
     * <p><b>为什么不构成数据域旁路</b>：本语句只返回 {@code COUNT}，不返回任何行数据、不参与任何读取口径；
     * 数据域读取限制（{@link #selectUserById} / {@link #selectUserPage} / {@link #selectDirectoryUsers} /
     * {@link #selectForExport}）一行未动。豁免的是一条「读一列计数」的窄语句，不是宽豁免
     * （参见 {@code DataScopeMapperGuardTest} 对 {@code selectOne/selectList/...} 一类宽豁免的禁令）。
     *
     * <p><b>口径细节</b>：**不过滤 {@code deleted_at}** —— 库唯一键 {@code uk_sys_user_account}
     * 覆盖全部行（含软删除行），判重与库约束保持同一口径，重复时才能稳定给出明确业务错误码
     * （409 / 40902），而不是偶尔落到数据库唯一键上。
     *
     * @param excludeId 更新时排除自身（{@code null} = 不排除）
     */
    int countByAccountSystem(@Param("account") String account, @Param("excludeId") Long excludeId);

    /**
     * 工号唯一性判重（<b>系统口径</b>，口径与 {@link #countByAccountSystem} 完全一致）。
     *
     * <p>import-spec E-USER-015 要求「工号全库唯一」，而 {@code sys_user.employee_no}
     * **没有**库唯一键：判重一旦被数据域裁剪，域外重复工号就会被静默写入（工号进签名/水印，
     * 属于不可接受的数据污染）。因此人工新增/修改人员（{@code UserService.create/update}）
     * 必须用本语句判重。
     *
     * <p>{@code @dataScope} 豁免理由同 {@link #countByAccountSystem}（只读计数、不返回行、
     * 已列入豁免清单）。
     */
    int countByEmployeeNoSystem(@Param("employeeNo") String employeeNo, @Param("excludeId") Long excludeId);

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

    // ---------------------------------------------------------------- 敏感字段加密（阶段 1.7）

    /**
     * 全量手机号行（仅 {@code id} + {@code phone}）—— 一次性明文迁移与密钥轮换遍历用。
     *
     * <p><b>调用约定</b>：本语句带 {@code @dataScope} 标记，调用方必须显式使用
     * {@code DataScopeContext.system()}（后台任务口径）执行；否则会按调用人的数据域裁剪，
     * 迁移就会「只迁移自己看得到的行」——静默漏迁移，属不可接受的部分成功。
     */
    List<PhoneRow> selectPhoneRows();

    /**
     * 回写单个手机号（迁移/轮换专用，**不接受外部入参**）。
     *
     * <p>与 {@link #updateUserProfile} 分开的理由：迁移是系统级后台操作，需要绕过
     * 「按人改档案」的字段覆盖语义，只写 {@code phone} 一列。
     */
    int updatePhoneCipher(@Param("id") Long id, @Param("phone") String phone, @Param("updatedBy") Long updatedBy);

    /**
     * 批量导入（{@code user.csv}）的 upsert 更新：按业务键 {@code account} 命中后
     * **整体覆盖** {@code employee_no / name / phone / email / company_id / org_id / status / remark}
     * （import-spec §6.1；{@code password_hash} **不覆盖**，防重置在职人员口令）。
     *
     * <p>与 {@link #updateUserProfile} 的区别：本语句显式写 {@code org_id}/{@code company_id}，
     * 允许把「无部门」的人写成 {@code NULL}（{@code updateUserProfile} 的 {@code <if>}
     * 条件更新无法表达「清空归属」，会把空值悄悄留在原部门 —— 那是静默错误）。
     */
    int updateUserImport(SysUser user);
}
