/**
 * oa-web · 组织人员批量导入/导出 API（阶段 1.8）
 * ----------------------------------------------------------------------------
 * 来源：`doc/import-spec.md`（V1.3）
 *   · §2.1 五步流水线（顺序不可调换）：①组织 → ②人员 → ③负责人 → ④岗位 → ⑤角色分配
 *   · §5.1/§5.4 校验报告（行号 + 列 + 错误码 + 中文说明，byCode/suggestions）
 *   · §5.3 全量校验 → 整批判定 → 单事务导入（**错误零落库**）
 *   · §9.1 导出列与模板一致（往返编辑）；§9.2 主数据导出仅系统管理员
 *
 * 路由（与后端 BulkImportController 的表一致）：
 *   preview: POST {prefix}/import/preview    commit: POST {prefix}/import
 *   prefix ：/identity/orgs、/identity/users、/identity/org-leaders、
 *            /identity/user-positions、/identity/user-roles
 *
 * ⚠ 导入是**高危写操作**：服务端要求系统管理员或分公司流程管理员，且逐行校验数据域
 *   （域外行 fail-closed 拒绝整批）。前端只负责「提交文件 + 展示报告」，不承担鉴权。
 */
import http, { get, post } from './http'
import type {
  ImportKindMeta,
  ImportReport,
  ImportResult,
} from '@/types/bulk'

/** 五类导入的路由前缀（与 ImportKind.routePrefix() 一致） */
export const IMPORT_PREFIX: Record<string, string> = {
  org: '/identity/orgs',
  user: '/identity/users',
  'org-leader': '/identity/org-leaders',
  'user-position': '/identity/user-positions',
  'user-role': '/identity/user-roles',
}

/** 五类导入的元数据（标签 + 模板文件名 + 列契约 + 路由），由服务端下发以免前后端漂移 */
export async function fetchImportKinds(): Promise<ImportKindMeta[]> {
  return get<ImportKindMeta[]>('/admin/bulk-import/kinds')
}

/**
 * 上传并按「裸 CSV 原文」提交（`text/csv` + UTF-8 BOM）。
 *
 * 之所以不用 multipart：服务端两种形态都支持（见 BulkImportController#bytes），
 * 裸 CSV 更省事，且与 `curl --data-binary @org.csv` 的运维口径一致。
 */
async function uploadFile(path: string, file: File): Promise<ImportReport> {
  const buffer = await file.arrayBuffer()
  return post<ImportReport>(path, buffer, {
    headers: { 'Content-Type': 'text/csv' },
    // 文件本身是 CSV，不走 JSON 序列化
    transformRequest: [(data: unknown) => data],
  })
}

/** 干跑校验（不写任何业务表） */
export async function previewImport(kind: string, file: File): Promise<ImportReport> {
  const prefix = IMPORT_PREFIX[kind]
  if (!prefix) throw new Error(`未知的导入类型：${kind}`)
  return uploadFile(`${prefix}/import/preview`, file)
}

/** 正式导入（事务落库；校验不通过时整批拒绝） */
export async function commitImport(kind: string, file: File): Promise<ImportReport> {
  const prefix = IMPORT_PREFIX[kind]
  if (!prefix) throw new Error(`未知的导入类型：${kind}`)
  return uploadFile(`${prefix}/import`, file)
}

/** 受影响在途单据清单预检（import-spec §7；W12 前清单恒为空） */
export async function impactPreview(kind: string, file: File): Promise<ImportReport> {
  const buffer = await file.arrayBuffer()
  return post<ImportReport>(`/admin/bulk-import/impact-preview?kind=${encodeURIComponent(kind)}`, buffer, {
    headers: { 'Content-Type': 'text/csv' },
    transformRequest: [(data: unknown) => data],
  })
}

/**
 * 五张模板导出（仅系统管理员）。返回 Blob 供浏览器直接下载；
 * 文件名与模板一致，保证「导出 → 修改 → 再导入」闭环（§9.1）。
 */
export async function exportMasterData(
  which: 'org' | 'user' | 'org-leader' | 'user-position' | 'user-role' | 'audit-log',
): Promise<{ blob: Blob; fileName: string }> {
  const routes: Record<string, { path: string; fileName: string }> = {
    org: { path: '/identity/orgs/export', fileName: 'org.csv' },
    user: { path: '/identity/users/export', fileName: 'user.csv' },
    'org-leader': { path: '/identity/org-leaders/export', fileName: 'org_leader.csv' },
    'user-position': { path: '/identity/user-positions/export', fileName: 'user_position.csv' },
    'user-role': { path: '/identity/user-roles/export', fileName: 'user_role.csv' },
    'audit-log': { path: '/admin/audit-logs/export', fileName: 'audit-log.csv' },
  }
  const target = routes[which]
  if (!target) throw new Error(`未知的导出类型：${which}`)
  const response = await http.get(target.path, {
    responseType: 'blob',
    unwrap: false,
  } as never)
  // unwrap=false 时拦截器原样透传 AxiosResponse
  const raw = response as unknown as { data: Blob }
  return { blob: raw.data, fileName: target.fileName }
}

/** 触发浏览器下载（导出物为 CSV + UTF-8 BOM） */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/** 导出结果类型（供页面展示「导出已开始」提示时使用） */
export type ExportResult = ImportResult
