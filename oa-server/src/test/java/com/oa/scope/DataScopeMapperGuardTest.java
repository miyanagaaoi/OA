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

    /**
     * 受控表对应的 Mapper：必须显式声明全部语句、不继承 BaseMapper。
     *
     * <p>{@code com.oa.authz.infra.SysUserRoleMapper} 与 {@code com.oa.authz.infra.AuthzOrgLookupMapper}
     * 于阶段 1.4 加入：前者承担「某用户的角色」这一**用户数据读暴露**（JOIN 受控表 {@code sys_user}），
     * 后者查询受控表 {@code sys_org}；两者都必须带 {@code @dataScope} 标记（见
     * {@code com.oa.authz.scope.AuthzDataScopeMarkerTest}），因此也必须留在 BaseMapper 之外。
     */
    private static final List<String> SCOPED_MAPPERS = List.of(
            "com.oa.identity.infra.SysUserMapper",
            "com.oa.identity.infra.SysOrgMapper",
            "com.oa.identity.infra.SysOrgLeaderMapper",
            "com.oa.identity.infra.SysUserPositionMapper",
            "com.oa.authz.infra.SysUserRoleMapper",
            "com.oa.authz.infra.AuthzOrgLookupMapper",
            // 阶段 2a.3：在途/待办真实查询（flow_instance / flow_task 受控表）
            "com.oa.identity.infra.InFlightQueryMapper",
            // 阶段 2a.3：审批人解析目录（sys_org / sys_org_leader / sys_user）
            "com.oa.workflow.approver.infra.ApproverDirectoryMapper",
            // 阶段 2a.3：流程实例与表单数据（flow_instance / form_data）
            "com.oa.workflow.approver.infra.FlowInstanceMapper",
            // 阶段 2a.2：配置数据（flow_template / flow_node **不是**受控表，但保持同一纪律：
            // 不继承 BaseMapper、只走显式语句，避免后人误用 MP 注入的无标记语句读到业务表）
            "com.oa.workflow.definition.infra.FlowTemplateMapper",
            "com.oa.workflow.definition.infra.FlowNodeMapper",
            // 阶段 2a.4：运行时状态机（flow_task / flow_routing 是受控表；
            // flow_node_instance 不是受控表但同守「只用显式语句」的纪律）
            "com.oa.workflow.runtime.infra.FlowNodeInstanceMapper",
            "com.oa.workflow.runtime.infra.FlowTaskMapper",
            "com.oa.workflow.runtime.infra.FlowRuntimeMapper",
            // 阶段 2b.1：表单数据读写（form_data 是受控表；每条 SELECT 恰好 1 个 @dataScope 标记）
            "com.oa.form.infra.FormDataMapper",
            // 阶段 2b.4：数据字典（sys_dict_item **不是**受控表 —— 配置数据不织入数据域，
            // 但保持同一纪律：不继承 BaseMapper、只走显式语句）
            "com.oa.form.dict.infra.SysDictItemMapper",
            // 阶段 2b.7：附件元数据（flow_attachment 由 Attachment 上的
            // @DataScopeTable(kind=NONE) 登记为受控表 → 每条 SELECT 恰好 1 个标记，
            // 过滤主体恒为 flow_instance）
            "com.oa.form.attachment.infra.AttachmentMapper");

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
            // 列表内的**整行注释**（用来说明豁免理由）必须跳过而不是当作列表结束 ——
            // 否则解析会提前 break，后面的条目全部漏检（守卫静默失效）
            if (line.trim().startsWith("#")) {
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

    /**
     * 阶段 1 收口：账号/工号**唯一性判重**（系统口径）是唯一新增的「业务表窄豁免」，必须两处同步且保持窄。
     *
     * <p>背景（被判重口径缺陷逼出来的豁免）：唯一性是全局约束（{@code uk_sys_user_account}），
     * 与调用人的数据域无关。判重语句若织入数据域片段，分公司管理员对**域外**账号/工号判重得到 0，
     * 重复只能由数据库唯一键在 INSERT 时兜住（文案泛化、不带 import-spec 错误码），
     * 而 {@code sys_user.employee_no} **没有**库唯一键 → 域外重复工号会被静默写入。
     *
     * <p>本测试把「豁免是窄豁免」变成机器可验证的硬约束：
     * <ol>
     *   <li>两条语句必须在 {@code application.yml} **与** {@code OaProperties} 默认值里都存在
     *       （缺一边：配置覆盖时会退回默认值或反之，运行期 40303 直接让增改人员不可用）；</li>
     *   <li>豁免条目必须是**完整语句 id**，不得退化成 {@code ...SysUserMapper.countBy} 之类的前缀
     *       （前缀匹配会顺带放行日后新增的同类语句）；</li>
     *   <li>两条语句只读计数（SQL 里必须有 {@code COUNT(1)}）且不得返回行数据列 —— 这是「不构成
     *       数据域读取旁路」的可验证形态；数据域**读取**限制由 {@code selectUserById} 等语句承担，一行未动。</li>
     * </ol>
     */
    @Test
    @DisplayName("系统口径判重语句是窄豁免：yml ∪ 默认值两处都在、完整语句 id、SQL 只读计数")
    void systemScopeDedupStatementsAreNarrowlyExempted() throws Exception {
        List<String> accountSystem = List.of("com.oa.identity.infra.SysUserMapper.countByAccountSystem");
        List<String> employeeNoSystem = List.of("com.oa.identity.infra.SysUserMapper.countByEmployeeNoSystem");
        List<String> both = new ArrayList<>(accountSystem);
        both.addAll(employeeNoSystem);

        // ① 两处同步（分别断言，避免「默认值兜底」掩盖 application.yml 漏配）
        assertThat(ymlExemptStatementIds()).as("application.yml 必须显式列出系统口径判重语句")
                .containsAll(both);
        assertThat(new OaProperties().getScope().getExemptStatementIds())
                .as("OaProperties 默认豁免清单必须同步（只改 yml 时，配置回退仍会让判重被 40303 拒绝）")
                .containsAll(both);

        // ② 完整语句 id（不是前缀）
        List<String> exempt = effectiveExemptStatementIds();
        for (String entry : exempt) {
            for (String suffix : List.of("SysUserMapper.countBy", "SysUserMapper.count")) {
                assertThat(entry.endsWith(suffix))
                        .as("豁免条目 %s 是前缀形态（%s）：会顺带放行日后新增的同类语句，必须写完整语句 id",
                                entry, suffix)
                        .isFalse();
            }
        }

        // ③ 两条语句的 SQL 只做计数，不返回行数据 → 窄豁免，不是数据域读取旁路
        String xml;
        try (InputStream in = new ClassPathResource("mapper/identity/SysUserMapper.xml").getInputStream()) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String statementId : both) {
            String id = statementId.substring(statementId.lastIndexOf('.') + 1);
            Pattern block = Pattern.compile("<select id=\"" + id + "\"[^>]*>(.*?)</select>", Pattern.DOTALL);
            Matcher matcher = block.matcher(xml);
            assertThat(matcher.find()).as("SysUserMapper.xml 必须有 #%s", id).isTrue();
            String sql = matcher.group(1);
            assertThat(sql).as("%s 必须是计数语句（COUNT(1)）", id).contains("COUNT(1)");
            assertThat(sql).as("%s 不得带 @dataScope 标记：判重必须全库口径", id).doesNotContain("@dataScope(");
            assertThat(sql).as("%s 只允许查这两列，不得返回行数据", id)
                    .doesNotContain("SELECT u.*").doesNotContain("SELECT *");
        }
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
