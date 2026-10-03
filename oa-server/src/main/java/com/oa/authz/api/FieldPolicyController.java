package com.oa.authz.api;

import com.oa.authz.api.dto.VisibilityDtos;
import com.oa.authz.visibility.AmountFieldPolicy;
import com.oa.authz.visibility.FormFieldWriteGuard;
import com.oa.authz.visibility.PhoneVisibilityService;
import com.oa.authz.visibility.VisibilityRoles;
import com.oa.common.api.ApiResponse;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.form.app.FormWritePolicy;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字段级限制（{@code oa.authz.visibility.field.*}，阶段 1.6）接口。
 *
 * <ul>
 *   <li>{@code GET  /api/v1/authz/field-policy/amount}：金额字段对当前角色的读写策略；</li>
 *   <li>{@code GET  /api/v1/authz/field-policy/contact}：联系方式脱敏策略（唯一实现的出参）；</li>
 *   <li>{@code POST /api/v1/authz/field-policy/assert-write}：**字段级写入闸门** ——
 *       PRD §8.1「一期不对外提供单据写入接口」，因此这是当前对外可测的写入门禁；
 *       表单保存接口（阶段 2b）必须复用同一条代码路径（{@link FormFieldWriteGuard}），
 *       不允许另写一套判定。</li>
 * </ul>
 *
 * <p>金额只读错误码：非财务类角色提交含金额字段的载荷 → <b>403 {@code 40306 AMOUNT_READ_ONLY}</b>；
 * 状态不允许（审批中/已完结）→ 403 {@code 40304 FIELD_WRITE_DENIED}（两者可同时命中，金额规则先判）。
 */
@RestController
@RequestMapping("/api/v1/authz/field-policy")
public class FieldPolicyController {

    private final FormFieldWriteGuard writeGuard;

    private final PhoneVisibilityService phoneVisibility;

    public FieldPolicyController(FormFieldWriteGuard writeGuard, PhoneVisibilityService phoneVisibility) {
        this.writeGuard = writeGuard;
        this.phoneVisibility = phoneVisibility;
    }

    @GetMapping("/amount")
    public ApiResponse<Map<String, Object>> amountPolicy() {
        return ApiResponse.success(writeGuard.amountPolicy(requirePrincipal()));
    }

    /** 联系方式脱敏策略（PRD §5.3 / AC-18）：脱敏形态与「谁可见完整值」的唯一口径。 */
    @GetMapping("/contact")
    public ApiResponse<Map<String, Object>> contactPolicy() {
        CurrentUser principal = requirePrincipal();
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("maskPattern", "138****8888");
        policy.put("masked", true);
        policy.put("fullValueRoles", List.of(VisibilityRoles.ADMIN));
        policy.put("selfVisible", true);
        policy.put("callerSeesOwnFullValue", true);
        policy.put("callerIsAdmin", principal.hasRole(VisibilityRoles.ADMIN));
        policy.put("sample", phoneVisibility.maskPlain("13800008888"));
        policy.put("encryptionAtRest", "AES-256-GCM（密文形态 v1:<keyId>:<base64(iv||ct||tag)>）");
        policy.put("reason", "手机号在通讯录与所有 JSON 出参中默认脱敏；本人与系统管理员可见完整值（PRD §5.3）");
        return ApiResponse.success(policy);
    }

    /**
     * 字段级写入闸门。
     *
     * @throws BizException 403 {@code AMOUNT_READ_ONLY}（非财务角色写金额）
     *                      / 403 {@code FIELD_WRITE_DENIED}（状态不允许写该字段）
     */
    @PostMapping("/assert-write")
    public ApiResponse<VisibilityDtos.FieldWriteCheckResponse> assertWrite(
            @Valid @RequestBody VisibilityDtos.FieldWriteCheckRequest request) {
        CurrentUser principal = requirePrincipal();
        FormWritePolicy.FormType formType = FormWritePolicy.FormType.of(request.formType());
        FormWritePolicy.FormState state = resolveState(request.state());
        boolean isInitiator = Boolean.TRUE.equals(request.isInitiator());
        boolean isArchiveNode = Boolean.TRUE.equals(request.isArchiveNode());
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        Set<String> fields = payload.isEmpty() ? names(request.allFields()) : FormFieldWriteGuard.fieldNames(payload);
        Set<String> allFields = request.allFields() == null || request.allFields().isEmpty()
                ? fields
                : names(request.allFields());
        // 附件类字段（type ∈ {file, files}）：待补件窗口按**字段类型**放行，而不是写死 attachments。
        // 请求未带该项时为空集 ⇒ 白名单退化为「attachments + supplement_note」（宁窄不宽）。
        Set<String> attachmentFields = names(request.attachmentFields());
        boolean amountWritable = AmountFieldPolicy.canWriteAmounts(principal);

        if (Boolean.FALSE.equals(request.strict())) {
            // 宽容口径：返回过滤后的可写载荷
            Map<String, Object> filtered = writeGuard.filter(payload, state, formType, isInitiator, isArchiveNode,
                    allFields, attachmentFields);
            Set<String> writable = FormWritePolicy.writableFields(state, formType, isInitiator, isArchiveNode, allFields,
                    attachmentFields);
            if (!amountWritable) {
                writable.removeIf(AmountFieldPolicy::isAmountField);
            }
            return ApiResponse.success(new VisibilityDtos.FieldWriteCheckResponse(true, List.copyOf(writable),
                    List.of(), amountWritable, filtered, List.of()));
        }

        // 严格口径：先收集全部拒绝原因（便于前端一次看清），再抛 403 —— 与 AC-28「篡改请求被拒绝」一致
        List<VisibilityDtos.RejectedField> rejected = new ArrayList<>();
        Set<String> writable = FormWritePolicy.writableFields(state, formType, isInitiator, isArchiveNode, allFields,
                attachmentFields);
        for (String field : fields) {
            if (!amountWritable && AmountFieldPolicy.isAmountField(field)) {
                rejected.add(new VisibilityDtos.RejectedField(field, ErrorCode.AMOUNT_READ_ONLY.getCode(),
                        ErrorCode.AMOUNT_READ_ONLY.getMessage()));
            } else if (!writable.contains(field)) {
                rejected.add(new VisibilityDtos.RejectedField(field, ErrorCode.FIELD_WRITE_DENIED.getCode(),
                        String.format(ErrorCode.FIELD_WRITE_DENIED.getMessage(), field)));
            }
        }
        if (!rejected.isEmpty()) {
            VisibilityDtos.RejectedField first = rejected.get(0);
            ErrorCode code = first.code() == ErrorCode.AMOUNT_READ_ONLY.getCode()
                    ? ErrorCode.AMOUNT_READ_ONLY
                    : ErrorCode.FIELD_WRITE_DENIED;
            throw new BizException(code, first.reason() + "；被拒字段：" + joined(rejected)
                    + "（字段级限制：PRD §5.3 / AC-28）");
        }
        return ApiResponse.success(new VisibilityDtos.FieldWriteCheckResponse(true, List.copyOf(fields),
                List.of(), amountWritable, payload, List.of()));
    }

    private static String joined(List<VisibilityDtos.RejectedField> rejected) {
        List<String> names = new ArrayList<>();
        for (VisibilityDtos.RejectedField item : rejected) {
            names.add(item.field());
        }
        return String.join("、", names);
    }

    private static Set<String> names(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }
        return result;
    }

    /** 状态解析：接受中/英文字面量，未知值按草稿处理（与 {@code FormWritePolicy} 的宽松解析口径一致）。 */
    private static FormWritePolicy.FormState resolveState(String state) {
        if (state == null || state.isBlank()) {
            return FormWritePolicy.FormState.DRAFT;
        }
        return switch (state.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "draft", "草稿" -> FormWritePolicy.FormState.DRAFT;
            case "approving", "审批中" -> FormWritePolicy.FormState.APPROVING;
            case "pending_supplement", "待补件" -> FormWritePolicy.FormState.PENDING_SUPPLEMENT;
            case "closed", "已完结", "approved", "rejected", "terminated" -> FormWritePolicy.FormState.CLOSED;
            default -> FormWritePolicy.FormState.DRAFT;
        };
    }

    private static CurrentUser requirePrincipal() {
        DataScopeContext context = DataScopeContext.current();
        if (context == null || context.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return context.getPrincipal();
    }
}
