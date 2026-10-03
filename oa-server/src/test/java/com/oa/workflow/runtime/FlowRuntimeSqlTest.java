package com.oa.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 运行时 Mapper 的 **SQL 语义**静态断言（无 DB）—— 与 {@code InFlightQuerySqlTest} 同一风格。
 *
 * <p>覆盖三条「写错了会静默出错」的口径：
 * <ol>
 *   <li><b>轮次过滤</b>：决议判定只数本轮任务（否则回退重审会沿用上一轮同意票，
 *       节点无需重新审批即通过 —— 2026-10-03 运行期实测发现）；</li>
 *   <li><b>激活即刷新轮次边界</b>：{@code activate} 把 {@code started_at} 置为 {@code NOW()}
 *       （而不是 {@code IFNULL(started_at, NOW())}）；</li>
 *   <li><b>Q6 计数口径</b>：{@code incrementRoutingCount} 同时 +1 {@code routing_count} 与
 *       {@code routing_seq}，且 {@code back_home} 不计入（由引擎只对 route/rollback 调用本语句保证）。</li>
 * </ol>
 */
class FlowRuntimeSqlTest {

    private static String resource(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String statement(String xml, String id) {
        Matcher matcher = Pattern.compile("<(select|update|insert) id=\"" + id + "\"[^>]*>(.*?)</\\1>",
                Pattern.DOTALL).matcher(xml);
        assertThat(matcher.find()).as("%s 必须存在", id).isTrue();
        return matcher.group(2);
    }

    @Test
    @DisplayName("决议判定只数本轮主任务：带 started_at 轮次边界，且必须 JOIN flow_node_instance")
    void roundFilterBoundary() throws Exception {
        String xml = resource("mapper/workflow/FlowTaskMapper.xml");
        String round = statement(xml, "selectRoundPrimaryByNodeInstance");
        assertThat(round)
                .contains("flow_node_instance ni")
                .contains("t.add_sign_type IS NULL")
                .contains("ni.started_at IS NULL OR t.created_at &gt;= ni.started_at");
        assertThat(round).as("受控表标记必须恰好 1 个且指向 flow_instance")
                .contains("@dataScope(table=flow_instance, alias=i)");
    }

    @Test
    @DisplayName("激活即刷新轮次边界：activate 写 started_at = NOW()（非 IFNULL）")
    void activateRefreshesRoundBoundary() throws Exception {
        String xml = resource("mapper/workflow/FlowNodeInstanceMapper.xml");
        String activate = statement(xml, "activate");
        assertThat(activate).contains("status = 'active'").contains("started_at = NOW()")
                .contains("finished_at = NULL");
        assertThat(activate).as("用 IFNULL 会让重审沿用上一轮 started_at，决议计数随之失真")
                .doesNotContain("IFNULL(started_at");
    }

    @Test
    @DisplayName("Q6 计数落点：routing_count 与 routing_seq 同时 +1，补件计数单独 +1")
    void gateCounters() throws Exception {
        String xml = resource("mapper/workflow/FlowInstanceMapper.xml");
        String routing = statement(xml, "incrementRoutingCount");
        assertThat(routing).contains("routing_count = routing_count + 1")
                .contains("routing_seq = routing_seq + 1");
        String supplement = statement(xml, "incrementSupplementCount");
        assertThat(supplement).contains("supplement_count = supplement_count + 1");
        assertThat(supplement).as("补件计数不得顺带改 routing_count").doesNotContain("routing_count");

        // 补件轮次在**请求时**占位：supplement_round 由引擎取 count+1，插入语句必须写入该列
        String supplementXml = resource("mapper/workflow/FlowRuntimeMapper.xml");
        assertThat(statement(supplementXml, "insertSupplement"))
                .contains("supplement_round").contains("deadline");
    }

    @Test
    @DisplayName("补件与抄送表不是受控表：SELECT 不得带 @dataScope 标记（否则按实例拼片段会报错）")
    void nonScopedTablesHaveNoMarker() throws Exception {
        String xml = resource("mapper/workflow/FlowRuntimeMapper.xml");
        for (String id : new String[] {"selectPendingSupplement", "selectSupplementsByInstance",
                "selectThreadByInstance", "selectCcByInstance"}) {
            assertThat(statement(xml, id)).as("%s 不得带 @dataScope 标记", id)
                    .doesNotContain("@dataScope(");
        }
    }
}
