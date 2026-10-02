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
