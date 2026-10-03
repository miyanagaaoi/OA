package com.oa.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

/**
 * 异常路径审计 JSON × **真实 MySQL JSON 列**（默认跳过，仅在有库时开启）。
 *
 * <p>开启方式与既有集成测试一致（口令不写进仓库，走系统属性或环境变量）：
 * <pre>
 * mvn -B test -Dtest=AuditAspectMySqlIntegrationTest ^
 *     "-Doa.it.db.url=jdbc:mysql://127.0.0.1:3306/oa?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=Asia/Shanghai" ^
 *     -Doa.it.db.user=oa -Doa.it.db.password=***
 * </pre>
 *
 * <p>它证明的是**列级的判据**（单测只能证明「严格 JSON 解析器能读」）：
 * <ol>
 *   <li>旧拼装产物写 {@code JSON} 列 → 抛 {@code SQLException}（负对照，缺陷真的存在）；</li>
 *   <li>新结构化摘要写 {@code JSON} 列 → 成功，且 {@code JSON_VALID()} = 1。</li>
 * </ol>
 */
class AuditAspectMySqlIntegrationTest {

    private static final String NASTY_MESSAGE =
            "第一行 SQL 失败\n第二行\t原因：字段 \"amount\" 非法，路径 C:\\temp\\x";

    static class Sample {
        @Audited(action = "sample_boom", targetType = "sample", targetId = "#id", recordArgs = true)
        public String boom(Long id) {
            throw new BizException(ErrorCode.CONFLICT, NASTY_MESSAGE);
        }
    }

    @Test
    @DisplayName("异常路径 after_json 可写入 MySQL JSON 列（JSON_VALID=1）；旧拼装产物被列拒绝")
    void errorJsonIsAcceptedByJsonColumn() throws Throwable {
        String url = config("oa.it.db.url", "OA_IT_DB_URL");
        assumeTrue(!url.isBlank(), "未提供 -Doa.it.db.url / OA_IT_DB_URL，跳过 MySQL 集成测试");
        String user = config("oa.it.db.user", "OA_IT_DB_USER");
        String password = config("oa.it.db.password", "OA_IT_DB_PASSWORD");
        assumeTrue(canConnect(url, user, password), "MySQL 不可连接，跳过集成测试：" + url);

        String afterJson = capturedAfterJson();

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMPORARY TABLE probe_audit_json (after_json JSON NULL)");
            }

            // 负对照：旧实现（只替换双引号）的产物必须被 JSON 列拒绝
            String legacy = "{\"error\":\"" + BizException.class.getSimpleName() + "\",\"message\":\""
                    + NASTY_MESSAGE.replace("\"", "'") + "\"}";
            assertThatThrownBy(() -> insert(connection, legacy))
                    .as("旧拼装产物必须被 JSON 列拒绝（这正是运行期观察到的 Invalid JSON text）")
                    .isInstanceOf(SQLException.class);

            insert(connection, afterJson);

            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT JSON_VALID(after_json) AS v FROM probe_audit_json")) {
                try (ResultSet rs = statement.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("v")).as("JSON_VALID(after_json) 必须为 1").isEqualTo(1);
                }
            }
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT after_json AS j FROM probe_audit_json")) {
                try (ResultSet rs = statement.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    String stored = rs.getString("j");
                    assertThat(stored)
                            .contains("BizException")
                            .contains("trace-int-0001")
                            .contains("第一行 SQL 失败");
                    assertThat(new ObjectMapper().readTree(stored).get("stack").isTextual())
                            .as("堆栈在库里是 JSON **字符串字段**，不是 JSON 结构")
                            .isTrue();
                }
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    private static String capturedAfterJson() throws Throwable {
        MDC.put("traceId", "trace-int-0001");
        AuditLogWriter writer = mock(AuditLogWriter.class);
        AuditAspect aspect = new AuditAspect(writer, new ObjectMapper());

        Method method = Sample.class.getMethod("boom", Long.class);
        Audited audited = method.getAnnotation(Audited.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
        when(joinPoint.proceed()).thenThrow(new BizException(ErrorCode.CONFLICT, NASTY_MESSAGE));

        try {
            aspect.around(joinPoint, audited);
        } catch (BizException expected) {
            // 业务异常照旧抛出，不影响本用例
        }
        ArgumentCaptor<AuditLogWriter.AuditRecord> captor = ArgumentCaptor.forClass(AuditLogWriter.AuditRecord.class);
        verify(writer).append(captor.capture());
        return captor.getValue().afterJson();
    }

    private static void insert(Connection connection, String json) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("INSERT INTO probe_audit_json (after_json) VALUES (?)")) {
            statement.setString(1, json);
            statement.executeUpdate();
        }
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
