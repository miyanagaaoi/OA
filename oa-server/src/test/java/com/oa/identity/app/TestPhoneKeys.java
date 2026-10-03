package com.oa.identity.app;

import com.oa.common.config.OaProperties;
import com.oa.platform.security.crypto.PhoneCipher;
import java.util.Base64;

/**
 * 测试用手机号加密装置（**非真实密钥**，只存在于测试代码内）。
 *
 * <p>存在意义：{@code PhoneCipher} 在密钥缺失时 fail-fast，因此任何构造
 * {@code UserService}/{@code PhoneVisibilityService} 的单测都必须显式提供一个 32 字节测试密钥；
 * 集中在这里避免每个测试各写一份 Base64（也避免误把真实密钥写进测试）。
 */
public final class TestPhoneKeys {

    /** 固定测试密钥（32 字节，seed=11 递增；仅用于单测）。 */
    private static final byte SEED = 11;

    private TestPhoneKeys() {
    }

    /** 构造一个可用的测试用 {@link PhoneCipher}。 */
    public static PhoneCipher cipher() {
        return new PhoneCipher(properties());
    }

    /** 构造带测试密钥的配置。 */
    public static OaProperties properties() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (SEED + i);
        }
        OaProperties properties = new OaProperties();
        properties.getSecurity().setPhoneKey(Base64.getEncoder().encodeToString(raw));
        properties.getSecurity().setPhoneKeyId("k1");
        return properties;
    }
}
