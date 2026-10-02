package com.oa.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.app.InFlightChecker.InFlightSummary;
import com.oa.identity.app.InFlightChecker.PendingTask;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 在途/待办检查的分支（**纯逻辑单测 + 假实现**）：
 * {@code oa.identity.block-on-inflight} 为真时拒绝（409，文案含数量与单号），为假时仅告警放行。
 *
 * <p>覆盖 AC-11 / AC-12 / PRD §5.5 / import-spec §8.1（离职）与 §8.2（组织停用）。
 */
class InFlightGuardTest {

    /** 假实现：直接把给定摘要返回，用于驱动两个分支（真实现见 DefaultInFlightChecker 注释的接入点）。 */
    private static final class FakeChecker implements InFlightChecker {

        private final InFlightSummary orgSummary;
        private final InFlightSummary userSummary;
        private final List<PendingTask> tasks;

        private FakeChecker(InFlightSummary orgSummary, InFlightSummary userSummary, List<PendingTask> tasks) {
            this.orgSummary = orgSummary;
            this.userSummary = userSummary;
            this.tasks = tasks;
        }

        @Override
        public InFlightSummary checkOrgSubtree(String orgPathPrefix) {
            return orgSummary;
        }

        @Override
        public InFlightSummary checkUser(Long userId) {
            return userSummary;
        }

        @Override
        public List<PendingTask> pendingTasksOf(Long userId) {
            return tasks;
        }

        @Override
        public List<InFlightItem> inFlightItems(Long userId) {
            return List.of();
        }

        @Override
        public List<InFlightItem> orgInFlightItems(String orgPathPrefix) {
            return List.of();
        }
    }

    @Test
    @DisplayName("AC-12：离职前有待办 + block=true → 409，文案给出数量与单号，状态不变")
    void resignBlockedWhenPendingTasksExist() {
        InFlightSummary summary = new InFlightSummary(0, 2, List.of("OA-2026-100003", "OA-2026-100004"));
        InFlightGuard.Outcome outcome = InFlightGuard.evaluate(
                InFlightGuard.Subject.USER_RESIGN, "李乙（liyi0002）", summary, true);
        assertThat(outcome.blocked()).isTrue();
        assertThat(outcome.rejected()).isTrue();
        assertThat(outcome.message())
                .contains("离职前必须清空名下待办")
                .contains("李乙（liyi0002）")
                .contains("2 条未处理待办")
                .contains("OA-2026-100003")
                .contains("OA-2026-100004");

        assertThatThrownBy(() -> InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙（liyi0002）", summary, true))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(409);
                    assertThat(biz.getDetails()).containsEntry("pendingTasks", 2);
                });
    }

    @Test
    @DisplayName("AC-12 边界：待办清零后放行（不抛异常）")
    void resignPassesWhenCleared() {
        InFlightGuard.Outcome outcome = InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙（liyi0002）", InFlightSummary.none(), true);
        assertThat(outcome.blocked()).isFalse();
        assertThat(outcome.rejected()).isFalse();
        assertThat(outcome.message()).contains("无在途单据与待办");
    }

    @Test
    @DisplayName("block-on-inflight=false：仅告警放行，但仍返回影响清单")
    void warnOnlyModePassesWithImpact() {
        InFlightSummary summary = new InFlightSummary(0, 2, List.of("OA-2026-100003"));
        InFlightGuard.Outcome outcome = InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙", summary, false);
        assertThat(outcome.blocked()).isTrue();
        assertThat(outcome.rejected()).isFalse();
        assertThat(outcome.blockOnInflight()).isFalse();
        assertThat(outcome.summary().total()).isEqualTo(2);
        assertThat(outcome.message()).contains("2 条未处理待办");
    }

    @Test
    @DisplayName("AC-11/§8.2：组织停用前有在途单据 → 拒绝，文案为「及其子树下仍有 N 张在途单据」")
    void disableBlockedWhenInFlightExists() {
        InFlightSummary summary = new InFlightSummary(3, 0, List.of("ZJ-2026-000118", "ZJ-2026-000123"));
        InFlightGuard.Outcome outcome = InFlightGuard.evaluate(
                InFlightGuard.Subject.ORG_DISABLE, "集团/公司A", summary, true);
        assertThat(outcome.rejected()).isTrue();
        assertThat(outcome.message())
                .contains("组织停用前必须清空在途单据")
                .contains("集团/公司A 及其子树下仍有 3 张在途单据");

        assertThatThrownBy(() -> InFlightGuard.assertClear(
                InFlightGuard.Subject.ORG_DISABLE, "集团/公司A", summary, true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ZJ-2026-000118");
    }

    @Test
    @DisplayName("单号过多时文案截断但仍给出总数，且 summary 保留完整清单")
    void messageTruncatesLongBizNoList() {
        List<String> bizNos = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            bizNos.add("OA-2026-1000" + i);
        }
        InFlightSummary summary = new InFlightSummary(15, 0, bizNos);
        InFlightGuard.Outcome outcome = InFlightGuard.evaluate(
                InFlightGuard.Subject.ORG_DISABLE, "集团/公司A", summary, true);
        assertThat(outcome.message()).contains("等 15 张");
        assertThat(outcome.summary().bizNos()).hasSize(15);
    }

    @Test
    @DisplayName("假实现可驱动 OrgService/UserService 的调用路径（端口可替换性）")
    void fakeCheckerDrivesBothDirections() {
        InFlightChecker checker = new FakeChecker(                new InFlightSummary(1, 0, List.of("OA-2026-200001")),
                new InFlightSummary(0, 1, List.of("OA-2026-200002")),
                List.of(new PendingTask(11L, "OA-2026-200002", "直属部门负责人", "2026-10-02 10:00:00")));

        assertThat(checker.checkOrgSubtree("/1/12/")).isEqualTo(new InFlightSummary(1, 0, List.of("OA-2026-200001")));
        assertThat(checker.checkUser(7L).pendingTasks()).isEqualTo(1);
        assertThat(checker.pendingTasksOf(7L)).hasSize(1);
        assertThat(checker.checkUser(7L).hasInFlight()).isTrue();
        assertThat(InFlightSummary.none().hasInFlight()).isFalse();
        // 影响清单（人员/组织）在假实现里为空 → 出参是空清单而不是 null（施工要求第 3/4 条）
        assertThat(checker.inFlightItems(7L)).isEmpty();
        assertThat(checker.orgInFlightItems("/1/12/")).isEmpty();
        // 批量待办数：缺省实现逐条回退到 checkUser（桩为 0）
        assertThat(checker.pendingTaskCounts(List.of(7L, 8L))).containsEntry(7L, 1).containsEntry(8L, 1);
    }

    // ------------------------------------------------ 业务裁定：管理员 force 可覆盖阻断（AC-52）

    @Test
    @DisplayName("force=false：block=true + 命中 + 5 参重载 → 仍然 409（AC-12 默认阻断不变）")
    void forceFalseStillRejects409() {
        InFlightSummary summary = new InFlightSummary(0, 2, List.of("OA-2026-100003", "OA-2026-100004"));

        assertThatThrownBy(() -> InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙（liyi0002）", summary, true, false))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(409);
                    assertThat(biz.getDetails()).containsEntry("pendingTasks", 2);
                })
                .hasMessageContaining("OA-2026-100003");
    }

    @Test
    @DisplayName("force=true：block=true + 命中 → 不抛异常，且 rejected() 仍为 true 供调用方留痕")
    void forceTrueReleasesWithoutThrowing() {
        InFlightSummary summary = new InFlightSummary(0, 2, List.of("OA-2026-100003", "OA-2026-100004"));

        InFlightGuard.Outcome outcome = InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙（liyi0002）", summary, true, true);

        // 「按开关本应拒绝」这一事实不变（rejected=true），只是不再抛 409
        assertThat(outcome.blocked()).isTrue();
        assertThat(outcome.rejected()).isTrue();
        assertThat(outcome.blockOnInflight()).isTrue();
        assertThat(outcome.summary().pendingTasks()).isEqualTo(2);
        assertThat(outcome.message()).contains("2 条未处理待办").contains("OA-2026-100003");
        // 调用方据此判定「确实发生了覆盖」→ 打运行日志（第二道留痕）
        assertThat(InFlightGuard.forced(outcome, true)).isTrue();
        assertThat(InFlightGuard.forced(outcome, null)).isFalse();
        assertThat(InFlightGuard.forced(outcome, false)).isFalse();
    }

    @Test
    @DisplayName("force=true 对组织拦截同样放行（AC-11 的 409 被覆盖，文案仍带在途数量与单号）")
    void forceTrueReleasesOrgBlock() {
        InFlightSummary summary = new InFlightSummary(3, 0, List.of("ZJ-2026-000118"));

        InFlightGuard.Outcome outcome = InFlightGuard.assertClear(
                InFlightGuard.Subject.ORG_DISABLE, "集团/公司A", summary, true, true);

        assertThat(outcome.rejected()).isTrue();
        assertThat(InFlightGuard.forced(outcome, true)).isTrue();
        assertThat(outcome.message()).contains("3 张在途单据").contains("ZJ-2026-000118");
    }

    @Test
    @DisplayName("未命中在途时 force 无影响：结论与默认口径完全一致")
    void forceHasNoEffectWhenNothingBlocked() {
        InFlightGuard.Outcome withForce = InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙", InFlightSummary.none(), true, true);
        InFlightGuard.Outcome withoutForce = InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_RESIGN, "李乙", InFlightSummary.none(), true, false);

        assertThat(withForce).isEqualTo(withoutForce);
        assertThat(withForce.blocked()).isFalse();
        assertThat(withForce.rejected()).isFalse();
        assertThat(InFlightGuard.forced(withForce, true)).isFalse();
        // 覆盖日志只在「真的覆盖了阻断」时输出（此处不输出，也不会抛异常）
        InFlightGuard.logForceOverride(withForce, true, "不该被记录");
    }

    @Test
    @DisplayName("4 参重载与 force=false 的 5 参重载逐字等价（既有调用语义不变）")
    void fourArgOverloadDelegatesToForceFalse() {
        InFlightSummary summary = new InFlightSummary(0, 1, List.of("OA-2026-100005"));

        assertThatThrownBy(() -> InFlightGuard.assertClear(
                InFlightGuard.Subject.USER_DISABLE, "李乙", summary, true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("停用前必须清空名下待办");

        assertThat(InFlightGuard.assertClear(InFlightGuard.Subject.USER_DISABLE, "李乙", summary, false))
                .isEqualTo(InFlightGuard.assertClear(InFlightGuard.Subject.USER_DISABLE, "李乙", summary, false, false));
    }
}
