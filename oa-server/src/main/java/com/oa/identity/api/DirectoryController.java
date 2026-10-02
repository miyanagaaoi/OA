package com.oa.identity.api;

import com.oa.common.api.ApiResponse;
import com.oa.identity.api.dto.DirectoryDtos;
import com.oa.identity.app.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通讯录与水印策略（{@code /api/v1/identity/directory}、{@code /api/v1/identity/watermark-policy}）。
 *
 * <ul>
 *   <li>{@code GET /directory}：按组织分组的**数据域内**可见人员；手机号默认脱敏
 *       （本人与系统管理员可见完整值，PRD §5.3 / TC-AUTH-007）；</li>
 *   <li>{@code GET /watermark-policy}：水印透明度/旋转/内容口径（REQ-USER-004 / AC-44）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/identity")
public class DirectoryController {

    private final UserService userService;

    public DirectoryController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/directory")
    public ApiResponse<List<DirectoryDtos.DirectoryGroup>> directory(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "orgId", required = false) Long orgId,
            @RequestParam(name = "includeSubOrg", required = false, defaultValue = "true") Boolean includeSubOrg) {
        return ApiResponse.success(userService.directory(keyword, orgId, includeSubOrg));
    }

    @GetMapping("/watermark-policy")
    public ApiResponse<DirectoryDtos.WatermarkPolicyView> watermarkPolicy() {
        return ApiResponse.success(userService.watermarkPolicy());
    }
}
