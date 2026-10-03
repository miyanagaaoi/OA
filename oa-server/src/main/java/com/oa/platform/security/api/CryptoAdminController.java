package com.oa.platform.security.api;

import com.oa.authz.visibility.VisibilityRoles;
import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.platform.security.crypto.PhoneCipher;
import com.oa.platform.security.crypto.PhoneCryptoService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感字段加密与密钥管理（{@code oa.platform.security.crypto.*}，阶段 1.7）接口。**仅系统管理员**。
 *
 * <ul>
 *   <li>{@code GET  /api/v1/admin/crypto/fields}：已加密字段与当前脱敏方式（**不返回密钥本体**）；</li>
 *   <li>{@code GET  /api/v1/admin/keys}：密钥状态（活动 keyId + 可用 keyId 列表）；</li>
 *   <li>{@code POST /api/v1/admin/keys/rotate}：密钥轮换（把非活动 keyId 的密文收敛到活动密钥，幂等）；</li>
 *   <li>{@code POST /api/v1/admin/crypto/phone-migrate}：一次性把库中**历史明文**手机号加密为密文（幂等）。</li>
 * </ul>
 *
 * <p><b>为什么迁移做成交付接口而不是 SQL 脚本</b>：迁移必须用应用内的密钥环（含 keyId）
 * 完成「读 → 加密 → 回写」，用 SQL 无法产生正确的 GCM 认证标签；且接口天然带
 * 「仅系统管理员 + 审计留痕 + 幂等」三重约束。运维侧等价命令：
 * {@code curl -X POST .../admin/crypto/phone-migrate -b "OA_SESSION=..."}。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class CryptoAdminController {

    private final PhoneCryptoService phoneCrypto;

    public CryptoAdminController(PhoneCryptoService phoneCrypto) {
        this.phoneCrypto = phoneCrypto;
    }

    /** 已加密字段清单（{@code config/crypto/field-encryption.yml} 契约的接口化表达）。 */
    @GetMapping("/crypto/fields")
    public ApiResponse<Map<String, Object>> fields() {
        requireAdmin();
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("table", "sys_user");
        field.put("column", "phone");
        field.put("algorithm", "AES-256-GCM");
        field.put("cipherPrefix", PhoneCipher.PREFIX);
        field.put("keyIdInCiphertext", true);
        field.put("atRest", "encrypted");
        field.put("inResponse", "masked");
        field.put("maskPattern", "138****8888");
        field.put("fullValueRoles", List.of(VisibilityRoles.ADMIN));
        field.put("selfVisibleFullValue", true);
        field.put("exportValue", "完整明文（仅系统管理员导出主数据时可取，import-spec §9.2）");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", List.of(field));
        result.put("activeKeyId", phoneCrypto.activeKeyId());
        result.put("plaintextMigrationEndpoint", "POST /api/v1/admin/crypto/phone-migrate");
        return ApiResponse.success(result);
    }

    /** 密钥状态（**绝不返回密钥本体**）。 */
    @GetMapping("/keys")
    public ApiResponse<Map<String, Object>> keys() {
        requireAdmin();
        Map<String, Object> status = phoneCrypto.keyStatus();
        status.put("rotationEndpoint", "POST /api/v1/admin/keys/rotate");
        status.put("rotationNote", "轮换 = 配置新密钥并把旧密钥放入 oa.security.phone-keys.<旧keyId>，"
                + "重启后调用 /keys/rotate 收敛存量密文（幂等）");
        return ApiResponse.success(status);
    }

    /**
     * 密钥轮换（收敛到活动密钥）：旧 keyId 密文 → 活动密钥密文。
     *
     * <p>幂等：已在活动密钥上的行跳过；库中若仍有历史明文则顺手补齐为密文。
     */
    @PostMapping("/keys/rotate")
    @Audited(action = "update", targetType = "crypto_key", recordAfter = true)
    public ApiResponse<Map<String, Object>> rotate() {
        CurrentUser principal = requireAdmin();
        return ApiResponse.success(phoneCrypto.rotateToActiveKey(principal.id()));
    }

    /**
     * 手机号明文一次性迁移（幂等，可重复执行）。
     *
     * <p>库中历史明文（不带 {@code v1:} 前缀）会被加密为密文；已是密文的行跳过。
     */
    @PostMapping("/crypto/phone-migrate")
    @Audited(action = "update", targetType = "crypto_field", recordAfter = true)
    public ApiResponse<Map<String, Object>> migratePhones() {
        CurrentUser principal = requireAdmin();
        return ApiResponse.success(phoneCrypto.migratePlaintextPhones(principal.id()));
    }

    private static CurrentUser requireAdmin() {
        DataScopeContext context = DataScopeContext.current();
        CurrentUser principal = context == null ? null : context.getPrincipal();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!principal.hasRole(VisibilityRoles.ADMIN)) {
            throw new BizException(ErrorCode.FORBIDDEN, "密钥与敏感字段管理仅系统管理员可用（PRD §5.3 / REQ-NFR-005）");
        }
        return principal;
    }
}
