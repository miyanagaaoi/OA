<script setup lang="ts">
/**
 * oa-web · 组织负责人面板（正职 / 副职 + 集团层业务线绑定）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.1（每个部门/科室可指定一个或多个负责人，支持一人多岗；
 *     集团层指定「分管领导」，按业务线绑定）、5.4（审批人解析取正职）
 *   · `doc/import-spec.md` §3.4 / §4.4（正职唯一性 E-LEAD-004、业务线仅集团层 E-LEAD-008、
 *     负责人不得离职 E-LEAD-006）、§7.1 场景 5（负责人变更 → 影响清单）
 *   · `doc/prd-0.1.md` AC-11：**未设正职 → 该部门成员发起审批被拒绝**
 *     （`W-ORG-014`：部门/科室未设正职负责人 → 审批人候选为空）
 *
 * 交互口径：
 *   · 正职与副职分区展示；「未设正职」用警示条常驻（不是 toast，必须能一直看见）。
 *   · 升正职 / 移除负责人都是危险操作：二次确认 + 原因必填；有在途单据时默认阻断。
 *   · 业务线绑定只在 `org_type=集团` 时出现（其余节点填写即违反 E-LEAD-008）。
 *   · 后端字段与前端领域模型的差异（leaderId→id、businessLine→category 等）
 *     由 `api/org.ts` 的映射层吸收，本组件只使用领域模型。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImpactConfirmDialog from '@/components/ImpactConfirmDialog.vue'
import StatusPill from '@/components/StatusPill.vue'
import {
  checkOrgInFlight,
  createOrgLeader,
  deleteOrgLeader,
  fetchLeaderCandidates,
  fetchLeaderLines,
  fetchOrgLeaders,
  updateLeaderLine,
  updateOrgLeader,
} from '@/api/org'
import { useOrgStore } from '@/stores/org'
import { useUserStore } from '@/stores/user'
import { canForceChange, canManageOrg } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import { BUSINESS_LINE_LABEL, BUSINESS_LINE_ORDER, LEADER_TYPE_LABEL } from '@/utils/identity-rules'
import type {
  BusinessLine,
  ImpactItem,
  InFlightCheck,
  LeaderCandidate,
  LeaderLineBinding,
  LeaderType,
  OrgLeader,
  OrgTreeNode,
} from '@/types/identity'

const props = defineProps<{ org: OrgTreeNode }>()
const emit = defineEmits<{
  (e: 'changed'): void
  /** 把「该节点是否有正职」上报给父级，用于节点详情与树上的 AC-11 警示（权威来源） */
  (e: 'primary-state', payload: { orgId: string; hasPrimary: boolean }): void
}>()

const userStore = useUserStore()
const orgStore = useOrgStore()

const editable = computed(() => canManageOrg(userStore))
const canForce = computed(() => canForceChange(userStore))

const leaders = ref<OrgLeader[]>([])
const loading = ref(false)
const errorMessage = ref('')

/** 集团层业务线绑定（五值：经营/经济/行政/人力/投资） */
const lines = ref<LeaderLineBinding[]>([])
const linesLoading = ref(false)

const candidates = ref<LeaderCandidate[]>([])
const candidateLoading = ref(false)

const primaries = computed(() => leaders.value.filter((item) => item.leaderType === 'primary'))
const deputies = computed(() => leaders.value.filter((item) => item.leaderType === 'deputy'))

/** el-table 作用域插槽的 row 是 DefaultRow；进出业务函数前统一收窄（不用 any） */
function asLeader(row: unknown): OrgLeader {
  return row as OrgLeader
}

/** AC-11 / W-ORG-014：部门、科室必须有正职，否则审批人候选为空 → 发起被拦截 */
const missingPrimary = computed(
  () => (props.org.orgType === 'dept' || props.org.orgType === 'section') && primaries.value.length === 0,
)

const isGroup = computed(() => props.org.orgType === 'group')

/** 集团层：还没绑定正职分管领导的业务线（AC-11 同源风险） */
const linesWithoutPrimary = computed(() =>
  lines.value.filter((item) => !item.leaderId || (item.leaderType && item.leaderType !== 'primary')),
)

// ---------------------------------------------------------------------------
// 加载
// ---------------------------------------------------------------------------
async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    leaders.value = await fetchOrgLeaders(props.org.id)
    // AC-11 的权威判定：只有拿到负责人列表，才能确认该部门/科室是否真的没有正职
    emit('primary-state', { orgId: props.org.id, hasPrimary: primaries.value.length > 0 })
  } catch (error) {
    leaders.value = []
    errorMessage.value = (error as Error).message || '负责人加载失败'
  } finally {
    loading.value = false
  }
}

async function loadLines(): Promise<void> {
  if (!isGroup.value) {
    lines.value = []
    return
  }
  linesLoading.value = true
  try {
    lines.value = await fetchLeaderLines()
  } catch {
    lines.value = []
  } finally {
    linesLoading.value = false
  }
}

/**
 * 候选人：`GET /orgs/{id}/leader-candidates?keyword=` —— `keyword` 为**服务端过滤**
 * （姓名 / 账号 / 工号），下拉框用 remote 触发，避免一次拉全量后本地过滤。
 */
async function loadCandidates(orgId: string, keyword = ''): Promise<void> {
  if (!orgId) {
    candidates.value = []
    return
  }
  candidateLoading.value = true
  try {
    candidates.value = await fetchLeaderCandidates(orgId, keyword)
  } catch {
    candidates.value = []
  } finally {
    candidateLoading.value = false
  }
}

/** el-select remote-method：服务端关键字过滤（空串回全量） */
async function searchCandidates(keyword: string): Promise<void> {
  await loadCandidates(addDialog.value.visible ? props.org.id : lineDialog.value.orgId, keyword)
}

// ---------------------------------------------------------------------------
// 新增负责人
// ---------------------------------------------------------------------------
const addDialog = ref({
  visible: false,
  userId: '',
  leaderType: 'deputy' as LeaderType,
  sortNo: 0,
  businessLine: '' as BusinessLine | '',
  remark: '',
  submitting: false,
})

async function openAdd(): Promise<void> {
  addDialog.value = {
    visible: true,
    userId: '',
    leaderType: 'deputy',
    sortNo: 0,
    businessLine: '',
    remark: '',
    submitting: false,
  }
  await loadCandidates(props.org.id)
}

/** E-LEAD-004 / E-LEAD-008 的前端预检：服务端仍是权威 */
function precheckAdd(): string {
  const form = addDialog.value
  if (!form.userId) return '请选择负责人'
  if (form.leaderType === 'primary') {
    const sameLine = primaries.value.find(
      (item) => (item.businessLine ?? '') === (form.businessLine || ''),
    )
    if (sameLine) {
      return `同一组织同一业务线只能有一个正职：${sameLine.userName} 已是「${LEADER_TYPE_LABEL.primary}」，副职不限（E-LEAD-004）`
    }
  }
  if (form.businessLine && !isGroup.value) {
    return '分管业务线仅允许填在「集团」节点上，其余组织填写即报错（E-LEAD-008）'
  }
  return ''
}

async function submitAdd(): Promise<void> {
  const problem = precheckAdd()
  if (problem) {
    ElMessage({ type: 'warning', message: problem })
    return
  }
  addDialog.value.submitting = true
  try {
    await createOrgLeader(props.org.id, {
      userId: addDialog.value.userId,
      leaderType: addDialog.value.leaderType,
      sortNo: Number(addDialog.value.sortNo) || 0,
      businessLine: addDialog.value.businessLine || null,
      remark: addDialog.value.remark || undefined,
    })
    ElMessage({ type: 'success', message: '负责人已新增' })
    addDialog.value.visible = false
    await load()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  } finally {
    addDialog.value.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 升 / 降正副职
// ---------------------------------------------------------------------------
async function changeLeaderType(leader: OrgLeader, next: LeaderType): Promise<void> {
  if (next === 'primary') {
    const sameLine = primaries.value.find(
      (item) => item.leaderId !== leader.leaderId && (item.businessLine ?? '') === (leader.businessLine ?? ''),
    )
    try {
      await ElMessageBox.confirm(
        sameLine
          ? `该业务线已有正职「${sameLine.userName}」。同一组织同一业务线只能有一个正职（E-LEAD-004），本次操作会被服务端拒绝。`
          : `确认将「${leader.userName}」设为「${props.org.name}」的正职负责人？正职是审批人解析的取值来源（PRD 5.4）。`,
        '确认调整负责人类型',
        { confirmButtonText: '确认设为正职', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    await updateOrgLeader(props.org.id, leader.leaderId, { leaderType: next })
    ElMessage({ type: 'success', message: `已调整为${LEADER_TYPE_LABEL[next]}` })
    await load()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  }
}

// ---------------------------------------------------------------------------
// 移除负责人（危险操作：二次确认 + 原因 + 在途影响清单）
// ---------------------------------------------------------------------------
const removeDialog = ref({
  visible: false,
  submitting: false,
  target: null as OrgLeader | null,
  blocked: false,
  count: 0,
  pendingCount: 0,
  items: [] as ImpactItem[],
  message: '',
})

async function openRemove(leader: OrgLeader): Promise<void> {
  removeDialog.value = {
    visible: true,
    submitting: false,
    target: leader,
    blocked: false,
    count: 0,
    pendingCount: 0,
    items: [],
    message: '',
  }
  // import-spec §7.1 场景 5：负责人变更会影响在途单据的候选人来源
  try {
    const check: InFlightCheck = await checkOrgInFlight(props.org.id)
    removeDialog.value.blocked = check.blocking || check.inFlightCount > 0 || (check.pendingTasks ?? 0) > 0
    removeDialog.value.count = check.inFlightCount
    removeDialog.value.pendingCount = check.pendingTasks ?? 0
    removeDialog.value.items = check.items ?? []
    removeDialog.value.message =
      check.message ||
      `负责人变更前请确认影响面：「${props.org.name}」及其子树下有 ${check.inFlightCount} 张在途单据${(check.pendingTasks ?? 0) > 0 ? `、${check.pendingTasks} 条待处理待办` : ''}。审批人在流程发起时已快照，本次变更不会改派在途单据（PRD 5.4）。`
  } catch {
    removeDialog.value.message =
      '未能取到在途影响清单（接口不可用）。审批人已快照，本次变更不会改派在途单据（PRD 5.4），请自行确认影响面后继续。'
  }
}

async function confirmRemove(payload: { reason: string; force: boolean }): Promise<void> {
  const target = removeDialog.value.target
  if (!target) return
  removeDialog.value.submitting = true
  try {
    await deleteOrgLeader(props.org.id, target.leaderId, {
      reason: payload.reason,
      force: payload.force,
    })
    ElMessage({ type: 'success', message: `已移除负责人「${target.userName}」` })
    removeDialog.value.visible = false
    await load()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  } finally {
    removeDialog.value.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 集团层业务线绑定
// ---------------------------------------------------------------------------
const lineDialog = ref({
  visible: false,
  category: 'business' as BusinessLine,
  orgId: '',
  userId: '',
  leaderType: 'primary' as LeaderType,
  reason: '',
  submitting: false,
})

/** 业务线绑定的候选组织：集团节点自身及其下级部门（PRD 5.1 集团职能部门） */
const lineOrgTree = computed<OrgTreeNode[]>(() => orgStore.tree)

async function openLineDialog(category: BusinessLine): Promise<void> {
  const current = lines.value.find((item) => item.category === category)
  lineDialog.value = {
    visible: true,
    category,
    orgId: current?.orgId ?? props.org.id,
    userId: current?.leaderId ?? '',
    leaderType: current?.leaderType ?? 'primary',
    reason: '',
    submitting: false,
  }
  await loadCandidates(lineDialog.value.orgId)
}

async function onLineOrgChange(): Promise<void> {
  lineDialog.value.userId = ''
  await loadCandidates(lineDialog.value.orgId)
}

async function submitLine(): Promise<void> {
  const form = lineDialog.value
  if (!form.orgId || !form.userId) {
    ElMessage({ type: 'warning', message: '请选择绑定组织与分管领导' })
    return
  }
  if (!form.reason.trim()) {
    ElMessage({ type: 'warning', message: '请填写变更原因（将写入审计日志）' })
    return
  }
  form.submitting = true
  try {
    await updateLeaderLine(form.category, {
      orgId: form.orgId,
      userId: form.userId,
      leaderType: form.leaderType,
      reason: form.reason.trim(),
    })
    ElMessage({ type: 'success', message: `${BUSINESS_LINE_LABEL[form.category]}线分管领导已更新` })
    form.visible = false
    await loadLines()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  } finally {
    form.submitting = false
  }
}

/** 业务线 → 绑定情况（五值固定，缺项也占位显示，避免「没显示=没绑定」的误读） */
const lineRows = computed(() =>
  BUSINESS_LINE_ORDER.map((category) => {
    const found = lines.value.find((item) => item.category === category)
    return {
      category,
      label: found?.categoryLabel || BUSINESS_LINE_LABEL[category],
      binding: found ?? null,
    }
  }),
)

// ---------------------------------------------------------------------------
// 切换组织节点：关闭所有对话框并重新拉取。
// 放在所有 ref 声明之后 —— immediate 会在 setup 期间同步执行回调，提前引用会踩 TDZ。
// ---------------------------------------------------------------------------
watch(
  () => props.org.id,
  async () => {
    addDialog.value.visible = false
    removeDialog.value.visible = false
    lineDialog.value.visible = false
    await Promise.all([load(), loadLines()])
  },
  { immediate: true },
)
</script>

<template>
  <section class="oa-card leader-panel">
    <header class="panel-head">
      <h2 class="oa-text-title-section">负责人</h2>
      <span class="oa-text-caption oa-text-subtle">
        审批人解析取「正职」；一人可在多个组织任负责人（PRD 5.1）
      </span>
      <span class="oa-text-caption oa-text-subtle">
        正职 <b class="oa-tnum">{{ primaries.length }}</b> · 副职 <b class="oa-tnum">{{ deputies.length }}</b>
      </span>
      <el-button v-if="editable" type="primary" size="small" class="add-btn" @click="openAdd">
        新增负责人
      </el-button>
    </header>

    <!-- AC-11：未设正职必须常驻可见（不是 toast） -->
    <div v-if="missingPrimary" class="alert is-warning" role="status">
      <span class="oa-pill is-pending">未设正职</span>
      <p>
        「{{ org.name }}」尚未设置正职负责人。该部门成员发起审批时，「直属部门负责人」节点候选为空，
        <b>服务端将直接拒绝发起</b>并提示「节点无有效审批人，请联系管理员」（AC-11 / W-ORG-014）。
      </p>
    </div>

    <el-alert v-if="errorMessage" class="load-error" type="error" :closable="false" show-icon :title="errorMessage" />

    <el-table v-loading="loading" :data="leaders" size="small" border>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <StatusPill kind="leader" :status="row.leaderType" />
        </template>
      </el-table-column>
      <el-table-column label="姓名" min-width="110">
        <template #default="{ row }">
          <b class="oa-text-body-sm">{{ row.userName }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="account" label="账号" min-width="130" />
      <el-table-column label="工号" width="110">
        <template #default="{ row }">
          <span class="oa-tnum">{{ row.employeeNo || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分管业务线" min-width="110">
        <template #default="{ row }">
          <span v-if="row.businessLine" class="oa-tag is-info">
            {{ BUSINESS_LINE_LABEL[row.businessLine as BusinessLine] }}
          </span>
          <span v-else class="oa-text-subtle">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" width="80" />
      <el-table-column label="在岗状态" width="100">
        <template #default="{ row }">
          <StatusPill v-if="row.userStatus" kind="user" :status="row.userStatus" />
          <span v-else class="oa-text-subtle">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="editable" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.leaderType !== 'primary'"
            link
            type="primary"
            @click="changeLeaderType(asLeader(row), 'primary')"
          >
            设为正职
          </el-button>
          <el-button v-else link type="primary" @click="changeLeaderType(asLeader(row), 'deputy')">
            改为副职
          </el-button>
          <el-button link type="danger" @click="openRemove(asLeader(row))">移除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="oa-empty">
          <p>暂无负责人记录</p>
          <p v-if="editable" class="oa-text-caption">点击右上角「新增负责人」绑定正职 / 副职。</p>
        </div>
      </template>
    </el-table>

    <!-- 集团层业务线绑定：仅 org_type=集团（E-LEAD-008） -->
    <template v-if="isGroup">
      <header class="panel-head sub">
        <h2 class="oa-text-title-section">业务线分管领导绑定</h2>
        <span class="oa-text-caption oa-text-subtle">
          五值：经营 / 经济 / 行政 / 人力 / 投资（事项类别口径，T-06 定稿）
        </span>
      </header>

      <div v-if="linesWithoutPrimary.length" class="alert is-warning" role="status">
        <span class="oa-pill is-pending">未设正职</span>
        <p>
          有 {{ linesWithoutPrimary.length }} 条业务线尚未绑定正职分管领导，对应类别的事项单在「集团分管领导」
          节点将无有效审批人（AC-11）。
        </p>
      </div>

      <el-table v-loading="linesLoading" :data="lineRows" size="small" border>
        <el-table-column label="业务线" width="110">
          <template #default="{ row }">
            <span class="oa-tag is-info">{{ row.label }}</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定组织" min-width="180">
          <template #default="{ row }">{{ row.binding?.orgName || '—' }}</template>
        </el-table-column>
        <el-table-column label="分管领导" min-width="150">
          <template #default="{ row }">
            <span v-if="row.binding?.leaderName">{{ row.binding.leaderName }}</span>
            <span v-else class="oa-text-subtle">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <StatusPill v-if="row.binding?.leaderType" kind="leader" :status="row.binding.leaderType" />
            <span v-else class="oa-text-subtle">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="editable" label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLineDialog(row.category)">绑定 / 更换</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </section>

  <!-- ===================== 新增负责人 ===================== -->
  <el-dialog v-model="addDialog.visible" title="新增负责人" width="560px" append-to-body>
    <el-form label-position="top">
      <el-form-item label="负责人" required>
        <el-select
          v-model="addDialog.userId"
          filterable
          remote
          clearable
          :remote-method="searchCandidates"
          :loading="candidateLoading"
          placeholder="按姓名 / 工号 / 账号检索（服务端过滤；仅在职人员，E-LEAD-006）"
          class="fill"
        >
          <el-option
            v-for="item in candidates"
            :key="item.userId"
            :value="item.userId"
            :label="`${item.name}（${item.account}）`"
            :disabled="item.status !== 'active'"
          >
            <span>{{ item.name }}</span>
            <span class="oa-text-caption oa-text-subtle">
              {{ item.account }} · {{ item.employeeNo }} · {{ item.positionName || '—' }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="负责人类型" required>
        <el-radio-group v-model="addDialog.leaderType">
          <el-radio value="primary">正职（审批人解析取值）</el-radio>
          <el-radio value="deputy">副职</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="`分管业务线${isGroup ? '' : '（仅集团层可填，E-LEAD-008）'}`">
        <el-select
          v-model="addDialog.businessLine"
          clearable
          :disabled="!isGroup"
          placeholder="仅「集团」节点可绑定业务线"
          class="fill"
        >
          <el-option
            v-for="line in BUSINESS_LINE_ORDER"
            :key="line"
            :value="line"
            :label="BUSINESS_LINE_LABEL[line]"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="排序号（0–9999，越小越靠前）">
        <el-input-number v-model="addDialog.sortNo" :min="0" :max="9999" :step="1" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="addDialog.remark" maxlength="255" show-word-limit placeholder="不超过 255 字" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="addDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="addDialog.submitting" @click="submitAdd">确认新增</el-button>
    </template>
  </el-dialog>

  <!-- ===================== 移除负责人（危险操作） ===================== -->
  <ImpactConfirmDialog
    v-model="removeDialog.visible"
    title="移除负责人"
    action-label="移除负责人"
    :target="removeDialog.target ? `${removeDialog.target.userName}（${removeDialog.target.account}）` : ''"
    :message="removeDialog.message"
    :blocked="removeDialog.blocked"
    :count="removeDialog.count"
    :pending-count="removeDialog.pendingCount"
    :items="removeDialog.items"
    :can-force="canForce"
    :reason-required="true"
    :submitting="removeDialog.submitting"
    consequence="在途单据仍由原快照审批人处理，不会自动改派（PRD 5.4）"
    @confirm="confirmRemove"
  />

  <!-- ===================== 业务线绑定 ===================== -->
  <el-dialog v-model="lineDialog.visible" title="绑定业务线分管领导" width="560px" append-to-body>
    <el-form label-position="top">
      <el-form-item label="业务线">
        <span class="oa-tag is-info">{{ BUSINESS_LINE_LABEL[lineDialog.category] }}</span>
      </el-form-item>

      <el-form-item label="绑定组织（集团及其下级部门）" required>
        <el-tree-select
          v-model="lineDialog.orgId"
          :data="lineOrgTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          filterable
          class="fill"
          @change="onLineOrgChange"
        />
      </el-form-item>

      <el-form-item label="分管领导" required>
        <el-select
          v-model="lineDialog.userId"
          filterable
          remote
          :remote-method="searchCandidates"
          :loading="candidateLoading"
          placeholder="从该组织负责人候选中选择（服务端关键字过滤）"
          class="fill"
        >
          <el-option
            v-for="item in candidates"
            :key="item.userId"
            :value="item.userId"
            :label="`${item.name}（${item.account}）`"
            :disabled="item.status !== 'active'"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="负责人类型" required>
        <el-radio-group v-model="lineDialog.leaderType">
          <el-radio value="primary">正职</el-radio>
          <el-radio value="deputy">副职</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="变更原因" required>
        <el-input
          v-model="lineDialog.reason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          placeholder="必填：将写入审计日志（REQ-LOG-004）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="lineDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="lineDialog.submitting" @click="submitLine">确认绑定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.leader-panel {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-sm);
}

.panel-head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-sm);
}

.panel-head.sub {
  margin-top: var(--oa-space-md);
}

.panel-head .add-btn {
  margin-left: auto;
}

/* 警示条：语义色 surface 底 + 左侧 3px 竖条，常驻而非 toast */
.alert {
  display: flex;
  align-items: flex-start;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border-left: 3px solid var(--oa-color-warning);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-warning-surface);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.alert p {
  min-width: 0;
}

.load-error {
  margin: var(--oa-space-xs) 0;
}

.fill {
  width: 100%;
}
</style>
