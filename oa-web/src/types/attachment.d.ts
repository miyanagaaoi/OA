/**
 * oa-web · 附件域领域模型（页面只依赖本文件）
 * ----------------------------------------------------------------------------
 * 由 `api/attachment.ts` 从 `types/attachment-wire.d.ts`（后端 DTO 镜像）映射而来。
 * 映射口径：
 *   · **id 一律 string**（后端 `Long` → JSON 字符串）；
 *   · **`fileSize` 一律 number | null**（wire 层是字符串，映射层 `Number(...)`；
 *     文件大小不是金额，不存在定点精度约束，可安全转数字用于展示与 20MB 内联判定）；
 *   · 缺失的可空键统一归一为 `null`（wire 层因 `non_null` 会整键省略）；
 *   · `round` 保持 number（`0` = 原始附件，`1..3` = 第 N 次补件）。
 *
 * ⚠ 命名避让：`types/api.d.ts` 已有一个面向旧「门户详情 / 打印」演示形态的
 * `AttachmentItem`（含后端**不存在**的 `attachmentId` / `uploadedBy` 字段）。
 * 本域一律用 `AttachmentFile` 等命名，**两者不要混用**：混用会把 `attachmentId`
 * 这种不存在的键带进请求。
 */

/** 生效档位的来源（用于如实披露「这个上限是谁定的」） */
export type AttachmentBoundsSource =
  /** 服务端上传响应下发的 `bounds`（`AttachmentPolicy` 判定时用的那一份，**权威**） */
  | 'server'
  /** 模板 `filePolicy` 声明（schema `ruleDetails`），未声明项回落全局缺省 */
  | 'template'
  /** 全局缺省（`doc/forms.md` §1.4：50MB / 20 个 / 50 个 + 15 种允许 / 9 种禁止） */
  | 'global'

/**
 * 三档限额 + 双清单（生效档位）。
 *
 * `maxPerUpload` / `maxPerInstance` 是**全局档**（模板不可改），
 * `maxSizeMb` / `maxCount` / `allowExt` / `denyExt` 可被模板 `filePolicy` 收窄。
 */
export interface AttachmentBounds {
  maxSizeMb: number
  maxCount: number
  maxPerUpload: number
  maxPerInstance: number
  allowExt: string[]
  denyExt: string[]
  /** 档位来源（展示用，不参与判定） */
  source: AttachmentBoundsSource
  /** 模板 `filePolicy.message` 原文（未声明为 `null`；展示时**原样**给出） */
  declaredMessage: string | null
}

/** 单个附件（领域模型） */
export interface AttachmentFile {
  id: string
  instanceId: string
  /** 0 = 原始附件，1..3 = 第 N 次补件 */
  round: number
  fieldCode: string
  fileName: string
  /** 字节数（wire 层是字符串；非法/缺失为 `null`） */
  fileSizeBytes: number | null
  fileExt: string
  mimeType: string | null
  sha256: string | null
  /** 上传人 id（删除判据） */
  uploaderId: string
  /** 上传时间（后端原样：`2026-10-03 15:49:19`） */
  uploadedAt: string | null
  /** 鉴权下载接口路径（**不要**拼直链） */
  downloadUrl: string
  /** 鉴权预览接口路径 */
  previewUrl: string
}

/** 按 round 分组的附件组 */
export interface AttachmentRoundGroup {
  round: number
  files: AttachmentFile[]
}

/** 附件清单（`GET /forms/instances/{id}/attachments`） */
export interface AttachmentList {
  instanceId: string
  bizNo: string
  total: number
  rounds: AttachmentRoundGroup[]
  evidence: string
}

/** 上传结果（单次请求 = 服务端一次 `POST`） */
export interface AttachmentUploadResult {
  instanceId: string
  fieldCode: string
  round: number
  uploaded: AttachmentFile[]
  fieldCount: number
  instanceCount: number
  bounds: AttachmentBounds
  evidence: string
}

/**
 * 逐文件上传结论。
 *
 * <p>服务端对**一次请求**是「全量校验、任一不合格即整体拒绝」，因此「逐文件结果」
 * 只能由「逐文件分别请求」得到（见 `api/attachment.ts#uploadAttachments`）：
 * `ok=true` 表示该文件已落库；`ok=false` 时 `message` 是**服务端原文**（未改写）。
 */
export interface AttachmentUploadOutcome {
  fileName: string
  ok: boolean
  /** 业务码（`40012` / `40304` / `40308` / `40310` / `40402` / `50004`…）；网络异常为 `null` */
  code: number | string | null
  /** 服务端文案（原样）或网络异常文案 */
  message: string
  traceId: string
  /** 成功时为落库后的附件；失败为 `null` */
  attachment: AttachmentFile | null
}

/** 批量（逐文件）上传汇总 */
export interface AttachmentBatchUploadResult {
  fieldCode: string
  /** 本次落库的 round（全部失败时为 `null`） */
  round: number | null
  outcomes: AttachmentUploadOutcome[]
  succeeded: number
  failed: number
  /** 最后一次成功响应下发的生效档位（失败时 `null`） */
  bounds: AttachmentBounds | null
  /** 服务端算出的**该字段累计数量**（含本次；全部失败时为 `null`） */
  fieldCount: number | null
  /** 服务端算出的**单据累计数量**（含补件；全部失败时为 `null`） */
  instanceCount: number | null
}

/** 删除结果 */
export interface AttachmentDeleteResult {
  id: string
  fileName: string
  deleted: boolean
  physicalFileRemoved: boolean
}

/** 下载 / 预览取回的二进制内容 */
export interface AttachmentBlob {
  blob: Blob
  /** 服务端 `Content-Disposition` 里的文件名（解析失败时由调用方回落本地文件名） */
  fileName: string
  contentType: string
  /** `Content-Disposition` 是否 `inline`（**服务端的裁决**，前端不自行判定） */
  inline: boolean
  byteLength: number
}

/** 取二进制内容的两种口径 */
export type AttachmentTransferMode = 'download' | 'preview'
