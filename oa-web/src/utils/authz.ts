/**
 * oa-web · 权限域规则与标签（纯函数 + 常量，不依赖 Pinia）
 * ----------------------------------------------------------------------------
 * 与 `utils/status.ts` / `utils/identity-rules.ts` 的分工一致：
 *   · 本文件只放**纯函数与常量**（标签映射、受保护角色、权限树遍历与差异计算、校验）；
 *   · 「当前登录人能不能看见某个入口」这类需要会话态的判据放 `utils/admin.ts`。
 *
 * 来源：
 *   · `doc/import-spec.md` §2.3（9 个角色码 + 建议数据域，**权威源是 `sys_role.code` 列注释**）、
 *     §3.6（`role_code` 格式 `^[a-z][a-z0-9_]{1,31}$`、`scope_org_path` 留空 = 按角色默认）、
 *     §4.6（E-ROLE-001 ~ E-ROLE-005）
 *   · `doc/data-model.md` 3.1（`role_scope` group/company、`data_scope` 五值）、
 *     3.3（类别五值 business/economy/admin/hr/invest）、3.4（`perm_type` menu/button/api）
 *   · `doc/prd-0.1.md` 5.2（逐级分配、分公司管理员不可再授权）、6.10 REQ-ADMIN-003 / 006、
 *     AC-59（权限配置与变更留痕）
 */
import type { DataScope } from '@/types/api'
import type {
  ChangeTargetType,
  MatterCategory,
  PermissionNode,
  PermissionType,
  RoleScope,
} from '@/types/authz'

// ---------------------------------------------------------------------------
// 受保护的内置角色（任务书硬要求 3）
// ---------------------------------------------------------------------------
/**
 * `sys_role.code` 的 9 个权威取值（`doc/data-model.md` 3.1 的列注释 = 唯一权威定义）。
 * 这 9 个角色在 UI 上**禁止删除**，且 `code` 与 `role_scope` **只读**
 * （改 code 会让已落库的 `sys_user_role` 与导入模板白名单同时失效）。
 */
export const BUILT_IN_ROLE_CODES = [
  'admin',
  'company_admin',
  'employee',
  'dept_leader',
  'branch_leader',
  'subsidiary_gm',
  'finance_owner',
  'group_leader',
  'chairman',
] as const

export type BuiltInRoleCode = (typeof BUILT_IN_ROLE_CODES)[number]

export interface BuiltInRoleMeta {
  /** 角色名（与 import-spec §2.3 表格逐字一致） */
  name: string
  roleScope: RoleScope
  /** 建议数据域（import-spec §2.3「数据域建议」列） */
  dataScope: DataScope
}

/**
 * 9 个内置角色的名称 / 层级 / 建议数据域。
 * 用途有二：① 列表未取到时兜底展示；② 新建/编辑表单的默认值。
 * ⚠ 运行期仍以服务端 `sys_role` 为准，本表只做提示与兜底。
 */
export const BUILT_IN_ROLE_META: Record<BuiltInRoleCode, BuiltInRoleMeta> = {
  admin: { name: '系统管理员', roleScope: 'group', dataScope: 'group_all' },
  company_admin: { name: '分公司流程管理员', roleScope: 'company', dataScope: 'company' },
  employee: { name: '普通员工', roleScope: 'company', dataScope: 'self' },
  dept_leader: { name: '部门/科室负责人', roleScope: 'company', dataScope: 'dept' },
  branch_leader: { name: '分公司分管领导', roleScope: 'company', dataScope: 'company' },
  subsidiary_gm: { name: '子公司总经理', roleScope: 'company', dataScope: 'company' },
  finance_owner: { name: '集团归口（财务部）负责人', roleScope: 'group', dataScope: 'group_category' },
  group_leader: { name: '集团分管领导', roleScope: 'group', dataScope: 'group_category' },
  chairman: { name: '集团董事长', roleScope: 'group', dataScope: 'group_all' },
}

/** 是否内置受保护角色码（`sys_role.code` 白名单） */
export function isBuiltInRoleCode(code: string): boolean {
  return (BUILT_IN_ROLE_CODES as readonly string[]).includes(code)
}

/** 内置角色的兜底元数据；非内置返回 null（不猜） */
export function builtInRoleMeta(code: string): BuiltInRoleMeta | null {
  return isBuiltInRoleCode(code) ? BUILT_IN_ROLE_META[code as BuiltInRoleCode] : null
}

// ---------------------------------------------------------------------------
// 标签映射（服务端未下发中文标签时兜底；下发则优先用服务端值）
// ---------------------------------------------------------------------------
export const ROLE_SCOPE_LABEL: Record<RoleScope, string> = {
  group: '集团级',
  company: '公司级',
}

export const DATA_SCOPE_LABEL: Record<DataScope, string> = {
  self: '本人',
  dept: '本部门',
  company: '本公司',
  group_all: '全集团',
  group_category: '全集团按归口类别',
}

/** 与 `doc/prd-0.1.md` 5.3 的数据域口径一一对应（界面上直接展示，避免用户猜） */
export const DATA_SCOPE_DESCRIPTION: Record<DataScope, string> = {
  self: '仅本人发起的单据 + 本人作为审批人/抄送人的单据',
  dept: '本部门/科室成员发起的单据 + 本人审批的单据',
  company: '本公司（含下属部门/科室）的全部单据',
  group_all: '全集团全部单据（无过滤）',
  group_category: '按事项类别限定范围，必须至少勾选一个类别',
}

/** `group_category` 是唯一需要配类别的数据域（硬要求 4） */
export const GROUP_CATEGORY_SCOPE: DataScope = 'group_category'

export function dataScopeRequiresCategory(scope: DataScope): boolean {
  return scope === GROUP_CATEGORY_SCOPE
}

/** 事项类别五值中文（`sys_role_category.category`；import-spec §3.4 业务线复用同一组值） */
export const MATTER_CATEGORY_LABEL: Record<MatterCategory, string> = {
  business: '经营',
  economy: '经济',
  admin: '行政',
  hr: '人力',
  invest: '投资',
}

/** 类别顺序固定为五值声明顺序（经营/经济/行政/人力/投资），界面不按字母序重排 */
export const MATTER_CATEGORY_CODES: MatterCategory[] = [
  'business',
  'economy',
  'admin',
  'hr',
  'invest',
]

export const MATTER_CATEGORY_OPTIONS = MATTER_CATEGORY_CODES.map((code) => ({
  value: code,
  label: MATTER_CATEGORY_LABEL[code],
}))

export const PERMISSION_TYPE_LABEL: Record<PermissionType, string> = {
  menu: '菜单',
  button: '按钮',
  api: '接口',
}

/** 变更对象类型中文（REQ-LOG-004 的日志列表列） */
export const CHANGE_TARGET_LABEL: Record<ChangeTargetType, string> = {
  role: '角色',
  role_permission: '权限树勾选',
  user_role: '用户角色分配',
  data_scope: '数据域',
  category: '事项类别',
  org_node: '组织节点范围',
}

/**
 * 变更动作中文兜底（服务端 `actionLabel` 优先，其次本地字典，最后原样展示 `action`）。
 *
 * <p>键沿用全项目的**冒号风格**；后端若下发别的动作码（例如 `role.permission.update`
 *   这类点号动作码），`authzActionLabel` 会退化成「服务端标签优先 → 原样展示」，
 *   不会因为本地字典没命中就丢信息。
 */
export const AUTHZ_ACTION_LABEL: Record<string, string> = {
  'role:create': '新增角色',
  'role:update': '修改角色',
  'role:delete': '删除角色',
  'role:permission:update': '调整权限树勾选',
  'role:data-scope:update': '调整数据域',
  'role:category:update': '调整事项类别',
  'role:org-node:update': '调整组织节点范围',
  'user:role:assign': '分配角色',
  'user:role:revoke': '撤销角色分配',
  'cache:invalidate': '失效权限缓存',
}

export function roleScopeLabel(code: string, fallback?: string | null): string {
  if (fallback) return fallback
  return code === 'group' || code === 'company' ? ROLE_SCOPE_LABEL[code] : code
}

export function dataScopeLabel(code: string, fallback?: string | null): string {
  if (fallback) return fallback
  const known = DATA_SCOPE_LABEL[code as DataScope]
  return known ?? code
}

export function dataScopeDescription(code: string): string {
  return DATA_SCOPE_DESCRIPTION[code as DataScope] ?? ''
}

export function categoryLabel(code: string, fallback?: string | null): string {
  if (fallback) return fallback
  return MATTER_CATEGORY_LABEL[code as MatterCategory] ?? code
}

export function permissionTypeLabel(code: string, fallback?: string | null): string {
  if (fallback) return fallback
  return PERMISSION_TYPE_LABEL[code as PermissionType] ?? code
}

export function changeTargetLabel(code: string, fallback?: string | null): string {
  if (fallback) return fallback
  return CHANGE_TARGET_LABEL[code as ChangeTargetType] ?? code
}

export function authzActionLabel(code: string, fallback?: string | null): string {
  if (fallback) return fallback
  return AUTHZ_ACTION_LABEL[code] ?? code
}

// ---------------------------------------------------------------------------
// 表单校验（前端预检；服务端仍是权威，E-ROLE-001/002 等由后端最终裁决）
// ---------------------------------------------------------------------------
/** `role_code`：小写蛇形 2–32 位（import-spec §3.6 / enums.md §1.1） */
const ROLE_CODE_RE = /^[a-z][a-z0-9_]{1,31}$/

export function checkRoleCode(code: string): string {
  const value = code.trim()
  if (!value) return '请填写角色码'
  if (!ROLE_CODE_RE.test(value)) {
    return '角色码为 2–32 位小写字母/数字/下划线，且以字母开头（如 company_admin）'
  }
  return ''
}

export function checkRoleName(name: string): string {
  const value = name.trim()
  if (!value) return '请填写角色名称'
  if (value.length > 50) return '角色名称不超过 50 字'
  return ''
}

export function checkRoleRemark(remark: string): string {
  return remark.length > 255 ? '备注不超过 255 字' : ''
}

/** 分配备注沿用 `sys_user_role.remark VARCHAR(255)`（import-spec §3.6 E-ROLE-005） */
export function checkAssignmentRemark(remark: string): string {
  return remark.length > 255 ? '备注不超过 255 字（E-ROLE-005）' : ''
}

/**
 * 数据域 × 类别前置校验（硬要求 4）：
 * `group_category` 必须至少勾选一个事项类别，否则保存前置拦截并说明原因。
 * 返回值 `''` 表示通过。
 */
export function checkDataScopeCategories(dataScope: DataScope, categories: MatterCategory[]): string {
  if (!dataScopeRequiresCategory(dataScope)) return ''
  if (categories.length === 0) {
    return '数据域「全集团按归口类别」必须至少勾选一个事项类别（经营/经济/行政/人力/投资），否则该角色看不到任何数据'
  }
  return ''
}

// ---------------------------------------------------------------------------
// 权限树遍历
// ---------------------------------------------------------------------------
/** 摊平权限树（先序），用于建索引与差异展示 */
export function flattenPermissionTree(
  nodes: PermissionNode[],
  out: PermissionNode[] = [],
): PermissionNode[] {
  for (const node of nodes) {
    out.push(node)
    if (node.children?.length) flattenPermissionTree(node.children, out)
  }
  return out
}

/** id → 节点索引；`seen` 兼作环保护（脏数据只会跳过，不会挂死页面） */
export function indexPermissionTree(
  nodes: PermissionNode[],
  map: Map<string, PermissionNode> = new Map<string, PermissionNode>(),
  seen: Set<string> = new Set<string>(),
): Map<string, PermissionNode> {
  for (const node of nodes) {
    if (seen.has(node.id)) continue
    seen.add(node.id)
    map.set(node.id, node)
    if (node.children?.length) indexPermissionTree(node.children, map, seen)
  }
  return map
}

/** 全部叶子节点 id（`check-strictly=false` 时「存叶子」口径需要它） */
export function collectPermissionLeafIds(nodes: PermissionNode[], out: string[] = []): string[] {
  for (const node of nodes) {
    if (node.children?.length) collectPermissionLeafIds(node.children, out)
    else out.push(node.id)
  }
  return out
}

/** 树中全部节点 id（含父节点） */
export function collectPermissionIds(nodes: PermissionNode[], out: string[] = []): string[] {
  for (const node of nodes) {
    out.push(node.id)
    if (node.children?.length) collectPermissionIds(node.children, out)
  }
  return out
}

/**
 * 服务端回显口径 → el-tree 的 `setCheckedKeys` 入参（**只给叶子**）。
 *
 * 为什么不能直接把服务端 id 全塞给 `setCheckedKeys`：`check-strictly=false` 时 el-tree
 * 会把「父节点被勾选」当作「整棵子树被勾选」，用含父节点的全量集合回填会**凭空多勾**子节点。
 * 因此这里只保留「末端已勾选节点」，父节点的全选与半选由 el-tree 按联动规则推导：
 *   · 节点在集合内、且**没有**子节点也在集合内 → 取它的**叶子后代**（含自身为叶子的情况）；
 *     这样即便服务端只回父节点 id（「整支都授权」），回显也能铺满该分支的叶子。
 *   · 子节点有命中 → 继续往子树里走，由子节点的勾选决定父节点的勾选/半选。
 */
export function toCheckableLeafIds(nodes: PermissionNode[], grantedIds: string[]): string[] {
  const granted = new Set(grantedIds)
  const result: string[] = []
  const walk = (list: PermissionNode[]): void => {
    for (const node of list) {
      const childInGranted = (node.children ?? []).filter((child) => granted.has(child.id))
      if (childInGranted.length === 0) {
        if (granted.has(node.id)) collectPermissionLeafIds([node], result)
        continue
      }
      walk(childInGranted)
    }
  }
  walk(nodes)
  return result
}

/** 节点勾选态（与 el-tree 的 check-strictly=false 语义一致） */
type CheckStatus = 'none' | 'partial' | 'full'

export interface ResolvedCheckedState {
  /** 全选中：叶子 + 「子节点全部选中」的父节点（= el-tree 的 `getCheckedKeys`） */
  checkedIds: string[]
  /** 半选父节点（= el-tree 的 `getHalfCheckedKeys`，只参与回显与提交副本） */
  halfCheckedIds: string[]
}

function resolveNodeStatus(
  node: PermissionNode,
  leafSet: Set<string>,
  checked: string[],
  half: string[],
): CheckStatus {
  if (!node.children?.length) return leafSet.has(node.id) ? 'full' : 'none'
  const statuses = node.children.map((child) => resolveNodeStatus(child, leafSet, checked, half))
  if (statuses.every((status) => status === 'full')) {
    checked.push(node.id)
    return 'full'
  }
  if (statuses.some((status) => status !== 'none')) {
    half.push(node.id)
    return 'partial'
  }
  return 'none'
}

/**
 * 由「叶子勾选集合」**推导**出 el-tree 的完整勾选态。
 *
 * <p>为什么需要它：本页的树是**受控重挂载**（展开/折叠/全选/反选都换 `key` 重建），
 *   重建时只有 `default-checked-keys` 一个入口，因此必须自己算出与 el-tree 一致的
 *   `checkedKeys ∪ halfCheckedKeys`；否则「全选/反选」之后草稿里少了父节点，
 *   保存前差异会把父节点整批误报为「移除」。
 *
 * <p>返回的 `checkedIds` **已包含**命中的叶子；树里找不到的 id 会被丢弃（服务端删过的权限）。
 */
export function resolveCheckedStateFromLeaves(
  nodes: PermissionNode[],
  checkedLeafIds: readonly string[],
): ResolvedCheckedState {
  const treeLeafIds = new Set(collectPermissionLeafIds(nodes))
  const leafSet = new Set(checkedLeafIds.filter((id) => treeLeafIds.has(id)))
  const parents: string[] = []
  const half: string[] = []
  for (const node of nodes) resolveNodeStatus(node, leafSet, parents, half)
  return { checkedIds: [...leafSet, ...parents], halfCheckedIds: half }
}

/** 权限节点展示名：`名称（code）`——code 是唯一权限码，排障时必须可见 */
export function permissionDisplayName(node: PermissionNode): string {
  return `${node.name}（${node.code}）`
}

/** 从根到该节点的路径文本（差异清单里说明「改了哪一支」） */
export function permissionPathText(
  index: Map<string, PermissionNode>,
  id: string,
  separator = ' / ',
): string {
  const chain: string[] = []
  let cursor = index.get(id)
  let guard = 0
  while (cursor && guard < 32) {
    chain.unshift(cursor.name)
    const parentId: string | null = cursor.parentId
    cursor = parentId ? index.get(parentId) : undefined
    guard += 1
  }
  return chain.join(separator)
}

// ---------------------------------------------------------------------------
// 授权差异（保存前展示「新增 / 移除 N 项」）
// ---------------------------------------------------------------------------
export interface PermissionIdDiff {
  addedIds: string[]
  removedIds: string[]
}

/** 已保存集合 → 草稿集合的差异（`added` = 草稿新增，`removed` = 草稿移除） */
export function diffPermissionIdSets(before: string[], after: string[]): PermissionIdDiff {
  const beforeSet = new Set(before)
  const afterSet = new Set(after)
  return {
    addedIds: after.filter((id) => !beforeSet.has(id)),
    removedIds: before.filter((id) => !afterSet.has(id)),
  }
}

/** 差异 id → 可展示的节点列表（id 在树里找不到时跳过——服务端下了已删权限就被忽略） */
export function toPermissionNodes(
  index: Map<string, PermissionNode>,
  ids: string[],
): PermissionNode[] {
  return ids.map((id) => index.get(id)).filter((node): node is PermissionNode => Boolean(node))
}
