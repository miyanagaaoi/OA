package com.oa.authz.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 权限域（authz）DTO —— 与 normify {@code oa.authz.rbac.*} / {@code oa.authz.scope.*}
 * 的接口契约逐字对齐。
 *
 * <p>命名与形状约定：
 * <ul>
 *   <li>权限树读取返回**嵌套树**（{@code children}），保存/回显用**扁平 id 集合**；</li>
 *   <li>角色勾选保存只提交 {@code permissionIds}（勾选语义由后端
 *       {@code PermissionTreePolicy} 规范化，见其类注释的存储策略）；</li>
 *   <li>{@code *Id} 字段用 {@code Long}：Jackson 侧 Long 以字符串下发，但反序列化兼容
 *       {@code "123"} 与 {@code 123}（{@code JacksonConfig} 已断言该口径）。</li>
 * </ul>
 */
public final class AuthzDtos {

    private AuthzDtos() {
    }

    // ================================================================ 权限树

    /** 权限树节点（嵌套）。 */
    public record PermissionNodeView(
            Long id,
            Long parentId,
            String code,
            String name,
            String permType,
            String url,
            Integer sortNo,
            List<PermissionNodeView> children
    ) {
    }

    /** 权限节点（扁平）。 */
    public record PermissionView(
            Long id,
            Long parentId,
            String code,
            String name,
            String permType,
            String url,
            Integer sortNo
    ) {
    }

    /** 新增 / 修改权限节点（{@code POST|PUT /api/v1/authz/permissions}）。 */
    public record PermissionUpsertRequest(
            Long parentId,
            @NotBlank(message = "perm_type 不能为空") String permType,
            @NotBlank(message = "权限码不能为空") @Size(max = 64, message = "权限码不得超过 64 字符") String code,
            @NotBlank(message = "权限名称不能为空") @Size(max = 50, message = "权限名称不得超过 50 字符") String name,
            @Size(max = 255, message = "url 不得超过 255 字符") String url,
            Integer sortNo
    ) {
    }

    // ================================================================ 角色

    /**
     * 角色视图。
     *
     * @param builtIn 是否为 9 个内置角色码之一（受保护：不可删除、不可改 code 与 role_scope）
     */
    public record RoleView(
            Long id,
            String code,
            String name,
            String roleScope,
            String dataScope,
            String remark,
            boolean builtIn
    ) {
    }

    /**
     * 新增 / 修改角色。
     *
     * <p>{@code categories} 仅在 {@code dataScope=group_category} 时有意义：
     * 新增时可直接带上；修改时省略表示「不改动已有类别」。
     */
    public record RoleUpsertRequest(
            String code,
            @NotBlank(message = "角色名称不能为空") @Size(max = 50, message = "角色名称不得超过 50 字符") String name,
            String roleScope,
            String dataScope,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark,
            List<String> categories
    ) {
    }

    /**
     * 角色已勾选权限。
     *
     * @param checkedIds      全选节点（前端复选框树的「已勾选键」；再次提交时**用这个集合**）
     * @param permissionIds   落库集合 G（展开为全量：被勾选节点 + 全部后代 + 必要祖先）
     * @param permissionCodes 对应的权限码（前端按 code 判可见性）
     * @param halfCheckedIds  半选态节点（自身在 G 中但存在未授予的后代）
     */
    public record RolePermissionView(
            Long roleId,
            List<Long> checkedIds,
            List<Long> permissionIds,
            List<String> permissionCodes,
            List<Long> halfCheckedIds
    ) {
    }

    /**
     * 保存角色勾选（{@code PUT /api/v1/authz/roles/{id}/permissions}）。
     *
     * <p>{@code permissionIds} <b>必须存在</b>但**允许为空数组**——空数组 = 取消该角色的全部权限
     * （「取消父节点 ⇒ 后代全部取消」的极端形态），因此这里只做 {@code @NotNull}。
     *
     * <p>{@code mode} 决定入参含义，**缺省（不传 / null / 空串）= {@code auto}**：
     * <ul>
     *   <li>{@code auto}（缺省，前端实际口径）：后端看入参是否**祖先闭合**——
     *       闭合（= {@code GET} 返回的 {@code permissionIds}，即前端 {@code checked ∪ halfChecked}
     *       原样回传）⇒ 视为「完整已授予集合」，**原样落库**（幂等，不会连锁授予兄弟子树）；
     *       不闭合（例如只提交叶子）⇒ 视为「全选节点集合」，按「勾选父 ⇒ 后代全授予 + 补齐祖先」展开；</li>
     *   <li>{@code ticks}：强制按「全选节点集合」展开（调用方明确只提交勾选节点时用）；</li>
     *   <li>{@code granted}：强制按「完整已授予集合」处理；不祖先闭合即 400
     *       （「父节点未授予时子节点不得单独授予」）。</li>
     * </ul>
     *
     * <p>请求体中的额外字段（如前端一并提交的 {@code leafIds} / {@code halfCheckedIds}）
     * **会被忽略**：{@code oa} 的 Jackson 已关闭 {@code FAIL_ON_UNKNOWN_PROPERTIES}
     * （见 {@code WebMvcConfig#oaJacksonCustomizer}）。
     */
    public record RolePermissionSaveRequest(
            @NotNull(message = "permissionIds 不能为 null；如需清空全部权限请传空数组") List<Long> permissionIds,
            String mode
    ) {
    }

    // ================================================================ 数据域 / 类别

    /** 数据域取值（{@code GET /api/v1/authz/data-scopes}）。 */
    public record DataScopeView(String value, String label, String description) {
    }

    /** 设置角色数据域（{@code PUT /api/v1/authz/roles/{id}/data-scope}）。 */
    public record DataScopeUpdateRequest(@NotBlank(message = "data_scope 不能为空") String dataScope) {
    }

    /** 事项类别选项（{@code GET /api/v1/authz/categories}）。 */
    public record CategoryView(String value, String label) {
    }

    /** 角色类别范围（{@code GET|PUT /api/v1/authz/roles/{id}/categories}）。 */
    public record CategoryBindingView(Long roleId, String dataScope, List<String> categories) {
    }

    /** 设置角色类别范围（空数组 = 清空；{@code group_category} 角色清空会被 400 拒绝）。 */
    public record CategoryBindRequest(List<String> categories) {
    }

    // ================================================================ 组织节点

    /** 组织节点条目。 */
    public record OrgNodeView(Long orgId, String name, String path, String orgType) {
    }

    /** 角色组织节点范围（{@code GET|PUT /api/v1/authz/roles/{id}/org-nodes}）。 */
    public record OrgNodeBindingView(Long roleId, List<Long> orgIds, List<OrgNodeView> nodes) {
    }

    /** 设置角色组织节点范围。 */
    public record OrgNodeBindRequest(List<Long> orgIds) {
    }

    // ================================================================ 用户角色分配

    /**
     * 用户角色分配条目（import-spec §3.6 的落库形态）。
     *
     * @param assignmentId {@code sys_user_role.id}
     * @param scopeOrgId   生效组织范围；为空 = 按角色默认数据域
     * @param scopeOrgPath 生效组织范围的组织路径（由服务层还原，便于导入导出往返）
     */
    public record UserRoleView(
            Long assignmentId,
            Long userId,
            Long roleId,
            String roleCode,
            String roleName,
            String roleScope,
            String dataScope,
            Long scopeOrgId,
            String scopeOrgPath,
            String remark,
            String createdAt
    ) {
    }

    /**
     * 授予角色（{@code POST /api/v1/authz/users/{id}/roles}）。
     *
     * <p>{@code roleCode} 与 {@code roleId} 二选一（同时给出时以 {@code roleId} 为准并校验一致性）；
     * {@code scopeOrgPath} 与 {@code scopeOrgId} 二选一（同时给出时以 {@code scopeOrgId} 为准）。
     */
    public record UserRoleAssignRequest(
            Long roleId,
            String roleCode,
            Long scopeOrgId,
            String scopeOrgPath,
            @Size(max = 255, message = "备注不得超过 255 字符") String remark
    ) {
    }

    // ================================================================ 有效权限 / 缓存

    /**
     * 有效权限（{@code GET /api/v1/authz/effective-permissions}）。
     *
     * @param permissions  全部角色权限码的并集（{@code /auth/me} 与前端管理入口可见性的唯一真实来源）
     * @param menus        按权限树装配出的菜单子树（仅含被授予的 menu 节点）
     * @param isSuperAdmin 是否拥有 {@code admin} 角色
     */
    public record EffectivePermissionView(
            Long userId,
            List<String> roleCodes,
            List<String> permissions,
            List<PermissionNodeView> menus,
            @JsonProperty("isSuperAdmin") boolean isSuperAdmin
    ) {
    }

    /** 失效权限缓存（{@code POST /api/v1/authz/cache/invalidate}）：{@code userId} 与 {@code all=true} 二选一。 */
    public record CacheInvalidateRequest(Long userId, Boolean all) {
    }

    /** 失效结果。 */
    public record CacheInvalidateResponse(int invalidatedUsers) {
    }

    // ================================================================ 变更日志

    /** 权限变更记录（{@code GET /api/v1/authz/change-logs}，来源 {@code sys_log}）。 */
    public record ChangeLogView(
            Long id,
            Long userId,
            String userName,
            String action,
            String targetType,
            Long targetId,
            String beforeJson,
            String afterJson,
            String ip,
            String createdAt
    ) {
    }
}
