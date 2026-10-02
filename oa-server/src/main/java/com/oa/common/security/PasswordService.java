package com.oa.common.security;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 口令服务：BCrypt 加盐哈希 + 口令强度校验。
 *
 * <p>强度口径（REQ-NFR-005 / PRD 9.1）：**≥8 位且同时包含字母与数字**；
 * 哈希一律用 {@link BCryptPasswordEncoder}（strength ≥10，默认 12），不自行实现。
 * 明文口令永不落库、永不进日志（审计切面还会再脱敏一次）。
 */
@Service
public class PasswordService {

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final BCryptPasswordEncoder encoder;
    private final int minLength;
    private final int strength;

    public PasswordService(OaProperties properties) {
        OaProperties.Security security = properties.getSecurity();
        this.minLength = Math.max(8, security.getPasswordMinLength());
        this.strength = Math.max(10, security.getBcryptStrength());
        this.encoder = new BCryptPasswordEncoder(this.strength);
    }

    public int getMinLength() {
        return minLength;
    }

    public int getStrength() {
        return strength;
    }

    /** 生成哈希（每次调用盐不同，同一口令哈希不同）。 */
    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new BizException(ErrorCode.PASSWORD_WEAK);
        }
        return encoder.encode(rawPassword);
    }

    /** 校验口令；哈希为空/非法一律返回 false（不抛异常，避免泄露内部细节）。 */
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, passwordHash);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 返回全部不满足项（空列表 = 通过）。 */
    public List<String> violations(String rawPassword) {
        List<String> violations = new ArrayList<>();
        if (rawPassword == null || rawPassword.isEmpty()) {
            violations.add("口令不能为空");
            return violations;
        }
        if (rawPassword.length() < minLength) {
            violations.add("口令长度不足 " + minLength + " 位");
        }
        if (!LETTER.matcher(rawPassword).find()) {
            violations.add("口令必须包含字母");
        }
        if (!DIGIT.matcher(rawPassword).find()) {
            violations.add("口令必须包含数字");
        }
        if (WHITESPACE.matcher(rawPassword).find()) {
            violations.add("口令不能包含空白字符");
        }
        return violations;
    }

    public boolean isStrong(String rawPassword) {
        return violations(rawPassword).isEmpty();
    }

    /** 强度不达标即抛 {@link ErrorCode#PASSWORD_WEAK}。 */
    public void assertStrong(String rawPassword) {
        List<String> violations = violations(rawPassword);
        if (!violations.isEmpty()) {
            throw new BizException(ErrorCode.PASSWORD_WEAK, String.join("；", violations));
        }
    }

    /** 哈希是否使用了低于当前策略的 cost（用于登录成功后透明升级）。 */
    public boolean needsRehash(String passwordHash) {
        if (passwordHash == null || passwordHash.length() < 7) {
            return true;
        }
        try {
            int cost = Integer.parseInt(passwordHash.substring(4, 6));
            return cost < strength;
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
