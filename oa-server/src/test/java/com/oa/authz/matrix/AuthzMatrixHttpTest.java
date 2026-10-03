package com.oa.authz.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.security.PasswordService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 阶段 1 DoD：<b>逐接口越权矩阵测试（角色 × 数据域 × 入口）——HTTP 层（真实运行的应用）</b>。
 *
 * <p>与 {@link AuthzMatrixMySqlIntegrationTest}（SQL 层）互补：本类打的是**真接口**
 * （手改 URL / 直连接口 / 换 id 参数三条 DoD 断言都在这一层落地）。夹具沿用
 * {@code .cache/oa-authz-matrix-fixture.sql}（5 个角色各 1 人），测试只把口令哈希与
 * {@code last_login_at} 补成可登录状态（口令每次运行随机生成，不写进任何入库文件）。
 *
 * <h2>期望三态</h2>
 * <ul>
 *   <li><b>200 + 数据被过滤</b>：列表类接口；</li>
 *   <li><b>403 / 404</b>：管理类接口越权（403）与换 id 参数取域外详情（404）；</li>
 *   <li><b>200 + ok=false</b>：导入类接口的「数据域外行 → 整批拒绝」（fail-closed 的可观测形态）。</li>
 * </ul>
 *
 * <h2>开启方式</h2>
 * <pre>
 * $env:OA_IT_DB_URL='jdbc:mysql://127.0.0.1:3306/oa?...'; $env:OA_IT_DB_USER='oa'; $env:OA_IT_DB_PASSWORD='***'
 * $env:OA_IT_APP_URL='http://127.0.0.1:8080'; $env:OA_IT_ADMIN_PASSWORD='***'
 * mvn -B test -Dtest=AuthzMatrixHttpIT
 * </pre>
 * 未提供连接参数 / 应用未启动 / 未给管理员口令 → 整类跳过（CI 无环境时不会红）。
 */
class AuthzMatrixHttpTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 夹具账号（与 .cache/oa-authz-matrix-fixture.sql 一致）。 */
    private static final String CA = "mtx_ca01";

    private static final String DL = "mtx_dl01";

    private static final String GL = "mtx_gl01";

    private static final String EM_A = "mtx_em01";

    private static final String EM_B = "mtx_em02";

    private static final long EM_B_ID = 205L;

    private static final long CA_ID = 201L;

    private static final String ADMIN = "admin";

    private static String baseUrl;

    private static String dbUrl;

    private static String dbUser;

    private static String dbPassword;

    private static HttpClient client;

    /** 每角色一份会话 Cookie。 */
    private static final Map<String, String> COOKIES = new LinkedHashMap<>();

    @BeforeAll
    static void setUp() throws Exception {
        baseUrl = config("oa.it.app.url", "OA_IT_APP_URL", "http://127.0.0.1:8080");
        dbUrl = config("oa.it.db.url", "OA_IT_DB_URL", "");
        dbUser = config("oa.it.db.user", "OA_IT_DB_USER", "");
        dbPassword = config("oa.it.db.password", "OA_IT_DB_PASSWORD", "");
        String adminPassword = config("oa.it.admin.password", "OA_IT_ADMIN_PASSWORD", "");
        assumeTrue(!dbUrl.isBlank(), "未提供数据库连接参数，跳过 HTTP 越权矩阵测试");
        assumeTrue(!adminPassword.isBlank(), "未提供 -Doa.it.admin.password / OA_IT_ADMIN_PASSWORD，跳过 HTTP 越权矩阵");
        assumeTrue(healthUp(), "应用未就绪（" + baseUrl + "/actuator/health），跳过 HTTP 越权矩阵测试");
        assumeTrue(canConnect(), "MySQL 不可连接，跳过 HTTP 越权矩阵测试");

        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        applyFixtureAndCredentials();

        COOKIES.put(ADMIN, login(ADMIN, adminPassword));
        String shared = "Matrix@" + randomDigits();
        setPassword(CA, shared);
        setPassword(DL, shared);
        setPassword(GL, shared);
        setPassword(EM_A, shared);
        setPassword(EM_B, shared);
        for (String account : new String[] {CA, DL, GL, EM_A, EM_B}) {
            COOKIES.put(account, login(account, shared));
        }
    }

    // ================================================================ 矩阵

    @Test
    @DisplayName("组织树（GET /identity/orgs/tree）：5 角色都能进，但可见节点随数据域收敛（不报错、不 500）")
    void orgTreeIsScopedNotBroken() {
        assertThat(get("/api/v1/identity/orgs/tree", ADMIN).status()).isEqualTo(200);
        for (String account : new String[] {CA, DL, GL, EM_A}) {
            ApiResult result = get("/api/v1/identity/orgs/tree", account);
            assertThat(result.status()).as("角色 %s 的组织树应 200 而不是报错", account).isEqualTo(200);
            assertThat(result.json().path("data")).isNotNull();
        }
        // 数据域收敛的证据：员工看不到「公司B」子树的节点
        assertThat(get("/api/v1/identity/orgs/tree", EM_A).body()).doesNotContain("公司B");
        assertThat(get("/api/v1/identity/orgs/tree", CA).body()).doesNotContain("公司B");
        assertThat(get("/api/v1/identity/orgs/tree", ADMIN).body()).contains("公司B");
    }

    @Test
    @DisplayName("人员列表（GET /identity/users）：换 id 参数/直连都拿不到域外人员（200 + 数据被过滤为空）")
    void userListIsFilteredByDataScope() {
        assertThat(accountList(ADMIN)).contains(CA, DL, GL, EM_A, EM_B);
        assertThat(accountList(CA)).contains(CA, DL, EM_A).doesNotContain(EM_B, GL);
        assertThat(accountList(DL)).contains(DL, EM_A).doesNotContain(CA, EM_B);
        assertThat(accountList(EM_A)).containsExactly(EM_A);
        assertThat(accountList(EM_B)).containsExactly(EM_B);
    }

    @Test
    @DisplayName("换 id 参数（GET /users/{id}/pending-tasks 与 PUT /users/{id}）：域外一律 404 —— 手改 id 读写都被硬拒绝")
    void changingIdCannotReachOtherScope() {
        assertThat(get("/api/v1/identity/users/" + EM_B_ID + "/pending-tasks", ADMIN).status()).isEqualTo(200);
        for (String account : new String[] {CA, DL, GL, EM_A}) {
            ApiResult read = get("/api/v1/identity/users/" + EM_B_ID + "/pending-tasks", account);
            assertThat(read.status()).as("角色 %s 读域外人员必须 404", account).isEqualTo(404);
            assertThat(read.body()).doesNotContain("员工乙");
            ApiResult write = raw("PUT", "/api/v1/identity/users/" + EM_B_ID, "{\"name\":\"越权改名\"}",
                    COOKIES.get(account));
            assertThat(write.status()).as("角色 %s 改域外人员必须 404", account).isEqualTo(404);
        }
        // 反向：员工乙取公司管理员的档案同样 404（不存在「谁都能读」的入口）
        assertThat(get("/api/v1/identity/users/" + CA_ID + "/pending-tasks", EM_B).status()).isEqualTo(404);
        assertThat(get("/api/v1/identity/users/" + CA_ID + "/positions", EM_B).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("通讯录（GET /identity/directory）：他人手机号统一 138****xxxx；本人可见完整值（PRD §5.3）")
    void directoryMasksPhone() {
        // 分公司管理员看得到域内的部门负责人/员工甲，但手机号必须是脱敏形态
        String companyAdminView = get("/api/v1/identity/directory", CA).body();
        assertThat(companyAdminView).contains("138****0102").contains("138****0104");
        assertThat(companyAdminView).doesNotContain("13800000102").doesNotContain("13800000104");

        // 本人（员工甲）看自己的手机号是完整值（本人不看自己脱敏，PRD §5.3）
        String selfView = get("/api/v1/identity/users?size=200", EM_A).body();
        assertThat(selfView).contains("13800000104");
    }

    @Test
    @DisplayName("导出（GET /identity/users/export）：仅系统管理员；其余角色 403 EXPORT_DENIED（手改 URL 无效）")
    void exportRequiresAdmin() {
        assertThat(get("/api/v1/identity/users/export", ADMIN).status()).isEqualTo(200);
        for (String account : new String[] {CA, DL, GL, EM_A}) {
            ApiResult result = get("/api/v1/identity/users/export", account);
            assertThat(result.status()).as("角色 %s 不得导出主数据", account).isEqualTo(403);
            assertThat(result.body()).contains("40305");
        }
        // 组织导出同口径；干部/岗位/角色分配三张模板导出同样仅系统管理员
        assertThat(get("/api/v1/identity/orgs/export", CA).status()).isEqualTo(403);
        assertThat(get("/api/v1/identity/org-leaders/export", DL).status()).isEqualTo(403);
        assertThat(get("/api/v1/identity/user-positions/export", EM_A).status()).isEqualTo(403);
        assertThat(get("/api/v1/identity/user-roles/export", GL).status()).isEqualTo(403);
        assertThat(get("/api/v1/admin/audit-logs/export", CA).status()).isEqualTo(403);
        assertThat(get("/api/v1/identity/org-leaders/export", ADMIN).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("导出管控（POST /authz/export-check）：非系统管理员/财务 403；金额列一律不在生效列内")
    void exportCheckRejectsForgedFieldsAndAmounts() {
        ApiResult admin = post("/api/v1/authz/export-check", ADMIN,
                "{\"target\":\"instance_list\"}");
        assertThat(admin.status()).isEqualTo(200);
        JsonNode columns = admin.json().path("data").path("effectiveColumns");
        assertThat(columns.isArray()).isTrue();
        for (JsonNode column : columns) {
            assertThat(column.asText()).as("生效导出列不得含金额列").isNotEqualTo("amount");
        }
        assertThat(admin.json().path("data").path("amountExported").asBoolean()).isFalse();
        assertThat(admin.json().path("data").path("excludedFields").toString()).contains("amount");

        // 伪造金额列 → 403（直连接口也无法绕过列白名单）
        ApiResult forged = post("/api/v1/authz/export-check", ADMIN,
                "{\"target\":\"instance_list\",\"fields\":[\"biz_no\",\"amount\"]}");
        assertThat(forged.status()).isEqualTo(403);
        assertThat(forged.body()).contains("40307");

        for (String account : new String[] {CA, DL, EM_A}) {
            assertThat(post("/api/v1/authz/export-check", account, "{\"target\":\"instance_list\"}").status())
                    .as("角色 %s 不得导出单据列表", account).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("权限树与角色列表：仅系统管理员（company_admin 可读角色列表但不能读权限树）")
    void authzAdminSurfacesAreGated() {
        assertThat(get("/api/v1/authz/permissions/tree", ADMIN).status()).isEqualTo(200);
        for (String account : new String[] {CA, DL, GL, EM_A}) {
            assertThat(get("/api/v1/authz/permissions/tree", account).status())
                    .as("角色 %s 不得读取权限树", account).isEqualTo(403);
        }
        // 直接调写接口（手改 URL 造权限）同样被拒
        assertThat(post("/api/v1/authz/permissions", EM_A,
                "{\"permType\":\"button\",\"code\":\"matrix:evil:act\",\"name\":\"越权节点\"}").status())
                .isEqualTo(403);

        assertThat(get("/api/v1/authz/roles", ADMIN).status()).isEqualTo(200);
        assertThat(get("/api/v1/authz/roles", CA).status()).isEqualTo(200);
        assertThat(get("/api/v1/authz/roles", DL).status()).isEqualTo(403);
        assertThat(get("/api/v1/authz/roles", EM_A).status()).isEqualTo(403);
        assertThat(get("/api/v1/authz/roles", GL).status()).isEqualTo(403);
    }

    @Test
    @DisplayName("导入（POST /identity/orgs/import/preview）：普通角色/部门负责人 403；分公司管理员越域行被 fail-closed 拒绝")
    void importIsGatedAndScopeChecked() {
        for (String account : new String[] {DL, GL, EM_A, EM_B}) {
            assertThat(postRaw("/api/v1/identity/orgs/import/preview", account, inScopeCsv()).status())
                    .as("角色 %s 不得调用批量导入", account).isEqualTo(403);
        }
        // 分公司管理员：本公司子树内的行 → 校验通过（ok=true）
        ApiResult inScope = postRaw("/api/v1/identity/orgs/import/preview", CA, inScopeCsv());
        assertThat(inScope.status()).isEqualTo(200);
        assertThat(inScope.json().path("data").path("ok").asBoolean()).isTrue();

        // 分公司管理员：数据域外的行（公司B）→ 整批拒绝，错误码 E-ORG-020（fail-closed）
        ApiResult outOfScope = postRaw("/api/v1/identity/orgs/import/preview", CA, outOfScopeCsv());
        assertThat(outOfScope.status()).isEqualTo(200);
        assertThat(outOfScope.json().path("data").path("ok").asBoolean()).isFalse();
        assertThat(outOfScope.body()).contains("E-ORG-020");

        // 系统管理员对同一份文件的域外行：数据域不是障碍（不再出现 E-ORG-020）
        ApiResult asAdmin = postRaw("/api/v1/identity/orgs/import/preview", ADMIN, outOfScopeCsv());
        assertThat(asAdmin.status()).isEqualTo(200);
        assertThat(asAdmin.body()).doesNotContain("E-ORG-020");
    }

    @Test
    @DisplayName("安全基座（手改 URL 直连 /admin/**）：非系统管理员一律 403；密钥本体永不下发")
    void cryptoAdminSurfacesAreGated() {
        assertThat(get("/api/v1/admin/crypto/fields", ADMIN).status()).isEqualTo(200);
        assertThat(get("/api/v1/admin/keys", ADMIN).status()).isEqualTo(200);
        assertThat(get("/api/v1/admin/keys", ADMIN).body()).contains("activeKeyId").doesNotContain("phoneKey");

        for (String account : new String[] {CA, DL, GL, EM_A, EM_B}) {
            assertThat(get("/api/v1/admin/crypto/fields", account).status()).isEqualTo(403);
            assertThat(get("/api/v1/admin/keys", account).status()).isEqualTo(403);
            assertThat(post("/api/v1/admin/crypto/phone-migrate", account, "").status()).isEqualTo(403);
            assertThat(post("/api/v1/admin/keys/rotate", account, "").status()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("未登录（无会话 Cookie）：所有受保护入口 401 —— 网关级兜底")
    void anonymousIsRejected() {
        for (String path : new String[] {"/api/v1/identity/orgs/tree", "/api/v1/identity/users",
                "/api/v1/authz/roles", "/api/v1/admin/keys", "/api/v1/identity/users/export"}) {
            ApiResult result = raw("GET", path, null, null);
            assertThat(result.status()).as("未登录访问 %s 必须 401", path).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("字段级写入闸门（POST /authz/field-policy/assert-write）：非财务角色写金额 → 403 40306")
    void amountWriteIsRejectedOverHttp() {
        String payload = "{\"formType\":\"contract\",\"state\":\"draft\",\"isInitiator\":true,"
                + "\"payload\":{\"counterparty\":\"乙公司\",\"amount\":\"100000.00\"}}";

        ApiResult employee = post("/api/v1/authz/field-policy/assert-write", EM_A, payload);
        assertThat(employee.status()).isEqualTo(403);
        assertThat(employee.body()).contains("40306").contains("amount");

        // 系统管理员：同一请求通过（金额可写）
        ApiResult admin = post("/api/v1/authz/field-policy/assert-write", ADMIN, payload);
        assertThat(admin.status()).isEqualTo(200);
        assertThat(admin.json().path("data").path("amountWritable").asBoolean()).isTrue();

        // 联系方式脱敏策略：脱敏形态与「谁可见完整值」的唯一口径
        ApiResult contact = get("/api/v1/authz/field-policy/contact", EM_A);
        assertThat(contact.status()).isEqualTo(200);
        assertThat(contact.body()).contains("138****8888").contains("AES-256-GCM");
    }

    // ================================================================ HTTP 工具

    /** 取人员列表里的账号集合（数据域过滤后的可观测形态）。 */
    private java.util.List<String> accountList(String account) {
        JsonNode records = get("/api/v1/identity/users?size=200", account).json()
                .path("data").path("records");
        java.util.List<String> accounts = new java.util.ArrayList<>();
        for (JsonNode row : records) {
            accounts.add(row.path("account").asText());
        }
        return accounts;
    }

    private ApiResult get(String path, String account) {
        return raw("GET", path, null, COOKIES.get(account));
    }

    private ApiResult post(String path, String account, String json) {
        return raw("POST", path, json, COOKIES.get(account));
    }

    /** 以 CSV 原文作为请求体（导入接口支持「裸 CSV」形态，便于脚本化）。 */
    private ApiResult postRaw(String path, String account, byte[] csv) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "text/csv")
                .header("Cookie", COOKIES.get(account))
                .POST(HttpRequest.BodyPublishers.ofByteArray(csv))
                .build();
        return send(request);
    }

    private ApiResult raw(String method, String path, String json, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("POST".equals(method) || "PUT".equals(method)) {
            builder.header("Content-Type", "application/json");
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json,
                    StandardCharsets.UTF_8);
            if ("PUT".equals(method)) {
                builder.PUT(body);
            } else {
                builder.POST(body);
            }
        } else {
            builder.GET();
        }
        return send(builder.build());
    }

    private static ApiResult send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(
                    StandardCharsets.UTF_8));
            JsonNode json = null;
            try {
                json = MAPPER.readTree(response.body());
            } catch (Exception ignored) {
                json = MAPPER.createObjectNode();
            }
            return new ApiResult(response.statusCode(), response.body(), json);
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP 调用失败：" + request.uri(), ex);
        }
    }

    private static String login(String account, String password) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("account", account, "password", password));
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assumeTrue(response.statusCode() == 200,
                "登录失败（" + account + "）：HTTP " + response.statusCode() + " " + response.body());
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("OA_SESSION="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("登录响应未返回会话 Cookie"));
    }

    private record ApiResult(int status, String body, JsonNode json) {
    }

    // ================================================================ 夹具与环境

    private static byte[] inScopeCsv() {
        // 公司A 子树内的新科室（分公司管理员数据域内，父子路径自洽）
        String body = String.join(",", com.oa.admin.bulk.ImportKind.ORG.columns()) + "\r\n"
                + "集团/公司A/部门1/矩阵科室,矩阵科室,科室,集团/公司A/部门1,启用,越权矩阵夹具\r\n";
        return withBom(body);
    }

    private static byte[] outOfScopeCsv() {
        // 公司B 子树（分公司管理员数据域外）→ 必须 fail-closed 拒绝整批
        String body = String.join(",", com.oa.admin.bulk.ImportKind.ORG.columns()) + "\r\n"
                + "集团/公司B/部门3/矩阵科室,矩阵科室,科室,集团/公司B/部门3,启用,越权矩阵夹具\r\n";
        return withBom(body);
    }

    private static byte[] withBom(String body) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[raw.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(raw, 0, result, 3, raw.length);
        return result;
    }

    /** 执行夹具 SQL，并把夹具人员的口令改成可登录状态（口令随每次运行随机生成，不入库文件）。 */
    private static void applyFixtureAndCredentials() throws Exception {
        String fixturePath = config("oa.it.fixture", "OA_IT_FIXTURE",
                Path.of("..", ".cache", "oa-authz-matrix-fixture.sql").toString());
        Path path = Path.of(fixturePath);
        assumeTrue(Files.isReadable(path), "未找到夹具 SQL：" + path.toAbsolutePath());
        String script = Files.readString(path, StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement statement = connection.createStatement()) {
            for (String raw : script.split(";")) {
                String sql = stripComments(raw);
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
            // 夹具人员此前从未登录 → last_login_at 置位，避免命中「首登强制改密」闸门
            statement.execute("UPDATE sys_user SET last_login_at = NOW() WHERE account LIKE 'mtx\\_%'");
        }
    }

    /** 把某夹具账号的口令设为 BCrypt 哈希（与生产同一套 PasswordService 编码）。 */
    private static void setPassword(String account, String password) throws Exception {
        PasswordService passwordService = new PasswordService(new com.oa.common.config.OaProperties());
        try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE sys_user SET password_hash = ? WHERE account = ?")) {
            statement.setString(1, passwordService.encode(password));
            statement.setString(2, account);
            statement.executeUpdate();
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

    private static boolean healthUp() {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("UP");
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String randomDigits() {
        return String.valueOf(100000 + new java.security.SecureRandom().nextInt(900000));
    }

    private static String config(String property, String environment, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
