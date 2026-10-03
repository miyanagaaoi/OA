/**
 * oa-web · 单据状态映射
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md` 附录 B「状态与颜色映射表（全局唯一，实现时不得扩展）」
 *       + `normify-oa/modules/oa/design/component/data-display/status-pill.md`
 *
 * 铁律：状态与颜色一一映射且全局唯一；不要为不同单据类型发明新配色。
 * 状态色只出现在状态徽标、流程节点与风险提示上。
 */
import type { DocumentStatus, FormType, TrailNodeState } from '@/types/api'
import type { LeaderType, OrgStatus, UserStatus } from '@/types/identity'

export interface StatusStyle {
  /** 全局状态徽标类名（styles/index.scss 的 .oa-pill） */
  pillClass: 'is-pending' | 'is-approved' | 'is-rejected' | 'is-processing' | 'is-closed'
  label: string
  /** 是否需要与超时预警标记叠加 */
  timeout?: boolean
}

const STATUS_MAP: Record<DocumentStatus, StatusStyle> = {
  draft: { pillClass: 'is-closed', label: '草稿' },
  pending: { pillClass: 'is-pending', label: '待我审批' },
  processing: { pillClass: 'is-processing', label: '审批中' },
  approved: { pillClass: 'is-approved', label: '已通过' },
  rejected: { pillClass: 'is-rejected', label: '已驳回' },
  closed: { pillClass: 'is-closed', label: '已撤回' },
  timeout: { pillClass: 'is-pending', label: '超时预警', timeout: true },
}

export function statusStyle(status: DocumentStatus): StatusStyle {
  return STATUS_MAP[status] ?? STATUS_MAP.closed
}

export function statusPillClass(status: DocumentStatus): string {
  return statusStyle(status).pillClass
}

export function statusLabel(status: DocumentStatus, fallback?: string): string {
  return fallback || statusStyle(status).label
}

/** 单据类型 → 图标字符（单一强调色；确需区分类型时用图标形状而非颜色） */
export const FORM_TYPE_GLYPH: Record<FormType, string> = {
  matter: '事',
  fund: '资',
  contract: '合',
  seal_cert: '印',
}

/** 审批轨迹节点四态 → 圆点类名（workflow-step-*） */
export function trailStepClass(state: TrailNodeState): string {
  switch (state) {
    case 'done':
      return ''
    case 'current':
      return 'is-current'
    case 'timeout':
      return 'is-timeout'
    case 'skipped':
      return 'is-pending'
    case 'pending':
    default:
      return 'is-pending'
  }
}

/** 节点序号 → ①–⑦ 带圈数字（发起者与结束不计入节点编号） */
export function nodeNoGlyphSafe(nodeNo?: number): string {
  const glyphs = ['①', '②', '③', '④', '⑤', '⑥', '⑦']
  if (!nodeNo || nodeNo < 1 || nodeNo > glyphs.length) return ''
  return glyphs[nodeNo - 1]
}

export function trailStateLabel(state: TrailNodeState): string {
  switch (state) {
    case 'done':
      return '已完成'
    case 'current':
      return '进行中'
    case 'timeout':
      return '超时'
    case 'skipped':
      return '已跳过'
    case 'pending':
    default:
      return '未到达'
  }
}

// ---------------------------------------------------------------------------
// 身份域状态徽标（组织 / 人员 / 岗位 / 负责人）
// ----------------------------------------------------------------------------
//  口径与单据状态完全一致：**只从 `.oa-pill` 已有的五个类里选**，不新造配色
//  （DESIGN.md 附录 B「状态与颜色映射表（全局唯一，实现时不得扩展）」）。
//  语义落位：
//    · 启用 / 在职 / 主岗  → 正常可用 → success（is-approved）
//    · 停用 / 离职         → 中性、非风险 → neutral（is-closed）
//    · 正职负责人          → 当前责任人，需与副职区分 → info（is-processing）
//  中文标签与 `doc/import-spec.md` §3 的枚举中文逐字一致，改动即契约变更。
// ---------------------------------------------------------------------------
export interface IdentityPillStyle {
  pillClass: StatusStyle['pillClass']
  label: string
}

const ORG_STATUS_MAP: Record<OrgStatus, IdentityPillStyle> = {
  active: { pillClass: 'is-approved', label: '启用' },
  disabled: { pillClass: 'is-closed', label: '停用' },
}

const USER_STATUS_MAP: Record<UserStatus, IdentityPillStyle> = {
  active: { pillClass: 'is-approved', label: '在职' },
  // 离职与停用都是「不可用且非风险」，颜色不再细分（避免为不同对象发明新配色）
  resigned: { pillClass: 'is-closed', label: '离职' },
  disabled: { pillClass: 'is-closed', label: '停用' },
}

const LEADER_TYPE_MAP: Record<LeaderType, IdentityPillStyle> = {
  primary: { pillClass: 'is-processing', label: '正职' },
  deputy: { pillClass: 'is-closed', label: '副职' },
}

export function orgStatusStyle(status: OrgStatus): IdentityPillStyle {
  return ORG_STATUS_MAP[status] ?? ORG_STATUS_MAP.disabled
}

export function userStatusStyle(status: UserStatus): IdentityPillStyle {
  return USER_STATUS_MAP[status] ?? USER_STATUS_MAP.disabled
}

export function leaderTypeStyle(type: LeaderType): IdentityPillStyle {
  return LEADER_TYPE_MAP[type] ?? LEADER_TYPE_MAP.deputy
}

/** 岗位主岗标记（E-POS-005：每人最多一个主岗） */
export function primaryPillStyle(isPrimary: boolean): IdentityPillStyle {
  return isPrimary
    ? { pillClass: 'is-approved', label: '主岗' }
    : { pillClass: 'is-closed', label: '兼岗' }
}

// ---------------------------------------------------------------------------
// 权限域状态徽标（角色层级 / 是否内置）
// ----------------------------------------------------------------------------
//  口径同上：**只从 `.oa-pill` 已有的五个类里选**，不新造配色。
//    · 集团级角色    → 作用范围最大 → info（is-processing）
//    · 公司级角色    → 限于本公司   → neutral（is-closed）
//    · 内置受保护角色 → 不可删改     → success（is-approved）
//    · 自定义角色    → 可维护       → neutral（is-closed）
// ---------------------------------------------------------------------------
const ROLE_SCOPE_MAP: Record<'group' | 'company', IdentityPillStyle> = {
  group: { pillClass: 'is-processing', label: '集团级' },
  company: { pillClass: 'is-closed', label: '公司级' },
}

export function roleScopeStyle(scope: string): IdentityPillStyle {
  return scope === 'group' ? ROLE_SCOPE_MAP.group : ROLE_SCOPE_MAP.company
}

/**
 * 内置角色标记（`sys_role.code` 的 9 个权威取值）。
 * `true` = 受保护：禁止删除、`code`/`role_scope` 只读（任务书硬要求 3）。
 */
export function roleBuiltInStyle(builtIn: boolean): IdentityPillStyle {
  return builtIn
    ? { pillClass: 'is-approved', label: '内置' }
    : { pillClass: 'is-closed', label: '自定义' }
}

// ---------------------------------------------------------------------------
// 流程域状态徽标（模板状态机，`doc/templates.md` §3.3）
// ----------------------------------------------------------------------------
//  口径同上：**只从 `.oa-pill` 已有的五个类里选**，不新造配色。
//    · published 已发布 → 生效中、可被新实例使用 → success（is-approved）
//    · draft     草稿   → 唯一可编辑态、不可被新实例使用 → neutral（is-closed）
//    · archived  已归档 → 只读、不可被新实例使用（在途继续） → neutral（is-closed）
//  草稿与已归档同为 neutral 是刻意的：二者都不是「风险」也不是「生效」，
//  界面靠**文字**区分（`FLOW_STATUS_LABEL`），不靠颜色（DESIGN.md 附录 B 不得扩展）。
// ---------------------------------------------------------------------------
const FLOW_STATUS_MAP: Record<'draft' | 'published' | 'archived', IdentityPillStyle> = {
  draft: { pillClass: 'is-closed', label: '草稿' },
  published: { pillClass: 'is-approved', label: '已发布' },
  archived: { pillClass: 'is-closed', label: '已归档' },
}

/**
 * 流程模板状态徽标。
 * 入参已由 `api/flow.ts` 收窄为 `draft|published|archived`（未知取值在映射层一律按
 * `archived` 处理，fail-safe 到只读），因此这里只需处理三个已知值，其余按中性兜底。
 */
export function flowStatusStyle(status: string): IdentityPillStyle {
  if (status === 'published' || status === 'archived') return FLOW_STATUS_MAP[status]
  return FLOW_STATUS_MAP.draft
}
