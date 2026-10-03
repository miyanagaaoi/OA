package com.oa.form.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeTableRegistry;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>2b.7 附件 Mapper 的静态合规校验</b>（无 DB / 无 Spring 容器）—— 与
 * {@code com.oa.form.FormMapperXmlTest} 同一风格。
 *
 * <p>锁死四件事：
 * <ol>
 *   <li>XML 能被 MyBatis 解析，且接口方法都有对应语句；</li>
 *   <li>{@code flow_attachment} 已被登记为**受控表**（裸查询会被 40303 拒绝）—— 这正是
 *       「后人新增未带标记的查询不会悄悄绕过数据域」的机器可验证形态；</li>
 *   <li>每条 SELECT **恰好 1 个** {@code @dataScope} 标记，标记位置在 {@code WHERE 1 = 1} 之后，
 *       且**过滤主体恒为 {@code flow_instance}**（本表没有 {@code initiator_id} 一族列，
 *       写成 {@code table=flow_attachment} 会在运行期拼出 {@code a.initiator_id} → Unknown column）；</li>
 *   <li>Mapper 不继承 {@code BaseMapper}、不声明 MP 通用读方法。</li>
 * </ol>
 */
class AttachmentMapperXmlTest {

    private static final String RESOURCE = "mapper/form/AttachmentMapper.xml";

    private static final String NAMESPACE = "com.oa.form.attachment.infra.AttachmentMapper";

    private static final String MAPPER_CLASS = "com.oa.form.attachment.infra.AttachmentMapper";

    private static final List<String> STATEMENTS = List.of(
            "selectById", "selectByInstance", "countByInstanceAndField", "countByInstance",
            "insert", "deleteById");

    private static final Pattern ID_ATTR = Pattern.compile("id\\s*=\\s*\"([A-Za-z0-9_]+)\"");
    private static final Pattern TABLE_ATTR = Pattern.compile("table\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");

    private static String read() throws Exception {
        try (InputStream inputStream = Resources.getResourceAsStream(RESOURCE)) {
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
    @DisplayName("XML 可被 MyBatis 解析，且接口的每个方法都有对应语句")
    void xmlParsesAndDeclaresAllStatements() throws Exception {
        Configuration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream inputStream = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(inputStream, configuration, RESOURCE,
                    configuration.getSqlFragments()).parse();
        }
        for (String id : STATEMENTS) {
            assertThat(configuration.hasStatement(NAMESPACE + "." + id))
                    .as("%s#%s 必须有对应 MappedStatement（接口加了方法、XML 忘了写会在此暴露）", RESOURCE, id)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("flow_attachment 是受控表：每条 SELECT 恰好 1 个标记，位置在 WHERE 1 = 1 之后")
    void markerDiscipline() throws Exception {
        for (String block : selectBlocks(read())) {
            Matcher idMatcher = ID_ATTR.matcher(block);
            String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
            int markers = block.split("@dataScope\\(", -1).length - 1;
            assertThat(markers).as("%s#%s 必须恰好带 1 个 @dataScope 标记", RESOURCE, id).isEqualTo(1);
            assertThat(block).as("%s#%s 的标记位置必须是完整布尔表达式处", RESOURCE, id)
                    .contains("WHERE 1 = 1");
        }
    }

    @Test
    @DisplayName("过滤主体恒为 flow_instance：flow_attachment 没有 initiator_id 一族列")
    void markerTablesMatchOwnership() throws Exception {
        String xml = read();
        Set<String> tables = new LinkedHashSet<>();
        for (String block : selectBlocks(xml)) {
            Matcher matcher = TABLE_ATTR.matcher(block);
            while (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        assertThat(tables)
                .as("DataScopeSqlBuilder 生成的是 alias.initiator_id 一族条件；flow_attachment 没有这些列 "
                        + "—— 写成 table=flow_attachment 会在运行期拼出 a.initiator_id → Unknown column")
                .containsExactly("flow_instance");
        assertThat(xml)
                .as("每条 flow_attachment 的 SELECT 都必须 JOIN flow_instance（否则数据域无法织入）")
                .contains("JOIN flow_instance i ON i.id = a.instance_id");
        int joins = 0;
        for (String block : selectBlocks(xml)) {
            joins += countOf(block, "JOIN flow_instance i ON i.id = a.instance_id");
        }
        assertThat(joins).as("4 条 SELECT 各一个 JOIN").isEqualTo(4);
    }

    @Test
    @DisplayName("受控表登记一致：flow_attachment 在册（裸查询会被 40303 拦下）")
    void attachmentTableIsRegisteredAsScoped() {
        DataScopeTableRegistry registry = new DataScopeTableRegistry(new OaProperties());
        assertThat(registry.isScoped("flow_attachment"))
                .as("Attachment 上的 @DataScopeTable(kind=NONE) 必须把本表登记为受控表；"
                        + "未登记时未带标记的裸查询不会被拒绝，数据域防线只剩一处")
                .isTrue();
        java.util.Optional<DataScopeTableRegistry.Entry> entry = registry.find("flow_attachment");
        assertThat(entry).isPresent();
        assertThat(entry.orElseThrow().kind()).isEqualTo(com.oa.common.scope.DataScopeKind.NONE);
    }

    @Test
    @DisplayName("Mapper 不继承 BaseMapper，且不声明 MP 注入的通用读方法")
    void mapperDoesNotExtendBaseMapper() throws Exception {
        Class<?> clazz = Class.forName(MAPPER_CLASS, false, AttachmentMapperXmlTest.class.getClassLoader());
        assertThat(clazz.isInterface()).isTrue();
        assertThat(BaseMapper.class.isAssignableFrom(clazz))
                .as("%s 不得继承 BaseMapper：其注入语句无 @dataScope 标记，会被 fail-closed 拒绝（40303）",
                        MAPPER_CLASS)
                .isFalse();
        Set<String> names = new LinkedHashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            names.add(method.getName());
        }
        // selectById 是本接口**显式声明**的语句（XML 里带标记），与 MP 注入的同名方法不是一回事：
        // 这里禁的是「本工程没写 SQL、却由 MP 代劳」的那一族。语句覆盖由上面的 XML 用例锁死。
        assertThat(names).doesNotContain("selectList", "selectOne", "selectPage",
                "selectCount", "selectBatchIds");
        assertThat(names).contains("selectById", "selectByInstance", "countByInstance",
                "countByInstanceAndField", "insert", "deleteById");
    }

    @Test
    @DisplayName("出参契约：Attachment#view 不得含 storage_path（TC-FORM-024 不给出攻击面）")
    void viewNeverExposesStoragePath() throws Exception {
        Class<?> clazz = Class.forName("com.oa.form.attachment.domain.Attachment",
                false, AttachmentMapperXmlTest.class.getClassLoader());
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Method view = clazz.getMethod("view");
        Object result = view.invoke(instance);
        assertThat(result).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) result;
        assertThat(map).doesNotContainKeys("storagePath", "storage_path");
        assertThat(map).containsKeys("id", "fileName", "fileExt", "sha256", "downloadUrl");
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
