/**
 * oa-web · 用户会话 Store
 * ----------------------------------------------------------------------------
 * 来源：`doc/tech-design.md` §5.3（数据域解析）、§6（安全与会话）
 *       + `normify-oa/modules/oa/authz/scope/**`、`oa.sign.preset/**`
 *
 * 持有：当前用户、角色、数据域与归口类别、权限树勾选结果、签名预存标记、
 *       水印配置（REQ-USER-004）、多设备会话（REQ-USER-003）。
 * 约束：会话令牌存放在 HttpOnly Cookie 里，前端**不持有 token**——
 *       本 store 只缓存"用户画像"，刷新后由 /auth/me 重建。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchClientConfig,
  fetchCurrentUser,
  fetchWatermarkProfile,
  listSessions,
  login as loginApi,
  logout as logoutApi,
  revokeSession as revokeSessionApi,
} from '@/api/auth'
import { demoClientConfig, demoWatermarkProfile } from '@/api/demo'
import { clampWatermarkOpacity } from '@/utils/watermark'
import type {
  CategoryCode,
  ClientConfig,
  CurrentUser,
  DataScope,
  LoginRequest,
  SessionDevice,
  WatermarkProfile,
} from '@/types/api'

/**
 * 水印透明度必须收敛在 5%–8%（AC-44），任何来源越界都夹回区间。
 *
 * 实现已抽到 `src/utils/watermark.ts`（`api/auth.ts` 的映射层也要用同一份常量与规则）；
 * 这里**原样再导出**，保持既有的 `import { clampWatermarkOpacity } from '@/stores/user'` 可用。
 */
export { clampWatermarkOpacity }

export const useUserStore = defineStore('user', () => {
  // ---- state ----
  const user = ref<CurrentUser | null>(null)
  const sessions = ref<SessionDevice[]>([])
  const clientConfig = ref<ClientConfig>(demoClientConfig)
  const watermark = ref<WatermarkProfile | null>(null)
  /** 用户手动调整的透明度（覆盖服务端区间中值），永远夹在 5%–8% 内 */
  const watermarkOpacityOverride = ref<number | null>(null)
  const loading = ref(false)
  const loaded = ref(false)
  /** 会话失效原因，登录页据此提示 */
  const sessionExpired = ref(false)

  // ---- getters ----
  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => user.value?.name ?? '')
  const employeeNo = computed(() => user.value?.employeeNo ?? '')
  const orgName = computed(() => user.value?.orgName ?? '')
  const roles = computed(() => user.value?.roles ?? [])
  const permissions = computed(() => user.value?.permissions ?? [])
  const dataScopes = computed<DataScope[]>(() => user.value?.dataScopes ?? [])
  const categories = computed<CategoryCode[]>(() => user.value?.categories ?? [])
  /** 签名预存标记（oa.sign.preset）：审批需签名时用于提示"使用预存签名" */
  const signaturePresetReady = computed(() => user.value?.signaturePresetReady ?? false)
  const isSuperAdmin = computed(() => user.value?.isSuperAdmin ?? false)

  /** 水印文本：姓名 + 工号 */
  const watermarkText = computed(() => {
    if (watermark.value) return watermark.value.text
    if (!user.value) return ''
    return `${user.value.name} · ${user.value.employeeNo}`
  })

  const watermarkEnabled = computed(
    () => watermark.value?.enabled ?? user.value?.watermarkEnabled ?? true,
  )

  const watermarkOpacity = computed(() => {
    if (watermarkOpacityOverride.value !== null) {
      return clampWatermarkOpacity(watermarkOpacityOverride.value)
    }
    if (watermark.value) {
      // 服务端下发的是**单一真值** `opacity`（已夹到 5%–8%），优先用它；
      // 旧口径的区间中值只在没有单值时兜底（避免「服务端 0.05、页面显示 0.065」的偏差）。
      return clampWatermarkOpacity(
        watermark.value.opacity ?? (watermark.value.opacityMin + watermark.value.opacityMax) / 2,
      )
    }
    return clampWatermarkOpacity(user.value?.watermarkOpacity ?? 0.06)
  })

  const watermarkRotate = computed(() => watermark.value?.rotate ?? -24)

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  function hasCategory(code: CategoryCode): boolean {
    return categories.value.includes(code) || isSuperAdmin.value
  }

  // ---- actions ----
  async function login(payload: LoginRequest): Promise<void> {
    loading.value = true
    try {
      const result = await loginApi(payload)
      user.value = result.user
      sessionExpired.value = false
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 刷新页面后重建会话画像（Cookie 仍在则成功，401 由 http.ts 统一处理） */
  async function hydrate(force = false): Promise<CurrentUser | null> {
    if (loaded.value && !force && user.value) return user.value
    loading.value = true
    try {
      user.value = await fetchCurrentUser()
      loaded.value = true
      sessionExpired.value = false
      return user.value
    } catch {
      user.value = null
      loaded.value = true
      return null
    } finally {
      loading.value = false
    }
  }

  async function hydrateWatermark(): Promise<void> {
    try {
      watermark.value = await fetchWatermarkProfile()
    } catch {
      watermark.value = null
    }
  }

  async function hydrateSessions(): Promise<void> {
    try {
      sessions.value = await listSessions()
    } catch {
      sessions.value = []
    }
  }

  async function hydrateClientConfig(): Promise<void> {
    try {
      clientConfig.value = await fetchClientConfig()
    } catch {
      clientConfig.value = demoClientConfig
    }
  }

  async function revokeSession(sessionId: string): Promise<void> {
    await revokeSessionApi(sessionId)
    sessions.value = sessions.value.filter((item) => item.sessionId !== sessionId)
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 退出接口失败也必须清空本地画像，避免残留他人信息
    } finally {
      reset()
    }
  }

  function reset(): void {
    user.value = null
    sessions.value = []
    watermark.value = null
    watermarkOpacityOverride.value = null
    loaded.value = false
  }

  /** 切换水印开关（个人偏好；服务端持久化留待阶段 3 的偏好接口） */
  function toggleWatermark(enabled: boolean): void {
    if (watermark.value) {
      watermark.value = { ...watermark.value, enabled }
    } else {
      watermark.value = { ...demoWatermarkProfile, enabled }
    }
    if (user.value) user.value.watermarkEnabled = enabled
  }

  function setWatermarkOpacity(opacity: number): void {
    watermarkOpacityOverride.value = clampWatermarkOpacity(opacity)
  }

  function markSessionExpired(): void {
    sessionExpired.value = true
  }

  return {
    // state
    user,
    sessions,
    clientConfig,
    watermark,
    watermarkOpacityOverride,
    loading,
    loaded,
    sessionExpired,
    // getters
    isAuthenticated,
    displayName,
    employeeNo,
    orgName,
    roles,
    permissions,
    dataScopes,
    categories,
    signaturePresetReady,
    isSuperAdmin,
    watermarkText,
    watermarkEnabled,
    watermarkOpacity,
    watermarkRotate,
    // actions
    hasPermission,
    hasCategory,
    login,
    hydrate,
    hydrateWatermark,
    hydrateSessions,
    hydrateClientConfig,
    revokeSession,
    logout,
    reset,
    toggleWatermark,
    setWatermarkOpacity,
    markSessionExpired,
  }
})
