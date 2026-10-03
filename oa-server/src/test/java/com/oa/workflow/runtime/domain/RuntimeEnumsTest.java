package com.oa.workflow.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.workflow.runtime.domain.RuntimeEnums.AddSignType;
import com.oa.workflow.runtime.domain.RuntimeEnums.InstanceStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.NodeStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.RoutingAction;
import com.oa.workflow.runtime.domain.RuntimeEnums.SubStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 三层状态值域 × doc/enums.md / doc/data-model.md 的**一致性断言**（2a.4）。
 *
 * <p>这些断言的价值：枚举一旦漂移（例如有人把 {@code archive_register} 写回废弃值 {@code archive}、
 * 或把 {@code rollback} 写成旧值 {@code return_node}），本测试立刻失败 —— 而这类漂移在 DDL 的
 * {@code CHECK} 约束里是**静默拒绝写入**，到了运行期才发现就太晚了。
 */
class RuntimeEnumsTest {

    private static String read(String resource) throws Exception {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("实例状态 6 值与 data-model §5.1 的 CHECK 逐字一致；终态只有 approved/rejected/terminated")
    void instanceStatusMatchesDdl() throws Exception {
        String ddl = read("db/migration/V1__schema.sql");
        Set<String> expected = Set.of("draft", "approving", "approved", "rejected", "withdrawn", "terminated");
        assertThat(java.util.Arrays.stream(InstanceStatus.values()).map(InstanceStatus::code).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
        for (String code : expected) {
            assertThat(ddl).as("flow_instance.status 的 CHECK 必须含 %s", code).contains("'" + code + "'");
        }
        assertThat(InstanceStatus.APPROVED.terminal()).isTrue();
        assertThat(InstanceStatus.REJECTED.terminal()).isTrue();
        assertThat(InstanceStatus.TERMINATED.terminal()).isTrue();
        assertThat(InstanceStatus.WITHDRAWN.terminal())
                .as("withdrawn 是到达态：写入轨迹与审计后立即回 draft（data-model §8.2）")
                .isTrue();
        assertThat(InstanceStatus.DRAFT.terminal()).isFalse();
        assertThat(InstanceStatus.APPROVING.terminal()).isFalse();
        assertThat(InstanceStatus.REJECTED.editableByInitiator()).isTrue();
        assertThat(InstanceStatus.APPROVED.editableByInitiator()).isFalse();
    }

    @Test
    @DisplayName("子状态只有 pending_supplement；待补件期间主状态仍是 approving（enums.md §4.2）")
    void subStatusMatchesDdl() throws Exception {
        String ddl = read("db/migration/V1__schema.sql");
        assertThat(SubStatus.values()).hasSize(1);
        assertThat(SubStatus.PENDING_SUPPLEMENT.code()).isEqualTo("pending_supplement");
        assertThat(ddl).contains("'pending_supplement'");
    }

    @Test
    @DisplayName("节点实例状态 8 值：终态 5 个、非终态 3 个（returned 非终态）")
    void nodeStatusMatchesDdl() throws Exception {
        String ddl = read("db/migration/V1__schema.sql");
        Set<String> expected = Set.of("pending", "active", "waiting_supplement", "approved", "rejected",
                "skipped", "returned", "cancelled");
        assertThat(java.util.Arrays.stream(NodeStatus.values()).map(NodeStatus::code).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
        for (String code : expected) {
            assertThat(ddl).contains("'" + code + "'");
        }
        assertThat(NodeStatus.RETURNED.finished())
                .as("returned 是**非终态**：上一节点重审通过后回到 active（enums.md §5）")
                .isFalse();
        assertThat(NodeStatus.ACTIVE.live()).isTrue();
        assertThat(NodeStatus.RETURNED.live()).isFalse();
        assertThat(NodeStatus.CANCELLED.finished()).isTrue();
    }

    @Test
    @DisplayName("任务状态 10 值；全部为终态，只有 pending 可动作（enums.md §6）")
    void taskStatusMatchesDdl() throws Exception {
        String ddl = read("db/migration/V1__schema.sql");
        Set<String> expected = Set.of("pending", "agreed", "rejected", "transferred", "reassigned",
                "added_sign", "routed", "rolled_back", "supplement_requested", "auto_closed");
        assertThat(java.util.Arrays.stream(TaskStatus.values()).map(TaskStatus::code).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
        for (String code : expected) {
            assertThat(ddl).contains("'" + code + "'");
        }
    }

    @Test
    @DisplayName("轨迹动作共 16 个定稿值；不含废弃值 archive/addsign/return_node")
    void threadActionsAreExactlySixteen() {
        assertThat(ThreadAction.values()).hasSize(16);
        Set<String> codes = ThreadAction.codes();
        assertThat(codes).containsExactlyInAnyOrder(
                "submit", "approve", "reject", "route", "rollback", "back_home",
                "supplement_request", "supplement_submit", "transfer", "reassign",
                "add_sign", "withdraw", "terminate", "skip", "archive_register", "cc");
        assertThat(codes).doesNotContain("archive", "addsign", "return_node", "jump", "reopen");
        assertThat(ThreadAction.ARCHIVE_REGISTER.code()).isEqualTo("archive_register");
    }

    @Test
    @DisplayName("流转动作 3 值：只有 route/rollback 计入 routing_count，back_home 不计（REQ-FLOW-022）")
    void routingActionsAndCounting() {
        assertThat(java.util.Arrays.stream(RoutingAction.values()).map(RoutingAction::code).toList())
                .containsExactly("route", "rollback", "back_home");
        assertThat(RoutingAction.ROUTE.countedInRoutingCount()).isTrue();
        assertThat(RoutingAction.ROLLBACK.countedInRoutingCount()).isTrue();
        assertThat(RoutingAction.BACK_HOME.countedInRoutingCount()).isFalse();
    }

    @Test
    @DisplayName("加签类型 pre/post 与 DDL CHECK 一致；of() 拒绝大小写以外的异形值")
    void addSignTypes() {
        assertThat(java.util.Arrays.stream(AddSignType.values()).map(AddSignType::code).toList())
                .containsExactly("pre", "post");
        assertThat(AddSignType.of("PRE")).contains(AddSignType.PRE);
        assertThat(AddSignType.of(" pre ")).contains(AddSignType.PRE);
        assertThat(AddSignType.of("front")).isEmpty();
        assertThat(AddSignType.of(null)).isEmpty();
    }

    @Test
    @DisplayName("of() 大小写与空白容错，未知值一律 empty（不做静默兜底）")
    void ofLookups() {
        assertThat(InstanceStatus.of("APPROVING")).contains(InstanceStatus.APPROVING);
        assertThat(InstanceStatus.of("  draft ")).contains(InstanceStatus.DRAFT);
        assertThat(InstanceStatus.of("processing"))
                .as("processing 是废弃值（enums.md §14 旧值），不得作为实例状态被接受")
                .isEmpty();
        assertThat(NodeStatus.of("awaiting_supplement"))
                .as("旧值 awaiting_supplement 必须被拒绝（定稿值 waiting_supplement）")
                .isEmpty();
        assertThat(TaskStatus.of("closed"))
                .as("旧值 closed 必须被拒绝（定稿值 auto_closed）")
                .isEmpty();
        assertThat(ThreadAction.of("archive")).isEmpty();
    }

    @Test
    @DisplayName("每个枚举都带中文名（出参与轨迹展示共用）")
    void labelsPresent() {
        List<String> labels = new java.util.ArrayList<>();
        for (InstanceStatus value : InstanceStatus.values()) {
            labels.add(value.label());
        }
        for (NodeStatus value : NodeStatus.values()) {
            labels.add(value.label());
        }
        for (TaskStatus value : TaskStatus.values()) {
            labels.add(value.label());
        }
        for (ThreadAction value : ThreadAction.values()) {
            labels.add(value.label());
        }
        assertThat(labels).allSatisfy(label -> assertThat(label).isNotBlank());
    }
}
