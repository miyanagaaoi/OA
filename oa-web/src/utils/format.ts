/**
 * oa-web · 格式化工具
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md`
 *   · 「Typography › Principles」：金额一律等宽数字（tnum）+ 右对齐 + 两位小数
 *   · 「Data Display › table 的金额列」：金额 ≥ 100 万时同时显示"万元"换算
 *   · 「Colors › Text」：时间戳用 ink-subtle
 * 约束：后端金额为字符串定点数 DECIMAL(18,2)，前端**禁止转浮点**（tech-design §5.4）
 */

/** 千分位 + 固定两位小数。纯字符串运算，不做浮点转换 */
export function formatAmount(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return ''

  const raw = typeof value === 'number' ? value.toFixed(2) : value.trim()
  const negative = raw.startsWith('-')
  const unsigned = negative ? raw.slice(1) : raw

  const [intPart = '0', decPart = '00'] = unsigned.split('.')
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const decimals = (decPart + '00').slice(0, 2)

  return `${negative ? '-' : ''}${grouped}.${decimals}`
}

/** 万元换算：金额 ≥ 100 万时才展示（DESIGN.md 金额列规则） */
export function formatWan(value: string | number | null | undefined): string | null {
  if (value === null || value === undefined || value === '') return null

  const raw = typeof value === 'number' ? value.toFixed(2) : value.trim()
  const numeric = Number(raw)
  if (!Number.isFinite(numeric) || Math.abs(numeric) < 1_000_000) return null

  const wan = numeric / 10_000
  return `${wan.toFixed(2)} 万`
}

export function formatCurrency(value: string | number | null | undefined): string {
  const amount = formatAmount(value)
  return amount ? `¥ ${amount}` : ''
}

/** 文件大小 */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

/** 时间戳：YYYY-MM-DD HH:mm，配合 mono 字体使用 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 相对到期时间，用于超时预警提示 */
export function describeDue(dueAt: string | null | undefined, now: Date = new Date()): string | null {
  if (!dueAt) return null
  const due = new Date(dueAt)
  if (Number.isNaN(due.getTime())) return null

  const diffMs = due.getTime() - now.getTime()
  const overdue = diffMs < 0
  const hours = Math.floor(Math.abs(diffMs) / 3_600_000)
  const minutes = Math.floor((Math.abs(diffMs) % 3_600_000) / 60_000)
  const text = hours > 0 ? `${hours} 小时 ${minutes} 分` : `${minutes} 分`

  return overdue ? `已超时 ${text}` : `剩余 ${text}`
}

/** 节点序号 → ①–⑦ 带圈数字（发起者与结束不计入编号） */
export function nodeNoGlyph(nodeNo?: number): string {
  const glyphs = ['①', '②', '③', '④', '⑤', '⑥', '⑦']
  if (!nodeNo || nodeNo < 1 || nodeNo > glyphs.length) return ''
  return glyphs[nodeNo - 1]
}
