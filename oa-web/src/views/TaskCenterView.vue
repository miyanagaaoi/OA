<script setup lang="ts">
/**
 * oa-web · 审批中心 · 列表页（阶段 2b：接**真实引擎**，替换此前的演示数据）
 * ----------------------------------------------------------------------------
 * 数据源（`flow-task.ts`，全部为 2a 已交付接口）：
 *   · 待我审批 = `GET /api/v1/flow-tasks/todo?page=&size=`（`pending` 且我是处理人）
 *   · 我已审批 = `GET /api/v1/flow-tasks/done`
 *   · 我发起的 = `GET /api/v1/flow-tasks/initiated`
 *   · 抄送我的 = **接口未实现** —— 后端只有上述三个列表；`flow_instance_cc` 只能在**单实例**
 *     维度通过 `GET /flow-instances/{id}/cc` 读出。本页**不伪造**「抄送我的」列表，
 *     如实显示「待实现」并说明替代路径（任务书硬要求 7）。
 *   · 历史库（`/archive`）= **阶段 3**：归档检索接口未交付，本页同样如实标注（不再展示假数据）。
 *
 * 来源：`doc/enums.md` §8（三层状态）、§2（主干 7 节点）；`doc/prd-0.1.md` §6.2（四类单据）；
 *       `DESIGN.md` › Layout（列表栏 / 表格 44px 行高 / 表头粘性 / 分页 32px）
 *
 * 与旧实现的差异（本页是**替换**，不是叠加）：
 *   1. 去掉批量同意 / 批量转办 —— 真实后端**没有**批量端点（`FlowTaskController` 只有逐任务动作），
 *      假入口会让用户以为已批量处理；批量需求属后续工作包。
 *   2. 去掉关键字/类型/状态/日期筛选 —— 三个列表接口**只接受 page/size**（无筛选参数），
 *      前端筛选会变成「只筛当前页」的骗人功能；列表页因此只做分页与跳转。
 *   3. 待办徽标与 Tab 计数取自各自列表的 `total`（服务端分页总数，`long` → JSON 字符串已归一）。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/http'
import { listDoneTasks, listInitiatedTasks, listTodoTasks } from '@/api/flow-task'
import { formatDateTime } from '@/utils/format'
import { FORM_DOC_TYPE_GLYPH, FORM_DOC_TYPE_LABEL, toFormDocType } from '@/utils/form-rules'
import { instancePillStatus, instanceStatusLabel, taskStatusLabel, subStatusLabel, toCount } from '@/utils/flow-task'
import { statusPillClass } from '@/utils/status'
import type { FlowListKind, FlowTaskListItem } from '@/types/flow-task'

const route = useRoute()
const router = useRouter()

const tabs: Array<{ key: FlowListKind; label: string; path: string }> = [
  { key: 'todo', label: '待我审批', path: '/task/pending' },
  { key: 'done', label: '我已审批', path: '/task/approved' },
  { key: 'initiated', label: '我发起的', path: '/task/initiated' },
  { key: 'cc', label: '抄送我的', path: '/task/cc' },
]

const activeTab = computed<FlowListKind>(() => (route.meta.tab as FlowListKind | undefined) ?? 'todo')
const isArchive = computed(() => route.meta.archiveOnly === true)
/** 抄送列表接口是否可用（后端未实现 → 本页如实标注，不发请求） */
const ccImplemented = false

const items = ref<FlowTaskListItem[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const counts = reactive<Record<FlowListKind, number>>({ todo: 0, done: 0, initiated: 0, cc: 0 })
const page = ref(1)
const pageSize = ref(20)

const activeTabLabel = computed(() => tabs.find((tab) => tab.key === activeTab.value)?.label ?? '待我审批')
const totalPages = computed(() => (total.value > 0 ? Math.ceil(total.value / pageSize.value) : 1))

onMounted(async () => {
  await Promise.all([loadList(), loadCounts()])
})

watch(
  () => route.fullPath,
  async () => {
    page.value = 1
    errorText.value = ''
    await Promise.all([loadList(), loadCounts()])
  },
)

async function loadList(): Promise<void> {
  if (isArchive.value || (!ccImplemented && activeTab.value === 'cc')) {
    items.value = []
    total.value = 0
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const result = await fetchByKind(activeTab.value, page.value, pageSize.value)
    items.value = result.items
    total.value = result.total
  } catch (error) {
    items.value = []
    total.value = 0
    errorText.value = error instanceof ApiError ? `${error.message}${error.code ? `（业务码 ${error.code}）` : ''}` : '列表加载失败'
  } finally {
    loading.value = false
  }
}

/** 四个 Tab 的计数（抄送跳过请求） */
async function loadCounts(): Promise<void> {
  const kinds: FlowListKind[] = ['todo', 'done', 'initiated']
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

function fetchByKind(kind: FlowListKind, targetPage: number, size: number) {
  switch (kind) {
    case 'done':
      return listDoneTasks(targetPage, size)
    case 'initiated':
      return listInitiatedTasks(targetPage, size)
    case 'todo':
    default:
      return listTodoTasks(targetPage, size)
  }
}

function goto(path: string): void {
  void router.push(path)
}

function openDetail(item: FlowTaskListItem): void {
  void router.push({ name: 'task-detail', params: { id: item.instanceId } })
}

function formTypeLabelOf(item: FlowTaskListItem): string {
  const type = toFormDocType(item.formType)
  return type ? FORM_DOC_TYPE_LABEL[type] : item.formType || '未知类型'
}

function formTypeGlyphOf(item: FlowTaskListItem): string {
  const type = toFormDocType(item.formType)
  return type ? FORM_DOC_TYPE_GLYPH[type] : '单'
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
function canAct(item: FlowTaskListItem): boolean {
  return activeTab.value === 'todo' && item.taskId !== '' && item.taskStatus === 'pending'
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
          <i v-if="tab.key !== 'cc'" class="oa-tnum">{{ counts[tab.key] }}</i>
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

      <!-- 抄送我的：后端未实现列表接口 -->
      <div v-else-if="!ccImplemented && activeTab === 'cc'" class="notice">
        <p class="notice-title">「抄送我的」列表待实现</p>
        <p>
          后端当前只有 `todo` / `done` / `initiated` 三个列表（`FlowTaskController`），
          **没有**「抄送我的」查询端点；被抄送人只能从**单据详情页**看该单的抄送清单
          （`GET /flow-instances/{id}/cc`）。本页如实标注，不伪造列表。
        </p>
        <button class="btn btn-secondary" type="button" @click="goto('/task/initiated')">去「我发起的」</button>
      </div>

      <template v-else>
        <!-- 能力缺失说明（不是入口消失）：让使用者知道是后端能力未到，而不是界面坏了 -->
        <p class="capability-note">
          <b>能力说明</b>：高级筛选（单号 / 标题 / 发起人 / 单据类型 / 状态 / 日期区间）与
          批量同意、批量转办**待后端支持** —— 当前三个列表接口只接受 `page` / `size`，
          也没有批量动作端点；为避免「只筛当前页」或「点了没生效」的假功能，这些入口先不渲染，
          后端补齐后会同步放开（已列入待办）。
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
                <th style="width: 130px">发起人</th>
                <th style="width: 120px">我的任务</th>
                <th style="width: 110px">单据状态</th>
                <th style="width: 150px">时间</th>
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
                    <p>当前列表没有单据。</p>
                    <p class="oa-text-caption oa-text-subtle">
                      待办为空说明没有分配给你的待处理任务；「我发起的」只列出你本人发起的单据（数据域过滤由服务端执行）。
                    </p>
                  </div>
                </td>
              </tr>
              <tr v-for="item in items" v-else :key="`${item.instanceId}-${item.taskId}`" @click="openDetail(item)">
                <td class="oa-mono">{{ item.bizNo || item.instanceId }}</td>
                <td>
                  <div class="cell-title">
                    <span class="type-ico" aria-hidden="true">{{ formTypeGlyphOf(item) }}</span>
                    <span class="title-text">{{ formTypeLabelOf(item) }}</span>
                    <span v-if="item.addSignType" class="oa-tag is-info">
                      {{ item.addSignType === 'pre' ? '前加签' : '后加签' }}
                    </span>
                  </div>
                  <div class="cell-meta">
                    节点{{ item.currentNodeSeq ?? item.nodeSeq ?? '—' }} · {{ item.nodeName || '—' }}
                    <template v-if="subStatusLabel(item.subStatus)"> · {{ subStatusLabel(item.subStatus) }}</template>
                  </div>
                </td>
                <td>{{ item.initiatorName || '—' }}</td>
                <td>
                  <span v-if="item.taskStatus" class="oa-pill" :class="item.taskStatus === 'pending' ? 'is-pending' : 'is-closed'">
                    {{ taskStatusLabel(item.taskStatus, item.taskStatusLabel) }}
                  </span>
                  <span v-else class="cell-meta">—</span>
                </td>
                <td>
                  <span class="oa-pill" :class="statusPillClass(instancePillStatus(item.instanceStatus) as never)">
                    {{ instanceStatusLabel(item.instanceStatus) }}
                  </span>
                </td>
                <td class="oa-mono">{{ formatDateTime(item.decidedAt ?? item.taskCreatedAt) }}</td>
                <td class="c">
                  <button class="btn btn-ghost" type="button" @click.stop="openDetail(item)">
                    {{ canAct(item) ? '去审批' : '查看' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="table-foot">
          <span>共 <b class="oa-tnum">{{ total }}</b> 条</span>
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
            列表数据来自真实引擎（`/flow-tasks/todo|done|initiated`）；详情页的审批动作可用性
            由服务端的动作面清单（`GET /flow-actions`）+ 实例状态 + 节点开关共同判定，
            不可用时**直接说明原因**。
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

.btn-secondary {
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
