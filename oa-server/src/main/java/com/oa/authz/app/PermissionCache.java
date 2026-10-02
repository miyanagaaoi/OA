package com.oa.authz.app;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 有效权限缓存（Redis 键 {@code authz:perms:{userId}}，TTL 10 分钟）。
 *
 * <p>契约（normify {@code oa.authz.rbac.effective}）：
 * <ul>
 *   <li>缓存的是**权限码集合**（角色权限的并集），不是权限 id —— {@code /auth/me} 与
 *       {@code /effective-permissions} 直接消费它；</li>
 *   <li>角色权限勾选变更、角色级联删除、数据域/类别变更、用户角色分配与撤销后
 *       **必须失效**相关用户（见 {@code EffectivePermissionService} 的各个调用点）；</li>
 *   <li>失效失败不得影响业务（调用方吞掉异常并记 WARN），但会被记为缓存缺口。</li>
 * </ul>
 *
 * <p>实现：生产用 {@code com.oa.authz.infra.RedisPermissionCache}；
 * 接口内的 {@link InMemory} 供单测与无 Redis 降级场景（口径与 {@code SessionStore.InMemory} 一致）。
 */
public interface PermissionCache {

    /** 键前缀（契约 {@code authz:perms:{userId}}）。 */
    String KEY_PREFIX = "authz:perms:";

    /** 建议 TTL：10 分钟。 */
    Duration DEFAULT_TTL = Duration.ofMinutes(10);

    static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    /** 读取；未命中返回 {@link Optional#empty()}。 */
    Optional<Set<String>> get(Long userId);

    /** 写入（默认 TTL）。 */
    default void put(Long userId, Collection<String> codes) {
        put(userId, codes, DEFAULT_TTL);
    }

    /** 写入（指定 TTL）。 */
    void put(Long userId, Collection<String> codes, Duration ttl);

    /** 失效单个用户；返回是否确实删除了键。 */
    boolean invalidate(Long userId);

    /** 全量失效；返回删除的键数量。 */
    int invalidateAll();

    /** 进程内实现（单测 / 无 Redis 降级）。 */
    final class InMemory implements PermissionCache {

        private final Map<Long, Set<String>> store = new ConcurrentHashMap<>();

        @Override
        public Optional<Set<String>> get(Long userId) {
            if (userId == null) {
                return Optional.empty();
            }
            Set<String> codes = store.get(userId);
            return codes == null ? Optional.empty() : Optional.of(codes);
        }

        @Override
        public void put(Long userId, Collection<String> codes, Duration ttl) {
            if (userId == null) {
                return;
            }
            store.put(userId, Collections.unmodifiableSet(new LinkedHashSet<>(
                    codes == null ? Set.of() : codes)));
        }

        @Override
        public boolean invalidate(Long userId) {
            return userId != null && store.remove(userId) != null;
        }

        @Override
        public int invalidateAll() {
            int size = store.size();
            store.clear();
            return size;
        }

        /** 便于测试：当前缓存条目数。 */
        public int size() {
            return store.size();
        }

        /** 便于测试：指定用户是否已缓存。 */
        public boolean contains(Long userId) {
            return userId != null && store.containsKey(userId);
        }
    }
}
