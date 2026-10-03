<script setup lang="ts">
/**
 * oa-web · 单据表单页（schema 驱动 · 四类共用）
 * ----------------------------------------------------------------------------
 * 两个入口共用本页：
 *   · `/form/new/:formType`            发起新单（用**当前已发布**版本的 schema）
 *   · `/form/instance/:instanceId`     继续填单 / 补件 / 重提（用**实例锁定版本**的 schema，AC-09）
 *
 * 来源：
 *   · `doc/forms.md` §11.1（表单由 `form_schema_json` 驱动，不在前端硬编码字段）、§11.2（服务端二次校验）
 *   · `doc/forms.md` §1.2（三态读写：草稿可写 / 审批中只读 / 待补件仅附件与补件说明）、
 *     §1.5（金额定点、禁浮点）、§5（印鉴单归还唯一例外）、§7/§8（补件）
 *   · `doc/templates.md` §3.2（V-02 在途实例锁版本 / V-03 提交时快照）
 *   · `doc/prd-0.1.md` §6.1（事项单分支：`involve_cost` → ②是否跳过）
 *   · 后端：`FormTemplateController` / `FormDataController` / `FormRuleController` /
 *     `FlowInstanceController`（预检 / 建草稿 / 提交）
 *
 * 交互顺序（顺序本身是口径）：
 *   ① 取 schema（按入口取 published 或实例锁定版本）→ ② 取字典（下拉取值的唯一来源）
 *   → ③ 取金额角色策略（`field-groups`）→ ④ 预填默认值（仅 boolean / select）
 *   → 保存：`PUT /forms/instances/{id}/draft?mode=DRAFT`（新建时是 `POST /flow-instances`）
 *   → 提交：干跑 `POST /forms/instances/{id}/validate?mode=SUBMIT`（**先拿到逐字段错误**）
 *     → `POST /flow-instances/precheck`（40007 逐条拦截项）→ `submit`。
 *
 * ⚠ 附件上传接口属**阶段 2b.7**：附件字段渲染为明确的「待接入」状态（不伪造上传）。
 * ⚠ 「抄送我的」列表、打印属**阶段 3**：本页不含相关入口。
 */
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FormRenderer from '@/components/FormRenderer.vue'
import { ApiError } from '@/api/http'
import {
  fetchDictItems,
  fetchFieldGroups,
  fetchFormDraft,
  fetchFormSchema,
  fetchInstanceSchema,
  fetchWritableFields,
  listFormTemplates,
  registerSealReturn,
  saveFormDraft,
  validateFormByType,
  validateFormInstance,
} from '@/api/form'
import {
  createFlowInstance,
  fetchFlowInstance,
  precheckFlowInstance,
  resubmitFlowInstance,
  submitFlowInstance,
  supplementSubmitFlowInstance,
} from '@/api/flow-task'
import { fetchDirectory } from '@/api/user'
import { fetchOrgSelector } from '@/api/org'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import {
  applyFieldDefaults,
  buildIssueIndex,
  errorHint,
  isSupplementState,
  parseValidationMessage,
  SUPPLEMENT_NOTE_FIELD,
  toFormDocType,
  toSubmitFields,
} from '@/utils/form-rules'
import { instancePillStatus, instanceStatusLabel, subStatusLabel } from '@/utils/flow-task'
import { FORM_DOC_TYPE_LABEL } from '@/utils/form-rules'
import type {
  FormDictCache,
  FormFieldIssue,
  FormJsonValue,
  FormOption,
  FormSchema,
  FormValidationReport,
  FormWriteState,
  FormAmountPolicy,
  FlowPrecheckReport,
  FormDocType,
} from '@/types/form'
import type { FlowInstance } from '@/types/flow-task'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

type Mode = 'create' | 'instance'

const mode = computed<Mode>(() => (route.name === 'form-new' ? 'create' : 'instance'))
const instanceId = computed(() => String(route.params.instanceId ?? ''))

/** 路由参数里的 formType（仅 create 模式有） */
const routeFormType = computed<FormDocType | null>(() => toFormDocType(String(route.params.formType ?? '')))

/**
 * 表单 schema 与字段值用 `shallowRef`：
 * 二者的类型里含**递归自由 JSON**（`FormJsonValue`），`ref` 的深度 `UnwrapRef` 会让
 * `vue-tsc` 报 TS2589（Type instantiation is excessively deep）。
 * 且本页对它们一律**整体替换**（schema 每次重新取数；值由 `FormRenderer` 发出新对象），
 * 不需要深层响应式，`shallowRef` 在语义与性能上都更贴切。
 */
const schema = shallowRef<FormSchema | null>(null)
const values = shallowRef<Record<string, FormJsonValue>>({})
const instance = ref<FlowInstance | null>(null)
const writeState = ref<FormWriteState | null>(null)
const amountPolicy = ref<FormAmountPolicy | null>(null)
const report = ref<FormValidationReport | null>(null)
const precheck = ref<FlowPrecheckReport | null>(null)
const dictCache = ref<FormDictCache>({})
const dictLoading = reactive<Record<string, boolean>>({})
const userOptions = ref<FormOption[]>([])
const orgOptions = ref<FormOption[]>([])
const pickerUnavailable = ref(false)
const supplementNote = ref('')
const sealReturn = reactive({ status: '', date: '' })

const loading = ref(false)
const busy = ref(false)
/** 整单级错误（无法归到具体字段：40304 / 40308 / 40309 / 预检 40007 等） */
const pageError = ref('')
const pageErrorHint = ref('')
/** 未绑定到字段的校验文本 */
const unboundErrors = ref<string[]>([])

/** 已发布模板摘要（create 模式用来确认「该类单据有已发布模板」并取版本号） */
const templateInfo = reactive({ templateCode: '', schemaVersion: 0, published: false, message: '' })

const formType = computed<FormDocType | null>(() => {
  if (mode.value === 'create') return routeFormType.value
  return toFormDocType(instance.value?.formType) ?? toFormDocType(schema.value?.formType)
})

const formTypeLabel = computed(() => (formType.value ? FORM_DOC_TYPE_LABEL[formType.value] : '未知单据类型'))

const errors = computed<Record<string, FormFieldIssue[]>>(() => buildIssueIndex(report.value))

/** 三态标签（服务端未下发时按状态推导） */
const stateLabel = computed(() => writeState.value?.stateLabel ?? '尚未取得可写字段白名单（只读渲染）')

/** 待补件态：补件说明是**系统字段**（`FormPayloadValidator.SYSTEM_FIELDS`），不在 schema 里 */
const inSupplement = computed(() => isSupplementState(writeState.value))

/** 印鉴单归还登记通道是否可见（三态唯一例外；服务端白名单说了算） */
const sealReturnWritable = computed(() => {
  const writable = writeState.value?.writableFields ?? []
  return formType.value === 'seal' && (writable.includes('return_status') || writable.includes('return_date'))
})

const supplementNoteWritable = computed(() => (writeState.value?.writableFields ?? []).includes(SUPPLEMENT_NOTE_FIELD))

/** 是否存在任何可写字段（决定「保存」按钮是否出现） */
const hasWritableField = computed(() => (writeState.value?.writableFields ?? []).length > 0)

const isInitiator = computed(
  () => writeState.value?.isInitiator === true || instance.value?.initiatorId === userStore.user?.userId,
)

// ---------------------------------------------------------------------------
// 加载
// ---------------------------------------------------------------------------
onMounted(async () => {
  await load()
})

watch(
  () => [route.name, route.params.formType, route.params.instanceId],
  async () => {
    await load()
  },
)

async function load(): Promise<void> {
  loading.value = true
  resetPage()
  try {
    if (mode.value === 'create') {
      await loadCreate()
    } else {
      await loadInstance()
    }
  } catch (error) {
    applyError(error)
  } finally {
    loading.value = false
  }
}

function resetPage(): void {
  pageError.value = ''
  pageErrorHint.value = ''
  unboundErrors.value = []
  report.value = null
  precheck.value = null
}

/** 发起模式：确认已发布模板 → 取 published schema → 取字段分组（金额策略） */
async function loadCreate(): Promise<void> {
  const type = routeFormType.value
  if (!type) {
    pageError.value = `未知的单据类型「${String(route.params.formType ?? '')}」（doc/enums.md §10.2 只认 matter / fund / contract / seal）`
    return
  }
  const templates = await listFormTemplates()
  const info = templates.find((item) => item.formType === type)
  templateInfo.templateCode = info?.templateCode ?? ''
  templateInfo.schemaVersion = info?.schemaVersion ?? 0
  templateInfo.published = info?.published === true
  templateInfo.message = info?.message ?? ''
  if (!templateInfo.published) {
    pageError.value = `单据类型「${FORM_DOC_TYPE_LABEL[type]}」当前没有已发布（published）的流程模板版本，无法发起`
    pageErrorHint.value = templateInfo.message || '请在「流程模板」里发布该类型的一个版本后再发起（doc/templates.md §3.3）'
    return
  }
  const loaded = await fetchFormSchema(type)
  schema.value = loaded
  amountPolicy.value = await loadAmountPolicy(type)
  await loadDicts(loaded)
  await loadPickers(loaded)
  values.value = applyFieldDefaults(loaded, {})
  writeState.value = null
  sealReturn.status = String(values.value.return_status ?? '')
  sealReturn.date = String(values.value.return_date ?? '')
}

/** 实例模式：取实例（锁定版本）→ schema → 草稿（值 + 三态）→ 可写字段 */
async function loadInstance(): Promise<void> {
  const id = instanceId.value
  if (!id) {
    pageError.value = '缺少单据 id'
    return
  }
  const detail = await fetchFlowInstance(id)
  instance.value = detail
  const loaded = await fetchInstanceSchema(id)
  schema.value = loaded
  const draft = await fetchFormDraft(id)
  const writable = await fetchWritableFields(id)
  writeState.value = writable.state
  values.value = applyFieldDefaults(loaded, { ...draft.snapshot.fields })
  supplementNote.value = String(draft.snapshot.fields[SUPPLEMENT_NOTE_FIELD] ?? '')
  sealReturn.status = String(draft.snapshot.fields.return_status ?? '')
  sealReturn.date = String(draft.snapshot.fields.return_date ?? '')
  const type = toFormDocType(detail.formType) ?? toFormDocType(loaded.formType)
  if (type) {
    amountPolicy.value = await loadAmountPolicy(type).catch(() => null)
  }
  await loadDicts(loaded)
  await loadPickers(loaded)
}

/** 金额角色策略（`GET /forms/{formType}/field-groups` 的 `amountPolicy`） */
async function loadAmountPolicy(type: FormDocType): Promise<FormAmountPolicy> {
  const groups = await fetchFieldGroups(type)
  return groups.amountPolicy
}

/** 字典：**下拉取值的唯一来源**（不得硬编码） */
async function loadDicts(loaded: FormSchema): Promise<void> {
  const types = Array.from(
    new Set(loaded.fields.map((field) => field.dictType).filter((item): item is string => Boolean(item))),
  )
  await Promise.all(
    types.map(async (dictType) => {
      dictLoading[dictType] = true
      try {
        dictCache.value = { ...dictCache.value, [dictType]: await fetchDictItems(dictType) }
      } catch (error) {
        const message = error instanceof ApiError ? error.message : '字典接口不可用'
        ElMessage({ type: 'warning', message: `字典 ${dictType} 取数失败：${message}` })
      } finally {
        dictLoading[dictType] = false
      }
    }),
  )
}

/**
 * 人员 / 组织选择器的数据源。
 *
 * `user` / `org` 字段出现时才取；取不到（如无通讯录权限）**不阻断填写**：
 * 降级为手填 id 并在控件下方说明（`pickerUnavailable`）。
 */
async function loadPickers(loaded: FormSchema): Promise<void> {
  const hasUser = loaded.fields.some((field) => field.control === 'user')
  const hasOrg = loaded.fields.some((field) => field.control === 'org')
  if (!hasUser && !hasOrg) return
  pickerUnavailable.value = false
  try {
    if (hasUser) {
      const page = await fetchDirectory({ page: 1, pageSize: 500 })
      userOptions.value = page.list.map((item) => ({
        value: item.userId,
        label: [item.name, item.employeeNo, item.orgName].filter((part) => Boolean(part)).join(' · '),
        enabled: true,
        sortNo: 0,
      }))
    }
    if (hasOrg) {
      const orgs = await fetchOrgSelector({})
      orgOptions.value = orgs.map((item) => ({
        value: item.id,
        label: `${item.name}（${item.path}）`,
        enabled: item.status !== 'disabled',
        sortNo: item.depth,
      }))
    }
  } catch {
    pickerUnavailable.value = true
    ElMessage({
      type: 'warning',
      message: '通讯录 / 组织选择器不可用，人员与组织字段已降级为手填 id',
    })
  }
}

// ---------------------------------------------------------------------------
// 动作
// ---------------------------------------------------------------------------
/** 保存草稿（create = 建草稿实例；instance = `PUT /draft?mode=DRAFT`） */
async function save(): Promise<void> {
  busy.value = true
  resetPage()
  try {
    if (mode.value === 'create') {
      await createDraft()
      return
    }
    const fields = collectFields('DRAFT')
    await saveFormDraft(instanceId.value, fields, 'DRAFT')
    ElMessage({ type: 'success', message: '草稿已保存' })
    await loadInstanceQuietly()
  } catch (error) {
    applyError(error)
  } finally {
    busy.value = false
  }
}

/**
 * 建草稿实例（`POST /flow-instances`）→ 跳转到实例填单页。
 *
 * 服务端在**落库前**就会跑一次发起前预检（未通过即 `40007`，不落库），
 * 因此这条路径与「提交」共用同一份 blocker 呈现逻辑。
 */
async function createDraft(): Promise<void> {
  const type = routeFormType.value
  if (!type) return
  const fields = collectFields('DRAFT')
  const created = await createFlowInstance({
    formType: type,
    templateVersion: templateInfo.schemaVersion || null,
    category: String(fields.category ?? '') || null,
    involveCost: typeof fields.involve_cost === 'boolean' ? fields.involve_cost : null,
    fields,
  })
  if (!created.id) {
    pageError.value = '建草稿成功但未返回单据 id，无法继续'
    return
  }
  ElMessage({ type: 'success', message: `草稿已建（${created.bizNo || created.id}）` })
  await router.replace({ name: 'form-edit', params: { instanceId: created.id } })
}

/**
 * 发起并直接提交（create 模式的「提交审批」）：
 *   ① `POST /forms/{formType}/validate?mode=SUBMIT` 干跑 → 拿到**逐字段**错误；
 *   ② `POST /flow-instances/precheck` → 未通过则逐条展示拦截项（节点 / 规则 / 缺什么配置）；
 *   ③ `POST /flow-instances` 建草稿（服务端会再预检一次，通过才落库）；
 *   ④ `POST /flow-instances/{id}/submit`（`reason` 必填）。
 *
 * 顺序固定为「先校验 → 再预检 → 才落库 → 最后提交」：任何一步失败都**不产生半成品**，
 * 也不会出现「草稿建了但字段不合法」这种需要人工清理的状态。
 */
async function createAndSubmit(): Promise<void> {
  const type = routeFormType.value
  if (!type) return
  busy.value = true
  resetPage()
  try {
    const fields = collectFields('SUBMIT')
    const validation = await validateFormByType(type, fields, 'SUBMIT')
    report.value = validation.report
    if (!validation.report.passed) {
      ElMessage({ type: 'warning', message: '提交前校验未通过，请按下方逐字段提示修正' })
      return
    }
    const pre = await precheckFlowInstance({
      formType: type,
      templateVersion: templateInfo.schemaVersion || null,
      category: String(fields.category ?? '') || null,
      involveCost: typeof fields.involve_cost === 'boolean' ? fields.involve_cost : null,
      formValues: fields,
    })
    precheck.value = pre
    if (!pre.allowed) {
      ElMessage({ type: 'warning', message: '发起前预检未通过：存在节点解析不出有效审批人（40007）' })
      return
    }
    const created = await createFlowInstance({
      formType: type,
      templateVersion: templateInfo.schemaVersion || null,
      category: String(fields.category ?? '') || null,
      involveCost: typeof fields.involve_cost === 'boolean' ? fields.involve_cost : null,
      fields,
    })
    if (!created.id) {
      pageError.value = '建草稿成功但未返回单据 id，无法提交'
      return
    }
    const submitted = await submitFlowInstance(created.id, '提交审批')
    ElMessage({ type: 'success', message: `已提交（${submitted.bizNo || created.id}）` })
    await router.push({ name: 'task-detail', params: { id: created.id } })
  } catch (error) {
    applyError(error)
  } finally {
    busy.value = false
  }
}

/** 提交审批（实例模式）：干跑 SUBMIT → 预检 → submit */
async function submit(): Promise<void> {
  const id = instanceId.value
  if (!id || !instance.value) return
  busy.value = true
  resetPage()
  try {
    // ① 先按 locked version 做 SUBMIT 档干跑校验，拿到**逐字段**错误
    const validation = await validateFormInstance(id, collectFields('SUBMIT'), 'SUBMIT')
    report.value = validation.report
    if (!validation.report.passed) {
      ElMessage({ type: 'warning', message: '提交前校验未通过，请按下方逐字段提示修正' })
      return
    }
    // ② 预检：逐节点解析候选人（40007 的逐条拦截项在这里提前呈现）
    const precheckInput = precheckPayload()
    const pre = await precheckFlowInstance(precheckInput)
    precheck.value = pre
    if (!pre.allowed) {
      ElMessage({ type: 'warning', message: '发起前预检未通过：存在节点解析不出有效审批人' })
      return
    }
    // ③ 落库（按 SUBMIT 档保存）→ 提交
    await saveFormDraft(id, collectFields('SUBMIT'), 'SUBMIT')
    try {
      await ElMessageBox.confirm(
        `确认提交《${instance.value.bizNo || id}》？提交后主字段全部只读，修改须由审批人驳回后重提。`,
        '确认提交审批',
        { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'info' },
      )
    } catch {
      return
    }
    const submitted = await submitFlowInstance(id, '提交审批')
    ElMessage({ type: 'success', message: `已提交（${submitted.bizNo || id}）` })
    await router.push({ name: 'task-detail', params: { id } })
  } catch (error) {
    applyError(error)
  } finally {
    busy.value = false
  }
}

/** 重提：重新解析审批人快照与版本（REQ-FLOW-017）后再提交 */
async function resubmit(): Promise<void> {
  const id = instanceId.value
  if (!id) return
  try {
    await ElMessageBox.confirm(
      '重提会按**最新已发布模板**重新解析审批人快照与流程版本（旧快照进审计日志），确认继续？',
      '确认重新提交',
      { confirmButtonText: '确认重提', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  busy.value = true
  resetPage()
  try {
    await saveFormDraft(id, collectFields('SUBMIT'), 'SUBMIT')
    const next = await resubmitFlowInstance(id, '驳回后重新提交')
    ElMessage({ type: 'success', message: `已重提（${next.bizNo || id}）` })
    await router.push({ name: 'task-detail', params: { id } })
  } catch (error) {
    applyError(error)
  } finally {
    busy.value = false
  }
}

/**
 * 提交补件：先按 DRAFT 档保存「附件 + 补件说明」（三态白名单只放行这两个），
 * 再调 `POST /flow-instances/{id}/supplement` 回到请求补件的审批人。
 */
async function submitSupplement(): Promise<void> {
  const id = instanceId.value
  if (!id) return
  const note = supplementNote.value.trim()
  if (note.length < 5) {
    ElMessage({ type: 'warning', message: '补件说明至少 5 个字符（doc/forms.md §8）' })
    return
  }
  busy.value = true
  resetPage()
  try {
    const payload: Record<string, FormJsonValue> = { [SUPPLEMENT_NOTE_FIELD]: note }
    const attachmentCode = attachmentFieldCode()
    if (attachmentCode) {
      const current = values.value[attachmentCode]
      if (current !== undefined && current !== null) payload[attachmentCode] = current
    }
    await saveFormDraft(id, payload, 'DRAFT')
    const result = await supplementSubmitFlowInstance(id, note)
    ElMessage({ type: 'success', message: result.message || '补件已提交' })
    await router.push({ name: 'task-detail', params: { id } })
  } catch (error) {
    applyError(error)
  } finally {
    busy.value = false
  }
}

/** 印鉴单归还登记（三态唯一例外通道：只接受 return_status / return_date） */
async function saveSealReturn(): Promise<void> {
  const id = instanceId.value
  if (!id) return
  busy.value = true
  resetPage()
  try {
    // 用**服务端返回的读视图**覆盖本地值：它就是写完之后的真相（含归一化后的日期/状态），
    // 避免界面显示与服务端落库值不一致；同时刷新实例状态与可写字段。
    const updated = await registerSealReturn(id, { returnStatus: sealReturn.status, returnDate: sealReturn.date })
    values.value = { ...updated.snapshot.fields, ...values.value, return_status: sealReturn.status, return_date: sealReturn.date }
    sealReturn.status = String(updated.snapshot.fields.return_status ?? sealReturn.status)
    sealReturn.date = String(updated.snapshot.fields.return_date ?? sealReturn.date)
    writeState.value = updated.state
    instance.value = await fetchFlowInstance(id)
    ElMessage({
      type: 'success',
      message: '归还登记已保存：服务端写一条轨迹动作「归还登记」（return_register），意见里带前后值',
    })
  } catch (error) {
    applyError(error)
  } finally {
    busy.value = false
  }
}

// ---------------------------------------------------------------------------
// 组装与错误呈现
// ---------------------------------------------------------------------------
/** 提交载荷：schema 字段归一（金额补零、空值 → null）+ 补件说明（系统字段） */
function collectFields(targetMode: 'DRAFT' | 'SUBMIT'): Record<string, FormJsonValue> {
  const loaded = schema.value
  if (!loaded) return {}
  const payload = toSubmitFields(loaded.fields, values.value)
  if (inSupplement.value && supplementNote.value.trim() !== '') {
    payload[SUPPLEMENT_NOTE_FIELD] = supplementNote.value.trim()
  }
  if (targetMode === 'SUBMIT' && instance.value) {
    // 类别随单据；服务端会在提交后锁定（40309）
    if (payload.category === null || payload.category === undefined) {
      const category = instance.value.category || String(values.value.category ?? '')
      if (category) payload.category = category
    }
  }
  return payload
}

/** 附件字段码（补件期只补附件与说明） */
function attachmentFieldCode(): string | null {
  const field = schema.value?.fields.find((item) => item.control === 'attachment')
  return field ? field.code : null
}

/** 预检入参（与提交用同一份值，避免「预检过了提交不过」） */
function precheckPayload() {
  const fields = collectFields('SUBMIT')
  const current = instance.value
  return {
    templateId: current?.templateId ?? null,
    templateVersion: current?.templateVersion ?? null,
    formType: formType.value,
    category: String(fields.category ?? '') || null,
    involveCost: typeof fields.involve_cost === 'boolean' ? fields.involve_cost : null,
    formValues: fields,
  }
}

/** 提交后静默刷新（不重置用户已改的值：只刷新三态与实例状态） */
async function loadInstanceQuietly(): Promise<void> {
  const id = instanceId.value
  if (!id) return
  try {
    instance.value = await fetchFlowInstance(id)
    const draft = await fetchFormDraft(id)
    const writable = await fetchWritableFields(id)
    writeState.value = writable.state
    values.value = { ...draft.snapshot.fields, ...values.value }
  } catch {
    // 静默：刷新失败不影响已经成功的写操作
  }
}

/**
 * 错误呈现：**按业务码**给出「服务端原文 + 处置提示」。
 *
 * 重点覆盖任务书点名的错误码：
 *   · `40011` 表单校验 —— message 里带全部逐字段文案，用 `parseValidationMessage` **尽力还原**成
 *     逐字段错误挂到控件上（结构化明细不进 HTTP 响应体，见 `utils/form-rules.ts` 注释）；
 *   · `40304` 三态只读 / `40308` 夹带未登记字段 / `40306` 金额只读 / `40309` 类别改判
 *     —— 服务端 message 已是可定位文案，原样展示并补一句「怎么办」；
 *   · `40007` 预检拦截 —— 逐条拦截项在 `precheck` 面板里展开。
 */
function applyError(error: unknown): void {
  if (!(error instanceof ApiError)) {
    pageError.value = error instanceof Error ? error.message : '请求失败'
    return
  }
  const code = typeof error.code === 'string' ? Number(error.code) : error.code
  pageError.value = `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  pageErrorHint.value = errorHint(code)
  if (code === 40011) {
    const parsed = parseValidationMessage(error.message)
    report.value = parsed
    if (parsed.unrestored) unboundErrors.value = [parsed.unrestored]
  }
  if (code === 40308) {
    // 未登记字段：服务端 message 里带字段名，原样作为整单级错误展示（键不在 schema 里，挂不到控件）
    unboundErrors.value = [error.message]
  }
}

function formatTime(value: string | null | undefined): string {
  return value ? formatDateTime(value) : '—'
}
</script>

<template>
  <div class="oa-form-page">
    <!-- ================= 头部 ================= -->
    <header class="page-head">
      <div class="head-main">
        <h1 class="title">{{ formTypeLabel }}</h1>
        <span class="oa-pill is-processing">{{ mode === 'create' ? '新建草稿' : stateLabel }}</span>
        <span v-if="instance" class="oa-mono biz-no">{{ instance.bizNo || instance.id }}</span>
        <span v-if="instance" class="oa-pill" :class="`is-${instancePillStatus(instance.status)}`">
          {{ instanceStatusLabel(instance.status) }}
          <template v-if="subStatusLabel(instance.subStatus)"> · {{ subStatusLabel(instance.subStatus) }}</template>
        </span>
        <span v-if="schema" class="meta">
          模板 {{ schema.templateCode }} v{{ schema.instanceId ? schema.templateVersion : schema.schemaVersion }}
          <template v-if="schema.instanceId">（**实例锁定版本**，AC-09；不读当前 published）</template>
        </span>
      </div>

      <div class="head-actions">
        <button v-if="instance" class="btn btn-ghost" type="button" @click="router.push({ name: 'task-detail', params: { id: instanceId } })">
          单据详情
        </button>
        <button class="btn btn-ghost" type="button" @click="router.push('/task/initiated')">我发起的</button>
      </div>
    </header>

    <!-- ================= 加载 / 整单错误 ================= -->
    <p v-if="loading" class="loading">正在加载表单模板与字典…</p>

    <section v-if="pageError" class="oa-card error-card">
      <div class="oa-section-band">请求被拒绝</div>
      <p class="error-text">{{ pageError }}</p>
      <p v-if="pageErrorHint" class="error-hint">{{ pageErrorHint }}</p>
      <ul v-if="unboundErrors.length > 0" class="error-list">
        <li v-for="(message, index) in unboundErrors" :key="index">{{ message }}</li>
      </ul>
    </section>

    <!-- ================= 校验报告 ================= -->
    <section v-if="report && !report.passed" class="oa-card report-card">
      <div class="oa-section-band">
        服务端校验未通过（{{ report.issueCount }} 项，逐字段列出）
        <span v-if="report.parsedFromMessage" class="tag-warn">明细由 40011 的 message 文本还原</span>
      </div>
      <ul class="issue-list">
        <li v-for="(issue, index) in report.issues" :key="`${issue.field}-${index}`">
          <b class="oa-mono">{{ issue.field }}</b>
          <span v-if="issue.label && issue.label !== issue.field">（{{ issue.label }}）</span>
          <span v-if="issue.rule" class="rule">{{ issue.rule }}</span>
          ：{{ issue.message }}
        </li>
        <li v-if="report.unrestored" class="unrestored">{{ report.unrestored }}</li>
      </ul>
    </section>

    <!-- ================= 预检拦截项 ================= -->
    <section v-if="precheck" class="oa-card precheck-card" :class="{ 'is-blocked': !precheck.allowed }">
      <div class="oa-section-band">
        发起前预检：{{ precheck.allowed ? '通过' : '存在拦截项' }}
        <span class="meta">
          模板 {{ precheck.templateCode }} v{{ precheck.templateVersion }} · 发起人 {{ precheck.initiatorName }}
        </span>
      </div>

      <template v-if="!precheck.allowed">
        <p class="error-hint">
          服务端会以 40007（APPROVER_RESOLUTION_BLOCKED）拦在提交处，以下为逐条拦截项（节点 / 规则 / 缺什么配置）：
        </p>
        <ul class="issue-list">
          <li v-for="(blocker, index) in precheck.blockers" :key="index">
            节点{{ blocker.nodeSeq }} <b>{{ blocker.nodeName }}</b> · 规则 <b>{{ blocker.ruleLabel || blocker.rule }}</b>：{{ blocker.reason }}
            <ul v-if="blocker.missingConfig.length > 0" class="missing">
              <li v-for="(item, configIndex) in blocker.missingConfig" :key="configIndex">缺少配置：{{ item }}</li>
            </ul>
          </li>
        </ul>
      </template>

      <ul v-else class="node-list">
        <li v-for="node in precheck.nodes" :key="`${node.nodeSeq}`">
          <b>节点{{ node.nodeSeq }} {{ node.nodeName }}</b>
          <span v-if="node.skipped" class="tag-warn">跳过：{{ node.skipReason || '按跳过条件' }}</span>
          <span v-else class="meta">
            候选人 {{ node.candidateCount }} 人（{{ node.candidateNames.join('、') || '—' }}）·
            阈值 {{ node.thresholdBasis }} / 需 {{ node.requiredApprovals }} 人
          </span>
        </li>
      </ul>

      <ul v-if="precheck.warnings.length > 0" class="warn-list">
        <li v-for="(warning, index) in precheck.warnings" :key="index">{{ warning }}</li>
      </ul>
    </section>

    <!-- ================= 三态说明 ================= -->
    <section class="oa-card state-card">
      <div class="oa-section-band">三态读写</div>
      <p class="state-line">
        <b>{{ stateLabel }}</b>
      </p>
      <p v-if="writeState" class="meta">
        可写字段 {{ writeState.writableFields.length }} 个
        <template v-if="writeState.writableFields.length > 0">
          （<span class="oa-mono">{{ writeState.writableFields.join('、') }}</span>）
        </template>
        · 单据状态由服务端判定，前端置灰只是提示，越权写入按 403/40304 拒绝
        <template v-if="writeState.isArchiveNode"> · 当前账号处于节点⑦（归档登记）</template>
        <template v-if="writeState.isInitiator"> · 当前账号是发起人</template>
      </p>
      <p v-else class="meta">新建草稿：全部字段可写（提交后由服务端按状态白名单接管）。</p>
      <p v-if="amountPolicy" class="meta">
        金额字段：{{ amountPolicy.writable ? '当前角色可写' : '当前角色**只读**（PRD §5.3，服务端按 40306 拒绝）' }}
        · 可写角色 {{ amountPolicy.writableRoles.join(' / ') }}
      </p>
    </section>

    <!-- ================= 表单主体（schema 驱动） ================= -->
    <FormRenderer
      v-if="schema"
      v-model="values"
      :schema="schema"
      :state="writeState"
      :errors="errors"
      :amount-policy="amountPolicy"
      :dict-cache="dictCache"
      :dict-loading="dictLoading"
      :user-options="userOptions"
      :org-options="orgOptions"
      :picker-unavailable="pickerUnavailable"
      :unbound-errors="unboundErrors"
    />

    <!-- ================= 补件说明（系统字段，不在 schema 内） ================= -->
    <section v-if="instance && inSupplement" class="oa-card supplement-card">
      <div class="oa-section-band">
        补件说明（系统字段 <span class="oa-mono">supplement_note</span>，≥5 字 / ≤500 字）
      </div>
      <el-input
        v-model="supplementNote"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        :disabled="!supplementNoteWritable"
        placeholder="请说明补充了哪些材料（doc/forms.md §8）"
      />
      <p class="meta">
        待补件期只有「附件」与「补件说明」可写；主字段请走「驳回 → 修改 → 重新提交」。
        {{ supplementNoteWritable ? '' : '（服务端当前未把 supplement_note 列入可写字段，已只读）' }}
      </p>
    </section>

    <!-- ================= 印鉴单归还登记（三态唯一例外） ================= -->
    <section
      v-if="instance && formType === 'seal'"
      class="oa-card seal-card"
      :class="{ 'is-editable': sealReturnWritable }"
    >
      <div class="oa-section-band">
        印鉴单归还登记（三态**唯一例外**）
        <span class="meta">doc/forms.md §5：仅「审批中」且仅发起人 / 节点⑦可改，且只写 return_status / return_date</span>
      </div>
      <div class="seal-body">
        <label class="seal-field">
          <span>归还状态</span>
          <el-select v-model="sealReturn.status" :disabled="!sealReturnWritable" placeholder="请选择">
            <el-option
              v-for="option in dictCache.return_status ?? []"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </label>
        <label class="seal-field">
          <span>归还日期</span>
          <el-date-picker
            v-model="sealReturn.date"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="!sealReturnWritable"
            placeholder="请选择归还日期"
          />
        </label>
        <button class="btn btn-secondary" type="button" :disabled="!sealReturnWritable || busy" @click="saveSealReturn">
          保存归还登记
        </button>
      </div>
      <p v-if="!sealReturnWritable" class="meta">
        当前不可写（只有「审批中」且你本人是发起人或节点⑦归档登记人时开放该例外）。
      </p>
    </section>

    <!-- ================= 底部操作栏 ================= -->
    <footer class="oa-card action-bar">
      <div class="bar-left">
        <span class="meta">
          <template v-if="instance">单据 {{ instance.bizNo || instance.id }} · 提交时间 {{ formatTime(instance.submittedAt) }}</template>
          <template v-else>四类单据共用同一套渲染器；字段集与校验口径全部来自服务端 schema。</template>
        </span>
      </div>

      <div class="bar-actions">
        <!-- 新建：保存草稿 / 直接提交 -->
        <template v-if="mode === 'create'">
          <button class="btn btn-secondary" type="button" :disabled="busy || !schema" @click="save">保存草稿</button>
          <button class="btn btn-primary" type="button" :disabled="busy || !schema" @click="createAndSubmit">
            提交审批
          </button>
        </template>

        <!-- 实例：按三态给按钮 -->
        <template v-else>
          <button
            v-if="hasWritableField && !inSupplement"
            class="btn btn-secondary"
            type="button"
            :disabled="busy"
            @click="save"
          >
            保存
          </button>
          <button
            v-if="instance && isInitiator && (instance.status === 'draft' || instance.status === 'rejected' || instance.status === 'withdrawn')"
            class="btn btn-primary"
            type="button"
            :disabled="busy"
            @click="submit"
          >
            提交审批
          </button>
          <button
            v-if="instance && isInitiator && (instance.status === 'rejected' || instance.status === 'withdrawn')"
            class="btn btn-secondary"
            type="button"
            :disabled="busy"
            @click="resubmit"
          >
            重提（重新解析快照）
          </button>
          <button v-if="inSupplement && isInitiator" class="btn btn-primary" type="button" :disabled="busy" @click="submitSupplement">
            提交补件
          </button>
        </template>
      </div>
    </footer>

    <p class="footnote">
      附件上传属阶段 2b.7（本页只展示已落库的附件元数据，不提供上传入口）；「抄送我的」列表与打印属阶段 3。
    </p>
  </div>
</template>

<style scoped>
.oa-form-page {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
  min-height: 100%;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-sm);
}

.head-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-xs);
  min-width: 0;
}

.title {
  font: var(--oa-font-title-page);
  letter-spacing: var(--oa-letter-spacing-title-page);
  color: var(--oa-color-ink);
}

.head-actions {
  display: flex;
  gap: var(--oa-space-xs);
  margin-left: auto;
}

.biz-no {
  color: var(--oa-color-ink-subtle);
}

.meta {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.loading {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.oa-card {
  background: var(--oa-color-canvas);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-md);
  overflow: hidden;
}

.oa-section-band {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-xs) var(--oa-space-md);
  background: var(--oa-color-canvas-subtle);
  border-bottom: 1px solid var(--oa-color-hairline);
  font: var(--oa-font-label);
  color: var(--oa-color-ink);
}

.error-card,
.report-card,
.precheck-card,
.state-card,
.supplement-card,
.seal-card,
.action-bar {
  padding-bottom: var(--oa-space-sm);
}

.error-text {
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
  color: var(--oa-color-error);
  font: var(--oa-font-body-sm);
}

.error-hint {
  padding: 4px var(--oa-space-md) 0;
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
}

.error-list,
.issue-list,
.node-list,
.warn-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: var(--oa-space-sm) var(--oa-space-md) 0 32px;
  font: var(--oa-font-body-sm);
}

.issue-list .rule {
  margin: 0 2px;
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
}

.issue-list .unrestored {
  color: var(--oa-color-ink-muted);
}

.missing {
  margin: 2px 0 0;
  padding-left: 18px;
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
}

.warn-list {
  color: var(--oa-color-warning);
  font: var(--oa-font-caption);
}

.tag-warn {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-warning);
  font: var(--oa-font-caption);
}

.state-line {
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
  font: var(--oa-font-body-sm);
}

.state-card .meta,
.supplement-card .meta,
.seal-card .meta {
  display: block;
  padding: 4px var(--oa-space-md) 0;
}

.supplement-card :deep(.el-textarea) {
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
}

.seal-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: var(--oa-space-sm);
  align-items: end;
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
}

.seal-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font: var(--oa-font-label);
  color: var(--oa-color-ink-muted);
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-sm);
  padding: var(--oa-space-sm) var(--oa-space-md);
  position: sticky;
  bottom: 0;
}

.bar-left {
  min-width: 0;
}

.bar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
  margin-left: auto;
}

.btn {
  height: var(--oa-space-control);
  padding: 0 var(--oa-space-md);
  border: 1px solid transparent;
  border-radius: var(--oa-radius-sm);
  font: var(--oa-font-button);
  white-space: nowrap;
  cursor: pointer;
}

.btn:disabled {
  cursor: not-allowed;
}

.btn-primary {
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
}

.btn-primary:hover:not(:disabled) {
  background: var(--oa-color-primary-hover);
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
  height: var(--oa-space-control-compact);
  padding: 0 var(--oa-space-xs);
  background: transparent;
  color: var(--oa-color-primary);
}

.footnote {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

@media (max-width: 768px) {
  .seal-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .btn {
    min-height: var(--oa-space-control-h5);
  }
}
</style>
