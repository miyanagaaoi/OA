package com.oa.workflow.runtime.domain;

import com.oa.workflow.definition.domain.FlowGateEnums.DeadlineType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <b>Q7「就位但不调度」</b>：补件时限的**应完成时间**计算（2a.4）。
 *
 * <h2>权威口径（doc/templates.md §1.7）</h2>
 * <ul>
 *   <li>{@code supplement_deadline_days}：{@code NULL} = 不设时限；{@code 1..365} 才生效；</li>
 *   <li>{@code supplement_deadline_type}：{@code calendar}（自然日）/ {@code working}（工作日）；
 *       <b>给了天数但未给口径时按 working</b>（{@link FlowGateEnums#effectiveDeadlineType()} 已固化该默认）；</li>
 *   <li>{@code on_supplement_timeout}：{@code notify} / {@code auto_pass} / {@code auto_return} ——
 *       <b>策略的「执行」属阶段 3 调度器</b>（{@code TODO(阶段3)}），本类只做一个判定函数
 *       {@link #timedOut(LocalDateTime, LocalDateTime)} 与披露字段，不自动改状态。</li>
 * </ul>
 *
 * <h2>节假日口径（**待补**，已在出参上显式标注）</h2>
 * <p>doc/templates.md §1.7 原文：「时限口径（<b>法定节假日排班属阶段 3</b>）」。本项目 DDL **没有**节假日表
 * （doc/data-model.md 的 27 张表清单内无 holiday 表，{@code flow_supplement.deadline} 的注释也只写
 * 「默认请求后 3 个工作日（工作日口径，超时仅催办）」）。因此本类的默认日历
 * {@link #WEEKEND_ONLY} **只跳周末**，并把 {@link Deadline#holidayCalendarMissing()} 置为 {@code true}，
 * 由接口/日志显式披露「法定节假日待补」。这样既不猜测一张不存在的表，也不静默把工作日算成自然日。
 *
 * <p>纯函数（可注入 {@link HolidayCalendar} 做穷举单测），无 Spring / DB 依赖。
 */
public final class SupplementDeadlinePolicy {

    /** 节假日日历（阶段 3 接入排班表时的扩展缝；当前生产实现只有 {@link #WEEKEND_ONLY}）。 */
    public interface HolidayCalendar {

        /** 是否为法定节假日 / 非工作日（周末由调用方另行判定，实现只需回答「节假日」）。 */
        boolean isHoliday(LocalDate date);

        /** 可读名称（出参披露用）。 */
        String name();
    }

    /**
     * 默认日历：**只跳周末**（周六、周日），不认法定节假日。
     *
     * <p>DDL 无节假日表，故这是当前唯一可诚实给出的实现；
     * 出参的 {@code holidayCalendarMissing=true} 即该局限的披露。
     */
    public static final HolidayCalendar WEEKEND_ONLY = new HolidayCalendar() {
        @Override
        public boolean isHoliday(LocalDate date) {
            return false;
        }

        @Override
        public String name() {
            return "weekend-only（只跳周末；法定节假日表不存在，待补）";
        }
    };

    /** 用于单测/阶段 3 的显式节假日日历。 */
    public static HolidayCalendar holidays(String name, Set<LocalDate> dates) {
        Set<LocalDate> fixed = dates == null ? Set.of() : Set.copyOf(dates);
        return new HolidayCalendar() {
            @Override
            public boolean isHoliday(LocalDate date) {
                return fixed.contains(date);
            }

            @Override
            public String name() {
                return name == null ? "holidays" : name;
            }
        };
    }

    /**
     * 计算结果。
     *
     * @param dueAt                 应完成时间（{@code null} = 不设时限）
     * @param days                  时限天数（{@code null} = 不设时限）
     * @param type                  时限口径（不设时限时为 {@code null}）
     * @param skippedNonWorkingDays 因非工作日被跳过的天数（可读披露）
     * @param holidayCalendarMissing 节假日表缺失（只跳了周末）
     * @param note                  人类可读说明
     */
    public record Deadline(
            LocalDateTime dueAt,
            Integer days,
            DeadlineType type,
            int skippedNonWorkingDays,
            boolean holidayCalendarMissing,
            String note
    ) {

        /** 是否不设时限。 */
        public boolean unlimited() {
            return dueAt == null;
        }
    }

    private SupplementDeadlinePolicy() {
    }

    /** 按天数与口径计算应完成时间（默认日历：只跳周末）。 */
    public static Deadline compute(LocalDateTime base, Integer days, DeadlineType type) {
        return compute(base, days, type, WEEKEND_ONLY, true);
    }

    /**
     * 按天数与口径计算应完成时间。
     *
     * @param base                   基准时间（请求补件的时刻）
     * @param days                   天数（{@code null} → 不设时限）
     * @param type                   口径（{@code null} 且有天数 → {@code working}，见 §1.7）
     * @param calendar               节假日日历（{@code null} → {@link #WEEKEND_ONLY}）
     * @param holidayCalendarMissing 是否缺节假日表（默认实现为 {@code true}）
     */
    public static Deadline compute(LocalDateTime base, Integer days, DeadlineType type,
                                   HolidayCalendar calendar, boolean holidayCalendarMissing) {
        if (days == null) {
            return new Deadline(null, null, null, 0, holidayCalendarMissing,
                    "未配置 supplementDeadlineDays → 不设时限（§1.7）");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("supplementDeadlineDays 必须 > 0（不设时限请留空），实际 " + days);
        }
        if (base == null) {
            throw new IllegalArgumentException("基准时间不能为空");
        }
        DeadlineType effective = type == null ? DeadlineType.WORKING : type;
        HolidayCalendar hol = calendar == null ? WEEKEND_ONLY : calendar;
        if (effective == DeadlineType.CALENDAR) {
            return new Deadline(base.plusDays(days), days, effective, 0, holidayCalendarMissing,
                    "自然日口径：自 " + base + " 起 " + days + " 个自然日（不跳周末）");
        }
        // 工作日口径：逐日推进，跳过周末与节假日，数满 days 个工作日
        List<LocalDate> skipped = new ArrayList<>();
        LocalDateTime cursor = base;
        int counted = 0;
        int guard = 0;
        int maxIterations = days * 7 + 400; // 防御：即使日历全放假也不会死循环
        while (counted < days && guard++ < maxIterations) {
            cursor = cursor.plusDays(1);
            if (isWorkingDay(cursor.toLocalDate(), hol)) {
                counted++;
            } else {
                skipped.add(cursor.toLocalDate());
            }
        }
        if (counted < days) {
            throw new IllegalStateException("工作日日历无法在合理范围内数满 " + days + " 个工作日，请检查节假日配置");
        }
        return new Deadline(cursor, days, effective, skipped.size(), holidayCalendarMissing,
                "工作日口径：自 " + base + " 起 " + days + " 个工作日（跳周末"
                        + (holidayCalendarMissing ? "；法定节假日表不存在，待补" : "；节假日日历=" + hol.name())
                        + "），跳过非工作日 " + skipped.size() + " 天");
    }

    /** 是否为工作日（非周末且非节假日）。 */
    public static boolean isWorkingDay(LocalDate date, HolidayCalendar calendar) {
        if (date == null) {
            return false;
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        HolidayCalendar hol = calendar == null ? WEEKEND_ONLY : calendar;
        return !hol.isHoliday(date);
    }

    /**
     * 是否已超时（用于阶段 3 的调度扫描与出参回显；本工作包**不**据此改状态）。
     *
     * @param dueAt 应完成时间（{@code null} = 不设时限 → 永不超时）
     * @param now   当前时间
     */
    public static boolean timedOut(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && now != null && now.isAfter(dueAt);
    }

    /** 超时策略的**执行**属阶段 3（本工作包只披露策略值，不自动通过/退回）。 */
    public static String executionTodo() {
        return com.oa.workflow.definition.domain.FlowGateEnums.DEADLINE_TODO;
    }
}
