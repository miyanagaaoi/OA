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

export function fetchClientConfig(): Promise<ClientConfig> {
  return withDemoFallback(() => get<ClientConfig>('/auth/client-config'), () => demoClientConfig)
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
export function fetchWatermarkProfile(): Promise<WatermarkProfile> {
  return withDemoFallback(
    () => get<WatermarkProfile>('/portal/watermark/profile'),
    () => demoWatermarkProfile,
  )
}

export function fetchH5Watermark(): Promise<WatermarkProfile> {
  return withDemoFallback(() => get<WatermarkProfile>('/portal/h5/watermark'), () => demoWatermarkProfile)
}

export function fetchMyWatermark(): Promise<WatermarkProfile> {
  return withDemoFallback(
    () => get<WatermarkProfile>('/identity/users/me/watermark'),
    () => demoWatermarkProfile,
  )
}
