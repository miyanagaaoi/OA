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

    private WatermarkPolicy() {
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
