package com.oa.authz.api;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.app.PermissionTreeService;
import com.oa.common.api.ApiResponse;
import com.oa.common.audit.Audited;
import com.oa.identity.api.dto.AuthDtos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限树维护接口（REQ-ADMIN-003），路径与 normify {@code oa.authz.rbac.permission-tree} 逐字一致：
 *
 * <ul>
 *   <li>{@code GET    /api/v1/authz/permissions/tree}</li>
 *   <li>{@code POST   /api/v1/authz/permissions}</li>
 *   <li>{@code PUT    /api/v1/authz/permissions/{id}}</li>
 *   <li>{@code DELETE /api/v1/authz/permissions/{id}}</li>
 * </ul>
 *
 * <p>增删改全部走 {@code @Audited}（REQ-LOG-004：权限树勾选与节点变更必须留痕，含变更前后值），
 * 授权判定（仅系统管理员可维护权限树）由服务层与 {@code DataScopeContext} 组合完成；
 * 本控制器不重复做角色判断（与既有 {@code UserController} 的分工口径一致）。
 */
@RestController
@RequestMapping("/api/v1/authz/permissions")
public class PermissionController {

    private final PermissionTreeService permissionTreeService;

    public PermissionController(PermissionTreeService permissionTreeService) {
        this.permissionTreeService = permissionTreeService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<AuthzDtos.PermissionNodeView>> tree() {
        return ApiResponse.success(permissionTreeService.tree());
    }

    @PostMapping
    @Audited(action = "create", targetType = "permission", recordArgs = true)
    public ApiResponse<AuthzDtos.PermissionView> create(@Valid @RequestBody AuthzDtos.PermissionUpsertRequest request) {
        return ApiResponse.success(permissionTreeService.create(request));
    }

    @PutMapping("/{id}")
    @Audited(action = "update", targetType = "permission", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.PermissionView> update(@PathVariable("id") Long id,
                                                        @Valid @RequestBody AuthzDtos.PermissionUpsertRequest request) {
        return ApiResponse.success(permissionTreeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Audited(action = "delete", targetType = "permission", targetId = "#id", recordBefore = true)
    public ApiResponse<AuthDtos.MessageResponse> delete(@PathVariable("id") Long id) {
        permissionTreeService.delete(id);
        return ApiResponse.success(new AuthDtos.MessageResponse("权限节点已删除，相关角色授权已级联清理"));
    }
}
