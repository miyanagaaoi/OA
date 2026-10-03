package com.oa.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeInterceptor;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.runtime.infra.FlowRuntimeMapper;
import com.oa.workflow.runtime.infra.row.FlowCcRow;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>抄送已读（{@code flow_cc.read_at}）在真实 MySQL 上的行级语义</b>（AC-54 / TC-MSG-004）。
 *
 * <p>为什么必须打真实 MySQL：本工作的验收点是「**只**把抄送人本人那一行置位」、「非抄送人命中 0 行」、
 * 「重复打开**不覆盖**首次已读时间」—— 这三件事全部由 {@code UPDATE ... WHERE instance_id = ?
 * AND user_id = ? AND read_at IS NULL} 的**行级**效果决定，替身 Mapper 永远返回我让它返回的东西，
 * 证明不了它们。本类跑的是**生产同一份** {@code mapper/workflow/FlowRuntimeMapper.xml}
 * 与**同一个** {@link DataScopeInterceptor}。
 *
 * <p><b>与 {@code FlowInstanceDetailCcReadTest} 的分工</b>：那一类断言「读详情**调用了**本语句」
 * （调用点回归网，本轮修的就是「没有调用点」），本类断言「这条语句在库里的效果正确」。
 * 两者合起来才是完整证据链。
 *
 * <p>每个用例前把两张单的 {@code read_at} 复位为 {@code NULL}，因此用例之间不依赖执行顺序。
 *
 * <p><b>开启方式</b>（与其它 {@code *MySqlIntegrationTest} 一致）：
 * <pre>
 * mvn -B test -Dtest=FlowCcReadMySqlIntegrationTest "-Doa.it.db.url=jdbc:mysql://127.0.0.1:3306/oa?..."
 * </pre>
 */
class FlowCcReadMySqlIntegrationTest {

    private static final String MARK_CC_READ = "com.oa.workflow.runtime.infra.FlowRuntimeMapper.markCcRead";
    private static final String SELECT_CC = "com.oa.workflow.runtime.infra.FlowRuntimeMapper.selectCcByInstance";

    /** 夹具账号前缀（清理时按此删除，避免留下悬挂人员）。 */
    private static final String ACCOUNT_PREFIX = "it_cc_";

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000L);

    private static SqlSessionFactory factory;
    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;

    /** 抄送人本人（「我」）。 */
    private static long ccUserId;
    /** 同单据上的另一位抄送人（非本人）。 */
    private static long otherCcUserId;

    private static long instanceOne;
    private static long instanceTwo;

    private SqlSession session;

    @BeforeAll
    static void initFixture() throws Exception {
        jdbcUrl = config("oa.it.db.url", "OA_IT_DB_URL");
        assumeTrue(!jdbcUrl.isBlank(), "未提供 -Doa.it.db.url / OA_IT_DB_URL，跳过 MySQL 集成测试");
        jdbcUser = config("oa.it.db.user", "OA_IT_DB_USER");
        jdbcPassword = config("oa.it.db.password", "OA_IT_DB_PASSWORD");
        assumeTrue(canConnect(), "MySQL 不可连接，跳过集成测试：" + jdbcUrl);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("integration", new JdbcTransactionFactory(),
                new PooledDataSource("com.mysql.cj.jdbc.Driver", jdbcUrl, jdbcUser, jdbcPassword)));
        configuration.addInterceptor(new DataScopeInterceptor(new DataScopeTableRegistry(new OaProperties())));
        try (InputStream xml = Resources.getResourceAsStream("mapper/workflow/FlowRuntimeMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/workflow/FlowRuntimeMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);

        seed();
    }

    @AfterAll
    static void cleanFixture() {
        factory = null;
        if (jdbcUrl == null || jdbcUrl.isBlank() || instanceOne <= 0) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM flow_cc WHERE instance_id IN ("
                    + instanceOne + "," + instanceTwo + ")");
            statement.executeUpdate("DELETE FROM flow_instance WHERE id IN ("
                    + instanceOne + "," + instanceTwo + ")");
            statement.executeUpdate("DELETE FROM form_data WHERE biz_no LIKE 'IT-CCREAD-%'");
            statement.executeUpdate("DELETE FROM sys_user WHERE account LIKE '" + ACCOUNT_PREFIX + "%'");
        } catch (Exception ex) {
            System.err.println("清理 IT 夹具失败（不影响断言）：" + ex.getMessage());
        }
    }

    /** 每个用例从「全部未读」开始（用例之间不依赖执行顺序）。 */
    @BeforeEach
    void resetReadState() {
        executeUpdate("UPDATE flow_cc SET read_at = NULL WHERE instance_id IN ("
                + instanceOne + "," + instanceTwo + ")");
    }

    @AfterEach
    void clearContext() {
        if (session != null) {
            session.close();
            session = null;
        }
        DataScopeContext.clear();
    }

    // ================================================================ 行级语义

    @Test
    @DisplayName("抄送人打开详情 → 本人那一行 read_at 落库；**同单据的其它抄送人不受影响**")
    void markCcReadSetsOnlyTheCallersRow() {
        authenticate(ccUserId);
        session = factory.openSession(true);

        assertThat(readAt(instanceOne, ccUserId)).as("前置：本人未读").isNull();
        assertThat(readAt(instanceOne, otherCcUserId)).as("前置：另一位抄送人未读").isNull();

        int updated = session.update(MARK_CC_READ, instanceParam(instanceOne, ccUserId));

        assertThat(updated).as("恰好命中 1 行（uk_flow_cc 保证一人一行）").isEqualTo(1);
        assertThat(readAt(instanceOne, ccUserId)).as("本人：已读时间落库").isNotNull();
        assertThat(readAt(instanceOne, otherCcUserId)).as("非本人：一行都不动").isNull();
    }

    @Test
    @DisplayName("非抄送人访问 → 命中 0 行（抄送表里没有他的行，也不会给别人置位）")
    void markCcReadForNonCcUserWritesNothing() {
        authenticate(otherCcUserId);
        session = factory.openSession(true);

        long stranger = otherCcUserId + 1_000_000L;
        int updated = session.update(MARK_CC_READ, instanceParam(instanceTwo, stranger));

        assertThat(updated).as("没有该 (instance, user) 行 ⇒ 0 行").isZero();
        assertThat(readAt(instanceTwo, ccUserId)).as("本人的未读状态不受他人访问影响").isNull();
        assertThat(readAt(instanceTwo, otherCcUserId)).isNull();
    }

    @Test
    @DisplayName("重复打开详情 → 幂等：read_at **不被覆盖**（首次已读时间可查，AC-54）")
    void markCcReadIsIdempotentAndKeepsFirstReadAt() {
        authenticate(ccUserId);
        session = factory.openSession(true);

        // ① 首次：NULL → NOW()
        int first = session.update(MARK_CC_READ, instanceParam(instanceOne, otherCcUserId));
        assertThat(first).isEqualTo(1);
        String firstReadAt = readAt(instanceOne, otherCcUserId);
        assertThat(firstReadAt).isNotNull();

        // ② 再次：0 行，且值一字不变
        int second = session.update(MARK_CC_READ, instanceParam(instanceOne, otherCcUserId));
        assertThat(second).as("read_at IS NULL 守卫 ⇒ 第二次命中 0 行").isZero();
        assertThat(readAt(instanceOne, otherCcUserId))
                .as("已读时间不被第二次打开覆盖（幂等）").isEqualTo(firstReadAt);

        // ③ 已经是历史值（模拟「早就已读」）：同样不被 NOW() 覆盖
        executeUpdate("UPDATE flow_cc SET read_at = '2020-01-01 08:00:00' WHERE instance_id = "
                + instanceOne + " AND user_id = " + otherCcUserId);
        assertThat(session.update(MARK_CC_READ, instanceParam(instanceOne, otherCcUserId))).isZero();
        assertThat(readAt(instanceOne, otherCcUserId)).isEqualTo("2020-01-01 08:00:00");
    }

    @Test
    @DisplayName("已读写入只对**该单据**：给 instanceOne 置位不影响 instanceTwo 上的同一人")
    void markCcReadIsScopedToOneInstance() {
        authenticate(ccUserId);
        session = factory.openSession(true);

        int updated = session.update(MARK_CC_READ, instanceParam(instanceTwo, otherCcUserId));

        assertThat(updated).isEqualTo(1);
        assertThat(readAt(instanceTwo, otherCcUserId)).isNotNull();
        assertThat(readAt(instanceOne, otherCcUserId)).as("另一张单上的同一人仍为未读").isNull();
    }

    @Test
    @DisplayName("抄送行读取：按 instance_id 返回全部抄送人（一行一人，与已读状态无关）")
    void selectCcByInstanceReturnsEveryReceiver() {
        authenticate(ccUserId);
        session = factory.openSession(true);

        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceOne);
        List<FlowCcRow> rows = session.selectList(SELECT_CC, params);
        assertThat(rows).extracting(FlowCcRow::getUserId).containsExactlyInAnyOrder(ccUserId, otherCcUserId);
    }

    // ================================================================ 夹具与工具

    private static Map<String, Object> instanceParam(long instanceId, long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("userId", userId);
        return params;
    }

    private static String readAt(long instanceId, long userId) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT DATE_FORMAT(read_at, '%Y-%m-%d %H:%i:%s') FROM flow_cc"
                     + " WHERE instance_id = " + instanceId + " AND user_id = " + userId)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception ex) {
            throw new IllegalStateException("读取 read_at 失败：" + ex.getMessage(), ex);
        }
    }

    private static void executeUpdate(String sql) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception ex) {
            throw new IllegalStateException("执行夹具 SQL 失败：" + ex.getMessage(), ex);
        }
    }

    /** 合成登录人（本类只打抄送表，不依赖数据域；仍显式装载上下文以贴近生产调用形态）。 */
    private static void authenticate(long userId) {
        Set<DataScopeType> scopes = EnumSet.of(DataScopeType.SELF);
        CurrentUser principal = CurrentUser.of(userId, "it_cc_" + userId, "IT抄送人", "ITCC" + userId,
                null, null, new LinkedHashSet<>(Set.of("employee")), scopes, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(scopes)
                .build());
    }

    /** 自建夹具（唯一命名，可反复执行；{@link #cleanFixture()} 负责删除）。 */
    private static void seed() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement()) {
            long templateId = firstLong(statement, "SELECT id FROM flow_template ORDER BY id ASC LIMIT 1");
            long templateVersion = firstLong(statement,
                    "SELECT version FROM flow_template ORDER BY id ASC LIMIT 1");
            long companyId = firstLong(statement,
                    "SELECT id FROM sys_org WHERE org_type = 'company' ORDER BY id ASC LIMIT 1");
            assumeTrue(templateId > 0 && companyId > 0,
                    "库中缺少流程模板或公司组织，跳过 MySQL 集成测试（请先跑 oa-deploy/fixtures）");

            String suffix = String.valueOf(SEQ.incrementAndGet());
            long initiatorId = insertUser(statement, ACCOUNT_PREFIX + "init_" + suffix,
                    "IT抄送发起人" + suffix, companyId);
            ccUserId = insertUser(statement, ACCOUNT_PREFIX + "me_" + suffix, "IT抄送我" + suffix, companyId);
            otherCcUserId = insertUser(statement, ACCOUNT_PREFIX + "other_" + suffix,
                    "IT抄送他人" + suffix, companyId);

            instanceOne = insertInstance(statement, "IT-CCREAD-A-" + suffix, initiatorId, companyId,
                    templateId, templateVersion);
            instanceTwo = insertInstance(statement, "IT-CCREAD-B-" + suffix, initiatorId, companyId,
                    templateId, templateVersion);

            // 两张单都抄送「我」与「另一位」；read_at 由用例自己制造（@BeforeEach 复位为 NULL）
            insertCc(statement, instanceOne, ccUserId);
            insertCc(statement, instanceOne, otherCcUserId);
            insertCc(statement, instanceTwo, ccUserId);
            insertCc(statement, instanceTwo, otherCcUserId);
        }
    }

    private static long insertUser(Statement statement, String account, String name, long companyId)
            throws Exception {
        statement.executeUpdate("INSERT INTO sys_user (account, name, employee_no, password_hash, "
                + "org_id, company_id, status) VALUES ('" + account + "', '" + name + "', 'IT" + account.hashCode()
                + "', '$2a$12$0000000000000000000000000000000000000000000000000000', "
                + companyId + ", " + companyId + ", 'active')");
        return firstLong(statement, "SELECT id FROM sys_user WHERE account = '" + account + "'");
    }

    private static long insertInstance(Statement statement, String bizNo, long initiatorId, long companyId,
                                       long templateId, long templateVersion) throws Exception {
        statement.executeUpdate("INSERT INTO form_data (form_type, biz_no, fields_json, schema_version, creator_id) "
                + "VALUES ('matter', '" + bizNo + "', '{\"title\":\"IT 抄送已读\"}', 1, " + initiatorId + ")");
        long formDataId = firstLong(statement, "SELECT id FROM form_data WHERE biz_no = '" + bizNo + "'");
        statement.executeUpdate("INSERT INTO flow_instance (biz_no, template_id, template_version, form_data_id, "
                + "form_type, category, initiator_id, initiator_org_id, initiator_company_id, initiator_org_path, "
                + "approver_snapshot_json, status) VALUES ('" + bizNo + "', " + templateId + ", "
                + templateVersion + ", " + formDataId + ", 'matter', 'business', " + initiatorId + ", "
                + companyId + ", " + companyId + ", '/1/" + companyId + "/', '{}', 'approving')");
        return firstLong(statement, "SELECT id FROM flow_instance WHERE biz_no = '" + bizNo + "'");
    }

    private static void insertCc(Statement statement, long instanceId, long userId) throws Exception {
        statement.executeUpdate("INSERT INTO flow_cc (instance_id, user_id, source) VALUES ("
                + instanceId + ", " + userId + ", 'initiator')");
    }

    private static long firstLong(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
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
