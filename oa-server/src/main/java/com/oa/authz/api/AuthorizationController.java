package com.oa.authz.api;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.app.AuthzChangeLogService;
import com.oa.authz.app.AuthzOperatorProvider;
import com.oa.authz.app.AuthorizationPolicy;
import com.oa.authz.app.EffectivePermissionService;
import com.oa.authz.app.UserRoleService;
import com.oa.common.api.ApiResponse;
import com.oa.common.api.PageResult;
import com.oa.common.audit.Audited;
import com.oa.common.error.BizException;
import com.oa.common.error.ErrorCode;
import com.oa.identity.api.dto.AuthDtos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 有效权限、用户角色分配、缓存失效与变更留痕接口，路径与 normify
 * {@code oa.authz.rbac.user-role}、{@code oa.authz.rbac.effective}、
 * {@code oa.authz.rbac.change-log} **逐字一致**：
 *
 * <ul>
 *   <li>{@code GET|POST /api/v1/authz/users/{id}/roles}、{@code DELETE .../roles/{assignmentId}}</li>
 *   <li>{@code GET  /api/v1/authz/effective-permissions}</li>
 *   <li>{@code POST /api/v1/authz/cache/invalidate}（按用户或全量；仅系统管理员）</li>
 *   <li>{@code GET  /api/v1/authz/change-logs}（分页，来源 {@code sys_log}）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/authz")
public class AuthorizationController {

    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final UserRoleService userRoleService;
    private final EffectivePermissionService effectivePermissionService;
    private final AuthzChangeLogService changeLogService;
    private final AuthzOperatorProvider operatorProvider;

    public AuthorizationController(UserRoleService userRoleService,
                                   EffectivePermissionService effectivePermissionService,
                                   AuthzChangeLogService changeLogService,
                                   AuthzOperatorProvider operatorProvider) {
        this.userRoleService = userRoleService;
        this.effectivePermissionService = effectivePermissionService;
        this.changeLogService = changeLogService;
        this.operatorProvider = operatorProvider;
    }

    // ---------------------------------------------------------------- 用户角色分配（import-spec 第 ⑤ 步）

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<AuthzDtos.UserRoleView>> userRoles(@PathVariable("id") Long id) {
        return ApiResponse.success(userRoleService.list(id));
    }

    @PostMapping("/users/{id}/roles")
    @Audited(action = "grant", targetType = "user_role", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.UserRoleView> assignRole(@PathVariable("id") Long id,
                                                          @Valid @RequestBody AuthzDtos.UserRoleAssignRequest request) {
        return ApiResponse.success(userRoleService.assign(id, request));
    }

    @DeleteMapping("/users/{id}/roles/{assignmentId}")
    @Audited(action = "revoke", targetType = "user_role", targetId = "#assignmentId",
            recordBefore = true, recordArgs = true)
    public ApiResponse<AuthDtos.MessageResponse> revokeRole(@PathVariable("id") Long id,
                                                            @PathVariable("assignmentId") Long assignmentId) {
        userRoleService.revoke(id, assignmentId);
        return ApiResponse.success(new AuthDtos.MessageResponse("角色授权已回收"));
    }

    // ---------------------------------------------------------------- 有效权限 / 缓存

    /** 当前登录人的有效权限码与菜单（与 {@code GET /api/v1/auth/me} 同源，供管理后台单独刷新）。 */
    @GetMapping("/effective-permissions")
    public ApiResponse<AuthzDtos.EffectivePermissionView> effectivePermissions() {
        return ApiResponse.success(effectivePermissionService.effectivePermissions(operatorProvider.currentUser()));
    }

    /**
     * 失效权限缓存（{@code authz:perms:{userId}}）。
     *
     * <p>{@code {"userId": 12}} 失效单个用户；{@code {"all": true}} 全量失效。
     * 两者都不给 → 400；**仅系统管理员**可用（见 {@link AuthorizationPolicy#assertCanInvalidateCache}）。
     */
    @PostMapping("/cache/invalidate")
    @Audited(action = "invalidate", targetType = "permission_cache", recordArgs = true)
    public ApiResponse<AuthzDtos.CacheInvalidateResponse> invalidateCache(
            @RequestBody AuthzDtos.CacheInvalidateRequest request) {
        AuthorizationPolicy.Operator operator = operatorProvider.current();
        AuthorizationPolicy.assertCanInvalidateCache(operator);
        boolean all = request != null && Boolean.TRUE.equals(request.all());
        if (all) {
            return ApiResponse.success(new AuthzDtos.CacheInvalidateResponse(effectivePermissionService.invalidateAll()));
        }
        if (request == null || request.userId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "userId 与 all 至少提供一个：{userId: 12} 失效单用户，{all: true} 全量失效");
        }
        effectivePermissionService.invalidateUser(request.userId());
        return ApiResponse.success(new AuthzDtos.CacheInvalidateResponse(1));
    }

    // ---------------------------------------------------------------- 变更留痕

    @GetMapping("/change-logs")
    public ApiResponse<PageResult<AuthzDtos.ChangeLogView>> changeLogs(
            @RequestParam(name = "page", required = false, defaultValue = "1") long page,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "userId", required = false) Long userId) {
        return ApiResponse.success(changeLogService.page(page, size == null ? DEFAULT_PAGE_SIZE : size,
                action, targetType, userId));
    }
}
