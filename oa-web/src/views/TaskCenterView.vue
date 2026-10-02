<script setup lang="ts">
/**
 * oa-web · 审批中心 · 列表页（待我审批 / 我已审批 / 我发起的 / 抄送我的）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `DESIGN.md` › Layout（审批列表栏固定 380px、列表项两行结构；业务表格仍用 table）
 *     › Components › Data Display（44px 行高、金额 tnum 右对齐、状态徽标、
 *       分页 32px + 每页条数、表头粘性）
 *     › Responsive Strategy（≥1025px 用表格；≤768px 单列流式 —— 卡片列表）
 *   · `doc/prd-0.1.md` 6.2（四类单据）、6.9（审计日志用紧凑表格）
 *   · `normify-oa/modules/oa/portal/workbench/**`（tabs / filter / table / batch / empty）
 *
 * 交互要点：
 *   · 筛选：关键字 + 单据类型 + 状态 + 日期区间
 *   · 分页：10/20/50 + 总条数；当前页 primary + primary-subtle 底
 *   · 批量：勾选后出现批量同意 / 批量转办，危险操作二次确认且确认文案写明动作与对象
 *   · 状态标签：只从附录 B 的五个 status-pill 里选
 *   · 金额 ≥100 万同时显示"万元"换算
 *   · 一屏一主按钮：本页主按钮为「批量同意」，仅在勾选后出现
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchApprove,
  batchTransfer,
  fetchWorkbenchFilters,
  fetchWorkbenchSummary,
  queryWorkbench,
} from '@/api/task'
import { ApiError } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { formatAmount, formatDateTime, formatWan } from '@/utils/format'
import { FORM_TYPE_GLYPH, nodeNoGlyphSafe, statusLabel, statusPillClass } from '@/utils/status'
import type {
  WorkbenchFilterOption,
  WorkbenchItem,
  WorkbenchQuery,
  WorkbenchSummary,
  WorkbenchTab,
} from '@/types/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs: Array<{ key: WorkbenchTab; label: string; path: string }> = [
  { key: 'pending', label: '待我审批', path: '/task/pending' },
  { key: 'approved', label: '我已审批', path: '/task/approved' },
  { key: 'initiated', label: '我发起的', path: '/task/initiated' },
  { key: 'cc', label: '抄送我的', path: '/task/cc' },
]

const activeTab = computed<WorkbenchTab>(() => (route.meta.tab as WorkbenchTab | undefined) ?? 'pending')
const isArchive = computed(() => route.meta.archiveOnly === true)

const summary = ref<WorkbenchSummary>({ pending: 0, approved: 0, initiated: 0, cc: 0, timeoutRisk: 0 })
const filters = ref<WorkbenchFilterOption[]>([])
const list = ref<WorkbenchItem[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<WorkbenchItem[]>([])

const query = reactive<WorkbenchQuery>({
  tab: 'pending',
  keyword: '',
  formTypes: [],
  statuses: [],
  dateFrom: '',
  dateTo: '',
  page: 1,
  pageSize: 20,
})

const formTypeOptions = computed(
  () => filters.value.find((item) => item.key === 'formType')?.options ?? [
    { value: 'matter', label: '事项审批单' },
    { value: 'fund', label: '资金审批单' },
    { value: 'contract', label: '合同审批单' },
    { value: 'seal_cert', label: '印鉴证照审批单' },
  ],
)

const statusOptions = computed(
  () => filters.value.find((item) => item.key === 'status')?.options ?? [],
)

const hasSelection = computed(() => selected.value.length > 0)
const selectedTaskIds = computed(() =>
  selected.value.map((item) => item.taskId).filter((id): id is string => Boolean(id)),
)

/** 表格批量操作只对「待我审批」开放 */
const batchEnabled = computed(() => activeTab.value === 'pending' && !isArchive.value)

onMounted(async () => {
  await Promise.all([loadSummary(), loadFilters()])
  await loadList()
})

watch(
  () => route.fullPath,
  async () => {
    query.page = 1
    query.tab = activeTab.value
    selected.value = []
    await loadList()
  },
)

async function loadSummary(): Promise<void> {
  try {
    summary.value = await fetchWorkbenchSummary()
  } catch {
    summary.value = { pending: 0, approved: 0, initiated: 0, cc: 0, timeoutRisk: 0 }
  }
}

async function loadFilters(): Promise<void> {
  try {
    filters.value = await fetchWorkbenchFilters()
  } catch {
    filters.value = []
  }
}

async function loadList(): Promise<void> {
  loading.value = true
  query.tab = activeTab.value
  try {
    const result = await queryWorkbench({ ...query })
    list.value = result.list
    total.value = result.total
  } catch (error) {
    list.value = []
    total.value = 0
    if (error instanceof ApiError && error.httpStatus !== 401) {
      ElMessage({ type: 'error', message: error.message })
    }
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.page = 1
  void loadList()
}

function resetFilters(): void {
  query.keyword = ''
  query.formTypes = []
  query.statuses = []
  query.dateFrom = ''
  query.dateTo = ''
  query.page = 1
  void loadList()
}

function changePage(page: number): void {
  query.page = page
  void loadList()
}

function changePageSize(size: number): void {
  query.pageSize = size
  query.page = 1
  void loadList()
}

function openDetail(item: WorkbenchItem): void {
  void router.push({ name: 'task-detail', params: { id: item.instanceId } })
}

function tabCount(key: WorkbenchTab): number {
  return summary.value[key] ?? 0
}

/** 批量同意：危险度低，但仍二次确认（告知数量与对象范围） */
async function handleBatchApprove(): Promise<void> {
  const ids = selectedTaskIds.value
  if (ids.length === 0) {
    ElMessage({ type: 'warning', message: '所选单据没有可操作的待办任务' })
    return
  }

  let opinion = ''
  try {
    const result = await ElMessageBox.prompt(
      `将对选中的 ${ids.length} 张单据执行同意操作，请填写审批意见。`,
      `确认同意 ${ids.length} 张单据`,
      {
        confirmButtonText: '确认同意',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '审批意见（选填，最多 500 字）',
        inputValidator: (value: string) => (value?.length ?? 0) <= 500 || '审批意见不得超过 500 字',
      },
    )
    opinion = result.value ?? ''
  } catch {
    return
  }

  try {
    const result = await batchApprove({ taskIds: ids, opinion })
    const failed = result.failed?.length ?? 0
    ElMessage({
      type: failed > 0 ? 'warning' : 'success',
      message: failed > 0 ? `已同意 ${result.succeeded.length} 张，${failed} 张失败（状态已变更）` : `已同意 ${result.succeeded.length} 张单据`,
    })
    selected.value = []
    await Promise.all([loadList(), loadSummary()])
  } catch (error) {
    ElMessage({ type: 'error', message: error instanceof Error ? error.message : '批量同意失败' })
  }
}

/** 批量转办：必须填写转办原因与接收人 */
async function handleBatchTransfer(): Promise<void> {
  const ids = selectedTaskIds.value
  if (ids.length === 0) {
    ElMessage({ type: 'warning', message: '所选单据没有可操作的待办任务' })
    return
  }

  let targetUserId = ''
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `将把选中的 ${ids.length} 个任务转办给他人，转办对象必须是同一数据域内可见该单据的人。请填写「接收人工号 / 转办原因」。`,
      `确认转办 ${ids.length} 个任务`,
      {
        confirmButtonText: '确认转办',
        cancelButtonText: '取消',
        inputPlaceholder: '例：10086 / 外出期间由同事代办',
        inputValidator: (value: string) => {
          if (!value || !value.includes('/')) return '请按「接收人工号 / 转办原因」填写'
          return true
        },
      },
    )
    const [uid, ...rest] = (result.value ?? '').split('/')
    targetUserId = uid.trim()
    reason = rest.join('/').trim()
  } catch {
    return
  }

  try {
    const result = await batchTransfer({ taskIds: ids, targetUserId, reason })
    ElMessage({ type: 'success', message: `已转办 ${result.succeeded.length} 个任务` })
    selected.value = []
    await Promise.all([loadList(), loadSummary()])
  } catch (error) {
    ElMessage({ type: 'error', message: error instanceof Error ? error.message : '批量转办失败' })
  }
}
</script>

<template>
  <div class="oa-center" :class="{ 'is-h5': false }">
    <!-- ================= 列表栏 ================= -->
    <section class="list-pane">
      <!-- 标签页：待办数量用徽标显示在标签右侧，不使用彩色圆点 -->
      <nav class="tabs" aria-label="工作台标签">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ 'is-active': activeTab === tab.key && !isArchive }"
          type="button"
          @click="router.push(tab.path)"
        >
          {{ tab.label }}
          <i class="oa-tnum">{{ tabCount(tab.key) }}</i>
        </button>
      </nav>

      <h1 class="page-title">{{ isArchive ? '历史库' : (route.meta.title || '待我审批') }}</h1>

      <!-- 筛选区 -->
      <div class="filters">
        <input
          v-model="query.keyword"
          class="control"
          type="search"
          placeholder="搜索单号 / 标题 / 发起人"
          @keyup.enter="search"
        />

        <div class="filter-row">
          <select v-model="query.formTypes" class="control" multiple size="1" aria-label="单据类型">
            <option v-for="opt in formTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <select v-model="query.statuses" class="control" multiple size="1" aria-label="单据状态">
            <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>

        <div class="filter-row">
          <input v-model="query.dateFrom" class="control" type="date" aria-label="起始日期" />
          <span class="tilde">—</span>
          <input v-model="query.dateTo" class="control" type="date" aria-label="结束日期" />
        </div>

        <div class="filter-actions">
          <button class="btn btn-secondary" type="button" @click="resetFilters">重置</button>
          <button class="btn btn-primary" type="button" @click="search">查询</button>
        </div>

        <p v-if="!isArchive && summary.timeoutRisk > 0" class="timeout-hint">
          其中 <b class="oa-tnum">{{ summary.timeoutRisk }}</b> 张已进入超时预警，请优先处理。
        </p>
      </div>

      <!-- 批量操作条：仅勾选后出现，主按钮一屏只出现一次 -->
      <div v-if="hasSelection" class="batch-bar">
        <span class="batch-count">已选 <b class="oa-tnum">{{ selected.length }}</b> 张</span>
        <button class="btn btn-ghost" type="button" @click="selected = []">取消选择</button>
        <span class="spacer" />
        <button v-if="batchEnabled" class="btn btn-secondary" type="button" @click="handleBatchTransfer">批量转办</button>
        <button v-if="batchEnabled" class="btn btn-primary" type="button" @click="handleBatchApprove">批量同意</button>
      </div>

      <!-- 桌面：数据表（44px 行高 / 表头粘性 / 金额右对齐等宽） -->
      <div class="table-wrap">
        <table class="oa-table">
          <thead>
            <tr>
              <th class="c" style="width: 36px">
                <input
                  type="checkbox"
                  :checked="selected.length > 0 && selected.length === list.length"
                  aria-label="全选"
                  @change="
                    selected = ($event.target as HTMLInputElement).checked
                      ? list.filter((item) => item.taskId)
                      : []
                  "
                />
              </th>
              <th style="width: 148px">单号</th>
              <th>标题</th>
              <th style="width: 150px">发起人 / 部门</th>
              <th class="r" style="width: 168px">金额</th>
              <th style="width: 120px">当前节点</th>
              <th style="width: 96px">状态</th>
              <th style="width: 132px">发起时间</th>
            </tr>
          </thead>

          <tbody>
            <tr v-if="loading">
              <td class="c empty" colspan="8">正在加载…</td>
            </tr>
            <tr v-else-if="list.length === 0">
              <td class="c empty" colspan="8">
                <div class="oa-empty">
                  <p>当前筛选条件下没有单据。</p>
                  <button class="btn btn-secondary" type="button" @click="resetFilters">清空筛选条件</button>
                </div>
              </td>
            </tr>
            <tr
              v-for="item in list"
              v-else
              :key="item.instanceId"
              :class="{ 'is-selected': selected.some((s) => s.instanceId === item.instanceId) }"
              @click="openDetail(item)"
            >
              <td class="c" @click.stop>
                <input
                  v-model="selected"
                  type="checkbox"
                  :value="item"
                  :disabled="!item.taskId"
                  :aria-label="`选择 ${item.bizNo}`"
                />
              </td>
              <td class="oa-mono">{{ item.bizNo }}</td>
              <td>
                <div class="cell-title">
                  <span class="type-ico" aria-hidden="true">{{ FORM_TYPE_GLYPH[item.formType] }}</span>
                  <span class="title-text">{{ item.title }}</span>
                  <span v-if="item.collaborationProgress" class="oa-tag is-info">
                    协同 {{ item.collaborationProgress }}
                  </span>
                  <span v-if="item.ccOnly" class="oa-tag">抄送</span>
                </div>
                <div class="cell-meta">
                  {{ item.formTypeLabel }}
                  <template v-if="item.dueAt">
                    · 截止 <i class="oa-mono">{{ formatDateTime(item.dueAt) }}</i>
                  </template>
                </div>
              </td>
              <td>
                <div class="cell-strong">{{ item.initiatorName }}</div>
                <div class="cell-meta">{{ item.deptName }}</div>
              </td>
              <td class="oa-amount">
                <template v-if="item.amount">
                  {{ formatAmount(item.amount) }}
                  <span v-if="formatWan(item.amount)" class="wan">{{ formatWan(item.amount) }}</span>
                </template>
                <span v-else class="cell-meta">—</span>
              </td>
              <td>
                <span v-if="item.currentNodeName" class="node-cell">
                  <i v-if="nodeNoGlyphSafe(item.currentNodeNo)" class="node-no">
                    {{ nodeNoGlyphSafe(item.currentNodeNo) }}
                  </i>
                  {{ item.currentNodeName }}
                </span>
                <span v-else class="cell-meta">—</span>
              </td>
              <td>
                <span class="oa-pill" :class="statusPillClass(item.status)">
                  {{ statusLabel(item.status, item.statusLabel) }}
                </span>
              </td>
              <td class="oa-mono">{{ formatDateTime(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页：32px 高、每页条数切换 + 总条数 -->
      <footer class="table-foot">
        <span>共 <b class="oa-tnum">{{ total }}</b> 条</span>
        <label class="page-size">
          每页
          <select :value="query.pageSize" @change="changePageSize(Number(($event.target as HTMLSelectElement).value))">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
          条
        </label>
        <span class="spacer" />
        <span class="pager">
          <button
            class="pgbtn"
            type="button"
            :disabled="query.page <= 1"
            @click="changePage(query.page - 1)"
          >
            上一页
          </button>
          <button class="pgbtn is-active" type="button">
            <i class="oa-tnum">{{ query.page }}</i>
          </button>
          <button
            class="pgbtn"
            type="button"
            :disabled="query.page * query.pageSize >= total"
            @click="changePage(query.page + 1)"
          >
            下一页
          </button>
        </span>
      </footer>
    </section>

    <!-- ================= 详情栏（桌面并置；窄屏点击列表项跳详情页） ================= -->
    <aside class="detail-pane">
      <header class="detail-head">
        <h2 class="detail-title">单据详情</h2>
        <span class="oa-text-caption oa-text-subtle">选择左侧任一单据，或打开独立详情页</span>
      </header>

      <div class="detail-body">
        <div class="oa-empty">
          <p>审批中心采用「列表 + 详情」双栏并置。</p>
          <p class="oa-text-body-sm oa-text-muted">
            点击列表中的单据即在右侧就地加载详情，不跳页；窄屏（≤768px）自动切换为「列表 → 详情」两级。
          </p>
          <p class="oa-text-caption oa-text-subtle">
            当前登录：{{ userStore.displayName }} · 工号 {{ userStore.employeeNo }}
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
  gap: 0;
  min-height: 100%;
  background: var(--oa-color-canvas);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-md);
  overflow: hidden;
}

/* ---------------- 列表栏 ---------------- */
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

.tab:hover {
  color: var(--oa-color-ink);
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

.filters {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-sm) var(--oa-space-md);
  border-bottom: 1px solid var(--oa-color-hairline);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

.tilde {
  color: var(--oa-color-ink-disabled);
}

.control {
  flex: 1 1 auto;
  min-width: 0;
  height: var(--oa-space-control);
  padding: 0 var(--oa-space-sm);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  font: var(--oa-font-body-sm);
}

.control:focus {
  border-color: var(--oa-color-primary);
  outline: none;
  box-shadow: var(--oa-shadow-focus-ring);
}

.filter-actions {
  display: flex;
  gap: var(--oa-space-xs);
  justify-content: flex-end;
}

.timeout-hint {
  font: var(--oa-font-caption);
  color: var(--oa-color-warning);
}

.batch-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-xs) var(--oa-space-md);
  background: var(--oa-color-primary-subtle);
  border-bottom: 1px solid var(--oa-color-hairline);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.batch-count b {
  font-weight: 500;
}

.spacer {
  flex: 1 1 auto;
}

/* 通用按钮（本页用原生按钮 + 令牌，避免 Element Plus 默认观感渗入） */
.btn {
  height: var(--oa-space-control);
  padding: 0 var(--oa-space-md);
  border: 1px solid transparent;
  border-radius: var(--oa-radius-sm);
  font: var(--oa-font-button);
  white-space: nowrap;
  cursor: pointer;
}

.btn-primary {
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
}

.btn-primary:hover {
  background: var(--oa-color-primary-hover);
}

.btn-secondary {
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
  border-color: var(--oa-color-hairline-strong);
}

.btn-secondary:hover {
  background: var(--oa-color-canvas-subtle);
}

.btn-ghost {
  height: var(--oa-space-control-compact);
  padding: 0 var(--oa-space-xs);
  background: transparent;
  color: var(--oa-color-primary);
}

/* ---------------- 表格 ---------------- */
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

/* 选中态：primary-subtle 底 + 左侧 2px 指示条 */
.oa-table tbody tr.is-selected td {
  background: var(--oa-color-primary-subtle);
}

.oa-table tbody tr.is-selected td:first-child {
  box-shadow: inset 2px 0 0 var(--oa-color-primary);
}

.oa-table .c {
  text-align: center;
}

.oa-table .r {
  text-align: right;
}

.oa-table .empty {
  height: auto;
  padding: 0;
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
  max-width: 320px;
}

.cell-meta {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.cell-strong {
  color: var(--oa-color-ink);
}

.node-cell {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font: var(--oa-font-body-sm);
}

.node-no {
  font-style: normal;
  color: var(--oa-color-primary);
}

.oa-amount .wan {
  margin-left: 4px;
  font-size: var(--oa-font-size-caption);
  font-weight: 400;
  color: var(--oa-color-ink-subtle);
}

/* ---------------- 分页 ---------------- */
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

.pgbtn:hover:not(:disabled) {
  background: var(--oa-color-canvas-subtle);
}

.pgbtn:disabled {
  color: var(--oa-color-ink-disabled);
  cursor: not-allowed;
}

.pgbtn.is-active {
  background: var(--oa-color-primary-subtle);
  color: var(--oa-color-primary);
  font-weight: 500;
}

/* ---------------- 详情栏 ---------------- */
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
  height: var(--oa-detail-head-h);
  padding: 0 var(--oa-space-lg);
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

.oa-empty {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  align-items: center;
  text-align: center;
}

/* ---------------- 断点 ---------------- */
@media (max-width: 1280px) {
  .oa-center {
    grid-template-columns: var(--oa-panel-list-min) minmax(0, 1fr);
  }
}

/* ≤1024px：列表与详情切换为两级，本页只渲染列表 */
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

/* ≤768px：单列流式；隐藏表格化列，触控目标抬到 44px */
@media (max-width: 768px) {
  .oa-center {
    border: 0;
    border-radius: 0;
  }

  .oa-table th:nth-child(4),
  .oa-table td:nth-child(4),
  .oa-table th:nth-child(6),
  .oa-table td:nth-child(6),
  .oa-table th:nth-child(8),
  .oa-table td:nth-child(8) {
    display: none;
  }

  .control,
  .btn,
  .pgbtn {
    min-height: var(--oa-space-control-h5);
  }

  .title-text {
    max-width: 100%;
    white-space: normal;
  }
}
</style>
