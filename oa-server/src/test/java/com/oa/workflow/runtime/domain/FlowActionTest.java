package com.oa.workflow.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.workflow.runtime.domain.RuntimeEnums.TaskStatus;
import com.oa.workflow.runtime.domain.RuntimeEnums.ThreadAction;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * <b>动作面清单</b>的一致性单测（2a.4 / 2a.5）。
 *
 * <p>断言的关键点：每个动作的权限码都**真实存在于**权限种子 {@code V4__permissions.sql}
 * （避免「代码里写了一个没人持有的权限码」导致动作永远 403）；
 * 轨迹动作必须落在 16 个定稿值内；必填原因/意见下限与 PRD 一致。
 */
class FlowActionTest {

    private static String read(String resource) throws Exception {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("每个动作的权限码都存在于 V4__permissions.sql 的种子清单（动作面与授权面对齐）")
    void permissionsExistInSeeds() throws Exception {
        String sql = read("db/migration/V4__permissions.sql");
        for (FlowAction action : FlowAction.values()) {
            assertThat(sql)
                    .as("动作 %s 的权限码 %s 必须存在于权限种子", action.code(), action.permission())
                    .contains("'" + action.permission() + "'");
        }
    }

    @Test
    @DisplayName("轨迹动作必须是 16 个定稿值之一（唯一例外：reopen 无对应轨迹值，仅写审计）")
    void threadActionsInValueDomain() {
        Set<String> codes = ThreadAction.codes();
        for (FlowAction action : FlowAction.values()) {
            if (action == FlowAction.REOPEN) {
                assertThat(action.threadAction()).as("reopen 在 16 值里没有对应轨迹动作").isNull();
                continue;
            }
            assertThat(action.threadAction()).as("%s 必须有轨迹动作", action.code()).isNotNull();
            assertThat(codes).as("%s 的轨迹动作 %s 必须在 16 值内", action.code(), action.threadAction())
                    .contains(action.threadAction().code());
        }
    }

    @Test
    @DisplayName("跳转在轨迹上落 skip（16 值无 jump）；流转/回退/终止/加签/跳转 均必填原因")
    void reasonAndThreadMapping() {
        assertThat(FlowAction.JUMP.threadAction()).isEqualTo(ThreadAction.SKIP);
        for (FlowAction action : new FlowAction[] {FlowAction.ROLLBACK, FlowAction.ROUTE,
                FlowAction.BACK_HOME, FlowAction.JUMP, FlowAction.TERMINATE, FlowAction.ADD_SIGN,
                FlowAction.TRANSFER, FlowAction.REASSIGN, FlowAction.SUPPLEMENT_REQUEST}) {
            assertThat(action.requiresReason()).as("%s 必须填写原因", action.code()).isTrue();
        }
        assertThat(FlowAction.APPROVE.requiresReason()).isFalse();
        assertThat(FlowAction.ARCHIVE_REGISTER.requiresReason()).isFalse();
    }

    @Test
    @DisplayName("只有驳回有 5 字意见下限（AC-50）；动作后置任务状态与 enums.md §6 一致")
    void opinionAndTaskStatus() {
        assertThat(FlowAction.REJECT.minOpinionChars()).isEqualTo(5);
        for (FlowAction action : FlowAction.values()) {
            if (action != FlowAction.REJECT) {
                assertThat(action.minOpinionChars()).as("%s 不应设 5 字下限", action.code()).isZero();
            }
        }
        assertThat(FlowAction.APPROVE.taskStatusAfter()).isEqualTo(TaskStatus.AGREED);
        assertThat(FlowAction.ARCHIVE_REGISTER.taskStatusAfter()).isEqualTo(TaskStatus.AGREED);
        assertThat(FlowAction.REJECT.taskStatusAfter()).isEqualTo(TaskStatus.REJECTED);
        assertThat(FlowAction.ROLLBACK.taskStatusAfter()).isEqualTo(TaskStatus.ROLLED_BACK);
        assertThat(FlowAction.ROUTE.taskStatusAfter()).isEqualTo(TaskStatus.ROUTED);
        assertThat(FlowAction.BACK_HOME.taskStatusAfter()).isEqualTo(TaskStatus.ROUTED);
        assertThat(FlowAction.ADD_SIGN.taskStatusAfter()).isEqualTo(TaskStatus.ADDED_SIGN);
        assertThat(FlowAction.TRANSFER.taskStatusAfter()).isEqualTo(TaskStatus.TRANSFERRED);
        assertThat(FlowAction.REASSIGN.taskStatusAfter()).isEqualTo(TaskStatus.REASSIGNED);
        assertThat(FlowAction.SUPPLEMENT_REQUEST.taskStatusAfter())
                .isEqualTo(TaskStatus.SUPPLEMENT_REQUESTED);
        assertThat(FlowAction.WITHDRAW.taskStatusAfter()).isNull();
        assertThat(FlowAction.TERMINATE.taskStatusAfter()).isNull();
        assertThat(FlowAction.CC.taskStatusAfter()).isNull();
    }

    @Test
    @DisplayName("动作清单码唯一且可 of() 反查；PRD 列出的动作一个不少")
    void catalogCompleteness() {
        Set<String> codes = new LinkedHashSet<>();
        for (FlowAction action : FlowAction.values()) {
            assertThat(codes.add(action.code())).as("动作码重复：%s", action.code()).isTrue();
            assertThat(FlowAction.of(action.code())).contains(action);
            assertThat(action.transition()).isNotBlank();
        }
        // PRD §6.3.1 / §6.4 逐项点名：通过 / 驳回 / 流转 / 回退上一节点 / 回到本部门 / 要求补充材料 / 终止
        assertThat(codes).contains("approve", "reject", "route", "rollback", "back_home",
                "supplement_request", "terminate");
        // §6.4 / §6.6：动态加签 / 自由跳转 / 撤回 / 转办 / 改派 / 归档登记 / 抄送
        assertThat(codes).contains("add_sign", "jump", "withdraw", "transfer", "reassign",
                "archive_register", "cc", "submit", "supplement_submit");
    }

    @Test
    @DisplayName("归档登记与抄送的 statusAfter = null（不产生审批决议/不产生任务）")
    void nonDecisionActions() {
        assertThat(FlowAction.CC.taskStatusAfter()).isNull();
        assertThat(FlowAction.CC.threadAction()).isEqualTo(ThreadAction.CC);
        assertThat(FlowAction.ARCHIVE_REGISTER.threadAction()).isEqualTo(ThreadAction.ARCHIVE_REGISTER);
    }
}
