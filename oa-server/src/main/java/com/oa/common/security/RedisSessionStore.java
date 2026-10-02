package com.oa.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.common.config.OaProperties;
import com.oa.identity.domain.SysUserSession;
import com.oa.identity.infra.SysUserSessionMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话存储的生产实现：Redis 注册表（{@code auth:session:{sha256(token)}}）+ {@code sys_user_session} 持久化。
 *
 * <p>设计取舍：
 * <ul>
 *   <li>Redis 键用**令牌哈希**而非明文令牌 —— 与 normify 契约的 {@code auth:session:{token}} 语义一致，
 *       同时满足「只存哈希，禁止存明文令牌」的 DDL 注释；</li>
 *   <li>DB 是权威（Redis 丢失可自愈：{@link #find} 未命中时回查 DB 并回填 Redis）；</li>
 *   <li>踢出只写 {@code revoked_at}/{@code revoked_reason}，不物理删除，保留审计与设备历史。</li>
 * </ul>
 */
@Service
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);

    /** Redis 会话注册表键前缀（normify 契约：auth:session:{token}）。 */
    public static final String SESSION_KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate redisTemplate;
    private final SysUserSessionMapper sessionMapper;
    private final OaProperties properties;

    public RedisSessionStore(StringRedisTemplate redisTemplate, SysUserSessionMapper sessionMapper,
                             OaProperties properties) {
        this.redisTemplate = redisTemplate;
        this.sessionMapper = sessionMapper;
        this.properties = properties;
    }

    @Override
    public IssuedSession create(Long userId, String deviceFingerprint, String ip, String userAgent, boolean rememberMe) {
        OaProperties.Session session = properties.getSession();
        LocalDateTime now = LocalDateTime.now();
        Duration ttl = rememberMe
                ? Duration.ofDays(session.getRememberMeDays())
                : Duration.ofHours(session.getTemporaryHours());

        String rawToken = SessionStore.newToken();
        String tokenHash = SessionStore.hashToken(rawToken);

        SysUserSession row = new SysUserSession();
        row.setUserId(userId);
        row.setDeviceFingerprint(trim(deviceFingerprint, 128));
        row.setIp(trim(ip, 64));
        row.setUserAgent(trim(userAgent, 255));
        row.setTokenHash(tokenHash);
        row.setLoginAt(now);
        row.setLastActiveAt(now);
        row.setExpiresAt(now.plus(ttl));
        row.setCreatedAt(now);
        sessionMapper.insert(row);

        registerInRedis(tokenHash, userId, ttl);

        List<SessionInfo> evicted = enforceDeviceLimit(userId, row.getId(), now);
        if (!evicted.isEmpty()) {
            log.info("账号 {} 超过设备上限 {}，已软踢出最早会话 {}", userId, session.getMaxDevices(),
                    evicted.stream().map(SessionInfo::sessionId).toList());
        }
        return new IssuedSession(rawToken, toInfo(row, rememberMe), evicted);
    }

    @Override
    public Optional<SessionInfo> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = SessionStore.hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now();

        SysUserSession row = sessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getTokenHash, tokenHash)
                .last("LIMIT 1"));
        if (row == null || !row.isActive(now)) {
            redisTemplate.delete(SESSION_KEY_PREFIX + tokenHash);
            return Optional.empty();
        }

        String key = SESSION_KEY_PREFIX + tokenHash;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            // Redis 丢失（重启/驱逐）→ 以 DB 为准回填，保证会话连续性
            Duration remaining = Duration.between(now, row.getExpiresAt());
            if (!remaining.isNegative() && !remaining.isZero()) {
                registerInRedis(tokenHash, row.getUserId(), remaining);
            }
        }
        return Optional.of(toInfo(row, false));
    }

    @Override
    public void touch(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = SessionStore.hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now();
        SysUserSession row = sessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getTokenHash, tokenHash)
                .last("LIMIT 1"));
        if (row == null || !row.isActive(now)) {
            return;
        }
        long interval = Math.max(30, properties.getSession().getTouchIntervalSeconds());
        LocalDateTime lastActive = row.getLastActiveAt() == null ? row.getLoginAt() : row.getLastActiveAt();
        if (lastActive == null || Duration.between(lastActive, now).getSeconds() >= interval) {
            row.setLastActiveAt(now);
            sessionMapper.updateById(row);
        }
    }

    @Override
    public void revoke(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = SessionStore.hashToken(rawToken);
        SysUserSession row = sessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getTokenHash, tokenHash)
                .last("LIMIT 1"));
        if (row != null) {
            revokeEntity(row, reason, LocalDateTime.now());
        }
        redisTemplate.delete(SESSION_KEY_PREFIX + tokenHash);
    }

    @Override
    public void revokeById(Long sessionId, Long userId, String reason) {
        if (sessionId == null) {
            return;
        }
        SysUserSession row = sessionMapper.selectById(sessionId);
        if (row == null || row.getRevokedAt() != null) {
            return;
        }
        if (userId != null && !userId.equals(row.getUserId())) {
            return;
        }
        revokeEntity(row, reason, LocalDateTime.now());
    }

    @Override
    public int revokeAll(Long userId, String reason) {
        List<SysUserSession> active = selectActive(userId);
        LocalDateTime now = LocalDateTime.now();
        for (SysUserSession row : active) {
            revokeEntity(row, reason, now);
        }
        return active.size();
    }

    @Override
    public List<SessionInfo> listActive(Long userId) {
        List<SessionInfo> result = new ArrayList<>();
        for (SysUserSession row : selectActive(userId)) {
            result.add(toInfo(row, false));
        }
        return result;
    }

    // ------------------------------------------------------------------ 内部

    private void registerInRedis(String tokenHash, Long userId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + tokenHash, String.valueOf(userId), ttl);
    }

    private List<SysUserSession> selectActive(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getUserId, userId)
                .isNull(SysUserSession::getRevokedAt)
                .gt(SysUserSession::getExpiresAt, LocalDateTime.now())
                .orderByAsc(SysUserSession::getLoginAt));
    }

    /** 多设备上限：超出时对 login_at 最早的会话软踢出。 */
    private List<SessionInfo> enforceDeviceLimit(Long userId, Long newSessionId, LocalDateTime now) {
        int maxDevices = Math.max(1, properties.getSession().getMaxDevices());
        List<SysUserSession> active = selectActive(userId);
        List<SessionInfo> evicted = new ArrayList<>();
        int index = 0;
        while (active.size() - index > maxDevices && index < active.size()) {
            SysUserSession oldest = active.get(index++);
            if (oldest.getId().equals(newSessionId)) {
                continue;
            }
            revokeEntity(oldest, SysUserSession.REASON_KICKED, now);
            evicted.add(toInfo(oldest, false));
        }
        return evicted;
    }

    private void revokeEntity(SysUserSession row, String reason, LocalDateTime now) {
        row.setRevokedAt(now);
        row.setRevokedReason(reason == null ? SysUserSession.REASON_LOGOUT : reason);
        sessionMapper.updateById(row);
        if (row.getTokenHash() != null) {
            redisTemplate.delete(SESSION_KEY_PREFIX + row.getTokenHash());
        }
    }

    private static SessionInfo toInfo(SysUserSession row, boolean rememberMe) {
        return new SessionInfo(row.getId(), row.getUserId(), row.getTokenHash(), row.getDeviceFingerprint(),
                row.getIp(), row.getUserAgent(), row.getLoginAt(), row.getLastActiveAt(), row.getExpiresAt(), rememberMe);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
