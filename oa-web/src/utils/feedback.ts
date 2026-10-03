/**
 * oa-web · 业务错误反馈
 * ----------------------------------------------------------------------------
 * 背景（`api/http.ts` 的既有约定）：
 *   · HTTP 403 / 409 / 429 / 5xx 由响应拦截器**统一弹 toast**（含 traceId）；
 *   · 但「HTTP 200 + 业务码 ≠ 0」这一支（网关统一错误体的常规形态）只抛 `ApiError`，
 *     **不弹提示**——若调用方 catch 里什么都不做，用户会以为操作成功。
 *
 * 危险操作尤其不能静默：AC-12 的离职拦截、E-ORG-010 的停用拦截、E-LEAD-004 的
 * 正职唯一性冲突都是这种「200 + 业务码」形态，提示文案里还带着待办数量与单号。
 *
 * 因此约定：写操作的 catch 一律调用 `reportApiError`，由本函数决定是否需要补提示
 * （已经由拦截器提示过的 4xx/5xx 不再重复提示，避免同一个错误弹两次）。
 */
import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/http'

/**
 * 补一次业务错误提示。
 * @param error  catch 到的异常
 * @param action 动作名（可选），用于拼「{{动作}}失败：原因」；省略时为「操作失败：原因」
 */
export function reportApiError(error: unknown, action = '操作'): void {
  const apiError = error instanceof ApiError ? error : null
  const status = apiError?.httpStatus ?? 0

  // 4xx / 5xx 已由 api/http.ts 的拦截器统一提示（含 traceId），这里不重复
  if (status >= 400) return

  const detail = apiError?.message || (error instanceof Error ? error.message : '') || '未知错误'
  const trace = apiError?.traceId ? `（追踪号 ${apiError.traceId}）` : ''
  ElMessage({
    type: 'error',
    message: `${action}失败：${detail}${trace}`,
    duration: 5000,
    showClose: true,
  })
}

/**
 * 带**业务码**的错误提示（流程设计器专用）。
 *
 * <p>与 `reportApiError` 的差别：
 *   1. 文案里带上稳定业务码（`40008` 主干不可删 / `40906` 已发布只读 / `40907` 草稿已存在 /
 *      `40001` 参数校验 …），便于流程管理员按码对照文档，也便于报障时一句话说清；
 *   2. **不跳过 4xx**：`api/flow.ts` 对本域请求关闭了拦截器 toast（`notify.* = false`），
 *      由本函数做**唯一**呈现方，避免同一个错误弹两次。
 *
 * <p>因此调用方必须在 catch 里调用它——否则用户看不到任何错误。
 */
export function reportApiErrorWithCode(error: unknown, action = '操作'): void {
  const apiError = error instanceof ApiError ? error : null
  const code = apiError?.code
  const codeText = code === undefined || code === null || code === '' ? '' : `［业务码 ${code}］`
  const detail = apiError?.message || (error instanceof Error ? error.message : '') || '未知错误'
  const trace = apiError?.traceId ? `（追踪号 ${apiError.traceId}）` : ''
  ElMessage({
    type: 'error',
    message: `${action}失败${codeText}：${detail}${trace}`,
    duration: 6000,
    showClose: true,
  })
}
