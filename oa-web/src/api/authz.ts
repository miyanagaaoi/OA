/**
 * oa-web · 权限域接口（oa.authz.rbac / oa.authz.scope）
 * ----------------------------------------------------------------------------
 * 路径为后端并行实现的**定稿契约**（`/api/v1/authz/**`），本文件把
 * `types/authz-wire.d.ts`（后端 DTO 镜像）映射为前端领域模型 `types/authz.d.ts`。
 *
 *   GET    /api/v1/authz/permissions/tree                权限树（menu/button/api 三类）
 *   POST   /api/v1/authz/permissions                     新增权限节点
 *   PUT    /api/v1/authz/permissions/{id}                修改权限节点
 *   DELETE /api/v1/authz/permissions/{id}                删除权限节点
 *   GET    /api/v1/authz/roles                           角色列表（分页；后端不分页时也兼容）
 *   POST   /api/v1/authz/roles                           新增角色
 *   PUT    /api/v1/authz/roles/{id}                      修改角色（内置角色 code/role_scope 只读）
 *   DELETE /api/v1/authz/roles/{id}                      删除角色（内置角色被服务端拒绝）
 *   GET    /api/v1/authz/roles/{id}/permissions          角色已授权限（逐级勾选回显）
 *   PUT    /api/v1/authz/roles/{id}/permissions          保存角色权限（**逐级勾选**）
 *   GET    /api/v1/authz/data-scopes                     数据域字典（五值 + 是否需配类别）
 *   PUT    /api/v1/authz/roles/{id}/data-scope           修改角色数据域
 *   GET    /api/v1/authz/categories                      事项类别字典（经营/经济/行政/人力/投资）
 *   GET    /api/v1/authz/roles/{id}/categories           角色类别范围
 *   PUT    /api/v1/authz/roles/{id}/categories           保存角色类别范围
 *   GET    /api/v1/authz/roles/{id}/org-nodes            角色可访问组织节点
 *   PUT    /api/v1/authz/roles/{id}/org-nodes            保存角色组织节点范围
 *   GET    /api/v1/authz/users/{id}/roles                用户角色分配列表
 *   POST   /api/v1/authz/users/{id}/roles                分配角色（重复分配 → 409）
 *   DELETE /api/v1/authz/users/{id}/roles/{assignmentId} 撤销角色分配
 *   GET    /api/v1/authz/effective-permissions           有效权限（角色码 + 权限码 + 数据域）
 *   POST   /api/v1/authz/cache/invalidate                失效权限缓存（改权限后调用）
 *   GET    /api/v1/authz/change-logs                     权限变更日志（REQ-LOG-004，分页）
 *
 * 口径来源：
 *   · `doc/data-model.md` 3.1–3.5；`doc/prd-0.1.md` 5.2 / 6.10（REQ-ADMIN-003 / 006）、
 *     6.9 REQ-LOG-004；`doc/import-spec.md` §2.3 / §3.6 / §4.6
 *   · `normify-oa/modules/oa/authz/rbac/**`（planned）：role / permission-tree / user-role /
 *     grant.{menu,org-node} / change-log / effective
 *
 * 三点实现约定：
 *   1. **映射层承担 Long → string**：id 与分页计数在 JSON 里是字符串（见 `types/authz-wire.d.ts`），
 *      领域模型里 id 恒为 string、计数恒为 number，页面不做二次转换。
 *   2. **不做演示数据降级**（与 `api/org.ts` / `api/user.ts` 同口径）：角色与权限是**安全主数据**，
 *      静默回落演示数据会让管理员误判真实授权范围。接口不可用时页面显式报错。
 *   3. **保存权限后失效缓存**：`invalidatePermissionCache` 由 store 在写成功后调用，
 *      界面提示「相关用户权限缓存已失效，下次请求生效」（任务书硬要求 5）。
 */
import { del, get, post, put } from './http'
import { isBuiltInRoleCode } from '@/utils/authz'
import type { DataScope } from '@/types/api'
import type { WireId, WireNumber, WirePageResult } from '@/types/identity-wire'
import type {
  AuthzChangeLog,
  AuthzChangeLogPage,
  AuthzChangeLogQuery,
  CacheInvalidatePayload,
  CacheInvalidateResult,
  CategoryOption,
  ChangeTargetType,
  DataScopeOption,
  EffectivePermission,
  MatterCategory,
  PermissionNode,
  PermissionType,
  PermissionUpsertPayload,
  RoleAssignment,
  RoleAssignmentPayload,
  RoleCategoryGrant,
  RoleItem,
  RoleListResult,
  RoleOrgNodeGrant,
  RolePermissionGrant,
  RolePermissionUpdatePayload,
  RoleScope,
  RoleUpsertPayload,
} from '@/types/authz'
import type {
  WireCacheInvalidateRequest,
  WireCacheInvalidateResult,
  WireCategoryView,
  WireChangeLogQueryParams,
  WireChangeLogView,
  WireDataScopeView,
  WireEffectivePermissionView,
  WirePermissionUpsertRequest,
  WirePermissionView,
  WireRoleAssignmentCreateRequest,
  WireRoleAssignmentView,
  WireRoleCategoryUpdateRequest,
  WireRoleCategoryView,
  WireRoleListResponse,
  WireRoleOrgNodeUpdateRequest,
  WireRoleOrgNodeView,
  WireRolePermissionUpdateRequest,
  WireRolePermissionView,
  WireRoleUpsertRequest,
  WireRoleView,
} from '@/types/authz-wire'

// ---------------------------------------------------------------------------
// 映射工具（Long → string / Number；枚举 code 收窄）
// ---------------------------------------------------------------------------
function sid(value: WireId | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

function sidOrNull(value: WireId | null | undefined): string | null {
  return value === null || value === undefined ? null : String(value)
}

/**
 * 计数归一：分页字段与 `Long` 计数在 JSON 里是字符串，`int` 计数是 number，两种都吃。
 * 非法/缺失值退回 fallback（缺省页大小用调用方请求值）。
 */
function toCount(value: WireNumber | null | undefined, fallback: number): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function toPermissionType(value: string | null | undefined): PermissionType {
  switch (value) {
    case 'menu':
    case 'button':
    case 'api':
      return value
    default:
      // `sys_permission.perm_type` 是 NOT NULL 三值；未知值按最保守的叶子节点处理
      return 'button'
  }
}

function toRoleScope(value: string | null | undefined): RoleScope {
  return value === 'group' ? 'group' : 'company'
}

function toDataScope(value: string | null | undefined): DataScope {
  switch (value) {
    case 'self':
    case 'dept':
    case 'company':
    case 'group_all':
    case 'group_category':
      return value
    default:
      // `sys_role.data_scope` 有 CHECK 约束（五值）；未知值退到最小可见范围，不放大权限
      return 'self'
  }
}

function toMatterCategory(value: string | null | undefined): MatterCategory | null {
  switch (value) {
    case 'business':
    case 'economy':
    case 'admin':
    case 'hr':
    case 'invest':
      return value
    default:
      return null
  }
}

function toMatterCategories(values: string[] | null | undefined): MatterCategory[] {
  const result: MatterCategory[] = []
  for (const value of values ?? []) {
    const category = toMatterCategory(value)
    if (category && !result.includes(category)) result.push(category)
  }
  return result
}

function toChangeTargetType(value: string | null | undefined): ChangeTargetType {
  switch (value) {
    case 'role':
    case 'role_permission':
    case 'user_role':
    case 'data_scope':
    case 'category':
    case 'org_node':
      return value
    default:
      return 'role'
  }
}

// ---------------------------------------------------------------------------
// 权限树
// ---------------------------------------------------------------------------
function toPermissionNode(view: WirePermissionView): PermissionNode {
  const children = view.children?.length ? view.children.map(toPermissionNode) : undefined
  return {
    id: sid(view.id),
    parentId: sidOrNull(view.parentId),
    code: view.code,
    name: view.name,
    permType: toPermissionType(view.permType),
    permTypeLabel: view.permTypeLabel ?? undefined,
    url: view.url ?? null,
    sortNo: view.sortNo ?? 0,
    children,
  }
}

/**
 * 后端可能给**树**（children 嵌套），也可能给**平铺数组**（只带 parentId）。
 * 这里归一成树：先按 children 判定，若整份数据没有任何 children 则按 parentId 组装；
 * 父节点缺失（服务端只下发子树）时把该节点提升为根，避免整棵权限树渲染不出来。
 */
function normalizePermissionTree(views: WirePermissionView[]): PermissionNode[] {
  const hasNested = views.some((view) => (view.children?.length ?? 0) > 0)
  if (hasNested) return views.map(toPermissionNode)

  const nodes = views.map(toPermissionNode)
  const byId = new Map<string, PermissionNode>()
  for (const node of nodes) byId.set(node.id, node)

  const roots: PermissionNode[] = []
  for (const node of nodes) {
    const parent = node.parentId ? byId.get(node.parentId) : undefined
    if (!parent || parent.id === node.id) {
      roots.push(node)
      continue
    }
    parent.children = parent.children ? [...parent.children, node] : [node]
  }
  const sortTree = (list: PermissionNode[]): void => {
    list.sort((a, b) => a.sortNo - b.sortNo || a.id.localeCompare(b.id))
    for (const node of list) if (node.children?.length) sortTree(node.children)
  }
  sortTree(roots)
  return roots
}

export function fetchPermissionTree(): Promise<PermissionNode[]> {
  return get<WirePermissionView[]>('/authz/permissions/tree').then((views) =>
    normalizePermissionTree(views ?? []),
  )
}

export function createPermission(payload: PermissionUpsertPayload): Promise<PermissionNode> {
  const body: WirePermissionUpsertRequest = {
    parentId: payload.parentId,
    permType: payload.permType,
    code: payload.code,
    name: payload.name,
    url: payload.url,
    sortNo: payload.sortNo,
  }
  return post<WirePermissionView>('/authz/permissions', body).then(toPermissionNode)
}

export function updatePermission(
  permissionId: string,
  payload: Partial<PermissionUpsertPayload>,
): Promise<PermissionNode> {
  const body: WirePermissionUpsertRequest = {
    parentId: payload.parentId,
    permType: payload.permType ?? 'button',
    code: payload.code ?? '',
    name: payload.name ?? '',
    url: payload.url,
    sortNo: payload.sortNo,
  }
  return put<WirePermissionView>(`/authz/permissions/${permissionId}`, body).then(toPermissionNode)
}

/** 删除权限节点：服务端需级联检查子节点与 `sys_role_permission` 引用（前端只做入口与确认） */
export function deletePermission(permissionId: string, reason?: string): Promise<void> {
  return del<void>(`/authz/permissions/${permissionId}`, { data: reason ? { reason } : undefined })
}

// ---------------------------------------------------------------------------
// 角色
// ---------------------------------------------------------------------------
function toRoleItem(view: WireRoleView): RoleItem {
  return {
    roleId: sid(view.id),
    code: view.code,
    name: view.name,
    roleScope: toRoleScope(view.roleScope),
    dataScope: toDataScope(view.dataScope),
    remark: view.remark ?? null,
    /*
     * 内置受保护角色判定：
     * ① 服务端明确下发 builtIn 时以服务端为准；
     * ② 否则用 `sys_role.code` 白名单兜底（`doc/data-model.md` 3.1 列注释即权威角色码），
     *    保证「9 个内置角色不可删、code/role_scope 只读」不依赖后端字段先落地。
     */
    builtIn: view.builtIn ?? isBuiltInRoleCode(view.code),
    /*
     * 计数：**后端已下发真值**（`AuthzDtos.RoleView.permissionCount/userCount`，批量聚合、非 N+1）。
     * 因此这里只在**字段缺失**时回退 0（`??` 而非 `||`：下发的 0 就是真的 0，
     * 例如 company_admin 确实没有用户分配）。
     */
    permissionCount: toCount(view.permissionCount, 0),
    userCount: toCount(view.userCount, 0),
    categories: toMatterCategories(view.categories),
    createdAt: view.createdAt ?? undefined,
    updatedAt: view.updatedAt ?? undefined,
  }
}

/**
 * 角色列表：后端可能分页（`PageResult{records,total,page,size}`）也可能直接回数组
 * （角色总量很小，通常一屏放得下）。两种形状都在这里归一。
 */
function toRoleList(payload: WireRoleListResponse | null | undefined, fallbackSize: number): RoleListResult {
  if (Array.isArray(payload)) {
    const list = payload.map(toRoleItem)
    return { list, total: list.length, page: 1, pageSize: list.length || fallbackSize }
  }
  const page: WirePageResult<WireRoleView> | null | undefined = payload
  const list = (page?.records ?? []).map(toRoleItem)
  return {
    list,
    total: toCount(page?.total, list.length),
    page: toCount(page?.page, 1),
    pageSize: toCount(page?.size, fallbackSize),
  }
}

/** 角色列表：`code`/`name` 关键字与 `roleScope` 过滤交给服务端（数据域外的角色本就不下发） */
export function fetchRoles(
  params: { keyword?: string; roleScope?: RoleScope; page?: number; pageSize?: number } = {},
): Promise<RoleListResult> {
  const pageSize = params.pageSize ?? 50
  return get<WireRoleListResponse>('/authz/roles', {
    params: {
      keyword: params.keyword?.trim() || undefined,
      roleScope: params.roleScope,
      page: params.page ?? 1,
      // ⚠ 后端参数名与 identity 域一致，用 size（不是 pageSize）
      size: pageSize,
    },
  }).then((payload) => toRoleList(payload, pageSize))
}

export function createRole(payload: RoleUpsertPayload): Promise<RoleItem> {
  const body: WireRoleUpsertRequest = {
    code: payload.code,
    name: payload.name,
    roleScope: payload.roleScope,
    dataScope: payload.dataScope,
    remark: payload.remark,
    categories: payload.categories,
  }
  return post<WireRoleView>('/authz/roles', body).then(toRoleItem)
}

/**
 * 修改角色。内置角色的 `code` 与 `role_scope` 只读（任务书硬要求 3）：
 * 前端不提交这两个字段的变更（仍按当前值提交，避免后端把缺省当成清空），
 * **服务端才是裁决方**——越权请求一律 403。
 */
export function updateRole(roleId: string, payload: RoleUpsertPayload): Promise<RoleItem> {
  const body: WireRoleUpsertRequest = {
    code: payload.code,
    name: payload.name,
    roleScope: payload.roleScope,
    dataScope: payload.dataScope,
    remark: payload.remark,
    categories: payload.categories,
  }
  return put<WireRoleView>(`/authz/roles/${roleId}`, body).then(toRoleItem)
}

/** 删除角色：内置角色与仍有用户分配的角色由服务端拒绝（409 / 403），前端只做入口与确认 */
export function deleteRole(roleId: string, reason?: string): Promise<void> {
  return del<void>(`/authz/roles/${roleId}`, { data: reason ? { reason } : undefined })
}

// ---------------------------------------------------------------------------
// 角色 × 权限（逐级勾选）
// ---------------------------------------------------------------------------
function toRolePermissionGrant(roleId: string, view: WireRolePermissionView | null | undefined): RolePermissionGrant {
  const permissionIds = view?.permissionIds ?? view?.ids ?? view?.checkedKeys ?? view?.leafIds ?? []
  return {
    roleId: sid(view?.roleId) || roleId,
    permissionIds: permissionIds.map((id) => sid(id)),
    halfCheckedIds: (view?.halfCheckedIds ?? []).map((id) => sid(id)),
  }
}

export function fetchRolePermissions(roleId: string): Promise<RolePermissionGrant> {
  return get<WireRolePermissionView>(`/authz/roles/${roleId}/permissions`).then((view) =>
    toRolePermissionGrant(roleId, view),
  )
}

/**
 * 保存角色的权限树勾选（REQ-ADMIN-003 的「逐级勾选」写入口）。
 *
 * ⚠ **待对齐（后端本轮说明）**：后端尚未明确是「只存叶子」还是「存展开后的全量 id」。
 *   当前按任务书约定提交**全量已勾选 id 集合** `permissionIds`（= el-tree 的
 *   checkedKeys ∪ halfCheckedKeys，即含被联动勾上的父节点，保证父级菜单可达），
 *   同时把 `leafIds`（仅叶子）与 `halfCheckedIds`（半选父节点）作为只读副本一并发出，
 *   后端定稿后可删掉用不到的那两个字段。
 */
export function saveRolePermissions(
  roleId: string,
  payload: RolePermissionUpdatePayload,
): Promise<RolePermissionGrant | null> {
  const body: WireRolePermissionUpdateRequest = {
    permissionIds: payload.permissionIds,
    leafIds: payload.leafIds,
    halfCheckedIds: payload.halfCheckedIds,
    reason: payload.reason,
  }
  return put<WireRolePermissionView | null>(`/authz/roles/${roleId}/permissions`, body).then((view) =>
    view ? toRolePermissionGrant(roleId, view) : null,
  )
}

// ---------------------------------------------------------------------------
// 数据域与事项类别
// ---------------------------------------------------------------------------
/**
 * 数据域字典（五值）。
 * 后端不可用时的兜底：**不臆造**——只标注 `requiresCategory`，标签由 `utils/authz.ts` 的
 * `DATA_SCOPE_LABEL` 兜底（该表与 `doc/prd-0.1.md` 5.3 逐字一致）。
 */
export function fetchDataScopes(): Promise<DataScopeOption[]> {
  return get<WireDataScopeView[]>('/authz/data-scopes').then((views) =>
    (views ?? []).map((view) => ({
      code: toDataScope(view.code),
      label: view.label,
      description: view.description ?? undefined,
      requiresCategory: view.requiresCategory ?? view.code === 'group_category',
    })),
  )
}

export function updateRoleDataScope(roleId: string, dataScope: DataScope, reason?: string): Promise<void> {
  return put<void>(`/authz/roles/${roleId}/data-scope`, { dataScope, reason })
}

/** 事项类别字典（五值：经营/经济/行政/人力/投资） */
export function fetchCategories(): Promise<CategoryOption[]> {
  return get<WireCategoryView[]>('/authz/categories').then((views) =>
    (views ?? []).flatMap((view) => {
      const code = toMatterCategory(view.code)
      return code ? [{ code, label: view.label || view.name || code }] : []
    }),
  )
}

export function fetchRoleCategories(roleId: string): Promise<RoleCategoryGrant> {
  return get<WireRoleCategoryView>(`/authz/roles/${roleId}/categories`).then((view) => ({
    roleId: sid(view?.roleId) || roleId,
    categories: toMatterCategories(view?.categories),
  }))
}

/** 保存类别范围：`group_category` 数据域**必须**至少一个类别（硬要求 4，前端已前置拦截） */
export function updateRoleCategories(
  roleId: string,
  categories: MatterCategory[],
  reason?: string,
): Promise<RoleCategoryGrant> {
  const body: WireRoleCategoryUpdateRequest = { categories, reason }
  return put<WireRoleCategoryView>(`/authz/roles/${roleId}/categories`, body).then((view) => {
    // 服务端回显优先；未回显时按提交值回填（避免界面「保存成功却清空」的错觉）
    const echoed = toMatterCategories(view?.categories)
    return { roleId: sid(view?.roleId) || roleId, categories: echoed.length ? echoed : categories }
  })
}

// ---------------------------------------------------------------------------
// 角色 × 组织节点（授权粒度：组织节点 × 功能）
// ---------------------------------------------------------------------------
export function fetchRoleOrgNodes(roleId: string): Promise<RoleOrgNodeGrant> {
  return get<WireRoleOrgNodeView>(`/authz/roles/${roleId}/org-nodes`).then((view) => ({
    roleId: sid(view?.roleId) || roleId,
    orgNodeIds: (view?.orgNodeIds ?? view?.orgIds ?? []).map((id) => sid(id)),
    orgPaths: (view?.orgPaths ?? []).map((path) => String(path)),
  }))
}

export function updateRoleOrgNodes(
  roleId: string,
  orgNodeIds: string[],
  reason?: string,
): Promise<RoleOrgNodeGrant> {
  const body: WireRoleOrgNodeUpdateRequest = { orgNodeIds, reason }
  return put<WireRoleOrgNodeView>(`/authz/roles/${roleId}/org-nodes`, body).then((view) => ({
    roleId: sid(view?.roleId) || roleId,
    orgNodeIds: (view?.orgNodeIds ?? view?.orgIds ?? orgNodeIds).map((id) => sid(id)),
    orgPaths: (view?.orgPaths ?? []).map((path) => String(path)),
  }))
}

// ---------------------------------------------------------------------------
// 用户角色分配
// ---------------------------------------------------------------------------
function toRoleAssignment(view: WireRoleAssignmentView): RoleAssignment {
  return {
    assignmentId: sid(view.id),
    userId: sid(view.userId),
    roleId: sid(view.roleId),
    roleCode: view.roleCode,
    roleName: view.roleName,
    roleScope: view.roleScope ? toRoleScope(view.roleScope) : undefined,
    dataScope: view.dataScope ? toDataScope(view.dataScope) : undefined,
    scopeOrgId: sidOrNull(view.scopeOrgId),
    scopeOrgPath: view.scopeOrgPath ?? null,
    scopeOrgName: view.scopeOrgName ?? null,
    remark: view.remark ?? null,
    createdAt: view.createdAt ?? undefined,
  }
}

export function fetchUserRoles(userId: string): Promise<RoleAssignment[]> {
  return get<WireRoleAssignmentView[]>(`/authz/users/${userId}/roles`).then((views) =>
    (views ?? []).map(toRoleAssignment),
  )
}

/**
 * 分配角色。
 * ⚠ 重复分配（同一人 + 同一角色 + 相同 `scope_org_key`）由 `uk_sys_user_role` 判重，
 *   服务端回 **409**；`api/http.ts` 的拦截器会就地提示，页面再补一条「已有相同范围分配」的说明。
 */
export function assignUserRole(userId: string, payload: RoleAssignmentPayload): Promise<RoleAssignment> {
  const body: WireRoleAssignmentCreateRequest = {
    roleId: payload.roleId,
    scopeOrgId: payload.scopeOrgId ?? null,
    remark: payload.remark,
  }
  return post<WireRoleAssignmentView>(`/authz/users/${userId}/roles`, body).then(toRoleAssignment)
}

export function revokeUserRole(userId: string, assignmentId: string): Promise<void> {
  return del<void>(`/authz/users/${userId}/roles/${assignmentId}`)
}

// ---------------------------------------------------------------------------
// 有效权限与缓存
// ---------------------------------------------------------------------------
/**
 * 有效权限：`userId` 省略时取当前登录人。
 * `permissions` 后端可能回「权限码」也可能回「权限 id」——映射层只做归一，
 * **不猜语义**：非空且形如 `xxx.yyy` 的按权限码收，其余按原值保留（界面按码展示）。
 */
export function fetchEffectivePermissions(userId?: string): Promise<EffectivePermission> {
  return get<WireEffectivePermissionView>('/authz/effective-permissions', {
    params: { userId },
  }).then((view) => ({
    userId: sid(view?.userId),
    account: view?.account ?? undefined,
    userName: view?.userName ?? undefined,
    roleCodes: (view?.roleCodes ?? []).map((code) => String(code)),
    permissions: (view?.permissions ?? []).map((code) => String(code)),
    dataScopes: (view?.dataScopes ?? []).map(toDataScope),
    categories: toMatterCategories(view?.categories),
    isSuperAdmin: view?.isSuperAdmin ?? false,
    expiresAt: view?.expiresAt ?? undefined,
  }))
}

/** 失效权限缓存：改角色 / 权限 / 分配后调用，界面提示「下次请求生效」 */
export function invalidatePermissionCache(payload: CacheInvalidatePayload = {}): Promise<CacheInvalidateResult> {
  const body: WireCacheInvalidateRequest = {
    userIds: payload.userIds,
    roleIds: payload.roleIds,
    reason: payload.reason,
  }
  return post<WireCacheInvalidateResult | null>('/authz/cache/invalidate', body).then((view) => ({
    invalidatedUsers: toCount(view?.invalidatedUsers, 0),
    invalidatedRoles: toCount(view?.invalidatedRoles, 0),
    note: view?.note ?? undefined,
  }))
}

// ---------------------------------------------------------------------------
// 变更日志（REQ-LOG-004 / AC-59，只读）
// ---------------------------------------------------------------------------
/**
 * 前后值归一为可读文本。
 * 后端可能给 JSON 字符串（`beforeJson: "{\"permissionIds\":[1,2]}"`）或结构化对象
 * （`before: { permissionIds: [1,2] }`）；这里统一尝试解析成 `键: 值` 的短文本，
 * 解析失败则原样展示——**绝不吞掉原始值**（审计场景丢信息比难看更严重）。
 */
function toChangeText(raw: unknown, depth = 0): string {
  if (raw === null || raw === undefined) return ''
  if (typeof raw === 'string') {
    const text = raw.trim()
    if (!text) return ''
    if ((text.startsWith('{') || text.startsWith('[')) && depth < 4) {
      try {
        return toChangeText(JSON.parse(text) as unknown, depth + 1)
      } catch {
        return text
      }
    }
    return text
  }
  if (Array.isArray(raw)) {
    return raw
      .map((item) => toChangeText(item, depth + 1))
      .filter((item) => item !== '')
      .join('、')
  }
  if (typeof raw === 'object') {
    return Object.entries(raw as Record<string, unknown>)
      .map(([key, value]) => {
        const text = toChangeText(value, depth + 1)
        return text ? `${key}: ${text}` : ''
      })
      .filter((item) => item !== '')
      .join('；')
  }
  return String(raw)
}

function toChangeLog(view: WireChangeLogView): AuthzChangeLog {
  return {
    logId: sid(view.id),
    action: view.action,
    actionLabel: view.actionLabel ?? '',
    targetType: toChangeTargetType(view.targetType),
    targetTypeLabel: view.targetTypeLabel ?? '',
    targetId: sid(view.targetId),
    targetLabel: view.targetLabel ?? '',
    operatorId: sid(view.operatorId),
    operatorName: view.operatorName ?? '',
    account: view.account ?? undefined,
    reason: view.reason ?? undefined,
    before: toChangeText(view.beforeJson ?? view.before),
    after: toChangeText(view.afterJson ?? view.after),
    added: (view.added ?? []).map((item) => String(item)),
    removed: (view.removed ?? []).map((item) => String(item)),
    createdAt: view.createdAt ?? '',
    traceId: view.traceId ?? undefined,
  }
}

/** 变更日志列表（分页 + 服务端过滤；只读页面） */
export function fetchChangeLogs(query: AuthzChangeLogQuery): Promise<AuthzChangeLogPage> {
  const params: WireChangeLogQueryParams = {
    keyword: query.keyword?.trim() || undefined,
    action: query.action,
    targetType: query.targetType,
    targetId: query.targetId,
    dateFrom: query.dateFrom,
    dateTo: query.dateTo,
    page: query.page,
    size: query.pageSize,
  }
  return get<WirePageResult<WireChangeLogView>>('/authz/change-logs', { params }).then((page) => {
    const list = (page?.records ?? []).map(toChangeLog)
    return {
      list,
      total: toCount(page?.total, list.length),
      page: toCount(page?.page, query.page),
      pageSize: toCount(page?.size, query.pageSize),
    }
  })
}
