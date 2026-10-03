package com.oa.platform.security.crypto;

import com.oa.common.scope.DataScopeContext;
import com.oa.identity.infra.SysUserMapper;
import com.oa.identity.infra.row.PhoneRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 手机号加密存储服务（阶段 1.7 安全基座）—— {@code sys_user.phone} 的**唯一读写端口**。
 *
 * <h2>职责</h2>
 * <ol>
 *   <li><b>写加密</b>：任何写入 {@code sys_user.phone} 的路径（人员新增/修改、五类批量导入）
 *       都必须先经 {@link #encryptForStore(String)}；</li>
 *   <li><b>读解密</b>：任何读取都必须经 {@link #decryptForRead(String)}，再交给
 *       {@code com.oa.authz.visibility.PhoneVisibilityService} 做脱敏 —— 加解密与脱敏
 *       **各自只有一处实现**，禁止调用方手写；</li>
 *   <li><b>兼容历史明文</b>：不带 {@code v1:} 前缀的值按明文读取（{@link PhoneCipher#decrypt(String)}），
 *       由 {@link #migratePlaintextPhones(Long)} 一次性迁移；</li>
 *   <li><b>密钥轮换钩子</b>：密文自带 {@code keyId}，{@link #rotateToActiveKey(Long)} 把
 *       非活动 keyId 的密文重新加密到活动密钥；活动密钥本身由配置/环境变量决定（见 {@link PhoneCipher}）。</li>
 * </ol>
 *
 * <h2>数据域口径</h2>
 * 迁移与轮换是**系统级后台操作**，必须显式以 {@link DataScopeContext#system()} 执行
 * （{@code selectPhoneRows} 带 {@code @dataScope} 标记，系统口径下退化为全量）；
 * 执行前后**恢复**调用方的线程上下文，避免污染请求线程。
 */
@Service
public class PhoneCryptoService {

    private static final Logger log = LoggerFactory.getLogger(PhoneCryptoService.class);

    private final PhoneCipher cipher;

    private final SysUserMapper userMapper;

    public PhoneCryptoService(PhoneCipher cipher, SysUserMapper userMapper) {
        this.cipher = cipher;
        this.userMapper = userMapper;
    }

    // ---------------------------------------------------------------- 读写端口

    /** 写库前加密（{@code null}/空白 → {@code null}）。 */
    public String encryptForStore(String plain) {
        return cipher.encrypt(plain);
    }

    /** 读库后解密（兼容历史明文）。 */
    public String decryptForRead(String stored) {
        return cipher.decrypt(stored);
    }

    /** 是否为密文（迁移报告与测试用）。 */
    public boolean isCiphertext(String stored) {
        return cipher.isCiphertext(stored);
    }

    /** 活动 keyId。 */
    public String activeKeyId() {
        return cipher.activeKeyId();
    }

    /** 密钥环状态（**不含密钥本体**，见 {@code GET /api/v1/admin/keys}）。 */
    public Map<String, Object> keyStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("algorithm", "AES-256-GCM");
        status.put("cipherPrefix", PhoneCipher.PREFIX);
        status.put("activeKeyId", cipher.activeKeyId());
        status.put("keyIds", new ArrayList<>(cipher.keyIds()));
        status.put("rotationSupported", true);
        return status;
    }

    // ---------------------------------------------------------------- 迁移与轮换

    /**
     * 一次性迁移：把库中**历史明文**手机号加密为密文（{@code v1:&lt;keyId&gt;:...}）。
     *
     * <p><b>幂等</b>：已是密文的行一律跳过，重复执行不会二次加密、不会改变已有密文。
     * 单事务提交：任一行失败则整体回滚（不会出现「一半密文一半明文」）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> migratePlaintextPhones(Long operator) {
        return withSystemContext(() -> {
            List<PhoneRow> rows = userMapper.selectPhoneRows();
            int migrated = 0;
            int alreadyCipher = 0;
            int blank = 0;
            for (PhoneRow row : rows) {
                String stored = row.getPhone();
                if (stored == null || stored.isBlank()) {
                    blank++;
                    continue;
                }
                if (cipher.isCiphertext(stored)) {
                    alreadyCipher++;
                    continue;
                }
                userMapper.updatePhoneCipher(row.getId(), cipher.encrypt(stored), operator);
                migrated++;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scanned", rows.size());
            result.put("migrated", migrated);
            result.put("alreadyEncrypted", alreadyCipher);
            result.put("blank", blank);
            result.put("activeKeyId", cipher.activeKeyId());
            result.put("idempotent", true);
            log.info("手机号明文迁移完成：扫描={} 迁移={} 已是密文={} 空值={} operator={}",
                    rows.size(), migrated, alreadyCipher, blank, operator);
            return result;
        });
    }

    /**
     * 密钥轮换（**收敛到活动密钥**）：把非活动 keyId 的密文解密后重新加密到活动密钥。
     *
     * <p>轮换的完整流程（与配置配合）：
     * <ol>
     *   <li>运维在配置里加入新密钥：{@code oa.security.phone-key=&lt;新密钥&gt;}、
     *       {@code oa.security.phone-key-id=k2}，并把旧密钥放进 {@code oa.security.phone-keys.k1}；</li>
     *   <li>重启后活动 keyId = {@code k2}，新旧密文都能读（密钥环同时持有 k1/k2）；</li>
     *   <li>调用本方法把所有 k1 密文改写为 k2 密文（幂等：已 k2 的跳过）；</li>
     *   <li>确认全部收敛后，才能把 {@code phone-keys.k1} 从配置移除。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rotateToActiveKey(Long operator) {
        return withSystemContext(() -> {
            List<PhoneRow> rows = userMapper.selectPhoneRows();
            int reEncrypted = 0;
            int onActiveKey = 0;
            int plaintextFixed = 0;
            for (PhoneRow row : rows) {
                String stored = row.getPhone();
                if (stored == null || stored.isBlank()) {
                    continue;
                }
                if (cipher.isCiphertext(stored)) {
                    if (cipher.activeKeyId().equals(cipher.keyIdOf(stored))) {
                        onActiveKey++;
                        continue;
                    }
                    String plain = cipher.decrypt(stored);
                    userMapper.updatePhoneCipher(row.getId(), cipher.encrypt(plain), operator);
                    reEncrypted++;
                } else {
                    userMapper.updatePhoneCipher(row.getId(), cipher.encrypt(stored), operator);
                    plaintextFixed++;
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scanned", rows.size());
            result.put("reEncrypted", reEncrypted);
            result.put("plaintextEncrypted", plaintextFixed);
            result.put("alreadyOnActiveKey", onActiveKey);
            result.put("activeKeyId", cipher.activeKeyId());
            log.info("手机号密钥轮换完成：扫描={} 重加密={} 明文补齐={} 已在活动密钥={} activeKeyId={} operator={}",
                    rows.size(), reEncrypted, plaintextFixed, onActiveKey, cipher.activeKeyId(), operator);
            return result;
        });
    }

    private Map<String, Object> withSystemContext(java.util.function.Supplier<Map<String, Object>> action) {
        DataScopeContext previous = DataScopeContext.current();
        try {
            DataScopeContext.set(DataScopeContext.system());
            return action.get();
        } finally {
            if (previous == null) {
                DataScopeContext.clear();
            } else {
                DataScopeContext.set(previous);
            }
        }
    }
}
