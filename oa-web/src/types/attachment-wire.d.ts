/**
 * oa-web · 后端附件域 DTO 镜像（wire 层）—— 阶段 2b.7
 * ----------------------------------------------------------------------------
 * 逐字段对齐 oa-server 的
 *   · `com.oa.form.attachment.api.AttachmentController`（5 条路由）
 *   · `com.oa.form.attachment.app.AttachmentService#upload / #list / #delete`
 *   · `com.oa.form.attachment.domain.Attachment#view()`（附件出参）
 *   · `AttachmentService#boundsView`（生效档位出参）
 *
 * 路由清单（入口闸门以服务端为准）：
 *   POST   /api/v1/forms/instances/{id}/attachments              `flow`（multipart：files + 可选 fieldCode）
 *   GET    /api/v1/forms/instances/{id}/attachments              `flow` ∪ `admin:flow`（按 round 分组）
 *   GET    /api/v1/forms/attachments/{id}/download               `flow` ∪ `admin:flow`（恒 attachment）
 *   GET    /api/v1/forms/attachments/{id}/preview                `flow` ∪ `admin:flow`（仅安全类型内联）
 *   DELETE /api/v1/forms/attachments/{id}                        `flow`
 *
 * ⚠ **删除与上传的身份口径不同**（2026-10-05 裁定，服务端同源）：
 *   上传 = 发起人本人或系统管理员（`requireInitiatorOrAdmin`）；
 *   删除 = **上传者本人 ∪ 单据发起人本人 ∪ 系统管理员**，且同样限草稿/待补件窗口。
 *   取「发起人本人」的理由：单据归发起人，管理员**代传**后本人若不能删，
 *   等于代传剥夺了本人的处置权 —— 代传只应「多一个能删的人」。
 *   前端判定见 `utils/attachment.ts#canDeleteAttachment`（只决定渲不渲染入口），
 *   被拒时 `40310` 的**服务端原文照旧展示**。
 *
 * ⚠ 序列化口径（与 `types/form-wire.d.ts` / `types/flow-wire.d.ts` 一致）：
 *   `com.oa.common.config.JacksonConfig` 对 `Long`/`long` 注册了 `ToStringSerializer`，
 *   因此 **id 与 `fileSize` 在 JSON 里都是字符串**（实测 `"fileSize":"70"`）；
 *   `Integer`（`round` / `total` / `fieldCount` / `instanceCount` / `bounds.maxCount`…）
 *   仍是 JSON number。
 *
 * ⚠⚠ 空值口径：`spring.jackson.default-property-inclusion: non_null`，因此可空键
 *   （`mimeType` / `sha256` / `uploaderId` / `createdAt` / `taskId`）在后端为 null 时
 *   **整个键被省略**；本文件一律写成 `?: T | null`，映射层（`api/attachment.ts`）
 *   必须把 `undefined` 归一为领域模型里的 `null` / 空串。
 *
 * ⚠ `view()` **刻意不含 `storagePath`**（`doc/test-cases.md` TC-FORM-024：
 *   已知 storage_path 也无法直连）——本镜像同样不声明该键，前端拿不到也不该依赖它。
 */
import type { WireId } from './identity-wire'

export type { WireId }

/**
 * `Attachment#view()` —— 单个附件出参（清单、上传回显、删除回显共用同一形状）。
 *
 * `downloadUrl` / `previewUrl` 是服务端拼好的**鉴权接口**相对路径
 * （`/api/v1/forms/attachments/{id}/download|preview`）：前端按它拼基址调用即可，
 * **不要**自己拼直链、更不要试图访问存储路径。
 */
export interface WireAttachmentView {
  id: WireId
  instanceId: WireId
  /** `0` = 原始附件，`1..3` = 第 N 次补件（`doc/enums.md` §12.3）；`Integer` → JSON number */
  round?: number | null
  /** 附件所属字段（缺省 `attachments`） */
  fieldCode?: string | null
  /** 展示名（服务端已做安全化：去路径 / 去控制字符 / 限长） */
  fileName: string
  /** `Long` → JSON **字符串**（实测 `"70"`） */
  fileSize?: WireId | null
  /** 小写扩展名（无点），如 `png` */
  fileExt?: string | null
  /** 服务端**落库**的 MIME（不是客户端声明） */
  mimeType?: string | null
  /** 内容摘要（服务端落库；可用于下载后比对） */
  sha256?: string | null
  /** 上传人 id（`Long` → 字符串）；删除判据是它 */
  uploaderId?: WireId | null
  /** 上传时间（后端 `LocalDateTime` 原样输出：`2026-10-03 15:49:19`，**不是 ISO 8601**） */
  createdAt?: string | null
  /** 鉴权下载接口路径（恒 `attachment`） */
  downloadUrl: string
  /** 鉴权预览接口路径（仅 jpeg/png/pdf 且 ≤20MB 才 `inline`，其余降级） */
  previewUrl: string
}

/**
 * `AttachmentService#boundsView` —— **生效档位**（`AttachmentPolicy.Bounds` 的出参）。
 *
 * 这是服务端 `AttachmentPolicy` 判定时用的那一份（模板 `filePolicy` 已收窄过），
 * 由**上传响应**下发；清单接口不下发。前端展示它、不要自己另算一套。
 */
export interface WireAttachmentBoundsView {
  maxSizeMb: number
  maxCount: number
  maxPerUpload: number
  maxPerInstance: number
  /** 允许扩展名（服务端 `TreeSet` 字典序） */
  allowExt: string[]
  /** 禁止扩展名（**优先于白名单**） */
  denyExt: string[]
}

/** `AttachmentService#upload` 出参。 */
export interface WireAttachmentUploadView {
  instanceId: WireId
  fieldCode: string
  round: number
  /** 本次真正落库的附件（服务端「先全量校验、再落盘落库」，因此失败时这里是空的） */
  uploaded: WireAttachmentView[]
  /** 该字段累计数量（含本次） */
  fieldCount: number
  /** 该单据累计数量（含本次，含补件） */
  instanceCount: number
  bounds: WireAttachmentBoundsView
  evidence: string
}

/** `AttachmentService#list` 出参（按 round 分组的清单，**不含存储路径**）。 */
export interface WireAttachmentListView {
  instanceId: WireId
  bizNo: string
  total: number
  /**
   * `Map<Integer, List<...>>`：键是 round 的**十进制字符串**（`"0"` / `"1"`…）。
   * 无附件时是空对象 `{}`。
   */
  rounds?: Record<string, WireAttachmentView[]> | null
  evidence: string
}

/** `AttachmentService#delete` 出参（附件视图 + 两个删除事实）。 */
export interface WireAttachmentDeleteView extends WireAttachmentView {
  deleted: boolean
  /** 物理文件是否已确认移除（服务端先删文件再删元数据） */
  physicalFileRemoved: boolean
}
