package com.oa.workflow.approver.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.common.json.JsonText;
import com.oa.workflow.approver.domain.ApproverSnapshot;
import com.oa.workflow.approver.domain.ApproverSnapshot.SnapshotApprover;
import com.oa.workflow.approver.domain.ApproverSnapshot.SnapshotNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审批人快照编解码单测（doc/data-model.md §7.1 的结构契约）。
 *
 * <p>关键断言：
 * <ol>
 *   <li>结构键名与 §7.1 一致（{@code template_version} / {@code parsed_at} / {@code basis} / {@code nodes}）；</li>
 *   <li>候选人带「姓名 / 工号 / 组织」（{@code name} / {@code employee_no} / {@code org_id} / {@code org_path}）；</li>
 *   <li><b>{@code user_id} 必须是 JSON 数字</b>（不能用 Web mapper 的 Long→String 口径，
 *       否则 {@code JSON_EXTRACT} 与结构契约都会漂移）；</li>
 *   <li>往返无损；节点配置（决议模式 / 阈值 / 签名 / 超时）随快照冻结（templates.md V-07）。</li>
 * </ol>
 */
class ApproverSnapshotCodecTest {

    private static ApproverSnapshot snapshot() {
        SnapshotApprover approver = new SnapshotApprover(2001L, "张三", "zhangsan", "A001", 120L, "部门1",
                "/1/12/120/", 12L, "负责人");
        SnapshotNode node = new SnapshotNode(1, "dept_leader", "直属部门负责人", "approve",
                "dept_leader_upward", "any", null, "optional", 24, Boolean.FALSE, Boolean.TRUE,
                Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, null,
                "科室1A 未配置负责人 → 上溯至部门1 的负责人", List.of(approver), List.of(List.of(2001L)),
                1, "any", Boolean.TRUE);
        return new ApproverSnapshot(1L, "matter", 3, "2026-10-03 12:00:00",
                Map.of("initiator_id", 1024L, "initiator_org_id", 135L, "initiator_org_path", "/1/12/135/",
                        "company_id", 12L, "category", "economy", "involve_cost", true),
                List.of(node));
    }

    @Test
    @DisplayName("写入：JSON 键名与 §7.1 一致；user_id 是数字（不是字符串）")
    void writeMatchesContract() {
        String json = ApproverSnapshotCodec.write(snapshot());

        assertThat(json)
                .contains("\"template_version\":3")
                .contains("\"template_code\":\"matter\"")
                .contains("\"parsed_at\":\"2026-10-03 12:00:00\"")
                .contains("\"basis\":")
                .contains("\"initiator_org_path\":\"/1/12/135/\"")
                .contains("\"nodes\":")
                .contains("\"node_seq\":1")
                .contains("\"node_code\":\"dept_leader\"")
                .contains("\"rule\":\"dept_leader_upward\"")
                .contains("\"evidence\":")
                .contains("\"approvers\":")
                .contains("\"employee_no\":\"A001\"")
                .contains("\"org_path\":\"/1/12/120/\"")
                .contains("\"decision_mode\":\"any\"")
                .contains("\"sign_policy\":\"optional\"")
                .contains("\"timeout_hours\":24");

        assertThat(json).as("user_id 必须是数字（Web mapper 的 Long→String 口径不得用于落库 JSON）")
                .contains("\"user_id\":2001")
                .doesNotContain("\"user_id\":\"2001\"");
        assertThat(json).contains("\"initiator_id\":1024").doesNotContain("\"initiator_id\":\"1024\"");
        assertThat(json).contains("\"groups\":[[2001]]");
    }

    @Test
    @DisplayName("读取：往返无损（节点配置与候选人一字不差）")
    void readRoundTrip() {
        ApproverSnapshot original = snapshot();
        ApproverSnapshot parsed = ApproverSnapshotCodec.read(ApproverSnapshotCodec.write(original));

        assertThat(parsed.templateVersion()).isEqualTo(3);
        assertThat(parsed.templateCode()).isEqualTo("matter");
        assertThat(parsed.basis()).containsEntry("category", "economy").containsEntry("involve_cost", true);
        assertThat(parsed.nodes()).hasSize(1);
        SnapshotNode node = parsed.nodes().get(0);
        assertThat(node.nodeCode()).isEqualTo("dept_leader");
        assertThat(node.decisionMode()).isEqualTo("any");
        assertThat(node.signPolicy()).isEqualTo("optional");
        assertThat(node.timeoutHours()).isEqualTo(24);
        assertThat(node.allowAddSign()).isTrue();
        assertThat(node.allowJump()).isFalse();
        assertThat(node.approvers()).hasSize(1);
        assertThat(node.approvers().get(0))
                .extracting(SnapshotApprover::userId, SnapshotApprover::name, SnapshotApprover::employeeNo,
                        SnapshotApprover::orgId)
                .containsExactly(2001L, "张三", "A001", 120L);
        assertThat(node.groups()).containsExactly(List.of(2001L));
        assertThat(node.requiredApprovals()).isEqualTo(1);
        assertThat(node.satisfiable()).isTrue();
    }

    @Test
    @DisplayName("approverIds：按节点码取候选人（flow_node_instance.approver_ids_json 的派生源）")
    void approverIds() {
        ApproverSnapshot parsed = ApproverSnapshotCodec.read(ApproverSnapshotCodec.write(snapshot()));
        assertThat(ApproverSnapshotCodec.approverIds(parsed, "dept_leader")).containsExactly(2001L);
        assertThat(ApproverSnapshotCodec.approverIds(parsed, "chairman")).isEmpty();
        assertThat(ApproverSnapshotCodec.approverIds(parsed, null)).containsExactly(2001L);
        assertThat(ApproverSnapshotCodec.hasBlocker(parsed)).isFalse();
    }

    @Test
    @DisplayName("blocker：未跳过且无候选人的节点即阻断（读回后的自检口径）")
    void blockerDetection() {
        SnapshotNode empty = new SnapshotNode(3, "branch_leader", "分公司分管领导", "approve", "branch_leader",
                "any", null, "optional", 24, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE,
                Boolean.FALSE, null, "公司A 未配置副职负责人", List.of(), List.of(), 0, "any", Boolean.TRUE);        SnapshotNode skipped = new SnapshotNode(2, "finance_review", "财务部复核", "approve", "finance_owner",
                "any", null, "optional", 48, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE,
                Boolean.TRUE, "命中跳过条件 involve_cost eq false",
                "本单不涉及费用，财务节点已跳过（归口部门仍记为财务部）",
                List.of(), List.of(), 0, "any", Boolean.TRUE);        ApproverSnapshot snapshot = new ApproverSnapshot(1L, "matter", 1, "2026-10-03 12:00:00",
                Map.of(), List.of(empty, skipped));

        assertThat(empty.blocker()).isTrue();
        assertThat(skipped.blocker()).as("跳过的节点不构成阻断（prd §6.1）").isFalse();
        assertThat(ApproverSnapshotCodec.hasBlocker(snapshot)).isTrue();
    }

    @Test
    @DisplayName("assemble：basis 取自发起上下文（doc/data-model.md §7.1 的 basis 六键）")
    void assembleBasis() {
        RuleRequest request = new RuleRequest(1024L, 135L, 12L, "/1/12/135/", "economy",
                List.of(204L), List.of(), Map.of("involve_cost", false), java.util.Set.of());
        Map<String, Object> basis = ApproverSnapshotCodec.basis(request);

        assertThat(basis).containsEntry("initiator_id", 1024L)
                .containsEntry("initiator_org_id", 135L)
                .containsEntry("initiator_org_path", "/1/12/135/")
                .containsEntry("company_id", 12L)
                .containsEntry("category", "economy")
                .containsEntry("involve_cost", false)
                .containsEntry("initiator_picks", List.of(204L));
        assertThat(JsonText.write(basis)).doesNotContain("\"1024\"");

        ApproverSnapshot assembled = ApproverSnapshotCodec.assemble(1L, "matter", 1, request, List.of());
        assertThat(assembled.nodes()).isEmpty();
        assertThat(assembled.parsedAt()).isNotBlank();
    }
}
