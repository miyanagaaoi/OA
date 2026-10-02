package com.oa.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定（REQ-NFR-005）：连续失败 **5 次锁 15 分钟**，成功登录清零。
 *
 * <p>生产实现为 {@link RedisLoginAttemptGuard}（缓存键 {@code auth:fail:{account}}，
 * 与 normify 模块 {@code oa.identity.session.lockout} 的契约一致）；本接口内的
 * {@link InMemory} 是**不带任何外部依赖**的纯逻辑实现，供单元测试与「无 Redis 降级」使用
 * （对应 doc/tech-design.md §2 的降级路径：Caffeine/进程内计数 + 数据库会话表）。
 */
public interface LoginAttemptGuard {

    int DEFAULT_MAX_FAILURES = 5;

    long DEFAULT_LOCK_MINUTES = 15;

    int DEFAULT_WINDOW_MINUTES = 15;

    /** 查询当前锁定状态（不计数）。 */
    LockState check(String account);

    /** 记录一次失败并返回最新状态。 */
    LockState recordFailure(String account);

    /** 登录成功 / 管理员解锁时清零。 */
    void reset(String account);

    /**
     * 锁定状态快照。
     *
     * @param locked               当前是否锁定
     * @param failures             当前累计失败次数（窗口内）
     * @param remainingLockSeconds 剩余锁定秒数，未锁定为 0
     * @param maxFailures          上限（用于提示文案）
     */
    record LockState(boolean locked, int failures, long remainingLockSeconds, int maxFailures) {

        public static LockState unlocked(int failures, int maxFailures) {
            return new LockState(false, failures, 0L, maxFailures);
        }

        /** 剩余锁定分钟数（向上取整，用于「请于 N 分钟后重试」文案）。 */
        public long remainingMinutes() {
            return (remainingLockSeconds + 59) / 60;
        }
    }

    /**
     * 进程内实现（纯逻辑，可用 {@link Clock} 驱动时间，单测无需 DB/Redis）。
     *
     * <p>并发安全；不做容量淘汰（300 人规模下无必要，键数上限 = 账号数）。
     */
    final class InMemory implements LoginAttemptGuard {

        private final Map<String, Entry> entries = new ConcurrentHashMap<>();
        private final Clock clock;
        private final int maxFailures;
        private final Duration lockDuration;
        private final Duration window;

        public InMemory() {
            this(Clock.systemUTC(), DEFAULT_MAX_FAILURES,
                    Duration.ofMinutes(DEFAULT_LOCK_MINUTES), Duration.ofMinutes(DEFAULT_WINDOW_MINUTES));
        }

        public InMemory(Clock clock, int maxFailures, Duration lockDuration, Duration window) {
            this.clock = clock == null ? Clock.systemUTC() : clock;
            this.maxFailures = Math.max(1, maxFailures);
            this.lockDuration = lockDuration == null ? Duration.ofMinutes(DEFAULT_LOCK_MINUTES) : lockDuration;
            this.window = window == null ? Duration.ofMinutes(DEFAULT_WINDOW_MINUTES) : window;
        }

        @Override
        public LockState check(String account) {
            String key = normalize(account);
            if (key == null) {
                return LockState.unlocked(0, maxFailures);
            }
            Entry entry = entries.get(key);
            if (entry == null) {
                return LockState.unlocked(0, maxFailures);
            }
            synchronized (entry) {
                return evaluate(key, entry);
            }
        }

        @Override
        public LockState recordFailure(String account) {
            String key = normalize(account);
            if (key == null) {
                return LockState.unlocked(0, maxFailures);
            }
            Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
            synchronized (entry) {
                Instant now = clock.instant();
                if (entry.lockedUntil != null && now.isBefore(entry.lockedUntil)) {
                    return evaluate(key, entry);
                }
                if (entry.lockedUntil != null) {
                    // 锁定已到期 → 重新计数
                    entry.failures = 0;
                    entry.lockedUntil = null;
                    entry.firstFailureAt = null;
                }
                if (entry.firstFailureAt == null || Duration.between(entry.firstFailureAt, now).compareTo(window) > 0) {
                    entry.failures = 0;
                    entry.firstFailureAt = now;
                }
                entry.failures++;
                if (entry.failures >= maxFailures) {
                    entry.lockedUntil = now.plus(lockDuration);
                    return evaluate(key, entry);
                }
                return LockState.unlocked(entry.failures, maxFailures);
            }
        }

        @Override
        public void reset(String account) {
            String key = normalize(account);
            if (key != null) {
                entries.remove(key);
            }
        }

        private LockState evaluate(String key, Entry entry) {
            Instant now = clock.instant();
            if (entry.lockedUntil != null) {
                if (now.isBefore(entry.lockedUntil)) {
                    return new LockState(true, entry.failures,
                            Duration.between(now, entry.lockedUntil).getSeconds(), maxFailures);
                }
                entry.lockedUntil = null;
                entry.failures = 0;
                entry.firstFailureAt = null;
                entries.remove(key, entry);
                return LockState.unlocked(0, maxFailures);
            }
            if (entry.firstFailureAt != null
                    && Duration.between(entry.firstFailureAt, now).compareTo(window) > 0) {
                entry.failures = 0;
                entry.firstFailureAt = null;
                entries.remove(key, entry);
                return LockState.unlocked(0, maxFailures);
            }
            return LockState.unlocked(entry.failures, maxFailures);
        }

        private static String normalize(String account) {
            if (account == null || account.isBlank()) {
                return null;
            }
            return account.trim().toLowerCase(java.util.Locale.ROOT);
        }

        private static final class Entry {
            private int failures;
            private Instant firstFailureAt;
            private Instant lockedUntil;
        }
    }
}
