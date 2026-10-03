package com.oa.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oa.common.config.OaProperties;
import com.oa.common.scope.DataScopeTableRegistry;
import java.io.InputStream;
import java.lang.reflect.Method;
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
 * 阶段 2a 新增 Mapper 的**静态合规校验**（无 DB / 无 Spring 容器）。
 *
 * <ol>
 *   <li>4 个 XML 能被 MyBatis 解析，且接口方法都有对应语句（防止「接口加了方法、XML 忘了写」）；</li>
 *   <li><b>受控表 SELECT 恰好 1 个 {@code @dataScope} 标记</b>
 *       （{@code ApproverDirectoryMapper} / {@code FlowInstanceMapper} / {@code InFlightQueryMapper}）；</li>
 *   <li><b>配置数据（{@code flow_template} / {@code flow_node}）刻意 0 个标记</b>：
 *       它们不是受控表，加标记会按调用人的单据可见范围裁剪**流程模板**（配置面崩坏）；</li>
 *   <li>受控 Mapper 不继承 {@code BaseMapper}、不声明 MP 通用读方法；</li>
 *   <li>{@code flow_template} / {@code flow_node} **不得**出现在受控表清单里（否则第 3 条会被拦截器推翻）。</li>
 * </ol>
 */
class WorkflowMapperXmlTest {

    private static final String DIR = "mapper/workflow/";
    private static final String IDENTITY_DIR = "mapper/identity/";
    private static final Pattern TABLE_ATTR = Pattern.compile("table\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ID_ATTR = Pattern.compile("id\\s*=\\s*\"([A-Za-z0-9_]+)\"");

    /** 资源 → 期望存在的语句 id。 */
    private static final Map<String, List<String>> STATEMENTS = new LinkedHashMap<>();

    static {
        STATEMENTS.put(DIR + "FlowTemplateMapper.xml", List.of(
                "selectByCode", "selectByCodeAndStatus", "selectTemplates", "selectTemplateById",
                "selectByCodeAndVersion", "selectMaxVersion",
                "insertTemplate", "updateDraft", "updateStatus", "archiveOtherPublished", "updateNodeCount"));
        STATEMENTS.put(DIR + "FlowNodeMapper.xml", List.of(
                "selectByTemplateId", "selectByTemplateIds", "selectNodeById", "countByTemplateId",
                "countByTemplateAndCode", "insertNode", "updateNode", "updateSeq", "deleteById",
                "deleteByTemplateId", "cloneNodes"));
        STATEMENTS.put(DIR + "ApproverDirectoryMapper.xml", List.of(
                "selectOrg", "selectOrgByName", "selectOrgsByType", "selectPrimaryLeaders",
                "selectDeputyLeaders", "selectCategoryLeaders", "selectUser", "selectUsers",
                "selectUsersByRoleCode"));
        STATEMENTS.put(DIR + "FlowInstanceMapper.xml", List.of(
                "selectInstanceById", "selectInstances", "selectByBizNo", "countByBizNo", "countInFlightByTemplate",
                "selectFormDataCreator", "selectFormDataFields",
                "insertInstance", "updateSnapshot", "markSubmitted", "updateCurrentNodeSeq", "insertFormData",
                // 2a.4 运行时进度写语句
                "updateProgress", "markFinished", "incrementRoutingCount", "incrementSupplementCount",
                "resetForResubmit"));
        // 2a.4 运行时从表（flow_node_instance）
        STATEMENTS.put(DIR + "FlowNodeInstanceMapper.xml", List.of(
                "selectByInstance", "selectNodeInstanceById", "selectByInstanceAndKey", "selectLiveByInstance",
                "selectByInstanceAndSeq", "selectPreviousApproved", "selectReturnedNode",
                "countLiveAtSeq", "countFinishedAtSeq", "countByInstance",
                "insert", "updateStatus", "activate", "finish", "updateApprovers", "updateAddSignChain",
                "incrementReturnedCount", "markSupplementWaiting", "markSupplementResolved",
                "resetForNewRound"));
        // 2a.5 任务（flow_task；受控表 → 每条 SELECT 1 个标记）
        STATEMENTS.put(DIR + "FlowTaskMapper.xml", List.of(
                "selectTaskById", "selectByNodeInstance", "selectPrimaryByNodeInstance",
                "selectRoundPrimaryByNodeInstance",
                "selectPendingPrimaryByNodeInstance", "selectPendingPrimaryByInstanceAndAssignee",
                "countPendingByInstance", "selectTasksByInstance",
                "selectTodo", "countTodo", "selectDone", "countDone", "selectInitiated", "countInitiated",
                "insert", "updateDecision", "closePendingByNodeInstance", "closePendingByInstance",
                "updateHandover"));
        // 2a.4 流转链（flow_routing；受控表 → 每条 SELECT 1 个标记）
        STATEMENTS.put(DIR + "FlowRoutingMapper.xml", List.of(
                "selectRoutingByInstance", "selectLastRouting", "selectProcessingRollback",
                "countRoutingToDept", "selectRecentRoutings", "insertRouting", "finishRouting"));
        // 2a.4 补件 / 轨迹 / 抄送（均非受控表 → 0 个标记）
        STATEMENTS.put(DIR + "FlowRuntimeMapper.xml", List.of(
                "selectPendingSupplement", "selectSupplementsByInstance", "insertSupplement",
                "markSupplementSubmitted", "markSupplementOverdue", "cancelPendingSupplements",
                "selectThreadByInstance", "nextThreadSeq", "insertThread",
                "selectCcByInstance", "insertCc", "markCcRead"));
        STATEMENTS.put(IDENTITY_DIR + "InFlightQueryMapper.xml", List.of(
                "countOrgInFlight", "selectOrgInFlightItems", "selectOrgInFlightBizNos",
                "countUserPendingTasks", "selectPendingTaskCounts", "selectUserPendingTasks",
                "selectUserPendingBizNos", "selectUserInFlightItems",
                "countUserCandidateNodes", "selectUserCandidateBizNos", "selectUserCandidateInFlightItems"));
    }

    /** 命名空间推断（与文件名一一对应）。 */
    private static String namespaceOf(String resource) {
        if (resource.endsWith("FlowTemplateMapper.xml")) {
            return "com.oa.workflow.definition.infra.FlowTemplateMapper";
        }
        if (resource.endsWith("FlowNodeMapper.xml")) {
            return "com.oa.workflow.definition.infra.FlowNodeMapper";
        }
        if (resource.endsWith("ApproverDirectoryMapper.xml")) {
            return "com.oa.workflow.approver.infra.ApproverDirectoryMapper";
        }
        if (resource.endsWith("FlowInstanceMapper.xml")) {
            return "com.oa.workflow.approver.infra.FlowInstanceMapper";
        }
        if (resource.endsWith("FlowNodeInstanceMapper.xml")) {
            return "com.oa.workflow.runtime.infra.FlowNodeInstanceMapper";
        }
        if (resource.endsWith("FlowTaskMapper.xml")) {
            return "com.oa.workflow.runtime.infra.FlowTaskMapper";
        }
        if (resource.endsWith("FlowRoutingMapper.xml")) {
            return "com.oa.workflow.runtime.infra.FlowRoutingMapper";
        }
        if (resource.endsWith("FlowRuntimeMapper.xml")) {
            return "com.oa.workflow.runtime.infra.FlowRuntimeMapper";
        }
        return "com.oa.identity.infra.InFlightQueryMapper";
    }

    /** 受控表语义的 Mapper XML（每条 SELECT 必须恰好 1 个标记）。 */
    private static final Set<String> SCOPED_XML = new LinkedHashSet<>(List.of(
            DIR + "ApproverDirectoryMapper.xml",
            DIR + "FlowInstanceMapper.xml",
            DIR + "FlowTaskMapper.xml",
            DIR + "FlowRoutingMapper.xml",
            IDENTITY_DIR + "InFlightQueryMapper.xml"));

    /** 配置数据 / 非受控表语义的 Mapper XML（**刻意 0 个标记**）。 */
    private static final Set<String> CONFIG_XML = new LinkedHashSet<>(List.of(
            DIR + "FlowTemplateMapper.xml",
            DIR + "FlowNodeMapper.xml",
            DIR + "FlowNodeInstanceMapper.xml",
            DIR + "FlowRuntimeMapper.xml"));

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
    @DisplayName("5 个 Mapper XML 均可被 MyBatis 解析，且接口方法都有对应语句")
    void mapperXmlParsesAndDeclaresAllStatements() throws Exception {
        Configuration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (Map.Entry<String, List<String>> entry : STATEMENTS.entrySet()) {
            try (InputStream inputStream = Resources.getResourceAsStream(entry.getKey())) {
                new XMLMapperBuilder(inputStream, configuration, entry.getKey(),
                        configuration.getSqlFragments()).parse();
            }
            String namespace = namespaceOf(entry.getKey());
            for (String id : entry.getValue()) {
                assertThat(configuration.hasStatement(namespace + "." + id))
                        .as("%s#%s 必须有对应 MappedStatement", entry.getKey(), id)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("受控表 SELECT 恰好 1 个 @dataScope 标记；配置数据（flow_template/flow_node）刻意 0 个")
    void markerDiscipline() throws Exception {
        for (String resource : SCOPED_XML) {
            String content = read(resource);
            List<String> blocks = selectBlocks(content);
            assertThat(blocks).as("%s 应至少有一条 SELECT", resource).isNotEmpty();
            for (String block : blocks) {
                Matcher idMatcher = ID_ATTR.matcher(block);
                String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
                int markers = block.split("@dataScope\\(", -1).length - 1;
                assertThat(markers)
                        .as("%s#%s 必须恰好带 1 个 @dataScope 标记（0 个会被 40303 fail-closed 拒绝）",
                                resource, id)
                        .isEqualTo(1);
                assertThat(block).as("%s#%s 的标记位置必须是完整布尔表达式处", resource, id)
                        .contains("WHERE 1 = 1");
            }
        }

        for (String resource : CONFIG_XML) {
            String content = read(resource);
            assertThat(selectBlocks(content)).as("%s 应至少有一条 SELECT", resource).isNotEmpty();
            for (String block : selectBlocks(content)) {
                Matcher idMatcher = ID_ATTR.matcher(block);
                String id = idMatcher.find() ? idMatcher.group(1) : "(unknown)";
                assertThat(block)
                        .as("%s#%s 不得带 @dataScope 标记：flow_template / flow_node 是**配置数据**，"
                                + "织入实例类数据域片段会按调用人的单据可见范围裁剪流程模板（配置面直接崩坏）",
                                resource, id)
                        .doesNotContain("@dataScope(");
            }
        }
    }

    @Test
    @DisplayName("受控表声明一致性：flow_template / flow_node 不在受控表清单内；flow_instance 等在")
    void scopedTableRegistryMatchesMarkerDiscipline() {
        DataScopeTableRegistry registry = new DataScopeTableRegistry(new OaProperties());

        assertThat(registry.isScoped("flow_template"))
                .as("flow_template 是配置数据，不得登记为受控表（否则上面的 0 标记口径会被拦截器推翻）")
                .isFalse();
        assertThat(registry.isScoped("flow_node")).isFalse();
        assertThat(registry.isScoped("flow_instance")).isTrue();
        assertThat(registry.isScoped("flow_task")).isTrue();
        assertThat(registry.isScoped("form_data")).isTrue();
        assertThat(registry.isScoped("sys_org")).isTrue();
        assertThat(registry.isScoped("sys_org_leader")).isTrue();
        assertThat(registry.isScoped("sys_user")).isTrue();
        assertThat(registry.isScoped("flow_node_instance"))
                .as("flow_node_instance 未登记：它是运行时快照表，读取一律经 flow_instance 的关联查询")
                .isFalse();
    }

    @Test
    @DisplayName("标记里的 table 与各文件负责的受控表一致（跨表 JOIN 只标注过滤主体）")
    void markerTablesMatchOwnership() throws Exception {
        assertThat(markerTables(DIR + "FlowInstanceMapper.xml")).containsExactly("flow_instance", "form_data");
        assertThat(markerTables(DIR + "ApproverDirectoryMapper.xml"))
                .containsExactlyInAnyOrder("sys_org", "sys_org_leader", "sys_user");
        assertThat(markerTables(IDENTITY_DIR + "InFlightQueryMapper.xml")).containsExactly("flow_instance");
        // 2a.4/2a.5：任务与流转链的过滤主体恒为 flow_instance（标记写成 flow_task/flow_routing
        // 会拼出该表不存在的 initiator_id → SQL 报错）
        assertThat(markerTables(DIR + "FlowTaskMapper.xml")).containsExactly("flow_instance");
        assertThat(markerTables(DIR + "FlowRoutingMapper.xml")).containsExactly("flow_instance");
        assertThat(markerTables(DIR + "FlowTemplateMapper.xml")).isEmpty();
        assertThat(markerTables(DIR + "FlowNodeMapper.xml")).isEmpty();
        assertThat(markerTables(DIR + "FlowNodeInstanceMapper.xml")).isEmpty();
        assertThat(markerTables(DIR + "FlowRuntimeMapper.xml")).isEmpty();
    }

    @Test
    @DisplayName("受控 Mapper 不继承 BaseMapper、不声明 MP 通用读方法（MP 注入语句无标记 → 40303）")
    void scopedMappersDoNotExtendBaseMapper() throws Exception {
        List<String> mappers = List.of(
                "com.oa.workflow.approver.infra.ApproverDirectoryMapper",
                "com.oa.workflow.approver.infra.FlowInstanceMapper",
                "com.oa.identity.infra.InFlightQueryMapper",
                "com.oa.workflow.definition.infra.FlowTemplateMapper",
                "com.oa.workflow.definition.infra.FlowNodeMapper",
                // 2a.4 / 2a.5 新增
                "com.oa.workflow.runtime.infra.FlowNodeInstanceMapper",
                "com.oa.workflow.runtime.infra.FlowTaskMapper",
                "com.oa.workflow.runtime.infra.FlowRuntimeMapper");
        Set<String> mpReadMethods = Set.of("selectById", "selectList", "selectOne", "selectPage",
                "selectCount", "selectBatchIds");

        for (String className : mappers) {
            Class<?> clazz = Class.forName(className, false, WorkflowMapperXmlTest.class.getClassLoader());
            assertThat(clazz.isInterface()).as("%s 应是 Mapper 接口", className).isTrue();
            assertThat(BaseMapper.class.isAssignableFrom(clazz))
                    .as("%s 不得继承 MyBatis-Plus BaseMapper", className).isFalse();
            Set<String> names = new LinkedHashSet<>();
            for (Method method : clazz.getDeclaredMethods()) {
                names.add(method.getName());
            }
            assertThat(names).as("%s 不得声明 MP 通用读方法", className)
                    .doesNotContainAnyElementsOf(mpReadMethods);
        }
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
