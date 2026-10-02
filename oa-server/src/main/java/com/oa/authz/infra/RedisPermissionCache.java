package com.oa.authz.infra;

import com.oa.authz.app.PermissionCache;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 权限缓存的生产实现：Redis {@code authz:perms:{userId}}（值为权限码，换行分隔）。
 *
 * <p>为什么用换行分隔而不是 JSON：权限码是小写蛇形 + 冒号（{@code flow:task:approve}），
 * 不含空白，换行分隔可零依赖解析；同时避免为了一个字符串集合引入额外序列化约定。
 *
 * <p>**降级口径**：Redis 不可用时 {@link #get} 返回未命中、{@link #put}/{@link #invalidate}
 * 只记 WARN —— 权限计算的权威源始终是 MySQL（{@code sys_role_permission}），
 * 缓存只是加速层，缺失只会让下次请求回源，绝不返回错误权限。
 */
@Repository
public class RedisPermissionCache implements PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(RedisPermissionCache.class);

    private final StringRedisTemplate redisTemplate;

    public RedisPermissionCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Set<String>> get(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(PermissionCache.key(userId));
            if (value == null) {
                return Optional.empty();
            }
            Set<String> codes = new LinkedHashSet<>();
            for (String line : value.split("\n")) {
                if (!line.isBlank()) {
                    codes.add(line.trim());
                }
            }
            return Optional.of(codes);
        } catch (RuntimeException ex) {
            log.warn("读取权限缓存失败（按未命中处理，回源 MySQL）：userId={} error={}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long userId, java.util.Collection<String> codes, Duration ttl) {
        if (userId == null) {
            return;
        }
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? PermissionCache.DEFAULT_TTL : ttl;
        try {
            redisTemplate.opsForValue().set(PermissionCache.key(userId), String.join("\n", codes == null ? Set.of() : codes),
                    effective);
        } catch (RuntimeException ex) {
            log.warn("写入权限缓存失败（不影响业务）：userId={} error={}", userId, ex.getMessage());
        }
    }

    @Override
    public boolean invalidate(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(PermissionCache.key(userId)));
        } catch (RuntimeException ex) {
            log.warn("失效权限缓存失败：userId={} error={}", userId, ex.getMessage());
            return false;
        }
    }

    /** 全量失效：权限树规模有限、键数量 = 在线用户数，用前缀扫描删除（不做 KEYS 大范围扫描以外的优化）。 */
    @Override
    public int invalidateAll() {
        try {
            Set<String> keys = redisTemplate.keys(PermissionCache.KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Long removed = redisTemplate.delete(keys);
            return removed == null ? 0 : removed.intValue();
        } catch (RuntimeException ex) {
            log.warn("全量失效权限缓存失败：{}", ex.getMessage());
            return 0;
        }
    }
}
