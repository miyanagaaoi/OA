package com.oa.authz.visibility;

import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.security.CurrentUser;
import com.oa.platform.security.crypto.PhoneCryptoService;
import org.springframework.stereotype.Service;

/**
 * 手机号可见性 —— <b>1.6 字段级限制中「手机号脱敏」的唯一实现</b>。
 *
 * <h2>口径（doc/prd-0.1.md §5.3 / AC-18 / TC-AUTH-007）</h2>
 * <ul>
 *   <li>通讯录、人员列表、人员详情等所有出参默认脱敏为 {@code 138****8888}；</li>
 *   <li><b>仅本人与系统管理员可见完整值</b>（本人查看自己的手机号**不脱敏**，这是 PRD 明文口径，
 *       不是本实现的猜测）；</li>
 *   <li>主数据导出（{@code GET /identity/users/export}）按 import-spec §9.2「导出物即用于数据维护」
 *       取完整值，且**仅系统管理员**可调用 —— 由 {@link #exportPlain(CurrentUser, String)} 内部再校验一次，
 *       调用方无法「忘记脱敏」。</li>
 * </ul>
 *
 * <h2>为什么加密与脱敏分成两个类</h2>
 * <p>{@link PhoneCryptoService}（平台层）只负责「密文 ↔ 明文」，本类（权限层）只负责
 * 「明文 → 展示值」。两层各自只有一处实现：既避免了各处手写 {@code substring(0,3)}，
 * 也避免了「加密了但按密文长度脱敏」这类脏数据直接漏到前端。
 */
@Service
public class PhoneVisibilityService {

    /** 脱敏形态：前 3 位 + {@code ****} + 后 4 位。 */
    private static final String MASK = "****";

    /** 无法按手机号规则脱敏时的兜底形态。 */
    private static final String FALLBACK_MASK = "***";

    private final PhoneCryptoService phoneCrypto;

    public PhoneVisibilityService(PhoneCryptoService phoneCrypto) {
        this.phoneCrypto = phoneCrypto;
    }

    /**
     * 展示值 + 是否已脱敏。
     *
     * @param viewer      当前登录人（可为 {@code null}：未认证上下文一律按脱敏处理）
     * @param ownerUserId 手机号归属人（{@code sys_user.id}）
     * @param storedPhone **库中原值**（密文或历史明文）
     */
    public PhoneDisplay display(CurrentUser viewer, Long ownerUserId, String storedPhone) {
        if (storedPhone == null || storedPhone.isBlank()) {
            return new PhoneDisplay(null, false);
        }
        String plain = phoneCrypto.decryptForRead(storedPhone);
        if (canSeeFull(viewer, ownerUserId)) {
            return new PhoneDisplay(plain, false);
        }
        return new PhoneDisplay(maskPlain(plain), true);
    }

    /** 是否可见完整手机号：本人 或 系统管理员（PRD §5.3）。 */
    public boolean canSeeFull(CurrentUser viewer, Long ownerUserId) {
        if (viewer == null) {
            return false;
        }
        if (viewer.hasRole(VisibilityRoles.ADMIN)) {
            return true;
        }
        return ownerUserId != null && ownerUserId.equals(viewer.id());
    }

    /**
     * 主数据导出专用：取完整明文，但**在方法内强制校验系统管理员**（import-spec §9.2）。
     *
     * <p>往返约束（import-spec §9.1）要求导出物能被校验器直接通过、再导入 0 error，
     * 而 {@code E-USER-003} 校验的是 11 位手机号格式 —— 脱敏值 {@code 138****8888} 必然失败。
     * 因此主数据导出取完整值；授权与留痕（{@code @Audited(action="export")}）是它的兜底约束。
     */
    public String exportPlain(CurrentUser viewer, String storedPhone) {
        if (viewer == null || !viewer.hasRole(VisibilityRoles.ADMIN)) {
            throw new BizException(ErrorCode.EXPORT_DENIED,
                    "主数据导出仅系统管理员可用，且导出物含手机号完整值（import-spec §9.2）");
        }
        return storedPhone == null || storedPhone.isBlank() ? null : phoneCrypto.decryptForRead(storedPhone);
    }

    /** 纯脱敏函数（{@code 138****8888}）—— 暴露出来供测试与前端契约核对。 */
    public String maskPlain(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        String value = plain.trim();
        if (value.length() < 7) {
            return FALLBACK_MASK;
        }
        return value.substring(0, 3) + MASK + value.substring(value.length() - 4);
    }

    /**
     * 展示结果。
     *
     * @param value  展示值（脱敏后的 {@code 138****8888}，或完整值）
     * @param masked 是否已脱敏（出参 {@code phoneMasked} 字段，前端据此决定是否显示「查看完整」）
     */
    public record PhoneDisplay(String value, boolean masked) {
    }
}
