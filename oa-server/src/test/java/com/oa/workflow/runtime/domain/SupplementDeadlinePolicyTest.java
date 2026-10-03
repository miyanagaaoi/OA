package com.oa.workflow.runtime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Q7 补件时限</b>的判定单测（2a.4：**就位但不调度**）。
 *
 * <p>权威口径：doc/templates.md §1.7（{@code NULL} = 不设时限；{@code 1..365}；给了天数未给口径 →
 * {@code working}；法定节假日排班属阶段 3）。
 *
 * <p><b>节假日表缺失的诚实披露</b>：doc/data-model.md 的 27 张表里**没有** holiday 表，
 * 因此默认日历只跳周末，并把 {@code holidayCalendarMissing=true} 一路带到出参上
 * —— 不猜测一张不存在的表，也不把工作日偷偷按自然日算。
 */
class SupplementDeadlinePolicyTest {

    /** 2026-07-09 是**周四**（用于手工核对工作日推进）。 */
    private static final LocalDateTime THURSDAY = LocalDateTime.of(2026, 7, 9, 10, 0, 0);

    @Test
    @DisplayName("不设时限：days = NULL → dueAt 为 null，且不报错")
    void unlimited() {
        SupplementDeadlinePolicy.Deadline deadline =
                SupplementDeadlinePolicy.compute(THURSDAY, null, DeadlineType.WORKING);
        assertThat(deadline.dueAt()).isNull();
        assertThat(deadline.unlimited()).isTrue();
        assertThat(deadline.note()).contains("不设时限");
    }

    @Test
    @DisplayName("自然日口径：直接 +N 天，不跳周末（日历口径 = calendar）")
    void calendarType() {
        SupplementDeadlinePolicy.Deadline deadline =
                SupplementDeadlinePolicy.compute(THURSDAY, 3, DeadlineType.CALENDAR);
        assertThat(deadline.dueAt()).isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 0));
        assertThat(deadline.type()).isEqualTo(DeadlineType.CALENDAR);
        assertThat(deadline.skippedNonWorkingDays()).isZero();
        assertThat(deadline.note()).contains("自然日");
    }

    @Test
    @DisplayName("工作日口径：跳周末 —— 周四起 3 个工作日 = 下周二（跳过周六、周日）")
    void workingTypeSkipsWeekend() {
        SupplementDeadlinePolicy.Deadline deadline =
                SupplementDeadlinePolicy.compute(THURSDAY, 3, DeadlineType.WORKING);
        assertThat(THURSDAY.toLocalDate().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.THURSDAY);
        assertThat(deadline.dueAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 14)); // 周二
        assertThat(deadline.skippedNonWorkingDays()).isEqualTo(2);
        assertThat(deadline.holidayCalendarMissing()).isTrue();
        assertThat(deadline.note()).contains("工作日").contains("跳周末").contains("法定节假日表不存在");
    }

    @Test
    @DisplayName("未给口径但给了天数 → 按 working（§1.7 定稿）")
    void nullTypeFallsBackToWorking() {
        SupplementDeadlinePolicy.Deadline deadline = SupplementDeadlinePolicy.compute(THURSDAY, 1, null);
        assertThat(deadline.type()).isEqualTo(DeadlineType.WORKING);
        assertThat(deadline.dueAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 10)); // 周五
    }

    @Test
    @DisplayName("节假日日历可注入（阶段 3 接排班表时的同一入口）：显式假期一并跳过")
    void injectableHolidayCalendar() {
        // 2026-07-10（周五）设为法定假日 → 3 个工作日变成下周三
        SupplementDeadlinePolicy.HolidayCalendar calendar = SupplementDeadlinePolicy.holidays(
                "unit-test-holidays", Set.of(LocalDate.of(2026, 7, 10)));
        SupplementDeadlinePolicy.Deadline deadline = SupplementDeadlinePolicy.compute(THURSDAY, 3,
                DeadlineType.WORKING, calendar, false);
        assertThat(deadline.dueAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 15)); // 周三
        assertThat(deadline.holidayCalendarMissing()).isFalse();
        assertThat(deadline.note()).contains("unit-test-holidays");
        assertThat(deadline.skippedNonWorkingDays()).isEqualTo(3); // 周五(假) + 周六 + 周日
    }

    @Test
    @DisplayName("天数 <= 0 一律拒绝（不设时限请留空，§1.7 校验）")
    void invalidDays() {
        assertThatThrownBy(() -> SupplementDeadlinePolicy.compute(THURSDAY, 0, DeadlineType.WORKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须 > 0");
        assertThatThrownBy(() -> SupplementDeadlinePolicy.compute(THURSDAY, -1, DeadlineType.CALENDAR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupplementDeadlinePolicy.compute(null, 3, DeadlineType.WORKING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("基准时间");
    }

    @Test
    @DisplayName("长期限（365 个工作日）不会死循环：> 1 年零 1 周")
    void longDeadlineTerminates() {
        SupplementDeadlinePolicy.Deadline deadline =
                SupplementDeadlinePolicy.compute(THURSDAY, 365, DeadlineType.WORKING);
        assertThat(deadline.dueAt()).isAfter(THURSDAY.plusDays(365));
        assertThat(deadline.skippedNonWorkingDays()).isGreaterThan(100);
    }

    @Test
    @DisplayName("isWorkingDay：周末为否；注入假期后亦为否")
    void workingDayCheck() {
        assertThat(SupplementDeadlinePolicy.isWorkingDay(LocalDate.of(2026, 7, 10),
                SupplementDeadlinePolicy.WEEKEND_ONLY)).isTrue();
        assertThat(SupplementDeadlinePolicy.isWorkingDay(LocalDate.of(2026, 7, 11),
                SupplementDeadlinePolicy.WEEKEND_ONLY)).isFalse();
        assertThat(SupplementDeadlinePolicy.isWorkingDay(LocalDate.of(2026, 7, 12),
                SupplementDeadlinePolicy.WEEKEND_ONLY)).isFalse();
        SupplementDeadlinePolicy.HolidayCalendar holiday = SupplementDeadlinePolicy.holidays("h",
                Set.of(LocalDate.of(2026, 7, 10)));
        assertThat(SupplementDeadlinePolicy.isWorkingDay(LocalDate.of(2026, 7, 10), holiday)).isFalse();
        assertThat(SupplementDeadlinePolicy.isWorkingDay(null, holiday)).isFalse();
    }

    @Test
    @DisplayName("超时判定：仅用于阶段 3 的扫描与出参回显，本工作包不据此改状态")
    void timedOut() {
        LocalDateTime due = LocalDateTime.of(2026, 7, 14, 10, 0);
        assertThat(SupplementDeadlinePolicy.timedOut(due, due.minusMinutes(1))).isFalse();
        assertThat(SupplementDeadlinePolicy.timedOut(due, due)).isFalse();
        assertThat(SupplementDeadlinePolicy.timedOut(due, due.plusMinutes(1))).isTrue();
        assertThat(SupplementDeadlinePolicy.timedOut(null, due.plusYears(1)))
                .as("不设时限 → 永不超时")
                .isFalse();
        assertThat(SupplementDeadlinePolicy.executionTodo())
                .as("超时策略的**执行**属阶段 3，必须显式留 TODO")
                .contains("TODO(阶段3)")
                .contains("onSupplementTimeout");
    }
}
