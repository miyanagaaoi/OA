package com.oa.authz.api;

import com.oa.authz.api.dto.AuthzDtos;
import com.oa.authz.app.RolePermissionService;
import com.oa.authz.app.RoleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色 / 授权 / 数据域 / 类别 / 组织节点接口，路径与 normify
 * {@code oa.authz.rbac.role}、{@code oa.authz.rbac.grant.menu}、{@code oa.authz.scope.catalog}、
 * {@code oa.authz.scope.category}、{@code oa.authz.rbac.grant.org-node} **逐字一致**：
 *
 * <ul>
 *   <li>{@code GET|POST /api/v1/authz/roles}、{@code PUT|DELETE /api/v1/authz/roles/{id}}</li>
 *   <li>{@code GET|PUT /api/v1/authz/roles/{id}/permissions}（逐级勾选）</li>
 *   <li>{@code PUT /api/v1/authz/roles/{id}/data-scope}、{@code GET /api/v1/authz/data-scopes}</li>
 *   <li>{@code GET|PUT /api/v1/authz/roles/{id}/categories}、{@code GET /api/v1/authz/categories}</li>
 *   <li>{@code GET|PUT /api/v1/authz/roles/{id}/org-nodes}</li>
 * </ul>
 *
 * <p>写操作全部 {@code @Audited}（REQ-LOG-004：角色、数据域、权限树勾选、组织授权范围的任何变更
 * 都要记录操作人、时间与变更前后值）。403（分级授权）一律在服务端强制。
 */
@RestController
@RequestMapping("/api/v1/authz")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    public RoleController(RoleService roleService, RolePermissionService rolePermissionService) {
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
    }

    // ---------------------------------------------------------------- 角色

    @GetMapping("/roles")
    public ApiResponse<List<AuthzDtos.RoleView>> roles(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "roleScope", required = false) String roleScope) {
        return ApiResponse.success(roleService.list(keyword, roleScope));
    }

    @PostMapping("/roles")
    @Audited(action = "create", targetType = "role", recordArgs = true)
    public ApiResponse<AuthzDtos.RoleView> createRole(@Valid @RequestBody AuthzDtos.RoleUpsertRequest request) {
        return ApiResponse.success(roleService.create(request));
    }

    @PutMapping("/roles/{id}")
    @Audited(action = "update", targetType = "role", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.RoleView> updateRole(@PathVariable("id") Long id,
                                                      @Valid @RequestBody AuthzDtos.RoleUpsertRequest request) {
        return ApiResponse.success(roleService.update(id, request));
    }

    @DeleteMapping("/roles/{id}")
    @Audited(action = "delete", targetType = "role", targetId = "#id", recordBefore = true)
    public ApiResponse<AuthDtos.MessageResponse> deleteRole(@PathVariable("id") Long id) {
        roleService.delete(id);
        return ApiResponse.success(new AuthDtos.MessageResponse("角色已删除"));
    }

    // ---------------------------------------------------------------- 角色权限（逐级勾选）

    @GetMapping("/roles/{id}/permissions")
    public ApiResponse<AuthzDtos.RolePermissionView> rolePermissions(@PathVariable("id") Long id) {
        return ApiResponse.success(rolePermissionService.view(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @Audited(action = "grant", targetType = "role_permission", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.RolePermissionView> saveRolePermissions(
            @PathVariable("id") Long id,
            @Valid @RequestBody AuthzDtos.RolePermissionSaveRequest request) {
        return ApiResponse.success(rolePermissionService.save(id, request));
    }

    // ---------------------------------------------------------------- 数据域

    @GetMapping("/data-scopes")
    public ApiResponse<List<AuthzDtos.DataScopeView>> dataScopes() {
        return ApiResponse.success(roleService.dataScopes());
    }

    @PutMapping("/roles/{id}/data-scope")
    @Audited(action = "grant", targetType = "role_data_scope", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.RoleView> updateDataScope(@PathVariable("id") Long id,
                                                           @Valid @RequestBody AuthzDtos.DataScopeUpdateRequest request) {
        return ApiResponse.success(roleService.updateDataScope(id, request));
    }

    // ---------------------------------------------------------------- 类别范围

    @GetMapping("/categories")
    public ApiResponse<List<AuthzDtos.CategoryView>> categories() {
        return ApiResponse.success(roleService.categoryOptions());
    }

    @GetMapping("/roles/{id}/categories")
    public ApiResponse<AuthzDtos.CategoryBindingView> roleCategories(@PathVariable("id") Long id) {
        return ApiResponse.success(roleService.categories(id));
    }

    @PutMapping("/roles/{id}/categories")
    @Audited(action = "grant", targetType = "role_category", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.CategoryBindingView> saveRoleCategories(
            @PathVariable("id") Long id,
            @RequestBody AuthzDtos.CategoryBindRequest request) {
        return ApiResponse.success(roleService.setCategories(id, request == null ? null : request.categories()));
    }

    // ---------------------------------------------------------------- 组织节点

    @GetMapping("/roles/{id}/org-nodes")
    public ApiResponse<AuthzDtos.OrgNodeBindingView> roleOrgNodes(@PathVariable("id") Long id) {
        return ApiResponse.success(roleService.orgNodes(id));
    }

    @PutMapping("/roles/{id}/org-nodes")
    @Audited(action = "grant", targetType = "role_org_node", targetId = "#id", recordBefore = true, recordArgs = true)
    public ApiResponse<AuthzDtos.OrgNodeBindingView> saveRoleOrgNodes(
            @PathVariable("id") Long id,
            @RequestBody AuthzDtos.OrgNodeBindRequest request) {
        return ApiResponse.success(roleService.setOrgNodes(id, request == null ? null : request.orgIds()));
    }
}
