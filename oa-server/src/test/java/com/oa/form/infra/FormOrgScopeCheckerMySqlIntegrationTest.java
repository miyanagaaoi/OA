package com.oa.form.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeInterceptor;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.workflow.approver.infra.ApproverDirectoryMapper;
import com.oa.workflow.approver.infra.JdbcApproverDirectory;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>组织选择范围（{@code rules[orgScope] = initiator_company_subtree}）在真实 MySQL 上的判定</b>。
 *
 * <p>为什么必须打真实库：本判定依赖 {@code sys_org.path}（祖先路径，含自身）的前缀关系，
 * 而「本公司**及以下**节点」与「祖先节点不算、兄弟公司不算、路径前缀相同但不构成祖先不算」这几条
 * 只能由真实组织树证明（内存替身只会返回我写死的树）。本类跑的是**生产同一份**
 * {@code mapper/workflow/ApproverDirectoryMapper.xml} + {@link JdbcApproverDirectory}
 * （系统口径）+ 生产实现 {@link FormOrgScopeChecker}。
 *
 * <p>组织夹具取自 {@code oa-deploy/fixtures/10-dev-orgs.sql}（集团 1 / 公司A 12 / 公司B 13 /
 * 部门 135·136 / 公司B 部门 137 / 科室 138 / 集团财务部 150 / {@code RT-*} 151–155）；
 * 另自建一个**停用**节点（用完即删）验证「停用即不在范围内」。
 */
class FormOrgScopeCheckerMySqlIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 100_000L);

    private static SqlSessionFactory factory;
    private static SqlSession session;
    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;

    private static FormOrgScopeChecker checker;

    /** 自建的停用节点（用完即删）。 */
    private static long disabledOrgId;

    @BeforeAll
    static void initFixture() throws Exception {
        jdbcUrl = config("oa.it.db.url", "OA_IT_DB_URL");
        assumeTrue(!jdbcUrl.isBlank(), "未提供 -Doa.it.db.url / OA_IT_DB_URL，跳过 MySQL 集成测试");
        jdbcUser = config("oa.it.db.user", "OA_IT_DB_USER");
        jdbcPassword = config("oa.it.db.password", "OA_IT_DB_PASSWORD");
        assumeTrue(canConnect(), "MySQL 不可连接，跳过集成测试：" + jdbcUrl);

        String suffix = String.valueOf(SEQ.incrementAndGet());
        disabledOrgId = 990000L + (SEQ.get() % 1000L);
        assumeTrue(orgExists(12L) && orgExists(13L) && orgExists(135L) && orgExists(138L) && orgExists(1L),
                "库中缺少演示组织（集团/公司A/公司B/部门1/科室1），跳过 MySQL 集成测试"
                        + "（请先跑 oa-deploy/fixtures/10-dev-orgs.sql）");

        // 停用节点：挂在公司A 下（path 形如 /1/12/990001/），status=disabled
        executeUpdate("DELETE FROM sys_org WHERE id = " + disabledOrgId);
        executeUpdate("INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status) VALUES ("
                + disabledOrgId + ", 12, 'dept', 'IT-已停用部门" + suffix + "', '/1/12/" + disabledOrgId
                + "/', 3, 999, 'disabled')");

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("integration", new JdbcTransactionFactory(),
                new PooledDataSource("com.mysql.cj.jdbc.Driver", jdbcUrl, jdbcUser, jdbcPassword)));
        configuration.addInterceptor(new DataScopeInterceptor(new DataScopeTableRegistry(new OaProperties())));
        try (InputStream xml = Resources.getResourceAsStream("mapper/workflow/ApproverDirectoryMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/workflow/ApproverDirectoryMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
        // 会话在整个测试类生命周期内保持打开（JdbcApproverDirectory 会持续查询组织树）
        session = factory.openSession(true);
        checker = new FormOrgScopeChecker(
                new JdbcApproverDirectory(session.getMapper(ApproverDirectoryMapper.class)));
    }

    @AfterAll
    static void cleanFixture() {
        if (session != null) {
            session.close();
            session = null;
        }
        factory = null;
        if (jdbcUrl == null || jdbcUrl.isBlank() || disabledOrgId <= 0) {
            return;
        }
        try {
            executeUpdate("DELETE FROM sys_org WHERE id = " + disabledOrgId);
        } catch (Exception ex) {
            System.err.println("清理 IT 夹具失败（不影响断言）：" + ex.getMessage());
        }
    }

    // ================================================================ 通过侧

    @Test
    @DisplayName("本公司根（公司A 自己）+ 本公司子树内的部门/科室 → 通过（「本公司**及以下**」含自身）")
    void companyRootAndItsSubtreeAreAllowed() {
        assertThat(checker.withinInitiatorCompanySubtree("12", 12L)).as("公司A 自身").isTrue();
        assertThat(checker.withinInitiatorCompanySubtree("135", 12L)).as("公司A 的部门1").isTrue();
        assertThat(checker.withinInitiatorCompanySubtree("136", 12L)).as("公司A 的部门2").isTrue();
        assertThat(checker.withinInitiatorCompanySubtree("138", 12L)).as("部门1 下的科室1").isTrue();
    }

    @Test
    @DisplayName("集团层发起人（company_id 指向集团根）→ 子树包含全部公司（夹具 matrix_admin/dev_gl01 的形状）")
    void groupNodeCoversEveryCompany() {
        assertThat(checker.withinInitiatorCompanySubtree("1", 1L)).as("集团根自身").isTrue();
        assertThat(checker.withinInitiatorCompanySubtree("12", 1L)).as("公司A").isTrue();
        assertThat(checker.withinInitiatorCompanySubtree("13", 1L)).as("公司B").isTrue();
        assertThat(checker.withinInitiatorCompanySubtree("150", 1L)).as("集团财务部（直挂集团）").isTrue();
    }

    // ================================================================ 拒绝侧

    @Test
    @DisplayName("外公司节点（公司B 及其部门）→ 拒绝（这正是本条规则要堵住的越权取值）")
    void foreignCompanyNodesAreRejected() {
        assertThat(checker.withinInitiatorCompanySubtree("13", 12L)).as("另一家公司").isFalse();
        assertThat(checker.withinInitiatorCompanySubtree("137", 12L)).as("另一家公司的部门").isFalse();
    }

    @Test
    @DisplayName("**祖先**节点不算「本公司及以下」：集团根对公司A 的发起人是越界取值")
    void ancestorNodeIsRejected() {
        assertThat(checker.withinInitiatorCompanySubtree("1", 12L)).as("集团根是公司A 的祖先").isFalse();
        assertThat(checker.withinInitiatorCompanySubtree("150", 12L)).as("集团财务部不在公司A 子树内").isFalse();
    }

    @Test
    @DisplayName("路径前缀相近但不是后代：{@code /1/989121/} 不得被当成 {@code /1/98912/} 的子节点")
    void siblingWithSimilarPathPrefixIsRejected() {
        // 两个临时公司（用完即删）：祖先 id=98912（path /1/98912/）与候选 id=989121（path /1/989121/）——
        // 后者的 path 在**字符层面**以前者为前缀，只有「归一化后带尾斜杠」的前缀匹配才不会被骗。
        long ancestorId = 98912L;
        long candidateId = 989121L;
        assumeTrue(!orgExists(ancestorId) && !orgExists(candidateId),
                "临时 id 段被占用，跳过前缀相似用例（本机库非夹具状态）");
        executeUpdate("INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status) VALUES ("
                + ancestorId + ", 12, 'company', 'IT-前缀公司', '/1/12/" + ancestorId + "/', 2, 997, 'active')");
        // 注意：候选挂在集团根下，path 与祖先只是字符前缀相近，业务上并无父子关系
        executeUpdate("INSERT INTO sys_org (id, parent_id, org_type, name, path, depth, sort_no, status) VALUES ("
                + candidateId + ", 12, 'company', 'IT-前缀公司兄弟', '/1/12/" + candidateId + "/', 2, 996, 'active')");
        try {
            assertThat(checker.withinInitiatorCompanySubtree(String.valueOf(candidateId), ancestorId))
                    .as("/1/989121/ 不是 /1/98912/ 的后代").isFalse();
            assertThat(checker.withinInitiatorCompanySubtree(String.valueOf(ancestorId), ancestorId))
                    .as("自身仍然在范围内").isTrue();
        } finally {
            executeUpdate("DELETE FROM sys_org WHERE id IN (" + ancestorId + "," + candidateId + ")");
        }
    }

    @Test
    @DisplayName("不存在 / 非十进制取值 / 已停用节点 → 一律不在范围内（fail-closed）")
    void missingDisabledAndMalformedValuesAreRejected() {
        assertThat(checker.withinInitiatorCompanySubtree("999999999", 12L)).as("不存在的 id").isFalse();
        assertThat(checker.withinInitiatorCompanySubtree(String.valueOf(disabledOrgId), 12L))
                .as("存在但 status=disabled").isFalse();
        assertThat(checker.withinInitiatorCompanySubtree("initiator_company", 12L))
                .as("模板里的符号型占位（不是 id）").isFalse();
        assertThat(checker.withinInitiatorCompanySubtree("张三", 12L)).as("人手填的姓名").isFalse();
        assertThat(checker.withinInitiatorCompanySubtree("  ", 12L)).as("空白").isFalse();
    }

    @Test
    @DisplayName("发起人无公司（companyId 为空）→ **fail-closed 拒绝**（范围不可判定时不放行）")
    void nullInitiatorCompanyIsRejected() {
        assertThat(checker.withinInitiatorCompanySubtree("12", null)).isFalse();
        assertThat(checker.withinInitiatorCompanySubtree("135", null)).isFalse();
    }

    // ================================================================ 夹具与工具

    private static boolean orgExists(long orgId) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1 FROM sys_org WHERE id = " + orgId)) {
            return rs.next();
        } catch (Exception ex) {
            return false;
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
