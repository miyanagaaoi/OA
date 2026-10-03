/**
 * oa-web · 附件域纯逻辑（无 Vue / 无 DOM / 无 axios —— 可直接用 Node 跑断言自测）
 * ----------------------------------------------------------------------------
 * 分工：本文件只做**判定与归一**，不发请求、不碰组件状态、不碰 `File`/`Blob` 这类
 * 浏览器对象（预检入参是 `{name, size}` 的最小投影，因此可在 Node 里穷举）。
 *
 * 真源：
 *   · `doc/forms.md` §1.4（附件通用限制：单文件 ≤50MB / 单次 ≤20 个 / 单据合计 ≤50
 *     （含补件）/ 15 种允许格式 / 9 种禁止格式 / 私有化存储、禁止直链 / 补件轮次 0..3）
 *   · `doc/enums.md` §12.1（15 种允许格式逐项）、§12.2（9 种禁止格式「上传即拒绝」+
 *     「扩展名 + MIME 双重判断」）、§12.3（轮次语义：0=原始附件，1..3=第 N 次补件）
 *   · `doc/templates.md` §2.3（`rules[filePolicy]`：maxSizeMb / maxCount / allowExt /
 *     denyExt / message；缺省取全局规则）
 *   · 后端：`com.oa.form.attachment.domain.AttachmentPolicy`（纯函数判定源）、
 *     `AttachmentController`（内联白名单 `image/jpeg` / `image/png` / `application/pdf` +
 *     `oa.attachment.max-inline-preview-bytes`，默认 20MB）、
 *     `com.oa.form.template.validate.FormPayloadValidator`（三档常量与两份清单的出处）
 *
 * ⚠ 本文件**不是边界**：服务端 `AttachmentPolicy` 才是裁决方（扩展名 + MIME + **魔数**
 *   三层校验 + 三档限额 + 三态白名单 + 身份）。这里的一切结论只用于**即时提示**，
 *   文案里必须把这一点讲清楚（见 `ATTACHMENT_SERVER_DECIDES_NOTE`）。
 */
import type { FormJsonValue, FormWriteStateCode } from '@/types/form'
import type {
  AttachmentBounds,
  AttachmentFile,
  AttachmentRoundGroup,
} from '@/types/attachment'

// ================================================================ 常量（与后端口径逐条对齐）

/**
 * 15 种允许扩展名（`doc/enums.md` §12.1；后端 `AttachmentPolicy.ALLOWED_EXT`
 * 与 `FormPayloadValidator.ATTACHMENT_ALLOWED_EXT` 同源）。
 */
export const ATTACHMENT_ALLOWED_EXT: readonly string[] = [
  'pdf', 'doc', 'docx', 'wps', 'xls', 'xlsx', 'ppt', 'pptx',
  'jpg', 'jpeg', 'png', 'heic', 'zip', 'rar', '7z',
]

/** 9 种禁止扩展名（`doc/enums.md` §12.2「上传即拒绝」，**优先于**白名单） */
export const ATTACHMENT_DENIED_EXT: readonly string[] = [
  'exe', 'bat', 'cmd', 'js', 'vbs', 'ps1', 'dll', 'msi', 'scr',
]

/** 单文件大小上限（MB）——`doc/forms.md` §1.4「≤ 50 MB」 */
export const ATTACHMENT_MAX_SIZE_MB = 50

/** 单次上传数量上限——`doc/forms.md` §1.4「≤ 20 个」 */
export const ATTACHMENT_MAX_PER_UPLOAD = 20

/** 单张单据附件总数上限（含补件）——`doc/forms.md` §1.4「≤ 50 个」 */
export const ATTACHMENT_MAX_PER_INSTANCE = 50

/** 单张单据附件总数上限（含补件）——同 {@link ATTACHMENT_MAX_PER_INSTANCE}，语义化别名 */
export const ATTACHMENT_DEFAULT_MAX_COUNT = ATTACHMENT_MAX_PER_UPLOAD

/**
 * 内联预览体积上限（字节）。
 *
 * <p>后端 `OaProperties.Attachment#maxInlinePreviewBytes` 默认 `20L * 1024 * 1024`：
 * 超出即**降级为 `attachment`**（下载），`GET /preview` 一样不内联。
 */
export const ATTACHMENT_MAX_INLINE_PREVIEW_BYTES = 20 * 1024 * 1024

/** 允许**内联预览**的扩展名（与后端的 MIME 白名单同集，只是表述成扩展名便于比对） */
export const ATTACHMENT_INLINE_EXT: readonly string[] = ['jpg', 'jpeg', 'png', 'pdf']

/** 允许内联预览的 MIME（后端 `AttachmentController.INLINE_SAFE_MIME` 逐字一致） */
export const ATTACHMENT_INLINE_MIME: readonly string[] = [
  'image/jpeg', 'image/png', 'application/pdf',
]

/**
 * heic 的预览口径（**后端本轮未实现转码**）。
 *
 * <p>模板 `filePolicy.message` 里写的是「heic 转 jpg 预览」，但
 * `AttachmentController#preview` 的注释明确「HEIC 转码未在本轮实现（需要图像库）」，
 * 因此 heic 走 `attachment` 降级。界面**不能**照抄模板文案，必须如实标注本句。
 */
export const ATTACHMENT_HEIC_PREVIEW_NOTE = 'heic 暂不支持在线预览，请下载查看'

/** 「服务端是裁决方」的统一声明（每个预检提示后都要能看见） */
export const ATTACHMENT_SERVER_DECIDES_NOTE =
  '服务端是裁决方：以上为前端预检提示（扩展名 / 大小 / 数量）；服务端按扩展名 + MIME + ' +
  '文件头魔数三层校验与三档限额裁决，越限或格式不符一律 40012 拒收（doc/forms.md §1.4、doc/enums.md §12）。'

/**
 * 全局缺省档位（`AttachmentPolicy.boundsOf` 在模板未声明 `filePolicy` 时的取值）。
 *
 * <p>`maxPerUpload` / `maxPerInstance` 是全局档，模板不可改；
 * `maxCount` 缺省 = 20（`doc/templates.md` §2.3 filePolicy.maxCount）。
 */
export const GLOBAL_ATTACHMENT_BOUNDS: AttachmentBounds = {
  maxSizeMb: ATTACHMENT_MAX_SIZE_MB,
  maxCount: ATTACHMENT_MAX_PER_UPLOAD,
  maxPerUpload: ATTACHMENT_MAX_PER_UPLOAD,
  maxPerInstance: ATTACHMENT_MAX_PER_INSTANCE,
  allowExt: [...ATTACHMENT_ALLOWED_EXT],
  denyExt: [...ATTACHMENT_DENIED_EXT],
  source: 'global',
  declaredMessage: null,
}

/** 轮次上限（`doc/enums.md` §12.3：0..3） */
export const ATTACHMENT_MAX_ROUND = 3

// ================================================================ 错误码补充说明

/**
 * 附件域错误码 → 「这是什么、该怎么办」（**在服务端 message 之外**的补充）。
 *
 * ⚠ 服务端 message 必须**原样**展示（任务书要求：不要吞掉、不要改写）；
 * 本表只提供第二行的处置提示，不替代原文。
 */
export const ATTACHMENT_ERROR_HINT: Record<number, string> = {
  40012: '附件不符合上传规则：三档限额（单文件 ≤50MB / 单次 ≤20 个 / 单据合计 ≤50 个含补件）' +
    '或格式（15 种允许、9 种禁止；扩展名 + MIME + 魔数三层校验）被拒 —— 按服务端文案里的具体原因处理。',
  40304: '三态白名单拒绝：附件**只能在草稿与待补件期**上传或删除；审批中与已完结一律只读（doc/forms.md §1.2、§7）。',
  40308: '附件只能挂在**实例锁定版本 schema** 里已登记的 file/files 字段上；该字段码未登记（doc/templates.md §2.5）。',
  40310: '删除被拒：只能删除**本人上传**的附件（或由系统管理员删除）（AC-41 最小权限）。',
  40402: '附件不存在或不在你的数据域内 —— 服务端对「域外」与「不存在」用**同一个码**，不区分（避免 id 枚举）。',
  50004: '附件存储不可用（落盘/删除失败）：服务端已回滚，元数据保留，可稍后重试。',
  403: '只有**发起人本人或系统管理员**可以上传/删除该单据的附件（服务端 `requireInitiatorOrAdmin`）。',
}

/** 错误码的补充说明（无则空串；服务端原文不在此处、不得被本函数取代） */
export function attachmentErrorHint(code: number | string | null | undefined): string {
  const numeric = typeof code === 'string' ? Number(code) : code
  if (numeric === null || numeric === undefined || !Number.isFinite(numeric)) return ''
  return ATTACHMENT_ERROR_HINT[numeric as number] ?? ''
}

// ================================================================ 字节数格式化

/**
 * 字节数 → 人类可读（B / KB / MB / GB）。
 *
 * <p>为什么本文件另有一份而不是复用 `utils/format.ts#formatFileSize`：
 * 后者签名是 `(bytes: number)`，而附件域的字节数来源是 **wire 层的字符串**
 * （后端 `Long` → `"70"`）且可能缺失（`null`）；这里统一接受
 * `number | string | null | undefined` 并给出 `—` 兜底，避免每处调用都先 `Number(...)`。
 * 两者口径一致（KB/MB 一位小数、GB 两位小数），不存在第二套展示口径。
 */
export function formatBytes(bytes: number | string | null | undefined): string {
  const value = toFiniteNumber(bytes)
  if (value === null || value < 0) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 任意 wire/JSON 值 → 有限数字（非法返回 `null`；字符串走 `Number`） */
function toFiniteNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/**
 * 后端 `LocalDateTime` 原样值 → 界面时间文本。
 *
 * <p>后端下发的是 `2026-10-03 15:49:19`（**空格分隔，不是 ISO 8601**）。
 * `new Date('2026-10-03 15:49:19')` 在 V8 里能解析，但那是实现细节、且会受时区影响；
 * 这里直接做字符串裁剪（正则匹配不到时原样回显，**不猜**）。
 */
export function formatAttachmentTime(value: string | null | undefined): string {
  const raw = (value ?? '').trim()
  if (raw === '') return '—'
  const matched = raw.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/)
  return matched ? `${matched[1]} ${matched[2]}` : raw
}

// ================================================================ 扩展名与格式判定

/** 取小写扩展名（无扩展名返回空串；`"a.tar.gz"` → `"gz"`，与后端 `extensionOf` 同口径） */
export function extensionOf(fileName: string | null | undefined): string {
  const name = (fileName ?? '').replace(/\\/g, '/')
  const slash = name.lastIndexOf('/')
  const base = slash >= 0 ? name.slice(slash + 1) : name
  const dot = base.lastIndexOf('.')
  if (dot <= 0 || dot === base.length - 1) return ''
  return base.slice(dot + 1).trim().toLowerCase()
}

/** 扩展名判定结论（前端提示用；服务端仍是裁决方） */
export interface AttachmentExtVerdict {
  ext: string
  ok: boolean
  /** 不允许时的原因（含命中的是黑名单还是白名单） */
  reason: string
}

/**
 * 扩展名黑白名单判定（黑名单**优先于**白名单，与 `AttachmentPolicy#check` 的顺序一致）。
 *
 * <p>注意：服务端在此之后还要看客户端 MIME 与**文件头魔数**，因此
 * 「扩展名通过」不等于「一定能上传成功」——提示里必须保留这一层。
 */
export function checkAttachmentExtension(
  fileName: string | null | undefined,
  bounds: AttachmentBounds = GLOBAL_ATTACHMENT_BOUNDS,
): AttachmentExtVerdict {
  const ext = extensionOf(fileName)
  if (ext === '') {
    return {
      ext,
      ok: false,
      reason: `文件名缺少扩展名，无法判定格式；允许格式：${bounds.allowExt.join('/')}`,
    }
  }
  const deny = bounds.denyExt.map((item) => item.toLowerCase())
  const allow = bounds.allowExt.map((item) => item.toLowerCase())
  if (deny.includes(ext)) {
    return {
      ext,
      ok: false,
      reason: `不允许上传 ${ext} 格式的文件（上传即拒绝；doc/enums.md §12.2）`,
    }
  }
  if (!allow.includes(ext)) {
    return { ext, ok: false, reason: `仅支持 ${bounds.allowExt.join('/')}（doc/forms.md §1.4）` }
  }
  return { ext, ok: true, reason: '' }
}

/** 前端预检的单文件投影（不依赖浏览器 `File`，因此可在 Node 里自测） */
export interface AttachmentCandidate {
  name: string
  size: number
}

/** 单文件预检结论 */
export interface AttachmentPrecheckItem {
  name: string
  size: number
  ext: string
  ok: boolean
  reason: string
}

/** 整批预检结论 */
export interface AttachmentPrecheck {
  items: AttachmentPrecheckItem[]
  /** 通过预检、可以提交的文件 */
  accepted: AttachmentCandidate[]
  /** 被预检拦下的文件（含逐条原因） */
  rejected: AttachmentPrecheckItem[]
  /**
   * 整批级提示（数量档位等）。**不阻断提交**：档位可能被模板收窄/放宽，
   * 且服务端才是裁决方 —— 这里把话说清楚，由用户决定是否继续。
   */
  batchMessages: string[]
  /** 全是逐文件错误（`rejected.length === 0` 且无 batchMessages 时为 `true`） */
  ok: boolean
}

/** 预检上下文 */
export interface AttachmentPrecheckContext {
  bounds?: AttachmentBounds
  /** 该字段已落库数量（`GET /list` 的 round 分组求和，或上传响应的 `fieldCount`） */
  existingInField?: number
  /** 该单据已落库数量（含补件） */
  existingInInstance?: number
}

/**
 * 前端预检：**逐文件**判扩展名与大小，**整批**判数量档位。
 *
 * <p>与后端 `AttachmentPolicy` 的差异（刻意保留、并在文案里声明）：
 * 前端**看不到文件内容**（魔数）也拿不到客户端 MIME 的可信版本，因此
 * 「扩展名合法」只说明**大概率**能过；「文件内容与扩展名不符」只能由服务端发现。
 *
 * <p>数量判定只是提示：`maxCount` 缺省 20、`maxPerInstance` 恒 50
 * （`doc/forms.md` §1.4；AC-45 第 21 个文件被拒）。
 */
export function precheckAttachmentFiles(
  files: readonly AttachmentCandidate[],
  context: AttachmentPrecheckContext = {},
): AttachmentPrecheck {
  const bounds = context.bounds ?? GLOBAL_ATTACHMENT_BOUNDS
  const existingInField = context.existingInField ?? 0
  const existingInInstance = context.existingInInstance ?? 0

  const items: AttachmentPrecheckItem[] = []
  const accepted: AttachmentCandidate[] = []
  const rejected: AttachmentPrecheckItem[] = []

  for (const file of files) {
    const verdict = checkAttachmentExtension(file.name, bounds)
    const size = toFiniteNumber(file.size) ?? 0
    let ok = verdict.ok
    let reason = verdict.reason
    if (ok && size <= 0) {
      ok = false
      reason = '空文件不予接收（0 字节）'
    }
    if (ok && size > bounds.maxSizeMb * 1024 * 1024) {
      ok = false
      reason = `单个文件不超过 ${bounds.maxSizeMb}MB（doc/forms.md §1.4；本次 ${formatBytes(size)}）`
    }
    const item: AttachmentPrecheckItem = { name: file.name, size, ext: verdict.ext, ok, reason }
    items.push(item)
    if (ok) accepted.push({ name: file.name, size })
    else rejected.push(item)
  }

  const batchMessages: string[] = []
  const incoming = files.length
  if (incoming > bounds.maxPerUpload) {
    batchMessages.push(
      `本次选择 ${incoming} 个文件，超过单次上传上限 ${bounds.maxPerUpload} 个（doc/forms.md §1.4）——` +
        '服务端会按 40012 拒绝整批，请分批上传。',
    )
  }
  if (accepted.length > 0) {
    const fieldTotal = existingInField + accepted.length
    if (fieldTotal > bounds.maxCount) {
      batchMessages.push(
        `该字段累计将达到 ${fieldTotal} 个，超过档位上限 ${bounds.maxCount} 个` +
          `${bounds.source === 'template' ? '（模板 filePolicy.maxCount）' : '（服务端缺省 filePolicy.maxCount）'}——` +
          '服务端会按 40012 拒绝。',
      )
    }
    const instanceTotal = existingInInstance + accepted.length
    if (instanceTotal > bounds.maxPerInstance) {
      batchMessages.push(
        `单张单据附件总数将达到 ${instanceTotal} 个，超过上限 ${bounds.maxPerInstance} 个（含补件；doc/forms.md §1.4）——` +
          '服务端会按 40012 拒绝。',
      )
    }
  }

  return {
    items,
    accepted,
    rejected,
    batchMessages,
    ok: rejected.length === 0 && batchMessages.length === 0,
  }
}

// ================================================================ 档位（模板 filePolicy）

/**
 * 从 schema 的 `ruleDetails[type=filePolicy]` 解析**模板声明的档位**。
 *
 * <p>背景：schema 出参的 `rules` 只给规则**类型名**，参数在 2026-10-05 起随
 * `ruleDetails[]` 原样下发（`FormFieldDef#ruleDetailsView`：**如实转写模板声明的键**，
 * 不注入服务端默认值）。因此：
 * <ul>
 *   <li>模板声明了的键 → 用它（这就是「服务端判定时用的那一份」的前半段）；</li>
 *   <li>模板没声明的键 → 回落**全局缺省**（`AttachmentPolicy.boundsOf` 同口径）；</li>
 *   <li>`maxPerUpload` / `maxPerInstance` **模板改不了**，恒取全局值。</li>
 * </ul>
 * 上传成功后应当用响应里的 `bounds` 覆盖（那份是服务端算出来的**最终**档位）。
 */
export function resolveAttachmentBounds(
  filePolicy: FormJsonValue | undefined | null,
  fallback: AttachmentBounds = GLOBAL_ATTACHMENT_BOUNDS,
): AttachmentBounds {
  const record = asJsonObject(filePolicy)
  if (!record) return fallback
  const maxSizeMb = positiveInt(record.maxSizeMb) ?? fallback.maxSizeMb
  const maxCount = positiveInt(record.maxCount) ?? fallback.maxCount
  const allowExt = stringList(record.allowExt)
  const denyExt = stringList(record.denyExt)
  const message = typeof record.message === 'string' && record.message.trim() !== ''
    ? record.message
    : null
  return {
    maxSizeMb,
    maxCount,
    maxPerUpload: fallback.maxPerUpload,
    maxPerInstance: fallback.maxPerInstance,
    allowExt: allowExt.length > 0 ? allowExt : fallback.allowExt,
    denyExt: denyExt.length > 0 ? denyExt : fallback.denyExt,
    source: 'template',
    declaredMessage: message,
  }
}

/** 自由 JSON → 对象（非对象/数组返回 `null`） */
function asJsonObject(value: FormJsonValue | undefined | null): Record<string, FormJsonValue> | null {
  if (value === null || value === undefined) return null
  if (typeof value !== 'object' || Array.isArray(value)) return null
  return value as Record<string, FormJsonValue>
}

/** 正整数字面量（number 或数字字符串；非正数/非法返回 `null`，与后端「配非正数按全局兜底」同口径） */
function positiveInt(value: FormJsonValue | undefined): number | null {
  if (value === undefined || value === null) return null
  const parsed = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN
  if (!Number.isFinite(parsed) || parsed <= 0) return null
  return Math.trunc(parsed)
}

/** 字符串数组（小写去空去重保序） */
function stringList(value: FormJsonValue | undefined): string[] {
  if (!Array.isArray(value)) return []
  const result: string[] = []
  for (const item of value) {
    if (typeof item !== 'string') continue
    const normalized = item.trim().toLowerCase()
    if (normalized === '' || result.includes(normalized)) continue
    result.push(normalized)
  }
  return result
}

/** 一句话描述生效档位（展示用；`source` 标明出处，不猜） */
export function describeAttachmentBounds(bounds: AttachmentBounds): string {
  const source = bounds.source === 'server'
    ? '服务端上传响应 bounds（AttachmentPolicy 判定时用的那一份）'
    : bounds.source === 'template'
      ? '模板 filePolicy 声明（schema ruleDetails；未声明项回落全局缺省）'
      : '全局缺省（模板未声明 filePolicy）'
  return `单文件 ≤${bounds.maxSizeMb}MB · 单次 ≤${bounds.maxPerUpload} 个 · 单据合计 ≤${bounds.maxPerInstance} 个（含补件）` +
    ` · 允许 ${bounds.allowExt.length} 种（${bounds.allowExt.join('/')}） · 禁止 ${bounds.denyExt.length} 种（${bounds.denyExt.join('/')}）` +
    ` —— 来源：${source}`
}

// ================================================================ round 分组

/**
 * 轮次标签（`doc/enums.md` §12.3：`0` = 原始附件，`1..3` = 第 N 次补件）。
 *
 * <p>超出 0..3 的值**不美化**：如实标注「未登记轮次」（后端会把轮次夹到 1..3，
 * 真出现域外值说明数据异常，界面不该替它圆场）。
 */
export function roundLabel(round: number | null | undefined): string {
  if (round === null || round === undefined || !Number.isFinite(round)) return '轮次未知'
  if (round === 0) return '原始附件'
  if (round >= 1 && round <= ATTACHMENT_MAX_ROUND) return `第 ${round} 次补件`
  return `未登记轮次（round=${round}）`
}

/**
 * 按 round 分组（服务端 `list` 已按 round 分好，这里是**前端唯一的归一入口**：
 * 兼容服务端将来改成平铺数组，也便于对上传回显做本地插入）。
 *
 * <p>排序：round 升序（0 → 1 → 2 → 3）；组内保持传入顺序
 * （服务端按 `flow_attachment.id` 取，即上传先后）。
 */
export function groupByRound(files: readonly AttachmentFile[]): AttachmentRoundGroup[] {
  const buckets = new Map<number, AttachmentFile[]>()
  for (const file of files) {
    const round = Number.isFinite(file.round) ? file.round : 0
    const bucket = buckets.get(round)
    if (bucket) bucket.push(file)
    else buckets.set(round, [file])
  }
  return [...buckets.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([round, groupFiles]) => ({ round, files: groupFiles }))
}

/** 平铺清单里的附件总数（用于数量档位预检） */
export function countAttachments(files: readonly AttachmentFile[]): number {
  return files.length
}

// ================================================================ 三态与身份：可写窗口

/** 身份与状态的最小投影（便于脱离 Pinia 自测） */
export interface AttachmentSubject {
  userId: string
  /** 是否发起人本人 */
  isInitiator: boolean
  /** 是否系统管理员 */
  isAdmin: boolean
}

/** 上传/删除窗口结论 */
export interface AttachmentWindow {
  /** 三态白名单是否放行（草稿 / 待补件） */
  stateOpen: boolean
  /** 是否可以上传（状态 ∧ 身份） */
  canUpload: boolean
  /** 是否可以删除**本人上传**的附件（状态 ∧ 身份） */
  canDeleteOwn: boolean
  /** 面向用户的说明（不可写时给出原因；可写时也说明边界在服务端） */
  reason: string
}

/**
 * 附件读写窗口判定（**镜像后端判据，不是第二套规则**）。
 *
 * <p>后端 `AttachmentService#upload / #delete` 的顺序是：
 * 数据域 → 字段登记 → **三态白名单**（`FormStateWriteGuard#assertStateWritable`，
 * 草稿 / 待补件可写，审批中与已完结 40304）→ **身份**（`requireInitiatorOrAdmin`：
 * 发起人本人或系统管理员）。
 *
 * <p>本函数把这两条如实摊开，用于「入口显示或不显示」。**判定权仍在服务端**。
 */
export function resolveAttachmentWindow(
  state: FormWriteStateCode | null | undefined,
  subject: AttachmentSubject,
): AttachmentWindow {
  const stateOpen = state === 'DRAFT' || state === 'PENDING_SUPPLEMENT'
  const identityOk = subject.isInitiator || subject.isAdmin
  const serverDecides = '服务端仍是边界（越权上传/删除按 40304 / 403 拒绝）。'

  if (state === null || state === undefined) {
    return {
      stateOpen: false,
      canUpload: false,
      canDeleteOwn: false,
      reason: `尚未取得服务端三态白名单：按只读渲染附件入口。${serverDecides}`,
    }
  }
  if (!stateOpen) {
    const detail = state === 'APPROVING'
      ? '审批中附件只读'
      : state === 'CLOSED'
        ? '单据已完结，附件只读'
        : '当前状态下附件只读'
    return {
      stateOpen: false,
      canUpload: false,
      canDeleteOwn: false,
      reason: `${detail}：三态白名单只允许**草稿**与**待补件**上传/删除附件（服务端按 40304 拒绝）；` +
        `现在只能查看、下载与预览。${serverDecides}`,
    }
  }
  if (!identityOk) {
    return {
      stateOpen: true,
      canUpload: false,
      canDeleteOwn: false,
      reason: '只有**发起人本人或系统管理员**可以上传/删除该单据的附件（服务端 `requireInitiatorOrAdmin`）；' +
        `你当前既不是发起人也不是系统管理员。${serverDecides}`,
    }
  }
  return {
    stateOpen: true,
    canUpload: true,
    canDeleteOwn: true,
    reason: state === 'PENDING_SUPPLEMENT'
      ? `待补件期：附件与补件说明是唯一可写项（本次上传计入第轮次 round）。${serverDecides}`
      : `草稿期：全部字段可写，附件可上传/删除（上传的 round=0，属原始附件）。${serverDecides}`,
  }
}

/** 单个附件的删除入口结论 */
export interface AttachmentDeleteVerdict {
  allowed: boolean
  reason: string
}

/**
 * 是否可以**显示**某个附件的删除入口。
 *
 * <p>任务书口径：**仅上传者本人 + 可写窗口内**才给删除入口。
 * 服务端另允许系统管理员删除（`isAdmin`），但界面**不为管理员扩大入口**
 * ——避免在他人单据上误删；管理员需要时走接口或由上传者本人操作（已在面板文案里声明）。
 */
export function canDeleteAttachment(
  file: Pick<AttachmentFile, 'uploaderId' | 'fileName'>,
  subject: AttachmentSubject,
  window: AttachmentWindow,
): AttachmentDeleteVerdict {
  if (!window.stateOpen) {
    return { allowed: false, reason: window.reason }
  }
  if (!subject.isInitiator && !subject.isAdmin) {
    return { allowed: false, reason: window.reason }
  }
  if (file.uploaderId === '' || file.uploaderId !== subject.userId) {
    return {
      allowed: false,
      reason: '该附件不是你上传的：服务端只允许上传者本人或系统管理员删除（否则 40310）——' +
        '界面只对上传者本人开放删除入口。',
    }
  }
  return { allowed: true, reason: '' }
}

// ================================================================ 预览判定

/** 预览入口结论 */
export interface AttachmentPreviewVerdict {
  /** 是否给出「预览」入口（仅 jpeg/png/pdf 且 ≤20MB） */
  inline: boolean
  /** 面向用户的说明（含降级原因与服务端裁决口径） */
  reason: string
}

/**
 * 预览入口判定（**镜像** `AttachmentController#stream` 的内联条件：
 * MIME ∈ {image/jpeg, image/png, application/pdf} ∧ 体积 ≤ `maxInlinePreviewBytes`）。
 *
 * <p>返回 `inline=false` 时界面**只给下载**，并把原因讲清楚（含 heic 的**未实现**说明）；
 * 返回 `true` 时仍以服务端响应头 `Content-Disposition` 为准（服务端可能进一步降级）。
 */
export function resolveAttachmentPreview(
  file: Pick<AttachmentFile, 'fileExt' | 'mimeType' | 'fileSizeBytes' | 'fileName'>,
): AttachmentPreviewVerdict {
  const ext = (file.fileExt || extensionOf(file.fileName)).toLowerCase()
  const mime = (file.mimeType ?? '').toLowerCase()
  const size = file.fileSizeBytes

  if (ext === 'heic' || mime === 'image/heic' || mime === 'image/heif') {
    return {
      inline: false,
      reason: `${ATTACHMENT_HEIC_PREVIEW_NOTE}——后端本轮**未实现** heic→jpg 转码，` +
        '`GET /preview` 对 heic 返回 `Content-Disposition: attachment`（模板文案里的「heic 转 jpg 预览」尚未落地）。',
    }
  }
  const extAllowed = ATTACHMENT_INLINE_EXT.includes(ext)
  const mimeAllowed = mime !== '' && ATTACHMENT_INLINE_MIME.includes(mime)
  if (!extAllowed && !mimeAllowed) {
    return {
      inline: false,
      reason: `${ext === '' ? '未知' : ext} 格式不在内联白名单（服务端仅对 jpeg / png / pdf 内联），` +
        '`GET /preview` 会返回 `Content-Disposition: attachment` 降级 —— 请下载查看。',
    }
  }
  if (size !== null && size > ATTACHMENT_MAX_INLINE_PREVIEW_BYTES) {
    return {
      inline: false,
      reason: `超过 ${ATTACHMENT_MAX_INLINE_PREVIEW_BYTES / 1024 / 1024}MB，服务端预览接口强制降级为下载` +
        '（`oa.attachment.max-inline-preview-bytes`）—— 请下载查看。',
    }
  }
  return {
    inline: true,
    reason: '服务端仅对 jpeg / png / pdf 且 ≤20MB 内联预览；实际是否内联以响应头 `Content-Disposition` 为准。',
  }
}

// ================================================================ Content-Disposition

/**
 * 解析 `Content-Disposition` 里的文件名（RFC 5987 `filename*=UTF-8''...` 优先，
 * 回落 ASCII `filename="..."`）。
 *
 * <p>后端 `AttachmentController#contentDisposition` 同时给两个：ASCII 回退名（非 ASCII
 * 字符被替换成 `_`）与 `filename*`（百分号编码的真名）。**优先取真名**。
 * 解析失败返回空串，由调用方回落到本地已知文件名。
 */
export function fileNameFromContentDisposition(header: string | null | undefined): string {
  const raw = (header ?? '').trim()
  if (raw === '') return ''
  const extended = raw.match(/filename\*\s*=\s*([^;]+)/i)
  if (extended) {
    let value = extended[1].trim()
    // 形如 UTF-8''%E9%99%84%E4%BB%B6.png（语言可省略）
    const quote = value.indexOf("''")
    if (quote >= 0) value = value.slice(quote + 2)
    value = value.replace(/^["']|["']$/g, '')
    try {
      return decodeURIComponent(value)
    } catch {
      return value
    }
  }
  const plain = raw.match(/filename\s*=\s*"([^"]*)"/i) ?? raw.match(/filename\s*=\s*([^;]+)/i)
  if (plain) return plain[1].trim()
  return ''
}

/**
 * `Content-Disposition` 是否内联渲染（服务端的**裁决结果**）。
 *
 * <p>只看第一个分号前的 disposition 类型，不被文件名里的子串干扰。
 */
export function isInlineDisposition(header: string | null | undefined): boolean {
  const raw = (header ?? '').trim().toLowerCase()
  if (raw === '') return false
  return raw.split(';')[0].trim() === 'inline'
}

// ================================================================ 清单展示辅助

/** 附件是否由本人上传（界面标记「我上传的」） */
export function isUploadedByMe(file: Pick<AttachmentFile, 'uploaderId'>, userId: string): boolean {
  return userId !== '' && file.uploaderId === userId
}
