<script setup lang="ts">
/**
 * oa-web · 权限变更日志（只读）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 6.9 REQ-LOG-004（**权限变更日志**：角色、数据域、权限树勾选、
 *     流程模板发布的所有变更，**含变更前后值**）、REQ-LOG-006（日志只允许追加，禁止修改删除）、
 *     AC-59（为角色勾选权限树节点与数据域 → 变更前后值可在权限变更日志中查到）、
 *     AC-61（管理员边界：删除单据/日志一律被拒绝且留痕）
 *   · `doc/data-model.md` 3.1–3.5（变更对象：角色 / 角色权限 / 用户角色分配 / 数据域 / 类别）
 *   · `DESIGN.md` › Data Display › table（行高、操作列、只读页无编辑入口）
 *
 * 只读硬约束：本页**没有任何写入口**——审计日志只允许追加（REQ-LOG-006），
 * 因此不渲染编辑/删除，只提供分页、过滤与「变更前后值」对照查看。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchChangeLogs } from '@/api/authz'
import { canOpenAuthzLogAdmin } from '@/utils/admin'
import { useUserStore } from '@/stores/user'
import { AUTHZ_ACTION_LABEL, CHANGE_TARGET_LABEL, authzActionLabel } from '@/utils/authz'
import type { AuthzChangeLog, ChangeTargetType } from '@/types/authz'

const userStore = useUserStore()

/** 日志查看入口可见性（不可见优于不可用；服务端才是裁决方） */
const canView = computed(() => canOpenAuthzLogAdmin(userStore))

const filters = reactive({
  keyword: '',
  targetType: '' as ChangeTargetType | '',
  action: '',
  dateRange: [] as string[],
})

const list = ref<AuthzChangeLog[]>([])
const loading = ref(false)
const errorMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

/** 变更对象类型候选项（五类 + 组织节点范围） */
const targetTypeOptions = (Object.keys(CHANGE_TARGET_LABEL) as ChangeTargetType[]).map((code) => ({
  value: code,
  label: CHANGE_TARGET_LABEL[code],
}))

/** 动作候选项：以后端动作字典为准，前端只做已知动作的下拉（未知动作仍可按关键字搜索） */
const actionOptions = Object.keys(AUTHZ_ACTION_LABEL).map((code) => ({
  value: code,
  label: AUTHZ_ACTION_LABEL[code],
}))

const emptyFilter = computed(
  () =>
    !filters.keyword.trim() &&
    !filters.targetType &&
    !filters.action &&
    filters.dateRange.length === 0,
)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    // 日期区间：清空时 Element Plus 会把值置为 null，这里统一收敛成空数组
    const range: string[] = filters.dateRange ?? []
    const result = await fetchChangeLogs({
      keyword: filters.keyword.trim() || undefined,
      targetType: filters.targetType || undefined,
      action: filters.action || undefined,
      dateFrom: range[0] || undefined,
      // 日期区间取「当天结束」，否则当日 00:00 之后的变更会被漏掉
      dateTo: range[1] ? `${range[1]}T23:59:59` : undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    list.value = result.list
    total.value = result.total
  } catch (error) {
    list.value = []
    total.value = 0
    errorMessage.value = (error as Error).message || '权限变更日志加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function resetFilters(): void {
  filters.keyword = ''
  filters.targetType = ''
  filters.action = ''
  filters.dateRange = []
  page.value = 1
  void load()
}
function onPageChange(next: number): void {
  page.value = next
  void load()
}

function onSizeChange(size: number): void {
  pageSize.value = size
  page.value = 1
  void load()
}

onMounted(() => {
  void load()
})

// ---------------------------------------------------------------------------
// 展示辅助
// ---------------------------------------------------------------------------
function asLog(row: unknown): AuthzChangeLog {
  return row as AuthzChangeLog
}

/** 时间：服务端给 ISO 字符串，这里只做「T → 空格、去掉毫秒」的可读化（不引入时区推断） */
function timeText(value: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '')
}

function actionText(row: AuthzChangeLog | null): string {
  if (!row) return '—'
  return authzActionLabel(row.action, row.actionLabel)
}

function targetText(row: AuthzChangeLog | null): string {
  if (!row) return '—'
  return row.targetLabel || row.targetId || '—'
}

function targetTypeText(row: AuthzChangeLog | null): string {
  if (!row) return '—'
  if (row.targetTypeLabel) return row.targetTypeLabel
  return targetTypeOptions.find((item) => item.value === row.targetType)?.label ?? row.targetType
}

/** 「变更前后值」对照明细：用弹窗展示完整文本（表格里只放摘要，避免撑破行高） */
const detail = reactive({
  visible: false,
  row: null as AuthzChangeLog | null,
})

function openDetail(row: AuthzChangeLog): void {
  detail.row = row
  detail.visible = true
}

/** 差异摘要（服务端下发了 added/removed 时直接展示条数） */
function diffText(row: AuthzChangeLog): string {
  if (!row.added.length && !row.removed.length) return ''
  return `+${row.added.length} / −${row.removed.length}`
}

// 明细弹窗的取值统一走 computed：模板里不做空值收窄，避免 `detail.row` 为 null 时的取值假设
const detailBefore = computed(() => detail.row?.before ?? '')
const detailAfter = computed(() => detail.row?.after ?? '')
const detailAdded = computed<string[]>(() => detail.row?.added ?? [])
const detailRemoved = computed<string[]>(() => detail.row?.removed ?? [])
</script>

<template>
  <div class="oa-log-page">
    <header class="page-head">
      <h1 class="oa-text-title-page">权限变更日志</h1>
      <span class="oa-text-caption oa-text-subtle">
        角色、数据域、权限树勾选与角色分配的变更留痕（含变更前后值，REQ-LOG-004 / AC-59）；
        审计日志只允许追加，本页只读（REQ-LOG-006）
      </span>
    </header>

    <el-alert
      v-if="!canView"
      type="info"
      :closable="false"
      show-icon
      title="当前账号没有权限变更日志的查看权限"
      description="入口本就不渲染（不可见优于不可用）；服务端才是裁决方——绕过界面直接请求会被 403 拒绝。"
    />

    <!-- 筛选条：关键字 + 对象类型 + 动作 + 时间区间 -->
    <div class="oa-card filter-bar">
      <el-input
        v-model="filters.keyword"
        class="kw"
        clearable
        placeholder="关键字：变更人 / 角色名 / 对象"
        @keyup.enter="search"
      />
      <el-select v-model="filters.targetType" class="type-filter" clearable placeholder="变更对象">
        <el-option v-for="item in targetTypeOptions" :key="item.value" :value="item.value" :label="item.label" />
      </el-select>
      <el-select v-model="filters.action" class="action-filter" clearable filterable placeholder="变更动作">
        <el-option v-for="item in actionOptions" :key="item.value" :value="item.value" :label="item.label" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        class="date-filter"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        unlink-panels
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button :disabled="emptyFilter" @click="resetFilters">重置</el-button>
      <span class="oa-text-caption oa-text-subtle count">
        共 <b class="oa-tnum">{{ total }}</b> 条变更
      </span>
    </div>

    <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />

    <el-table v-loading="loading" :data="list" border size="default">
      <el-table-column label="时间" width="170" fixed="left">
        <template #default="{ row }">
          <span class="oa-tnum">{{ timeText(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更人" width="150">
        <template #default="{ row }">
          <span class="oa-text-body-sm">{{ row.operatorName || '—' }}</span>
          <span v-if="row.account" class="oa-text-caption oa-text-subtle sub-line">{{ row.account }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更动作" min-width="150">
        <template #default="{ row }">
          <span class="oa-text-body-sm">{{ actionText(asLog(row)) }}</span>
          <span class="oa-mono oa-text-caption sub-line">{{ row.action }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更对象" min-width="200">
        <template #default="{ row }">
          <span class="oa-tag is-info">{{ targetTypeText(asLog(row)) }}</span>
          <span class="oa-text-body-sm obj-text">{{ targetText(asLog(row)) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更前后值" min-width="260">
        <template #default="{ row }">
          <p class="diff-line">
            <span class="lbl">前</span>
            <span class="val">{{ row.before || '—' }}</span>
          </p>
          <p class="diff-line">
            <span class="lbl">后</span>
            <span class="val">{{ row.after || '—' }}</span>
          </p>
          <p v-if="diffText(asLog(row))" class="oa-text-caption oa-text-subtle">
            差异：<span class="is-add">+{{ row.added.length }}</span> /
            <span class="is-remove">−{{ row.removed.length }}</span>
            <el-button link type="primary" @click="openDetail(asLog(row))">查看明细</el-button>
          </p>
          <el-button v-else link type="primary" @click="openDetail(asLog(row))">查看明细</el-button>
        </template>
      </el-table-column>
      <el-table-column label="变更原因" min-width="150">
        <template #default="{ row }">
          <span class="oa-text-caption">{{ row.reason || '—' }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <div class="oa-empty">
          <p>没有符合条件的权限变更记录</p>
        </div>
      </template>
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />
  </div>

  <!-- ==================== 变更明细（只读） ==================== -->
  <el-dialog v-model="detail.visible" title="权限变更明细（只读）" width="720px" append-to-body>
    <template v-if="detail.row">
      <div class="detail-meta">
        <span>时间：<b class="oa-tnum">{{ timeText(detail.row?.createdAt ?? '') }}</b></span>
        <span>变更人：<b>{{ detail.row?.operatorName || '—' }}</b></span>
        <span>动作：<b>{{ actionText(detail.row) }}</b></span>
        <span>对象：<b>{{ targetText(detail.row) }}</b></span>
        <span v-if="detail.row?.traceId">追踪号：<b class="oa-mono">{{ detail.row.traceId }}</b></span>
      </div>

      <div class="oa-section-band">变更前</div>
      <pre class="value-block before">{{ detailBefore || '（无）' }}</pre>

      <div class="oa-section-band">变更后</div>
      <pre class="value-block after">{{ detailAfter || '（无）' }}</pre>

      <template v-if="detailAdded.length || detailRemoved.length">
        <div class="oa-section-band">差异清单</div>
        <p v-if="detailAdded.length" class="list-line">
          <span class="is-add">新增（{{ detailAdded.length }}）</span>
          {{ detailAdded.join('、') }}
        </p>
        <p v-if="detailRemoved.length" class="list-line">
          <span class="is-remove">移除（{{ detailRemoved.length }}）</span>
          {{ detailRemoved.join('、') }}
        </p>
      </template>

      <p class="oa-text-caption oa-text-subtle">
        审计日志只允许追加，禁止修改与删除（REQ-LOG-006 / AC-61）。
      </p>
    </template>
    <template #footer>
      <el-button @click="detail.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.oa-log-page {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-sm);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-sm) var(--oa-space-md);
}

.kw {
  width: 220px;
}

.type-filter {
  width: 150px;
}

.action-filter {
  width: 180px;
}

.date-filter {
  width: 260px;
}

.count {
  margin-left: auto;
}

.sub-line {
  display: block;
}

.obj-text {
  margin-left: var(--oa-space-xs);
}

.diff-line {
  display: flex;
  gap: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.diff-line .lbl {
  flex: none;
  color: var(--oa-color-ink-subtle);
}

.diff-line .val {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.is-add {
  color: var(--oa-color-success);
  font-weight: 500;
}

.is-remove {
  color: var(--oa-color-error);
  font-weight: 500;
}

.pager {
  justify-content: flex-end;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-sm);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.value-block {
  margin: 0;
  padding: var(--oa-space-sm);
  max-height: 180px;
  overflow: auto;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-mono);
  color: var(--oa-color-ink);
  white-space: pre-wrap;
  word-break: break-all;
}

.value-block.after {
  border-color: var(--oa-color-primary-border);
  background: var(--oa-color-primary-subtle);
}

.list-line {
  margin: var(--oa-space-xxs) 0;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
  word-break: break-all;
}

@media (max-width: 768px) {
  .kw,
  .type-filter,
  .action-filter,
  .date-filter {
    width: 100%;
  }
}
</style>
