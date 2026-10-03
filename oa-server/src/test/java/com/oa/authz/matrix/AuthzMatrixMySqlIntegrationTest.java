package com.oa.authz.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeInterceptor;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.identity.domain.SysUser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 阶段 1 DoD：<b>逐接口越权矩阵测试（角色 × 数据域 × 入口）</b>——SQL 层（真实 MySQL）。
 *
 * <p>为什么覆盖 SQL 层而不是只测控制器：越权的最终判定就发生在数据域织入的 SQL 上。
 * 本类用**生产同一份** Mapper XML（{@code mapper/identity/SysUserMapper.xml}）与
 * **同一个** {@link DataScopeInterceptor}，对 5 个内置角色逐一执行 8 类入口语句：
 * <table>
 *   <tr><th>入口</th><th>语句</th></tr>
 *   <tr><td>组织树 / 人员列表</td><td>{@code selectUserPage}</td></tr>
 *   <tr><td>通讯录</td><td>{@code selectDirectoryUsers}</td></tr>
 *   <tr><td>导出</td><td>{@code selectForExport}</td></tr>
 *   <tr><td>换 id 参数（详情）</td><td>{@code selectUserById}</td></tr>
 *   <tr><td>组织子树人数</td><td>{@code countActiveByOrgPath}</td></tr>
 *   <tr><td>唯一性判重（数据域口径 vs <b>系统口径</b>）</td><td>{@code countByAccount} / {@code countByEmployeeNo}（域外=0，读取限制未放宽）
 *       ↔ {@code countByAccountSystem} / {@code countByEmployeeNoSystem}（域外=1，判重不看数据域）</td></tr>
 *   <tr><td>单据列表（实例类）</td><td>{@code com.oa.matrix.MatrixMapper.countInstances}</td></tr>
 *   <tr><td>直连裸查询（无标记）</td><td>{@code com.oa.matrix.MatrixMapper.countUsersBare} → <b>40303</b></td></tr>
 * </table>
 *
 * <p><b>期望结果三态</b>：可见（数据在域内）／**数据被过滤为空（而不是报错）**／403。
 * 关键断言是第二条与最后一条：
 * <ul>
 *   <li>域外数据一律「过滤为空」而非抛错（列表页不该 500）；</li>
 *   <li>换个 id 参数（他人 user_id）也拿不到数据（返回 {@code null}）；</li>
 *   <li>受控表漏标记（有人新增裸查询 / 直连接口）→ 40303 fail-closed。</li>
 * </ul>
 *
 * <p><b>开启方式</b>（夹具默认读**已入库**的 {@code oa-deploy/fixtures/40-authz-matrix.sql}）：
 * <pre>
 * mvn -B test -Dtest=AuthzMatrixMySqlIntegrationTest `
 *     "-Doa.it.db.url=jdbc:mysql://127.0.0.1:3306/oa?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=Asia/Shanghai" `
 *     -Doa.it.db.user=oa -Doa.it.db.password=*** -Doa.it.fixture=..\oa-deploy\fixtures\40-authz-matrix.sql
 * </pre>
 * 未提供连接参数时整类跳过（CI 无库时不会红）。<b>但「已提供连接参数却找不到夹具」是失败，不是跳过</b>
 * （见 {@link AuthzMatrixFixture}）：夹具缺失时静默跳过 = 测试全绿而覆盖为零。
 */
class AuthzMatrixMySqlIntegrationTest {

    private static final String PAGE = "com.oa.identity.infra.SysUserMapper.selectUserPage";
    private static final String DIRECTORY = "com.oa.identity.infra.SysUserMapper.selectDirectoryUsers";
    private static final String EXPORT = "com.oa.identity.infra.SysUserMapper.selectForExport";
    private static final String BY_ID = "com.oa.identity.infra.SysUserMapper.selectUserById";
    private static final String ACTIVE_BY_PATH = "com.oa.identity.infra.SysUserMapper.countActiveByOrgPath";
    private static final String BY_ACCOUNT = "com.oa.identity.infra.SysUserMapper.countByAccount";
    private static final String BY_EMPLOYEE_NO = "com.oa.identity.infra.SysUserMapper.countByEmployeeNo";
    /**
     * 账号/工号**唯一性判重**（系统口径，阶段 1 收口）。
     *
     * <p>两条语句故意不带 {@code @dataScope} 标记（判重 = 全库口径），列入
     * {@code oa.scope.exempt-statement-ids}；本测试在**已认证**上下文里执行它们，
     * 既验证「重复能被判出来」，也验证「豁免在运行期真的生效」（否则 40303）。
     */
    private static final String BY_ACCOUNT_SYSTEM = "com.oa.identity.infra.SysUserMapper.countByAccountSystem";
    private static final String BY_EMPLOYEE_NO_SYSTEM =
            "com.oa.identity.infra.SysUserMapper.countByEmployeeNoSystem";
    private static final String INSTANCES = "com.oa.matrix.MatrixMapper.countInstances";
    private static final String INSTANCE_IDS = "com.oa.matrix.MatrixMapper.selectInstanceIds";
    private static final String USERS = "com.oa.matrix.MatrixMapper.countUsers";
    private static final String USERS_BARE = "com.oa.matrix.MatrixMapper.countUsersBare";

    /** 夹具账号（`oa-deploy/fixtures/40-authz-matrix.sql`）。 */
    private static final long CA = 201L;
    private static final long DL = 202L;
    private static final long GL = 203L;
    private static final long EM_A = 204L;
    private static final long EM_B = 205L;

    private static SqlSessionFactory factory;

    private SqlSession session;

    @BeforeAll
    static void initFactory() throws Exception {
        String url = config("oa.it.db.url", "OA_IT_DB_URL");
        assumeTrue(!url.isBlank(), "未提供 -Doa.it.db.url / OA_IT_DB_URL，跳过越权矩阵测试");
        String user = config("oa.it.db.user", "OA_IT_DB_USER");
        String password = config("oa.it.db.password", "OA_IT_DB_PASSWORD");
        assumeTrue(canConnect(url, user, password), "MySQL 不可连接，跳过越权矩阵测试：" + url);
        applyFixture(url, user, password);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("matrix", new JdbcTransactionFactory(),
                new PooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
        MybatisPlusInterceptor mybatisPlus = new MybatisPlusInterceptor();
        mybatisPlus.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 注册顺序即「内层 → 外层」：数据域拦截器最后注册 = 最外层（与生产一致）
        configuration.addInterceptor(mybatisPlus);
        configuration.addInterceptor(new DataScopeInterceptor(new DataScopeTableRegistry(new OaProperties())));
        try (InputStream xml = Resources.getResourceAsStream("mapper/identity/SysUserMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/identity/SysUserMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream xml = Resources.getResourceAsStream("mapper/matrix/MatrixMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/matrix/MatrixMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void closeFactory() {
        factory = null;
    }

    @AfterEach
    void cleanup() {
        if (session != null) {
            session.close();
            session = null;
        }
        DataScopeContext.clear();
    }

    // ================================================================ 矩阵

    @Test
    @DisplayName("admin（group_all）：全量可见，无过滤 —— 组织树/人员/通讯录/导出/详情/单据 全部放行")
    void adminSeesEverything() {
        authenticate(1L, "admin", EnumSet.of(DataScopeType.GROUP_ALL), null, 1L, null);
        open();

        List<Long> visible = visibleUserIds();
        assertThat(visible).contains(1L, CA, DL, GL, EM_A, EM_B);
        assertThat(countUsers()).isGreaterThanOrEqualTo(6);
        assertThat(byId(EM_B)).isNotNull();
        assertThat(instanceCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("company_admin（company=公司A）：只看本公司 —— org树/人员/通讯录/导出一致，跨公司与域外详情为空")
    void companyAdminIsLimitedToOwnCompany() {
        authenticate(CA, "company_admin", EnumSet.of(DataScopeType.COMPANY), "/1/12/", 12L, null);
        open();

        List<Long> visible = visibleUserIds();
        assertThat(visible).contains(CA, DL, EM_A);
        assertThat(visible).doesNotContain(EM_B, GL, 1L);

        // 通讯录与导出口径必须一致（三处一致过滤：列表/搜索/详情）
        assertThat(directoryUserIds()).isEqualTo(visible);
        assertThat(exportUserIds()).isEqualTo(visible);
        // 更换 id 参数（他人 user_id）→ 数据被过滤为空而不是报错
        assertThat(byId(EM_B)).isNull();
        assertThat(byId(GL)).isNull();
        // 人数口径按「相对断言」编写：本机库中可能还有其它本公司数据（夹具之外的真实数据），
        // 因此只断言「域内 > 0」与「域外 = 0」—— 后者才是越权防线的实质。
        assertThat(countActiveByOrgPath("/1/13/")).isZero();
        assertThat(countActiveByOrgPath("/1/12/")).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("dept_leader（dept=公司A/部门1）：只看本部门子树 + 本人")
    void deptLeaderIsLimitedToOwnDept() {
        authenticate(DL, "dept_leader", EnumSet.of(DataScopeType.DEPT), "/1/12/135/", 12L, null);
        open();

        List<Long> visible = visibleUserIds();
        assertThat(visible).contains(DL, EM_A);
        assertThat(visible).doesNotContain(CA, EM_B, GL, 1L);
        assertThat(byId(EM_B)).isNull();
        // 部门子树人数：域内 > 0、兄弟部门 = 0（相对断言，见上一条说明）
        assertThat(countActiveByOrgPath("/1/12/135/")).isGreaterThanOrEqualTo(2);
        assertThat(countActiveByOrgPath("/1/12/136/")).isZero();
    }

    @Test
    @DisplayName("group_leader（group_category）：按归口部门子树收敛；归口路径缺失时退化为本公司（不放宽为全量）")
    void groupLeaderIsLimitedToFinanceDeptOrOwnCompany() {
        authenticate(GL, "group_leader", EnumSet.of(DataScopeType.GROUP_CATEGORY), "/1/", 1L, null);
        open();

        // 归口部门路径未配置 → buildUserScope 退化为「本公司」（company_id=1）→ 只能看到集团本部的人
        List<Long> visible = visibleUserIds();
        assertThat(visible).contains(GL, 1L);
        assertThat(visible).doesNotContain(CA, DL, EM_A, EM_B);

        authenticate(GL, "group_leader", EnumSet.of(DataScopeType.GROUP_CATEGORY), "/1/", 1L, "/1/12/135/");
        open();
        List<Long> financeScoped = visibleUserIds();
        assertThat(financeScoped).contains(GL, DL, EM_A);
        assertThat(financeScoped).doesNotContain(CA, EM_B);
    }

    @Test
    @DisplayName("employee（self）：只看自己 —— 域外一律过滤为空（不是报错），且他人 id 也拿不到")
    void employeeSeesOnlySelf() {
        authenticate(EM_A, "employee", EnumSet.of(DataScopeType.SELF), "/1/12/135/", 12L, null);
        open();

        assertThat(visibleUserIds()).containsExactly(EM_A);
        assertThat(directoryUserIds()).containsExactly(EM_A);
        assertThat(exportUserIds()).containsExactly(EM_A);
        assertThat(byId(EM_A)).isNotNull();
        assertThat(byId(EM_B)).isNull();
        assertThat(byId(CA)).isNull();
        // ① 数据域**读取**限制未被放宽（回归防线）：带数据域的判重语句查域外账号/工号仍得到 0
        Integer outsideAccount = session.selectOne(BY_ACCOUNT, param("account", "mtx_em02", "excludeId", null));
        assertThat(outsideAccount).isZero();
        Integer outsideEmployeeNo = session.selectOne(BY_EMPLOYEE_NO,
                param("employeeNo", "MTX0005", "excludeId", null));
        assertThat(outsideEmployeeNo).isZero();

        // ② 判重口径 = **系统口径**（阶段 1 收口修复）：唯一性是全局约束，与数据域无关 ——
        //    域外账号/工号同样能判出重复（返回 > 0），于是 UserService 返回明确业务错误码
        //    40902 DUPLICATE，而不是：
        //      · 账号：落到库唯一键 uk_sys_user_account 上，只得到一句数据库层的泛化文案；
        //      · 工号：sys_user.employee_no **没有**库唯一键，域外重复会被**静默写入**。
        //    两条语句不带 @dataScope 标记，这里是在**已认证**上下文（employee/SELF）里执行：
        //    能跑通本身就证明 oa.scope.exempt-statement-ids 的窄豁免在运行期生效（否则 40303）。
        Integer outsideAccountSystem = session.selectOne(BY_ACCOUNT_SYSTEM,
                param("account", "mtx_em02", "excludeId", null));
        assertThat(outsideAccountSystem).as("系统口径判重必须看得见域外账号（否则只能靠库唯一键兜住）")
                .isEqualTo(1);
        Integer outsideEmployeeNoSystem = session.selectOne(BY_EMPLOYEE_NO_SYSTEM,
                param("employeeNo", "MTX0005", "excludeId", null));
        assertThat(outsideEmployeeNoSystem).as("系统口径判重必须看得见域外工号（工号无库唯一键，漏判=静默重复）")
                .isEqualTo(1);
        // excludeId 语义：排除自身后不再算重复（更新本人档案不应误报）
        Integer employeeNoSelf = session.selectOne(BY_EMPLOYEE_NO_SYSTEM,
                param("employeeNo", "MTX0005", "excludeId", EM_B));
        assertThat(employeeNoSelf).isZero();
        Integer accountSelf = session.selectOne(BY_ACCOUNT_SYSTEM,
                param("account", "mtx_em02", "excludeId", EM_B));
        assertThat(accountSelf).isZero();
    }

    @Test
    @DisplayName("无任何可用口径（空 scopes）：只剩「自读不变式」——看不到任何他人数据，也不报错")
    void emptyScopesDenyInsteadOfLeaking() {
        authenticate(999L, "employee", EnumSet.noneOf(DataScopeType.class), null, null, null);
        open();

        assertThat(visibleUserIds()).isEmpty();
        assertThat(countUsers()).isZero();
    }

    @Test
    @DisplayName("单据列表（实例类 flow_instance）：每个角色的织入片段都能在真实驱动上执行（占位符全部绑定）")
    void instanceScopeExecutesForEveryRole() {
        DataScopeType[][] scopeMatrix = {
                {DataScopeType.SELF},
                {DataScopeType.DEPT},
                {DataScopeType.COMPANY},
                {DataScopeType.GROUP_CATEGORY},
                {DataScopeType.GROUP_ALL},
                {DataScopeType.SELF, DataScopeType.DEPT, DataScopeType.COMPANY}
        };
        for (DataScopeType[] scopes : scopeMatrix) {
            EnumSet<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
            Collections.addAll(set, scopes);
            authenticate(EM_A, "employee", set, "/1/12/135/", 12L, "/1/12/135/");
            open();
            // 真实驱动下执行 count + list：任何未绑定占位符都会抛
            // "No value specified for parameter N"（数据域织入的回归缺陷形态）
            assertThat(instanceCount()).isGreaterThanOrEqualTo(0);
            assertThat(session.selectList(INSTANCE_IDS, new HashMap<String, Object>())).isNotNull();
            session.close();
            session = null;
            DataScopeContext.clear();
        }
    }

    @Test
    @DisplayName("直连接口/裸查询防线：受控表 SELECT 漏标记（sys_user / flow_instance）→ 40303 fail-closed")
    void unmarkedControlledQueriesAreRejected() {
        authenticate(1L, "admin", EnumSet.of(DataScopeType.GROUP_ALL), "/1/", 1L, null);
        open();

        // MyBatis 会把插件抛出的业务异常包成 PersistenceException，因此断言根因
        assertThatThrownBy(() -> session.selectOne(USERS_BARE, new HashMap<String, Object>()))
                .hasRootCauseInstanceOf(BizException.class)
                .rootCause()
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DATA_SCOPE_MISSING));

        // 带标记的同类查询正常放行（对照组）
        assertThat(countUsers()).isGreaterThan(0);
    }

    @Test
    @DisplayName("组织/岗位/负责人（kind=NONE）：标记仍必需（禁裸查询），但行过滤由服务层可见性收敛")
    void orgRelatedTablesRequireMarkerButDoNotRowFilter() {
        DataScopeTableRegistry registry = new DataScopeTableRegistry(new OaProperties());
        assertThat(registry.isScoped("sys_org")).isTrue();
        assertThat(registry.isScoped("sys_org_leader")).isTrue();
        assertThat(registry.isScoped("sys_user_position")).isTrue();
        assertThat(registry.isScoped("flow_instance")).isTrue();
        assertThat(registry.isScoped("form_data")).isTrue();
        assertThat(registry.isScoped("flow_task")).isTrue();
        assertThat(registry.isScoped("flow_routing")).isTrue();
    }

    // ================================================================ 工具

    /** 当前数据域下「人员列表」可见的 user id（按 id 升序）。 */
    private List<Long> visibleUserIds() {
        Page<SysUser> page = new Page<>(1, 100);
        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("param1", page);
        parameter.put("keyword", null);
        parameter.put("status", null);
        parameter.put("companyId", null);
        parameter.put("orgId", null);
        parameter.put("orgPathPrefix", null);
        List<SysUser> users = session.selectList(PAGE, parameter);
        List<Long> ids = new ArrayList<>();
        for (SysUser user : users) {
            ids.add(user.getId());
        }
        Collections.sort(ids);
        return ids;
    }

    private List<Long> directoryUserIds() {
        List<SysUser> users = session.selectList(DIRECTORY, directoryParams());
        return ids(users);
    }

    private List<Long> exportUserIds() {
        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("keyword", null);
        parameter.put("status", null);
        parameter.put("companyId", null);
        parameter.put("orgId", null);
        parameter.put("orgPathPrefix", null);
        return ids(session.selectList(EXPORT, parameter));
    }

    private static HashMap<String, Object> directoryParams() {
        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("keyword", null);
        parameter.put("orgId", null);
        parameter.put("orgPathPrefix", null);
        parameter.put("limit", 200);
        return parameter;
    }

    private static List<Long> ids(List<SysUser> users) {
        List<Long> ids = new ArrayList<>();
        for (SysUser user : users) {
            ids.add(user.getId());
        }
        Collections.sort(ids);
        return ids;
    }

    private int countUsers() {
        return session.selectOne(USERS, new HashMap<String, Object>());
    }

    private int instanceCount() {
        return session.selectOne(INSTANCES, new HashMap<String, Object>());
    }

    private SysUser byId(long id) {
        return session.selectOne(BY_ID, param("id", id));
    }

    private int countActiveByOrgPath(String pathPrefix) {
        return session.selectOne(ACTIVE_BY_PATH, param("orgPathPrefix", pathPrefix + "%"));
    }

    private static HashMap<String, Object> param(Object... keyValues) {
        HashMap<String, Object> parameter = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            parameter.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return parameter;
    }

    private void open() {
        session = factory.openSession(true);
    }

    /** 装配某角色的数据域上下文（与 DataScopeResolver 的口径一致）。 */
    private static void authenticate(long userId, String role, Set<DataScopeType> scopes, String deptPathPrefix,
                                     Long companyId, String financeDeptPathPrefix) {
        CurrentUser principal = CurrentUser.of(userId, "u" + userId, "用户" + userId, "MTX" + userId,
                companyId, companyId, new LinkedHashSet<>(Set.of(role)), scopes, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of(role))
                .scopes(scopes)
                .deptPathPrefix(deptPathPrefix)
                .financeDeptPathPrefix(financeDeptPathPrefix)
                .primaryOrgId(companyId)
                .companyId(companyId)
                .build());
    }

    // ================================================================ 夹具与环境

    /**
     * 执行夹具 SQL（默认 {@code ../oa-deploy/fixtures/40-authz-matrix.sql}，可被
     * {@code -Doa.it.fixture} / {@code OA_IT_FIXTURE} 覆盖）。
     *
     * <p>本方法只在 {@code url} 非空（= 已提供 DB 环境）时被调用；此时夹具缺失一律**失败**
     * 而不是跳过，缺失信息由 {@link AuthzMatrixFixture#resolve(String)} 给出。
     */
    private static void applyFixture(String url, String user, String password) throws Exception {
        Path path = AuthzMatrixFixture.resolve(url);
        assumeTrue(path != null, "未提供 -Doa.it.db.url / OA_IT_DB_URL，跳过越权矩阵测试");
        String script = Files.readString(path, StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            for (String raw : script.split(";")) {
                String sql = stripComments(raw);
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    private static String stripComments(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static boolean canConnect(String url, String user, String password) {
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String config(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null ? "" : value;
    }
}
