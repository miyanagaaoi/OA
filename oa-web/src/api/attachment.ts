/**
 * oa-web · 附件域接口（阶段 2b.7：上传 / 清单 / 鉴权下载 / 预览 / 删除）
 * ----------------------------------------------------------------------------
 * 路径为阶段 2b.7 **已交付**的定稿契约（全部在 `/api/v1` 之下），本文件把
 * `types/attachment-wire.d.ts`（后端 DTO 镜像）映射为领域模型 `types/attachment.d.ts`。
 *
 * 后端实现：
 *   · `com.oa.form.attachment.api.AttachmentController`（5 条路由）
 *   · `com.oa.form.attachment.app.AttachmentService`（上传 / 清单 / 删除）
 *   · `com.oa.form.attachment.domain.AttachmentPolicy`（档位与三层校验的**判定源**）
 *
 * 四条实现约定：
 *   1. **映射层承担 `Long` → string**（id 与 `fileSize` 在 JSON 里都是字符串）；
 *      `fileSize` 再转 number（文件大小不是金额，无定点约束）；
 *   2. **不拼直链**：下载/预览一律调 `downloadUrl` / `previewUrl` 给出的鉴权接口
 *      （服务端刻意不回 `storagePath`，见 TC-FORM-024）；
 *   3. **不做演示数据降级**：附件是「服务端落库 + 私有存储」的实体，静默回落演示数据
 *      会让界面显示一批并不存在的文件；
 *   4. **逐文件上传**（{@link uploadAttachments}）：服务端对**一次请求**是
 *      「全量校验、任一不合格即整体拒绝」（`AttachmentService#upload` 的固定顺序），
 *      因此只有逐文件分别请求才能给出「成功几个、失败哪个、为什么」。
 */
import { ApiError, del, get, getBlob, postMultipart, type OaRequestConfig } from './http'
import type {
  WireAttachmentBoundsView,
  WireAttachmentDeleteView,
  WireAttachmentListView,
  WireAttachmentUploadView,
  WireAttachmentView,
} from '@/types/attachment-wire'
import type {
  AttachmentBatchUploadResult,
  AttachmentBlob,
  AttachmentBounds,
  AttachmentDeleteResult,
  AttachmentFile,
  AttachmentList,
  AttachmentRoundGroup,
  AttachmentTransferMode,
  AttachmentUploadOutcome,
  AttachmentUploadResult,
} from '@/types/attachment'
import { fileNameFromContentDisposition, isInlineDisposition } from '@/utils/attachment'

// ---------------------------------------------------------------------------
// 传输层包装：错误提示由**页面/面板唯一呈现**（服务端文案原文 + 业务码 + 处置提示）
// ---------------------------------------------------------------------------
/**
 * 关闭 `api/http.ts` 拦截器的统一 toast。
 *
 * <p>理由与 `api/form.ts` / `api/flow.ts` 逐条一致：附件域的拒绝
 * （`40012` 限额/格式、`40304` 状态只读、`40310` 删除身份不足、`40402` 域外、`50004` 存储失败）
 * 都需要把**服务端原文 + 业务码 + traceId** 一起呈现，拦截器 toast 只带 message，
 * 两处都提示会一次操作弹两次。
 * 401 不受影响（仍走 `setUnauthorizedHandler` 的跳登录逻辑）。
 */
const NO_INTERCEPTOR_TOAST: OaRequestConfig = {
  notify: { forbidden: false, conflict: false, rateLimited: false, serverError: false },
}

// ---------------------------------------------------------------------------
// 映射工具
// ---------------------------------------------------------------------------
/** `Long` → 字符串 id（缺失 → 空串） */
function sid(value: string | number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

/** `Long`（JSON 字符串）→ number（非法/缺失 → `null`） */
function sizeOf(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** `Integer` → number（非法值退回 fallback） */
function num(value: number | string | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** 可空字符串归一 */
function text(value: string | null | undefined): string {
  return value === null || value === undefined ? '' : value
}

/** 可空字符串归一为 `string | null` */
function nullableText(value: string | null | undefined): string | null {
  return value === null || value === undefined || value === '' ? null : value
}

/** 字符串数组归一（去空保序） */
function stringList(value: readonly string[] | null | undefined): string[] {
  return (value ?? []).map((item) => text(item)).filter((item) => item !== '')
}

/** 附件视图 → 领域模型 */
export function toAttachmentFile(wire: WireAttachmentView): AttachmentFile {
  return {
    id: sid(wire.id),
    instanceId: sid(wire.instanceId),
    round: num(wire.round, 0),
    fieldCode: text(wire.fieldCode),
    fileName: text(wire.fileName),
    fileSizeBytes: sizeOf(wire.fileSize),
    fileExt: text(wire.fileExt).toLowerCase(),
    mimeType: nullableText(wire.mimeType),
    sha256: nullableText(wire.sha256),
    uploaderId: sid(wire.uploaderId),
    uploadedAt: nullableText(wire.createdAt),
    // 下载/预览地址一律用服务端给的**鉴权接口**路径（相对路径，交给 baseURL/代理）
    downloadUrl: text(wire.downloadUrl),
    previewUrl: text(wire.previewUrl),
  }
}

/** 档位出参 → 领域模型（`source='server'`：这份是服务端判定时用的那一份） */
function toBounds(wire: WireAttachmentBoundsView | null | undefined): AttachmentBounds {
  return {
    maxSizeMb: num(wire?.maxSizeMb, 0),
    maxCount: num(wire?.maxCount, 0),
    maxPerUpload: num(wire?.maxPerUpload, 0),
    maxPerInstance: num(wire?.maxPerInstance, 0),
    allowExt: stringList(wire?.allowExt),
    denyExt: stringList(wire?.denyExt),
    source: 'server',
    declaredMessage: null,
  }
}

/** 上传出参 → 领域模型 */
function toUploadResult(wire: WireAttachmentUploadView): AttachmentUploadResult {
  return {
    instanceId: sid(wire.instanceId),
    fieldCode: text(wire.fieldCode),
    round: num(wire.round, 0),
    uploaded: (wire.uploaded ?? []).map(toAttachmentFile),
    fieldCount: num(wire.fieldCount, 0),
    instanceCount: num(wire.instanceCount, 0),
    bounds: toBounds(wire.bounds),
    evidence: text(wire.evidence),
  }
}

// ================================================================ 清单

/**
 * 取某单据的附件清单（**按 round 分组**；出参不含存储路径）。
 *
 * <p>服务端的 `rounds` 是 `Map<Integer,…>` → JSON 对象，键是 round 的十进制字符串；
 * 这里统一拍平成 `rounds: [{round, files}]` 并按 round 升序（0 → 1..3），
 * 使界面与 `utils/attachment.ts#groupByRound` 只有**一个**分组口径。
 */
export async function fetchAttachmentList(instanceId: string): Promise<AttachmentList> {
  const wire = await get<WireAttachmentListView>(
    `/forms/instances/${encodeURIComponent(instanceId)}/attachments`,
    NO_INTERCEPTOR_TOAST,
  )
  const rounds: AttachmentRoundGroup[] = []
  const entries = Object.entries(wire?.rounds ?? {})
  for (const [key, items] of entries) {
    const files = (items ?? []).map(toAttachmentFile)
    if (files.length === 0) continue
    // 键是 round；条目自带 round 时以条目为准（服务端两者同源，取其一不产生第二套口径）
    const round = files[0].round
    rounds.push({ round: Number.isFinite(round) ? round : num(key, 0), files })
  }
  rounds.sort((left, right) => left.round - right.round)
  return {
    instanceId: sid(wire?.instanceId),
    bizNo: text(wire?.bizNo),
    total: num(wire?.total, rounds.reduce((sum, group) => sum + group.files.length, 0)),
    rounds,
    evidence: text(wire?.evidence),
  }
}

// ================================================================ 上传

/**
 * 上传**单个**文件（一次 `POST`，multipart）。
 *
 * <p>请求形状逐字对齐后端：`@RequestParam("files") List<MultipartFile>` +
 * 可选 `@RequestParam("fieldCode")`。因此表单键固定为 `files`（可重复），
 * 字段码固定为 `fieldCode` —— **不要**改名。
 */
export async function uploadAttachmentFile(
  instanceId: string,
  fieldCode: string,
  file: File,
): Promise<AttachmentUploadResult> {
  const form = new FormData()
  form.append('files', file, file.name)
  if (fieldCode !== '') form.append('fieldCode', fieldCode)
  const wire = await postMultipart<WireAttachmentUploadView>(
    `/forms/instances/${encodeURIComponent(instanceId)}/attachments`,
    form,
    NO_INTERCEPTOR_TOAST,
  )
  return toUploadResult(wire)
}

/**
 * 逐文件上传一批文件，**逐文件**给出结果。
 *
 * <p>为什么不一次性 `POST` 全部文件：服务端对一次请求是「先全量校验，任一不合格即整体
 * 拒绝」（`AttachmentService#upload`：批量里第 2 个是 `.exe` 时第 1 个也不会落库）。
 * 那样「部分失败」在界面上只能表现为「全部失败」，用户无法知道是哪个文件、为什么。
 * 逐文件请求把这件事摊开：成功 N 个、失败 M 个、每个失败的服务端原文都留着。
 *
 * <p>代价（如实说明）：不再享受「单次 20 个」的一次性语义（每次请求只带 1 个，
 * 必然 ≤20），但**单字段 maxCount 与单据合计 ≤50 仍然由服务端累计判定**，
 * 因此不会因为拆成多次而绕过任何档位。
 */
export async function uploadAttachments(
  instanceId: string,
  fieldCode: string,
  files: readonly File[],
): Promise<AttachmentBatchUploadResult> {
  const outcomes: AttachmentUploadOutcome[] = []
  let round: number | null = null
  let bounds: AttachmentBounds | null = null
  let fieldCount: number | null = null
  let instanceCount: number | null = null

  for (const file of files) {
    try {
      const result = await uploadAttachmentFile(instanceId, fieldCode, file)
      round = result.round
      bounds = result.bounds
      // 服务端算出的累计数量（含补件）：逐文件请求下，**最后一次**的响应就是最终值
      fieldCount = result.fieldCount
      instanceCount = result.instanceCount
      outcomes.push({
        fileName: file.name,
        ok: true,
        code: 0,
        message: '已上传',
        traceId: '',
        attachment: result.uploaded.length > 0 ? result.uploaded[0] : null,
      })
    } catch (error) {
      outcomes.push(toOutcome(file.name, error))
    }
  }

  const succeeded = outcomes.filter((item) => item.ok).length
  return {
    fieldCode,
    round: succeeded > 0 ? round : null,
    outcomes,
    succeeded,
    failed: outcomes.length - succeeded,
    bounds: succeeded > 0 ? bounds : null,
    fieldCount: succeeded > 0 ? fieldCount : null,
    instanceCount: succeeded > 0 ? instanceCount : null,
  }
}

/** 任意异常 → 逐文件结论（**服务端文案原样保留**，不改写、不吞掉） */
function toOutcome(fileName: string, error: unknown): AttachmentUploadOutcome {
  if (error instanceof ApiError) {
    return {
      fileName,
      ok: false,
      code: error.code,
      message: error.message,
      traceId: error.traceId ?? '',
      attachment: null,
    }
  }
  return {
    fileName,
    ok: false,
    code: null,
    message: error instanceof Error ? error.message : '上传失败（未知错误）',
    traceId: '',
    attachment: null,
  }
}

// ================================================================ 下载 / 预览

/**
 * 取附件的二进制内容（`download` 恒为下载；`preview` 由服务端决定内联或降级）。
 *
 * <p>两条硬约束：
 *   · **必须走带 id 的鉴权接口**（`/forms/attachments/{id}/download|preview`），
 *     不拼直链、不碰 `storagePath`；请求由 `api/http.ts` 统一携带会话 Cookie；</li>
 *   · **内联与否以服务端 `Content-Disposition` 为准**（{@link AttachmentBlob.inline}）：
 *     前端无权也不该自己判定「这个文件能不能内联」。
 */
export async function fetchAttachmentBlob(
  attachmentId: string,
  mode: AttachmentTransferMode,
): Promise<AttachmentBlob> {
  const response = await getBlob(
    `/forms/attachments/${encodeURIComponent(attachmentId)}/${mode}`,
    NO_INTERCEPTOR_TOAST,
  )
  const disposition = headerValue(response.headers, 'content-disposition')
  const contentType = headerValue(response.headers, 'content-type')
  return {
    blob: response.blob,
    fileName: fileNameFromContentDisposition(disposition),
    contentType,
    inline: isInlineDisposition(disposition),
    byteLength: response.blob.size,
  }
}

/** 响应头取值（axios 的 headers 形态在不同适配器下略有差异，统一按小写键取） */
function headerValue(headers: Record<string, unknown>, name: string): string {
  const direct = headers[name]
  if (typeof direct === 'string') return direct
  const lower = name.toLowerCase()
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === lower && typeof value === 'string') return value
  }
  return ''
}

// ================================================================ 删除

/**
 * 删除附件（服务端三档身份：**上传者本人 ∪ 单据发起人本人 ∪ 系统管理员**，
 * 且仅草稿/待补件窗口；否则 40310 / 40304 / 40402）。
 *
 * <p>**2026-10 裁定**：可删身份由旧判据「上传者本人 ∪ 系统管理员」放宽为**三档**——
 * 管理员**代传**时 `uploader_id = 管理员`，旧判据会让**单据发起人本人反而删不掉
 * 自己单据上的附件**；代传只应「多一个能删的人」，不应让本人失去删除权。
 * 同部门同事 / 审批人 / 抄送人仍被 40310 拒绝（数据域内可见 ≠ 可删）。
 *
 * <p>此处**仅注释**：行为早已按新裁定实现（服务端 `AttachmentService#delete`、
 * 错误码 `ATTACHMENT_DELETE_DENIED(40310)`、前端判据 `utils/attachment.ts` 与本文件
 * 的入参/出参形状均未改动），本次只把过时的注释文案对齐到事实。
 *
 * <p>返回体里带着两个删除事实（`deleted` / `physicalFileRemoved`），
 * 界面应**如实展示**（服务端先删物理文件再删元数据行，同一事务）。
 */
export async function deleteAttachment(attachmentId: string): Promise<AttachmentDeleteResult> {
  const wire = await del<WireAttachmentDeleteView>(
    `/forms/attachments/${encodeURIComponent(attachmentId)}`,
    NO_INTERCEPTOR_TOAST,
  )
  return {
    id: sid(wire?.id),
    fileName: text(wire?.fileName),
    deleted: wire?.deleted === true,
    physicalFileRemoved: wire?.physicalFileRemoved === true,
  }
}
