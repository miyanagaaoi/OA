package com.oa.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 在途/待办查询的 **SQL 语义**静态断言（无 DB）：证明「桩变真」之后，
 * 「在途」与「待办」确实是**按状态判断**的，而不是换个名字的恒 0。
 *
 * <p>与 {@code DefaultInFlightCheckerTest}（判定层）互补：后者验证「查出来的数怎么用」，
 * 本测试验证「查的是什么」。真实数据库路径由运行期实测覆盖：
 * 建一张在途单据后 {@code POST /identity/orgs/{id}/disable} 返回 409 并给出单号
 * （见交付说明的 D6 实测记录），以及 {@code GET /identity/orgs/{id}/in-flight-check}
 * 返回 {@code inFlightInstances=1} 与 {@code items[]}。
 */
class InFlightQuerySqlTest {

    private static final String RESOURCE = "mapper/identity/InFlightQueryMapper.xml";

    private static String sql(String id) throws Exception {
        String xml;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher matcher = Pattern.compile("<select id=\"" + id + "\"[^>]*>(.*?)</select>", Pattern.DOTALL)
                .matcher(xml);
        assertThat(matcher.find()).as("%s 必须存在", id).isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("「在途」= flow_instance.status = 'approving'（含待补件期间主状态仍为 approving）")
    void orgInFlightFiltersByApprovingStatus() throws Exception {
        String count = sql("countOrgInFlight");
        assertThat(count).contains("flow_instance").contains("i.status = 'approving'");
        assertThat(count).as("待补件期间主状态仍是 approving（enums.md §4），无需额外分支")
                .doesNotContain("sub_status");
        assertThat(count).as("组织口径按 sys_org.path 前缀匹配整棵子树（import-spec §8.2）")
                .contains("i.initiator_org_path LIKE").contains("sys_org").contains("so.path LIKE");

        assertThat(sql("selectOrgInFlightItems")).contains("i.status = 'approving'");
        assertThat(sql("selectOrgInFlightBizNos")).contains("i.status = 'approving'");
    }

    @Test
    @DisplayName("「待办」= flow_task.status = 'pending'（AC-12 的「名下未处理待办」）")
    void userPendingFiltersByPendingStatus() throws Exception {
        for (String id : new String[]{"countUserPendingTasks", "selectUserPendingTasks",
                "selectUserPendingBizNos", "selectPendingTaskCounts"}) {
            assertThat(sql(id)).as("%s 必须按 flow_task.status = 'pending' 过滤", id)
                    .contains("flow_task").contains("t.status = 'pending'");
        }
        assertThat(sql("countUserPendingTasks")).contains("t.assignee_id = #{userId}");
        assertThat(sql("selectPendingTaskCounts")).as("批量待办数一次 GROUP BY 取回（禁止 N+1）")
                .contains("GROUP BY t.assignee_id");
    }

    @Test
    @DisplayName("人员影响清单同时要求「待办 pending」与「单据 approving」（不把已办结单据算进来）")
    void userInFlightItemsRequiresBoth() throws Exception {
        String items = sql("selectUserInFlightItems");
        assertThat(items).contains("t.status = 'pending'").contains("i.status = 'approving'");
        assertThat(items).as("明细行必须带单据类型 / 发起人 / 状态（import-spec §7.2 影响清单八列）")
                .contains("i.form_type").contains("initiator_name").contains("t.status");
    }
}
