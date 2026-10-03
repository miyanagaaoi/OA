package com.oa.authz.matrix;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 越权矩阵测试的**夹具定位器**（{@link AuthzMatrixHttpTest} 与 {@link AuthzMatrixMySqlIntegrationTest} 共用）。
 *
 * <h2>默认路径在仓库内</h2>
 * 默认夹具是**已入库**的 {@code oa-deploy/fixtures/40-authz-matrix.sql}（见
 * {@code oa-deploy/fixtures/README.md}）。旧默认 {@code ../.cache/oa-authz-matrix-fixture.sql}
 * 位于 {@code .gitignore} 覆盖的目录里，新环境 clone 后必然不存在 —— 那会让两个矩阵测试
 * 静默跳过（测试全绿但覆盖为零的「假绿」）。
 *
 * <p>覆盖方式（优先级从高到低）：
 * <ol>
 *   <li>系统属性 {@code -Doa.it.fixture=<路径>}</li>
 *   <li>环境变量 {@code OA_IT_FIXTURE=<路径>}</li>
 *   <li>仓库内默认路径（相对 Maven 的 {@code ${basedir}} = {@code oa-server/}，故用 {@code ..}）</li>
 * </ol>
 *
 * <h2>语义：缺夹具是**失败**，不是跳过</h2>
 * <ul>
 *   <li><b>完全没提供 DB 环境</b>（{@code -Doa.it.db.url} / {@code OA_IT_DB_URL} 为空）：
 *       {@link #resolve(String)} 返回 {@code null}，调用方按既有口径 {@code assumeTrue} 跳过 —— 合法；</li>
 *   <li><b>已提供 DB 环境却找不到夹具</b>：抛 {@link IllegalStateException}
 *       （JUnit 报 <b>失败</b>而不是 skip），并给出可直接复制的 {@code -Doa.it.fixture=…} 提示。</li>
 * </ul>
 */
final class AuthzMatrixFixture {

    /** 仓库内默认夹具（相对仓库根；已入库、可被新环境 clone 到）。 */
    static final String REPO_RELATIVE = "oa-deploy/fixtures/40-authz-matrix.sql";

    private static final String PROPERTY = "oa.it.fixture";

    private static final String ENVIRONMENT = "OA_IT_FIXTURE";

    private AuthzMatrixFixture() {
    }

    /**
     * 定位夹具 SQL。
     *
     * @param dbUrl 集成测试的数据库连接串（{@code -Doa.it.db.url} / {@code OA_IT_DB_URL}）；
     *              为空 = 完全没提供 DB 环境 → 返回 {@code null}，调用方跳过
     * @return 可读的夹具文件路径；{@code dbUrl} 为空时返回 {@code null}
     * @throws IllegalStateException 已提供 DB 环境（{@code dbUrl} 非空）却找不到夹具文件
     */
    static Path resolve(String dbUrl) {
        boolean dbEnvProvided = dbUrl != null && !dbUrl.isBlank();

        String configured = config();
        if (!configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isReadable(path)) {
                return path;
            }
            if (!dbEnvProvided) {
                return null;
            }
            throw missingFixture("显式指定的夹具不存在：" + path.toAbsolutePath()
                    + "\n提示：-Doa.it.fixture / OA_IT_FIXTURE 指向的路径请写对（可写绝对路径）");
        }

        List<Path> tried = new ArrayList<>();
        for (Path candidate : defaultCandidates()) {
            tried.add(candidate.toAbsolutePath());
            if (Files.isReadable(candidate)) {
                return candidate;
            }
        }
        if (!dbEnvProvided) {
            return null;
        }
        throw missingFixture("仓库内默认夹具不存在，已尝试：\n  " + String.join("\n  ",
                tried.stream().map(Path::toString).toArray(String[]::new)));
    }

    /** 默认夹具候选（兼容「Maven 工作目录 = oa-server/」与「= 仓库根」两种启动方式）。 */
    private static List<Path> defaultCandidates() {
        return List.of(
                Path.of("..", "oa-deploy", "fixtures", "40-authz-matrix.sql"),
                Path.of("oa-deploy", "fixtures", "40-authz-matrix.sql"),
                Path.of("..", "..", "oa-deploy", "fixtures", "40-authz-matrix.sql"));
    }

    private static IllegalStateException missingFixture(String detail) {
        return new IllegalStateException("已提供集成测试环境（-Doa.it.db.* / OA_IT_DB_*）但找不到夹具 SQL。\n"
                + detail + "\n"
                + "修复：用 -Doa.it.fixture=" + REPO_RELATIVE.replace('/', '\\') + "（或环境变量 OA_IT_FIXTURE）"
                + "指向已入库的夹具。\n"
                + "说明：夹具缺失时**不允许静默跳过** —— 那会让测试全绿而覆盖为零。"
                + "只有完全没提供 DB 环境时才允许 skip。");
    }

    private static String config() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENVIRONMENT);
        }
        return value == null ? "" : value;
    }
}
