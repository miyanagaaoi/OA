<script setup lang="ts">
/**
 * oa-web · 审批中心 · 列表页（阶段 2b：接**真实引擎**）
 * ----------------------------------------------------------------------------
 * 数据源（`api/flow-task.ts`，**四个**列表全部为已交付接口）：
 *   · 待我审批 = `GET /api/v1/flow-tasks/todo?page=&size=…`（`pending` 且我是处理人）
 *   · 我已审批 = `GET /api/v1/flow-tasks/done`
 *   · 我发起的 = `GET /api/v1/flow-tasks/initiated`
 *   · 抄送我的 = `GET /api/v1/flow-tasks/cc`（2026-10-04 后端 7c409ea 交付；出参含
 *     单号 / 类型 / 标题 / 发起人 / 发起时间 / 当前状态 / 抄送时间 + `readAt` / `read`）
 *   · 历史库（`/archive`）= **阶段 3**：归档检索接口未交付，本页如实标注（不展示假数据）。
 *
 * **四个列表同一套筛选**（服务端 `TaskListFilter` + SQL 层过滤，`total` 是**筛选后**的总数）：
 *   `keyword`（单号 / 标题 / 发起人）、`formType`、`status`（含子状态 `pending_supplement`）、
 *   `dateFrom` / `dateTo`（`YYYY-MM-DD`，口径是单据**发起时间**）。
 *   非法取值服务端回 400 / `40001` 并给出合法清单 —— 因此界面用**白名单下拉**，不给人手填错的机会。
 *
 * 与 2a 旧实现的差异（本页是**替换**，不是叠加）：
 *   1. 去掉批量同意 / 批量转办 —— 真实后端**没有**批量端点（`FlowTaskController` 只有逐任务动作），
 *      假入口会让用户以为已批量处理；批量需求属后续工作包（这条仍然成立）。
 *   2. 筛选**交给服务端**（不再是不做筛选，也不是「只筛当前页」的假筛选）。
 *   3. 待办徽标与 Tab 计数取自各自列表的 `total`（服务端分页总数，`long` → JSON 字符串已归一）。
 *
 * 来源：`doc/enums.md` §8（三层状态）、§2（主干 7 节点）；`doc/prd-0.1.md` §6.2（四类单据）；
 *       `DESIGN.md` › Layout（列表栏 / 表格 44px 行高 / 表头粘性 / 分页 32px）
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/http'
import { listCcTasks, listDoneTasks, listInitiatedTasks, listTodoTasks } from '@/api/flow-task'
import { formatDateTime } from '@/utils/format'
import { FORM_DOC_TYPE_GLYPH, FORM_DOC_TYPE_LABEL, FORM_DOC_TYPES, toFormDocType } from '@/utils/form-rules'
import {
  activeFilterCount,
  ccReadLabel,
  ccListRow,
  emptyListFilterDraft,
  instancePillStatus,
  instanceStatusLabel,
  isDateRangeReversed,
  LIST_STATUS_OPTIONS,
  normalizeListFilter,
  subStatusLabel,
  taskListRow,
  taskStatusLabel,
  toCount,
} from '@/utils/flow-task'
import { statusPillClass } from '@/utils/status'
import type { FlowListFilter, FlowListKind } from '@/types/flow-task'
import type { FlowListRow, ListFilterDraft } from '@/utils/flow-task'

const route = useRoute()
const router = useRouter()

const tabs: Array<{ key: FlowListKind; label: string; path: string }> = [
  { key: 'todo', label: '待我审批', path: '/task/pending' },
  { key: 'done', label: '我已审批', path: '/task/approved' },
  { key: 'initiated', label: '我发起的', path: '/task/initiated' },
  { key: 'cc', label: '抄送我的', path: '/task/cc' },
]

/** 四类单据的筛选下拉（取值与服务端白名单逐字一致） */
const formTypeOptions = FORM_DOC_TYPES.map((type) => ({ value: type, label: FORM_DOC_TYPE_LABEL[type] }))
/** 单据状态筛选（含子状态 `pending_supplement`；与服务端 `TaskListFilter` 的合法值一致） */
const statusOptions = LIST_STATUS_OPTIONS

const activeTab = computed<FlowListKind>(() => (route.meta.tab as FlowListKind | undefined) ?? 'todo')
const isArchive = computed(() => route.meta.archiveOnly === true)

const items = ref<FlowListRow[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const counts = reactive<Record<FlowListKind, number>>({ todo: 0, done: 0, initiated: 0, cc: 0 })
const page = ref(1)
const pageSize = ref(20)

/**
 * 筛选草稿（界面输入）与**已生效**的筛选（已提交给服务端的那一份）。
 *
 * 分两份的原因：用户可能改了输入还没点「查询」，此时列表仍应显示上一次查询的结果
 * （否则会出现「输入一个字就换一次结果」的抖动与竞态）。计数与列表都用**已生效**的那份。
 */
const filterDraft = reactive(emptyListFilterDraft())
const applied = ref<FlowListFilter>({})

const activeTabLabel = computed(() => tabs.find((tab) => tab.key === activeTab.value)?.label ?? '待我审批')
const totalPages = computed(() => (total.value > 0 ? Math.ceil(total.value / pageSize.value) : 1))
/** 已生效筛选（回填成草稿形状，复用 `activeFilterCount` 的同一口径） */
const appliedDraft = computed<ListFilterDraft>(() => ({
  keyword: applied.value.keyword ?? '',
  formType: applied.value.formType ?? '',
  status: applied.value.status ?? '',
  dateFrom: applied.value.dateFrom ?? '',
  dateTo: applied.value.dateTo ?? '',
}))
const appliedCount = computed(() => activeFilterCount(appliedDraft.value))
const dateRangeReversed = computed(() => isDateRangeReversed(filterDraft.dateFrom, filterDraft.dateTo))
const isCcTab = computed(() => activeTab.value === 'cc')

onMounted(async () => {
  await Promise.all([loadList(), loadCounts()])
})

watch(
  () => route.fullPath,
  async () => {
    page.value = 1
    errorText.value = ''
    resetFilter()
    await Promise.all([loadList(), loadCounts()])
  },
)

/** 清空筛选草稿与已生效筛选 */
function resetFilter(): void {
  Object.assign(filterDraft, emptyListFilterDraft())
  applied.value = {}
}

/** 「查询」：把草稿归一成已生效筛选（空串不下发）→ 回第 1 页重取 */
async function applyFilter(): Promise<void> {
  if (dateRangeReversed.value) return
  const normalized = normalizeListFilter(filterDraft)
  const next: FlowListFilter = {}
  if (normalized.keyword) next.keyword = normalized.keyword
  if (normalized.formType) next.formType = normalized.formType
  if (normalized.status) next.status = normalized.status
  if (normalized.dateFrom) next.dateFrom = normalized.dateFrom
  if (normalized.dateTo) next.dateTo = normalized.dateTo
  applied.value = next
  page.value = 1
  await loadList()
}

/** 「重置」：清空筛选后重取（计数**不**带筛选，保持 Tab 角标是全局口径） */
async function resetAndReload(): Promise<void> {
  resetFilter()
  page.value = 1
  await loadList()
}

async function loadList(): Promise<void> {
  if (isArchive.value) {
    items.value = []
    total.value = 0
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const result = await fetchByKind(activeTab.value, page.value, pageSize.value, applied.value)
    items.value = result.items
    total.value = result.total
  } catch (error) {
    items.value = []
    total.value = 0
    errorText.value =
      error instanceof ApiError
        ? `${error.message}${error.code ? `（业务码 ${error.code}）` : ''}`
        : '列表加载失败'
  } finally {
    loading.value = false
  }
}

/** 四个 Tab 的计数（**不带筛选**：Tab 角标是「我有多少事」的全局口径，不是当前筛选的条数） */
async function loadCounts(): Promise<void> {
  const kinds: FlowListKind[] = ['todo', 'done', 'initiated', 'cc']
  await Promise.all(
    kinds.map(async (kind) => {
      try {
        const result = await fetchByKind(kind, 1, 1)
        counts[kind] = toCount(result.total, 0)
      } catch {
        counts[kind] = 0
      }
    }),
  )
}

/** 按列表类型取数，并把两种出参归一成同一个行视图（`utils/flow-task.ts` 的可测纯函数） */
async function fetchByKind(
  kind: FlowListKind,
  targetPage: number,
  size: number,
  filter?: FlowListFilter,
): Promise<{ items: FlowListRow[]; total: number }> {
  switch (kind) {
    case 'done': {
      const result = await listDoneTasks(targetPage, size, filter)
      return { items: result.items.map(taskListRow), total: result.total }
    }
    case 'initiated': {
      const result = await listInitiatedTasks(targetPage, size, filter)
      return { items: result.items.map(taskListRow), total: result.total }
    }
    case 'cc': {
      const result = await listCcTasks(targetPage, size, filter)
      return { items: result.items.map(ccListRow), total: result.total }
    }
    case 'todo':
    default: {
      const result = await listTodoTasks(targetPage, size, filter)
      return { items: result.items.map(taskListRow), total: result.total }
    }
  }
}

function goto(path: string): void {
  void router.push(path)
}

/** 点行 / 点「查看」都进详情页（抄送单同样可进，打开即由服务端写已读） */
function openDetail(row: FlowListRow): void {
  void router.push({ name: 'task-detail', params: { id: row.instanceId } })
}

function formTypeLabelOf(row: FlowListRow): string {
  const type = toFormDocType(row.formType)
  return type ? FORM_DOC_TYPE_LABEL[type] : row.formType || '未知类型'
}

function formTypeGlyphOf(row: FlowListRow): string {
  const type = toFormDocType(row.formType)
  return type ? FORM_DOC_TYPE_GLYPH[type] : '单'
}

/** 标题列：`title` 缺失时回落到类型名（关键字筛选命中标题，界面必须把标题显示出来） */
function titleOf(row: FlowListRow): string {
  return row.title || formTypeLabelOf(row)
}

function changePage(next: number): void {
  const target = Math.max(1, Math.min(next, totalPages.value))
  if (target === page.value) return
  page.value = target
  void loadList()
}

function changePageSize(size: number): void {
  pageSize.value = size
  page.value = 1
  void loadList()
}

/** 待办行主按钮文案（非待办不出现主按钮） */
function canAct(row: FlowListRow): boolean {
  return activeTab.value === 'todo' && row.taskId !== '' && row.taskStatus === 'pending'
}
</script>

<template>
  <div class="oa-center">
    <!-- ================= 列表栏 ================= -->
    <section class="list-pane">
      <nav class="tabs" aria-label="工作台标签">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ 'is-active': activeTab === tab.key && !isArchive }"
          type="button"
          @click="goto(tab.path)"
        >
          {{ tab.label }}
          <i class="oa-tnum">{{ counts[tab.key] }}</i>
        </button>
      </nav>

      <h1 class="page-title">{{ isArchive ? '历史库' : activeTabLabel }}</h1>

      <!-- 历史库：阶段 3（归档检索接口未交付） -->
      <div v-if="isArchive" class="notice">
        <p class="notice-title">历史库待阶段 3 实现</p>
        <p>
          归档检索接口（`admin:archive:search` 对应的查询端点）尚未交付，本页**不再展示演示数据**。
          已归档单据仍可通过「我已审批 / 我发起的」查到其单号，再进入详情页只读查看。
        </p>
      </div>

      <template v-else>
        <!-- 筛选区：四个列表同一套参数，过滤在**服务端 SQL 层**（不是「只筛当前页」） -->
        <form class="filters" @submit.prevent="applyFilter">
          <label class="f-item f-keyword">
            <span>关键字</span>
            <input
              v-model="filterDraft.keyword"
              type="search"
              placeholder="单号 / 标题 / 发起人"
              autocomplete="off"
            />
          </label>
          <label class="f-item">
            <span>单据类型</span>
            <select v-model="filterDraft.formType">
              <option value="">全部</option>
              <option v-for="option in formTypeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="f-item">
            <span>单据状态</span>
            <select v-model="filterDraft.status">
              <option value="">全部</option>
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="f-item">
            <span>发起日期起</span>
            <input v-model="filterDraft.dateFrom" type="date" />
          </label>
          <label class="f-item">
            <span>发起日期止</span>
            <input v-model="filterDraft.dateTo" type="date" />
          </label>
          <div class="f-actions">
            <button class="btn btn-primary" type="submit" :disabled="loading || dateRangeReversed">查询</button>
            <button class="btn btn-secondary" type="button" :disabled="loading" @click="resetAndReload">重置</button>
          </div>
        </form>

        <p v-if="dateRangeReversed" class="filter-hint is-error">
          「日期起」不能晚于「日期止」（服务端会按 400 / 40001 拒绝该区间）。
        </p>
        <p v-else class="filter-hint">
          <b>筛选在服务端执行</b>（`keyword` / `formType` / `status` / `dateFrom` / `dateTo`），
          「共 N 条」是**筛选后**的总数；日期口径是单据**发起时间**，含首含尾。
          <span v-if="appliedCount > 0">当前已生效 {{ appliedCount }} 个条件。</span>
        </p>

        <!-- 能力缺失说明（**只保留仍然成立的项**） -->
        <p class="capability-note">
          <b>能力说明</b>：批量同意、批量转办**仍待后端支持** —— 真实后端只有逐任务动作端点
          （`POST /flow-tasks/{taskId}/…`），没有批量端点；为避免「点了没生效」的假功能，这些入口先不渲染。
        </p>

        <div v-if="errorText" class="notice is-error">
          <p class="notice-title">列表加载失败</p>
          <p>{{ errorText }}</p>
          <button class="btn btn-secondary" type="button" @click="loadList">重试</button>
        </div>

        <div class="table-wrap">
          <table class="oa-table">
            <thead>
              <tr>
                <th style="width: 156px">单号</th>
                <th>单据 / 当前节点</th>
                <th style="width: 120px">发起人</th>
                <th v-if="isCcTab" style="width: 100px">已读状态</th>
                <th v-else style="width: 110px">我的任务</th>
                <th style="width: 110px">单据状态</th>
                <th style="width: 168px">{{ isCcTab ? '抄送时间' : '时间' }}</th>
                <th style="width: 96px">操作</th>
              </tr>
            </thead>

            <tbody>
              <tr v-if="loading">
                <td class="c empty" colspan="7">正在加载…</td>
              </tr>
              <tr v-else-if="items.length === 0">
                <td class="c empty" colspan="7">
                  <div class="oa-empty">
                    <p>{{ appliedCount > 0 ? '当前筛选条件下没有单据。' : '当前列表没有单据。' }}</p>
                    <p class="oa-text-caption oa-text-subtle">
                      <template v-if="appliedCount > 0">
                        筛选在服务端执行：请放宽关键字 / 类型 / 状态 / 日期区间后重试。
                      </template>
                      <template v-else-if="isCcTab">
                        抄送只读可见、不产生待办；没有单据说明暂时没有人抄送你。
                      </template>
                      <template v-else>
                        待办为空说明没有分配给你的待处理任务；「我发起的」只列出你本人发起的单据
                        （数据域过滤由服务端执行）。
                      </template>
                    </p>
                  </div>
                </td>
              </tr>
              <tr v-for="row in items" v-else :key="row.key" @click="openDetail(row)">
                <td class="oa-mono">{{ row.bizNo || row.instanceId }}</td>
                <td>
                  <div class="cell-title">
                    <span class="type-ico" aria-hidden="true">{{ formTypeGlyphOf(row) }}</span>
                    <span class="title-text" :title="titleOf(row)">{{ titleOf(row) }}</span>
                    <span v-if="row.addSignType" class="oa-tag is-info">
                      {{ row.addSignType === 'pre' ? '前加签' : '后加签' }}
                    </span>
                  </div>
                  <div class="cell-meta">
                    <template v-if="isCcTab">
                      类型：{{ formTypeLabelOf(row) }}
                      <template v-if="row.nodeSeq !== null"> · 当前节点{{ row.nodeSeq }}</template>
                      <template v-if="row.instanceCreatedAt"> · 发起于 {{ formatDateTime(row.instanceCreatedAt) }}</template>
                    </template>
                    <template v-else>
                      节点{{ row.nodeSeq ?? '—' }} · {{ row.nodeName || '—' }}
                      <template v-if="subStatusLabel(row.subStatus)"> · {{ subStatusLabel(row.subStatus) }}</template>
                    </template>
                  </div>
                </td>
                <td>{{ row.initiatorName || '—' }}</td>
                <td v-if="isCcTab">
                  <span class="oa-pill" :class="row.read ? 'is-closed' : 'is-pending'">
                    {{ ccReadLabel(row.read, row.readAt ? formatDateTime(row.readAt) : null) }}
                  </span>
                </td>
                <td v-else>
                  <span v-if="row.taskStatus" class="oa-pill" :class="row.taskStatus === 'pending' ? 'is-pending' : 'is-closed'">
                    {{ taskStatusLabel(row.taskStatus, row.taskStatusLabel) }}
                  </span>
                  <span v-else class="cell-meta">—</span>
                </td>
                <td>
                  <span class="oa-pill" :class="statusPillClass(instancePillStatus(row.instanceStatus) as never)">
                    {{ instanceStatusLabel(row.instanceStatus) }}
                  </span>
                </td>
                <td class="oa-mono">{{ row.time ? formatDateTime(row.time) : '—' }}</td>
                <td class="c">
                  <button class="btn btn-ghost" type="button" @click.stop="openDetail(row)">
                    {{ canAct(row) ? '去审批' : '查看' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="table-foot">
          <span>共 <b class="oa-tnum">{{ total }}</b> 条<template v-if="appliedCount > 0">（筛选后）</template></span>
          <label class="page-size">
            每页
            <select
              :value="pageSize"
              @change="changePageSize(Number(($event.target as HTMLSelectElement).value))"
            >
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
            条
          </label>
          <span class="spacer" />
          <span class="pager">
            <button class="pgbtn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
            <button class="pgbtn is-active" type="button">
              <i class="oa-tnum">{{ page }}</i>
              <span class="pg-total">/ {{ totalPages }}</span>
            </button>
            <button class="pgbtn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
          </span>
        </footer>
      </template>
    </section>

    <!-- ================= 详情栏 ================= -->
    <aside class="detail-pane">
      <header class="detail-head">
        <h2 class="detail-title">单据详情</h2>
        <span class="oa-text-caption oa-text-subtle">点任意一行进入详情页（含表单、轨迹与审批动作）</span>
      </header>

      <div class="detail-body">
        <div class="oa-empty">
          <p>审批中心采用「列表 + 详情」双栏并置。</p>
          <p class="oa-text-body-sm oa-text-muted">
            列表数据来自真实引擎（`/flow-tasks/todo|done|initiated|cc`）；详情页的审批动作可用性
            由服务端的动作面清单（`GET /flow-actions`）+ 实例状态 + 节点开关共同判定，
            不可用时**直接说明原因**。抄送我的一行点进去即视为已读（服务端写 `read_at`）。
          </p>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.oa-center {
  display: grid;
  grid-template-columns: var(--oa-panel-list) minmax(0, 1fr);
  min-height: 100%;
  background: var(--oa-color-canvas);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-md);
  overflow: hidden;
}

.list-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid var(--oa-color-hairline);
}

.tabs {
  display: flex;
  gap: var(--oa-space-md);
  flex: none;
  height: 40px;
  padding: 0 var(--oa-space-md);
  border-bottom: 1px solid var(--oa-color-hairline);
  overflow-x: auto;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-label);
  white-space: nowrap;
  cursor: pointer;
}

.tab.is-active {
  color: var(--oa-color-primary);
  border-bottom-color: var(--oa-color-primary);
}

.tab i {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
  font-style: normal;
}

.tab.is-active i {
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
}

.page-title {
  flex: none;
  padding: var(--oa-space-sm) var(--oa-space-md) 0;
  font: var(--oa-font-title-page);
  letter-spacing: var(--oa-letter-spacing-title-page);
  color: var(--oa-color-ink);
}

/* ================= 筛选区 ================= */
.filters {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--oa-space-xs) var(--oa-space-sm);
  padding: var(--oa-space-sm) var(--oa-space-md);
  border-bottom: 1px solid var(--oa-color-hairline);
  background: var(--oa-color-canvas-subtle);
}

.f-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.f-item input,
.f-item select {
  height: var(--oa-space-control-compact);
  min-width: 128px;
  padding: 0 6px;
  border: 1px solid var(--oa-color-hairline-strong);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  font: var(--oa-font-body-sm);
}

.f-keyword input {
  min-width: 200px;
}

.f-actions {
  display: flex;
  gap: var(--oa-space-xs);
  margin-left: auto;
}

.filter-hint {
  flex: none;
  padding: 4px var(--oa-space-md) 0;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.filter-hint.is-error {
  color: var(--oa-color-error);
}

.capability-note {
  flex: none;
  margin: var(--oa-space-xs) var(--oa-space-md) 0;
  padding: 4px 6px;
  border-left: 2px solid var(--oa-color-warning);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
}

.capability-note b {
  color: var(--oa-color-warning);
}

.notice {
  margin: var(--oa-space-sm) var(--oa-space-md);
  padding: var(--oa-space-sm);
  border: 1px dashed var(--oa-color-hairline-strong);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.notice p {
  margin: 0 0 4px;
}

.notice-title {
  color: var(--oa-color-ink);
  font-weight: 500;
}

.notice.is-error {
  border-color: var(--oa-color-error);
  color: var(--oa-color-error);
}

.notice .btn {
  margin-top: var(--oa-space-xs);
}

.table-wrap {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.oa-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font: var(--oa-font-body-sm);
}

.oa-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 40px;
  padding: 0 var(--oa-space-sm);
  background: var(--oa-color-canvas-subtle);
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-table-header);
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--oa-color-hairline);
}

.oa-table td {
  height: 44px;
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border-bottom: 1px solid var(--oa-color-hairline);
  vertical-align: middle;
}

.oa-table tbody tr {
  cursor: pointer;
}

.oa-table tbody tr:hover td {
  background: var(--oa-color-canvas-subtle);
}

.oa-table .c {
  text-align: center;
}

.oa-table .empty {
  height: auto;
  padding: var(--oa-space-md);
}

.cell-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.type-ico {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
  font: var(--oa-font-caption);
}

.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.cell-meta {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.oa-empty {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  align-items: center;
  text-align: center;
}

.btn {
  height: var(--oa-space-control-compact);
  padding: 0 var(--oa-space-xs);
  border: 1px solid transparent;
  border-radius: var(--oa-radius-sm);
  font: var(--oa-font-button);
  cursor: pointer;
}

.btn:disabled {
  cursor: not-allowed;
  color: var(--oa-color-ink-disabled);
}

.btn-primary {
  padding: 0 var(--oa-space-sm);
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
  padding: 0 var(--oa-space-sm);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  border-color: var(--oa-color-hairline-strong);
}

.btn-ghost {
  background: transparent;
  color: var(--oa-color-primary);
}

.table-foot {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--oa-space-sm);
  padding: var(--oa-space-xs) var(--oa-space-md);
  border-top: 1px solid var(--oa-color-hairline);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.page-size select {
  height: 24px;
  margin: 0 4px;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  font: var(--oa-font-caption);
}

.spacer {
  flex: 1 1 auto;
}

.pager {
  display: flex;
  gap: 2px;
}

.pgbtn {
  min-width: 32px;
  height: 32px;
  padding: 0 var(--oa-space-xs);
  border: 0;
  border-radius: var(--oa-radius-xs);
  background: transparent;
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
  cursor: pointer;
}

.pgbtn:disabled {
  color: var(--oa-color-ink-disabled);
  cursor: not-allowed;
}

.pgbtn.is-active {
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
}

.pg-total {
  margin-left: 4px;
  color: var(--oa-color-ink-subtle);
}

.detail-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.detail-head {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-sm);
  min-height: var(--oa-detail-head-h);
  padding: var(--oa-space-xs) var(--oa-space-lg);
  border-bottom: 1px solid var(--oa-color-hairline);
}

.detail-title {
  font: var(--oa-font-title-section);
  color: var(--oa-color-ink);
}

.detail-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: var(--oa-space-lg);
}

@media (max-width: 1280px) {
  .oa-center {
    grid-template-columns: var(--oa-panel-list-min) minmax(0, 1fr);
  }
}

@media (max-width: 1024px) {
  .oa-center {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-pane {
    display: none;
  }

  .list-pane {
    border-right: 0;
  }
}
</style>
