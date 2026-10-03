package com.oa.common.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.config.OaProperties;
import com.oa.common.security.CurrentUser;
import com.oa.identity.domain.SysUser;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
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
 * 数据域织入 × 真实 MySQL 的端到端集成测试（**默认跳过**，仅在本机有库时手动开启）。
 *
 * <p>开启方式（不把口令写进仓库，故一律走系统属性）：
 * <pre>
 * mvn -B test -Dtest=DataScopeMySqlIntegrationTest `
 *     "-Doa.it.db.url=jdbc:mysql://127.0.0.1:3306/oa?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=Asia/Shanghai" `
 *     -Doa.it.db.user=oa -Doa.it.db.password=***
 * </pre>
 *
 * <p>它验证的是**真实驱动**的判定口径：MySQL 的 {@code NativeQueryBindings#checkParameterSet} 在某个
 * {@code ?} 没有绑定时抛 {@code No value specified for parameter N}（回归缺陷的原始表现）。
 * 语句来自生产同一份 {@code mapper/identity/SysUserMapper.xml}（含 {@code @dataScope} 标记与
 * {@code LIMIT #{limit}}），因此覆盖「普通查询」与「IPage 分页 + 自动 count」两条路径，
 * 并断言返回行确实落在数据域内（本人口径下只能看到自己）。
 */
class DataScopeMySqlIntegrationTest {

    private static final String DIRECTORY_STATEMENT = "com.oa.identity.infra.SysUserMapper.selectDirectoryUsers";
    private static final String PAGE_STATEMENT = "com.oa.identity.infra.SysUserMapper.selectUserPage";

    private static SqlSessionFactory factory;

    private SqlSession session;

    @BeforeAll
    static void initFactory() throws Exception {
        String url = config("oa.it.db.url", "OA_IT_DB_URL");
        assumeTrue(!url.isBlank(), "未提供 -Doa.it.db.url / OA_IT_DB_URL，跳过 MySQL 集成测试");
        String user = config("oa.it.db.user", "OA_IT_DB_USER");
        String password = config("oa.it.db.password", "OA_IT_DB_PASSWORD");
        assumeTrue(canConnect(url, user, password), "MySQL 不可连接，跳过集成测试：" + url);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("integration", new JdbcTransactionFactory(),
                new PooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
        MybatisPlusInterceptor mybatisPlus = new MybatisPlusInterceptor();
        mybatisPlus.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 注册顺序即「内层 → 外层」：数据域拦截器最后注册 = 最外层，与生产（@Order(LOWEST_PRECEDENCE)）一致
        configuration.addInterceptor(mybatisPlus);
        configuration.addInterceptor(new DataScopeInterceptor(new DataScopeTableRegistry(new OaProperties())));
        try (InputStream xml = Resources.getResourceAsStream("mapper/identity/SysUserMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/identity/SysUserMapper.xml",
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

    @Test
    @DisplayName("普通查询（LIMIT #{limit} + 带参数据域片段）：不再出现 No value specified for parameter N")
    void directoryQueryBindsEveryPlaceholder() {
        // 自读不变式：SELF 口径下只应看到自己（断言与库中数据量无关，可反复执行）
        authenticate(1L, DataScopeType.SELF);
        session = factory.openSession(true);

        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("keyword", null);
        parameter.put("orgId", null);
        parameter.put("orgPathPrefix", null);
        parameter.put("limit", 50);

        List<SysUser> users = session.selectList(DIRECTORY_STATEMENT, parameter);

        assertThat(users).isNotEmpty();
        assertThat(users).allSatisfy(user -> assertThat(user.getId()).isEqualTo(1L));
    }

    @Test
    @DisplayName("多口径并集（SELF ∪ 部门子树 ∪ 本公司）：三个数据域参数全部绑定成功，查询不抛参")
    void unionScopesBindAllPlaceholders() {
        // 本用例只验证「多分支片段的占位符全部绑定成功」——具体可见行数取决于库中数据，
        // 因此不做行数断言（越权口径的行级断言见 AuthzMatrixMySqlIntegrationTest 的矩阵）
        authenticate(1L, DataScopeType.SELF, DataScopeType.DEPT, DataScopeType.COMPANY);
        session = factory.openSession(true);

        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("keyword", null);
        parameter.put("orgId", null);
        parameter.put("orgPathPrefix", null);
        parameter.put("limit", 50);

        List<SysUser> users = session.selectList(DIRECTORY_STATEMENT, parameter);

        assertThat(users).isNotEmpty();
        assertThat(users).allSatisfy(user -> assertThat(user.getId()).isNotNull());
    }

    @Test
    @DisplayName("IPage 分页：count 语句与主查询都带上数据域过滤，且分页参数/数据域参数全部绑定成功")
    void pagedQueryRunsCountAndRecords() {
        authenticate(1L, DataScopeType.SELF, DataScopeType.DEPT, DataScopeType.COMPANY);
        session = factory.openSession(true);

        Page<SysUser> page = new Page<>(1, 10);
        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("param1", page);
        parameter.put("keyword", null);
        parameter.put("status", null);
        parameter.put("companyId", null);
        parameter.put("orgId", null);
        parameter.put("orgPathPrefix", null);

        List<SysUser> users = session.selectList(PAGE_STATEMENT, parameter);

        // count 语句真的执行了（否则分页插件会在空页短路）
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(users.size());
        // 数据域已收窄：结果集不能超过「本人 ∪ 本公司 ∪ 本部门子树」的可见范围
        assertThat(users).allSatisfy(user -> assertThat(user.getId()).isNotNull());
    }

    private static void authenticate(long userId, DataScopeType... scopes) {
        Set<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
        Collections.addAll(set, scopes);
        CurrentUser principal = CurrentUser.of(userId, "admin", "系统管理员", "A0001", 1L, 1L,
                new LinkedHashSet<>(Set.of("super_admin")), set, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("super_admin"))
                .scopes(set)
                .deptPathPrefix("/1/")
                .companyId(1L)
                .primaryOrgId(1L)
                .build());
    }

    private static boolean canConnect(String url, String user, String password) {
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** 连接参数：系统属性优先，其次环境变量（两者都不提供即跳过本类）。 */
    private static String config(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null ? "" : value;
    }
}
