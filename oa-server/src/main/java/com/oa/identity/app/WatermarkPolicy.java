package com.oa.identity.app;

import com.oa.identity.api.dto.DirectoryDtos;

/**
 * 水印策略 —— <b>纯函数 + 常量集中处</b>。
 *
 * <p>依据：
 * <ul>
 *   <li>PRD REQ-USER-004 / §6.8 / §13.2：「H5 端与单据详情页显示『姓名 + 工号』水印，
 *       透明度 **5%–8%**、旋转 **-24°**，水印不得遮挡按钮与表单值」；</li>
 *   <li>doc/test-cases.md AC-44 / TC-USER-007/008：水印内容为当前登录人「姓名+工号」，
 *       **不含他人姓名**；透明度 5%–8%、旋转 -24°；</li>
 *   <li>doc/import-spec.md §3.3：{@code employee_no} 是水印工号的唯一来源
 *       （模板定为必填 + 唯一）→ 缺失即数据缺口，出参用 {@code employeeNoMissing} 显式标注。</li>
 * </ul>
 *
 * <p>透明度/旋转/间距是**设计常量**（不是运行期配置项），集中在本类，禁止散落到 Controller。
 */
public final class WatermarkPolicy {

    /** 水印文案模板（{@code {name}} / {@code {employeeNo}} 占位）。 */
    public static final String TEMPLATE = "{name} {employeeNo}";

    /** 内容口径（中文标签，给前端与验收对照用）。 */
    public static final String CONTENT_LABEL = "姓名+工号";

    public static final int MIN_OPACITY_PERCENT = 5;
    public static final int MAX_OPACITY_PERCENT = 8;
    public static final int ROTATION_DEGREES = -24;

    /** 透明度下限（小数口径 0.05）。 */
    public static final double MIN_OPACITY = MIN_OPACITY_PERCENT / 100.0;

    /** 透明度上限（小数口径 0.08）。 */
    public static final double MAX_OPACITY = MAX_OPACITY_PERCENT / 100.0;

    private WatermarkPolicy() {
    }

    /**
     * 透明度夹紧到 DESIGN.md 规定的 <b>5%–8%</b> 区间（越界即夹紧，绝不放行）。
     *
     * <p>背景（2026-xx 收口）：<ul>
     *   <li>PRD REQ-USER-004 / AC-44 与 DESIGN.md 都要求水印透明度落在 5%–8%：
     *       低于 5% 等于水印看不见（失去「谁看了这页」的追责意义），高于 8% 会干扰阅读；</li>
     *   <li>{@code oa.watermark.opacity} 是**环境配置**，可能被手改为越界值
     *       （例如误写成 {@code 0.6} 或百分数 {@code 6}）——直接下发会让前端画出不合规水印，
     *       因此服务端在**下发前**统一夹紧，前端另有一层 {@code clampWatermarkOpacity} 兜底；</li>
     *   <li>{@code NaN} / 无穷等非法浮点按**区间中值 0.065** 处理（不落进 0 或 1 的极端）。</li>
     * </ul>
     *
     * @param opacity 配置透明度（小数口径，如 0.06）
     * @return 夹紧后的透明度，恒在 {@code [0.05, 0.08]}
     */
    public static double clampOpacity(double opacity) {
        if (Double.isNaN(opacity) || Double.isInfinite(opacity)) {
            return (MIN_OPACITY + MAX_OPACITY) / 2;
        }
        if (opacity < MIN_OPACITY) {
            return MIN_OPACITY;
        }
        if (opacity > MAX_OPACITY) {
            return MAX_OPACITY;
        }
        return opacity;
    }

    /** 策略视图（{@code GET /api/v1/identity/watermark-policy}）。 */
    public static DirectoryDtos.WatermarkPolicyView view() {
        return new DirectoryDtos.WatermarkPolicyView(
                true,
                CONTENT_LABEL,
                TEMPLATE,
                MIN_OPACITY_PERCENT,
                MAX_OPACITY_PERCENT,
                ROTATION_DEGREES,
                true,
                "水印内容为当前登录人「姓名 + 工号」，不含他人姓名；不得遮挡按钮与表单值（AC-44）");
    }

    /** 水印文本：{@code 王甲 A0001}；工号缺失时退化为仅姓名。 */
    public static String text(String name, String employeeNo) {
        String safeName = name == null ? "" : name.trim();
        String safeNo = employeeNo == null ? "" : employeeNo.trim();
        if (safeNo.isEmpty()) {
            return safeName;
        }
        return safeName.isEmpty() ? safeNo : safeName + " " + safeNo;
    }

    /** 工号缺失（数据缺口，import-spec §3.3 要求必填）。 */
    public static boolean employeeNoMissing(String employeeNo) {
        return employeeNo == null || employeeNo.isBlank();
    }

    /** 当前登录人的水印载荷。 */
    public static DirectoryDtos.WatermarkView payload(Long userId, String name, String employeeNo) {
        return new DirectoryDtos.WatermarkView(
                userId, name, employeeNo, text(name, employeeNo), employeeNoMissing(employeeNo), view());
    }
}
