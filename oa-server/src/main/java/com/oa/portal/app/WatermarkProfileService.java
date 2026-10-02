package com.oa.portal.app;

import com.oa.common.config.OaProperties;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.common.scope.DataScopeContext;
import com.oa.common.security.CurrentUser;
import com.oa.identity.app.WatermarkPolicy;
import com.oa.portal.api.dto.PortalDtos;
import org.springframework.stereotype.Service;

/**
 * 门户水印画像装配（{@code GET /api/v1/portal/watermark/profile}）——
 * normify 模块 {@code oa.portal.detail.watermark}（H5 复用同一口径，见
 * {@code oa.portal.h5.watermark}）。
 *
 * <h2>数据域与自读不变式（本类最重要的一条）</h2>
 * <p>画像**只来自当前会话主体** {@link DataScopeContext} 的 {@link CurrentUser}：
 * <ul>
 *   <li>接口没有任何用户参数（路径 / 查询 / 请求体都不接受 {@code userId}），
 *       因此「按路径参数取任意用户的水印」在**签名层面**就不存在；</li>
 *   <li>上下文缺失（未登录、或拦截器被绕过）时抛 {@link ErrorCode#UNAUTHORIZED} → HTTP 401，
 *       而不是回落到「默认用户」或空画像；</li>
 *   <li>实现上**不查数据库**：姓名与工号随会话主体一起装载（{@code AuthInterceptor} →
 *       {@code DataScopeResolver}），既不需要任何受控表 SELECT，也就不存在
 *       「忘带 {@code /* @dataScope *}{@code /} 标记」的越权面。</li>
 * </ul>
 *
 * <h2>透明度 5%–8%</h2>
 * <p>取值 {@code oa.watermark.opacity}，经 {@link WatermarkPolicy#clampOpacity(double)}
 * 夹到 <b>0.05–0.08</b>（DESIGN.md / REQ-USER-004 规定的区间）后才下发；越界配置**夹紧而不是报错**，
 * 理由与边界见 {@code WatermarkPolicy#clampOpacity} 的 javadoc。
 * 文案模板与工号缺失退化规则同样复用 {@link WatermarkPolicy#text(String, String)}
 * （identity 域是水印设计常量的唯一归属地，portal 只组装，不复制常量）。
 */
@Service
public class WatermarkProfileService {

    private final OaProperties properties;

    public WatermarkProfileService(OaProperties properties) {
        this.properties = properties;
    }

    /** 当前登录人的水印画像（未登录 → 401）。 */
    public PortalDtos.WatermarkProfileView currentProfile() {
        DataScopeContext context = DataScopeContext.require();
        CurrentUser principal = context.getPrincipal();
        if (principal == null) {
            // 有上下文但无主体 = 未认证（例如系统上下文被误用），一律 401
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String employeeNo = blankToNull(principal.employeeNo());
        return new PortalDtos.WatermarkProfileView(
                properties.getWatermark().isEnabled(),
                WatermarkPolicy.text(principal.name(), employeeNo),
                WatermarkPolicy.clampOpacity(properties.getWatermark().getOpacity()),
                employeeNo,
                principal.name());
    }

    /** 空白串归一为 {@code null}：工号缺失必须显式下发 {@code null}，不能是 {@code ""}。 */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
