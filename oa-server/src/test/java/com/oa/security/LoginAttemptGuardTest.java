package com.oa.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.oa.common.security.LoginAttemptGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 登录失败锁定纯逻辑单测（REQ-NFR-005：连续失败 5 次锁 15 分钟）。
 *
 * <p>使用 {@link LoginAttemptGuard.InMemory} + 可变时钟，**不依赖 DB / Redis / Spring 容器**。
 * 生产实现（{@link com.oa.common.security.RedisLoginAttemptGuard}）与内存实现共享同一接口与常量口径。
 */
class LoginAttemptGuardTest {

    private static final String ACCOUNT = "zhangsan";

    /** 可手动推进的时钟。 */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-02T09:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private static LoginAttemptGuard.InMemory guard(MutableClock clock) {
        return new LoginAttemptGuard.InMemory(clock, LoginAttemptGuard.DEFAULT_MAX_FAILURES,
                Duration.ofMinutes(LoginAttemptGuard.DEFAULT_LOCK_MINUTES),
                Duration.ofMinutes(LoginAttemptGuard.DEFAULT_WINDOW_MINUTES));
    }

    @Test
    @DisplayName("口径常量：5 次 / 15 分钟")
    void defaults() {
        assertThat(LoginAttemptGuard.DEFAULT_MAX_FAILURES).isEqualTo(5);
        assertThat(LoginAttemptGuard.DEFAULT_LOCK_MINUTES).isEqualTo(15);
    }

    @Test
    @DisplayName("前 4 次失败不锁定，第 5 次锁定 15 分钟")
    void lockAfterFiveFailures() {
        MutableClock clock = new MutableClock();
        LoginAttemptGuard guard = guard(clock);

        for (int i = 1; i <= 4; i++) {
            LoginAttemptGuard.LockState state = guard.recordFailure(ACCOUNT);
            assertThat(state.locked()).as("第 %d 次失败不应锁定", i).isFalse();
            assertThat(state.failures()).isEqualTo(i);
            assertThat(state.remainingLockSeconds()).isZero();
        }

        LoginAttemptGuard.LockState fifth = guard.recordFailure(ACCOUNT);
        assertThat(fifth.locked()).isTrue();
        assertThat(fifth.failures()).isEqualTo(5);
        assertThat(fifth.remainingLockSeconds()).isEqualTo(Duration.ofMinutes(15).getSeconds());
        assertThat(fifth.remainingMinutes()).isEqualTo(15);

        // 锁定期间 check / recordFailure 都返回锁定
        assertThat(guard.check(ACCOUNT).locked()).isTrue();
        assertThat(guard.recordFailure(ACCOUNT).locked()).isTrue();
    }

    @Test
    @DisplayName("锁定 15 分钟后自动解锁且计数清零")
    void unlockAfterLockWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptGuard guard = guard(clock);
        for (int i = 0; i < 5; i++) {
            guard.recordFailure(ACCOUNT);
        }
        assertThat(guard.check(ACCOUNT).locked()).isTrue();

        clock.advance(Duration.ofMinutes(14));
        assertThat(guard.check(ACCOUNT).locked()).isTrue();
        assertThat(guard.check(ACCOUNT).remainingMinutes()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        assertThat(guard.check(ACCOUNT).locked()).isFalse();
        assertThat(guard.check(ACCOUNT).failures()).isZero();

        // 解锁后重新计数
        assertThat(guard.recordFailure(ACCOUNT).failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("登录成功（reset）后计数清零")
    void resetOnSuccess() {
        MutableClock clock = new MutableClock();
        LoginAttemptGuard guard = guard(clock);
        guard.recordFailure(ACCOUNT);
        guard.recordFailure(ACCOUNT);
        guard.recordFailure(ACCOUNT);
        assertThat(guard.check(ACCOUNT).failures()).isEqualTo(3);

        guard.reset(ACCOUNT);
        assertThat(guard.check(ACCOUNT).failures()).isZero();
        assertThat(guard.recordFailure(ACCOUNT).failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("超出计数窗口（15 分钟）后失败次数清零")
    void windowExpiry() {
        MutableClock clock = new MutableClock();
        LoginAttemptGuard guard = guard(clock);
        guard.recordFailure(ACCOUNT);
        guard.recordFailure(ACCOUNT);
        assertThat(guard.check(ACCOUNT).failures()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(16));
        assertThat(guard.check(ACCOUNT).failures()).isZero();
        assertThat(guard.recordFailure(ACCOUNT).failures()).isEqualTo(1);
    }

    @Test
    @DisplayName("账号归一化：大小写与首尾空格视为同一账号")
    void accountNormalization() {
        MutableClock clock = new MutableClock();
        LoginAttemptGuard guard = guard(clock);
        guard.recordFailure("  ZhangSan  ");
        guard.recordFailure("zhangsan");
        assertThat(guard.check("ZHANGSAN").failures()).isEqualTo(2);

        guard.reset(" Zhangsan ");
        assertThat(guard.check(ACCOUNT).failures()).isZero();
    }

    @Test
    @DisplayName("剩余分钟数向上取整（用于『请于 N 分钟后重试』）")
    void remainingMinutesRoundsUp() {
        assertThat(new LoginAttemptGuard.LockState(true, 5, 1, 5).remainingMinutes()).isEqualTo(1);
        assertThat(new LoginAttemptGuard.LockState(true, 5, 61, 5).remainingMinutes()).isEqualTo(2);
        assertThat(new LoginAttemptGuard.LockState(true, 5, 900, 5).remainingMinutes()).isEqualTo(15);
        assertThat(LoginAttemptGuard.LockState.unlocked(2, 5).remainingMinutes()).isZero();
    }

    @Test
    @DisplayName("空账号不计数、不锁定（防御性）")
    void blankAccountIsIgnored() {
        MutableClock clock = new MutableClock();
        LoginAttemptGuard guard = guard(clock);
        assertThat(guard.recordFailure("   ").locked()).isFalse();
        assertThat(guard.check(null).locked()).isFalse();
    }
}
