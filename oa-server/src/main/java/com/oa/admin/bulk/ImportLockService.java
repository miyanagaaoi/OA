package com.oa.admin.bulk;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 导入并发锁（import-spec §6.4）：同一时刻**只允许一个导入任务**，并发直接拒绝。
 *
 * <p>锁键 {@code oa:import:org_user}（规格指定）；Redis 实现为 {@code SET NX EX}，
 * 释放时用 token 比对删除（避免误删他人的锁）。Redis 不可用时**降级为进程内锁**并打 WARN ——
 * 单实例部署下语义等价；多实例部署时该降级会放宽并发约束，因此必须留痕。
 */
@Service
public class ImportLockService {

    private static final Logger log = LoggerFactory.getLogger(ImportLockService.class);

    /** 规格指定的分布式锁键（import-spec §6.4）。 */
    public static final String LOCK_KEY = "oa:import:org_user";

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    private final ReentrantLock localLock = new ReentrantLock();

    private final AtomicReference<String> localOwner = new AtomicReference<>();

    /** 本线程是否走的降级路径（决定释放时用哪种语义）。 */
    private final ThreadLocal<Boolean> localMode = new ThreadLocal<>();

    public ImportLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取导入锁。
     *
     * @return 锁令牌（释放时回传）
     * @throws BizException 409 {@link ErrorCode#IMPORT_IN_PROGRESS}
     */
    public String acquire() {
        String token = UUID.randomUUID().toString();
        Boolean ok;
        try {
            ok = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, TTL);
        } catch (RuntimeException ex) {
            log.warn("Redis 不可用，导入锁降级为进程内锁（多实例部署下并发约束会放宽）：{}", ex.getMessage());
            return acquireLocal(token);
        }
        if (Boolean.TRUE.equals(ok)) {
            localMode.set(Boolean.FALSE);
            return token;
        }
        throw new BizException(ErrorCode.IMPORT_IN_PROGRESS);
    }

    /** 释放锁（token 不匹配时不释放，避免误删他人锁）。 */
    public void release(String token) {
        if (token == null) {
            return;
        }
        if (Boolean.TRUE.equals(localMode.get())) {
            localMode.remove();
            releaseLocal(token);
            return;
        }
        try {
            String current = redisTemplate.opsForValue().get(LOCK_KEY);
            if (token.equals(current)) {
                redisTemplate.delete(LOCK_KEY);
            }
        } catch (RuntimeException ex) {
            log.warn("Redis 不可用，无法按 token 释放导入锁（将依赖 {} 的 TTL 自动过期）：{}", TTL, ex.getMessage());
        }
    }

    private String acquireLocal(String token) {
        if (localLock.tryLock()) {
            localOwner.set(token);
            localMode.set(Boolean.TRUE);
            return token;
        }
        throw new BizException(ErrorCode.IMPORT_IN_PROGRESS);
    }

    private void releaseLocal(String token) {
        if (token.equals(localOwner.get())) {
            localOwner.set(null);
            if (localLock.isHeldByCurrentThread()) {
                localLock.unlock();
            }
        }
    }
}
