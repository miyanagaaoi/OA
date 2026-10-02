package com.oa.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话存储：Redis 注册表 + {@code sys_user_session} 持久化（REQ-NFR-006 / REQ-USER-003）。
 *
 * <p>契约：
 * <ul>
 *   <li>Cookie 只承载**不透明随机令牌**；服务端只存 {@code SHA-256(token)}（{@code sys_user_session.token_hash}），
 *       Redis 键为 {@code auth:session:{sha256(token)}}（与 normify {@code oa.identity.session.login.issue}
 *       的 {@code auth:session:{token}} 契约对齐，且不落明文令牌）；</li>
 *   <li>多设备上限可配（默认 3），超出对 {@code login_at} 最早的**有效**会话软踢出
 *       （写 {@code revoked_at} + {@code revoked_reason='kicked'}，不物理删除）；</li>
 *   <li>「记住我」= 7 天；未勾选 = 会话级（默认 12 小时，可配）。</li>
 * </ul>
 *
 * <p>生产实现 {@link RedisSessionStore}；接口内的 {@link InMemory} 供单测与无 Redis 降级场景。
 */
public interface SessionStore {

    int DEFAULT_MAX_DEVICES = 3;

    long DEFAULT_REMEMBER_ME_DAYS = 7;

    /** 生成 32 字节随机令牌（Base64 URL，无填充）。 */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 令牌哈希：SHA-256 十六进制小写（长度 64，与 DDL 的 {@code CHAR(64)} 一致）。 */
    static String hashToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    SecureRandom RANDOM = new SecureRandom();

    /** 签发新会话（并按上限踢出最早会话）。 */
    IssuedSession create(Long userId, String deviceFingerprint, String ip, String userAgent, boolean rememberMe);

    /** 按原始令牌查找有效会话；无效/过期返回 {@link Optional#empty()}。 */
    Optional<SessionInfo> find(String rawToken);

    /** 活跃续期（访问时调用，内部按间隔节流）。 */
    void touch(String rawToken);

    /** 按原始令牌软撤销（登出）。 */
    void revoke(String rawToken, String reason);

    /** 按会话 id 软撤销（管理员远程注销设备）。 */
    void revokeById(Long sessionId, Long userId, String reason);

    /** 撤销某用户全部有效会话（改密 / 停用 / 离职）。 */
    int revokeAll(Long userId, String reason);

    /** 有效会话列表（按 {@code login_at} 升序）。 */
    List<SessionInfo> listActive(Long userId);

    /**
     * 新签发的会话。
     *
     * @param token   原始令牌（**只在签发瞬间返回**，用于写 Cookie）
     * @param session 会话信息
     * @param evicted 因超出设备上限被踢出的会话
     */
    record IssuedSession(String token, SessionInfo session, List<SessionInfo> evicted) {
    }

    /**
     * 会话快照（不含原始令牌）。
     */
    record SessionInfo(
            Long sessionId,
            Long userId,
            String tokenHash,
            String deviceFingerprint,
            String ip,
            String userAgent,
            LocalDateTime loginAt,
            LocalDateTime lastActiveAt,
            LocalDateTime expiresAt,
            boolean rememberMe
    ) {
    }

    /**
     * 进程内实现（单测 / 无 Redis 降级）。**不持久化**，重启即全部失效。
     */
    final class InMemory implements SessionStore {

        private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong(1);
        private int maxDevices;

        public InMemory() {
            this(DEFAULT_MAX_DEVICES);
        }

        public InMemory(int maxDevices) {
            this.maxDevices = Math.max(1, maxDevices);
        }

        public void setMaxDevices(int maxDevices) {
            this.maxDevices = Math.max(1, maxDevices);
        }

        @Override
        public synchronized IssuedSession create(Long userId, String deviceFingerprint, String ip, String userAgent,
                                                 boolean rememberMe) {
            LocalDateTime now = LocalDateTime.now();
            String token = SessionStore.newToken();
            String tokenHash = SessionStore.hashToken(token);
            SessionInfo info = new SessionInfo(ids.getAndIncrement(), userId, tokenHash, deviceFingerprint, ip,
                    userAgent, now, now, now.plusDays(rememberMe ? DEFAULT_REMEMBER_ME_DAYS : 1), rememberMe);
            sessions.put(tokenHash, info);

            List<SessionInfo> evicted = new ArrayList<>();
            List<SessionInfo> active = listActive(userId);
            while (active.size() > maxDevices) {
                SessionInfo oldest = active.remove(0);
                if (oldest.sessionId().equals(info.sessionId())) {
                    continue;
                }
                sessions.remove(oldest.tokenHash());
                evicted.add(oldest);
            }
            return new IssuedSession(token, info, evicted);
        }

        @Override
        public Optional<SessionInfo> find(String rawToken) {
            if (rawToken == null) {
                return Optional.empty();
            }
            SessionInfo info = sessions.get(SessionStore.hashToken(rawToken));
            if (info == null || info.expiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty();
            }
            return Optional.of(info);
        }

        @Override
        public void touch(String rawToken) {
            // 进程内实现无持久化需求
        }

        @Override
        public void revoke(String rawToken, String reason) {
            if (rawToken != null) {
                sessions.remove(SessionStore.hashToken(rawToken));
            }
        }

        @Override
        public void revokeById(Long sessionId, Long userId, String reason) {
            sessions.values().removeIf(info -> info.sessionId().equals(sessionId)
                    && (userId == null || userId.equals(info.userId())));
        }

        @Override
        public int revokeAll(Long userId, String reason) {
            int[] count = {0};
            sessions.values().removeIf(info -> {
                boolean match = info.userId().equals(userId);
                if (match) {
                    count[0]++;
                }
                return match;
            });
            return count[0];
        }

        @Override
        public List<SessionInfo> listActive(Long userId) {
            LocalDateTime now = LocalDateTime.now();
            List<SessionInfo> result = new ArrayList<>();
            for (SessionInfo info : sessions.values()) {
                if (info.userId().equals(userId) && info.expiresAt().isAfter(now)) {
                    result.add(info);
                }
            }
            result.sort((left, right) -> left.loginAt().compareTo(right.loginAt()));
            return result;
        }

        /** 便于测试：当前有效会话数。 */
        public int size() {
            return sessions.size();
        }

        /** 便于测试：按账号归一化（保留方法以便与生产实现一致的语义）。 */
        public static String normalize(String value) {
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
