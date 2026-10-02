/**
 * oa-web · 鉴权与身份接口
 * ----------------------------------------------------------------------------
 * 接口路径与 `normify-oa/api-index.json` 契约逐条对齐：
 *   POST   /api/v1/auth/login                    登录
 *   POST   /api/v1/auth/logout                   退出
 *   GET    /api/v1/auth/me                       当前用户 + 角色 + 数据域
 *   POST   /api/v1/auth/remember                 「记住我」续期（REQ-USER-002）
 *   POST   /api/v1/auth/refresh                  会话续期
 *   GET    /api/v1/auth/lock-status              失败锁定状态（5 次 / 15 分钟）
 *   POST   /api/v1/auth/unlock                   解锁（管理员兜底）
 *   GET    /api/v1/auth/password-policy          口令策略
 *   GET    /api/v1/auth/client-config            客户端运行期配置
 *   GET    /api/v1/auth/sessions                 多设备会话列表（REQ-USER-003）
 *   DELETE /api/v1/auth/sessions/{sessionId}     踢出指定设备
 *   PUT    /api/v1/auth/sessions/limit           会话上限
 *   GET    /api/v1/portal/watermark/profile      「姓名 + 工号」水印（REQ-USER-004）
 *   GET    /api/v1/portal/h5/watermark           H5 水印口径
 *   GET    /api/v1/identity/users/me/watermark   身份域水印文本
 */
import { del, get, post, put, withDemoFallback } from './http'
import { demoClientConfig, demoCurrentUser, demoLockStatus, demoPasswordPolicy, demoSessions, demoWatermarkProfile } from './demo'
import {
  WATERMARK_GAP_X,
  WATERMARK_GAP_Y,
  WATERMARK_OPACITY_DEFAULT,
  WATERMARK_ROTATE,
  clampWatermarkOpacity,
} from '@/utils/watermark'
import type { WireClientConfig, WireWatermarkProfile } from '@/types/auth-wire'
import type {
  ClientConfig,
  CurrentUser,
  LockStatus,
  LoginRequest,
  LoginResult,
  PasswordPolicy,
  SessionDevice,
  WatermarkProfile,
} from '@/types/api'

export function login(payload: LoginRequest): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload)
}

export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}

export function refreshSession(): Promise<void> {
  return post<void>('/auth/refresh')
}

/** 勾选「记住我」后 7 天免登录 */
export function rememberMe(): Promise<void> {
  return post<void>('/auth/remember')
}

/** 当前用户：角色、数据域、归口类别、签名预存标记一次取回 */
export function fetchCurrentUser(): Promise<CurrentUser> {
  return withDemoFallback(() => get<CurrentUser>('/auth/me'), () => demoCurrentUser)
}

export function fetchLockStatus(account: string): Promise<LockStatus> {
  return withDemoFallback(
    () => get<LockStatus>('/auth/lock-status', { params: { account }, notify: { serverError: false } }),
    () => ({ ...demoLockStatus, account }),
  )
}

export function unlockAccount(payload: { account: string; reason: string }): Promise<void> {
  return post<void>('/auth/unlock', payload)
}

export function fetchPasswordPolicy(): Promise<PasswordPolicy> {
  return withDemoFallback(() => get<PasswordPolicy>('/auth/password-policy'), () => demoPasswordPolicy)
}

// ---------------------------------------------------------------------------
// 客户端运行期配置（GET /auth/client-config，登录前可取）
// ---------------------------------------------------------------------------

/** `int`/`long` 计数归一：服务端把 `long` 序列化成字符串，两种都吃；非法值退回 fallback。 */
function toCount(value: unknown, fallback: number): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/**
 * 后端 DTO → 领域模型。
 *
 * 口径：
 *   · **服务端下发的值一律用真值**（不再「有字段也回退默认」——那正是权限数永远显示 0 的成因）；
 *     只有**字段缺失**（后端未实现的别名，如 `upload`）才回落到演示默认值，并在下方逐项注明；
 *   · `session.maxDevices / rememberMeDays` 摊平到顶层，保持登录页既有用法不变；
 *   · 口令阈值与 `/auth/password-policy` 同源（服务端同一批 `oa.security.*`）。
 */
export function toClientConfig(view: WireClientConfig | null | undefined): ClientConfig {
  const session = view?.session ?? null
  const password = view?.password ?? null
  const maxDevices = toCount(session?.maxDevices ?? view?.maxDevices, demoClientConfig.maxDevices)
  const rememberMeDays = toCount(
    session?.rememberMeDays ?? view?.rememberMeDays,
    demoClientConfig.rememberMeDays,
  )
  const upload = view?.upload
  return {
    title: view?.title?.trim() || demoClientConfig.title,
    env: view?.env?.trim() || demoClientConfig.env,
    apiBaseUrl: view?.apiBaseUrl?.trim() || demoClientConfig.apiBaseUrl,
    sessionCookieName: view?.sessionCookieName?.trim() || demoClientConfig.sessionCookieName,
    forceHttps: view?.forceHttps ?? demoClientConfig.forceHttps,
    watermarkOpacity: clampWatermarkOpacity(
      toCount(view?.watermarkOpacity, demoClientConfig.watermarkOpacity),
    ),
    session: { maxDevices, rememberMeDays },
    password: {
      minLength: toCount(password?.minLength, demoClientConfig.password.minLength),
      requireLetter: password?.requireLetter ?? demoClientConfig.password.requireLetter,
      requireDigit: password?.requireDigit ?? demoClientConfig.password.requireDigit,
      lockThreshold: toCount(password?.lockThreshold, demoClientConfig.password.lockThreshold),
      lockMinutes: toCount(password?.lockMinutes, demoClientConfig.password.lockMinutes),
    },
    maxDevices,
    rememberMeDays,
    /*
     * `upload` 是**后端契约里没有**的一组上限（当前实现只下发会话/口令/水印等项）。
     * 这里回落到演示默认值（50MB / 20 / 50），并挂账在 `types/auth-wire.d.ts` 的注释里：
     * 后端一旦下发，`view.upload` 有值即自动采用真值（上面的 `??` 顺序已经保证）。
     */
    upload: {
      maxFileSizeMb: toCount(upload?.maxFileSizeMb, demoClientConfig.upload.maxFileSizeMb),
      maxFilesPerSubmit: toCount(upload?.maxFilesPerSubmit, demoClientConfig.upload.maxFilesPerSubmit),
      maxFilesPerDocument: toCount(
        upload?.maxFilesPerDocument,
        demoClientConfig.upload.maxFilesPerDocument,
      ),
      allowedExtensions: upload?.allowedExtensions?.length
        ? [...upload.allowedExtensions]
        : [...demoClientConfig.upload.allowedExtensions],
    },
  }
}

export function fetchClientConfig(): Promise<ClientConfig> {
  return withDemoFallback(
    () => get<WireClientConfig>('/auth/client-config').then(toClientConfig),
    () => demoClientConfig,
  )
}

export function listSessions(): Promise<SessionDevice[]> {
  return withDemoFallback(() => get<SessionDevice[]>('/auth/sessions'), () => demoSessions)
}

/** 软踢出：写 revoked_at，不物理删除（tech-design §6 会话） */
export function revokeSession(sessionId: string): Promise<void> {
  return del<void>(`/auth/sessions/${sessionId}`)
}

export function updateSessionLimit(maxDevices: number): Promise<void> {
  return put<void>('/auth/sessions/limit', { maxDevices })
}

// ---------------------------------------------------------------------------
// 水印（REQ-USER-004 / AC-44）
// ---------------------------------------------------------------------------
/**
 * 后端 DTO → 领域模型。
 *
 * 服务端只给单一 `opacity`（已夹到 5%–8%）与拼好的 `text`；这里
 *   · `text` 用真值；缺失时按「姓名 + 工号」自行拼接（与后端 `WatermarkPolicy#text` 同规则）；
 *   · `opacity` 用真值并夹紧，同时写入 `opacityMin`/`opacityMax`，
 *     使「取区间中值」的旧逻辑恰好等于服务端值（不再出现服务端 0.05、页面显示 0.065 的偏差）；
 *   · `rotate`/`gapX`/`gapY` 是 DESIGN.md 设计常量（服务端契约没有这三个字段）；
 *   · `enabled` 缺失时按「开」处理（服务端默认 `oa.watermark.enabled=true`）。
 */
export function toWatermarkProfile(view: WireWatermarkProfile | null | undefined): WatermarkProfile {
  const name = (view?.name ?? '').trim()
  // 工号：服务端 non_null 口径下键会整个消失 → 归一为空串（页面按「工号缺失」处理）
  const employeeNo = (view?.employeeNo ?? '').trim()
  const rawOpacity = view?.opacity ?? ((view?.opacityMin ?? NaN) + (view?.opacityMax ?? NaN)) / 2
  const opacity = Number.isFinite(rawOpacity as number)
    ? clampWatermarkOpacity(rawOpacity as number)
    : WATERMARK_OPACITY_DEFAULT
  return {
    text: view?.text?.trim() || [name, employeeNo].filter(Boolean).join(' '),
    name,
    employeeNo,
    opacity,
    // 服务端单值口径：区间上下沿都取同一个值（中值 = 服务端值）
    opacityMin: clampWatermarkOpacity(view?.opacityMin ?? opacity),
    opacityMax: clampWatermarkOpacity(view?.opacityMax ?? opacity),
    rotate: view?.rotate ?? WATERMARK_ROTATE,
    gapX: view?.gapX ?? WATERMARK_GAP_X,
    gapY: view?.gapY ?? WATERMARK_GAP_Y,
    enabled: view?.enabled ?? true,
  }
}

export function fetchWatermarkProfile(): Promise<WatermarkProfile> {
  return withDemoFallback(
    () => get<WireWatermarkProfile>('/portal/watermark/profile').then(toWatermarkProfile),
    () => demoWatermarkProfile,
  )
}

export function fetchH5Watermark(): Promise<WatermarkProfile> {
  return withDemoFallback(
    () => get<WireWatermarkProfile>('/portal/h5/watermark').then(toWatermarkProfile),
    () => demoWatermarkProfile,
  )
}

export function fetchMyWatermark(): Promise<WatermarkProfile> {
  return withDemoFallback(
    () => get<WireWatermarkProfile>('/identity/users/me/watermark').then(toWatermarkProfile),
    () => demoWatermarkProfile,
  )
}
