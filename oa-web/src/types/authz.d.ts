/**
 * oa-web · 权限域 DTO（角色 / 权限树 / 授权 / 数据域 / 类别 / 组织节点 / 变更日志）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/data-model.md` 3.1–3.5（`sys_role` / `sys_user_role` / `sys_role_category` /
 *     `sys_permission` / `sys_role_permission` 的列与约束）
 *   · `doc/prd-0.1.md` 5.2（RBAC + 数据域双层；逐级分配；**分公司流程管理员不可再向下分配权限**）、
 *     6.10 REQ-ADMIN-003（为角色勾选权限树节点与数据域）、REQ-ADMIN-006（管理员边界）、
 *     6.9 REQ-LOG-004（权限变更日志含变更前后值）、AC-59 / AC-61
 *   · `doc/import-spec.md` §2.3（9 个角色码白名单）、§3.6（`scope_org_path` 口径：
 *     留空 = 按角色默认数据域）、§4.6（E-ROLE-001 ~ E-ROLE-005）
 *   · 已冻结路径 `/api/v1/authz/**`（见 `api/authz.ts` 文件头）
 *
 * 命名约定与 `types/identity.d.ts` 一致：本文件只描述统一响应体里的 `data` 部分，
 * `api/http.ts` 负责解包；`api/authz.ts` 负责 wire → 领域模型映射（含 Long → string）。
 */
import type { DataScope, PageResult } from './api'
import type { BusinessLine } from './identity'

/** 分页结果复用网关统一结构（领域模型里 total/page/pageSize 恒为 number） */
export type { PageResult }

// ---------------------------------------------------------------------------
// 枚举
// ---------------------------------------------------------------------------
/** `sys_permission.perm_type`：菜单 / 按钮 / 接口 */
export type PermissionType = 'menu' | 'button' | 'api'

/** `sys_role.role_scope`：集团级 / 公司级 */
export type RoleScope = 'group' | 'company'

/**
 * 事项类别五值（`sys_role_category.category`）：经营 / 经济 / 行政 / 人力 / 投资。
 * ⚠ 与 `types/api.d.ts` 的 `CategoryCode`（资金/合同/印鉴等**归口类别**）**不是同一组值**：
 *   本组值直接复用身份域 `BusinessLine`（import-spec §3.4 T-06：业务线复用事项类别五值）。
 */
export type MatterCategory = BusinessLine

// ⚠ 本文件是**纯类型声明**（`.d.ts`）：不放任何运行期常量。
//   标签映射、受保护角色码、`group_category` 常量等一律放 `utils/authz.ts`。

// ---------------------------------------------------------------------------
// 权限树（sys_permission）
// ---------------------------------------------------------------------------
/** 权限节点：树形，逐级勾选的单位（`check-strictly=false` 时父子联动） */
export interface PermissionNode {
  id: string
  /** 根节点为 null */
  parentId: string | null
  code: string
  name: string
  permType: PermissionType
  /** 服务端下发的中文类型标签（缺省时用 `PERMISSION_TYPE_LABEL` 兜底） */
  permTypeLabel?: string
  url?: string | null
  sortNo: number
  /** 叶子节点无 children */
  children?: PermissionNode[]
}

/** `POST /api/v1/authz/permissions` / `PUT /{id}` */
export interface PermissionUpsertPayload {
  parentId: string | null
  permType: PermissionType
  code: string
  name: string
  url?: string | null
  sortNo?: number
}

// ---------------------------------------------------------------------------
// 角色（sys_role）
// ---------------------------------------------------------------------------
export interface RoleItem {
  roleId: string
  /** 9 个内置码之一或自定义码（小写蛇形，import-spec §3.6 的 `^[a-z][a-z0-9_]{1,31}$`） */
  code: string
  name: string
  roleScope: RoleScope
  dataScope: DataScope
  remark?: string | null
  /**
   * 内置受保护角色（`admin`/`company_admin`/…/`chairman` 九个）：UI 禁止删除，
   * `code` 与 `role_scope` 只读（任务书硬要求 3）。
   */
  builtIn: boolean
  /** 已授权限数（列表列） */
  permissionCount: number
  /** 已分配用户数（列表列） */
  userCount: number
  /** `data_scope=group_category` 时的类别五值 */
  categories: MatterCategory[]
  createdAt?: string
  updatedAt?: string
}

/** 角色列表：分页口径（后端不分页时 total = 行数、page = 1） */
export type RoleListResult = PageResult<RoleItem>

/** `POST /api/v1/authz/roles` / `PUT /{id}` */
export interface RoleUpsertPayload {
  code: string
  name: string
  roleScope: RoleScope
  dataScope: DataScope
  remark?: string | null
  /**
   * 类别五值。`data_scope=group_category` 时**必须非空**（硬要求 4）：
   * 前端保存前拦截，并在同一个提交里带上，避免「改了数据域但类别还没配」的中间态。
   */
  categories?: MatterCategory[]
}

// ---------------------------------------------------------------------------
// 数据域与类别
// ---------------------------------------------------------------------------
export interface DataScopeOption {
  code: DataScope
  label: string
  description?: string
  /** true = 选中该数据域时必须配至少一个事项类别（`group_category`） */
  requiresCategory: boolean
}

export interface CategoryOption {
  code: MatterCategory
  label: string
}

/** `GET/PUT /api/v1/authz/roles/{id}/categories` */
export interface RoleCategoryGrant {
  roleId: string
  categories: MatterCategory[]
}

// ---------------------------------------------------------------------------
// 角色 × 权限 / 角色 × 组织节点
// ---------------------------------------------------------------------------
/**
 * 角色的权限授权（`GET/PUT /api/v1/authz/roles/{id}/permissions`）。
 *
 * `permissionIds` = 全量已勾选 id 集合（含被联动勾上的父节点）；
 * `halfCheckedIds` = 半选父节点 id（**只用于回显半选态**，不参与「是否已授权」的判定）。
 */
export interface RolePermissionGrant {
  roleId: string
  permissionIds: string[]
  halfCheckedIds: string[]
}

/** `PUT /api/v1/authz/roles/{id}/permissions` 的提交体 */
export interface RolePermissionUpdatePayload {
  /** 全量已勾选 id 集合（当前提交口径，见 `api/authz.ts` 的待对齐注释） */
  permissionIds: string[]
  /** 仅叶子 id（供「存叶子」口径的后端取用） */
  leafIds: string[]
  /** 半选父节点 id（只读副本） */
  halfCheckedIds: string[]
  reason?: string
}

/** `GET/PUT /api/v1/authz/roles/{id}/org-nodes`：角色可访问的组织节点范围 */
export interface RoleOrgNodeGrant {
  roleId: string
  orgNodeIds: string[]
  /** 服务端可回传路径，界面直接展示（缺省时用组织树本地补全） */
  orgPaths?: string[]
}

// ---------------------------------------------------------------------------
// 用户角色分配（sys_user_role）
// ---------------------------------------------------------------------------
/**
 * 一条角色分配。唯一键 `(user_id, role_id, scope_org_key)`：
 * 同一人 + 同一角色 + 相同组织范围重复提交 → 服务端 **409**。
 */
export interface RoleAssignment {
  assignmentId: string
  userId: string
  roleId: string
  roleCode: string
  roleName: string
  roleScope?: RoleScope
  /** 角色默认数据域（界面提示「留空 = 按角色默认」） */
  dataScope?: DataScope
  /** 生效组织范围；空 = 按角色默认（import-spec §3.6） */
  scopeOrgId: string | null
  scopeOrgPath?: string | null
  scopeOrgName?: string | null
  remark?: string | null
  createdAt?: string
}

/** `POST /api/v1/authz/users/{id}/roles` */
export interface RoleAssignmentPayload {
  roleId: string
  /** 生效组织范围（`scope_org_path` 选择器）；留空 = 按角色默认数据域 */
  scopeOrgId?: string | null
  remark?: string
}

// ---------------------------------------------------------------------------
// 有效权限（/authz/effective-permissions 与 /auth/me 的新增字段）
// ---------------------------------------------------------------------------
/**
 * 某用户的最终权限口径。
 * `GET /api/v1/auth/me` 本轮新增 `permissions: string[]` 与 `isSuperAdmin: boolean`，
 * 与本结构同源——`utils/admin.ts` 的判据即建立在 `permissions` 上（角色码仅作兜底）。
 */
export interface EffectivePermission {
  userId: string
  account?: string
  userName?: string
  /** 命中的角色码（兜底判据与界面展示都要用） */
  roleCodes: string[]
  /** 权限码集合（去重） */
  permissions: string[]
  dataScopes: DataScope[]
  categories: MatterCategory[]
  isSuperAdmin: boolean
  /** 缓存过期时间：界面据此提示「权限缓存已失效，下次请求生效」 */
  expiresAt?: string
}

/** `POST /api/v1/authz/cache/invalidate` */
export interface CacheInvalidatePayload {
  /** 留空 = 全量失效 */
  userIds?: string[]
  roleIds?: string[]
  reason?: string
}

export interface CacheInvalidateResult {
  invalidatedUsers: number
  invalidatedRoles: number
  note?: string
}

// ---------------------------------------------------------------------------
// 权限变更日志（REQ-LOG-004 / AC-59）
// ---------------------------------------------------------------------------
export type ChangeTargetType =
  | 'role'
  | 'role_permission'
  | 'user_role'
  | 'data_scope'
  | 'category'
  | 'org_node'

export interface AuthzChangeLog {
  logId: string
  action: string
  actionLabel: string
  targetType: ChangeTargetType
  targetTypeLabel: string
  targetId: string
  targetLabel: string
  operatorId: string
  operatorName: string
  account?: string
  reason?: string
  /** 变更前值（已归一为可读文本；结构化时是 JSON 文本） */
  before: string
  /** 变更后值 */
  after: string
  /** 服务端直接给出的差异（权限 id / 码），界面优先展示 */
  added: string[]
  removed: string[]
  createdAt: string
  traceId?: string
}

export interface AuthzChangeLogQuery {
  keyword?: string
  action?: string
  targetType?: ChangeTargetType
  targetId?: string
  dateFrom?: string
  dateTo?: string
  page: number
  pageSize: number
}

export type AuthzChangeLogPage = PageResult<AuthzChangeLog>

// ---------------------------------------------------------------------------
// 保存前的差异（「新增 / 移除 N 项」提示用）
// ---------------------------------------------------------------------------
export interface PermissionDiff {
  addedIds: string[]
  removedIds: string[]
  added: PermissionNode[]
  removed: PermissionNode[]
  /** 本次是否真的没有变化（按钮据此禁用保存） */
  empty: boolean
}
