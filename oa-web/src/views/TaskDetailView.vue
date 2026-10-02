<script setup lang="ts">
/**
 * oa-web · 单据详情（表单分区 + 审批轨迹 + 审批操作区 + 水印 + 三态只读）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `DESIGN.md` › Layout（详情面板头部 56px、底部操作栏 60px、主按钮只出现一次）
 *     › Components › Data Display（workflow-step-* 四态、approval-opinion-block、
 *       workflow-step-parallel-group 并行协同进度）
 *     › Components › Feedback（watermark：姓名 + 工号 5%–8%、-24°、不遮挡按钮与表单值）
 *     › Components › Navigation（h5-bottom-action-bar 60px + 安全区）
 *     › Agent Usage Rules（一屏一主按钮、危险操作二次确认且文案写明动作与对象、
 *       权限不可见优于不可用）
 *   · `doc/tech-design.md` §5.4（三态读写与唯一例外）、§5.5（签名只追加）
 *   · `doc/prd-0.1.md` 6.6（驳回 / 回退 / 补件）、6.8（H5 与水印）
 *   · `normify-oa/modules/oa/portal/detail/**`
 *
 * 本页真实演示三态只读：
 *   ① 草稿：主字段全部可编辑
 *   ② 审批中：主字段全部 disabled
 *   ③ 待补件：主字段 disabled，仅「补件说明 + 附件」可编辑
 *   ④ 已办结：全部只读
 *   ⑤ **唯一例外**：印鉴证照单的 return_status / return_date，发起人与节点⑦可改
 *      —— 页面上通过「当前身份」切换器可复现该例外。
 * 演示开关仅用于阶段 1 骨架（`isDemo`），接真实后端后由 /portal/detail/** 驱动。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAttachments,
  fetchAvailableActions,
  fetchDetailForm,
  fetchDetailHeader,
  fetchPrintDocument,
  fetchSupplementState,
  fetchTrail,
  submitDetailAction,
  updateSealReturnDate,
  updateSealReturnStatus,
} from '@/api/task'
import { ApiError } from '@/api/http'
import { buildDemoForm, demoAttachments, demoDetailHeader, demoTrail } from '@/api/demo'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatDateTime, nodeNoGlyph } from '@/utils/format'
import {
  NODE_ARCHIVE_REGISTER_NO,
  SUPPLEMENT_WRITABLE_FIELD_IDS,
  contextFromHeader,
  describeState,
  isSealReturnEditable,
  resolveFieldEditability,
  type ReadWriteContext,
} from '@/utils/readwrite'
import { FORM_TYPE_GLYPH, statusLabel, statusPillClass, trailStateLabel, trailStepClass } from '@/utils/status'
import type {
  ActionAvailability,
  AttachmentItem,
  DetailAction,
  DetailForm,
  DetailHeader,
  FormField,
  FormType,
  ReadWriteState,
  SupplementState,
  TrailResult,
} from '@/types/api'

const props = defineProps<{ id: string }>()

const router = useRouter()
const userStore = useUserStore()

// ---------------------------------------------------------------------------
// 演示态（阶段 1 骨架）：真实后端就绪后删掉这一段即可
// ---------------------------------------------------------------------------
type DemoIdentity = 'approver' | 'initiator' | 'node7'

const demo = reactive({
  enabled: import.meta.env.VITE_USE_MOCK === 'true',
  formType: 'fund' as FormType,
  state: 'approving' as ReadWriteState,
  identity: 'approver' as DemoIdentity,
})

const formTypeOptions: Array<{ value: FormType; label: string }> = [
  { value: 'matter', label: '事项审批单' },
  { value: 'fund', label: '资金审批单' },
  { value: 'contract', label: '合同审批单' },
  { value: 'seal_cert', label: '印鉴证照审批单' },
]

const stateOptions: Array<{ value: ReadWriteState; label: string }> = [
  { value: 'draft', label: '草稿（可编辑）' },
  { value: 'approving', label: '审批中（只读）' },
  { value: 'supplement', label: '待补件（仅附件与说明）' },
  { value: 'finished', label: '已办结（只读）' },
]

const identityOptions: Array<{ value: DemoIdentity; label: string }> = [
  { value: 'approver', label: '审批人（非发起人）' },
  { value: 'initiator', label: '发起人本人' },
  { value: 'node7', label: '节点⑦ 登记归档' },
]

// ---------------------------------------------------------------------------
// 数据
// ---------------------------------------------------------------------------
const header = ref<DetailHeader>({ ...demoDetailHeader, instanceId: props.id })
const form = ref<DetailForm>(buildDemoForm('fund'))
const trail = ref<TrailResult>(demoTrail)
const attachments = ref<AttachmentItem[]>(demoAttachments)
const supplement = ref<SupplementState>({
  instanceId: props.id,
  pending: false,
  round: 0,
  limit: 3,
  writableFieldIds: [],
})
/** 服务端下发的可用动作（权限不可见优于不可用：不可用/未下发的动作不渲染） */
const availability = ref<ActionAvailability[]>([])
const loading = ref(false)

const opinion = reactive({
  text: '',
  /** 补件说明（待补件态唯一可写的两个字段之一） */
  supplementNote: '',
  signatureId: '',
})

const sealReturn = reactive({
  status: 'borrowed' as string,
  date: '' as string,
})

const OPINION_MAX = 500
const opinionLength = computed(() => opinion.text.length)
const opinionOverLimit = computed(() => opinionLength.value > OPINION_MAX)

/** 三态上下文：状态 + 单据类型 + 是否发起人 + 当前节点 */
const context = computed<ReadWriteContext>(() =>
  contextFromHeader(header.value, writableFieldIds.value),
)

/** 模拟服务端下发的可写字段白名单（tech-design §5.4） */
const writableFieldIds = computed<string[]>(() => {
  switch (header.value.readWriteState) {
    case 'draft':
      return allFieldIds.value
    case 'supplement':
      return [...SUPPLEMENT_WRITABLE_FIELD_IDS]
    case 'approving':
    case 'finished':
    default:
      return []
  }
})

const allFieldIds = computed(() =>
  form.value.sections.flatMap((section) => section.fields.map((field) => field.fieldId)),
)

const bannerModel = computed(() => describeState(context.value))

/** 印鉴单归还例外是否生效 */
const sealEditable = computed(() => isSealReturnEditable(context.value))

/** 一屏一主按钮：主按钮文案随三态变化 */
const primaryAction = computed(() => {
  switch (header.value.readWriteState) {
    case 'draft':
      return { key: 'submit', label: '提交审批' }
    case 'supplement':
      return { key: 'supplement-submit', label: '提交补件' }
    case 'approving':
      return { key: 'approve', label: '同意' }
    case 'finished':
    default:
      return { key: 'print', label: '打印审批单' }
  }
})

/** 除主按钮外的次要操作（审批中才出现；可用性以服务端下发为准） */
const secondaryActions = computed<Array<{ key: string; label: string; danger?: boolean }>>(() => {
  if (header.value.readWriteState !== 'approving') return []

  const presence = new Map(availability.value.map((item) => [item.action, item]))
  const candidates: Array<{ key: DetailAction; label: string; danger?: boolean }> = [
    { key: 'reject', label: '驳回', danger: true },
    { key: 'add-sign', label: '加签' },
    { key: 'route', label: '流转' },
    { key: 'rollback', label: '回退上一节点' },
    { key: 'return-to-department', label: '回到本部门' },
    { key: 'supplement', label: '请求补件' },
    { key: 'terminate', label: '终止' },
    { key: 'transfer', label: '转办' },
  ]

  // 服务端明确说 enabled=false 的不渲染（不可见优于不可用）；未下发时按本地默认渲染
  return candidates
    .filter((item) => {
      const record = presence.get(item.key)
      return record ? record.enabled : true
    })
    .map((item) => {
      const record = presence.get(item.key)
      return { key: item.key, label: record?.label || item.label, danger: item.danger }
    })
})

// ---------------------------------------------------------------------------
// 生命周期
// ---------------------------------------------------------------------------
onMounted(async () => {
  await loadDetail()
})

watch(
  () => props.id,
  async () => {
    await loadDetail()
  },
)

watch(
  () => [demo.formType, demo.state, demo.identity],
  () => {
    if (!demo.enabled) return
    applyDemoState()
  },
)

async function loadDetail(): Promise<void> {
  loading.value = true
  try {
    const [h, f, tr, at, sup, acts] = await Promise.all([
      fetchDetailHeader(props.id),
      fetchDetailForm(props.id),
      fetchTrail(props.id),
      fetchAttachments(props.id),
      fetchSupplementState(props.id),
      fetchAvailableActions(props.id).catch(() => [] as ActionAvailability[]),
    ])
    header.value = { ...h, instanceId: props.id }
    form.value = f
    trail.value = tr
    attachments.value = at
    supplement.value = sup
    availability.value = acts

    // 演示模式下用真实表单结构替换占位数据，保证字段口径与 doc/forms.md 一致
    if (demo.enabled) applyDemoState()
  } catch (error) {
    if (error instanceof ApiError && error.httpStatus !== 401) {
      ElMessage({ type: 'error', message: error.message })
    }
  } finally {
    loading.value = false
  }
}

/** 把三个演示开关作用到 header / form 上 */
function applyDemoState(): void {
  const nextForm = buildDemoForm(demo.formType)
  nextForm.instanceId = props.id
  form.value = nextForm

  header.value = {
    ...header.value,
    instanceId: props.id,
    formType: demo.formType,
    formTypeLabel: formTypeOptions.find((item) => item.value === demo.formType)?.label ?? '',
    readWriteState: demo.state,
    status: demo.state === 'draft' ? 'draft' : demo.state === 'finished' ? 'approved' : 'processing',
    statusLabel:
      demo.state === 'draft' ? '草稿' : demo.state === 'finished' ? '已通过' : demo.state === 'supplement' ? '待补件' : '审批中',
    initiatedByMe: demo.identity === 'initiator',
    currentNodeNo: demo.identity === 'node7' ? NODE_ARCHIVE_REGISTER_NO : demo.identity === 'initiator' ? undefined : 2,
  }

  supplement.value = {
    ...supplement.value,
    pending: demo.state === 'supplement',
    round: demo.state === 'supplement' ? 1 : 0,
    deadline: demo.state === 'supplement' ? '2026-10-07T18:00:00+08:00' : undefined,
    writableFieldIds: [...SUPPLEMENT_WRITABLE_FIELD_IDS],
  }

  // 印鉴单的归还字段值来自表单，同步到本地编辑态
  const returnStatus = nextForm.sections
    .flatMap((section) => section.fields)
    .find((field) => field.fieldId === 'return_status')
  const returnDate = nextForm.sections
    .flatMap((section) => section.fields)
    .find((field) => field.fieldId === 'return_date')
  sealReturn.status = String(returnStatus?.value ?? 'borrowed')
  sealReturn.date = String(returnDate?.value ?? '')
}

// ---------------------------------------------------------------------------
// 字段渲染
// ---------------------------------------------------------------------------
function fieldEditability(field: FormField) {
  return resolveFieldEditability(field, context.value)
}

/** 字段值的窄化读取（表单值是联合类型，模板里需要显式收敛） */
function asString(value: FormField['value']): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

function asStringArray(value: FormField['value']): string[] {
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

/** 统一入口：Element Plus 各控件的 update:model-value 载荷差异在这里收敛 */
function onFieldUpdate(field: FormField, value: unknown): void {
  if (field.control === 'amount') {
    field.value = String(value ?? '').replace(/[^\d.-]/g, '')
    return
  }
  if (field.control === 'checkbox') {
    field.value = Boolean(value)
    return
  }
  if (field.control === 'multiselect' || field.control === 'user') {
    field.value = Array.isArray(value) ? (value as string[]) : []
    return
  }
  if (value === null || value === undefined) {
    field.value = ''
    return
  }
  field.value = String(value)
}

/** 金额失焦规范化：统一两位小数（DESIGN.md amount-input） */
function onAmountBlur(field: FormField): void {
  const value = String(field.value ?? '')
  if (!value) return
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) {
    field.value = ''
    return
  }
  field.value = numeric.toFixed(2)
}

// ---------------------------------------------------------------------------
// 审批动作
// ---------------------------------------------------------------------------
const acting = ref(false)

async function runAction(action: string): Promise<void> {
  if (opinionOverLimit.value) {
    ElMessage({ type: 'warning', message: `审批意见不得超过 ${OPINION_MAX} 字` })
    return
  }

  const title = header.value.title
  const bizNo = header.value.bizNo

  try {
    if (action === 'approve') {
      await ElMessageBox.confirm(
        `确认同意《${title}》（${bizNo}）？同意后将流转至下一审批节点。`,
        '确认同意',
        { confirmButtonText: '确认同意', cancelButtonText: '再想想', type: 'info' },
      )
    } else if (action === 'reject') {
      // 危险操作二次确认，且确认文案包含动作与对象
      if (!opinion.text.trim()) {
        ElMessage({ type: 'warning', message: '驳回必须填写审批意见' })
        return
      }
      await ElMessageBox.confirm(
        `确认驳回《${title}》（${bizNo}）？驳回后流程回到发起人，其余在途任务将自动关闭。`,
        '确认驳回',
        { confirmButtonText: '确认驳回', cancelButtonText: '取消', type: 'warning' },
      )
    } else if (action === 'supplement') {
      const result = await ElMessageBox.prompt(
        `将对《${title}》发起补件请求。补件期间主字段只读，仅发起人可补充说明与附件；同节点最多 1 次、全单最多 3 次。`,
        '要求补充材料',
        {
          confirmButtonText: '发起补件请求',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请写明需要补充的材料（必填）',
          inputValidator: (value: string) => (value ?? '').trim().length > 0 || '请填写补件说明',
        },
      )
      await submitDetailAction(props.id, 'supplement', {
        opinion: opinion.text,
        supplementNote: result.value,
      })
      ElMessage({ type: 'success', message: '已发起补件请求' })
      await loadDetail()
      return
    } else if (action === 'rollback') {
      await ElMessageBox.confirm(
        `确认将《${title}》回退到上一已完成的审批节点？同一节点最多被回退 2 次，必须填写原因。`,
        '确认回退上一节点',
        { confirmButtonText: '确认回退', cancelButtonText: '取消', type: 'warning' },
      )
    } else if (action === 'route') {
      await ElMessageBox.confirm(
        `确认将《${title}》流转到下一承接部门？集团层流转与回退合计不得超过 5 次，禁止回流已处理部门。`,
        '确认流转',
        { confirmButtonText: '确认流转', cancelButtonText: '取消', type: 'info' },
      )
    } else if (action === 'add-sign') {
      await ElMessageBox.confirm(
        `将为《${title}》当前节点增加一名审批人（前加签 / 后加签），加签人需对结果负责并留痕。`,
        '确认加签',
        { confirmButtonText: '确认加签', cancelButtonText: '取消', type: 'info' },
      )
    } else if (action === 'transfer') {
      await ElMessageBox.confirm(
        `将把《${title}》的当前任务转办给他人，转办对象必须是同一数据域内可见该单据的人。`,
        '确认转办',
        { confirmButtonText: '确认转办', cancelButtonText: '取消', type: 'warning' },
      )
    } else if (action === 'return-to-department') {
      await ElMessageBox.confirm(
        `确认将《${title}》回到本部门？同一部门连续回到本部门不得超过 2 次。`,
        '确认回到本部门',
        { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' },
      )
    }

    // 演示模式：不真正调用写接口，只提示结果
    if (demo.enabled) {
      ElMessage({ type: 'success', message: `演示模式：已模拟「${action}」动作` })
      return
    }

    acting.value = true
    await submitDetailAction(props.id, action as DetailAction, {
      opinion: opinion.text,
      signatureId: opinion.signatureId || undefined,
    })
    ElMessage({ type: 'success', message: '操作已提交' })
    opinion.text = ''
    await loadDetail()
  } catch (error) {
    if (typeof error === 'string' && (error === 'cancel' || error === 'close')) return
    if (error instanceof ApiError) {
      ElMessage({ type: 'error', message: error.message })
    }
  } finally {
    acting.value = false
  }
}

function gotoPrint(): void {
  void router.push({ name: 'print-preview', params: { id: props.id }, query: { type: header.value.formType } })
}

async function printCurrent(): Promise<void> {
  try {
    await fetchPrintDocument(props.id, header.value.formType)
  } catch {
    // 打印数据取不到时仍允许打开预览页（预览页会再次尝试）
  }
  gotoPrint()
}

async function saveDraft(): Promise<void> {
  ElMessage({ type: 'success', message: '草稿已保存' })
}

async function submitDraft(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认提交《${header.value.title}》（${header.value.bizNo}）？提交后字段全部只读，修改须由审批人驳回后重提。`,
      '确认提交审批',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  ElMessage({ type: 'success', message: '演示模式：已模拟提交' })
}

async function submitSupplement(): Promise<void> {
  if (!opinion.supplementNote.trim() && attachments.value.length === 0) {
    ElMessage({ type: 'warning', message: '请至少填写补件说明或上传一份附件' })
    return
  }
  ElMessage({ type: 'success', message: '演示模式：补件已提交，回到请求节点' })
}

async function saveSealReturn(): Promise<void> {
  if (demo.enabled) {
    ElMessage({ type: 'success', message: '演示模式：已保存归还状态 / 归还日期' })
    return
  }
  try {
    await updateSealReturnStatus(props.id, sealReturn.status)
    if (sealReturn.date) await updateSealReturnDate(props.id, sealReturn.date)
    ElMessage({ type: 'success', message: '归还信息已保存' })
  } catch (error) {
    if (error instanceof ApiError) ElMessage({ type: 'error', message: error.message })
  }
}

function handlePrimary(): void {
  switch (primaryAction.value.key) {
    case 'submit':
      void submitDraft()
      break
    case 'supplement-submit':
      void submitSupplement()
      break
    case 'approve':
      void runAction('approve')
      break
    case 'print':
    default:
      void printCurrent()
      break
  }
}

function handleSecondary(key: string): void {
  if (key === 'print') {
    void printCurrent()
    return
  }
  void runAction(key)
}
</script>

<template>
  <div class="oa-detail">
    <!-- ================= 详情头部 56px ================= -->
    <header class="detail-head">
      <div class="head-main">
        <span class="type-ico" aria-hidden="true">{{ FORM_TYPE_GLYPH[header.formType] }}</span>
        <h1 class="head-title">{{ header.title }}</h1>
        <span class="oa-pill" :class="statusPillClass(header.status)">
          {{ statusLabel(header.status, header.statusLabel) }}
        </span>
        <span class="oa-mono biz-no">{{ header.bizNo }}</span>
        <span v-if="header.amount" class="oa-amount head-amount">
          ¥ {{ formatAmount(header.amount) }}
        </span>
      </div>

      <div class="head-actions">
        <button class="btn btn-ghost" type="button" @click="printCurrent">打印</button>
        <button class="btn btn-ghost" type="button" @click="runAction('route')">流转</button>
        <button class="btn btn-ghost" type="button" @click="runAction('transfer')">更多</button>
      </div>
    </header>

    <!-- 演示控制条：仅骨架期存在，用来复现三态只读与印鉴单例外 -->
    <section v-if="demo.enabled" class="demo-bar oa-screen-only">
      <span class="demo-label">骨架演示</span>

      <label class="demo-field">
        单据
        <select v-model="demo.formType">
          <option v-for="opt in formTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </label>

      <label class="demo-field">
        三态
        <select v-model="demo.state">
          <option v-for="opt in stateOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </label>

      <label class="demo-field">
        当前身份
        <select v-model="demo.identity">
          <option v-for="opt in identityOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </label>

      <span class="demo-hint">
        印鉴单归还字段：<b>{{ sealEditable ? '可编辑（唯一例外生效）' : '只读' }}</b>
      </span>
    </section>

    <!-- ================= 三态提示条 ================= -->
    <StateBanner
      class="banner"
      :context="context"
      :detail-extra="
        header.readWriteState === 'supplement' && supplement.deadline
          ? `补件截止 ${formatDateTime(supplement.deadline)}，超时仅催办发起人、不自动驳回。`
          : ''
      "
    />

    <!-- ================= 主体：表单（8 列）+ 轨迹（4 列） ================= -->
    <div class="detail-body">
      <div class="form-col">
        <section v-for="section in form.sections" :key="section.sectionId" class="oa-card form-section">
          <div class="oa-section-band">{{ section.title }}</div>

          <div class="fields">
            <div v-for="field in section.fields" :key="field.fieldId" class="oa-field-stack">
              <span class="oa-field-label">
                {{ field.label }}
                <i v-if="!fieldEditability(field).editable" class="ro-flag">只读</i>
              </span>

              <!-- 金额：右对齐 + 等宽数字 + 两位小数；失焦规范化 -->
              <el-input
                v-if="field.control === 'amount'"
                class="oa-amount-input oa-wm-raise"
                :model-value="asString(field.value)"
                :disabled="!fieldEditability(field).editable"
                inputmode="decimal"
                @update:model-value="(v: any) => onFieldUpdate(field, v)"
                @blur="onAmountBlur(field)"
              >
                <template #prefix>¥</template>
              </el-input>

              <!-- 多行文本（事项描述 / 补件说明） -->
              <el-input
                v-else-if="field.control === 'textarea'"
                class="oa-wm-raise"
                type="textarea"
                :rows="field.fieldId === 'supplement_note' ? 3 : 4"
                :maxlength="field.maxLength ?? 500"
                show-word-limit
                :model-value="asString(field.value)"
                :disabled="!fieldEditability(field).editable"
                :placeholder="field.fieldId === 'supplement_note' ? '待补件期间唯一可写的说明字段' : ''"
                @update:model-value="(v: any) => onFieldUpdate(field, v)"
              />

              <!-- 下拉 / 多选 -->
              <el-select
                v-else-if="field.control === 'select' || field.control === 'multiselect'"
                class="oa-wm-raise"
                :multiple="field.control === 'multiselect'"
                :collapse-tags="field.control === 'multiselect'"
                :model-value="field.control === 'multiselect' ? asStringArray(field.value) : asString(field.value)"
                :disabled="!fieldEditability(field).editable"
                @update:model-value="(v: any) => onFieldUpdate(field, v)"
              >
                <el-option
                  v-for="opt in field.options ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>

              <!-- 日期 -->
              <el-date-picker
                v-else-if="field.control === 'date'"
                class="oa-wm-raise"
                type="date"
                value-format="YYYY-MM-DD"
                :model-value="asString(field.value)"
                :disabled="!fieldEditability(field).editable"
                placeholder="请选择日期"
                @update:model-value="(v: any) => onFieldUpdate(field, v)"
              />

              <!-- 布尔 checkbox（计划类别 / 付款归属，只存不用） -->
              <el-checkbox
                v-else-if="field.control === 'checkbox'"
                class="oa-wm-raise"
                :model-value="Boolean(field.value)"
                :disabled="!fieldEditability(field).editable"
                @update:model-value="(v: any) => onFieldUpdate(field, v)"
              >
                {{ field.label }}
              </el-checkbox>

              <!-- 附件上传（待补件期唯一可写的两个字段之一） -->
              <div v-else-if="field.control === 'upload'" class="upload-block oa-wm-raise">
                <el-upload
                  :auto-upload="false"
                  :disabled="!fieldEditability(field).editable"
                  multiple
                  drag
                >
                  <div class="upload-text">
                    点击或拖拽文件到此处上传
                    <i>
                      上限 {{ userStore.clientConfig.upload.maxFilesPerSubmit }} 个文件，单个最大
                      {{ userStore.clientConfig.upload.maxFileSizeMb }} MB
                    </i>
                  </div>
                </el-upload>

                <ul class="attach-list">
                  <li v-for="file in attachments" :key="file.attachmentId">
                    <span class="attach-name">{{ file.fileName }}</span>
                    <span class="attach-meta oa-mono">{{ Math.round(file.fileSize / 1024) }} KB</span>
                    <span class="attach-meta">第 {{ file.round }} 轮</span>
                    <a class="oa-wm-raise" :href="file.downloadUrl">下载</a>
                  </li>
                </ul>
              </div>

              <!-- 人员（抄送人，只读回显） -->
              <el-select
                v-else-if="field.control === 'user'"
                class="oa-wm-raise"
                multiple
                collapse-tags
                disabled
                :model-value="asStringArray(field.value)"
              >
                <el-option
                  v-for="name in asStringArray(field.value)"
                  :key="name"
                  :label="name"
                  :value="name"
                />
              </el-select>

              <!-- 默认单行文本 -->
              <el-input
                v-else
                class="oa-wm-raise"
                :maxlength="field.maxLength"
                :model-value="asString(field.value)"
                :disabled="!fieldEditability(field).editable"
                @update:model-value="(v: any) => onFieldUpdate(field, v)"
              />

              <span class="field-extra">
                <template v-if="fieldEditability(field).editable">
                  <i v-if="field.required" class="req">必填</i>
                  <i v-if="field.unit" class="unit">{{ field.unit }}</i>
                </template>
                <template v-else>{{ fieldEditability(field).reason }}</template>
              </span>
            </div>
          </div>

          <!-- 印鉴单归还信息：三态只读的唯一例外（发起人与节点⑦可改） -->
          <div
            v-if="section.sectionId === 'return'"
            class="seal-return"
            :class="{ 'is-editable': sealEditable }"
          >
            <div class="seal-return-head">
              <b>归还状态 / 归还日期（唯一例外）</b>
              <span>
                科技方案 §5.4：待补件期主字段只读，**唯一例外**是印鉴证照单的
                <code>return_status</code> / <code>return_date</code>，发起人与节点⑦可改。
              </span>
            </div>

            <div class="seal-return-body">
              <label class="oa-field-stack">
                <span class="oa-field-label">归还状态</span>
                <el-select v-model="sealReturn.status" class="oa-wm-raise" :disabled="!sealEditable">
                  <el-option label="借出未还" value="borrowed" />
                  <el-option label="已归还" value="returned" />
                  <el-option label="部分归还" value="partial" />
                </el-select>
              </label>

              <label class="oa-field-stack">
                <span class="oa-field-label">归还日期</span>
                <el-date-picker
                  v-model="sealReturn.date"
                  class="oa-wm-raise"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :disabled="!sealEditable"
                  placeholder="请选择归还日期"
                />
              </label>

              <button
                class="btn btn-secondary seal-save oa-wm-raise"
                type="button"
                :disabled="!sealEditable"
                @click="saveSealReturn"
              >
                保存归还信息
              </button>
            </div>
          </div>
        </section>

        <!-- 审批意见区：审批意见块（意见正文 → 审批人 / 岗位 / 时间戳） -->
        <section
          v-if="header.readWriteState === 'approving' || header.readWriteState === 'supplement'"
          class="oa-card opinion-card"
        >
          <div class="oa-section-band">
            {{ header.readWriteState === 'supplement' ? '补件说明' : '审批意见' }}
          </div>

          <el-input
            v-if="header.readWriteState === 'approving'"
            v-model="opinion.text"
            class="oa-wm-raise"
            type="textarea"
            :rows="4"
            :maxlength="OPINION_MAX"
            show-word-limit
            placeholder="请填写审批意见（驳回 / 终止 / 回退 / 流转必须填写）"
          />
          <el-input
            v-else
            v-model="opinion.supplementNote"
            class="oa-wm-raise"
            type="textarea"
            :rows="3"
            :maxlength="500"
            show-word-limit
            placeholder="请说明补充了哪些材料"
          />

          <p v-if="opinionOverLimit" class="err-msg">审批意见不得超过 {{ OPINION_MAX }} 字，超出后禁止提交。</p>

          <div class="opinion-meta">
            <span>当前审批人：{{ userStore.displayName }} · {{ userStore.user?.positionName }}</span>
            <span v-if="userStore.signaturePresetReady" class="oa-tag is-info">已预存手写签名，可一键使用</span>
          </div>
        </section>
      </div>

      <!-- 审批轨迹 4 列 -->
      <aside class="trail-col">
        <section class="oa-card">
          <div class="oa-section-band">审批轨迹</div>

          <div class="trail-stats">
            <span>流转 + 回退 <b class="oa-tnum">{{ trail.routingCount }}/{{ trail.routingLimit }}</b></span>
            <span>同节点回退 <b class="oa-tnum">{{ trail.returnedCount }}/2</b></span>
            <span>补件轮次 <b class="oa-tnum">{{ trail.supplementRound }}/{{ trail.supplementLimit }}</b></span>
          </div>

          <ol class="trail">
            <li v-for="node in trail.nodes" :key="node.nodeKey" class="trail-item" :class="`is-${node.state}`">
              <span class="oa-step" :class="trailStepClass(node.state)">
                <template v-if="node.state === 'done'">✓</template>
                <template v-else-if="node.nodeNo">{{ nodeNoGlyph(node.nodeNo) }}</template>
                <template v-else>·</template>
              </span>

              <div class="trail-body">
                <div class="trail-title">
                  <b>{{ node.nodeName }}</b>
                  <span class="oa-pill" :class="node.state === 'current' ? 'is-processing' : node.state === 'timeout' ? 'is-pending' : node.state === 'done' ? 'is-approved' : 'is-closed'">
                    {{ trailStateLabel(node.state) }}
                  </span>
                  <span v-if="node.progress" class="oa-tag is-info">{{ node.progress }}</span>
                </div>

                <p v-if="node.parallelGroupTitle" class="trail-parallel">
                  {{ node.parallelGroupTitle }} —— 分组内全部完成才推进
                </p>

                <p v-if="node.approverName" class="trail-meta">
                  {{ node.approverName }}
                  <template v-if="node.approverPosition"> · {{ node.approverPosition }}</template>
                  <template v-if="node.decisionMode"> · {{ node.decisionMode }}</template>
                </p>

                <p v-if="node.action" class="trail-meta">
                  动作：{{ node.action }}
                  <template v-if="node.actedAt"> · <i class="oa-mono">{{ formatDateTime(node.actedAt) }}</i></template>
                </p>

                <div v-if="node.opinion" class="oa-opinion-block">
                  {{ node.opinion }}
                  <p class="meta">签名图与时间戳不可编辑、不可删除（签名记录只追加）</p>
                </div>

                <p v-if="node.timeoutAt" class="trail-timeout">
                  应完成时间 <i class="oa-mono">{{ formatDateTime(node.timeoutAt) }}</i>
                  —— 超时仅催办（站内信 + 邮件），不自动跳过、不自动升级。
                </p>
              </div>
            </li>
          </ol>
        </section>
      </aside>
    </div>

    <!-- ================= 底部操作栏 60px（桌面 sticky / H5 fixed + 安全区） ================= -->
    <footer class="oa-action-bar oa-screen-only" :class="{ 'is-h5-bar': true }">
      <div class="bar-left">
        <span class="bar-state">
          {{ bannerModel.title }}
        </span>
        <span v-if="header.readWriteState === 'supplement' && supplement.deadline" class="bar-deadline">
          补件截止 <i class="oa-mono">{{ formatDateTime(supplement.deadline) }}</i>
        </span>
      </div>

      <div class="bar-actions">
        <button
          v-if="header.readWriteState === 'draft'"
          class="btn btn-secondary oa-h5-secondary"
          type="button"
          @click="saveDraft"
        >
          保存草稿
        </button>

        <template v-for="action in secondaryActions" :key="action.key">
          <button
            v-if="action.key !== 'print'"
            class="btn btn-secondary action-btn"
            :class="{ 'is-danger': action.danger }"
            type="button"
            :disabled="acting"
            @click="handleSecondary(action.key)"
          >
            {{ action.label }}
          </button>
        </template>

        <button class="btn btn-ghost action-more" type="button" @click="printCurrent">打印</button>

        <!-- 一屏一主按钮：任何状态只有一个主按钮 -->
        <button
          class="btn btn-primary oa-h5-primary"
          type="button"
          :disabled="acting || opinionOverLimit"
          @click="handlePrimary"
        >
          {{ primaryAction.label }}
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.oa-detail {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
  min-height: 100%;
  padding-bottom: var(--oa-space-lg);
}

/* ---------------- 头部 ---------------- */
.detail-head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-md);
  height: var(--oa-detail-head-h);
}

.head-main {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  min-width: 0;
}

.type-ico {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
  font: var(--oa-font-caption);
}

.head-title {
  font: var(--oa-font-title-page);
  letter-spacing: var(--oa-letter-spacing-title-page);
  color: var(--oa-color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.biz-no {
  color: var(--oa-color-ink-subtle);
}

.head-amount {
  font: var(--oa-font-amount);
  color: var(--oa-color-ink);
}

.head-actions {
  display: flex;
  gap: var(--oa-space-xs);
  margin-left: auto;
}

/* ---------------- 演示条 ---------------- */
.demo-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-md);
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border: 1px dashed var(--oa-color-hairline-strong);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.demo-label {
  padding: 1px 6px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
  font-weight: 500;
}

.demo-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.demo-field select {
  height: 26px;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  font: var(--oa-font-caption);
}

.demo-hint b {
  color: var(--oa-color-primary);
}

.banner {
  flex: none;
}

/* ---------------- 主体两栏：表单 8 列 + 轨迹 4 列 ---------------- */
.detail-body {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: var(--oa-space-lg);
  align-items: start;
}

.form-col,
.trail-col {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
  min-width: 0;
}

.form-section {
  padding: 20px 24px;
}

.fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--oa-space-md) var(--oa-space-lg);
}

.fields > .oa-field-stack {
  margin-bottom: 0;
}

.oa-field-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--oa-space-xs);
  font: var(--oa-font-label);
  color: var(--oa-color-ink-muted);
}

.ro-flag {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
  font-style: normal;
}

.field-extra {
  display: block;
  margin-top: 4px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.field-extra .req {
  margin-right: var(--oa-space-xs);
  color: var(--oa-color-error);
  font-style: normal;
}

.field-extra .unit {
  font-style: normal;
  color: var(--oa-color-ink-muted);
}

/* 金额输入：右对齐 + 等宽数字 */
:deep(.oa-amount-input .el-input__inner) {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.upload-block {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
}

.upload-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font: var(--oa-font-body-sm);
}

.upload-text i {
  font: var(--oa-font-caption);
  font-style: normal;
  color: var(--oa-color-ink-subtle);
}

.attach-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.attach-list li {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  padding: 4px var(--oa-space-xs);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-xs);
  font: var(--oa-font-body-sm);
}

.attach-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-meta {
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
}

/* ---------------- 印鉴单归还例外 ---------------- */
.seal-return {
  margin-top: var(--oa-space-md);
  padding: var(--oa-space-sm);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
}

.seal-return.is-editable {
  border-color: var(--oa-color-primary-border);
  background: var(--oa-color-primary-subtle);
}

.seal-return-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: var(--oa-space-sm);
  font: var(--oa-font-body-sm);
}

.seal-return-head b {
  font-weight: 500;
  color: var(--oa-color-ink);
}

.seal-return-head span {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.seal-return-head code {
  padding: 0 2px;
  font: var(--oa-font-mono);
  color: var(--oa-color-ink);
}

.seal-return-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto;
  gap: var(--oa-space-sm);
  align-items: end;
}

.seal-save {
  height: var(--oa-space-control);
}

/* ---------------- 审批意见 ---------------- */
.opinion-card {
  padding: 20px 24px;
}

.opinion-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-xs);
  margin-top: var(--oa-space-xs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.err-msg {
  margin-top: 4px;
  font: var(--oa-font-caption);
  color: var(--oa-color-error);
}

/* ---------------- 轨迹 ---------------- */
.trail-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs) var(--oa-space-md);
  margin-bottom: var(--oa-space-md);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.trail-stats b {
  font-weight: 500;
  color: var(--oa-color-ink);
}

.trail {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.trail-item {
  position: relative;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: var(--oa-space-sm);
  padding-bottom: var(--oa-space-md);
}

/* 节点间连接线 2px：已完成段 primary，未完成段 hairline */
.trail-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 26px;
  bottom: 0;
  width: 2px;
  background: var(--oa-color-hairline);
}

.trail-item.is-done:not(:last-child)::before {
  background: var(--oa-color-primary);
}

.trail-item:last-child {
  padding-bottom: 0;
}

.trail-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font: var(--oa-font-body-sm);
}

.trail-parallel {
  margin-top: 2px;
  font: var(--oa-font-caption);
  color: var(--oa-color-info);
}

.trail-meta {
  margin-top: 2px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.trail-timeout {
  margin-top: var(--oa-space-xs);
  font: var(--oa-font-caption);
  color: var(--oa-color-warning);
}

.trail-item .oa-opinion-block {
  margin-top: 6px;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.oa-opinion-block .meta {
  margin: 4px 0 0;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

/* ---------------- 底部操作栏 60px ---------------- */
.oa-action-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--oa-space-sm);
  min-height: var(--oa-action-bar-h);
  padding: 12px 16px;
  background: var(--oa-color-canvas);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
}

.bar-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bar-state {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.bar-deadline {
  font: var(--oa-font-caption);
  color: var(--oa-color-warning);
}

.bar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
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

.btn-secondary:hover:not(:disabled) {
  background: var(--oa-color-canvas-subtle);
}

/* 驳回优先 ghost（白底红字），避免误触红色实心按钮 */
.btn-secondary.is-danger {
  color: var(--oa-color-error);
  border-color: var(--oa-color-error);
}

.btn-ghost {
  height: var(--oa-space-control-compact);
  padding: 0 var(--oa-space-xs);
  background: transparent;
  color: var(--oa-color-primary);
}

.btn-ghost:hover {
  background: var(--oa-color-primary-subtle);
}

.action-more {
  order: 99;
}

/* ---------------- 断点 ---------------- */
@media (max-width: 1440px) {
  .detail-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .seal-return-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-head {
    height: auto;
    flex-wrap: wrap;
  }

  .head-title {
    white-space: normal;
  }

  .head-actions {
    margin-left: 0;
  }

  /* H5 底部操作栏：60px 常驻 + 安全区；按钮 44px 触控目标 */
  .oa-action-bar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    min-height: 0;
    height: calc(var(--oa-action-bar-h) + var(--oa-safe-bottom));
    padding: 0 var(--oa-space-sm) var(--oa-safe-bottom);
    border: 0;
    border-top: 1px solid var(--oa-color-hairline);
    border-radius: 0;
  }

  .bar-left,
  .action-btn,
  .action-more {
    display: none;
  }

  .bar-actions {
    flex-wrap: nowrap;
  }

  .btn {
    height: var(--oa-space-control-h5);
  }

  .oa-h5-primary {
    flex: 1 1 auto;
    min-width: 0;
  }

  .oa-h5-secondary {
    flex: 0 0 96px;
  }

  .oa-detail {
    padding-bottom: calc(var(--oa-action-bar-h) + var(--oa-safe-bottom) + var(--oa-space-md));
  }
}
</style>
