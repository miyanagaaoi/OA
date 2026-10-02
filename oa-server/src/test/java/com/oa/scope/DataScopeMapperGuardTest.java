package com.oa.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.common.config.OaProperties;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 受控表 Mapper 与数据域豁免清单的**编译期/静态守卫**（纯逻辑，不依赖 DB / Redis / Spring 容器）。
 *
 * <p>回归背景（已修复的缺陷）：
 * <ol>
 *   <li>{@code SysUserMapper} 曾继承 MyBatis-Plus 的 {@link BaseMapper}，于是
 *       {@code AuthService} 用上了 MP 注入的**无 {@code @dataScope} 标记**的
 *       {@code selectById}/{@code updateById}：改密时「读自己」必然被
 *       {@code DataScopeInterceptor} fail-closed 拒绝（40303 DATA_SCOPE_MISSING），改密功能整体不可用；</li>
 *   <li>豁免清单里曾有一条 {@code ...SysUserMapper.selectOne}：全库无人调用，却把 MP 注入的
 *       {@code selectOne} 整体放行 —— 属于「宽豁免」数据域旁路，且随接口方法增删随时扩大攻击面。</li>
 * </ol>
 *
 * <p>本测试把这两条都变成**机器可验证的硬约束**：
 * <ul>
 *   <li>受控表对应的 Mapper 接口不得继承 {@link BaseMapper}（唯一的例外是非业务数据表
 *       {@code sys_user_session}，它整体豁免，单独列出并断言「确有豁免条目」）；</li>
 *   <li>豁免清单（{@code application.yml} 实际配置 ∪ {@link OaProperties} 默认值）中
 *       不得出现以 {@code .selectOne/.selectList/.selectById/.selectPage/.selectCount} 结尾的条目；</li>
 *   <li>{@code SysUserMapper} 不得再声明 MP 的通用读方法名。</li>
 * </ul>
 */
class DataScopeMapperGuardTest {

    /** 受控表对应的 Mapper：必须显式声明全部语句、不继承 BaseMapper。 */
    private static final List<String> SCOPED_MAPPERS = List.of(
            "com.oa.identity.infra.SysUserMapper",
            "com.oa.identity.infra.SysOrgMapper",
            "com.oa.identity.infra.SysOrgLeaderMapper",
            "com.oa.identity.infra.SysUserPositionMapper");

    /** 非业务数据表（会话注册表）：整体豁免，是 BaseMapper 的唯一合法持有者。 */
    private static final String EXEMPT_MAPPER = "com.oa.identity.infra.SysUserSessionMapper";

    private static final String EXEMPT_PREFIX = "com.oa.identity.infra.SysUserSessionMapper";

    /** MyBatis-Plus 注入的通用读方法名：受控表 Mapper 一律不得出现。 */
    private static final Set<String> MP_READ_METHODS = Set.of(
            "selectById", "selectList", "selectOne", "selectPage", "selectCount", "selectBatchIds");

    /** 「宽豁免」特征后缀：等价于给受控表整体开后门。 */
    private static final List<String> BROAD_SUFFIXES = List.of(
            ".selectOne", ".selectList", ".selectById", ".selectPage", ".selectCount");

    private static Class<?> load(String className) throws Exception {
        return Class.forName(className, false, DataScopeMapperGuardTest.class.getClassLoader());
    }

    /** 读取 {@code application.yml} 中 {@code oa.scope.exempt-statement-ids} 的实际配置值。 */
    private static List<String> ymlExemptStatementIds() throws Exception {
        String yaml;
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Pattern key = Pattern.compile("^(\\s*)exempt-statement-ids\\s*:\\s*$");
        Pattern item = Pattern.compile("^\\s*-\\s+(\\S+)\\s*$");
        List<String> ids = new ArrayList<>();
        boolean inBlock = false;
        for (String raw : yaml.split("\\R")) {
            String line = raw.replaceAll("\\s+#.*$", "");
            if (!inBlock) {
                Matcher matcher = key.matcher(line);
                if (matcher.find()) {
                    inBlock = true;
                }
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            Matcher itemMatcher = item.matcher(line);
            if (itemMatcher.matches()) {
                ids.add(itemMatcher.group(1));
                continue;
            }
            // 列表结束（遇到下一个配置键，或缩进回到 key 同级）
            break;
        }
        assertThat(ids).as("application.yml 中 oa.scope.exempt-statement-ids 必须能被解析出来").isNotEmpty();
        return ids;
    }

    /** 实际生效的豁免清单 = yml 配置 ∪ {@link OaProperties} 默认值（去重，保持顺序）。 */
    private static List<String> effectiveExemptStatementIds() throws Exception {
        Set<String> merged = new LinkedHashSet<>(ymlExemptStatementIds());
        List<String> defaults = new OaProperties().getScope().getExemptStatementIds();
        assertThat(defaults).as("OaProperties 默认豁免清单不应为空").isNotEmpty();
        merged.addAll(defaults);
        List<String> result = new ArrayList<>(merged);
        assertThat(result).isNotEmpty();
        return result;
    }

    @Test
    @DisplayName("受控表 Mapper 都不继承 BaseMapper（BaseMapper 注入的读语句不带标记，会被拦截器拒绝）")
    void scopedMappersDoNotExtendBaseMapper() throws Exception {
        for (String className : SCOPED_MAPPERS) {
            Class<?> clazz = load(className);
            assertThat(clazz.isInterface()).as("%s 应是 Mapper 接口", className).isTrue();
            assertThat(BaseMapper.class.isAssignableFrom(clazz))
                    .as("%s 不得继承 MyBatis-Plus BaseMapper：其注入语句无 @dataScope 标记，"
                            + "在已认证上下文会被 fail-closed 拒绝（40303）", className)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("豁免的会话 Mapper 仍继承 BaseMapper，且必须在豁免清单里有整体豁免条目")
    void exemptSessionMapperIsExplicitlyExempted() throws Exception {
        Class<?> sessionMapper = load(EXEMPT_MAPPER);
        // 非业务数据表（会话/设备注册表）：刻意保留 BaseMapper，前提是「整体豁免」条目仍在
        assertThat(BaseMapper.class.isAssignableFrom(sessionMapper))
                .as("%s 是唯一保留 BaseMapper 的 Mapper（非业务数据、整体豁免）", EXEMPT_MAPPER)
                .isTrue();

        List<String> exempt = effectiveExemptStatementIds();
        assertThat(exempt).as("SysUserSessionMapper 必须有整体豁免条目（否则其 selectOne/updateById 会被拦截）")
                .anyMatch(entry -> entry.startsWith(EXEMPT_PREFIX));
    }

    @Test
    @DisplayName("豁免清单不得含通用读方法（.selectOne/.selectList/.selectById/.selectPage/.selectCount 一律禁止）")
    void exemptListMustNotContainBroadReadMethods() throws Exception {
        // ① application.yml 实际配置（单独断言，避免「默认值兜底」掩盖配置里的宽豁免）
        List<String> configured = ymlExemptStatementIds();
        assertThat(configured).doesNotContain("com.oa.identity.infra.SysUserMapper.selectOne");
        for (String entry : configured) {
            for (String suffix : BROAD_SUFFIXES) {
                assertThat(entry.endsWith(suffix))
                        .as("application.yml 豁免条目 %s 以 %s 结尾 —— 等价于给受控表开通用的无标记读后门，必须删除",
                                entry, suffix)
                        .isFalse();
            }
        }

        // ② 合并 OaProperties 默认值后的实际生效清单
        List<String> exempt = effectiveExemptStatementIds();
        for (String entry : exempt) {
            for (String suffix : BROAD_SUFFIXES) {
                assertThat(entry.endsWith(suffix))
                        .as("豁免条目 %s 以 %s 结尾 —— 等价于给受控表开通用的无标记读后门，必须删除", entry, suffix)
                        .isFalse();
            }
        }
        // 保留项自检：认证前查询（selectByAccount）与非业务数据表仍在豁免内
        assertThat(exempt).contains("com.oa.identity.infra.SysUserMapper.selectByAccount");
        assertThat(exempt).contains("com.oa.identity.infra.SysLoginLogMapper");
        assertThat(exempt).contains("com.oa.authz.infra.DataScopeMapper");
    }

    @Test
    @DisplayName("SysUserMapper 不再声明 MP 通用读/写方法，且显式声明事务用的写语句")
    void sysUserMapperDeclaresExplicitStatementsOnly() throws Exception {
        Class<?> mapper = load("com.oa.identity.infra.SysUserMapper");
        Set<String> names = new LinkedHashSet<>();
        for (Method method : mapper.getDeclaredMethods()) {
            names.add(method.getName());
        }
        assertThat(names).as("SysUserMapper 不得再出现 MP 注入的通用方法名").doesNotContainAnyElementsOf(MP_READ_METHODS);
        assertThat(names).as("App 的写入必须走显式语句（AuthService/UserService 依赖）")
                .contains("insertUser", "touchLastLoginAt", "updatePasswordHash", "updateUserProfile",
                        "updateUserStatus", "selectUserById", "selectByAccount");
    }
}
