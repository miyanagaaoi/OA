package com.oa.authz.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.platform.security.crypto.PhoneCipher;
import com.oa.platform.security.crypto.PhoneCryptoService;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 手机号可见性单测（阶段 1.6；1.7 加密后仍成立）。
 *
 * <p>覆盖 PRD §5.3 / AC-18 的口径：
 * <ul>
 *   <li>他人手机号 → {@code 138****8888}；</li>
 *   <li>**本人**查看自己的手机号 → 完整值（PRD 明文口径，非猜测）；</li>
 *   <li>系统管理员 → 完整值；</li>
 *   <li>库中存的是**密文**时：先解密再脱敏（不能对密文 substring 产生 {@code v1:****xxxx}）；</li>
 *   <li>历史明文行同样可读；</li>
 *   <li>主数据导出取完整明文，但方法内强制系统管理员（非管理员 403）。</li>
 * </ul>
 */
class PhoneVisibilityServiceTest {

    private PhoneVisibilityService service;

    private PhoneCipher cipher;

    @BeforeEach
    void setUp() {
        OaProperties properties = new OaProperties();
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (7 + i);
        }
        properties.getSecurity().setPhoneKey(Base64.getEncoder().encodeToString(raw));
        properties.getSecurity().setPhoneKeyId("k1");
        cipher = new PhoneCipher(properties);
        PhoneCryptoService crypto = new PhoneCryptoService(cipher, mock(com.oa.identity.infra.SysUserMapper.class));
        service = new PhoneVisibilityService(crypto);
    }

    private static CurrentUser user(long id, String... roles) {
        return CurrentUser.of(id, "u" + id, "用户" + id, "A000" + id, 1L, 1L, Set.of(roles), Set.of(), false);
    }

    @Test
    @DisplayName("他人手机号 → 138****8888（密文先解密再脱敏）")
    void othersPhoneIsMasked() {
        String stored = cipher.encrypt("13812348888");

        PhoneVisibilityService.PhoneDisplay display = service.display(user(9L, "employee"), 7L, stored);

        assertThat(display.value()).isEqualTo("138****8888");
        assertThat(display.masked()).isTrue();
        assertThat(display.value()).doesNotContain("v1:");
    }

    @Test
    @DisplayName("本人查看自己的手机号 → 完整值（PRD §5.3；非脱敏）")
    void selfSeesFullValue() {
        String stored = cipher.encrypt("13812348888");

        PhoneVisibilityService.PhoneDisplay display = service.display(user(7L, "employee"), 7L, stored);

        assertThat(display.value()).isEqualTo("13812348888");
        assertThat(display.masked()).isFalse();
    }

    @Test
    @DisplayName("系统管理员 → 完整值；非管理员看他人 → 脱敏")
    void adminSeesFullValue() {
        String stored = cipher.encrypt("13812348888");

        assertThat(service.display(user(1L, "admin"), 7L, stored).value()).isEqualTo("13812348888");
        assertThat(service.display(user(1L, "company_admin"), 7L, stored).value()).isEqualTo("138****8888");
    }

    @Test
    @DisplayName("兼容历史明文：库中仍是明文时按同一规则脱敏/放行")
    void legacyPlaintextStillWorks() {
        assertThat(service.display(user(9L, "employee"), 7L, "13812348888").value()).isEqualTo("138****8888");
        assertThat(service.display(user(7L, "employee"), 7L, "13812348888").value()).isEqualTo("13812348888");
    }

    @Test
    @DisplayName("边界：空值 → null；未认证上下文（viewer=null）一律脱敏")
    void nullAndAnonymousAreHandled() {
        assertThat(service.display(user(7L, "employee"), 7L, null).value()).isNull();
        assertThat(service.display(user(7L, "employee"), 7L, "  ").value()).isNull();
        assertThat(service.display(null, 7L, "13812348888").value()).isEqualTo("138****8888");
        assertThat(service.maskPlain("123")).isEqualTo("***");
    }

    @Test
    @DisplayName("导出取完整明文，但方法内强制系统管理员（非管理员 403 EXPORT_DENIED）")
    void exportPlainRequiresAdmin() {
        String stored = cipher.encrypt("13812348888");

        assertThat(service.exportPlain(user(1L, "admin"), stored)).isEqualTo("13812348888");
        assertThatThrownBy(() -> service.exportPlain(user(9L, "company_admin"), stored))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.EXPORT_DENIED));
        assertThatThrownBy(() -> service.exportPlain(null, stored))
                .isInstanceOf(BizException.class);
    }
}
