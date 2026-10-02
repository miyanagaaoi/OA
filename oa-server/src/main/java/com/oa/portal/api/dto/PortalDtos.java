package com.oa.portal.api.dto;

/**
 * 门户（portal）域 DTO —— 与 normify {@code oa.portal.detail.watermark} /
 * {@code oa.portal.h5.watermark} 的接口契约逐字对齐。
 */
public final class PortalDtos {

    private PortalDtos() {
    }

    /**
     * 当前登录人的水印画像（{@code GET /api/v1/portal/watermark/profile}）。
     *
     * <p>字段形状与前端 {@code oa-web/src/types/api.d.ts} 的 {@code WatermarkProfile}
     * 及 {@code oa-web/src/api/auth.ts} 的 {@code fetchWatermarkProfile()} 对齐，
     * 前端不再回退演示数据。
     *
     * @param enabled    水印开关（服务端默认口径，见 {@code oa.watermark.enabled}）
     * @param text       水印文案 = 「姓名 + 工号」（如 {@code 系统管理员 10086}）；
     *                   工号缺失时**退化为仅姓名**，绝不拼出多余空格或 {@code null} 字面量
     * @param opacity    水印透明度，**恒在 0.05–0.08**（DESIGN.md 规定区间，越界已在服务端夹紧）
     * @param employeeNo 工号（{@code sys_user.employee_no}）；缺失时为 {@code null}
     *                   （前端据此提示「工号缺失」而不是显示空串）
     * @param name       姓名（当前登录人）
     */
    public record WatermarkProfileView(
            boolean enabled,
            String text,
            double opacity,
            String employeeNo,
            String name
    ) {
    }
}
