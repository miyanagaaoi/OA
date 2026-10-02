package com.oa.portal.api;

import com.oa.common.api.ApiResponse;
import com.oa.portal.api.dto.PortalDtos;
import com.oa.portal.app.WatermarkProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户接口（{@code /api/v1/portal/**}），路径与 normify {@code oa.portal.*} 契约一致：
 *
 * <ul>
 *   <li>{@code GET /api/v1/portal/watermark/profile} 当前登录人的水印画像
 *       （{@code oa.portal.detail.watermark}，H5 与详情页共用口径）；</li>
 * </ul>
 *
 * <p><b>本控制器刻意没有 {@code @Anonymous}</b>：水印是「谁看了这一页」的追责手段（REQ-USER-004 /
 * AC-44），画像只能发给**已登录的本人**。因此
 * <ul>
 *   <li>不加进 {@code oa.web.permit-all}（路径白名单），也不标注 {@code @Anonymous}，
 *       由 {@code AuthInterceptor} 在未登录时直接 401；</li>
 *   <li>实现层（{@link WatermarkProfileService}）再独立校验一次上下文，
 *       即便将来拦截器配置被改动，也不会把画像发给匿名请求；</li>
 *   <li>接口不接受任何用户标识参数 —— 只能取**当前会话用户**，不存在「按 id 取他人水印」的入口。</li>
 * </ul>
 *
 * <p>归属说明：放在 {@code com.oa.portal.*} 而不是 {@code identity} 域，因为该端点是
 * 「门户呈现层的水印画像」（详情页 / H5 两个前端模块共用），不是身份域的会话管理能力；
 * 身份域只提供设计常量与文案模板（{@code com.oa.identity.app.WatermarkPolicy}）。
 */
@RestController
@RequestMapping("/api/v1/portal")
public class WatermarkController {

    private final WatermarkProfileService watermarkProfileService;

    public WatermarkController(WatermarkProfileService watermarkProfileService) {
        this.watermarkProfileService = watermarkProfileService;
    }

    @GetMapping("/watermark/profile")
    public ApiResponse<PortalDtos.WatermarkProfileView> watermarkProfile() {
        return ApiResponse.success(watermarkProfileService.currentProfile());
    }
}
