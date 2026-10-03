/**
 * oa-web · 批量导入/导出类型（阶段 1.8）
 * ----------------------------------------------------------------------------
 * 与后端 `com.oa.admin.bulk.ImportReport` / `ImportFinding` / `ImportKind` 一一对应，
 * 契约来源 `doc/import-spec.md` §5.4（统计字段）与 §5.5（机器可读结构）。
 */

/** 校验发现（报告行） */
export interface ImportFinding {
  severity: 'error' | 'warning'
  code: string
  file: string
  /** 文件行号（表头为第 1 行），便于填报人直接定位 */
  line: number
  column?: string | null
  value?: string | null
  message: string
}

/** 错误码分布 + 修正建议（可直接复制给填报人） */
export interface ImportSuggestion {
  code: string
  count: number
  suggestion: string
}

/** 新增人员的初始口令（import-spec T-03：仅本次返回，线下加密清单分发） */
export interface ImportCredential {
  account: string
  name: string
  initialPassword: string
  note: string
}

/** 校验报告（preview 与 commit 同结构，`dryRun` 区分） */
export interface ImportReport {
  kind: string
  file: string
  label: string
  dryRun: boolean
  /** 无 error 即通过（commit 只在通过时落库） */
  ok: boolean
  totalRows: number
  passedRows: number
  /** 存在 error 的行数（按行去重） */
  failedRows: number
  errors: number
  warnings: number
  added: number
  updated: number
  skipped: number
  suggestions: ImportSuggestion[]
  findings: ImportFinding[]
  credentials?: ImportCredential[]
  notes: string[]
}

/** 导入类型元数据（服务端下发，避免前后端列契约漂移） */
export interface ImportKindMeta {
  kind: string
  label: string
  file: string
  columns: string[]
  previewRoute: string
  commitRoute: string
}

/** 导出结果占位类型（导出走 Blob，不经 JSON 包装） */
export interface ImportResult {
  ok: boolean
}
