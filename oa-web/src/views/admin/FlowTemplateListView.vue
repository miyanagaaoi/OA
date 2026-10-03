<script setup lang="ts">
/**
 * oa-web · 流程模板列表（2a.2 `GET /api/v1/flow-templates`）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/templates.md` §1.0（四类单据各自独立模板，`flow_template.code` = 单据类型码）、
 *     §3.3（模板状态机 draft / published / archived）、§4.1（六步变更流程：新增版本 → 发布）
 *   · `doc/prd-0.1.md` §6.4 REQ-FLOW-006 / AC-09（在途实例按发起时版本运行）、§7
 *   · `doc/enums.md` §10.2（单据类型码）
 *
 * 列表口径：模板总量很小（四类单据 × 少量版本），**服务端过滤**（code / formType / status
 * 都是后端 `@RequestParam`），不做本地过滤，避免与后端判据漂移。
 *
 * 三条界面纪律：
 *   1. **权限不可见优于不可用**：读入口按 `admin:flow:template`，写入口
 *      （开新草稿 / 发布 / 归档）按 `admin:flow:publish`——无权限**不渲染**按钮
 *      （服务端仍是裁决方，越权请求 403 由 `reportApiErrorWithCode` 如实展示）；
 *   2. **错误码如实呈现**：40008（配置非法）/ 40906（已发布只读）/ 40907（草稿已存在）/
 *      40001（参数校验）都会带业务码弹出，不吞；
 *   3. **在途锁版本提示**：归档/开新草稿的确认文案里写明「在途实例仍按发起时版本运行」。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import { useUserStore } from '@/stores/user'
import { canPublishFlowTemplate, canReadFlowTemplate } from '@/utils/admin'
import {
  archiveFlowTemplate,
  createFlowTemplateVersion,
  listFlowTemplates,
  publishFlowTemplate,
} from '@/api/flow'
import { reportApiErrorWithCode } from '@/utils/feedback'
import {
  FLOW_FORM_TYPE_LABEL,
  FLOW_FORM_TYPE_OPTIONS,
  FLOW_STATUS_LABEL,
  FLOW_STATUS_OPTIONS,
  describeGatePolicy,
  formatFlowTime,
} from '@/utils/flow'
import type { FlowFormType, FlowTemplate, FlowTemplateStatus } from '@/types/flow'

const router = useRouter()
const userStore = useUserStore()

/** 读入口：`admin:flow:template`（页面守卫已同口径，这里决定页内只读提示与操作列） */
const canRead = computed(() => canReadFlowTemplate(userStore))
/** 写入口：`admin:flow:publish`（开新草稿 / 发布 / 归档） */
const canPublish = computed(() => canPublishFlowTemplate(userStore))

const filters = reactive({
  code: '',
  formType: '' as FlowFormType | '',
  status: '' as FlowTemplateStatus | '',
})

const list = ref<FlowTemplate[]>([])
const loading = ref(false)
const loadError = ref('')

const actionLoading = reactive<Record<string, boolean>>({})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    list.value = await listFlowTemplates({
      code: filters.code.trim() || undefined,
      formType: filters.formType || undefined,
      status: filters.status || undefined,
    })
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '模板列表加载失败'
    list.value = []
    reportApiErrorWithCode(error, '加载流程模板列表')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

function resetFilters(): void {
  filters.code = ''
  filters.formType = ''
  filters.status = ''
  void load()
}

const draftCount = computed(() => list.value.filter((item) => item.status === 'draft').length)

function openDesigner(row: FlowTemplate): void {
  void router.push(`/admin/flow/template/${row.templateId}`)
}

/** el-table 作用域插槽的 row 是 any 形状；进出业务函数前统一收窄（与 RoleListView 同口径） */
function asTemplate(row: unknown): FlowTemplate {
  return row as FlowTemplate
}

/** 开新草稿：`POST /flow-templates/{id}/versions`（已有草稿 → 40907；基于草稿开版本 → 40008） */
async function newVersion(row: FlowTemplate): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `基于「${row.name}」v${row.version} 开新草稿（v${row.version + 1}）？` +
        '新草稿不会影响任何在途单据（在途实例按发起时锁定的版本执行，REQ-FLOW-006 / AC-09）；' +
        '同一单据类型同时只能存在一个草稿，否则服务端返回 40907。',
      '开新草稿版本',
      { confirmButtonText: '开新草稿', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actionLoading[row.templateId] = true
  try {
    const draft = await createFlowTemplateVersion(row.templateId, { fromVersion: row.version })
    ElMessage({ type: 'success', message: `已创建草稿 v${draft.version}，即将打开设计器` })
    await load()
    openDesigner(draft)
  } catch (error) {
    reportApiErrorWithCode(error, '开新草稿版本')
  } finally {
    actionLoading[row.templateId] = false
  }
}

/** 发布：`POST /flow-templates/{id}/publish`（服务端同事务复跑发布前校验，不通过即 40008） */
async function publish(row: FlowTemplate): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `发布「${row.name}」v${row.version}？发布后该版本转为只读，同单据类型的原已发布版本自动归档；` +
        '**在途实例仍按发起时版本执行，不受影响**（REQ-FLOW-006 / AC-09）。' +
        '服务端会在同一事务内重新执行发布前校验，未通过将返回 40008。',
      '确认发布',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  actionLoading[row.templateId] = true
  try {
    const published = await publishFlowTemplate(row.templateId, '流程模板列表页发布')
    ElMessage({ type: 'success', message: `v${published.version} 已发布（原已发布版本转归档）` })
    await load()
  } catch (error) {
    reportApiErrorWithCode(error, '发布流程模板')
  } finally {
    actionLoading[row.templateId] = false
  }
}

/**
 * 归档：`POST /flow-templates/{id}/archive`（只阻止**新**实例使用，在途继续执行 V-05）。
 *
 * <p>⚠ curl 实测：该接口**不做** `assertEditable`——对 `published` 版本归档会**直接成功（200）**，
 * 而其余写接口都会回 40906；若该 `code` 下没有别的 `published` 版本，归档后**新单据将无法发起**
 * 且**无法撤销**（归档态不可再编辑/发布）。因此这里对「唯一可用版本」额外给出严重提示。
 */
async function archive(row: FlowTemplate): Promise<void> {
  const otherPublished = list.value.filter(
    (item) => item.code === row.code && item.status === 'published' && item.templateId !== row.templateId,
  ).length
  const draft = list.value.find((item) => item.code === row.code && item.status === 'draft')
  const risk =
    row.status === 'published' && otherPublished === 0
      ? `\n\n⚠ 严重：这是单据类型「${row.code}」当前**唯一**的已发布版本，归档后**新单据将无法发起**` +
        (draft ? `；请先发布草稿 v${draft.version}。` : '；请先「开新草稿」并发布一个新版本。')
      : ''
  try {
    await ElMessageBox.confirm(
      `归档「${row.name}」v${row.version}？归档后不再有**新**单据使用该版本，但在途实例继续按原版本执行` +
        '（templates.md V-05）。注意：归档接口**不会**返回 40906（服务端未做「只读」检查），而是直接生效，' +
        '且归档态无法再编辑或发布。' +
        risk,
      '确认归档',
      { confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  actionLoading[row.templateId] = true
  try {
    await archiveFlowTemplate(row.templateId, '流程模板列表页归档')
    ElMessage({ type: 'success', message: '已归档（在途实例继续执行）' })
    await load()
  } catch (error) {
    reportApiErrorWithCode(error, '归档流程模板')
  } finally {
    actionLoading[row.templateId] = false
  }
}
</script>

<template>
  <div class="oa-flow-list">
    <header class="page-head">
      <h1 class="oa-text-title-page">流程模板</h1>
      <span class="oa-text-caption oa-text-subtle">
        四类单据各自独立模板（doc/templates.md §1.0），主干固定 7 个审批节点；
        <b class="oa-tnum">{{ draftCount }}</b> 个草稿版本
      </span>
      <div class="head-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="!canRead"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号没有 admin:flow:template 权限，流程模板入口不渲染（服务端同样会 403 拒绝）"
    />

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="模板按 (code, version) 累积、不覆盖历史；在途实例按发起时版本运行（REQ-FLOW-006 / AC-09）"
      description="改动已发布版本必须「开新草稿 → 改配置 → 发布」：发布后同单据类型的原已发布版本自动转归档（archived），但**在途实例的剩余节点仍按发起时锁定的版本执行**（templates.md V-01 / V-02 / V-05）。归档只阻止新实例使用。"
    />

    <!-- 筛选条：三个条件都是后端 @RequestParam（服务端过滤，不做本地过滤） -->
    <div class="oa-card filter-bar">
      <el-input v-model="filters.code" class="kw" clearable placeholder="单据类型码 code（matter / fund / contract / seal）" />
      <el-select v-model="filters.formType" class="sel" clearable placeholder="单据类型 formType">
        <el-option
          v-for="item in FLOW_FORM_TYPE_OPTIONS"
          :key="item"
          :value="item"
          :label="`${FLOW_FORM_TYPE_LABEL[item]}（${item}）`"
        />
      </el-select>
      <el-select v-model="filters.status" class="sel" clearable placeholder="状态 status">
        <el-option
          v-for="item in FLOW_STATUS_OPTIONS"
          :key="item"
          :value="item"
          :label="`${FLOW_STATUS_LABEL[item]}（${item}）`"
        />
      </el-select>
      <el-button @click="load">查询</el-button>
      <el-button :disabled="!filters.code && !filters.formType && !filters.status" @click="resetFilters">重置</el-button>
      <span class="oa-text-caption oa-text-subtle count">
        共 <b class="oa-tnum">{{ list.length }}</b> 条
      </span>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <el-table v-loading="loading" :data="list" border size="default">
      <el-table-column label="单据类型码" width="150" fixed="left">
        <template #default="{ row }">
          <span class="oa-mono">{{ asTemplate(row).code }}</span>
        </template>
      </el-table-column>
      <el-table-column label="模板名称" min-width="180">
        <template #default="{ row }">
          <b class="oa-text-body-sm">{{ asTemplate(row).name }}</b>
          <span class="oa-text-caption oa-text-subtle sub-line">
            {{ FLOW_FORM_TYPE_LABEL[asTemplate(row).formType] }}（form_type =
            <span class="oa-mono">{{ asTemplate(row).formType }}</span>）
          </span>
        </template>
      </el-table-column>
      <el-table-column label="当前版本" width="90" align="right">
        <template #default="{ row }">
          <span class="oa-tnum">v{{ asTemplate(row).version }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <StatusPill kind="flow-status" :status="asTemplate(row).status" />
          <span v-if="asTemplate(row).usableByNewInstance" class="oa-text-caption oa-text-subtle sub-line">新单可用</span>
          <span v-else class="oa-text-caption oa-text-subtle sub-line">新单不可用</span>
        </template>
      </el-table-column>
      <el-table-column label="节点数" width="80" align="right">
        <template #default="{ row }">
          <span class="oa-tnum">{{ asTemplate(row).nodeCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="闸门配置（Q6/Q7）" min-width="230">
        <template #default="{ row }">
          <span class="oa-text-body-sm">{{ describeGatePolicy(asTemplate(row).gatePolicy) }}</span>
          <span v-if="asTemplate(row).gatePolicy.v04Default" class="oa-text-caption oa-text-subtle sub-line">
            = V0.4 定稿默认值（默认行为不变）
          </span>
          <span v-else-if="asTemplate(row).gatePolicy.unlimited" class="oa-text-caption oa-text-subtle sub-line">
            次数不限 / 补件不设时限
          </span>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="160">
        <template #default="{ row }">
          <span class="oa-text-caption oa-tnum">{{ formatFlowTime(asTemplate(row).publishedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDesigner(asTemplate(row))">查看 / 设计</el-button>
          <el-button
            v-if="canPublish && asTemplate(row).status !== 'archived'"
            link
            type="primary"
            :loading="actionLoading[asTemplate(row).templateId]"
            @click="newVersion(asTemplate(row))"
          >
            开新草稿
          </el-button>
          <el-button
            v-if="canPublish && asTemplate(row).status === 'draft'"
            link
            type="primary"
            :loading="actionLoading[asTemplate(row).templateId]"
            @click="publish(asTemplate(row))"
          >
            发布
          </el-button>
          <el-button
            v-if="canPublish && asTemplate(row).status !== 'archived'"
            link
            type="warning"
            :loading="actionLoading[asTemplate(row).templateId]"
            @click="archive(asTemplate(row))"
          >
            归档
          </el-button>
          <span v-if="!canPublish" class="oa-text-caption oa-text-subtle">只读（无 admin:flow:publish）</span>
        </template>
      </el-table-column>
      <template #empty>
        <div class="oa-empty">
          <p>没有符合条件的流程模板</p>
        </div>
      </template>
    </el-table>

    <p class="oa-text-caption oa-text-subtle">
      <b>每行是一个版本</b>（`GET /flow-templates` 按 `(code, version)` 返回全部版本，不覆盖历史）。
      状态机（doc/templates.md §3.3）：<b>draft</b> 唯一可编辑态、不可被新实例使用；
      <b>published</b> 只读、可被新实例使用（同一 code 下最多一个）；
      <b>archived</b> 只读、不可被新实例使用（**在途实例继续执行**）。
      已发布 / 已归档版本的写入会被服务端以 40906 拒绝，界面不提供可点的编辑入口；
      **归档是唯一例外**：服务端未做只读检查，对已发布版本归档会直接成功（200）且不可撤销。
    </p>
  </div>
</template>

<style scoped>
.oa-flow-list {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-sm);
}

.page-head h1 {
  color: var(--oa-color-ink);
}

.head-actions {
  margin-left: auto;
  display: flex;
  gap: var(--oa-space-xs);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-sm) var(--oa-space-md);
}

.kw {
  width: 280px;
}

.sel {
  width: 200px;
}

.count {
  margin-left: auto;
}

.count b {
  font-weight: 500;
  color: var(--oa-color-ink);
}

.sub-line {
  display: block;
}

@media (max-width: 768px) {
  .kw,
  .sel {
    width: 100%;
  }
}
</style>
