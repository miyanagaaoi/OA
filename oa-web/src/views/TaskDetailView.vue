<script setup lang="ts">
/**
 * oa-web · 单据详情（阶段 2b：接**真实引擎**）
 * ----------------------------------------------------------------------------
 * 取数（全部是 2a/2b 已交付接口）：
 *   · `GET /flow-instances/{id}`                实例（三层状态 + **审批人快照**）
 *   · `GET /flow-instances/{id}/runtime`        运行态（节点实例 / 任务 / 轨迹 / 流转 / 补件 / 抄送 / Q6 剩余）
 *   · `GET /forms/instances/{id}/schema`        **实例锁定版本**的 schema（AC-09）
 *   · `GET /forms/instances/{id}/draft`         字段值 + 三态可写字段
 *   · `GET /flow-actions`                       动作面清单（权限 / 必填原因 / 意见下限 / 状态迁移）
 *   · 任务级动作 `POST /flow-tasks/{taskId}/...`；实例级动作 `POST /flow-instances/{id}/...`
 *
 * 来源：
 *   · `doc/prd-0.1.md` §6.3–§6.7（主干链 / 流转回退加签跳转 / 驳回补件 / 抄送）、AC-49（终止）
 *   · `doc/enums.md` §8（三层状态）、§9（**17 值**轨迹动作，含 2026-10-04 新增的
 *     `return_register` 归还登记）
 *   · `doc/forms.md` §1.2（三态读写）、§5（印鉴单归还唯一例外）、§7（补件）
 *   · `doc/templates.md` §1.7（Q6/Q7 闸门）、§1.8（撤回窗口）
 *   · `DESIGN.md` › Layout（详情头 56px、底部操作栏、一屏一主按钮）、
 *     Agent Usage Rules（危险操作二次确认且文案写明动作与对象；**不可用时说明原因**）
 *
 * 三条硬要求（本页是它们的落点）：
 *   1. **动作可用性由服务端数据推导**并用显式原因呈现：权限码取自 `GET /flow-actions`，
 *      实例状态取自 `/flow-instances/{id}`，节点开关取自**快照**（`allowAddSign/allowRoute/allowJump`），
 *      「我的待处理任务」取自运行态任务清单 —— 判定在 `utils/flow-task.ts`（可自测的纯函数）。
 *   2. **表单在详情页只读**（编辑在「填单」页），只读原因显式给出。
 *   3. **错误码如实展示**：40009 / 40007 / 40304 / 40908–40915 等均带业务码与处置提示。
 */
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FormRenderer from '@/components/FormRenderer.vue'
import { ApiError } from '@/api/http'
import { fetchDictItems, fetchFieldGroups, fetchFormDraft, fetchInstanceSchema, fetchWritableFields } from '@/api/form'
import {
  compileActionRequest,
  fetchActionCatalog,
  fetchFlowInstance,
  fetchInstanceRuntime,
  type CompiledActionRequest,
} from '@/api/flow-task'
import { fetchOrgSelector } from '@/api/org'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import {
  FORM_DOC_TYPE_GLYPH,
  FORM_DOC_TYPE_LABEL,
  errorHint,
  toFormDocType,
} from '@/utils/form-rules'
import {
  ACTION_CATALOG_FALLBACK_HINT,
  checkActionInputs,
  actionInputSpec,
  instancePillStatus,
  instanceStatusLabel,
  resolveActionAvailability,
  subStatusLabel,
  taskStatusLabel,
  threadActionLabel,
  THREAD_ACTION_LABEL,
  THREAD_ACTION_ORDER,
} from '@/utils/flow-task'
import { statusPillClass } from '@/utils/status'
import type { ActionAvailability, ActionCatalogItem, FlowInstance, FlowRuntime } from '@/types/flow-task'
import type { FormAmountPolicy, FormDictCache, FormJsonValue, FormOption, FormSchema, FormWriteState } from '@/types/form'

const props = defineProps<{ id: string }>()

const router = useRouter()
const userStore = useUserStore()

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------
const instance = ref<FlowInstance | null>(null)
const runtime = ref<FlowRuntime | null>(null)
/**
 * schema 与字段值用 `shallowRef`：类型里含**递归自由 JSON**（`FormJsonValue`），
 * `ref` 的深度 `UnwrapRef` 会让 `vue-tsc` 报 TS2589。两处都是整体替换，无需深层响应式。
 */
const schema = shallowRef<FormSchema | null>(null)
const values = shallowRef<Record<string, FormJsonValue>>({})
const writeState = ref<FormWriteState | null>(null)
const catalog = ref<ActionCatalogItem[]>([])
const amountPolicy = ref<FormAmountPolicy | null>(null)
const dictCache = ref<FormDictCache>({})
const loading = ref(false)
const acting = ref(false)
const loadErrors = ref<string[]>([])
const runtimeNotice = ref('')

/** 动作弹窗 */
const dialog = reactive({
  open: false,
  action: '',
  opinion: '',
  toDeptId: '',
  targetSeq: '',
  addSignType: 'post' as 'pre' | 'post',
  delegateUserId: '',
  toUserId: '',
  ccUserIds: '',
})
const dialogError = ref('')
const deptOptions = ref<FormOption[]>([])

/** 详情页不展示字段级校验错误（校验发生在填单/提交路径；这里表单只读） */
const NO_ISSUES: Record<string, never[]> = {}

// ---------------------------------------------------------------------------
// 派生
// ---------------------------------------------------------------------------
const formType = computed(() => toFormDocType(instance.value?.formType) ?? toFormDocType(schema.value?.formType))
const formTypeLabel = computed(() => (formType.value ? FORM_DOC_TYPE_LABEL[formType.value] : '未知单据类型'))
const formTypeGlyph = computed(() => (formType.value ? FORM_DOC_TYPE_GLYPH[formType.value] : '单'))

const subject = computed(() => ({
  isSuperAdmin: userStore.isSuperAdmin,
  permissions: userStore.permissions,
  roleCodes: userStore.roles.map((role) => role.roleCode),
}))

/** 动作可用性（**纯函数**判定，见 `utils/flow-task.ts#resolveActionAvailability`） */
const availability = computed<ActionAvailability[]>(() => {
  const current = instance.value
  if (!current) return []
  return resolveActionAvailability({
    catalog: catalog.value,
    subject: subject.value,
    currentUserId: userStore.user?.userId ?? '',
    instance: current,
    runtime: runtime.value,
    snapshotNodes: current.snapshot?.nodes ?? [],
  })
})

const availableActions = computed(() => availability.value.filter((item) => item.enabled))
const blockedActions = computed(() => availability.value.filter((item) => !item.enabled))

/** 主按钮：优先「通过」（或⑦节点的归档登记），否则第一个可用动作 */
const primaryAction = computed<ActionAvailability | null>(() => {
  const list = availableActions.value
  return list.find((item) => item.action === 'approve')
    ?? list.find((item) => item.action === 'archive_register')
    ?? list.find((item) => item.action === 'submit')
    ?? list.find((item) => item.action === 'supplement_submit')
    ?? list.find((item) => item.action === 'reopen')
    ?? list[0]
    ?? null
})

/** 次按钮：除主按钮外的其余可用动作（危险动作用 ghost 红字） */
const secondaryActions = computed(() => {
  const primary = primaryAction.value
  return availableActions.value.filter((item) => item.action !== primary?.action)
})

const canEditForm = computed(() => (writeState.value?.writableFields ?? []).length > 0)
const currentSpec = computed(() => actionInputSpec(dialog.action, catalog.value.find((item) => item.action === dialog.action)))

const opinionLength = computed(() => Array.from(dialog.opinion.trim()).length)
const opinionMax = computed(() => currentSpec.value.maxOpinionChars)
const opinionOver = computed(() => opinionLength.value > opinionMax.value)
/** 驳回时的实时字数提示（前端先校验，服务端仍是裁决方） */
const opinionHint = computed(() => {
  const spec = currentSpec.value
  if (!spec.opinion) return ''
  if (spec.minOpinionChars > 0) {
    const left = spec.minOpinionChars - opinionLength.value
    if (left > 0) return `还差 ${left} 个字（下限 ${spec.minOpinionChars} 字；服务端 40009）`
    return `已满足下限 ${spec.minOpinionChars} 字`
  }
  return `${opinionLength.value} / ${spec.maxOpinionChars} 字`
})

const dialogTitle = computed(() => {
  const item = catalog.value.find((entry) => entry.action === dialog.action)
  return item ? `${item.label} · ${instance.value?.bizNo || props.id}` : '审批动作'
})
const dialogTransition = computed(() => catalog.value.find((entry) => entry.action === dialog.action)?.transition ?? '')

const runtimeCounts = computed(() => ({
  routingUsed: runtime.value?.returnGate?.used ?? null,
  routingMax: runtime.value?.returnGate?.max ?? null,
  supplementUsed: runtime.value?.supplementGate?.used ?? null,
  supplementMax: runtime.value?.supplementGate?.max ?? null,
}))

// ---------------------------------------------------------------------------
// 加载
// ---------------------------------------------------------------------------
onMounted(async () => {
  await loadAll()
})

watch(
  () => props.id,
  async () => {
    await loadAll()
  },
)

async function loadAll(): Promise<void> {
  loading.value = true
  loadErrors.value = []
  runtimeNotice.value = ''
  try {
    const [detail, actions] = await Promise.all([fetchFlowInstance(props.id), fetchActionCatalog()])
    instance.value = detail
    catalog.value = actions
    // 运行态：数据域外/无节点时后端返回空数组，**不视为失败**
    try {
      runtime.value = await fetchInstanceRuntime(props.id)
      if (runtime.value.tasks.length === 0 && runtime.value.nodes.length === 0) {
        runtimeNotice.value =
          '运行态未返回节点实例与任务（该单可能尚未提交，或当前账号的数据域看不到明细）；动作可用性据此判定，必要时请联系管理员核对。'
      }
    } catch (error) {
      runtime.value = null
      runtimeNotice.value = describeError(error)
    }
    try {
      schema.value = await fetchInstanceSchema(props.id)
    } catch (error) {
      schema.value = null
      loadErrors.value.push(`表单模板（实例锁定版本）取数失败：${describeError(error)}`)
    }
    try {
      const draft = await fetchFormDraft(props.id)
      values.value = { ...draft.snapshot.fields }
      const writable = await fetchWritableFields(props.id)
      writeState.value = writable.state
      await loadDicts(schema.value)
      // 金额角色策略：详情页只读，但金额字段的只读原因要写准（40306 与状态正交）
      const type = toFormDocType(writable.formType) ?? toFormDocType(schema.value?.formType)
      if (type) {
        try {
          amountPolicy.value = (await fetchFieldGroups(type)).amountPolicy
        } catch {
          amountPolicy.value = null
        }
      }
    } catch (error) {
      loadErrors.value.push(`单据表单取数失败：${describeError(error)}`)
    }
  } catch (error) {
    loadErrors.value.push(describeError(error))
  } finally {
    loading.value = false
  }
}

/** 字典：详情页只读展示下拉的中文名（值本身来自服务端快照） */
async function loadDicts(loaded: FormSchema | null): Promise<void> {
  if (!loaded) return
  const types = Array.from(
    new Set(loaded.fields.map((field) => field.dictType).filter((item): item is string => Boolean(item))),
  )
  await Promise.all(
    types.map(async (dictType) => {
      try {
        dictCache.value = { ...dictCache.value, [dictType]: await fetchDictItems(dictType) }
      } catch {
        // 字典取不到不影响只读展示：el-select 会显示原始 code
      }
    }),
  )
}

async function loadDeptOptions(): Promise<void> {
  if (deptOptions.value.length > 0) return
  try {
    const orgs = await fetchOrgSelector({})
    deptOptions.value = orgs
      .filter((org) => org.depth <= 3)
      .map((org) => ({ value: org.id, label: `${org.name}（${org.path}）`, enabled: true, sortNo: org.depth }))
  } catch {
    deptOptions.value = []
  }
}

// ---------------------------------------------------------------------------
// 动作
// ---------------------------------------------------------------------------
function openAction(item: ActionAvailability): void {
  dialog.action = item.action
  dialog.opinion = ''
  dialog.toDeptId = ''
  dialog.targetSeq = ''
  dialog.addSignType = 'post'
  dialog.delegateUserId = ''
  dialog.toUserId = ''
  dialog.ccUserIds = ''
  dialogError.value = ''
  dialog.open = true
  if (actionInputSpec(item.action, item).toDept) void loadDeptOptions()
}

async function confirmAction(): Promise<void> {
  const spec = currentSpec.value
  const check = checkActionInputs(spec, {
    opinion: dialog.opinion,
    toDeptId: dialog.toDeptId,
    targetSeq: dialog.targetSeq,
    delegateUserId: dialog.delegateUserId,
    toUserId: dialog.toUserId,
    ccUserIds: dialog.ccUserIds,
    addSignType: dialog.addSignType,
  })
  if (!check.ok) {
    dialogError.value = check.message
    return
  }

  const item = catalog.value.find((entry) => entry.action === dialog.action)
  const label = item?.label ?? dialog.action
  // 危险操作二次确认，确认文案写明**动作与对象**（DESIGN.md Agent Usage Rules）
  if (item && item.requiresReason && ['reject', 'terminate'].includes(dialog.action)) {
    try {
      await ElMessageBox.confirm(
        `确认对《${instance.value?.bizNo || props.id}》执行「${label}」？` +
          (dialog.action === 'reject'
            ? '驳回后流程回到发起人，其余在途任务将自动关闭。'
            : '终止是终态，之后不可再提交（REQ-FLOW-010 / AC-49）。'),
        `确认${label}`,
        { confirmButtonText: `确认${label}`, cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }

  acting.value = true
  dialogError.value = ''
  try {
    const compiled: CompiledActionRequest = compileActionRequest({
      action: dialog.action,
      instanceId: props.id,
      taskId: findTaskIdForAction(dialog.action),
      opinion: dialog.opinion.trim(),
      toDeptId: dialog.toDeptId.trim(),
      targetSeq: dialog.targetSeq.trim(),
      addSignType: dialog.addSignType,
      delegateUserId: dialog.delegateUserId.trim(),
      toUserId: dialog.toUserId.trim(),
      ccUserIds: dialog.ccUserIds,
    })
    await compiled.run()
    ElMessage({ type: 'success', message: compiled.successMessage })
    dialog.open = false
    await loadAll()
  } catch (error) {
    dialogError.value = describeError(error)
  } finally {
    acting.value = false
  }
}

/**
 * 任务级动作取「我的待处理任务」id；实例级动作为空串。
 *
 * 与 `resolveActionAvailability` 同源（`findMyPendingTask`）：这里再取一次是为了
 * 弹窗确认时任务可能已被他人处理 —— 服务端会按 40910 拒绝，错误如实展示。
 */
function findTaskIdForAction(action: string): string {
  const current = instance.value
  const run = runtime.value
  const me = userStore.user?.userId ?? ''
  if (!current || !run) return ''
  const taskScoped = ['approve', 'reject', 'rollback', 'route', 'back_home', 'jump', 'add_sign', 'transfer', 'reassign', 'supplement_request', 'archive_register']
  if (!taskScoped.includes(action)) return ''
  const task = run.tasks.find((item) => item.status === 'pending' && item.assigneeId === me)
  return task ? task.id : ''
}

function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return error instanceof Error ? error.message : '请求失败'
  }
  const code = typeof error.code === 'string' ? Number(error.code) : error.code
  const hint = errorHint(code)
  return `${error.message}${hint ? ` —— ${hint}` : ''}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
}

function gotoFill(): void {
  void router.push({ name: 'form-edit', params: { instanceId: props.id } })
}

function formatTime(value: string | null | undefined): string {
  return value ? formatDateTime(value) : '—'
}
</script>

<template>
  <div class="oa-detail">
    <!-- ================= 头部 ================= -->
    <header class="detail-head">
      <div class="head-main">
        <span class="type-ico" aria-hidden="true">{{ formTypeGlyph }}</span>
        <h1 class="head-title">{{ formTypeLabel }}</h1>
        <span class="oa-pill" :class="statusPillClass(instancePillStatus(instance?.status) as never)">
          {{ instanceStatusLabel(instance?.status) }}
          <template v-if="subStatusLabel(instance?.subStatus)"> · {{ subStatusLabel(instance?.subStatus) }}</template>
        </span>
        <span class="oa-mono biz-no">{{ instance?.bizNo || props.id }}</span>
        <span v-if="instance?.currentNodeSeq" class="meta">当前节点 {{ instance.currentNodeSeq }}</span>
      </div>

      <div class="head-actions">
        <button v-if="canEditForm" class="btn btn-ghost" type="button" @click="gotoFill">编辑（填单页）</button>
        <button class="btn btn-ghost" type="button" @click="router.push('/task/pending')">返回列表</button>
      </div>
    </header>

    <p v-if="loading" class="loading">正在加载单据、运行态与动作面清单…</p>

    <section v-for="(message, index) in loadErrors" :key="index" class="oa-card error-card">
      <div class="oa-section-band">取数失败</div>
      <p class="error-text">{{ message }}</p>
    </section>

    <p v-if="runtimeNotice" class="runtime-notice">{{ runtimeNotice }}</p>

    <!-- ================= 三态 ================= -->
    <section class="oa-card state-card">
      <div class="oa-section-band">
        三态读写与流转闸门
        <span class="meta">
          标签页锁定版本 v{{ schema?.templateVersion ?? instance?.templateVersion ?? '—' }}（AC-09：不读当前 published）
        </span>
      </div>
      <p class="state-line"><b>{{ writeState?.stateLabel ?? '尚未取得可写字段白名单（按只读渲染）' }}</b></p>
      <p class="meta">
        可写字段 {{ writeState?.writableFields.length ?? 0 }} 个
        <template v-if="(writeState?.writableFields.length ?? 0) > 0">
          （<span class="oa-mono">{{ writeState?.writableFields.join('、') }}</span>）
        </template>
        · 服务端 `FormStateWriteGuard` 是边界，前端置灰只是提示（越权写入按 403/40304 拒绝）
      </p>
      <p v-if="runtime" class="meta">
        Q6 流转+回退：
        <b class="oa-tnum">{{ runtimeCounts.routingUsed ?? 0 }}</b> / {{ runtimeCounts.routingMax ?? '不限' }}
        · Q7 补件：<b class="oa-tnum">{{ runtimeCounts.supplementUsed ?? 0 }}</b> / {{ runtimeCounts.supplementMax ?? '不限' }}
        · <span class="oa-mono">{{ runtime.returnGate?.note || '—' }}</span>
      </p>
    </section>

    <!-- ================= 动作面板 ================= -->
    <section class="oa-card action-card">
      <div class="oa-section-band">
        审批动作
        <span class="meta">
          可用性由 `GET /flow-actions`（权限 / 必填原因 / 意见下限）+ 实例状态 + 节点开关（快照）共同判定
        </span>
      </div>

      <p v-if="catalog.length === 0 && !loading" class="error-text">
        动作面清单（`GET /flow-actions`）取不到，无法判定动作可用性。{{ ACTION_CATALOG_FALLBACK_HINT }}
      </p>

      <div v-else class="action-bar">
        <!-- 一屏一主按钮：主按钮按「通过 → 归档登记 → 提交 → 提交补件 → 回到草稿」优先取第一个可用项 -->
        <button
          v-if="primaryAction"
          class="btn btn-primary"
          type="button"
          :title="primaryAction.note || primaryAction.label"
          :disabled="acting"
          @click="openAction(primaryAction)"
        >
          {{ primaryAction.label }}
        </button>
        <!-- 次按钮：其余可用动作；危险动作（驳回 / 终止）用 ghost 红字，避免误触实心红按钮 -->
        <button
          v-for="item in secondaryActions"
          :key="item.action"
          class="btn"
          :class="item.danger ? 'btn-secondary is-danger' : 'btn-secondary'"
          type="button"
          :title="item.note || item.label"
          :disabled="acting"
          @click="openAction(item)"
        >
          {{ item.label }}
        </button>
        <span v-if="availableActions.length === 0" class="meta">
          当前没有任何可用动作（下面逐条列出原因）。
        </span>
      </div>

      <ul v-if="availableActions.some((item) => item.note)" class="notes">
        <li v-for="item in availableActions.filter((entry) => entry.note)" :key="item.action">
          <b>{{ item.label }}</b>：{{ item.note }}
        </li>
      </ul>

      <!-- 不可用动作**逐条给出原因**（而不是点了报错） -->
      <details v-if="blockedActions.length > 0" class="blocked">
        <summary>不可用的动作及原因（{{ blockedActions.length }} 项）</summary>
        <ul>
          <li v-for="item in blockedActions" :key="item.action">
            <b>{{ item.label }}</b>
            <span class="perm oa-mono">{{ item.permission }}</span>
            ：{{ item.reason }}
          </li>
        </ul>
      </details>
    </section>

    <!-- ================= 表单（只读） ================= -->
    <FormRenderer
      v-if="schema"
      v-model="values"
      :schema="schema"
      :state="writeState"
      :errors="NO_ISSUES"
      :amount-policy="amountPolicy"
      :dict-cache="dictCache"
      :dict-loading="{}"
      :user-options="[]"
      :org-options="[]"
      :picker-unavailable="true"
      :unbound-errors="[]"
      force-readonly-reason="单据详情页为只读视图：如需修改请在「填单页」操作（那里按服务端可写字段白名单开放）"
    />
    <p v-else-if="!loading" class="meta">表单模板未取到，无法渲染字段（见上方取数失败信息）。</p>

    <!-- ================= 轨迹 / 流转 / 补件 / 抄送 ================= -->
    <div class="detail-body">
      <section class="oa-card">
        <div class="oa-section-band">审批轨迹（{{ runtime?.thread.length ?? 0 }} 条）</div>
        <ol v-if="runtime && runtime.thread.length > 0" class="trail">
          <li v-for="item in runtime.thread" :key="item.seq" class="trail-item">
            <span class="oa-step">{{ item.seq }}</span>
            <div class="trail-body">
              <p class="trail-title">
                <b>{{ threadActionLabel(item.action, item.actionLabel) }}</b>
                <span class="meta">{{ item.actorName || '—' }}<template v-if="item.actorPosition"> · {{ item.actorPosition }}</template></span>
                <span class="oa-mono meta">{{ formatTime(item.createdAt) }}</span>
              </p>
              <p v-if="item.opinion" class="opinion">{{ item.opinion }}</p>
            </div>
          </li>
        </ol>
        <p v-else class="meta">暂无轨迹（草稿尚未提交时为空）。</p>

        <!-- 轨迹动作值域图例（17 值；`return_register` 归还登记排在归档登记之前） -->
        <details class="legend">
          <summary>轨迹动作值域（17 值，2026-10-04 由 16 值加入「归还登记」）</summary>
          <ul>
            <li v-for="action in THREAD_ACTION_ORDER" :key="action">
              <span class="oa-mono">{{ action }}</span> · {{ THREAD_ACTION_LABEL[action] }}
              <i v-if="action === 'return_register'" class="legend-note">印鉴单归还登记专用；不再复用 archive_register</i>
            </li>
          </ul>
        </details>
      </section>

      <section class="oa-card">
        <div class="oa-section-band">节点实例与任务（{{ runtime?.nodes.length ?? 0 }} 节点 / {{ runtime?.tasks.length ?? 0 }} 任务）</div>
        <ul v-if="runtime && runtime.nodes.length > 0" class="node-list">
          <li v-for="node in runtime.nodes" :key="node.id">
            <b>节点{{ node.nodeSeq }} {{ node.nodeName }}</b>
            <span class="oa-pill" :class="node.status === 'active' ? 'is-processing' : node.status === 'approved' ? 'is-approved' : 'is-closed'">
              {{ node.statusLabel || node.status }}
            </span>
            <span class="meta">
              决议 {{ node.decisionMode || '—' }}
              <template v-if="node.passThreshold"> / 阈值 {{ node.passThreshold }}</template>
              · 已回退 {{ node.returnedCount }} 次
              <template v-if="node.supplementRequested"> · 已请求补件</template>
              <template v-if="node.approverIds.length > 0"> · 候选人 {{ node.approverIds.length }} 人</template>
            </span>
          </li>
        </ul>
        <ul v-if="runtime && runtime.tasks.length > 0" class="task-list">
          <li v-for="task in runtime.tasks" :key="task.id">
            <span class="oa-mono">#{{ task.id }}</span>
            节点{{ task.nodeSeq ?? '—' }} · {{ task.assigneeName || task.assigneeId || '—' }}
            <span class="oa-pill" :class="task.status === 'pending' ? 'is-pending' : 'is-closed'">
              {{ taskStatusLabel(task.status, task.statusLabel) }}
            </span>
            <template v-if="task.addSignType"> · {{ task.addSignType === 'pre' ? '前加签' : '后加签' }}</template>
            <template v-if="task.opinion"> · 意见：{{ task.opinion }}</template>
          </li>
        </ul>
        <p v-if="!runtime || (runtime.nodes.length === 0 && runtime.tasks.length === 0)" class="meta">暂无节点实例与任务。</p>
      </section>

      <section class="oa-card">
        <div class="oa-section-band">流转链 / 补件 / 抄送</div>
        <ul v-if="runtime && runtime.routing.length > 0" class="node-list">
          <li v-for="item in runtime.routing" :key="item.seq">
            第{{ item.seq }}次 {{ item.actionLabel || item.actionType }}：{{ item.fromDeptId || '—' }} → {{ item.toDeptId || '—' }}
            <span class="meta">{{ item.reason }}</span>
          </li>
        </ul>
        <ul v-if="runtime && runtime.supplements.length > 0" class="node-list">
          <li v-for="item in runtime.supplements" :key="item.id">
            补件第{{ item.round }}轮 · {{ item.status }}
            <template v-if="item.deadline"> · 应完成 {{ formatTime(item.deadline) }}</template>
            <template v-if="item.overdue"> · <span class="is-error">已逾期</span></template>
            <template v-if="item.submittedNote"> · 说明：{{ item.submittedNote }}</template>
          </li>
        </ul>
        <ul v-if="runtime && runtime.cc.length > 0" class="node-list">
          <li v-for="item in runtime.cc" :key="item.userId">
            抄送 {{ item.userName || item.userId }}（{{ item.source || '—' }}）
            <template v-if="item.readAt"> · 已读 {{ formatTime(item.readAt) }}</template>
          </li>
        </ul>
        <p v-if="!runtime || (runtime.routing.length === 0 && runtime.supplements.length === 0 && runtime.cc.length === 0)" class="meta">
          暂无流转 / 补件 / 抄送记录。
        </p>
      </section>

      <section class="oa-card">
        <div class="oa-section-band">审批人快照（运行时权威数据）</div>
        <p class="meta">
          建实例时固化：之后改组织负责人或模板，**不会**改变已有快照（只有显式 `reparse` 才会重算，且旧快照进审计）。
          节点开关（加签 / 流转 / 跳转）取自本快照。
        </p>
        <ul v-if="instance?.snapshot" class="node-list">
          <li v-for="node in instance.snapshot.nodes" :key="`${node.nodeSeq}`">
            <b>节点{{ node.nodeSeq }} {{ node.nodeName }}</b>
            <span class="meta">
              规则 {{ node.rule }} · 决议 {{ node.decisionMode || '—' }} · 阈值 {{ node.passThreshold || '过半' }}
              · 加签 {{ node.allowAddSign ? '开' : '关' }} · 流转 {{ node.allowRoute ? '开' : '关' }} · 跳转 {{ node.allowJump ? '开' : '关' }}
              <template v-if="node.skipped"> · <b>已跳过</b>（{{ node.skipReason || '按跳过条件' }}）</template>
            </span>
            <span class="meta">候选人：{{ node.approvers.map((approver) => approver.name).join('、') || '（空）' }}</span>
          </li>
        </ul>
        <p v-else class="meta">
          实例详情未返回快照（可能尚无快照或数据域受限）；节点开关类动作会因此判为不可用并说明原因。
        </p>
      </section>
    </div>

    <!-- ================= 动作弹窗 ================= -->
    <el-dialog v-model="dialog.open" :title="dialogTitle" width="560px" append-to-body>
      <p class="dialog-transition">{{ dialogTransition }}</p>

      <div class="dialog-body">
        <label v-if="currentSpec.opinion" class="dialog-field">
          <span>{{ currentSpec.opinionLabel }}</span>
          <el-input
            v-model="dialog.opinion"
            type="textarea"
            :rows="4"
            :maxlength="opinionMax"
            :placeholder="currentSpec.opinionKind === 'reason' ? '请写明原因（≤255 字）' : '审批意见'"
          />
          <i class="hint" :class="{ 'is-error': opinionOver || (currentSpec.minOpinionChars > 0 && opinionLength > 0 && opinionLength < currentSpec.minOpinionChars) }">
            {{ opinionHint }}
          </i>
        </label>

        <label v-if="currentSpec.toDept" class="dialog-field">
          <span>承接部门（必选；禁止回流已处理过的部门）</span>
          <el-select v-model="dialog.toDeptId" filterable placeholder="选择承接部门">
            <el-option v-for="option in deptOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <i class="hint">取不到组织选择器时可直接填写部门 id（服务端按 40001/40911 判定）</i>
        </label>

        <label v-if="currentSpec.targetSeq" class="dialog-field">
          <span>目标节点序号（必填，正整数）</span>
          <el-input v-model="dialog.targetSeq" placeholder="如 5" />
        </label>

        <template v-if="currentSpec.addSign">
          <label class="dialog-field">
            <span>加签类型</span>
            <el-select v-model="dialog.addSignType">
              <el-option label="前加签（加签人先审）" value="pre" />
              <el-option label="后加签（我同意后加签人再审）" value="post" />
            </el-select>
          </label>
          <label class="dialog-field">
            <span>加签人（用户 id）</span>
            <el-input v-model="dialog.delegateUserId" placeholder="如 302" />
          </label>
        </template>

        <label v-if="currentSpec.toUser" class="dialog-field">
          <span>目标处理人（用户 id）</span>
          <el-input v-model="dialog.toUserId" placeholder="如 302" />
          <i class="hint">转办/改派目标必须是同一数据域内可见该单据的人；服务端会校验其存在与在职状态</i>
        </label>

        <label v-if="currentSpec.ccUsers" class="dialog-field">
          <span>抄送人（用户 id，逗号分隔）</span>
          <el-input v-model="dialog.ccUserIds" placeholder="如 301,302" />
          <i class="hint">抄送只读可见：不产生待办、不参与决议（doc/enums.md §8）</i>
        </label>

        <p v-if="currentSpec.reasonRequiredEvidence" class="evidence">{{ currentSpec.reasonRequiredEvidence }}</p>
        <p v-if="dialogError" class="dialog-error">{{ dialogError }}</p>
      </div>

      <template #footer>
        <button class="btn btn-secondary" type="button" @click="dialog.open = false">取消</button>
        <button class="btn btn-primary" type="button" :disabled="acting" @click="confirmAction">
          {{ acting ? '提交中…' : '确认执行' }}
        </button>
      </template>
    </el-dialog>

    <p class="footnote">
      动作入参形状逐条对应后端 `RuntimeRequests`（意见 ≤1000 / 原因 ≤255 / 加签 pre|post / 流转需部门 …）；
      可用性判定与错误码文案见 `oa-web/src/utils/flow-task.ts`。抄送列表与打印属阶段 3。
    </p>
  </div>
</template>

<style scoped>
.oa-detail {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
  min-height: 100%;
}

.detail-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-sm);
  min-height: var(--oa-detail-head-h);
}

.head-main {
  display: flex;
  flex-wrap: wrap;
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
}

.biz-no {
  color: var(--oa-color-ink-subtle);
}

.head-actions {
  display: flex;
  gap: var(--oa-space-xs);
  margin-left: auto;
}

.meta {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.loading,
.runtime-notice {
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

.state-line {
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
  font: var(--oa-font-body-sm);
}

.state-card .meta,
.action-card .meta {
  display: block;
  padding: 4px var(--oa-space-md) 0;
}

.error-card .error-text,
.error-text {
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
  color: var(--oa-color-error);
  font: var(--oa-font-body-sm);
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
}

.notes,
.blocked ul {
  display: flex;
  flex-direction: column;
  gap: 4px;
  list-style: none;
  margin: var(--oa-space-xs) 0 0;
  padding: 0 var(--oa-space-md) var(--oa-space-xs) 32px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.blocked {
  padding: var(--oa-space-xs) var(--oa-space-md) var(--oa-space-sm);
  font: var(--oa-font-caption);
}

.blocked summary {
  cursor: pointer;
  color: var(--oa-color-ink-muted);
}

.perm {
  margin: 0 2px;
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
}

.detail-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--oa-space-md);
  align-items: start;
}

.trail {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-sm);
  margin: 0;
  padding: var(--oa-space-sm) var(--oa-space-md) var(--oa-space-sm) 32px;
  list-style: none;
}

.trail-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: var(--oa-space-xs);
}

.oa-step {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
  font: var(--oa-font-caption);
}

.trail-title {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  font: var(--oa-font-body-sm);
}

.opinion {
  margin-top: 2px;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.legend {
  padding: var(--oa-space-xs) var(--oa-space-md) var(--oa-space-sm);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.legend summary {
  cursor: pointer;
}

.legend ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px var(--oa-space-sm);
  margin: var(--oa-space-xs) 0 0;
  padding-left: 18px;
}

.legend-note {
  margin-left: 4px;
  color: var(--oa-color-warning);
  font-style: normal;
}

.node-list,
.task-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: var(--oa-space-sm) var(--oa-space-md) 0 32px;
  list-style: none;
  font: var(--oa-font-body-sm);
}

.node-list li,
.task-list li {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
}

.dialog-transition {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-sm);
  margin-top: var(--oa-space-sm);
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font: var(--oa-font-label);
  color: var(--oa-color-ink-muted);
}

.hint,
.evidence {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.hint.is-error,
.dialog-error,
.is-error {
  color: var(--oa-color-error);
}

.dialog-error {
  font: var(--oa-font-body-sm);
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

@media (max-width: 1280px) {
  .detail-body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
