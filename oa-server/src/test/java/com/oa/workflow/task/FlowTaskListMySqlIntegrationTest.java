package com.oa.workflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.scope.DataScopeInterceptor;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.common.scope.DataScopeType;
import com.oa.common.security.CurrentUser;
import com.oa.workflow.runtime.infra.row.FlowCcViewRow;
import com.oa.workflow.runtime.infra.row.FlowTaskViewRow;
import com.oa.workflow.task.domain.TaskListFilter;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>审批中心四个列表的筛选 × 抄送我的一览 × 真实 MySQL</b>（默认跳过，见类尾开启方式）。
 *
 * <p>为什么必须打在真实 MySQL 上：本工作的验收点是「**过滤在 SQL 层**（不是只筛当前页）」、
 * 「分页 {@code total} 是筛选后的总数」、「抄送表只出抄送给我的」、「域外 fail-closed」——
 * 这四件事都无法用替身 Mapper 证明（替身永远返回我让它返回的东西）。本类直接跑
 * <b>生产同一份</b> {@code mapper/workflow/FlowTaskMapper.xml} 与<b>同一个</b>
 * {@link DataScopeInterceptor}，因此断言到的就是运行期口径。
 *
 * <h2>夹具（本类自建自清，不依赖既有库内容）</h2>
 * <table border="1">
 *   <tr><th>单据</th><th>发起人</th><th>公司</th><th>状态</th><th>发起时间</th><th>抄送</th><th>任务</th></tr>
 *   <tr><td>A（甲 / matter）</td><td>我</td><td>C1</td><td>approving</td><td>2020-01-01</td>
 *       <td>我（未读）</td><td>pending→我（**待办**）</td></tr>
 *   <tr><td>B（乙 / fund）</td><td>我</td><td>C1</td><td>approved</td><td>2020-01-02</td>
 *       <td>我（已读）</td><td>agreed→我（**已办**）</td></tr>
 *   <tr><td>C（丙 / matter）</td><td>他（域外）</td><td>C2</td><td>approving</td><td>2020-01-03</td>
 *       <td>我</td><td>pending→他</td></tr>
 *   <tr><td>D（丁 / matter）</td><td>我</td><td>C1</td><td>draft</td><td>2020-01-04</td>
 *       <td>**他人**</td><td>—</td></tr>
 * </table>
 *
 * <p>D 是「只能看到抄送给我的」的关键反例：它由我发起（数据域内可见），但**没有抄送我**，
 * 因此绝不能出现在「抄送我的一览」里。
 *
 * <p><b>开启方式</b>：
 * <pre>
 * mvn -B test -Dtest=FlowTaskListMySqlIntegrationTest `
 *     "-Doa.it.db.url=jdbc:mysql://127.0.0.1:3306/oa?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=Asia/Shanghai" `
 *     -Doa.it.db.user=oa -Doa.it.db.password=***
 * </pre>
 */
class FlowTaskListMySqlIntegrationTest {

    private static final String TODO = "com.oa.workflow.runtime.infra.FlowTaskMapper.selectTodo";
    private static final String COUNT_TODO = "com.oa.workflow.runtime.infra.FlowTaskMapper.countTodo";
    private static final String DONE = "com.oa.workflow.runtime.infra.FlowTaskMapper.selectDone";
    private static final String COUNT_DONE = "com.oa.workflow.runtime.infra.FlowTaskMapper.countDone";
    private static final String INITIATED = "com.oa.workflow.runtime.infra.FlowTaskMapper.selectInitiated";
    private static final String COUNT_INITIATED = "com.oa.workflow.runtime.infra.FlowTaskMapper.countInitiated";
    private static final String CC = "com.oa.workflow.runtime.infra.FlowTaskMapper.selectCcOverview";
    private static final String COUNT_CC = "com.oa.workflow.runtime.infra.FlowTaskMapper.countCcOverview";

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000L);

    private static SqlSessionFactory factory;
    private static String jdbcUrl;
    private static String jdbcUser;
    private static String jdbcPassword;

    /** 我（列表的调用人）。 */
    private static long meId;
    private static String myName;
    private static long otherId;
    private static long companyOne;
    private static long companyTwo;
    /** 单据 id：A 待办 / B 已办 / C 域外 / D 未抄送我。 */
    private static long instanceA;
    private static long instanceB;
    private static long instanceC;
    private static long instanceD;
    private static String bizA;
    private static String bizB;
    private static String bizC;
    private static String bizD;

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
        // 顺序即「内层 → 外层」：数据域拦截器最后注册 = 最外层（与生产 @Order(LOWEST_PRECEDENCE) 一致）
        configuration.addInterceptor(new DataScopeInterceptor(new DataScopeTableRegistry(new OaProperties())));
        try (InputStream xml = Resources.getResourceAsStream("mapper/workflow/FlowTaskMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/workflow/FlowTaskMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);

        seed();
    }

    @AfterAll
    static void cleanFixture() {
        factory = null;
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement()) {
            if (instanceA > 0) {
                statement.executeUpdate("DELETE FROM flow_cc WHERE instance_id IN ("
                        + instanceA + "," + instanceB + "," + instanceC + "," + instanceD + ")");
                statement.executeUpdate("DELETE FROM flow_task WHERE instance_id IN ("
                        + instanceA + "," + instanceB + "," + instanceC + "," + instanceD + ")");
                statement.executeUpdate("DELETE FROM flow_node_instance WHERE instance_id IN ("
                        + instanceA + "," + instanceB + "," + instanceC + "," + instanceD + ")");
                statement.executeUpdate("DELETE FROM flow_instance WHERE id IN ("
                        + instanceA + "," + instanceB + "," + instanceC + "," + instanceD + ")");
                statement.executeUpdate("DELETE FROM form_data WHERE biz_no IN ('"
                        + bizA + "','" + bizB + "','" + bizC + "','" + bizD + "')");
            }
            if (meId > 0) {
                statement.executeUpdate("DELETE FROM sys_user WHERE id IN (" + meId + "," + otherId + ")");
            }
        } catch (Exception ex) {
            System.err.println("清理 IT 夹具失败（不影响断言）：" + ex.getMessage());
        }
    }

    @AfterEach
    void clearContext() {
        if (session != null) {
            session.close();
            session = null;
        }
        DataScopeContext.clear();
    }

    // ================================================================ 抄送我的一览（任务 2）

    @Test
    @DisplayName("抄送我的一览：只出「抄送给我」的单据（我发起但未抄送我的 D 不出现）")
    void ccListOnlyReturnsRowsCcToMe() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        List<FlowCcViewRow> rows = cc(TaskListFilter.none(), 1, 20);
        assertThat(bizNos(rows)).containsExactlyInAnyOrder(bizA, bizB, bizC);
        assertThat(bizNos(rows)).as("D 由我发起、抄送的是他人 → 不在「抄送我的一览」里").doesNotContain(bizD);
        assertThat(count(COUNT_CC, TaskListFilter.none())).isEqualTo(3);
    }

    @Test
    @DisplayName("抄送我的一览：出参七项齐全（标题/发起人/发起时间/状态/抄送时间/是否已读）")
    void ccListCarriesEveryRequiredField() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        Map<String, FlowCcViewRow> byBizNo = new HashMap<>();
        for (FlowCcViewRow row : cc(TaskListFilter.none(), 1, 20)) {
            byBizNo.put(row.getBizNo(), row);
        }

        FlowCcViewRow a = byBizNo.get(bizA);
        assertThat(a.getTitle()).isEqualTo("IT 抄送我的一览 甲");
        assertThat(a.getFormType()).isEqualTo("matter");
        assertThat(a.getInitiatorId()).isEqualTo(meId);
        assertThat(a.getInitiatorName()).isEqualTo(myName);
        assertThat(a.getInstanceCreatedAt()).isEqualTo("2020-01-01 09:00:00");
        assertThat(a.getInstanceStatus()).isEqualTo("approving");
        assertThat(a.getCcCreatedAt()).isEqualTo("2020-03-03 09:00:00");
        assertThat(a.getReadAt()).as("未读：read_at 为 NULL").isNull();
        assertThat(a.getCcSource()).isEqualTo("initiator");

        FlowCcViewRow b = byBizNo.get(bizB);
        assertThat(b.getReadAt()).as("已读：read_at 有值（抄送人打开详情时 markCcRead 写入）")
                .isEqualTo("2020-02-01 10:00:00");
    }

    @Test
    @DisplayName("抄送我的一览：抄送时间倒序 + 分页 total 是筛选后的总数（不是当前页条数）")
    void ccListIsPagedAndOrdered() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        List<FlowCcViewRow> firstPage = cc(TaskListFilter.none(), 1, 2);
        assertThat(firstPage).extracting(FlowCcViewRow::getBizNo).containsExactly(bizA, bizB);
        assertThat(count(COUNT_CC, TaskListFilter.none())).as("total 与页大小无关").isEqualTo(3);

        List<FlowCcViewRow> secondPage = cc(TaskListFilter.none(), 2, 2);
        assertThat(secondPage).extracting(FlowCcViewRow::getBizNo).containsExactly(bizC);
    }

    @Test
    @DisplayName("抄送我的一览：数据域 fail-closed —— company 口径下域外单据（C）不出现")
    void ccListIsFilteredByDataScope() {
        authenticate(meId, companyOne, DataScopeType.COMPANY);
        session = factory.openSession(true);

        List<FlowCcViewRow> rows = cc(TaskListFilter.none(), 1, 20);
        assertThat(bizNos(rows)).as("C 发起人属公司 C2，company 口径下必须不可见").containsExactlyInAnyOrder(bizA, bizB);
        assertThat(count(COUNT_CC, TaskListFilter.none())).isEqualTo(2);
    }

    // ================================================================ 三个列表的筛选（任务 3）

    @Test
    @DisplayName("筛选｜空值等价于不筛：四个端点 params 全空 = 全量（total = 行数）")
    void emptyFilterEqualsNoFilter() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        TaskListFilter empty = TaskListFilter.of("  ", null, "", null, "  ");
        assertThat(empty.isEmpty()).isTrue();

        assertThat(bizNos(todo(empty))).containsExactly(bizA);
        assertThat(bizNos(done(empty))).containsExactly(bizB);
        assertThat(bizNos(initiated(empty))).containsExactlyInAnyOrder(bizA, bizB, bizD);
        assertThat(bizNos(cc(empty, 1, 20))).containsExactlyInAnyOrder(bizA, bizB, bizC);
        assertThat(count(COUNT_INITIATED, empty)).isEqualTo(3);
    }

    @Test
    @DisplayName("筛选｜关键字：命中单号 / 标题 / 发起人三项，且通配符被转义（% 不匹配全部）")
    void keywordMatchesBizNoTitleAndInitiator() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        assertThat(bizNos(initiated(TaskListFilter.of("甲", null, null, null, null))))
                .as("标题命中").containsExactly(bizA);
        assertThat(bizNos(initiated(TaskListFilter.of(bizB, null, null, null, null))))
                .as("单号命中").containsExactly(bizB);
        assertThat(bizNos(initiated(TaskListFilter.of(myName, null, null, null, null))))
                .as("发起人命中：A/B/D 都是我发起的").containsExactlyInAnyOrder(bizA, bizB, bizD);
        assertThat(bizNos(initiated(TaskListFilter.of("丙", null, null, null, null))))
                .as("C 不是我发起的 → 「我发起的」里搜不到").isEmpty();
        assertThat(bizNos(initiated(TaskListFilter.of("%", null, null, null, null))))
                .as("未转义时 LIKE '%%%' 会命中全部；转义后 % 只按字面量匹配").isEmpty();
        assertThat(bizNos(initiated(TaskListFilter.of("_", null, null, null, null))))
                .as("_ 同样必须按字面量匹配").isEmpty();
    }

    @Test
    @DisplayName("筛选｜单据类型 / 单据状态：单独生效，且 total 随筛选变化")
    void formTypeAndStatusFiltersApplyAtSqlLevel() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        assertThat(bizNos(initiated(TaskListFilter.of(null, "fund", null, null, null))))
                .containsExactly(bizB);
        assertThat(count(COUNT_INITIATED, TaskListFilter.of(null, "matter", null, null, null)))
                .as("matter 两单（A/D）；total 是筛选后的总数").isEqualTo(2);
        assertThat(count(COUNT_TODO, TaskListFilter.of(null, "fund", null, null, null)))
                .as("待办里没有 fund 单 → 0（而不是「当前页为空但 total 仍是 1」）").isZero();

        assertThat(bizNos(initiated(TaskListFilter.of(null, null, "approved", null, null))))
                .containsExactly(bizB);
        assertThat(bizNos(initiated(TaskListFilter.of(null, null, "approving", null, null))))
                .containsExactly(bizA);
        assertThat(bizNos(initiated(TaskListFilter.of(null, null, "draft", null, null))))
                .containsExactly(bizD);
    }

    @Test
    @DisplayName("筛选｜起止日期：含首含尾（按单据发起时间 i.created_at），并支持区间")
    void dateRangeFilterIsInclusive() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        assertThat(bizNos(initiated(TaskListFilter.of(null, null, null, "2020-01-02", null))))
                .as("dateFrom 含当日（B 发起于 2020-01-02）").containsExactlyInAnyOrder(bizB, bizD);
        assertThat(bizNos(initiated(TaskListFilter.of(null, null, null, null, "2020-01-02"))))
                .as("dateTo 含当日（B 发起于 2020-01-02）").containsExactlyInAnyOrder(bizA, bizB);
        assertThat(bizNos(initiated(TaskListFilter.of(null, null, null, "2020-01-02", "2020-01-02"))))
                .containsExactly(bizB);
        assertThat(bizNos(initiated(TaskListFilter.of(null, null, null, "2021-01-01", null))))
                .isEmpty();
    }

    @Test
    @DisplayName("筛选｜组合生效：关键字 + 类型 + 状态 + 日期区间 → 唯一命中")
    void combinedFiltersNarrowToOneRow() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        TaskListFilter combined = TaskListFilter.of("乙", "fund", "approved", "2020-01-02", "2020-01-02");
        assertThat(bizNos(initiated(combined))).containsExactly(bizB);
        assertThat(count(COUNT_INITIATED, combined)).isEqualTo(1);

        TaskListFilter almost = TaskListFilter.of("乙", "matter", "approved", "2020-01-02", "2020-01-02");
        assertThat(bizNos(initiated(almost))).as("类型不符即 0（各条件是 AND）").isEmpty();
    }

    @Test
    @DisplayName("筛选｜三个任务列表同一套参数：同一组条件在 todo / done / initiated 上都生效")
    void everyListSharesTheSameFilterContract() {
        authenticate(meId, companyOne, DataScopeType.SELF);
        session = factory.openSession(true);

        TaskListFilter matter = TaskListFilter.of("甲", "matter", "approving", "2020-01-01", "2020-01-01");
        assertThat(bizNos(todo(matter))).containsExactly(bizA);
        assertThat(bizNos(done(matter))).as("A 的任务是 pending，不在已办").isEmpty();
        assertThat(bizNos(initiated(matter))).containsExactly(bizA);
        assertThat(bizNos(cc(matter, 1, 20))).containsExactly(bizA);
    }

    @Test
    @DisplayName("筛选｜非法取值 fail-closed：类型/状态/日期格式不合法一律 40001（不静默返回全量）")
    void illegalFilterValuesAreRejected() {
        assertThatThrownByCode(() -> TaskListFilter.of(null, "invoice", null, null, null));
        assertThatThrownByCode(() -> TaskListFilter.of(null, null, "done", null, null));
        assertThatThrownByCode(() -> TaskListFilter.of(null, null, null, "2020/01/01", null));
        assertThatThrownByCode(() -> TaskListFilter.of(null, null, null, "2020-02-30", null));
        assertThatThrownByCode(() -> TaskListFilter.of(null, null, null, "2020-01-05", "2020-01-01"));
        // 合法：子状态 pending_supplement 被接受（它落在 i.sub_status 上）
        assertThat(TaskListFilter.of(null, null, "pending_supplement", null, null).hasSubStatus()).isTrue();
    }

    // ================================================================ 夹具与工具

    private static void assertThatThrownByCode(Runnable action) {
        try {
            action.run();
            throw new AssertionError("期望 40001 参数校验失败，实际未抛出");
        } catch (BizException ex) {
            assertThat(ex.getErrorCode().getCode()).isEqualTo(40001);
        }
    }

    private List<FlowTaskViewRow> todo(TaskListFilter filter) {
        return query(TODO, filter);
    }

    private List<FlowTaskViewRow> done(TaskListFilter filter) {
        return query(DONE, filter);
    }

    private List<FlowTaskViewRow> initiated(TaskListFilter filter) {
        return query(INITIATED, filter);
    }

    private List<FlowTaskViewRow> query(String statement, TaskListFilter filter) {
        Map<String, Object> params = params(filter);
        params.put("offset", 0);
        params.put("size", 20);
        return session.selectList(statement, params);
    }

    private long count(String statement, TaskListFilter filter) {
        return ((Number) session.selectOne(statement, params(filter))).longValue();
    }

    private List<FlowCcViewRow> cc(TaskListFilter filter, int page, int size) {
        Map<String, Object> params = params(filter);
        params.put("offset", (page - 1) * size);
        params.put("size", size);
        return session.selectList(CC, params);
    }

    private Map<String, Object> params(TaskListFilter filter) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", meId);
        params.put("filter", filter == null ? TaskListFilter.none() : filter);
        return params;
    }

    private static List<String> bizNos(List<?> rows) {
        List<String> result = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof FlowTaskViewRow task) {
                result.add(task.getBizNo());
            } else if (row instanceof FlowCcViewRow cc) {
                result.add(cc.getBizNo());
            }
        }
        return result;
    }

    private static void authenticate(long userId, long companyId, DataScopeType... scopes) {
        Set<DataScopeType> set = EnumSet.noneOf(DataScopeType.class);
        Collections.addAll(set, scopes);
        CurrentUser principal = CurrentUser.of(userId, "it_list_me", myName, "IT0001", companyId, companyId,
                new LinkedHashSet<>(Set.of("employee")), set, false);
        DataScopeContext.set(DataScopeContext.builder()
                .principal(principal)
                .roleCodes(Set.of("employee"))
                .scopes(set)
                .companyId(companyId)
                .primaryOrgId(companyId)
                .build());
    }

    /** 自建夹具（唯一命名，可反复执行；{@link #cleanFixture()} 负责删除）。 */
    private static void seed() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
             Statement statement = connection.createStatement()) {
            companyOne = firstLong(statement, "SELECT id FROM sys_org ORDER BY id ASC LIMIT 1");
            companyTwo = firstLong(statement, "SELECT id FROM sys_org ORDER BY id ASC LIMIT 1 OFFSET 1");
            long templateId = firstLong(statement, "SELECT id FROM flow_template ORDER BY id ASC LIMIT 1");
            long templateVersion = firstLong(statement, "SELECT version FROM flow_template ORDER BY id ASC LIMIT 1");
            assumeTrue(companyOne > 0 && companyTwo > 0 && companyTwo != companyOne && templateId > 0,
                    "库中缺少组织或模板，跳过 MySQL 集成测试（请先跑 oa-deploy/fixtures）");

            String suffix = String.valueOf(SEQ.incrementAndGet());
            myName = "IT列表我" + suffix;
            meId = insertUser(statement, "it_list_me_" + suffix, myName, companyOne);
            otherId = insertUser(statement, "it_list_other_" + suffix, "IT列表他人" + suffix, companyOne);
            long foreignId = insertUser(statement, "it_list_foreign_" + suffix, "IT列表域外" + suffix, companyTwo);

            bizA = "IT-A-" + suffix;
            bizB = "IT-B-" + suffix;
            bizC = "IT-C-" + suffix;
            bizD = "IT-D-" + suffix;

            instanceA = insertInstance(statement, bizA, "matter", meId, companyOne, "approving",
                    "IT 抄送我的一览 甲", "2020-01-01 09:00:00", templateId, templateVersion);
            instanceB = insertInstance(statement, bizB, "fund", meId, companyOne, "approved",
                    "IT 抄送我的一览 乙", "2020-01-02 09:00:00", templateId, templateVersion);
            instanceC = insertInstance(statement, bizC, "matter", foreignId, companyTwo, "approving",
                    "IT 抄送我的一览 丙", "2020-01-03 09:00:00", templateId, templateVersion);
            instanceD = insertInstance(statement, bizD, "matter", meId, companyOne, "draft",
                    "IT 抄送我的一览 丁", "2020-01-04 09:00:00", templateId, templateVersion);

            // 任务：A 待办给我；B 已办给我；C 待办给域外人
            insertTask(statement, instanceA, meId, "pending", null, "2020-01-01 10:00:00");
            insertTask(statement, instanceB, meId, "agreed", "2020-01-02 10:00:00", "2020-01-02 10:00:00");
            insertTask(statement, instanceC, foreignId, "pending", null, "2020-01-03 10:00:00");

            // 抄送：A/B/C 抄送我（A 未读、B 已读）；D 抄送他人
            insertCc(statement, instanceA, meId, "initiator", null, "2020-03-03 09:00:00");
            insertCc(statement, instanceB, meId, "initiator", "2020-02-01 10:00:00", "2020-03-02 09:00:00");
            insertCc(statement, instanceC, meId, "template", null, "2020-03-01 09:00:00");
            insertCc(statement, instanceD, otherId, "initiator", null, "2020-03-04 09:00:00");
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

    private static long insertInstance(Statement statement, String bizNo, String formType, long initiatorId,
                                       long companyId, String status, String title, String createdAt,
                                       long templateId, long templateVersion) throws Exception {
        statement.executeUpdate("INSERT INTO form_data (form_type, biz_no, fields_json, schema_version, creator_id) "
                + "VALUES ('" + formType + "', '" + bizNo + "', '{\"title\":\"" + title + "\"}', 1, " + initiatorId + ")");
        long formDataId = firstLong(statement, "SELECT id FROM form_data WHERE biz_no = '" + bizNo + "'");
        statement.executeUpdate("INSERT INTO flow_instance (biz_no, template_id, template_version, form_data_id, "
                + "form_type, category, initiator_id, initiator_org_id, initiator_company_id, initiator_org_path, "
                + "approver_snapshot_json, status, created_at) VALUES ('" + bizNo + "', " + templateId + ", "
                + templateVersion + ", " + formDataId + ", '" + formType + "', 'business', " + initiatorId + ", "
                + companyId + ", " + companyId + ", '/1/" + companyId + "/', '{}', '" + status + "', '"
                + createdAt + "')");
        return firstLong(statement, "SELECT id FROM flow_instance WHERE biz_no = '" + bizNo + "'");
    }

    private static void insertTask(Statement statement, long instanceId, long assigneeId, String status,
                                   String decidedAt, String createdAt) throws Exception {
        statement.executeUpdate("INSERT INTO flow_node_instance (instance_id, node_seq, node_code, node_name, "
                + "node_key, approver_ids_json, status) VALUES (" + instanceId + ", 1, 'dept_leader', '直属部门负责人', '"
                + instanceId + ":1:dept_leader', '[]', 'active')");
        long nodeInstanceId = firstLong(statement,
                "SELECT id FROM flow_node_instance WHERE instance_id = " + instanceId + " ORDER BY id DESC LIMIT 1");
        statement.executeUpdate("INSERT INTO flow_task (instance_id, node_instance_id, assignee_id, status, "
                + "decided_at, created_at) VALUES (" + instanceId + ", " + nodeInstanceId + ", " + assigneeId + ", '"
                + status + "', " + (decidedAt == null ? "NULL" : "'" + decidedAt + "'") + ", '" + createdAt + "')");
    }

    private static void insertCc(Statement statement, long instanceId, long userId, String source,
                                 String readAt, String createdAt) throws Exception {
        statement.executeUpdate("INSERT INTO flow_cc (instance_id, user_id, source, read_at, created_at) VALUES ("
                + instanceId + ", " + userId + ", '" + source + "', "
                + (readAt == null ? "NULL" : "'" + readAt + "'") + ", '" + createdAt + "')");
    }

    private static long firstLong(Statement statement, String sql, long... fallback) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        }
        return fallback.length > 0 ? fallback[0] : 0L;
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
