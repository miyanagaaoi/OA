package com.oa.platform.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 手机号 AES-256-GCM 加解密单测（阶段 1.7）。
 *
 * <p>覆盖点（每条都对应一个可验收的行为）：
 * <ol>
 *   <li>往返：{@code encrypt → decrypt} 还原明文；</li>
 *   <li>随机 IV：同一明文两次加密产生**不同密文**（否则可被流量分析关联）；</li>
 *   <li>密文自带 keyId（{@code v1:<keyId>:<base64>}），为轮换预留；</li>
 *   <li>兼容历史明文：不带前缀的值按明文读出（迁移前的存量数据可读）；</li>
 *   <li>fail-fast：密钥缺失 / 长度不符 → 构造期抛错（**不静默降级为明文**）；</li>
 *   <li>fail-closed：未知 keyId / 密文被篡改 → {@code CRYPTO_KEY_UNAVAILABLE}，不返回脏数据。</li>
 * </ol>
 */
class PhoneCipherTest {

    private static final String KEY_1 = base64Key((byte) 1);

    private static final String KEY_2 = base64Key((byte) 2);

    private static String base64Key(byte seed) {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (seed + i);
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    private static OaProperties properties(String key, String keyId, Map<String, String> extraKeys) {
        OaProperties properties = new OaProperties();
        properties.getSecurity().setPhoneKey(key);
        properties.getSecurity().setPhoneKeyId(keyId);
        properties.getSecurity().setPhoneKeys(extraKeys);
        return properties;
    }

    @Test
    @DisplayName("往返：encrypt → decrypt 还原明文；空值保持 null")
    void roundTrip() {
        PhoneCipher cipher = new PhoneCipher(properties(KEY_1, "k1", Map.of()));
        String cipherText = cipher.encrypt("13800008888");

        assertThat(cipherText).startsWith("v1:k1:");
        assertThat(cipher.isCiphertext(cipherText)).isTrue();
        assertThat(cipher.keyIdOf(cipherText)).isEqualTo("k1");
        assertThat(cipher.decrypt(cipherText)).isEqualTo("13800008888");
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("   ")).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("随机 IV：同一明文两次加密的密文不同（但不能影响解密）")
    void randomIvProducesDifferentCiphertexts() {
        PhoneCipher cipher = new PhoneCipher(properties(KEY_1, "k1", Map.of()));
        String first = cipher.encrypt("13800008888");
        String second = cipher.encrypt("13800008888");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("13800008888");
        assertThat(cipher.decrypt(second)).isEqualTo("13800008888");
    }

    @Test
    @DisplayName("兼容历史明文：不带 v1: 前缀的值原样返回（迁移前的存量数据仍可读）")
    void legacyPlaintextIsReadable() {
        PhoneCipher cipher = new PhoneCipher(properties(KEY_1, "k1", Map.of()));

        assertThat(cipher.isCiphertext("13800008888")).isFalse();
        assertThat(cipher.decrypt("13800008888")).isEqualTo("13800008888");
        assertThat(cipher.keyIdOf("13800008888")).isNull();
    }

    @Test
    @DisplayName("fail-fast：密钥缺失 → 构造期抛错（绝不静默降级为明文存储）")
    void missingKeyFailsFast() {
        OaProperties properties = new OaProperties();
        properties.getSecurity().setPhoneKey(null);
        properties.getSecurity().setPhoneKeyFile("");

        assertThatThrownBy(() -> new PhoneCipher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("加密密钥缺失")
                .hasMessageContaining("OA_PHONE_KEY");
    }

    @Test
    @DisplayName("fail-fast：密钥长度不是 32 字节 → 构造期抛错")
    void wrongKeyLengthFailsFast() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new PhoneCipher(properties(shortKey, "k1", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("轮换：活动 keyId=k2 时新密文用 k2；旧 k1 密文仍能解密（密钥环持有旧密钥）")
    void rotationKeepsOldKeysDecryptable() {
        Map<String, String> oldKeys = new LinkedHashMap<>();
        oldKeys.put("k1", KEY_1);
        PhoneCipher rotated = new PhoneCipher(properties(KEY_2, "k2", oldKeys));

        String oldCipherText = new PhoneCipher(properties(KEY_1, "k1", Map.of())).encrypt("13800008888");
        assertThat(rotated.decrypt(oldCipherText)).isEqualTo("13800008888");
        assertThat(rotated.encrypt("13900009999")).startsWith("v1:k2:");
        assertThat(rotated.activeKeyId()).isEqualTo("k2");
        assertThat(rotated.keyIds()).containsExactlyInAnyOrder("k1", "k2");
    }

    @Test
    @DisplayName("fail-closed：未知 keyId → CRYPTO_KEY_UNAVAILABLE（不把密文当明文返回）")
    void unknownKeyIdIsRejected() {
        PhoneCipher cipher = new PhoneCipher(properties(KEY_1, "k1", Map.of()));
        String foreign = "v1:k9:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> cipher.decrypt(foreign))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CRYPTO_KEY_UNAVAILABLE))
                .hasMessageContaining("k9");
    }

    @Test
    @DisplayName("fail-closed：密文被篡改 → 认证失败（GCM 认证标签校验）")
    void tamperedCiphertextIsRejected() {
        PhoneCipher cipher = new PhoneCipher(properties(KEY_1, "k1", Map.of()));
        String cipherText = cipher.encrypt("13800008888");
        String payload = cipherText.substring("v1:k1:".length());
        byte[] raw = Base64.getDecoder().decode(payload);
        raw[raw.length - 1] = (byte) (raw[raw.length - 1] ^ 0x01);
        String tampered = "v1:k1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("认证失败");
    }

    @Test
    @DisplayName("密钥来源：支持从文件读取（dev 便利路径，文件在仓库之外）")
    void keyCanBeReadFromFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path keyFile = tempDir.resolve("oa-phone.key");
        java.nio.file.Files.writeString(keyFile, "# dev key\n" + KEY_1 + "\n", StandardCharsets.UTF_8);
        OaProperties properties = new OaProperties();
        properties.getSecurity().setPhoneKey("");
        properties.getSecurity().setPhoneKeyFile(keyFile.toString());

        PhoneCipher cipher = new PhoneCipher(properties);
        assertThat(cipher.decrypt(cipher.encrypt("13800008888"))).isEqualTo("13800008888");
    }
}
