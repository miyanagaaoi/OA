/**
 * oa-web · HTTP 客户端
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/tech-design.md` §2（/api/v1/** 经网关）、§5.3（数据域由服务端强制过滤）、
 *     §6（安全：全站 HTTPS + 会话 Cookie HttpOnly/Secure/SameSite=Lax）
 *   · `normify-oa/modules/oa/integration/gateway/**`（统一错误体、限流、幂等、追踪）
 *
 * 职责：
 *   1. 统一响应解包：{ code, message, data, traceId } → data
 *   2. 401 → 清理会话并跳登录（带 redirect 回跳）
 *   3. 403 / 409 → 就地提示（无数据权限 / 状态已变更）
 *   4. traceId 透出：每个响应都记下 traceId，出错时随提示一起展示
 *   5. 会话 Cookie：withCredentials = true（HttpOnly，前端不读 token）
 *   6. 幂等：写操作带 Idempotency-Key（gateway/idempotency）
 *   7. 结构化错误明细：响应体的 `details`（当前唯一已登记键是 40011 的 `errors[]`）
 *      随 `ApiError#details` 透出；缺失时调用方仍走 message 文本兜底
 *
 * ── 2026-10-05 · 附件（阶段 2b.7）带来的两处**最小扩展**（不改变任何既有请求的行为）──
 *   ① `postMultipart()`：multipart 上传。**必须**逐请求覆盖实例默认的
 *      `Content-Type: application/json` —— axios 1.x 的 `transformRequest` 在
 *      「声明 JSON 且 data 是 FormData」时会把 FormData **序列化成 JSON 对象**
 *      （文件直接丢失），且缺 boundary 的 multipart 服务端也解析不了。
 *      改为 `multipart/form-data` 后，浏览器环境由 axios 交还浏览器自动补 boundary。
 *      同时把超时抬到 {@link MULTIPART_TIMEOUT_MS}：默认 30s 对 50MB 附件不够，
 *      超时会让用户以为「上传失败」，而服务端其实已经落库（重复上传）。
 *   ② `getBlob()`：鉴权下载/预览（响应体是**二进制流**，不是统一响应体）。
 *      走 `responseType: 'blob'` + `unwrap: false` 取出响应头（`Content-Disposition` /
 *      `Content-Type` 是服务端对内联与否的裁决结果，前端必须读它而不是自己猜）。
 *      失败响应的 body 此时也是 Blob，因此错误拦截器**增加**一段：把 Blob 读成文本
 *      再按统一响应体解析，否则 40402 这类错误的服务端文案会退化成
 *      「Request failed with status code 404」而被吞掉。
 */
import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiEnvelope, ApiErrorDetails, ApiErrorPayload } from '@/types/api'

/** 统一业务错误：调用方可按 code 分支，也可读 traceId 报障 */
export class ApiError extends Error {
  readonly code: number | string
  readonly traceId?: string
  readonly httpStatus?: number
  /**
   * 服务端声明的**结构化**错误明细（当前唯一已登记键是 `errors`，`40011` 表单二次校验）。
   *
   * <p>为什么必须带上：`40011` 此前只有一个把全部逐字段文案拼在一起的长 message，
   * 前端得靠正则从文本里还原字段码；现在服务端把 `FormValidationReport#issueViews()`
   * 原样放进 `details.errors[]`（与干跑 `report.issues[]` 同源），前端可以直接逐字段挂载。
   * `details` 缺失（老响应 / 其它错误码）时**必须**保留文本兜底路径。
   */
  readonly details?: ApiErrorDetails

  constructor(payload: ApiErrorPayload) {
    super(payload.message)
    this.name = 'ApiError'
    this.code = payload.code
    this.traceId = payload.traceId
    this.httpStatus = payload.httpStatus
    this.details = payload.details
  }

  toPayload(): ApiErrorPayload {
    return {
      code: this.code,
      message: this.message,
      traceId: this.traceId,
      httpStatus: this.httpStatus,
      details: this.details,
    }
  }
}

// ---------------------------------------------------------------------------
// 追踪 ID：请求头带上前端生成的 traceId，服务端回填后由响应头/响应体透出
// ---------------------------------------------------------------------------
let lastTraceId = ''

/** 最近一次请求的 traceId（审计场景：把 traceId 展示给用户以便报障） */
export function getLastTraceId(): string {
  return lastTraceId
}

function newTraceId(): string {
  const rnd = Math.random().toString(16).slice(2, 10)
  return `web-${Date.now().toString(36)}-${rnd}`
}

let traceCounter = 0

function nextIdempotencyKey(): string {
  traceCounter += 1
  return `${Date.now().toString(36)}-${traceCounter}-${Math.random().toString(36).slice(2, 8)}`
}

// ---------------------------------------------------------------------------
// 未授权处理：由 main.ts 注入，避免 http ↔ router ↔ store 三向循环依赖
// ---------------------------------------------------------------------------
type UnauthorizedHandler = (reason: 'expired' | 'anonymous') => void
let onUnauthorized: UnauthorizedHandler = () => {}

export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  onUnauthorized = handler
}

// ---------------------------------------------------------------------------
// 提示策略：403/409/429/5xx 统一走 ElMessage（DESIGN.md notification-toast）
// ---------------------------------------------------------------------------
export interface NotifyPolicy {
  /** 403 无数据权限：是否就地提示 */
  forbidden: boolean
  /** 409 状态冲突（乐观锁 / 状态已变更） */
  conflict: boolean
  /** 429 限流 */
  rateLimited: boolean
  /** 5xx 服务端异常 */
  serverError: boolean
}

const defaultNotify: NotifyPolicy = {
  forbidden: true,
  conflict: true,
  rateLimited: true,
  serverError: true,
}

/** 单次请求可覆盖提示策略（如列表预检不希望弹 toast） */
export interface OaRequestConfig extends AxiosRequestConfig {
  notify?: Partial<NotifyPolicy>
  /** 是否把非 0 业务码包装成 ApiError（默认 true） */
  unwrap?: boolean
}

function notifyWithTrace(message: string, traceId: string, type: 'error' | 'warning' = 'error') {
  const suffix = traceId ? `（追踪号 ${traceId}）` : ''
  ElMessage({ type, message: `${message}${suffix}`, duration: type === 'error' ? 5000 : 3000, showClose: true })
}

// ---------------------------------------------------------------------------
// axios 实例
// ---------------------------------------------------------------------------
const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 30_000,
  // 会话 Cookie 由服务端下发（HttpOnly + Secure + SameSite=Lax），前端只负责携带
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
    Accept: 'application/json',
  },
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const traceId = newTraceId()
  lastTraceId = traceId
  config.headers.set('X-Trace-Id', traceId)

  // 写操作统一带幂等键（gateway/idempotency）：网络重试不会产生重复审批
  const method = (config.method || 'get').toLowerCase()
  if (method === 'post' || method === 'put' || method === 'patch' || method === 'delete') {
    if (!config.headers.has('Idempotency-Key')) {
      config.headers.set('Idempotency-Key', nextIdempotencyKey())
    }
  }

  return config
})

http.interceptors.response.use(
  (response: AxiosResponse): AxiosResponse => {
    const headerTrace =
      (response.headers['x-trace-id'] as string | undefined) ||
      (response.headers['trace-id'] as string | undefined)
    if (headerTrace) lastTraceId = headerTrace

    const cfg = response.config as OaRequestConfig
    const body = response.data as ApiEnvelope<unknown> | unknown

    if (cfg.unwrap === false) {
      return response
    }

    // 无包装（如文件流、或后端直接返回对象）时原样透传
    if (!body || typeof body !== 'object' || !('code' in (body as Record<string, unknown>))) {
      return body as AxiosResponse
    }

    const env = body as ApiEnvelope<unknown>
    if (env.traceId) lastTraceId = env.traceId

    const ok = env.code === 0 || env.code === '0' || env.code === 'SUCCESS'
    if (ok) {
      return env.data as AxiosResponse
    }

    throw new ApiError({
      code: env.code,
      message: env.message || '请求失败',
      traceId: env.traceId || lastTraceId,
      httpStatus: response.status,
      details: env.details,
    })
  },
  (error: AxiosError<ApiEnvelope<unknown>>): Promise<never> => {
    return handleError(error)
  },
)

/**
 * 错误响应体归一：普通 JSON 直接用；`responseType: 'blob'` 的请求失败时
 * body 是 `Blob`，必须读成文本再解析，否则服务端文案（如 40402「附件不存在或无权访问」）
 * 会被 axios 的通用 message 顶掉。
 *
 * <p>解析失败（非 JSON / 空体）时返回 `undefined` —— 调用方仍走
 * `error.message` 兜底，绝不因为「读不出明细」而把错误吞掉。
 */
async function readErrorBody(data: unknown): Promise<ApiEnvelope<unknown> | undefined> {
  if (data === null || data === undefined) return undefined
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      const text = await data.text()
      const parsed: unknown = JSON.parse(text)
      if (parsed !== null && typeof parsed === 'object') return parsed as ApiEnvelope<unknown>
    } catch {
      return undefined
    }
    return undefined
  }
  if (typeof data === 'object') return data as ApiEnvelope<unknown>
  return undefined
}

/** 统一错误处理（含可选的中间拦截 toast 策略，见 `NotifyPolicy`） */
async function handleError(error: AxiosError<ApiEnvelope<unknown>>): Promise<never> {
  const cfg = (error.config || {}) as OaRequestConfig
  const policy: NotifyPolicy = { ...defaultNotify, ...(cfg.notify || {}) }
  const status = error.response?.status ?? 0
  const body = await readErrorBody(error.response?.data)
  const rawHeaders = (error.response?.headers || {}) as Record<string, unknown>
  const headerTrace =
    (rawHeaders['x-trace-id'] as string | undefined) ||
    (rawHeaders['trace-id'] as string | undefined)
  const traceId = body?.traceId || headerTrace || lastTraceId

  if (traceId) lastTraceId = traceId

  let message = body?.message || error.message || '网络异常，请稍后重试'

  if (status === 401) {
    // 会话失效 / 未登录：不弹 toast，直接交给路由守卫跳登录
    onUnauthorized(body?.code === 'SESSION_REVOKED' ? 'expired' : 'anonymous')
  } else if (status === 403 && policy.forbidden) {
    message = body?.message || '无权访问该数据（数据域外）'
    notifyWithTrace(message, traceId)
  } else if (status === 409 && policy.conflict) {
    message = body?.message || '单据状态已变更，请刷新后重试'
    notifyWithTrace(message, traceId, 'warning')
  } else if (status === 429 && policy.rateLimited) {
    message = body?.message || '操作过于频繁，请稍后再试'
    notifyWithTrace(message, traceId, 'warning')
  } else if (status >= 500 && policy.serverError) {
    message = body?.message || '服务暂时不可用，请稍后重试'
    notifyWithTrace(message, traceId)
  }

  throw new ApiError({
    code: body?.code ?? status ?? 'NETWORK_ERROR',
    message,
    traceId,
    httpStatus: status,
    details: body?.details,
  })
}

export default http

/** 便捷方法：泛型化后的 get/post，返回值已解包 */
export async function get<T>(url: string, config?: OaRequestConfig): Promise<T> {
  return (await http.get(url, config)) as unknown as T
}

export async function post<T>(url: string, data?: unknown, config?: OaRequestConfig): Promise<T> {
  return (await http.post(url, data, config)) as unknown as T
}

export async function put<T>(url: string, data?: unknown, config?: OaRequestConfig): Promise<T> {
  return (await http.put(url, data, config)) as unknown as T
}

export async function del<T>(url: string, config?: OaRequestConfig): Promise<T> {
  return (await http.delete(url, config)) as unknown as T
}

// ---------------------------------------------------------------------------
// 附件（阶段 2b.7）：multipart 上传 / 鉴权二进制下载
// ---------------------------------------------------------------------------

/**
 * multipart 请求的超时（附件最大 50MB）。
 *
 * <p>默认 30s 对 50MB 上传偏紧：一旦超时，**服务端可能已经落库**，用户只会看到
 * 「上传失败」然后重传（重复附件）。这里给足 5 分钟，并在 UI 上如实说明
 * 「上传中请勿关闭页面」。
 */
export const MULTIPART_TIMEOUT_MS = 300_000

/**
 * multipart POST（`Content-Type: multipart/form-data`）。
 *
 * <p><b>为什么必须在封装里覆盖 Content-Type</b>：实例默认头是
 * `application/json;charset=UTF-8`，而 axios 1.x 的 `transformRequest` 遇到
 * 「Content-Type 含 application/json ∧ data 是 FormData」时会走
 * `JSON.stringify(formDataToJSON(data))` —— **文件被丢掉、只剩一个 JSON 对象**。
 * 覆盖成 multipart 后 axios 在浏览器环境会 `setContentType(false)`，
 * 交由浏览器补 `boundary`（自己拼 boundary 必然错）。
 *
 * <p>调用方只需传 `FormData`，**不要**自己设置 Content-Type。
 */
export async function postMultipart<T>(
  url: string,
  form: FormData,
  config?: OaRequestConfig,
): Promise<T> {
  const merged: OaRequestConfig = {
    ...config,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: config?.timeout ?? MULTIPART_TIMEOUT_MS,
  }
  return (await http.post(url, form, merged)) as unknown as T
}

/** 二进制响应（原始 body + 响应头；`Content-Disposition` 是服务端的裁决结果） */
export interface BlobResponse {
  blob: Blob
  headers: Record<string, unknown>
  status: number
}

/**
 * 取二进制内容（鉴权下载 / 预览）。
 *
 * <p>三处要点：
 * <ol>
 *   <li>`responseType: 'blob'`：响应体是二进制流，**没有**统一响应体外壳；</li>
 *   <li>`unwrap: false`：保留 `AxiosResponse`，否则拦截器会把非响应体外壳的 body
 *       直接返回，`Content-Disposition` / `Content-Type` 就丢了；</li>
 *   <li>出错时仍走同一套拦截器（Blob 错误体由 {@link readErrorBody} 还原成服务端文案）。</li>
 * </ol>
 */
export async function getBlob(url: string, config?: OaRequestConfig): Promise<BlobResponse> {
  const merged: OaRequestConfig = {
    ...config,
    responseType: 'blob',
    unwrap: false,
  }
  const response = (await http.get(url, merged)) as unknown as AxiosResponse<Blob>
  return {
    blob: response.data,
    headers: (response.headers ?? {}) as unknown as Record<string, unknown>,
    status: response.status,
  }
}

/** 是否开启离线演示（阶段 1 骨架：后端未就绪时页面仍可渲染） */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/**
 * 演示降级：接口不可用时回落到内置演示数据，并把 traceId 一并带出。
 * 生产环境（VITE_USE_MOCK=false）必须严格抛出，不允许静默吞错。
 */
export async function withDemoFallback<T>(
  task: () => Promise<T>,
  fallback: () => T,
): Promise<T> {
  try {
    return await task()
  } catch (error) {
    if (!USE_MOCK) throw error
    // eslint-disable-next-line no-console
    console.warn('[oa-web] 接口不可用，使用演示数据渲染：', (error as Error).message)
    return fallback()
  }
}
