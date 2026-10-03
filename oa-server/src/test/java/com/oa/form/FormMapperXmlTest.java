package com.oa.form;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeTableRegistry;
import com.oa.form.document.FormRuleRegistry;
import com.oa.form.fund.FundFormRules;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b 新增 Mapper 的静态合规校验</b>（无 DB / 无 Spring 容器）——与
 * {@code com.oa.workflow.WorkflowMapperXmlTest} 同一风格。
 *
 * <ol>
 *   <li>2 个 XML 能被 MyBatis 解析，且接口方法都有对应语句（防「接口加了方法、XML 忘了写」）；</li>
 *   <li><b>{@code form_data} 是受控表</b>：每条 SELECT **恰好 1 个** {@code @dataScope} 标记，
 *       且标记位置在 {@code WHERE 1 = 1} 之后（拦截器只认那个位置）；
 *       {@code countApprovedInstanceByBizNo} 的过滤主体必须是 {@code flow_instance}；</li>
 *   <li><b>{@code sys_dict_item} 不是受控表</b>：SELECT **刻意 0 个**标记
 *       （织入数据域会按调用人的单据可见范围裁剪字典，下拉框在域外直接空掉）；</li>
 *   <li>两个 Mapper 都不继承 {@code BaseMapper}、不声明 MP 通用读方法。</li>
 * </ol>
 */
class FormMapperXmlTest {

    private static final String DIR = "mapper/form/";
    private static final Pattern TABLE_ATTR = Pattern.compile("table\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ID_ATTR = Pattern.compile("id\\s*=\\s*\"([A-Za-z0-9_]+)\"");

    private static final Map<String, List<String>> STATEMENTS = new LinkedHashMap<>();

    static {
        STATEMENTS.put(DIR + "FormDataMapper.xml", List.of(
                "selectFormDataById", "selectFormDataByBizNo", "selectPayeeAccountCipher",
                "countApprovedInstanceByBizNo",
                "updateFieldsJson", "updatePayeeAccountCipher", "clearPayeeAccountCipher"));
        STATEMENTS.put(DIR + "SysDictItemMapper.xml", List.of(
                "selectEnabledByType", "selectAllByType", "countByType"));
    }

    /** 受控表语义的 XML（每条 SELECT 恰好 1 个标记）。 */
    private static final Set<String> SCOPED_XML = Set.of(DIR + "FormDataMapper.xml");

    /** 非受控表 / 配置数据语义的 XML（刻意 0 个标记）。 */
    private static final Set<String> CONFIG_XML = Set.of(DIR + "SysDictItemMapper.xml");

    private static String read(String resource) throws Exception {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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
    @DisplayName("2 个 Mapper XML 均可被 MyBatis 解析，且接口方法都有对应语句")
    void mapperXmlParsesAndDeclaresAllStatements() throws Exception {
        Configuration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (Map.Entry<String, List<String>> entry : STATEMENTS.entrySet()) {
            try (InputStream inputStream = Resources.getResourceAsStream(entry.getKey())) {
                new XMLMapperBuilder(inputStream, configuration, entry.getKey(),
                        configuration.getSqlFragments()).parse();
            }
            String namespace = entry.getKey().endsWith("FormDataMapper.xml")
                    ? "com.oa.form.infra.FormDataMapper" : "com.oa.form.dict.infra.SysDictItemMapper";
            for (String id : entry.getValue()) {
                assertThat(configuration.hasStatement(namespace + "." + id))
                        .as("%s#%s 必须有对应 MappedStatement", entry.getKey(), id)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("form_data 受控 → 每条 SELECT 恰好 1 个标记；sys_dict_item 非受控 → 0 个标记")
    void markerDiscipline() throws Exception {
        for (String resource : SCOPED_XML) {
            for (String block : selectBlocks(read(resource))) {
                Matcher idMatcher = ID_ATTR.matcher(block);
                String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
                int markers = block.split("@dataScope\\(", -1).length - 1;
                assertThat(markers).as("%s#%s 必须恰好带 1 个 @dataScope 标记", resource, id).isEqualTo(1);
                assertThat(block).as("%s#%s 的标记位置必须是完整布尔表达式处", resource, id)
                        .contains("WHERE 1 = 1");
            }
        }
        for (String resource : CONFIG_XML) {
            for (String block : selectBlocks(read(resource))) {
                Matcher idMatcher = ID_ATTR.matcher(block);
                String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
                assertThat(block).as("%s#%s 不得带 @dataScope 标记：sys_dict_item 是配置数据，"
                        + "织入数据域会按调用人的单据可见范围裁剪字典（下拉框在域外直接空掉）", resource, id)
                        .doesNotContain("@dataScope(");
            }
        }
    }

    @Test
    @DisplayName("标记指向的过滤主体正确：**恒为 flow_instance**（form_data 没有 initiator_id 列）")
    void markerTablesMatchOwnership() throws Exception {
        Set<String> tables = new LinkedHashSet<>();
        for (String block : selectBlocks(read(DIR + "FormDataMapper.xml"))) {
            Matcher matcher = TABLE_ATTR.matcher(block);
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        assertThat(tables)
                .as("DataScopeSqlBuilder 生成的是 alias.initiator_id 一族条件；"
                        + "form_data 表没有这些列 —— 写成 table=form_data 会在运行期拼出 "
                        + "f.initiator_id → Unknown column（2026-10-03 实测）")
                .containsExactly("flow_instance");
        assertThat(read(DIR + "FormDataMapper.xml"))
                .as("每条 form_data 的 SELECT 都必须 JOIN flow_instance（否则数据域无法织入）")
                .contains("JOIN flow_instance i ON i.form_data_id = f.id");
        assertThat(read(DIR + "FormDataMapper.xml"))
                .as("敏感字段单独密文落列（doc/data-model.md §8.2）：收款账号不进 fields_json")
                .contains("payee_account_cipher");
    }

    @Test
    @DisplayName("两个 Mapper 都不继承 BaseMapper、不声明 MP 通用读方法")
    void mappersDoNotExtendBaseMapper() throws Exception {
        for (String className : List.of("com.oa.form.infra.FormDataMapper",
                "com.oa.form.dict.infra.SysDictItemMapper")) {
            Class<?> clazz = Class.forName(className, false, FormMapperXmlTest.class.getClassLoader());
            assertThat(clazz.isInterface()).isTrue();
            assertThat(BaseMapper.class.isAssignableFrom(clazz)).as("%s 不得继承 BaseMapper", className).isFalse();
            Set<String> names = new LinkedHashSet<>();
            for (Method method : clazz.getDeclaredMethods()) {
                names.add(method.getName());
            }
            assertThat(names).doesNotContain("selectById", "selectList", "selectOne", "selectPage",
                    "selectCount", "selectBatchIds");
        }
    }

    @Test
    @DisplayName("受控表登记一致性：form_data 在册；sys_dict_item 不在册（否则字典会被裁剪）")
    void scopedTableRegistry() {
        DataScopeTableRegistry registry = new DataScopeTableRegistry(new OaProperties());
        assertThat(registry.isScoped("form_data")).isTrue();
        assertThat(registry.isScoped("sys_dict_item"))
                .as("字典是配置数据（doc/data-model.md §11「配置/字典」）")
                .isFalse();
    }

    @Test
    @DisplayName("真源种子回归：四类模板的 skip_condition 都不得引用「只存不用」字段")
    void seededSkipConditionsRespectStorageOnly() throws Exception {
        Path script = null;
        for (Path candidate : List.of(Path.of("..", "oa-deploy", "sql", "03-templates.sql"),
                Path.of("oa-deploy", "sql", "03-templates.sql"))) {
            if (Files.isRegularFile(candidate)) {
                script = candidate;
                break;
            }
        }
        Assumptions.assumeTrue(script != null, "找不到 oa-deploy/sql/03-templates.sql，跳过真源种子回归");

        String sql = Files.readString(script, StandardCharsets.UTF_8);
        List<String> conditions = new ArrayList<>();
        // 种子脚本里的 JSON 是**转义后的单引号字面量**（{\"field\":...}）；
        // 这里先取所有单引号包裹的对象字面量，再反转义，最后只保留「扁平的条件对象」
        // （必须同时含 field 与 op，且内部不再有 '{'）—— 嵌套的 form_schema_json 由此被排除。
        Matcher matcher = Pattern.compile("'(\\{[^']*\\})'", Pattern.DOTALL).matcher(sql);
        while (matcher.find()) {
            String raw = matcher.group(1).replace("\\\"", "\"");
            if (!raw.contains("\"field\"") || !raw.contains("\"op\"") || raw.indexOf('{', 1) >= 0) {
                continue;
            }
            conditions.add(raw);
        }
        // 即使一条都没匹配到也必须过（不能因为解析不到就静默通过 —— 这里显式断言至少解析到 1 条）
        assertThat(conditions).as("03-templates.sql 里应当存在至少一条 skip_condition（matter 的②）")
                .isNotEmpty();
        assertThat(conditions).as("matter 的② 跳过条件逐字对齐 doc/templates.md §1.1")
                .contains("{\"field\":\"involve_cost\",\"op\":\"eq\",\"value\":false}");
        FundFormRules.assertNotRouted(conditions);
    }

    @Test
    @DisplayName("四类单据专属规则都已注册（漏注册会让专属校验被静默跳过）")
    void ruleRegistryIsComplete() {
        FormRuleRegistry registry = new FormRuleRegistry(List.of(
                new com.oa.form.matter.MatterFormRules(), new FundFormRules(),
                new com.oa.form.contract.ContractFormRules(), new com.oa.form.seal.SealFormRules()));
        assertThat(registry.registered()).containsExactlyInAnyOrder("matter", "fund", "contract", "seal");
    }
}
