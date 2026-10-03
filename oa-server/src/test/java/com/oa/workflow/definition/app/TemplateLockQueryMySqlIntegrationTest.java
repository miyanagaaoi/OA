package com.oa.workflow.definition.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeInterceptor;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.approver.infra.row.LockedInstanceRow;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <b>{@code locked-by} 的数据域端到端（E 项，2026-10-04；默认跳过，仅本机有库时开启）</b>。
 *
 * <p>它证明的是本接口最关键的一条安全属性：查询走的是**受控表带标记**的 SQL，且在
 * <b>调用人数据域</b>下执行 —— 域外的在途实例**根本查不到**（不是查出来再过滤，也不是报错）。
 *
 * <p>开启方式（与 {@code DataScopeMySqlIntegrationTest} 同一套系统属性）：
 * <pre>
 * mvn -B test -Dtest=TemplateLockQueryMySqlIntegrationTest `
 *     "-Doa.it.db.url=jdbc:mysql://127.0.0.1:3306/oa?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=Asia/Shanghai" `
 *     -Doa.it.db.user=oa -Doa.it.db.password=***
 * </pre>
 *
 * <h2>为什么用例自己造数据并回滚</h2>
 * <p>在途实例是运行期数据，夹具里没有稳定形态（{@code 50-trigger-fixture.sql} 的那张单是
 * {@code draft}）。因此本类在**未提交事务**里插入 3 行（本人 approving / 他人 approving / 本人 draft），
 * 断言完就 {@code rollback()} —— 不污染开发库，也不依赖夹具的人员 id（发起人取自
 * {@code sys_user} 的前两行，取不到就跳过）。
 *
 * <p>语句来自生产同一份 {@code mapper/workflow/FlowInstanceMapper.xml}（含
 * {@code /* @dataScope(table=flow_instance, alias=i) *}{@code /} 标记），
 * 因此覆盖「标记替换 + 参数绑定 + 数据域收窄」三段真实链路。
 */
class TemplateLockQueryMySqlIntegrationTest {

    private static final String STATEMENT =
            "com.oa.workflow.approver.infra.FlowInstanceMapper.selectInFlightByTemplate";

    private static SqlSessionFactory factory;

    private SqlSession session;

    private Long templateId;

    private Integer templateVersion;

    private long mineId;

    private long otherId;

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
        // 注册顺序即「内层 → 外层」：数据域拦截器最后注册 = 最外层，与生产一致
        configuration.addInterceptor(mybatisPlus);
        configuration.addInterceptor(new DataScopeInterceptor(new DataScopeTableRegistry(new OaProperties())));
        try (InputStream xml = Resources.getResourceAsStream("mapper/workflow/FlowInstanceMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/workflow/FlowInstanceMapper.xml",
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
            session.rollback(); // 未提交事务：用例结束后库里不留任何痕迹
            session.close();
            session = null;
        }
        DataScopeContext.clear();
    }

    // ================================================================ 用例

    @Test
    @DisplayName("域内可见：本人发起的在途实例出现在结果里（单号/发起人/当前节点齐全），draft 不出现")
    void inFlightInstanceOfCallerIsVisible() throws Exception {
        seedInFlightInstances();

        Map<String, Object> parameter = parameter();
        List<LockedInstanceRow> mine = query(parameter, mineId, DataScopeType.SELF);

        assertThat(mine).extracting(LockedInstanceRow::getBizNo).containsExactly(MINE_BIZ_NO);
        LockedInstanceRow row = mine.get(0);
        assertThat(row.getTemplateVersion()).isEqualTo(templateVersion);
        assertThat(row.getStatus()).isEqualTo("approving");
        assertThat(row.getInitiatorId()).isEqualTo(mineId);
        assertThat(row.getInitiatorName()).as("LEFT JOIN sys_user 取到发起人姓名").isNotBlank();
        assertThat(row.getSubmittedAt()).as("DATE_FORMAT 后的发起时间").isNotBlank();
        assertThat(mine).extracting(LockedInstanceRow::getBizNo).doesNotContain(DRAFT_BIZ_NO);
    }

    @Test
    @DisplayName("域外不可见：他人发起的在途实例对我不出现（数据域在 SQL 层收窄，不是查完再过滤）")
    void outOfScopeInstanceIsNotVisible() throws Exception {
        seedInFlightInstances();

        Map<String, Object> parameter = parameter();
        List<LockedInstanceRow> mine = query(parameter, mineId, DataScopeType.SELF);
        List<LockedInstanceRow> others = query(parameter, otherId, DataScopeType.SELF);

        assertThat(mine).extracting(LockedInstanceRow::getBizNo).containsExactly(MINE_BIZ_NO);
        assertThat(others).extracting(LockedInstanceRow::getBizNo).containsExactly(OTHER_BIZ_NO);
        // 双向都不串：同一模板版本下两条在途实例各自只被其发起人看见
        assertThat(mine).extracting(LockedInstanceRow::getBizNo).doesNotContain(OTHER_BIZ_NO);
        assertThat(others).extracting(LockedInstanceRow::getBizNo).doesNotContain(MINE_BIZ_NO);
    }

    @Test
    @DisplayName("全集团口径：两条在途实例都可见（同一 SQL、不同数据域 → 不同可见集）")
    void groupScopeSeesEveryInFlightInstance() throws Exception {
        seedInFlightInstances();

        List<LockedInstanceRow> all = query(parameter(), mineId, DataScopeType.GROUP_ALL);

        assertThat(all).extracting(LockedInstanceRow::getBizNo)
                .contains(MINE_BIZ_NO, OTHER_BIZ_NO)
                .doesNotContain(DRAFT_BIZ_NO);
    }

    // ================================================================ 夹具

    private static final String MINE_BIZ_NO = "OA-IT-LOCK-0001";

    private static final String OTHER_BIZ_NO = "OA-IT-LOCK-0002";

    private static final String DRAFT_BIZ_NO = "OA-IT-LOCK-0003";

    private Map<String, Object> parameter() {
        HashMap<String, Object> parameter = new HashMap<>();
        parameter.put("templateId", templateId);
        parameter.put("templateVersion", templateVersion);
        return parameter;
    }

    /** 三个用例共用的事务内夹具（幂等：先删掉可能残留的旧行，再插新的）。 */
    private void seedInFlightInstances() throws Exception {
        session = factory.openSession(); // autocommit=false → 由 @AfterEach 回滚
        Connection connection = session.getConnection();

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM flow_instance WHERE biz_no LIKE 'OA-IT-LOCK-%'");
            statement.executeUpdate("DELETE FROM form_data WHERE biz_no LIKE 'OA-IT-LOCK-%'");
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, version FROM flow_template WHERE code = 'matter' ORDER BY id LIMIT 1")) {
            assumeTrue(rs.next(), "库中没有 code='matter' 的模板（未跑 Flyway V3？），跳过");
            templateId = rs.getLong("id");
            templateVersion = rs.getInt("version");
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id FROM sys_user ORDER BY id LIMIT 2")) {
            assumeTrue(rs.next(), "库中没有 sys_user（未跑夹具 10/20/40？），跳过");
            mineId = rs.getLong(1);
            assumeTrue(rs.next(), "库中 sys_user 少于 2 个，无法构造「域外」对手方，跳过");
            otherId = rs.getLong(1);
        }

        insertInstance(connection, MINE_BIZ_NO, mineId, "approving");
        insertInstance(connection, OTHER_BIZ_NO, otherId, "approving");
        insertInstance(connection, DRAFT_BIZ_NO, mineId, "draft"); // 在途口径：draft 不算「锁版本」
    }

    private void insertInstance(Connection connection, String bizNo, long initiatorId, String status)
            throws Exception {
        long formDataId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO form_data (biz_no, form_type, fields_json, schema_version, creator_id)"
                        + " VALUES (?, 'matter', '{}', 1, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, bizNo);
            statement.setLong(2, initiatorId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                formDataId = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO flow_instance (biz_no, template_id, template_version, form_data_id, form_type,"
                        + " category, initiator_id, initiator_org_id, initiator_company_id, initiator_org_path,"
                        + " approver_snapshot_json, status, current_node_seq, submitted_at)"
                        + " VALUES (?, ?, ?, ?, 'matter', 'business', ?, 135, 12, '/1/12/135/',"
                        + " '{\"it\":\"locked-by\"}', ?, 2, NOW())")) {
            statement.setString(1, bizNo);
            statement.setLong(2, templateId);
            statement.setInt(3, templateVersion);
            statement.setLong(4, formDataId);
            statement.setLong(5, initiatorId);
            statement.setString(6, status);
            statement.executeUpdate();
        }
    }

    // ================================================================ 查询 / 上下文

    private List<LockedInstanceRow> query(Map<String, Object> parameter, long userId, DataScopeType scope) {
        authenticate(userId, scope);
        return session.selectList(STATEMENT, parameter);
    }

    private static void authenticate(long userId, DataScopeType... scopes) {
        Set<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
        Collections.addAll(set, scopes);
        CurrentUser principal = CurrentUser.of(userId, "u" + userId, "集成测试用户", "IT" + userId, 135L, 12L,
                new LinkedHashSet<>(Set.of("employee")), set, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(set)
                .deptPathPrefix("/1/12/135/")
                .companyId(12L)
                .primaryOrgId(135L)
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
