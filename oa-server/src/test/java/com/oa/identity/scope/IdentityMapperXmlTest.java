package com.oa.identity.scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.oa.common.config.OaProperties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 身份领域全部 Mapper XML 的**静态合规校验**（无 DB / 无 Spring 容器）：
 *
 * <ol>
 *   <li>4 个 XML 能被 MyBatis-Plus 正常解析（命名空间、resultMap、SQL 语法结构无笔误）；</li>
 *   <li>接口声明的方法在 XML 中都有对应语句（防止「接口加了方法、XML 忘了写」在运行期才炸）；</li>
 *   <li>**每一条 SELECT 恰好带 1 个 {@code @dataScope} 标记** —— 这是受控表 fail-closed 的硬要求
 *       （0 个 → 运行期 40303 被拒；≥2 个 → 拦截器只替换第一个，第二个会残留成裸注释）；</li>
 *   <li>标记里的 {@code table=} 必须与本文件负责的受控表一致
 *       （唯一例外：{@code SysOrgLeaderMapper.selectCandidates} 的过滤主体是 {@code sys_user}，
 *       因为候选人是「用户类」数据，见该文件头注释）。</li>
 * </ol>
 */
class IdentityMapperXmlTest {

    private static final String DIR = "mapper/identity/";
    private static final Pattern TABLE_ATTR = Pattern.compile("table\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ID_ATTR = Pattern.compile("id\\s*=\\s*\"([A-Za-z0-9_]+)\"");

    /** 资源 → 期望存在的语句 id。 */
    private static final Map<String, List<String>> STATEMENTS = new LinkedHashMap<>();

    static {
        STATEMENTS.put(DIR + "SysOrgMapper.xml", List.of(
                "selectAll", "selectById", "selectByIds", "selectRoots",
                "selectSubtree", "search", "countByType", "countChildren", "countSiblingName",
                "insertOrg", "updateOrg", "updateStatus", "updatePathAndDepth", "updateParent", "updateLeaderId"));
        STATEMENTS.put(DIR + "SysUserMapper.xml", List.of(
                "selectDirectory", "selectUserPage", "selectUserById", "selectDirectoryUsers",
                "countByEmployeeNo", "countByAccount",
                // 阶段 1 收口：账号/工号唯一性判重（**系统口径**，故意不带 @dataScope 标记 → 见下方豁免清单断言）
                "countByAccountSystem", "countByEmployeeNoSystem",
                "updateUserProfile", "updateUserStatus",
                // 契约补齐（施工要求第 4/6 条）：子树在职人数 + 主数据导出（仅系统管理员，不分页）
                "countActiveByOrgPath", "selectForExport",
                // 受控表 Mapper 不继承 BaseMapper：原 MP 注入的写语句改由 XML 显式声明
                "insertUser", "touchLastLoginAt", "updatePasswordHash"));
        STATEMENTS.put(DIR + "SysOrgLeaderMapper.xml", List.of(
                "selectByOrgId", "selectRowById", "selectEntityById", "selectByUserId", "selectGroupLines",
                "selectBindings", "selectBinding", "selectCandidates", "countByUserId",
                // 契约补齐（施工要求第 10 条）：一次批量查询聚合「已设正职」的组织
                "selectPrimaryOrgIds",
                "insertLeader", "updateLeader", "deleteById"));
        STATEMENTS.put(DIR + "SysUserPositionMapper.xml", List.of(
                "selectRowsByUserId", "selectByUserId", "selectById", "selectByUserAndOrg", "countByOrgId",
                "insertPosition", "updatePrimary", "updatePosition", "deleteById"));
    }

    /**
     * 受控表 SELECT 允许「0 个标记」的**唯一窄豁免**清单（阶段 1 收口）：
     * 账号/工号**唯一性判重**（系统口径）。
     *
     * <p>为什么必须豁免：唯一性是全局约束（{@code uk_sys_user_account}），与调用人的数据域无关；
     * 判重语句一旦织入数据域片段，域外账号/工号判重就会得到 0 —— 重复只能由数据库唯一键兜住
     * （工号更无库唯一键，会被静默写入）。两条语句只返回 {@code COUNT}、不返回行数据，
     * 因此是**窄豁免**而非数据域读取旁路（读取限制见 {@code selectUserById/selectUserPage/...}，
     * 一行未动）。豁免条目必须在 {@code application.yml} 与 {@code OaProperties} 默认值**两处**同时存在，
     * 否则运行期会被 40303 fail-closed 拒绝。
     */
    private static final Map<String, String> SYSTEM_SCOPE_EXEMPT_SELECTS = Map.of(
            "countByAccountSystem", "com.oa.identity.infra.SysUserMapper.countByAccountSystem",
            "countByEmployeeNoSystem", "com.oa.identity.infra.SysUserMapper.countByEmployeeNoSystem");

    private static String read(String resource) throws Exception {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 取出所有 {@code <select>} 块（跳过文件头注释与 XML 注释）。 */
    private static List<String> selectBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        String[] chunks = content.split("<select");
        for (int i = 1; i < chunks.length; i++) {
            int end = chunks[i].indexOf("</select>");
            if (end >= 0) {
                blocks.add(chunks[i].substring(0, end));
            }
        }
        return blocks;
    }

    @Test
    @DisplayName("4 个 Mapper XML 均可被 MyBatis 解析，且接口方法都有对应语句")
    void mapperXmlParsesAndDeclaresAllStatements() throws Exception {
        Configuration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (Map.Entry<String, List<String>> entry : STATEMENTS.entrySet()) {
            try (InputStream inputStream = Resources.getResourceAsStream(entry.getKey())) {
                new XMLMapperBuilder(inputStream, configuration, entry.getKey(), configuration.getSqlFragments()).parse();
            }
            String namespace = entry.getKey().contains("SysOrgMapper") ? "com.oa.identity.infra.SysOrgMapper"
                    : entry.getKey().contains("SysUserMapper") ? "com.oa.identity.infra.SysUserMapper"
                    : entry.getKey().contains("SysOrgLeaderMapper") ? "com.oa.identity.infra.SysOrgLeaderMapper"
                    : "com.oa.identity.infra.SysUserPositionMapper";
            for (String id : entry.getValue()) {
                assertThat(configuration.hasStatement(namespace + "." + id))
                        .as("%s#%s 必须有对应 MappedStatement", entry.getKey(), id)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("每条 SELECT 恰好 1 个 @dataScope 标记（0 个会被 fail-closed 拒绝，≥2 个只会替换第一个）")
    void everySelectCarriesExactlyOneMarker() throws Exception {
        Set<String> exemptSeen = new LinkedHashSet<>();
        for (String resource : STATEMENTS.keySet()) {
            String content = read(resource);
            List<String> blocks = selectBlocks(content);
            assertThat(blocks).as("%s 应至少有一条 SELECT", resource).isNotEmpty();
            for (String block : blocks) {
                Matcher idMatcher = ID_ATTR.matcher(block);
                String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
                String exemptStatementId = SYSTEM_SCOPE_EXEMPT_SELECTS.get(id);
                if (exemptStatementId != null) {
                    // 窄豁免（系统口径判重）：只允许 SysUserMapper.xml 的这两条「只读计数」语句；
                    // 必须真的不带标记，且必须同时列进豁免清单（否则运行期被 40303 拒绝，功能直接不可用）
                    assertThat(resource).as("%s#%s 是系统口径判重的窄豁免，只允许出现在 SysUserMapper.xml",
                            resource, id).isEqualTo(DIR + "SysUserMapper.xml");
                    assertThat(block).as("%s#%s 不得带 @dataScope 标记：判重必须是全库口径，"
                            + "织入数据域片段即「域外判重得 0」这一缺陷本身", resource, id)
                            .doesNotContain("@dataScope(");
                    assertThat(new OaProperties().getScope().getExemptStatementIds())
                            .as("%s#%s 必须列入 OaProperties 默认豁免清单（application.yml 同步，见 DataScopeMapperGuardTest）",
                                    resource, id)
                            .contains(exemptStatementId);
                    exemptSeen.add(id);
                    continue;
                }
                int markers = block.split("@dataScope\\(", -1).length - 1;
                assertThat(markers)
                        .as("%s#%s 必须恰好带 1 个 @dataScope 标记", resource, id)
                        .isEqualTo(1);
                assertThat(block).as("%s#%s 的标记位置必须是完整布尔表达式处", resource, id)
                        .contains("WHERE 1 = 1");
            }
        }
        // 豁免面不得悄悄扩大：本次只允许这两条
        assertThat(exemptSeen).as("允许 0 标记的受控表 SELECT 只有系统口径判重这两条")
                .containsExactlyInAnyOrderElementsOf(SYSTEM_SCOPE_EXEMPT_SELECTS.keySet());
    }

    @Test
    @DisplayName("标记里的 table 与各文件负责的受控表一致（唯一例外：候选人以 sys_user 为过滤主体）")
    void markerTablesMatchOwnership() throws Exception {
        assertThat(markerTables(DIR + "SysOrgMapper.xml")).containsExactly("sys_org");
        assertThat(markerTables(DIR + "SysUserMapper.xml")).containsExactly("sys_user");
        assertThat(markerTables(DIR + "SysUserPositionMapper.xml")).containsExactly("sys_user_position");
        assertThat(markerTables(DIR + "SysOrgLeaderMapper.xml"))
                .containsExactlyInAnyOrder("sys_org_leader", "sys_user");

        // 例外点必须唯一定位：只有 selectCandidates 用 sys_user 作过滤主体
        String leaderXml = read(DIR + "SysOrgLeaderMapper.xml");
        for (String block : selectBlocks(leaderXml)) {
            Matcher idMatcher = ID_ATTR.matcher(block);
            String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
            if (block.contains("table=sys_user")) {
                assertThat(id).as("以 sys_user 为过滤主体的语句只允许 selectCandidates").isEqualTo("selectCandidates");
            }
        }
    }

    @Test
    @DisplayName("受控表登记：sys_org/sys_user/sys_org_leader/sys_user_position 的实体都带 @DataScopeTable")
    void entitiesAreRegistered() throws Exception {
        assertThat(com.oa.identity.domain.SysOrg.class
                .getAnnotation(com.oa.common.scope.DataScopeTable.class)).isNotNull();
        assertThat(com.oa.identity.domain.SysUser.class
                .getAnnotation(com.oa.common.scope.DataScopeTable.class)).isNotNull();
        assertThat(com.oa.identity.domain.SysOrgLeader.class
                .getAnnotation(com.oa.common.scope.DataScopeTable.class)).isNotNull();
        assertThat(com.oa.identity.domain.SysUserPosition.class
                .getAnnotation(com.oa.common.scope.DataScopeTable.class)).isNotNull();
        // sys_user 是用户类口径（USER），其余为 NONE（仅禁止裸查询，不做行级过滤）
        assertThat(com.oa.identity.domain.SysUser.class
                .getAnnotation(com.oa.common.scope.DataScopeTable.class).kind())
                .isEqualTo(com.oa.common.scope.DataScopeKind.USER);
        assertThat(com.oa.identity.domain.SysOrg.class
                .getAnnotation(com.oa.common.scope.DataScopeTable.class).kind())
                .isEqualTo(com.oa.common.scope.DataScopeKind.NONE);
    }

    private static Set<String> markerTables(String resource) throws Exception {
        String content = read(resource);
        Set<String> tables = new LinkedHashSet<>();
        for (String block : selectBlocks(content)) {
            Matcher matcher = TABLE_ATTR.matcher(block);
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        return tables;
    }
}
