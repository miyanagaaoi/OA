package com.oa.platform.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * <b>入库夹具不得携带「可登录的已知口令哈希」（安全边界回归网，oa-deploy/fixtures/README.md §6）</b>。
 *
 * <h2>守的是哪个坑</h2>
 * <p>上一轮为了跑「真实审批人身份的端到端实测」，把矩阵账号（{@code mtx_*}）的口令哈希**在运行期**
 * 重置成了 dev 口令（口令值本身已在 git 历史里）。一旦这类哈希被写回<b>入库</b>夹具，
 * 任何拿到仓库的人都能用公开口令登录本机/联调环境里的 {@code mtx_*} 与 {@code matrix_admin}。
 *
 * <h2>本测试的两道断言</h2>
 * <ol>
 *   <li><b>硬闸门（默认跑）</b>：{@code oa-deploy/fixtures/*.sql} 里出现的**每一个** BCrypt 字面量
 *       都必须逐字等于**规范占位哈希** {@code $2a$12$0…（53 个 0）}（与 {@code matrix_admin} 同款做法）。
 *       任何「看起来可用」的哈希（结构合法、salt 随机）都会让本用例变红 —— 因为无从证明它不是
 *       某个已知口令的哈希，而<b>无从证明即不得入库</b>；</li>
 *   <li><b>动态证明（提供候选口令时跑）</b>：把候选口令通过
 *       {@code -Doa.it.known.passwords=...}（或环境变量 {@code OA_IT_KNOWN_PASSWORDS}，逗号分隔）
 *       注入，断言 {@code BCryptPasswordEncoder.matches(口令, 夹具哈希)} **全部为 false** ——
 *       口令本身**不写进任何入库文件**，所以这里只能由运行期注入。
 *       （{@code AuthzMatrixHttpTest} 的 {@code applyFixtureAndCredentials} 也是同一思路：
 *       夹具只给占位哈希，可登录口令由测试在运行期随机生成后 {@code UPDATE} 进去。）</li>
 * </ol>
 */
class FixtureCredentialSafetyTest {

    /**
     * 规范占位哈希（{@code matrix_admin} 与 {@code mtx_*} 同款）：{@code $2a$12$} + **52** 个 {@code 0}，
     * 合计 **59** 字符 —— BCrypt 密文恰好是 **60** 字符（{@code $2a$12$} + 22 位 salt + 31 位 digest），
     * 因此这个字面量**结构上就不是 BCrypt 密文**：{@code BCryptPasswordEncoder#matches} 会打印
     * 「Encoded password does not look like BCrypt」并**对任何口令一律返回 false**。
     */
    private static final String PLACEHOLDER = "$2a$12$" + "0".repeat(52);

    /** BCrypt 字面量的形状（{@code $2a$12$} + 至少 40 个 base64 字符；不要求 60，才能识别出这种占位形态）。 */
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d\\d\\$[./A-Za-z0-9]{40,}");

    /** BCrypt 密文的规范长度：短于它 = 结构非法 = 无法用于校验任何口令。 */
    private static final int BCRYPT_LENGTH = 60;

    private static final String PROPERTY = "oa.it.known.passwords";

    private static final String ENVIRONMENT = "OA_IT_KNOWN_PASSWORDS";

    @Test
    @DisplayName("硬闸门｜入库夹具里的每个 BCrypt 哈希都必须是规范占位哈希（不可登录）")
    void everyFixtureHashIsTheCanonicalPlaceholder() throws IOException {
        List<Path> files = fixtureSqlFiles();
        assertThat(files).as("入库夹具目录必须存在且含 SQL").isNotEmpty();

        int seen = 0;
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = BCRYPT.matcher(text);
            while (matcher.find()) {
                seen++;
                assertThat(matcher.group())
                        .as("%s 携带了非占位哈希 —— 无从证明它不是某个已知口令的哈希，不得入库"
                                + "（需要登录时请在运行期 UPDATE，见 AuthzMatrixHttpTest#applyFixtureAndCredentials）",
                                file.getFileName())
                        .isEqualTo(PLACEHOLDER);
            }
        }
        assertThat(seen)
                .as("夹具仍应显式写出占位哈希（本断言防止「整段被删」让硬闸门静默失效）")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("结构证明（默认跑）｜占位哈希不是合法 BCrypt 密文（不是 60 字符）⇒ 对任何口令 matches 恒为 false")
    void placeholderIsStructurallyInvalidSoItCanNeverMatch() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        assertThat(PLACEHOLDER.length())
                .as("BCrypt 密文恒为 60 字符；占位哈希刻意短一位，使其结构上无法参与校验")
                .isNotEqualTo(BCRYPT_LENGTH)
                .isEqualTo(59);

        // 「对任何口令不可用」的形态：空串 / 弱口令样本 / **随机口令** 一律 false
        String random = java.util.UUID.randomUUID().toString().replace("-", "");
        for (String probe : new String[] {"", " ", "0", "000000", "password", "123456", "admin", random}) {
            assertThat(encoder.matches(probe, PLACEHOLDER))
                    .as("占位哈希对任意口令都必须 matches=false（probe 长度 %d）", probe.length())
                    .isFalse();
        }

        // 反证编码器本身是好的：真实哈希能命中自己的口令（否则上面的 false 可能只是「坏环境」）
        assertThat(encoder.matches("sanity-probe", encoder.encode("sanity-probe")))
                .as("编码器自校验：真实 BCrypt 哈希必须 matches=true，否则本用例的 false 无意义")
                .isTrue();
    }

    @Test
    @DisplayName("动态证明｜（提供 -Doa.it.known.passwords 时）夹具哈希不能用于登录任何已知口令")
    void fixtureHashesCannotLogInWithAKnownPassword() throws IOException {
        List<String> candidates = knownPasswords();
        assumeTrue(!candidates.isEmpty(),
                "未提供 -Doa.it.known.passwords / OA_IT_KNOWN_PASSWORDS，跳过动态证明"
                        + "（口令不得写进入库文件，只能运行期注入）");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        for (Path file : fixtureSqlFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = BCRYPT.matcher(text);
            while (matcher.find()) {
                String hash = matcher.group();
                for (int i = 0; i < candidates.size(); i++) {
                    assertThat(encoder.matches(candidates.get(i), hash))
                            .as("%s 的哈希可用于第 %d 个候选口令登录 —— 必须改为占位哈希",
                                    file.getFileName(), i + 1)
                            .isFalse();
                }
            }
        }
    }

    // ================================================================ 定位与读取

    /** 入库夹具目录（兼容「Maven 工作目录 = oa-server/」与「= 仓库根」两种启动方式）。 */
    private static List<Path> fixtureSqlFiles() throws IOException {
        List<Path> dirs = List.of(
                Path.of("..", "oa-deploy", "fixtures"),
                Path.of("oa-deploy", "fixtures"),
                Path.of("..", "..", "oa-deploy", "fixtures"));
        for (Path dir : dirs) {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.list(dir)) {
                    List<Path> files = new ArrayList<>(stream
                            .filter(path -> path.getFileName().toString().endsWith(".sql"))
                            .sorted()
                            .toList());
                    if (!files.isEmpty()) {
                        return files;
                    }
                }
            }
        }
        return List.of();
    }

    private static List<String> knownPasswords() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(ENVIRONMENT);
        }
        List<String> values = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String item : raw.split(",")) {
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }
}
