package com.oa.common.security;

import com.oa.common.config.OaProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录失败锁定的 Redis 实现。
 *
 * <p>缓存键：{@code auth:fail:{account}}（与 normify {@code oa.identity.session.lockout} 契约一致），
 * 值形如 {@code failures:lockedUntilEpochMillis}，TTL = max(锁定时长, 计数窗口)。
 * Redis 不可用时由 {@link LoginAttemptGuard.InMemory} 降级（见 doc/tech-design.md §2 的降级路径）。
 */
@Service
public class RedisLoginAttemptGuard implements LoginAttemptGuard {

    /** 缓存键前缀（normify 契约：auth:fail:{account}）。 */
    public static final String KEY_PREFIX = "auth:fail:";

    private final StringRedisTemplate redisTemplate;
    private final int maxFailures;
    private final Duration lockDuration;
    private final Duration window;

    public RedisLoginAttemptGuard(StringRedisTemplate redisTemplate, OaProperties properties) {
        this.redisTemplate = redisTemplate;
        OaProperties.Security security = properties.getSecurity();
        this.maxFailures = Math.max(1, security.getLoginMaxFailures());
        this.lockDuration = Duration.ofMinutes(Math.max(1, security.getLoginLockMinutes()));
        this.window = Duration.ofMinutes(Math.max(1, security.getLoginFailWindowMinutes()));
    }

    @Override
    public LockState check(String account) {
        String key = key(account);
        if (key == null) {
            return LockState.unlocked(0, maxFailures);
        }
        return parse(redisTemplate.opsForValue().get(key));
    }

    @Override
    public LockState recordFailure(String account) {
        String key = key(account);
        if (key == null) {
            return LockState.unlocked(0, maxFailures);
        }
        LockState current = parse(redisTemplate.opsForValue().get(key));
        if (current.locked()) {
            return current;
        }
        int failures = current.failures() + 1;
        Instant now = Instant.now();
        if (failures >= maxFailures) {
            Instant lockedUntil = now.plus(lockDuration);
            write(key, failures, lockedUntil, lockDuration);
            return new LockState(true, failures, Duration.between(now, lockedUntil).getSeconds(), maxFailures);
        }
        write(key, failures, null, window);
        return LockState.unlocked(failures, maxFailures);
    }

    @Override
    public void reset(String account) {
        String key = key(account);
        if (key != null) {
            redisTemplate.delete(key);
        }
    }

    private void write(String key, int failures, Instant lockedUntil, Duration ttl) {
        String value = failures + ":" + (lockedUntil == null ? 0L : lockedUntil.toEpochMilli());
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    private LockState parse(String value) {
        if (value == null || value.isBlank()) {
            return LockState.unlocked(0, maxFailures);
        }
        try {
            String[] parts = value.split(":");
            int failures = Integer.parseInt(parts[0]);
            long lockedUntilMillis = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
            if (lockedUntilMillis > 0) {
                Instant lockedUntil = Instant.ofEpochMilli(lockedUntilMillis);
                Instant now = Instant.now();
                if (now.isBefore(lockedUntil)) {
                    return new LockState(true, failures, Duration.between(now, lockedUntil).getSeconds(), maxFailures);
                }
            }
            return LockState.unlocked(failures, maxFailures);
        } catch (RuntimeException ex) {
            return LockState.unlocked(0, maxFailures);
        }
    }

    private static String key(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        return KEY_PREFIX + account.trim().toLowerCase(Locale.ROOT);
    }
}
