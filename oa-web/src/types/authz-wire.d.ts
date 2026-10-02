/**
 * oa-web · 后端 authz 域 DTO 镜像（wire 层）
 * ----------------------------------------------------------------------------
 * 契约来源：
 *   · `doc/data-model.md` 3.1 `sys_role`（`code` 列注释 = 9 个权威角色码）、
 *     3.2 `sys_user_role`（含 `scope_org_key` 生成列的唯一键口径）、
 *     3.3 `sys_role_category`（五值类别，仅 `data_scope=group_category` 需要）、
 *     3.4 `sys_permission`（`perm_type` menu/button/api）、3.5 `sys_role_permission`
 *   · `doc/prd-0.1.md` 5.2（权限模型 / 逐级分配 / 分公司管理员不可再授权）、
 *     6.10 REQ-ADMIN-003（权限配置）、REQ-ADMIN-006（管理员边界）、REQ-LOG-004（权限变更留痕）
 *   · `doc/import-spec.md` §2.3（9 个角色码白名单）、§3.6 / §4.6（角色分配口径）
 *   · 已定稿路径：`/api/v1/authz/**`（见 `api/authz.ts` 文件头逐条列表）
 *
 * ⚠ 路径已冻结，**字段名以 oa-server 实现为准**。本文件是那些字段的忠实镜像：
 *   `api/authz.ts` 把 wire 映射成领域模型 `types/authz.d.ts`，页面只依赖领域模型，
 *   后端改字段名/加别名时**只改映射层**。
 *
 * ⚠⚠ 分页与 id 的序列化口径（全项目统一，见 `types/identity-wire.d.ts` 的同名说明）：
 *   `com.oa.common.config.JacksonConfig` 对 `Long`/`long`/`BigInteger` 注册了
 *   `ToStringSerializer`，因此 **id 与 `PageResult.total/page/size/pages` 在 JSON 里是字符串**
 *   （`"1755000000000000001"`、`"20"`）。映射层统一 `String(...)` 成 id、`Number(...)` 成数值；
 *   只有 `Integer`/`int`/`long` 之外的基本类型（如 `sortNo`、`permissionCount`）才是 JSON number。
 *
 * ⚠ 本文件里出现「别名」字段（如 `beforeJson` / `before`）不是投机：后端并行实现尚未定稿，
 *   别名让同一个映射层能同时吃下两种命名，**避免后端一改字段名就整页白屏**。
 *   后端定稿后应删掉未使用的别名并在此登记「待对齐」清单（见文件末）。
 */
import type { WireId, WireNumber, WirePageResult } from './identity-wire'

export type { WireId, WireNumber, WirePageResult }

// ---------------------------------------------------------------------------
// 权限树（sys_permission）
// ---------------------------------------------------------------------------
/**
 * 权限节点：`GET /api/v1/authz/permissions/tree`（树）与 `POST/PUT` 的返回。
 * `permType` 取 `sys_permission.perm_type`：`menu` 菜单 / `button` 按钮 / `api` 接口。
 */
export interface WirePermissionView {
  id: WireId
  parentId?: WireId | null
  /** menu | button | api */
  permType: string
  permTypeLabel?: string | null
  /** 唯一权限码（**冒号风格** `域:子域:动作`），如 `admin:role:grant`（`uk_sys_permission_code`） */
  code: string
  name: string
  /** 前端路由或接口路径 */
  url?: string | null
  /** 同层排序（`int`，JSON number） */
  sortNo?: number | null
  /** 树接口非空；平铺接口为 null */
  children?: WirePermissionView[] | null
}

/** `POST /api/v1/authz/permissions` / `PUT /{id}` 请求体 */
export interface WirePermissionUpsertRequest {
  /** PUT 时由路径给出，body 里可省略 */
  id?: WireId | null
  parentId?: WireId | null
  permType: string
  code: string
  name: string
  url?: string | null
  sortNo?: number | null
}

/**
 * `GET /api/v1/authz/roles/{id}/permissions` 的出参。
 *
 * 后端可能是「已勾选 id 集合」，也可能是「全量 id + 叶子 id」两种口径之一，
 * 因此这里把三种都写成可选，由映射层按「能找到哪个用哪个」的顺序归一：
 * `permissionIds` / `ids` / `checkedKeys`（首选中）→ `leafIds`（仅叶子）→ 由树推导。
 */
export interface WireRolePermissionView {
  roleId?: WireId | null
  /** 已勾选的权限 id 集合（首选口径） */
  permissionIds?: WireId[] | null
  /** 别名：部分实现直接回 ids */
  ids?: WireId[] | null
  /** 别名：与前端 el-tree 的 checkedKeys 同名 */
  checkedKeys?: WireId[] | null
  /**
   * 仅叶子口径：`check-strictly=false` 时 el-tree 的「全选父节点」会带上父节点，
   * 若后端只存叶子，映射层必须用树把父节点补全（否则回显会缺半选态）。
   */
  leafIds?: WireId[] | null
  /** 半选（部分勾选）的父节点 id，仅用于回显校验，一般不参与提交 */
  halfCheckedIds?: WireId[] | null
  updatedAt?: string | null
}

/**
 * `PUT /api/v1/authz/roles/{id}/permissions` 请求体。
 *
 * ⚠ 待对齐：后端说明的是「存叶子」还是「存展开全量」。当前实现按**全量已勾选 id 集合**
 *   提交（`permissionIds`），并同时带上 `leafIds` 与 `halfCheckedIds` 两个只读副本，
 *   便于后端按自己的口径取用；定稿后应删除未使用的字段。
 */
export interface WireRolePermissionUpdateRequest {
  /** 全量已勾选 id 集合（当前提交口径） */
  permissionIds: WireId[]
  /** 仅叶子 id（与 permissionIds 一并提交，供「存叶子」口径的后端取用） */
  leafIds?: WireId[]
  /** 半选父节点 id（只读副本，供后端校验与留痕） */
  halfCheckedIds?: WireId[]
  /** 留痕原因（REQ-LOG-004 要求记录变更前后值，原因可选） */
  reason?: string
}

// ---------------------------------------------------------------------------
// 角色（sys_role）
// ---------------------------------------------------------------------------
/** `GET /api/v1/authz/roles` 单行 / `POST`、`PUT` 的返回 */
export interface WireRoleView {
  id: WireId
  /** `sys_role.code`（9 个内置码 + 自定义码），唯一键 `uk_sys_role_code` */
  code: string
  name: string
  /** `group` 集团级 | `company` 公司级 */
  roleScope: string
  roleScopeLabel?: string | null
  /** `self`/`dept`/`company`/`group_all`/`group_category` */
  dataScope: string
  dataScopeLabel?: string | null
  remark?: string | null
  /**
   * 是否内置角色（9 个权威角色码）——后端可能不下发，映射层用 `sys_role.code`
   * 白名单兜底判定（任务书硬要求 3：内置角色禁止删除、禁止改 code/role_scope）。
   */
  builtIn?: boolean | null
  /** 已授权限数（`int`） */
  permissionCount?: number | null
  /** 已分配用户数（`int` 或 `long`，映射层统一 Number） */
  userCount?: WireNumber | null
  /** `data_scope=group_category` 时的类别五值 */
  categories?: string[] | null
  categoryLabels?: string[] | null
  createdAt?: string | null
  updatedAt?: string | null
}

/**
 * 角色列表出参：既支持**分页**（`PageResult{records,total,page,size}`），
 * 也支持**全量数组**（角色总量很小，后端可能不分页）。映射层两种都吃。
 */
export type WireRoleListResponse = WirePageResult<WireRoleView> | WireRoleView[]

/** `POST /api/v1/authz/roles` / `PUT /api/v1/authz/roles/{id}` 请求体 */
export interface WireRoleUpsertRequest {
  code: string
  name: string
  roleScope: string
  dataScope: string
  remark?: string | null
  /** `group_category` 时随角色一并提交的类别（也可走 `PUT /{id}/categories`） */
  categories?: string[] | null
}

// ---------------------------------------------------------------------------
// 数据域（sys_role.data_scope 字典）与类别（sys_role_category）
// ---------------------------------------------------------------------------
/** `GET /api/v1/authz/data-scopes`：五个数据域 + 中文标签 + 是否必须配类别 */
export interface WireDataScopeView {
  code: string
  label: string
  description?: string | null
  /** `group_category` 为 true：必须配至少一个事项类别（任务书硬要求 4） */
  requiresCategory?: boolean | null
}

/** `PUT /api/v1/authz/roles/{id}/data-scope` 请求体 */
export interface WireRoleDataScopeUpdateRequest {
  dataScope: string
  reason?: string
}

/** `GET /api/v1/authz/categories`：事项类别五值（business/economy/admin/hr/invest） */
export interface WireCategoryView {
  code: string
  label: string
  /** 别名：部分实现回 name */
  name?: string | null
  sortNo?: number | null
}

/** `GET/PUT /api/v1/authz/roles/{id}/categories` */
export interface WireRoleCategoryView {
  roleId?: WireId | null
  categories?: string[] | null
  categoryLabels?: string[] | null
}

export interface WireRoleCategoryUpdateRequest {
  categories: string[]
  reason?: string
}

// ---------------------------------------------------------------------------
// 角色 × 组织节点授权（组织节点×功能 的授权粒度）
// ---------------------------------------------------------------------------
/** `GET/PUT /api/v1/authz/roles/{id}/org-nodes` */
export interface WireRoleOrgNodeView {
  roleId?: WireId | null
  /** 已授权的组织节点 id 集合 */
  orgNodeIds?: WireId[] | null
  /** 别名：部分实现回 orgIds */
  orgIds?: WireId[] | null
  /** 节点路径，供界面直接展示（可选） */
  orgPaths?: string[] | null
}

export interface WireRoleOrgNodeUpdateRequest {
  orgNodeIds: WireId[]
  reason?: string
}

// ---------------------------------------------------------------------------
// 用户角色分配（sys_user_role）
// ---------------------------------------------------------------------------
/**
 * 一条角色分配。唯一键口径：`uk_sys_user_role (user_id, role_id, scope_org_key)`，
 * 其中 `scope_org_key = IFNULL(scope_org_id, 0)` —— 因此「同一人 + 同一角色 + 空范围」
 * 重复提交会被数据库判重，服务端应回 **409**（前端提示「该角色已分配（含相同组织范围）」）。
 */
export interface WireRoleAssignmentView {
  id: WireId
  userId: WireId
  roleId: WireId
  roleCode: string
  roleName: string
  roleScope?: string | null
  /** 角色默认数据域（便于界面提示「留空 = 按角色默认」） */
  dataScope?: string | null
  /** 该角色生效的组织范围；为空 = 按角色默认（import-spec §3.6） */
  scopeOrgId?: WireId | null
  scopeOrgPath?: string | null
  scopeOrgName?: string | null
  remark?: string | null
  createdAt?: string | null
}

/** `POST /api/v1/authz/users/{id}/roles` 请求体 */
export interface WireRoleAssignmentCreateRequest {
  roleId: WireId
  scopeOrgId?: WireId | null
  remark?: string | null
}

// ---------------------------------------------------------------------------
// 有效权限（/authz/effective-permissions）
// ---------------------------------------------------------------------------
/** `GET /api/v1/authz/effective-permissions`：某用户（或本人）的最终权限口径 */
export interface WireEffectivePermissionView {
  userId: WireId
  account?: string | null
  userName?: string | null
  roleCodes?: string[] | null
  /** 权限码集合（`sys_role_permission` ⋈ `sys_permission.code` 去重） */
  permissions?: string[] | null
  /** 权限 id 集合（部分实现回 id 而非 code） */
  permissionIds?: WireId[] | null
  dataScopes?: string[] | null
  categories?: string[] | null
  /** 服务端兜底能力（REQ-ADMIN-006） */
  isSuperAdmin?: boolean | null
  /** 缓存过期时间；用于界面提示「下次请求生效」 */
  expiresAt?: string | null
}

/** `POST /api/v1/authz/cache/invalidate` 请求体 / 返回 */
export interface WireCacheInvalidateRequest {
  /** 留空 = 全量失效 */
  userIds?: WireId[] | null
  roleIds?: WireId[] | null
  reason?: string
}

export interface WireCacheInvalidateResult {
  invalidatedUsers?: number | null
  invalidatedRoles?: number | null
  note?: string | null
}

// ---------------------------------------------------------------------------
// 权限变更日志（REQ-LOG-004 / AC-59）
// ---------------------------------------------------------------------------
/**
 * 变更日志一行。REQ-LOG-004 要求含**变更前后值**：
 * 前后值后端可能给 JSON 字符串（`beforeJson`/`afterJson`），也可能给结构化对象
 * （`before`/`after`，如 `{ permissionIds: [...] }`）——映射层两种都吃，
 * 统一归一成 `{ text, summary }` 供界面展示。
 */
export interface WireChangeLogView {
  id: WireId
  /**
   * 动作 code，形如 `role:permission:update` / `role:create` / `user:role:assign`
   * （全项目统一冒号风格；后端若下发点号动作码，界面按 `actionLabel` → 原值兜底展示）。
   */
  action: string
  actionLabel?: string | null
  /** 对象类型：`role` | `role_permission` | `user_role` | `data_scope` | `category` | `org_node` */
  targetType?: string | null
  targetTypeLabel?: string | null
  targetId?: WireId | null
  targetLabel?: string | null
  operatorId?: WireId | null
  operatorName?: string | null
  account?: string | null
  reason?: string | null
  /** 变更前值（JSON 字符串口径） */
  beforeJson?: string | null
  /** 变更后值（JSON 字符串口径） */
  afterJson?: string | null
  /** 变更前值（结构化口径） */
  before?: Record<string, unknown> | null
  /** 变更后值（结构化口径） */
  after?: Record<string, unknown> | null
  /** 新增的权限 id/码集合（服务端可直接给出差异，前端优先展示） */
  added?: string[] | null
  removed?: string[] | null
  createdAt?: string | null
  traceId?: string | null
}

/** `GET /api/v1/authz/change-logs` 查询参数（服务端过滤） */
export interface WireChangeLogQueryParams {
  keyword?: string
  action?: string
  targetType?: string
  targetId?: WireId
  operatorId?: WireId
  dateFrom?: string
  dateTo?: string
  page?: number
  /** 后端参数名通常是 size（与 identity 域一致） */
  size?: number
}

// ---------------------------------------------------------------------------
// 待对齐清单（后端本轮补齐后逐条删除）
// ----------------------------------------------------------------------------
//  1. `WireRolePermissionUpdateRequest`：提交全量 id 还是仅叶子 id（当前两者都发）。
//  2. `WireRoleView.builtIn` / `permissionCount` / `userCount` 是否下发（当前用 code 白名单兜底内置判定）。
//  3. `WireRoleListResponse`：角色列表是否分页（当前分页与全量数组都支持）。
//  4. `WireChangeLogView`：前后值是 `beforeJson`/`afterJson` 字符串还是 `before`/`after` 对象。
//  5. `WireEffectivePermissionView.permissions` 回的是权限码还是 id。
//  6. 角色分配重复提交的 409 错误码（`AUTHZ_ROLE_DUPLICATE` 之类的稳定码）。
//  7. `org-nodes` 是纯 id 集合还是含「可访问功能×节点」二维授权。
