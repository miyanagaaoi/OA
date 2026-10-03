package com.oa.identity.app;

import com.oa.common.security.PasswordService;
import java.security.SecureRandom;

/**
 * 初始口令生成（import-spec T-03）：**随机口令 + 线下分发 + 首登强制改密**。
 *
 * <p>从 {@code UserService} 抽出来的原因：单条新增人员与批量导入都必须用**同一套**
 * 口令生成规则（≥8 位、必然同时含字母与数字，REQ-NFR-005；字符集去掉易混淆的
 * {@code l/1/I/o/0}），两处各写一份必然漂移。
 */
public final class InitialPasswordGenerator {

    /** 字母集（去掉 l/1/I/o/0 等易混淆字符）。 */
    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

    /** 数字集（去掉 0/1）。 */
    private static final String DIGITS = "23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private InitialPasswordGenerator() {
    }

    /**
     * 生成随机初始口令。
     *
     * @param passwordService 用于自校验生成的弱口令（不符合策略直接抛错，绝不放过）
     * @param length          期望长度（不足 8 位按 8 位处理）
     */
    public static String generate(PasswordService passwordService, int length) {
        int size = Math.max(8, length);
        StringBuilder builder = new StringBuilder(size);
        builder.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        builder.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        String alphabet = LETTERS + DIGITS;
        for (int i = 2; i < size; i++) {
            builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        String password = builder.toString();
        passwordService.assertStrong(password);
        return password;
    }
}
