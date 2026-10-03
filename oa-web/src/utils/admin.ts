/**
 * oa-web · 管理后台权限口径（身份域 + 权限域）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.2（权限模型：逐级分配、**分公司流程管理员不可再向下分配权限**）、
 *     5.3（字段级限制：手机号仅本人与系统管理员可见）、
 *     6.10 REQ-ADMIN-003（权限配置）/ REQ-ADMIN-006（系统管理员兜底权限边界，所有操作留痕）、
 *     6.9 REQ-LOG-004（权限变更日志，AC-59）
 *   · `doc/import-spec.md` §9.2（**主数据导出仅系统管理员**，T-11 定稿）、
 *     §7.4（影响程度=高 且未确认 → 阻断，确认动作写 sys_log）、
 *     §2.3（9 个权威角色码）
 *   · `doc/prd-0.1.md` 5.5「强制继续」唯一例外（2026-10-02 裁定：仅系统管理员 + 必填原因 + 双留痕）
 *   · `doc/templates.md` §3.3 / §4.1（模板状态机与「改配置必须开新版本」，2a.2 流程设计器）
 *   · `DESIGN.md` Agent Usage Rules 第 6 条「权限不可见优于不可用」
 *
 * 口径：入口与按钮**不渲染**（而非置灰）；服务端（`ForceReasonPolicy` / 数据域拦截器 /
 * `@PreAuthorize`）仍是最终裁决方，本文件只决定「可不可见」，不承担鉴权。
 *
 * 判据顺序（1.4 起）：`isSuperAdmin` → `permissions` 命中权限码（精确或前缀）→
 * `roleCodes` 命中角色码兜底。角色码兜底仅在 `permissions` 为空（旧后端 / 未初始化权限数据）时生效，
 * 且**不适用于禁止性规则**（如「分公司管理员不可再授权」）。
 *
 * 权限码风格：**一律冒号**（`域:子域:动作`），权威源是 `oa-deploy/sql/04-permissions.sql`
 * 的 94 项种子与 `doc/data-model.md` 3.4 的示例 `flow:task:approve`；本文件里不得出现点号权限码。
 * 「强制继续」不是权限码——后端 `ForceReasonPolicy` 判的是角色码 `admin`（见 `canForceChange`）。
 */
import type { useUserStore } from '@/stores/user'

type UserStore = ReturnType<typeof useUserStore>

/**
 * 系统管理员角色码（`doc/data-model.md` 3.1 `sys_role.code` 的权威取值）。
 *
 * <p>为什么还需要它：`GET /api/v1/auth/me` 自 1.4 起同时下发 `roleCodes`、`permissions`
 * 与 `isSuperAdmin`。判据顺序固定为 **① `isSuperAdmin`（服务端兜底能力）→ ② `permissions`
 * 命中权限码（真实授权，1.4 之后的主判据）→ ③ `roleCodes` 命中角色码（兜底）**。
 *
 * <p>保留 ③ 的理由：旧版本后端 / 未初始化 `sys_role_permission` 的环境下 `permissions`
 * 会是空数组，只看权限码会让管理入口**恒为 false**（「功能好了但按钮永远不出现」）。
 * 但兜底有明确边界：**「分公司管理员不可再授权」这类禁止性规则不参与兜底**
 * （见 `canGrantRolePermission`），兜底只能用来「放宽到与角色同口径」，不能用来越过红线。
 */
export const SYSTEM_ADMIN_ROLE = 'admin'

/** 分公司流程管理员（PRD 5.2：可维护本公司组织与人员、本公司流程模板，**不可再向下分配权限**） */
export const COMPANY_ADMIN_ROLE = 'company_admin'

/**
 * 身份域权限码（`sys_permission.code`，**冒号风格**）。
 *
 * <p>权威源：`oa-deploy/sql/04-permissions.sql`（94 项权限种子，全部 `域:子域:动作` 冒号风格）
 *   与 `doc/data-model.md` 3.4 `sys_permission.code` 的示例 `flow:task:approve`。
 *   **不要写点号**：点号码在这套种子里一个都不存在，写错会让入口恒不可见。
 *
 * <p>组织与人员是**前缀族**（`admin:org:*` / `admin:user:*`），因此判据用
 *   `hasAnyPermission`（前缀匹配）而不是精确匹配：后端新增 `admin:org:xxx` 细项时前端不必跟着改。
 */
export const IDENTITY_PERMISSION = {
  /** 组织架构（前缀族）：`admin:org:tree` / `admin:org:leader` / `admin:org:position` */
  orgPrefix: 'admin:org:',
  /** 人员管理（前缀族）：`admin:user:profile` / `admin:user:handover` / `admin:user:import` */
  userPrefix: 'admin:user:',
  /** 主数据导出：**精确**码，仅系统管理员（import-spec §9.2 T-11） */
  userExport: 'admin:user:export',
  /** 人员批量导入（高危写操作，import-spec §2；与导出分开判定） */
  userImport: 'admin:user:import',
} as const

/**
 * 权限域（authz）权限码（1.4 角色与权限树），逐条对应 `04-permissions.sql` 里
 * `admin:role*` / `admin:authz:*` / `admin:audit:permission` 这些权限项。
 */
export const AUTHZ_PERMISSION = {
  /** 角色列表与角色维护（新增/编辑/删除） */
  roleList: 'admin:role:list',
  /** 权限树逐级勾选：**分公司管理员不可再授权**，因此是独立权限项 */
  roleGrant: 'admin:role:grant',
  /** 数据域与事项类别配置（`group_category` 必须配类别） */
  scopeConfig: 'admin:authz:scope',
  /** 角色分配（把人挂到角色上，写 `sys_user_role`） */
  assign: 'admin:authz:assign',
  /** 权限变更日志（REQ-LOG-004 / AC-59） */
  changeLog: 'admin:audit:permission',
} as const

/**
 * **前缀匹配**判据：`permissions` 中任一权限码以给定前缀开头即命中。
 *
 * <p>为什么需要它：组织/人员管理在种子里被拆成多个细项（`admin:org:tree`、
 *   `admin:org:leader`、`admin:user:import` …），入口只要「该域内任一权限」即可见；
 *   逐条枚举会在后端新增细项时漏掉。
 */
export function hasAnyPermission(store: UserStore, prefixes: readonly string[]): boolean {
  return store.permissions.some((code) => prefixes.some((prefix) => code.startsWith(prefix)))
}

/**
 * 通用判据：**权限码优先，角色码兜底**。
 *
 * <p>返回 true 的两种情形：
 *   ① `permissions` 命中任一权限码 —— 真实授权（1.4 之后的主路径）；
 *   ② `permissions` 为空（旧后端 / 未初始化权限数据）**且** `roleCodes` 命中兜底角色码
 *      —— 只在「真实权限一个都没取回」时才启用兜底，避免抛出的权限码被无声覆盖。
 *
 * <p>注意：`permissions` 非空时**不再看角色码**。若管理员在后台取消了某角色的权限，
 * 前端必须跟着收回入口，否则会出现「后台已收权、界面还可点」的错位。
 */
export function hasPermissionOrRole(
  store: UserStore,
  permissionCodes: readonly string[],
  fallbackRoleCodes: readonly string[] = [],
): boolean {
  if (store.isSuperAdmin) return true
  // ① 真实权限码
  if (permissionCodes.some((code) => store.hasPermission(code))) return true
  // ② 角色码兜底：仅在没有任何真实权限数据时生效
  if (store.permissions.length > 0) return false
  return store.roles.some((role) => fallbackRoleCodes.includes(role.roleCode))
}

/**
 * 是否系统管理员：服务端兜底能力（`isSuperAdmin`）或角色码命中。
 * 两者取并集——前者代表服务端明确授予的兜底能力，后者是权限数据缺失时的过渡判据。
 */
export function isSystemAdmin(store: UserStore): boolean {
  return store.isSuperAdmin || store.roles.some((role) => role.roleCode === SYSTEM_ADMIN_ROLE)
}

/** 是否分公司流程管理员（PRD 5.2；本文件只用于**收紧**可见性，不用于放宽） */
export function isCompanyAdmin(store: UserStore): boolean {
  return store.roles.some((role) => role.roleCode === COMPANY_ADMIN_ROLE)
}

/** 组织架构入口：系统管理员，或命中 `admin:org:*` 任一权限（前缀匹配） */
export function canManageOrg(store: UserStore): boolean {
  return isSystemAdmin(store) || hasAnyPermission(store, [IDENTITY_PERMISSION.orgPrefix])
}

/** 人员管理入口：系统管理员，或命中 `admin:user:*` 任一权限（前缀匹配） */
export function canManageUser(store: UserStore): boolean {
  return isSystemAdmin(store) || hasAnyPermission(store, [IDENTITY_PERMISSION.userPrefix])
}

/**
 * 组织人员**批量导入**入口（阶段 1.8）。
 *
 * <p>判据：系统管理员、分公司流程管理员，或命中 `admin:user:import`（权限种子里的人员导入细项）。
 * 与 `canManageUser` 的区别：导入是**高危写操作**（import-spec §2），普通 `admin:org:*` /
 * `admin:user:profile` 权限不应自动获得导入能力，因此单列一个判据。
 *
 * <p>分公司管理员即使看得到入口，也只能导入**本公司子树**内的行——服务端逐行校验数据域，
 * 域外行以 `E-XXX-020` fail-closed 拒绝整批（前端只如实展示报告）。
 */
export function canImportOrgUser(store: UserStore): boolean {
  return (
    isSystemAdmin(store) ||
    isCompanyAdmin(store) ||
    store.hasPermission(IDENTITY_PERMISSION.userImport)
  )
}

/** 管理后台总览入口：系统管理员，或命中任一 `admin:*` 权限 */
export function canEnterAdminConsole(store: UserStore): boolean {
  return isSystemAdmin(store) || hasAnyPermission(store, ['admin:'])
}

// ---------------------------------------------------------------------------
// 权限域（1.4 角色与权限树）
// ----------------------------------------------------------------------------
//  分级授权可见性（`doc/prd-0.1.md` 5.2「分公司流程管理员…**不可再向下分配权限**」）：
//    · 角色列表/维护：系统管理员 / `admin:role:list` / `company_admin`（兜底）可见；
//    · **权限树勾选：仅系统管理员与 `admin:role:grant` 可见——`company_admin` 不参与兜底**；
//    · 数据域与类别：`admin:authz:scope`；角色分配（挂人）：`admin:authz:assign`；
//    · 集团级角色（`role_scope=group`）的编辑/删除：`company_admin` 一律不可见。
//  ⚠ 本文件**只决定「渲不渲染」**，不承担鉴权：无权限用户直接构造请求仍会被服务端 403 拒绝，
//    服务端（`@PreAuthorize` / 权限拦截器）才是裁决方。
// ---------------------------------------------------------------------------

/** 「角色与权限」页面入口（列表页只读浏览也在内）：权限码优先，`company_admin` 兜底 */
export function canManageRole(store: UserStore): boolean {
  return hasPermissionOrRole(store, [AUTHZ_PERMISSION.roleList], [COMPANY_ADMIN_ROLE])
}

/**
 * 「角色与权限」后台入口（**侧栏与路由**的判据）。
 *
 * <p>比 `canManageRole` 更严：**不做 `company_admin` 角色码兜底**。
 *   侧栏与路由是「后台结构」的暴露面，按交付口径仅系统管理员（`isSuperAdmin` /
 *   `admin` 角色码）或**显式持有** `admin:role:list` 的账号可见。
 */
export function canOpenRoleAdmin(store: UserStore): boolean {
  if (isSystemAdmin(store)) return true
  return store.hasPermission(AUTHZ_PERMISSION.roleList)
}

/** 「权限变更日志」后台入口（侧栏与路由）：`admin:audit:permission` 或系统管理员 */
export function canOpenAuthzLogAdmin(store: UserStore): boolean {
  if (isSystemAdmin(store)) return true
  return store.hasPermission(AUTHZ_PERMISSION.changeLog)
}

/**
 * 权限树 / 数据域维护入口是否可见
 * （勾选权限树、配数据域、配类别、配组织节点范围）。
 *
 * <p>命中 `admin:role:grant`（权限树勾选）**或** `admin:authz:scope`（数据域与类别）即可；
 *   两者都是 `04-permissions.sql` 里的独立权限项。
 *
 * <p>「分公司管理员不可再授权」是 PRD 5.2 的**禁止性**条款，因此这里**不做角色码兜底**：
 *   `company_admin` 即使在权限数据缺失时也不可见；只有系统管理员（`isSuperAdmin` /
 *   `admin` 角色码，见 `isSystemAdmin`）或显式持有上述权限码才可见。
 */
export function canGrantRolePermission(store: UserStore): boolean {
  if (isSystemAdmin(store)) return true
  return (
    store.hasPermission(AUTHZ_PERMISSION.roleGrant) ||
    store.hasPermission(AUTHZ_PERMISSION.scopeConfig)
  )
}

/** 角色分配（挂人）入口：`admin:authz:assign`，`company_admin` 参与兜底（其可维护本公司人员） */
export function canAssignUserRole(store: UserStore): boolean {
  return hasPermissionOrRole(store, [AUTHZ_PERMISSION.assign], [COMPANY_ADMIN_ROLE])
}

/**
 * 单个角色是否可编辑 / 可删除（分级可见性）。
 *
 * <p>规则：
 *   · 权限数据缺失时的 `company_admin` 只能看/改**公司级**角色；
 *   · **集团级角色（`role_scope=group`）对 `company_admin` 一律不可见**（不渲染编辑/删除入口）；
 *   · 其余按 `canManageRole` 口径。
 * <p>内置受保护角色（9 个权威角色码）的 `code`/`role_scope` 只读与「禁止删除」由
 *    `RoleListView` 依 `RoleItem.builtIn` 另行拦截——那是**角色自身**的约束，与调用人是谁无关。
 * <p>服务端才是裁决方：越权请求一律 403，前端只负责不渲染。
 */
export function canEditRole(
  store: UserStore,
  role: { roleScope: string },
): boolean {
  if (isSystemAdmin(store)) return true
  if (role.roleScope === 'group' && isCompanyAdmin(store)) return false
  return canManageRole(store)
}

/** 删除入口（与编辑同口径；内置角色由 `builtIn` 另行拦截） */
export function canDeleteRole(store: UserStore, role: { roleScope: string }): boolean {
  return canEditRole(store, role)
}

/** 侧栏「管理后台」分组是否需要渲染 */
export function canEnterAdmin(store: UserStore): boolean {
  return (
    canManageOrg(store) ||
    canManageUser(store) ||
    canOpenRoleAdmin(store) ||
    canOpenAuthzLogAdmin(store) ||
    canReadFlowTemplate(store)
  )
}

// ---------------------------------------------------------------------------
// 流程域（2a.2 已交付 FlowDefinitionController）
// ----------------------------------------------------------------------------
//  权限码出自 `oa-deploy/sql/04-permissions.sql` 的 `admin:flow*` 三个精确码
//  （`admin:flow` / `admin:flow:template` / `admin:flow:node` / `admin:flow:publish`）：
//    · 读模板与节点、跑发布前校验（读）：`admin:flow:template`；
//    · 写节点配置：`admin:flow:node`（`GET /approver-rules` 也要求它，见控制器注释）；
//    · 开新版本 / 发布 / 归档 / 写模板级闸门：`admin:flow:publish`。
//  三者是**精确码**（种子里没有 `admin:flow:xxx` 的孙项），因此用精确匹配而不是前缀匹配。
//  ⚠ 与既有函数同口径：本文件只决定「渲不渲染」，服务端（`WorkflowPermissionService`
//     → `FlowConfigPermission`）才是裁决方，越权请求一律 403。
// ---------------------------------------------------------------------------

/** 流程域权限码（**冒号风格**，权威源 `oa-deploy/sql/04-permissions.sql`） */
export const FLOW_PERMISSION = {
  /** 流程管理父项（仅用于「后台是否可见」的兜底判断） */
  flow: 'admin:flow',
  /** 读模板 / 节点 / 版本 / 发布前校验（`GET /flow-templates/**`、`GET /flow-nodes/**` 读接口） */
  templateRead: 'admin:flow:template',
  /** 写节点配置 + 读 `GET /approver-rules`（解析规则清单） */
  nodeWrite: 'admin:flow:node',
  /** 开新版本 / 发布 / 归档 / 写模板级闸门配置 */
  publish: 'admin:flow:publish',
} as const

/**
 * 流程管理判据的**最小只读投影**。
 *
 * <p>为什么不用 `UserStore`：这三项都是只读字段，用结构类型可以让权限判据**脱离 Pinia**
 * 单独复核（`oa-web` 没有前端测试框架，纯函数越好验证越好）；Pinia 的 `useUserStore()`
 * 返回值在结构上天然满足本接口，调用点写法不变。
 */
export interface FlowPermissionSubject {
  readonly isSuperAdmin: boolean
  readonly permissions: readonly string[]
  readonly roles: readonly { readonly roleCode: string }[]
}

/**
 * 流程域判据：**权限码优先，`company_admin` 角色码兜底**。
 *
 * <p>兜底口径与 `hasPermissionOrRole` 完全一致：只有在 `permissions` 为空
 * （旧后端 / 未初始化权限数据）时才看角色码——`04-permissions.sql` 里
 * `company_admin` 确实持有 `admin:flow:template` / `:node` / `:publish` 三项
 * （分公司流程管理员可维护本公司流程模板），因此这个兜底不会放宽红线。
 */
function hasFlowPermissionOrCompanyAdmin(
  store: FlowPermissionSubject,
  permissionCodes: readonly string[],
): boolean {
  if (store.isSuperAdmin) return true
  if (permissionCodes.some((code) => store.permissions.includes(code))) return true
  if (store.permissions.length > 0) return false
  return store.roles.some((role) => role.roleCode === COMPANY_ADMIN_ROLE)
}

/**
 * 流程模板**读**入口（模板列表页 + 设计器页 + 侧栏「流程模板」）。
 *
 * <p>判据：`admin:flow:template`（或系统管理员 / 权限数据缺失时的 `company_admin`）。
 * 设计器里的只读能力（查看节点、版本历史、跑发布前校验）都只需要本权限。
 */
export function canReadFlowTemplate(store: FlowPermissionSubject): boolean {
  return hasFlowPermissionOrCompanyAdmin(store, [FLOW_PERMISSION.templateRead, FLOW_PERMISSION.flow])
}

/**
 * 流程**节点配置写**入口：`admin:flow:node`。
 *
 * <p>决定设计器里的保存按钮是否渲染（决议模式/阈值、策略、跳过条件、解析规则、换序、增删节点）。
 * 另外 `GET /api/v1/approver-rules`（9 条解析规则清单）在后端也要求本权限，
 * 因此「审批人规则下拉」的可用性同样由本判据决定。
 */
export function canWriteFlowNode(store: FlowPermissionSubject): boolean {
  return hasFlowPermissionOrCompanyAdmin(store, [FLOW_PERMISSION.nodeWrite])
}

/**
 * 流程**发布/归档**入口：`admin:flow:publish`。
 *
 * <p>决定「开新草稿」「发布」「归档」「写入闸门配置（Q6/Q7）」是否渲染。
 */
export function canPublishFlowTemplate(store: FlowPermissionSubject): boolean {
  return hasFlowPermissionOrCompanyAdmin(store, [FLOW_PERMISSION.publish])
}

/**
 * 导出入口是否可见。
 * import-spec §9.2 T-11：主数据（组织/人员/负责人/岗位/角色分配）导出**仅系统管理员**；
 * 单据与金额类导出才适用「系统管理员与财务角色」，两类分组治理、互不覆盖。
 * 权限码 `admin:user:export` 是精确码（种子里与 `admin:user:*` 并列的独立细项）。
 */
export function canExportMasterData(store: UserStore): boolean {
  return isSystemAdmin(store) || store.hasPermission(IDENTITY_PERMISSION.userExport)
}

// ---------------------------------------------------------------------------
// 单据运行域（阶段 2b：发起与填单 + 审批中心接真实引擎）
// ----------------------------------------------------------------------------
//  权限码是一**精确码 + 一个父项**，口径见 `FlowInstanceController` / `FormDataController`
//  的类注释（2026-10-04 收敛）：
//    · **写**（`POST /flow-instances` 建草稿、`PUT /forms/instances/{id}/draft` 保存、
//      `POST .../submit` 提交）= `flow` **单码**——「发起」这一动作自己的权限码；
//    · **只读**（预检 / 列表 / 详情 / 快照 / schema / 可写字段）= `flow` ∪ `admin:flow`。
//  为什么写口不收 `admin:flow`：`company_admin` 持 `admin:flow` 一族但（在旧种子下）不持 `flow`，
//  入口若放行它，就会出现「入口过、引擎 403」的错位（详见上述类注释）。
//  ⚠ 与既有函数同口径：本文件只决定「渲不渲染 / 放不放行」，服务端才是裁决方。
// ---------------------------------------------------------------------------

/** 单据运行域权限码（**冒号风格**，权威源 `oa-deploy/sql/04-permissions.sql` 的 `flow` 一族） */
export const FLOW_USE_PERMISSION = {
  /** 门户基础权限：发起、保存、提交、撤回、补件等**写**入口的动作码 */
  use: 'flow',
  /** 流程管理父项：**只读**入口的并集另一支 */
  adminFlow: 'admin:flow',
} as const

/**
 * 单据运行域判据的**最小只读投影**（结构类型，便于脱离 Pinia 单独复核；
 * `useUserStore()` 的返回值在结构上天然满足本接口）。
 */
export interface FlowUseSubject {
  readonly isSuperAdmin: boolean
  readonly permissions: readonly string[]
  readonly roles: readonly { readonly roleCode: string }[]
}

/**
 * 内置角色兜底：`permissions` 一个都没取回（旧后端 / 未初始化权限数据）时，
 * 只要拿到了角色码就放行——`04-permissions.sql` 里 9 个内置角色**全部**持有 `flow`，
 * 因此这个兜底不会放宽红线，只是避免「功能好了但入口永远不出现」。
 */
function flowFallback(store: FlowUseSubject): boolean {
  return store.permissions.length === 0 && store.roles.length > 0
}

/** 单据**只读**入口（审批中心列表 / 单据详情 / 预检 / schema）：`flow` ∪ `admin:flow` */
export function canReadFlowInstance(store: FlowUseSubject): boolean {
  if (store.isSuperAdmin) return true
  if (store.permissions.includes(FLOW_USE_PERMISSION.use)) return true
  if (store.permissions.includes(FLOW_USE_PERMISSION.adminFlow)) return true
  return flowFallback(store)
}

/** 单据**写**入口（发起 / 保存草稿 / 提交 / 撤回 / 补件）：`flow` 单码 */
export function canWriteFlowInstance(store: FlowUseSubject): boolean {
  if (store.isSuperAdmin) return true
  if (store.permissions.includes(FLOW_USE_PERMISSION.use)) return true
  return flowFallback(store)
}

/**
 * 是否可对「在途/待办非零」的阻断执行强制继续。
 *
 * <p>强制继续**不是权限码**：后端 `ForceReasonPolicy` 判的是**角色码 `admin`**
 *   （PRD 5.5 的唯一例外：仅系统管理员 + 必填原因 + 双留痕），因此这里只认
 *   `isSystemAdmin`（`isSuperAdmin` 或 `admin` 角色码），不去找权限码。
 *
 * <p>满足本判据不等于服务端一定放行——最终由服务端裁决，前端只负责可见性与必填校验。
 */
export function canForceChange(store: UserStore): boolean {
  return isSystemAdmin(store)
}
