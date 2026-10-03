package com.oa.authz.api;

import com.oa.authz.api.dto.VisibilityDtos;
import com.oa.authz.visibility.ExportFieldPolicy;
import com.oa.authz.visibility.ExportTarget;
import com.oa.common.api.ApiResponse;
import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出管控（{@code oa.authz.visibility.export}，阶段 1.6）接口。
 *
 * <ul>
 *   <li>{@code POST /api/v1/authz/export-check}：导出前鉴权（角色 + 字段范围）——
 *       界面上的「导出」按钮与真正的导出接口都以此为准，**直连接口也无法绕过**；</li>
 *   <li>{@code GET  /api/v1/authz/export-policy}：导出策略总览（各目标的生效列与剔除列）。</li>
 * </ul>
 *
 * <p>金额列口径：默认**一律剔除**（{@code oa.authz.export.amount-enabled=false}）；
 * 置为 {@code true} 时仅系统管理员与财务角色可随导出拿到金额列（PRD §5.3 V0.4 例外）。
 */
@RestController
@RequestMapping("/api/v1/authz")
public class ExportPolicyController {

    private final OaProperties properties;

    public ExportPolicyController(OaProperties properties) {
        this.properties = properties;
    }

    /** 导出前鉴权：角色不通过 → 403；字段越界（例如伪造 {@code fields=["amount"]}）→ 403。 */
    @PostMapping("/export-check")
    public ApiResponse<VisibilityDtos.ExportCheckResponse> exportCheck(
            @Valid @RequestBody VisibilityDtos.ExportCheckRequest request) {
        CurrentUser principal = requirePrincipal();
        ExportTarget target = ExportTarget.of(request.target());
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "未知的导出目标：" + request.target() + "（可用值见 GET /api/v1/authz/export-policy）");
        }
        boolean amountEnabled = properties.getAuthz().getExport().isAmountEnabled();
        boolean amountExported = ExportFieldPolicy.amountExportable(principal, target, amountEnabled);
        // 字段越界先拒绝（即使目标本身无权限，也把「伪造字段」这一越权意图显式暴露出来）
        ExportFieldPolicy.assertFieldsExportable(target, request.fields(), amountExported);
        ExportFieldPolicy.assertAllowed(principal, target);
        ExportFieldPolicy.ExportDecision decision = ExportFieldPolicy.decide(principal, target, amountEnabled);
        return ApiResponse.success(new VisibilityDtos.ExportCheckResponse(
                decision.target(),
                decision.allowed(),
                decision.masterData(),
                decision.effectiveColumns(),
                decision.excludedFields(),
                decision.amountExportEnabled(),
                decision.amountExported(),
                ErrorCode.SUCCESS.getCode(),
                decision.reason()));
    }

    @GetMapping("/export-policy")
    public ApiResponse<Map<String, Object>> exportPolicy() {
        return ApiResponse.success(ExportFieldPolicy.describe(requirePrincipal(),
                properties.getAuthz().getExport().isAmountEnabled()));
    }

    private static CurrentUser requirePrincipal() {
        DataScopeContext context = DataScopeContext.current();
        if (context == null || context.getPrincipal() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return context.getPrincipal();
    }
}
