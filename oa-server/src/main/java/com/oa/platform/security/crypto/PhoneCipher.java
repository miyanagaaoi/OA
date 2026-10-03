package com.oa.platform.security.crypto;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 手机号字段级加密（AES-256-GCM）——<b>1.7 安全基座</b>的唯一加解密实现。
 *
 * <h2>密文格式（自带 keyId，为轮换预留）</h2>
 * <pre>
 *   v1:&lt;keyId&gt;:&lt;Base64(iv[12] ‖ ciphertext ‖ tag[16])&gt;
 * </pre>
 * <ul>
 *   <li>前缀 {@code v1:} 是「密文版本」，用于与**历史明文**区分（兼容读取，见 {@link #decrypt(String)}）；</li>
 *   <li>{@code keyId} 让将来轮换时可以新旧密钥共存解密（{@code oa.security.phone-keys.&lt;id&gt;}）；</li>
 *   <li>每次加密使用 {@link SecureRandom} 生成的**随机 12 字节 IV**；GCM 认证标签 128 位 ——
 *       篡改密文会导致解密失败而不是静默返回脏数据。</li>
 * </ul>
 *
 * <h2>密钥来源与 fail-fast（不得静默降级为明文）</h2>
 * <ol>
 *   <li>{@code oa.security.phone-key}（生产由环境变量 {@code OA_PHONE_KEY} 注入）；</li>
 *   <li>否则读 {@code oa.security.phone-key-file} 指向的文件首行（dev 便利，文件在仓库之外）；</li>
 *   <li>两者都没有 / 密钥材料非法（非 32 字节）→ 构造期抛 {@link IllegalStateException}，
 *       <b>Spring 启动直接失败</b>（PRD §5.3 手机号加密存储 + REQ-NFR-005）。</li>
 * </ol>
 *
 * <p>本类是**纯加解密**组件：不碰数据库、不做脱敏（脱敏的唯一实现在
 * {@code com.oa.authz.visibility.PhoneVisibilityService}）。
 */
@Component
public class PhoneCipher {

    private static final Logger log = LoggerFactory.getLogger(PhoneCipher.class);

    /** 密文前缀（含 keyId 的版本号）；历史明文没有该前缀。 */
    public static final String VERSION_PREFIX = "v1";

    /** 密文版本前缀完整写法。 */
    public static final String PREFIX = VERSION_PREFIX + ":";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** GCM 推荐 IV 长度（字节）。 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度（位）。 */
    private static final int TAG_BITS = 128;

    /** AES-256 密钥长度（字节）。 */
    private static final int KEY_BYTES = 32;

    /** keyId 允许的字符（与配置键风格一致）。 */
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final Map<String, SecretKey> keys;

    private final String activeKeyId;

    private final SecureRandom random = new SecureRandom();

    public PhoneCipher(OaProperties properties) {
        OaProperties.Security security = properties.getSecurity();
        Map<String, SecretKey> resolved = new LinkedHashMap<>();
        String activeId = normalizeKeyId(security.getPhoneKeyId());

        String material = firstNonBlank(security.getPhoneKey(), readKeyFile(security.getPhoneKeyFile()));
        if (material != null) {
            resolved.put(activeId, toKey(material, "oa.security.phone-key/key-file"));
        }
        if (security.getPhoneKeys() != null) {
            for (Map.Entry<String, String> entry : security.getPhoneKeys().entrySet()) {
                String id = normalizeKeyId(entry.getKey());
                String value = entry.getValue() == null ? null : entry.getValue().trim();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                resolved.put(id, toKey(value, "oa.security.phone-keys." + id));
            }
        }
        if (!resolved.containsKey(activeId)) {
            // fail-fast：绝不静默降级为明文
            throw new IllegalStateException("敏感字段加密密钥缺失：请通过环境变量 OA_PHONE_KEY 注入"
                    + "（或配置 oa.security.phone-key / oa.security.phone-key-file，keyId=" + activeId + "）。"
                    + "密钥为 32 字节的 Base64 或 hex；系统不允许以明文方式存储手机号（PRD §5.3 / REQ-NFR-005）。");
        }
        this.keys = Collections.unmodifiableMap(resolved);
        this.activeKeyId = activeId;
        log.info("手机号字段加密已启用：活动 keyId={}，可用 keyId={}（密钥本体不打印）", activeId, this.keys.keySet());
    }

    /** 活动 keyId（新密文一律用它）。 */
    public String activeKeyId() {
        return activeKeyId;
    }

    /** 全部可用于解密的 keyId（含历史密钥，**不含密钥本体**）。 */
    public Set<String> keyIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(keys.keySet()));
    }

    /** 加密明文（{@code null}/空白 → {@code null}，保持「允许留空」语义）。 */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(cipherText, 0, joined, iv.length, cipherText.length);
            return PREFIX + activeKeyId + ":" + Base64.getEncoder().encodeToString(joined);
        } catch (GeneralSecurityException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "手机号加密失败", ex);
        }
    }

    /**
     * 解密（**兼容历史明文**）。
     *
     * <p>口径（import-spec 与 data-model 的过渡要求）：不带 {@code v1:} 前缀的值按**历史明文**原样返回，
     * 由 {@code PhoneCryptoService#migratePlaintextPhones()} 一次性迁移为密文；
     * 带前缀但 keyId 未知 → 抛 {@link ErrorCode#CRYPTO_KEY_UNAVAILABLE}（fail-closed，绝不返回密文冒充明文）。
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String value = stored.trim();
        if (!isCiphertext(value)) {
            return value;
        }
        String[] parts = value.split(":", 3);
        if (parts.length != 3) {
            throw new BizException(ErrorCode.CRYPTO_KEY_UNAVAILABLE, "手机号密文格式不合法（缺少 keyId 段）");
        }
        String keyId = parts[1];
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new BizException(ErrorCode.CRYPTO_KEY_UNAVAILABLE,
                    "手机号密文使用的 keyId=" + keyId + " 不在当前密钥环内，请补充 oa.security.phone-keys." + keyId);
        }
        byte[] joined;
        try {
            joined = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.CRYPTO_KEY_UNAVAILABLE, "手机号密文 Base64 解码失败", ex);
        }
        if (joined.length <= IV_LENGTH) {
            throw new BizException(ErrorCode.CRYPTO_KEY_UNAVAILABLE, "手机号密文长度不合法");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(joined, 0, iv, 0, IV_LENGTH);
        byte[] cipherText = new byte[joined.length - IV_LENGTH];
        System.arraycopy(joined, IV_LENGTH, cipherText, 0, cipherText.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            // 认证失败 = 密文被篡改或密钥不匹配：必须是显式错误，不得返回脏数据
            throw new BizException(ErrorCode.CRYPTO_KEY_UNAVAILABLE,
                    "手机号密文认证失败（密钥不匹配或密文被篡改）", ex);
        }
    }

    /** 是否为密文（带版本前缀）。 */
    public boolean isCiphertext(String stored) {
        return stored != null && stored.trim().startsWith(PREFIX);
    }

    /** 密文内的 keyId；非密文返回 {@code null}。 */
    public String keyIdOf(String stored) {
        if (!isCiphertext(stored)) {
            return null;
        }
        String[] parts = stored.trim().split(":", 3);
        return parts.length < 2 ? null : parts[1];
    }

    // ------------------------------------------------------------------ 内部

    private static String normalizeKeyId(String keyId) {
        String value = keyId == null || keyId.isBlank() ? "k1" : keyId.trim();
        if (!KEY_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalStateException("非法的加密 keyId（仅允许字母/数字/下划线/连字符，≤32）：" + value);
        }
        return value;
    }

    /** 把配置里的密钥材料（Base64 或 hex）转成 AES-256 密钥。 */
    private static SecretKey toKey(String material, String source) {
        byte[] raw = decodeKey(material);
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(source + " 的密钥长度必须为 " + KEY_BYTES
                    + " 字节（AES-256），当前为 " + raw.length + " 字节");
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static byte[] decodeKey(String material) {
        String value = material.trim();
        try {
            if (value.matches("^(?i)[0-9a-f]{" + (KEY_BYTES * 2) + "}$")) {
                return HexFormat.of().parseHex(value);
            }
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("加密密钥既不是合法的 Base64 也不是 hex 字符串", ex);
        }
    }

    private static String readKeyFile(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path file = Path.of(path.trim());
        if (!Files.isReadable(file)) {
            return null;
        }
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    return line;
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取密钥文件失败：" + file, ex);
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
