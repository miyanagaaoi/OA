<script setup lang="ts">
/**
 * oa-web · 附件面板（上传 / 按 round 分组的清单 / 鉴权下载与预览 / 删除）
 * ----------------------------------------------------------------------------
 * 阶段 2b.7：把 `POST|GET /forms/instances/{id}/attachments`、
 * `GET /forms/attachments/{id}/download|preview`、`DELETE /forms/attachments/{id}`
 * 落成**真正可用**的界面；本组件是这套交互的唯一落点，`FormFieldControl` 的
 * `attachment` 分支只负责把它挂进字段里。
 *
 * 来源：
 *   · `doc/forms.md` §1.4（单文件 ≤50MB / 单次 ≤20 个 / 单据合计 ≤50 个（含补件）/
 *     15 种允许 + 9 种禁止 / **下载必须经鉴权接口，禁止直链** / 私有化存储）
 *   · `doc/forms.md` §1.2 与 §7（三态：**草稿与待补件可传**，审批中/已完结只读）
 *   · `doc/enums.md` §12.1（15 种允许）、§12.2（9 种禁止「上传即拒绝」）、
 *     §12.3（轮次：0=原始附件，1..3=第 N 次补件）
 *   · `doc/templates.md` §2.3（`rules[filePolicy]` 可收窄 maxSizeMb/maxCount/allowExt/denyExt）
 *   · `doc/prd-0.1.md` AC-45（上传 exe / 超 50MB / 第 21 个被拒；鉴权接口下载并留痕）
 *   · 后端：`AttachmentController` / `AttachmentService` / `AttachmentPolicy`
 *
 * 五条口径（本组件的实现顺序就是它们）：
 *   1. **服务端是裁决方**：前端预检只给**即时提示**（扩展名 / 大小 / 数量），
 *      三层校验里的 **MIME 与文件头魔数**前端根本做不到，因此提示里必须写着这一句，
 *      且失败时展示的是**服务端原文**（不改写、不吞掉）。
 *   2. **逐文件上传**：服务端对一次请求是「全量校验，任一不合格即整体拒绝」，
 *      因此这里逐个文件分别请求，才能给出「成功几个 / 失败哪个 / 为什么」。
 *   3. **下载与预览都走鉴权接口**：用 `downloadUrl` / `previewUrl` 的 id 路径，
 *      **不拼直链、不碰 storagePath**；是否内联由服务端 `Content-Disposition` 决定，
 *      前端只按内联白名单决定**要不要给预览入口**。
 *   4. **只读态不给入口**：审批中 / 已完结（以及详情页只读视图）**不显示**上传与删除，
 *      并写明原因，不让用户白点一下再被 40304 拒。
 *   5. **heic 如实标注**：后端本轮**未实现** heic→jpg 转码，预览接口对 heic 返回
 *      `attachment` 降级 ⇒ 界面写「heic 暂不支持在线预览，请下载查看」，
 *      **不照抄**模板 `filePolicy.message` 里那句「heic 转 jpg 预览」。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApiError } from '@/api/http'
import {
  deleteAttachment,
  fetchAttachmentBlob,
  fetchAttachmentList,
  uploadAttachments,
} from '@/api/attachment'
import {
  ATTACHMENT_SERVER_DECIDES_NOTE,
  attachmentErrorHint,
  canDeleteAttachment,
  describeAttachmentBounds,
  formatAttachmentTime,
  formatBytes,
  groupByRound,
  isUploadedByMe,
  precheckAttachmentFiles,
  resolveAttachmentBounds,
  resolveAttachmentPreview,
  resolveAttachmentWindow,
  roundLabel,
  type AttachmentPrecheck,
  type AttachmentSubject,
} from '@/utils/attachment'
import type { FormJsonValue, FormWriteStateCode } from '@/types/form'
import type {
  AttachmentBounds,
  AttachmentFile,
  AttachmentUploadOutcome,
} from '@/types/attachment'

const props = defineProps<{
  /** 单据 id（发起页在「保存草稿」之前为空串：接口无处可挂，面板如实说明） */
  instanceId: string
  /** 附件字段码（必须是实例**锁定版本** schema 里已登记的 file/files 字段） */
  fieldCode: string
  /** 字段名（提示文案用） */
  fieldLabel: string
  /**
   * 该字段当前是否可写（渲染器 `resolveFieldReadonlyReason` 的净结论：
   * 三态白名单 ∧ 强制只读）。
   */
  writable: boolean
  /** 三态码（用于给出**附件专属**的只读原因） */
  stateCode: FormWriteStateCode | null
  /** 当前登录人 id（删除入口的判据：只能删自己传的） */
  currentUserId: string
  /** 身份：发起人 / 系统管理员 / 其他（镜像后端 `requireInitiatorOrAdmin`） */
  identity: 'initiator' | 'admin' | 'other'
  /** 模板 `filePolicy` 参数（schema `ruleParams.filePolicy`；缺省则回落全局档） */
  filePolicy: FormJsonValue | undefined
}>()

// ---------------------------------------------------------------------------
// 档位（生效上限）：服务端上传响应回填（**权威**）→ 模板声明 → 全局缺省
// ---------------------------------------------------------------------------
/**
 * 生效档位。
 *
 * <p>三级来源，优先级递增：**全局缺省 → 模板 `filePolicy` 声明 → 服务端上传响应 `bounds`**。
 * 最后一份是 `AttachmentPolicy` 判定时用的那一份（模板收窄已在服务端算过），
 * 因此每次上传成功后都用它覆盖，并在界面上写明来源（`describeAttachmentBounds`）。
 * 换实例 / 换字段时服务端那份作废（回到模板声明）。
 */
const serverBounds = ref<AttachmentBounds | null>(null)
const bounds = computed<AttachmentBounds>(() =>
  serverBounds.value ?? resolveAttachmentBounds(props.filePolicy),
)

/** 服务端在**上传响应**里下发过的单据累计数量（含补件；未上传过则为 `null`） */
const serverInstanceCount = ref<number | null>(null)

const boundsText = computed(() => describeAttachmentBounds(bounds.value))

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------
const files = ref<AttachmentFile[]>([])
const loading = ref(false)
const deletingId = ref('')
const uploading = ref(false)
const dragActive = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

/** 已选择的文件（浏览器 `File`；预检入参只用 name/size，故判定逻辑可脱离 DOM 自测） */
const selection = ref<File[]>([])
const accepted = ref<File[]>([])
const precheck = ref<AttachmentPrecheck | null>(null)
const outcomes = ref<AttachmentUploadOutcome[]>([])

/** 服务端错误（原文 + 码 + traceId + 处置提示） */
interface ServerProblem {
  code: number | string | null
  message: string
  traceId: string
  hint: string
}
const listProblem = ref<ServerProblem | null>(null)
const actionProblem = ref<ServerProblem | null>(null)

const groups = computed(() => groupByRound(files.value))
const total = computed(() => files.value.length)

/** 身份/状态窗口（镜像后端判据；服务端仍是边界） */
const subject = computed<AttachmentSubject>(() => ({
  userId: props.currentUserId,
  isInitiator: props.identity === 'initiator',
  isAdmin: props.identity === 'admin',
}))
const writeWindow = computed(() => resolveAttachmentWindow(props.stateCode, subject.value))

/** 上传入口是否可见 */
const canUpload = computed(
  () => props.writable && props.instanceId !== '' && writeWindow.value.canUpload,
)

/**
 * 上传/删除入口不可见时的**原因**（区分四种成因，绝不只灰掉不说）。
 *
 * 顺序即语义：
 *   ① 尚无实例（发起页未保存草稿）→ 接口需要实例 id；
 *   ② 三态不可写（审批中/已完结）→ 40304 口径；
 *   ③ 可写但页面是只读视图（详情页）→ 去填单页操作；
 *   ④ 可写但身份不符（非发起人且非管理员）→ 403 口径。
 */
const readonlyNotice = computed(() => {
  if (props.instanceId === '') {
    return '新建草稿尚未生成实例 id：附件接口挂在 `POST /forms/instances/{id}/attachments` 上，' +
      '请先「保存草稿」建出实例，再在实例填单页上传附件。'
  }
  if (!props.writable && !writeWindow.value.stateOpen) return writeWindow.value.reason
  if (!props.writable) {
    return '当前页面是只读视图（如单据详情）：附件只能查看、下载与预览；' +
      '上传与删除在「填单页」按服务端可写字段白名单与三态窗口开放。'
  }
  return writeWindow.value.reason
})

/**
 * 删除入口（2026-10-05 裁定：**可删 = 上传者本人 ∪ 单据发起人本人 ∪ 系统管理员**，
 * 且仅草稿/待补件窗口；判定见 `utils/attachment.ts#canDeleteAttachment`）。
 *
 * <p>`writable` 再与一次：详情页等**只读视图**即使窗口开着也不给删除入口。
 * 服务端仍是裁决方 —— 被拒时 40310 的**原文照旧展示**，这里只决定渲不渲染按钮。
 */
function deleteVerdict(file: AttachmentFile) {
  const verdict = canDeleteAttachment(file, subject.value, writeWindow.value)
  return {
    allowed: props.writable && verdict.allowed,
    capacity: verdict.capacity,
    reason: verdict.reason,
  }
}

function previewVerdict(file: AttachmentFile) {
  return resolveAttachmentPreview(file)
}

// ---------------------------------------------------------------------------
// 取数
// ---------------------------------------------------------------------------
onMounted(async () => {
  await reload()
})

watch(
  () => [props.instanceId, props.fieldCode],
  async () => {
    // 换实例 / 换字段：服务端下发过的档位与累计数量都属于上一个单据，必须作废
    serverBounds.value = null
    serverInstanceCount.value = null
    files.value = []
    resetSelection()
    await reload()
  },
)

/** 拉清单（失败时**原样**展示服务端文案：403 数据域 / 40301 权限 / 网络异常） */
async function reload(): Promise<void> {
  if (props.instanceId === '') return
  loading.value = true
  listProblem.value = null
  try {
    const list = await fetchAttachmentList(props.instanceId)
    files.value = list.rounds.flatMap((group) => group.files)
  } catch (error) {
    files.value = []
    listProblem.value = describe(error)
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------------------
// 选择与预检
// ---------------------------------------------------------------------------
function pickFiles(): void {
  fileInput.value?.click()
}

function onFileInput(event: Event): void {
  const input = event.target as HTMLInputElement
  applySelection(input.files ? Array.from(input.files) : [])
  // 允许重复选择同一个文件（否则第二次选择不会触发 change）
  input.value = ''
}

function onDrop(event: DragEvent): void {
  dragActive.value = false
  const dropped = event.dataTransfer?.files
  if (!dropped || dropped.length === 0) return
  applySelection(Array.from(dropped))
}

function applySelection(picked: File[]): void {
  selection.value = picked
  actionProblem.value = null
  outcomes.value = []
  recomputePrecheck()
}

function resetSelection(): void {
  selection.value = []
  accepted.value = []
  precheck.value = null
  outcomes.value = []
}

/**
 * 预检（**即时提示**）：逐文件判扩展名与大小，整批判数量档位。
 *
 * <p>判定与文案全在 `utils/attachment.ts#precheckAttachmentFiles`（可脱离 Vue 自测）；
 * 本处只负责把结论铺到界面上，并保留「服务端是裁决方」的声明。
 */
function recomputePrecheck(): void {
  const picked = selection.value
  const result = precheckAttachmentFiles(
    picked.map((file) => ({ name: file.name, size: file.size })),
    {
      bounds: bounds.value,
      // 字段累计用本地清单条数（服务端 `fieldCount` 与它同源）；
      // 单据累计优先用服务端上传响应下发过的 `instanceCount`（别的字段也可能挂了附件）
      existingInField: total.value,
      existingInInstance: serverInstanceCount.value ?? total.value,
    },
  )
  precheck.value = result
  accepted.value = picked.filter((_file, index) => result.items[index]?.ok === true)
}

// ---------------------------------------------------------------------------
// 上传
// ---------------------------------------------------------------------------
const uploadSummary = computed(() => {
  if (outcomes.value.length === 0) return ''
  const ok = outcomes.value.filter((item) => item.ok).length
  return `本次上传 ${outcomes.value.length} 个文件：成功 ${ok} 个，失败 ${outcomes.value.length - ok} 个（逐文件结果见下）`
})

async function doUpload(): Promise<void> {
  if (!canUpload.value || accepted.value.length === 0) return
  uploading.value = true
  actionProblem.value = null
  outcomes.value = []
  const batch = accepted.value
  try {
    const result = await uploadAttachments(props.instanceId, props.fieldCode, batch)
    // 逐文件结果**保留在界面上**（成功几个、失败哪个、为什么），因此这里只清空选择
    selection.value = []
    accepted.value = []
    precheck.value = null
    outcomes.value = result.outcomes
    if (result.bounds) serverBounds.value = result.bounds
    if (result.instanceCount !== null) serverInstanceCount.value = result.instanceCount
    if (result.succeeded > 0) {
      await reload()
      ElMessage({
        type: result.failed === 0 ? 'success' : 'warning',
        message:
          `已上传 ${result.succeeded} 个附件` +
          `${result.failed > 0 ? `，${result.failed} 个被服务端拒绝（逐条原因见下）` : ''}`,
      })
    }
  } catch (error) {
    // 走到这里说明是**整批级**异常（如网络中断）：逐文件结论在 uploadAttachments 内部已收集
    actionProblem.value = describe(error)
  } finally {
    uploading.value = false
  }
}

// ---------------------------------------------------------------------------
// 下载 / 预览
// ---------------------------------------------------------------------------
async function doDownload(file: AttachmentFile): Promise<void> {
  actionProblem.value = null
  try {
    const content = await fetchAttachmentBlob(file.id, 'download')
    saveBlob(content.blob, content.fileName || file.fileName)
    ElMessage({
      type: 'success',
      message:
        `已下载《${content.fileName || file.fileName}》（${formatBytes(content.byteLength)}；` +
        `服务端 Content-Disposition=${content.inline ? 'inline' : 'attachment'}）`,
    })
  } catch (error) {
    actionProblem.value = describe(error)
  }
}

async function doPreview(file: AttachmentFile): Promise<void> {
  actionProblem.value = null
  try {
    const content = await fetchAttachmentBlob(file.id, 'preview')
    if (content.inline) {
      const url = URL.createObjectURL(content.blob)
      const popup = window.open(url, '_blank', 'noopener')
      if (popup === null) {
        // 弹窗被拦截：**不要把这一次点击变成「什么都没发生」** —— 退回下载并说明
        saveBlob(content.blob, content.fileName || file.fileName)
        ElMessage({
          type: 'warning',
          message: '浏览器拦截了新标签（内联预览无法打开），已改为下载；请允许本站弹窗后重试预览。',
        })
        return
      }
      // 关闭新标签后浏览器仍持有该 blob；延迟回收（过早回收会让预览白屏）
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
      return
    }
    // 服务端降级（非内联白名单 / 超 20MB）：按下载处理，并说明**这是服务端的裁决**
    saveBlob(content.blob, content.fileName || file.fileName)
    ElMessage({
      type: 'warning',
      message:
        `服务端对《${content.fileName || file.fileName}》返回 attachment（未内联：` +
        `${previewVerdict(file).reason}），已改为下载。`,
    })
  } catch (error) {
    actionProblem.value = describe(error)
  }
}

/** 用 Blob 触发浏览器下载（文件名优先取服务端 `Content-Disposition` 里的真名） */
function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName || 'attachment'
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

// ---------------------------------------------------------------------------
// 删除
// ---------------------------------------------------------------------------
async function doDelete(file: AttachmentFile): Promise<void> {
  if (!deleteVerdict(file).allowed) return
  try {
    await ElMessageBox.confirm(
      `确认删除《${file.fileName}》（${formatBytes(file.fileSizeBytes)}，${roundLabel(file.round)}）？` +
        '服务端先删物理文件、再删元数据行（删除后不可恢复，轨迹会记 attachment_delete）。',
      '确认删除附件',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  deletingId.value = file.id
  actionProblem.value = null
  try {
    const result = await deleteAttachment(file.id)
    ElMessage({
      type: 'success',
      message:
        `已删除《${result.fileName || file.fileName}》` +
        (result.physicalFileRemoved
          ? '（服务端确认物理文件已移除）'
          : '（服务端未确认物理文件已移除，请留意）'),
    })
    await reload()
    if (serverInstanceCount.value !== null) {
      serverInstanceCount.value = Math.max(0, serverInstanceCount.value - 1)
    }
  } catch (error) {
    actionProblem.value = describe(error)
  } finally {
    deletingId.value = ''
  }
}

// ---------------------------------------------------------------------------
// 错误呈现（**服务端原文** + 业务码 + traceId + 处置提示）
// ---------------------------------------------------------------------------
/**
 * 异常 → 可直接展示的四段式。
 *
 * ⚠ `message` 永远是**服务端原文**（`ApiError#message` 原样透传，不改写、不吞掉）；
 * `hint` 只是**追加**的处置说明（`utils/attachment.ts#attachmentErrorHint`），
 * 用于把「40304 / 40310 / 40402」这类码翻译成「该怎么办」。
 */
function describe(error: unknown): ServerProblem {
  if (error instanceof ApiError) {
    const numeric = typeof error.code === 'string' ? Number(error.code) : error.code
    return {
      code: error.code,
      message: error.message,
      traceId: error.traceId ?? '',
      hint: attachmentErrorHint(numeric),
    }
  }
  return {
    code: null,
    message: error instanceof Error ? error.message : '请求失败',
    traceId: '',
    hint: '',
  }
}

/** 逐文件结果里的错误码提示（模板里直接调用） */
function outcomeHint(code: number | string | null): string {
  const numeric = typeof code === 'string' ? Number(code) : code
  return attachmentErrorHint(numeric)
}
</script>

<template>
  <div class="attach-panel">
    <!-- ============ 口径与档位（如实披露：谁定的上限、谁裁决） ============ -->
    <p class="meta">
      附件接口：<span class="oa-mono">POST|GET /forms/instances/{id}/attachments</span> ·
      <span class="oa-mono">GET /forms/attachments/{id}/download|preview</span> ·
      <span class="oa-mono">DELETE /forms/attachments/{id}</span>
      （下载与预览一律走**鉴权接口**，不拼直链、不暴露存储路径）
    </p>
    <p class="meta">生效档位：{{ boundsText }}</p>
    <p v-if="bounds.declaredMessage" class="meta">
      模板 <span class="oa-mono">filePolicy.message</span>（原样）：{{ bounds.declaredMessage }}
      <span class="tag-warn">
        其中「heic 转 jpg 预览」**本轮未落地**——后端未实现转码，heic 请下载查看
      </span>
    </p>
    <p class="hint capability">{{ ATTACHMENT_SERVER_DECIDES_NOTE }}</p>

    <!-- ============ 上传（仅可写窗口 ∧ 有实例 ∧ 身份符合时出现） ============ -->
    <section v-if="canUpload" class="uploader">
      <div
        class="drop"
        :class="{ 'is-active': dragActive }"
        role="button"
        tabindex="0"
        @click="pickFiles"
        @keydown.enter.prevent="pickFiles"
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="onDrop"
      >
        <b>拖拽文件到此处，或点击选择（支持多选）</b>
        <span class="meta">
          单次 ≤ {{ bounds.maxPerUpload }} 个 · 单文件 ≤ {{ bounds.maxSizeMb }}MB ·
          允许：{{ bounds.allowExt.join('/') }} · 禁止：{{ bounds.denyExt.join('/') }}
        </span>
        <!-- 刻意**不加** accept 过滤：让用户能选中任何文件，由下方预检给出即时原因，
             而不是被浏览器的文件选择器静默挡掉（那样用户只会以为「选不了」）。 -->
        <input ref="fileInput" class="file-input" type="file" multiple @change="onFileInput" />
      </div>

      <div v-if="selection.length > 0" class="precheck">
        <p class="precheck-head">
          前端预检（{{ selection.length }} 个文件，通过 {{ accepted.length }} 个）：
          <span class="meta">只挑得出「扩展名 / 大小 / 数量」这三件事，MIME 与文件头魔数只有服务端能验</span>
        </p>
        <ul>
          <li
            v-for="(item, index) in precheck?.items ?? []"
            :key="`${item.name}-${index}`"
            :class="{ 'is-error': !item.ok }"
          >
            <span class="oa-mono">{{ item.name }}</span>
            <span class="meta">{{ formatBytes(item.size) }}</span>
            <span v-if="item.ok" class="ok">前端预检通过（服务端仍需三层校验）</span>
            <span v-else class="bad">{{ item.reason }}</span>
          </li>
        </ul>
        <ul v-if="(precheck?.batchMessages.length ?? 0) > 0" class="batch">
          <li v-for="(message, index) in precheck?.batchMessages ?? []" :key="index">{{ message }}</li>
        </ul>
        <!-- 预检的**语义说明**（不与"违规"混在一起）：逐文件上传 vs 单次上限 -->
        <ul v-if="(precheck?.notes.length ?? 0) > 0" class="notes">
          <li v-for="(note, index) in precheck?.notes ?? []" :key="index">{{ note }}</li>
        </ul>
        <div class="row">
          <button
            class="btn btn-primary"
            type="button"
            :disabled="uploading || accepted.length === 0"
            @click="doUpload"
          >
            {{ uploading ? '上传中…（请勿关闭页面）' : `上传 ${accepted.length} 个文件` }}
          </button>
          <button class="btn btn-secondary" type="button" :disabled="uploading" @click="resetSelection">
            清空选择
          </button>
          <span class="meta">
            上传按**逐文件**分别请求：服务端对一次请求是「任一不合格即整体拒绝」，
            拆开才能给出「成功几个 / 失败哪个 / 为什么」。
          </span>
        </div>
      </div>
    </section>

    <!-- 不可上传的原因（不是灰掉了事） -->
    <p v-else class="readonly-notice">{{ readonlyNotice }}</p>

    <!-- ============ 上传结果（逐文件） ============ -->
    <section v-if="outcomes.length > 0" class="outcomes">
      <p class="outcomes-head">{{ uploadSummary }}</p>
      <ul>
        <li
          v-for="(item, index) in outcomes"
          :key="`${item.fileName}-${index}`"
          :class="{ 'is-error': !item.ok }"
        >
          <span class="oa-mono">{{ item.fileName }}</span>
          <template v-if="item.ok">
            <span class="ok">
              已落库（附件 id={{ item.attachment?.id ?? '—' }}，round={{ item.attachment?.round ?? 0 }}）
            </span>
          </template>
          <template v-else>
            <span class="bad">
              失败<template v-if="item.code !== null">（业务码 {{ item.code }}）</template>：{{ item.message }}
            </span>
            <span v-if="outcomeHint(item.code)" class="meta">{{ outcomeHint(item.code) }}</span>
            <span v-if="item.traceId" class="meta oa-mono">追踪号 {{ item.traceId }}</span>
          </template>
        </li>
      </ul>
    </section>

    <!-- ============ 服务端错误原文（不做改写） ============ -->
    <section v-if="actionProblem" class="problem">
      <p class="problem-title">操作被拒绝</p>
      <p class="problem-text">
        服务端原文<template v-if="actionProblem.code !== null">（业务码 {{ actionProblem.code }}）</template>：{{ actionProblem.message }}
      </p>
      <p v-if="actionProblem.hint" class="meta">{{ actionProblem.hint }}</p>
      <p v-if="actionProblem.traceId" class="meta oa-mono">追踪号 {{ actionProblem.traceId }}</p>
    </section>

    <!-- ============ 清单（按 round 分组） ============ -->
    <section class="list">
      <p class="list-head">
        附件清单
        <span class="meta">
          共 {{ total }} 个（单据合计上限 {{ bounds.maxPerInstance }} 个含补件）·
          按 round 分组：0 = 原始附件，1..3 = 第 N 次补件（doc/enums.md §12.3）
        </span>
      </p>

      <p v-if="loading" class="meta">正在取附件清单…</p>

      <template v-else-if="listProblem">
        <p class="problem-text">
          清单取数失败<template v-if="listProblem.code !== null">（业务码 {{ listProblem.code }}）</template>：{{ listProblem.message }}
        </p>
        <p v-if="listProblem.hint" class="meta">{{ listProblem.hint }}</p>
      </template>

      <p v-else-if="total === 0" class="meta">尚无附件（服务端清单为空）。</p>

      <div v-for="group in groups" v-else :key="group.round" class="round">
        <p class="round-head">
          <b>{{ roundLabel(group.round) }}</b>
          <span class="meta">round={{ group.round }} · {{ group.files.length }} 个</span>
        </p>
        <ul>
          <li v-for="file in group.files" :key="file.id">
            <span class="name" :title="file.fileName">{{ file.fileName }}</span>
            <span class="meta oa-mono">{{ formatBytes(file.fileSizeBytes) }}</span>
            <span class="tag">{{ file.fileExt || '—' }}</span>
            <span v-if="file.mimeType" class="meta oa-mono">{{ file.mimeType }}</span>
            <span class="meta">
              上传人 {{ file.uploaderId || '—' }}
              <template v-if="isUploadedByMe(file, currentUserId)">（我）</template>
            </span>
            <span class="meta">{{ formatAttachmentTime(file.uploadedAt) }}</span>

            <span class="actions">
              <button class="btn btn-ghost" type="button" @click="doDownload(file)">下载</button>
              <button
                v-if="previewVerdict(file).inline"
                class="btn btn-ghost"
                type="button"
                :title="previewVerdict(file).reason"
                @click="doPreview(file)"
              >
                预览
              </button>
              <span v-else class="tag" :title="previewVerdict(file).reason">
                仅下载{{ file.fileExt === 'heic' ? '（heic 暂不支持在线预览）' : '' }}
              </span>
              <button
                v-if="deleteVerdict(file).allowed"
                class="btn btn-ghost is-danger"
                type="button"
                :title="deleteVerdict(file).reason"
                :disabled="deletingId === file.id"
                @click="doDelete(file)"
              >
                {{ deletingId === file.id ? '删除中…' : '删除' }}
              </button>
              <span v-else class="meta" :title="deleteVerdict(file).reason">
                {{ props.writable ? '不可删（非上传者/发起人/管理员）' : '只读窗口' }}
              </span>
            </span>
          </li>
        </ul>
      </div>
    </section>

    <p class="hint">
      预览口径：服务端仅对 <span class="oa-mono">jpeg / png / pdf</span> 且 ≤20MB 内联渲染，
      其余（含 heic、wps、zip、office 文档）返回
      <span class="oa-mono">Content-Disposition: attachment</span> 降级 —— 界面据此只给下载并说明原因；
      实际动作以响应头为准。删除入口按**三档**开放：<b>上传者本人 / 单据发起人本人 / 系统管理员</b>
      （单据归发起人，他人代传的附件本人同样可删），且仅草稿 / 待补件窗口；
      服务端仍是裁决方，被拒时 40310 原文照旧展示。
    </p>
  </div>
</template>

<style scoped>
.attach-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--oa-space-xs);
  border: 1px dashed var(--oa-color-hairline-strong);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
  min-width: 0;
}

.meta {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.hint {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.hint.capability {
  padding: 2px 6px;
  border-left: 2px solid var(--oa-color-warning);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink-muted);
}

.tag-warn {
  margin-left: 4px;
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-warning);
  font: var(--oa-font-caption);
}

.uploader {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drop {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--oa-space-sm);
  border: 1px dashed var(--oa-color-primary-border);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas);
  cursor: pointer;
  font: var(--oa-font-body-sm);
}

.drop.is-active {
  border-color: var(--oa-color-primary);
  background: var(--oa-color-primary-subtle);
}

.file-input {
  display: none;
}

.precheck ul,
.outcomes ul,
.list ul {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.precheck-head,
.outcomes-head,
.list-head,
.round-head,
.problem-title {
  font: var(--oa-font-label);
  color: var(--oa-color-ink);
}

.precheck ul {
  margin-top: 4px;
}

.precheck li,
.outcomes li {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  font: var(--oa-font-body-sm);
}

.batch {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 4px 0 0;
  padding-left: 18px;
  color: var(--oa-color-warning);
  font: var(--oa-font-caption);
}

/* 预检的语义说明（不是违规）：与 batch 同形但用中性色，避免读成"又出错了" */
.notes {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 4px 0 0;
  padding-left: 18px;
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
}

.ok {
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
}

.bad {
  color: var(--oa-color-error);
  font: var(--oa-font-caption);
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
  align-items: center;
  margin-top: var(--oa-space-xs);
}

.readonly-notice {
  padding: 2px 6px;
  border-left: 2px solid var(--oa-color-hairline-strong);
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
}

.outcomes,
.problem {
  padding: var(--oa-space-xs);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas);
}

.problem {
  border-color: var(--oa-color-error);
}

.problem-text {
  color: var(--oa-color-error);
  font: var(--oa-font-body-sm);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.round {
  border-top: 1px solid var(--oa-color-hairline);
  padding-top: 4px;
}

.round-head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}

.list li {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  font: var(--oa-font-body-sm);
}

.name {
  max-width: 22ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: baseline;
  margin-left: auto;
}

.btn {
  height: var(--oa-space-control-compact);
  padding: 0 var(--oa-space-xs);
  border: 1px solid transparent;
  border-radius: var(--oa-radius-sm);
  font: var(--oa-font-button);
  white-space: nowrap;
  cursor: pointer;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.btn-primary {
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
}

.btn-primary:disabled {
  background: var(--oa-color-primary-border);
  color: var(--oa-color-ink-muted);
}

.btn-secondary {
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  border-color: var(--oa-color-hairline-strong);
}

.btn-ghost {
  background: transparent;
  color: var(--oa-color-primary);
}

.btn-ghost.is-danger {
  color: var(--oa-color-error);
}
</style>
