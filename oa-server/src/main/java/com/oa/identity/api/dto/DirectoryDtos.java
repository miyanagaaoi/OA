package com.oa.identity.api.dto;

import java.util.List;

/**
 * 通讯录与水印接口出入参。
 *
 * <p>水印口径（REQ-USER-004 / AC-44 / PRD §6.8）：内容为「**姓名 + 工号**」，
 * 透明度 5%–8%、旋转 -24°，**不得遮挡按钮与表单值**。
 */
public final class DirectoryDtos {

    private DirectoryDtos() {
    }

    /** 通讯录分组（按组织）。 */
    public record DirectoryGroup(
            Long orgId,
            String orgName,
            String orgPath,
            String orgType,
            String orgTypeLabel,
            Integer depth,
            int userCount,
            List<DirectoryUser> users
    ) {
    }

    /** 通讯录条目（手机号按角色脱敏）。 */
    public record DirectoryUser(
            Long id,
            String name,
            String account,
            String employeeNo,
            String phone,
            boolean phoneMasked,
            String position,
            String email,
            String orgName
    ) {
    }

    /** 水印策略（前端据此渲染，不写死常量）。 */
    public record WatermarkPolicyView(
            boolean enabled,
            String contentLabel,
            String template,
            int minOpacityPercent,
            int maxOpacityPercent,
            int rotationDegrees,
            boolean avoidInteractiveElements,
            String note
    ) {
    }

    /** 当前登录人的水印载荷（{@code GET /users/me/watermark}）。 */
    public record WatermarkView(
            Long userId,
            String name,
            String employeeNo,
            String text,
            boolean employeeNoMissing,
            WatermarkPolicyView policy
    ) {
    }
}
