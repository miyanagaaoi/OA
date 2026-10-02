/**
 * oa-web · 后端 identity 域（鉴权/会话/门户水印）DTO 的 wire 镜像
 * ----------------------------------------------------------------------------
 * 契约来源：`oa-server` 的实际实现（**字段名以服务端为准**）
 *   · `com.oa.identity.api.dto.AuthDtos.ClientConfigResponse` →
 *     `GET /api/v1/auth/client-config`（normify `oa.identity.session.client`，登录前可取）
 *   · `com.oa.portal.api.dto.PortalDtos.WatermarkProfileView` →
 *     `GET /api/v1/portal/watermark/profile`（normify `oa.portal.detail.watermark`）
 *
 * 与 `types/authz-wire.d.ts` / `types/identity-wire.d.ts` 同一约定：
 *   本层只描述**服务端真实下发的形状**（含可选别名），领域模型在 `types/api.d.ts`，
 *   映射在 `api/auth.ts`；页面只依赖领域模型，后端调字段名时只改映射层。
 *
 * ⚠ 序列化口径（`com.oa.common.config.JacksonConfig`）：`Long`/`long`/`BigInteger`
 *   一律序列化为**字符串**，`int`/`Integer`/`double` 保持 JSON number。
 *   因此 `session.rememberMeDays` 在服务端刻意声明为 `int`（见
 *   `AuthDtos.SessionLimits` 的 javadoc），这里按 `number` 描述；同时保留 `WireNumber`
 *   兜底，将来若被改回 `long`（字符串）映射层也不会静默变成 `NaN`。
 *
 * ⚠ 服务端 `spring.jackson.default-property-inclusion=non_null`：为 null 的字段**不会出现**
 *   在 JSON 里（例如工号缺失时 `employeeNo` 整个键消失），因此这里一律写成可选。
 */
import type { WireNumber } from './identity-wire'

export type { WireNumber }

/**
 * `GET /api/v1/auth/client-config` 的出参。
 *
 * 服务端只下发**非敏感**运行期配置；**不得**在此扩展出数据源/Redis/口令哈希成本等字段。
 */
export interface WireClientConfig {
  /** 系统标题（`oa.web.title`，与 `VITE_APP_TITLE` 对齐） */
  title?: string | null
  /** 运行环境（激活的 Spring profile，如 `dev`） */
  env?: string | null
  /** 前端统一 API 前缀（相对路径，如 `/api/v1`） */
  apiBaseUrl?: string | null
  /** 会话 Cookie 名（HttpOnly，前端只读其存在性） */
  sessionCookieName?: string | null
  /** 是否强制 HTTPS（服务端 `oa.session.cookie-secure`） */
  forceHttps?: boolean | null
  /** 水印透明度（服务端已夹紧到 0.05–0.08） */
  watermarkOpacity?: number | null
  /** 会话上限（后端嵌套口径，当前实现） */
  session?: {
    maxDevices?: WireNumber | null
    rememberMeDays?: WireNumber | null
  } | null
  /** 口令策略（后端嵌套口径，当前实现） */
  password?: {
    minLength?: WireNumber | null
    requireLetter?: boolean | null
    requireDigit?: boolean | null
    lockThreshold?: WireNumber | null
    lockMinutes?: WireNumber | null
  } | null
  /** 别名：部分实现把会话上限平铺在顶层（兼容旧口径，避免整块配置读不出来） */
  maxDevices?: WireNumber | null
  /** 别名：同上 */
  rememberMeDays?: WireNumber | null
  /**
   * 上传上限：**当前后端不下发**（不在非敏感运行期配置契约内），
   * 映射层回落到演示默认值并保留此别名，便于后端将来补齐时零改动接入。
   */
  upload?: {
    maxFileSizeMb?: WireNumber | null
    maxFilesPerSubmit?: WireNumber | null
    maxFilesPerDocument?: WireNumber | null
    allowedExtensions?: string[] | null
  } | null
}

/**
 * `GET /api/v1/portal/watermark/profile` 的出参：当前登录人的水印画像。
 *
 * 服务端形状是 **`{enabled, text, opacity, employeeNo, name}`**（单一透明度 + 文案已拼好）；
 * 前端领域模型还需要区间/旋转/间距（设计常量），由 `api/auth.ts` 的映射层补齐 ——
 * 因此下面的 `opacityMin/opacityMax/rotate/gapX/gapY` 只作为**兼容别名**保留。
 */
export interface WireWatermarkProfile {
  /** 水印开关（服务端默认口径 `oa.watermark.enabled`） */
  enabled?: boolean | null
  /** 水印文案 = 「姓名 工号」；工号缺失时退化为仅姓名 */
  text?: string | null
  /** 透明度（服务端已夹紧到 0.05–0.08） */
  opacity?: number | null
  /** 工号（`sys_user.employee_no`）；缺失时**键不存在**（non_null 口径） */
  employeeNo?: string | null
  /** 姓名 */
  name?: string | null
  /** 别名（旧口径）：透明度区间下沿 */
  opacityMin?: number | null
  /** 别名（旧口径）：透明度区间上沿 */
  opacityMax?: number | null
  /** 别名：旋转角度 */
  rotate?: number | null
  /** 别名：横向间距 */
  gapX?: number | null
  /** 别名：纵向间距 */
  gapY?: number | null
}
