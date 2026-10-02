<script setup lang="ts">
/**
 * oa-web · 组织架构（左树右详情）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.1（集团-公司-部门-科室四级；节点字段：名称/类型/上级/负责人/状态）、
 *     5.5（组织节点停用前必须处理完该节点全部在途单据）、AC-11（审批人空缺拦截）
 *   · `doc/import-spec.md` §3.2（org_path 为唯一业务键、四级层级 E-ORG-006）、
 *     §4.2（E-ORG-010 停用拦截、W-ORG-014 未设正职、W-ORG-017 停用组织下仍有在职人员）、
 *     §7.1 场景 1/2（父节点变更、启用→停用 → 影响清单）、§8.2（停用拦截文案）
 *   · `DESIGN.md` › Data Display / Feedback & Overlays（表格密度、状态徽标、危险操作二次确认）
 *
 * 三处必须遵守的硬约束：
 *   1. 停用组织**必须先调 `/in-flight-check`**；在途/待办非零时弹确认框展示
 *      「数量 + 明细（单号/单据类型/发起人/当前节点）+ 子树在职人数」并**默认阻断**；
 *      只有系统管理员填了原因才能「强制继续」——`{reason, force:true}` 随请求提交，
 *      服务端据此**放行**该次在途阻断并写入审计日志（AC-52）。
 *   2. 每次写操作成功后重新拉取组织树，避免本地乐观更新与服务端的 org_path 推导结果不一致。
 *   3. 颜色/间距/字号一律走 `tokens.scss` 的 CSS 变量，不写死色值。
 *
 * 注：`DESIGN.md` › Navigation 有一句「组织树只出现在侧栏或选择器内，不做独立页面」，
 *     本页是阶段 1 交付物明确要求的管理界面（左树右详情），属于对上述约定的**受控例外**，
 *     已在交付说明中记录；树本身的视觉仍严格复用 `sidebar-tree-node` 的密度（高 32px）。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import OrgLeadersPanel from './OrgLeadersPanel.vue'
import StatusPill from '@/components/StatusPill.vue'
import ImpactConfirmDialog from '@/components/ImpactConfirmDialog.vue'
import { useOrgStore, treeWithoutSubtree } from '@/stores/org'
import { useUserStore } from '@/stores/user'
import { checkOrgInFlight, createOrg, disableOrg, enableOrg, moveOrg, updateOrg } from '@/api/org'
import { canForceChange, canManageOrg } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import {
  ORG_TYPE_LABEL,
  allowedChildTypes,
  checkOrgName,
  checkRemark,
  formTypeText,
} from '@/utils/identity-rules'
import type { ImpactItem, InFlightCheck, OrgTreeNode, OrgType } from '@/types/identity'

const userStore = useUserStore()
const orgStore = useOrgStore()

const editable = computed(() => canManageOrg(userStore))
const canForce = computed(() => canForceChange(userStore))

const treeRef = ref<InstanceType<typeof ElTree> | null>(null)
const keyword = ref('')
const showSearchResult = ref(false)

const selected = computed<OrgTreeNode | null>(() => orgStore.selectedNode)

/** 模板里的 el-tree 作用域插槽数据是 unknown，统一走这个收窄函数（不用 any） */
function asNode(value: unknown): OrgTreeNode {
  return value as OrgTreeNode
}

/**
 * 「未设正职」判定（AC-11 / W-ORG-014）。两个来源：
 *   1. 权威来源 = 树接口的 `OrgView.hasPrimaryLeader`（服务端按 `sys_org_leader` 中
 *      `leader_type='primary' AND category IS NULL` 全树**一次批量查询**聚合，无 N+1）；
 *      ⚠ 不再用 `leaderId` 推断——那只是 `sys_org.leader_id` 的冗余列，
 *      集团层业务线分管领导（category 非空）会让判定误判为「已设正职」。
 *   2. 会话内的即时覆盖 = 负责人面板刚改完负责人后上报的结果（避免等下一次整树刷新）。
 * 两者都没有时不显示警示，避免误报。
 */
const primaryState = ref<Record<string, boolean>>({})

function onPrimaryState(payload: { orgId: string; hasPrimary: boolean }): void {
  primaryState.value = { ...primaryState.value, [payload.orgId]: payload.hasPrimary }
}

function needsPrimaryWarning(node: OrgTreeNode): boolean {
  if (node.orgType !== 'dept' && node.orgType !== 'section') return false
  const known = primaryState.value[node.id]
  if (known !== undefined) return !known
  return node.hasPrimaryLeader === false
}

function nodeMatches(value: string, node: OrgTreeNode): boolean {
  const text = value.trim()
  if (!text) return true
  return node.name.includes(text) || node.path.includes(text)
}

function filterNode(value: string, data: unknown): boolean {
  return nodeMatches(value, asNode(data))
}

/** 树事件回调：模板里不写带类型标注的箭头函数，统一落到这里 */
function onNodeClick(data: unknown): void {
  orgStore.select(asNode(data).id)
}

function onNodeToggle(data: unknown): void {
  orgStore.toggleExpand(asNode(data).id)
}

watch(keyword, (value) => {
  treeRef.value?.filter(value)
  if (!value.trim()) {
    showSearchResult.value = false
    orgStore.clearSearch()
  }
})

// ---------------------------------------------------------------------------
// 检索定位：本地即时过滤树 + 服务端检索结果（跨子树精确定位）
// ---------------------------------------------------------------------------
async function runSearch(): Promise<void> {
  showSearchResult.value = true
  await orgStore.search(keyword.value)
}

function gotoOrg(orgId: string): void {
  orgStore.select(orgId)
  treeRef.value?.getNode(orgId)?.expand()
  showSearchResult.value = false
}

// ---------------------------------------------------------------------------
// 选中节点后的在途检查（详情区常驻展示；停用前会再查一次）
// ---------------------------------------------------------------------------
const inFlight = ref<InFlightCheck | null>(null)
const checking = ref(false)

async function loadInFlight(orgId: string): Promise<void> {
  checking.value = true
  inFlight.value = null
  try {
    inFlight.value = await checkOrgInFlight(orgId)
  } catch {
    // 接口不可用时不阻断浏览，仅让详情区显示「—」
    inFlight.value = null
  } finally {
    checking.value = false
  }
}

watch(
  () => selected.value?.id,
  (id) => {
    if (id) void loadInFlight(id)
  },
)

onMounted(async () => {
  // 在途检查由 selected 的 watch 触发（loadTree 会选中根节点），此处不再重复请求
  await orgStore.loadTree()
})

/** 写操作成功后统一刷新：重新拉树并保持选中 */
async function refreshAfterWrite(orgId: string): Promise<void> {
  await orgStore.reload()
  if (orgStore.flatMap[orgId]) orgStore.select(orgId)
}

// ---------------------------------------------------------------------------
// 新增 / 改名
// ---------------------------------------------------------------------------
const formDialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'rename',
  name: '',
  orgType: 'dept' as OrgType,
  remark: '',
  submitting: false,
})

/** E-ORG-006：可选子类型由父节点类型推导（公司必须挂集团、科室必须挂部门） */
const allowedTypes = computed(() => allowedChildTypes(selected.value?.orgType ?? null))

const formTitle = computed(() =>
  formDialog.mode === 'create'
    ? selected.value
      ? `在「${selected.value.name}」下新增节点`
      : '新增集团根节点'
    : `修改「${selected.value?.name ?? ''}」`,
)

function openCreate(): void {
  formDialog.visible = true
  formDialog.mode = 'create'
  formDialog.name = ''
  formDialog.orgType = allowedTypes.value[0] ?? 'dept'
  formDialog.remark = ''
}

function openRename(): void {
  if (!selected.value) return
  formDialog.visible = true
  formDialog.mode = 'rename'
  formDialog.name = selected.value.name
  formDialog.remark = selected.value.remark ?? ''
}

async function submitForm(): Promise<void> {
  const nameError = checkOrgName(formDialog.name)
  if (nameError) {
    ElMessage({ type: 'warning', message: nameError })
    return
  }
  const remarkError = checkRemark(formDialog.remark)
  if (remarkError) {
    ElMessage({ type: 'warning', message: remarkError })
    return
  }
  formDialog.submitting = true
  try {
    if (formDialog.mode === 'create') {
      const created = await createOrg({
        name: formDialog.name.trim(),
        orgType: formDialog.orgType,
        parentId: selected.value?.id ?? null,
        remark: formDialog.remark.trim() || undefined,
      })
      ElMessage({ type: 'success', message: '组织节点已创建' })
      formDialog.visible = false
      await refreshAfterWrite(created.id)
    } else if (selected.value) {
      await updateOrg(selected.value.id, {
        name: formDialog.name.trim(),
        remark: formDialog.remark.trim(),
      })
      ElMessage({ type: 'success', message: '组织节点已更新' })
      formDialog.visible = false
      await refreshAfterWrite(selected.value.id)
    }
  } catch (error) {
    reportApiError(error)
  } finally {
    formDialog.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 移动节点（父节点变更 → import-spec §7.1 场景 1）
// ---------------------------------------------------------------------------
const moveDialog = reactive({
  visible: false,
  parentId: '',
  reason: '',
  submitting: false,
})

/** 可选父节点：排除自身与其整棵子树，避免把父节点挂到自己的子孙下 */
const moveTargets = computed<OrgTreeNode[]>(() =>
  selected.value ? treeWithoutSubtree(orgStore.tree, selected.value.id) : [],
)

function openMove(): void {
  if (!selected.value) return
  moveDialog.visible = true
  moveDialog.parentId = selected.value.parentId ?? ''
  moveDialog.reason = ''
}

async function confirmBox(message: string, title: string, confirmText: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: confirmText,
      cancelButtonText: '取消',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

async function submitMove(): Promise<void> {
  if (!selected.value) return
  if (!moveDialog.parentId) {
    ElMessage({ type: 'warning', message: '请选择新的上级组织' })
    return
  }
  if (moveDialog.parentId === selected.value.parentId) {
    ElMessage({ type: 'warning', message: '目标上级与当前上级相同，无需移动' })
    return
  }
  if (!moveDialog.reason.trim()) {
    ElMessage({ type: 'warning', message: '请填写移动原因（将写入审计日志）' })
    return
  }
  const node = selected.value
  const ok = await confirmBox(
    `确认将「${node.path}」移动到所选上级下？组织变更不会改派在途单据（审批人已在发起时快照，PRD 5.4），但数据域可见性会随之变化。`,
    '确认移动组织节点',
    '确认移动',
  )
  if (!ok) return

  moveDialog.submitting = true
  try {
    await moveOrg(node.id, {
      targetParentId: moveDialog.parentId,
      reason: moveDialog.reason.trim(),
    })
    ElMessage({ type: 'success', message: '组织节点已移动' })
    moveDialog.visible = false
    await refreshAfterWrite(node.id)
  } catch (error) {
    reportApiError(error)
  } finally {
    moveDialog.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 启用 / 停用（停用前必须在途检查，非零默认阻断）
// ---------------------------------------------------------------------------
const disableDialog = reactive({
  visible: false,
  submitting: false,
  blocked: false,
  count: 0,
  pendingCount: 0,
  items: [] as ImpactItem[],
  message: '',
})

async function toggleStatus(): Promise<void> {
  const node = selected.value
  if (!node) return

  if (node.status === 'disabled') {
    const ok = await confirmBox(
      `确认启用组织「${node.path}」？启用后该节点可重新作为发起者的归属节点。`,
      '确认启用组织',
      '确认启用',
    )
    if (!ok) return
    try {
      // 启用同为危险操作：原因随请求提交（服务端写审计日志，AC-52）
      await enableOrg(node.id, { reason: '管理后台启用' })
      ElMessage({ type: 'success', message: '组织已启用' })
      await refreshAfterWrite(node.id)
    } catch (error) {
      reportApiError(error)
    }
    return
  }

  // 停用：先查在途（E-ORG-010 / PRD 5.5），再决定是否默认阻断
  let check: InFlightCheck | null = null
  try {
    check = await checkOrgInFlight(node.id)
  } catch {
    check = null
  }
  const count = check?.inFlightCount ?? 0
  const pending = check?.pendingTasks ?? 0
  const staff = check?.activeStaffCount ?? 0
  const staffHint = staff > 0 ? `该子树下仍有 ${staff} 名在职人员（W-ORG-017：停用后须先转岗）。` : ''
  inFlight.value = check
  disableDialog.blocked = check?.blocking ?? (count > 0 || pending > 0)
  disableDialog.count = count
  disableDialog.pendingCount = pending
  disableDialog.items = check?.items ?? []
  disableDialog.message =
    check?.message ||
    (count > 0 || pending > 0
      ? `组织停用前必须清空在途单据：「${node.path}」及其子树下仍有 ${count} 张在途单据${pending > 0 ? `、${pending} 条待处理待办` : ''}，请先办结或流转处理。${staffHint}`
      : `「${node.path}」及其子树下当前无在途单据，可安全停用。停用后该节点不可作为发起者的归属节点。${staffHint}` +
        (check?.warnings?.length ? ` 提示：${check.warnings.join('；')}` : ''))
  disableDialog.visible = true
}

/**
 * 停用确认：`reason` 必填，`force=true`（强制继续）时服务端**真正放行**在途/待办阻断，
 * 并把原因与操作人写进审计日志（AC-52）。在途单据不会自动改派（PRD 5.4）。
 */
async function confirmDisable(payload: { reason: string; force: boolean }): Promise<void> {
  const node = selected.value
  if (!node) return
  disableDialog.submitting = true
  try {
    await disableOrg(node.id, { reason: payload.reason, force: payload.force })
    ElMessage({
      type: 'success',
      message: payload.force ? '已强制停用（在途阻断已放行，原因已写入审计日志）' : '组织已停用',
    })
    disableDialog.visible = false
    await refreshAfterWrite(node.id)
  } catch (error) {
    reportApiError(error)
  } finally {
    disableDialog.submitting = false
  }
}

/** 明细列取值：服务端未下发的列一律「—」，不做推断（影响程度/受影响原因后端不给） */
function asItem(row: unknown): ImpactItem {
  return row as ImpactItem
}

function itemTypeText(row: unknown): string {
  const item = asItem(row)
  return formTypeText(item.formType, item.formTypeLabel)
}

function itemInitiatorText(row: unknown): string {
  return asItem(row).initiatorName || '—'
}

function itemNodeText(row: unknown): string {
  return asItem(row).currentNodeName || '—'
}

// ---------------------------------------------------------------------------
// 展示辅助
// ---------------------------------------------------------------------------
interface DetailRow {
  label: string
  value: string
  /** 路径等标识类字段用等宽数字字体（DESIGN.md › Typography › mono） */
  mono?: boolean
}

const detailRows = computed<DetailRow[]>(() => {
  const node = selected.value
  if (!node) return []
  return [
    { label: '节点类型', value: ORG_TYPE_LABEL[node.orgType] },
    { label: '层级', value: `第 ${node.depth} 级` },
    { label: '组织全路径', value: node.path, mono: true },
    { label: '子节点数', value: String(node.childCount ?? node.children?.length ?? 0) },
    { label: '在职人数（不含子树）', value: node.userCount === undefined ? '—' : String(node.userCount) },
    {
      label: '负责人',
      value: node.primaryLeaderNames?.length ? node.primaryLeaderNames.join('、') : '未设正职',
    },
    { label: '备注', value: node.remark || '—' },
    { label: '最近更新', value: node.updatedAt || '—' },
  ]
})
</script>

<template>
  <div class="oa-org-page">
    <!-- ==================== 左：组织树 ==================== -->
    <aside class="tree-pane oa-card">
      <header class="pane-head">
        <h1 class="oa-text-title-page">组织架构</h1>
        <el-button size="small" :loading="orgStore.loading" @click="orgStore.reload()">刷新</el-button>
      </header>

      <div class="search-row">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索组织名称或路径（支持 集团/公司A/部门1）"
          @keyup.enter="runSearch"
          @clear="orgStore.clearSearch()"
        />
        <el-button :loading="orgStore.searching" @click="runSearch">检索</el-button>
      </div>

      <!-- 服务端检索结果：可跨折叠分支精确定位 -->
      <ul v-if="showSearchResult && orgStore.searchResults.length" class="search-result">
        <li v-for="item in orgStore.searchResults" :key="item.id">
          <button type="button" @click="gotoOrg(item.id)">
            <b>{{ item.name }}</b>
            <span class="oa-text-caption oa-text-subtle">{{ item.path }}</span>
          </button>
        </li>
      </ul>

      <div class="tree-tools">
        <el-button size="small" :disabled="!editable" @click="openCreate">
          {{ selected ? '新增子节点' : '新增集团根节点' }}
        </el-button>
        <el-button size="small" @click="orgStore.setExpandedAll(true)">展开全部</el-button>
        <el-button size="small" @click="orgStore.setExpandedAll(false)">收起全部</el-button>
      </div>

      <!-- AC-11：未设正职的部门/科室数（判据 = 树接口的 hasPrimaryLeader，权威表聚合值） -->
      <p v-if="orgStore.orgsMissingPrimaryLeader.length" class="primary-summary">
        <span class="oa-pill is-pending">未设正职</span>
        当前有 <b class="oa-tnum">{{ orgStore.orgsMissingPrimaryLeader.length }}</b> 个部门/科室未设正职：
        其成员发起审批会被服务端拒绝（AC-11 / W-ORG-014）。
      </p>

      <el-tree
        ref="treeRef"
        class="org-tree"
        :data="orgStore.tree"
        node-key="id"
        highlight-current
        :current-node-key="orgStore.selectedId"
        :default-expanded-keys="orgStore.expandedIds"
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        :props="{ label: 'name', children: 'children' }"
        @node-click="onNodeClick"
        @node-expand="onNodeToggle"
        @node-collapse="onNodeToggle"
      >
        <template #default="{ data }">
          <span class="node-row">
            <span class="node-name">{{ asNode(data).name }}</span>
            <StatusPill v-if="asNode(data).status === 'disabled'" kind="org" status="disabled" />
            <span v-if="needsPrimaryWarning(asNode(data))" class="oa-pill is-pending">未设正职</span>
          </span>
        </template>
      </el-tree>

      <p v-if="orgStore.errorMessage" class="tree-error">{{ orgStore.errorMessage }}</p>
      <p v-else-if="!orgStore.loading && !orgStore.hasTree" class="oa-text-caption oa-text-subtle">
        暂无组织数据。可点击「新增集团根节点」创建集团节点（整棵组织树有且仅有 1 个集团）。
      </p>
    </aside>

    <!-- ==================== 右：节点详情 ==================== -->
    <section class="detail-pane">
      <div v-if="!selected" class="oa-card oa-empty">
        <p>请在左侧选择组织节点</p>
      </div>

      <template v-else>
        <div class="oa-card detail-head">
          <div class="title-row">
            <h2 class="oa-text-title-page">{{ selected.name }}</h2>
            <span class="oa-tag is-info">{{ ORG_TYPE_LABEL[selected.orgType] }}</span>
            <StatusPill kind="org" :status="selected.status" />
          </div>

          <nav class="path-crumb" aria-label="组织路径">
            <template v-for="(node, index) in orgStore.selectedChain" :key="node.id">
              <button type="button" class="crumb" @click="orgStore.select(node.id)">{{ node.name }}</button>
              <i v-if="index < orgStore.selectedChain.length - 1" aria-hidden="true">/</i>
            </template>
          </nav>

          <div class="actions">
            <el-button v-if="editable" type="primary" @click="openCreate">新增子节点</el-button>
            <el-button v-if="editable" @click="openRename">改名</el-button>
            <el-button v-if="editable" @click="openMove">移动</el-button>
            <el-button
              v-if="editable"
              :type="selected.status === 'active' ? 'danger' : 'primary'"
              :plain="selected.status === 'active'"
              @click="toggleStatus"
            >
              {{ selected.status === 'active' ? '停用' : '启用' }}
            </el-button>
          </div>
        </div>

        <!-- AC-11：未设正职负责人必须常驻可见 -->
        <div v-if="needsPrimaryWarning(selected)" class="alert is-warning" role="status">
          <span class="oa-pill is-pending">未设正职</span>
          <p>
            该{{ ORG_TYPE_LABEL[selected.orgType] }}尚未设置正职负责人：成员发起审批时「直属部门负责人」节点
            候选为空，<b>服务端将拒绝发起</b>并提示「节点无有效审批人，请联系管理员」（AC-11 / W-ORG-014）。
            请在下方「负责人」面板新增正职。
          </p>
        </div>

        <div class="oa-card">
          <h3 class="oa-text-title-section">节点信息</h3>
          <dl class="info-grid">
            <div v-for="row in detailRows" :key="row.label" class="info-row">
              <dt>{{ row.label }}</dt>
              <dd :class="{ 'oa-mono': row.mono }">{{ row.value }}</dd>
            </div>
          </dl>
        </div>

        <div class="oa-card">
          <header class="pane-head">
            <h3 class="oa-text-title-section">在途单据检查</h3>
            <el-button size="small" :loading="checking" @click="loadInFlight(selected.id)">重新检查</el-button>
          </header>

          <p v-if="checking" class="oa-text-body-sm oa-text-subtle">正在检查该节点及其子树下的在途单据…</p>
          <template v-else-if="inFlight">
            <div class="count-line">
              <span>在途单据</span>
              <b class="oa-tnum" :class="{ 'is-danger': inFlight.inFlightCount > 0 }">
                {{ inFlight.inFlightCount }}
              </b>
              <span class="unit">张</span>
              <template v-if="(inFlight.pendingTasks ?? 0) > 0">
                <span>待办</span>
                <b class="oa-tnum is-danger">{{ inFlight.pendingTasks }}</b>
                <span class="unit">条</span>
              </template>
              <span v-if="inFlight.activeStaffCount > 0" class="oa-text-caption oa-text-subtle">
                在职人数 {{ inFlight.activeStaffCount }} 人（含子树）
              </span>
            </div>
            <p v-if="inFlight.inFlightCount > 0 || (inFlight.pendingTasks ?? 0) > 0" class="oa-text-body-sm">
              停用前必须先办结或流转这些单据；停用不会自动改派在途单据（审批人在发起时已快照，PRD 5.4）。
              具备权限的系统管理员可填写原因后「强制继续」：服务端会放行该次阻断并把原因写入审计日志（AC-52）。
            </p>
            <ul v-if="inFlight.warnings?.length" class="warn-list">
              <li v-for="(text, index) in inFlight.warnings" :key="index">{{ text }}</li>
            </ul>
            <el-table v-if="inFlight.items?.length" :data="inFlight.items" size="small" border max-height="280">
              <el-table-column prop="bizNo" label="单号" min-width="150" />
              <el-table-column label="单据类型" min-width="130">
                <template #default="{ row }">{{ itemTypeText(row) }}</template>
              </el-table-column>
              <el-table-column label="发起人" min-width="110">
                <template #default="{ row }">{{ itemInitiatorText(row) }}</template>
              </el-table-column>
              <el-table-column label="当前节点" min-width="140">
                <template #default="{ row }">{{ itemNodeText(row) }}</template>
              </el-table-column>
            </el-table>
          </template>
          <p v-else class="oa-text-body-sm oa-text-subtle">
            未能取到检查结果（接口不可用或该节点不存在）。停用操作会重新检查一次。
          </p>
        </div>

        <OrgLeadersPanel
          :org="selected"
          @changed="refreshAfterWrite(selected.id)"
          @primary-state="onPrimaryState"
        />
      </template>
    </section>
  </div>

  <!-- ==================== 新增 / 改名 ==================== -->
  <el-dialog v-model="formDialog.visible" :title="formTitle" width="560px" append-to-body>
    <el-form label-position="top">
      <el-form-item v-if="formDialog.mode === 'create'" label="节点类型" required>
        <el-radio-group v-model="formDialog.orgType">
          <el-radio v-for="type in allowedTypes" :key="type" :value="type">
            {{ ORG_TYPE_LABEL[type] }}
          </el-radio>
        </el-radio-group>
        <p class="field-hint">
          层级约束：集团 → 公司 → 部门 → 科室；公司必须挂在集团下，科室必须挂在部门下（E-ORG-006）。
        </p>
      </el-form-item>

      <el-form-item label="组织名称" required>
        <el-input v-model="formDialog.name" maxlength="100" show-word-limit placeholder="不超过 100 字（E-ORG-012）" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="formDialog.remark" maxlength="255" show-word-limit placeholder="不超过 255 字" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="formDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="formDialog.submitting" @click="submitForm">
        {{ formDialog.mode === 'create' ? '确认新增' : '确认保存' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- ==================== 移动节点 ==================== -->
  <el-dialog v-model="moveDialog.visible" title="移动组织节点" width="560px" append-to-body>
    <el-form label-position="top">
      <el-form-item label="当前路径">
        <span class="oa-mono">{{ selected?.path }}</span>
      </el-form-item>
      <el-form-item label="新的上级组织" required>
        <el-tree-select
          v-model="moveDialog.parentId"
          :data="moveTargets"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          filterable
          clearable
          class="fill"
          placeholder="选择新的上级组织（已排除自身与子树）"
        />
      </el-form-item>
      <el-form-item label="移动原因" required>
        <el-input
          v-model="moveDialog.reason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          placeholder="必填：将写入审计日志（REQ-LOG-004）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="moveDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="moveDialog.submitting" @click="submitMove">确认移动</el-button>
    </template>
  </el-dialog>

  <!-- ==================== 停用（危险操作 + 在途阻断） ==================== -->
  <ImpactConfirmDialog
    v-model="disableDialog.visible"
    title="停用组织节点"
    action-label="停用"
    :target="selected?.path ?? ''"
    :message="disableDialog.message"
    :blocked="disableDialog.blocked"
    :count="disableDialog.count"
    :pending-count="disableDialog.pendingCount"
    :items="disableDialog.items"
    :can-force="canForce"
    :reason-required="true"
    :submitting="disableDialog.submitting"
    consequence="在途单据仍由原快照审批人处理，不会自动改派（PRD 5.4）"
    @confirm="confirmDisable"
  />
</template>

<style scoped>
.oa-org-page {
  display: grid;
  /* 左树固定 320px（与 .oa-panel-list-min 同值），右详情自适应 */
  grid-template-columns: var(--oa-panel-list-min) minmax(0, 1fr);
  gap: var(--oa-space-md);
  align-items: start;
}

/* ---------------- 左树 ---------------- */
.tree-pane {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-md);
  position: sticky;
  top: 0;
  max-height: calc(100vh - var(--oa-space-xxl));
  overflow: auto;
}

.pane-head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

.pane-head > :last-child {
  margin-left: auto;
}

.search-row {
  display: flex;
  gap: var(--oa-space-xs);
}

.search-result {
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas);
  max-height: 200px;
  overflow: auto;
}

.search-result button {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.search-result button:hover {
  background: var(--oa-color-canvas-subtle);
}

.tree-tools {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xxs);
}

/* 组织树密度对齐 DESIGN.md › sidebar-tree-node（四级、高 32px） */
.org-tree {
  --el-tree-node-content-height: var(--oa-space-control);
  font: var(--oa-font-body-sm);
}

.org-tree :deep(.el-tree-node__content) {
  height: var(--oa-space-control);
}

.node-row {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xxs);
  min-width: 0;
}

.node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* AC-11 汇总：语义色 surface + 左侧 3px 竖条（常驻提示，不用 toast） */
.primary-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oa-space-xxs);
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border-left: 3px solid var(--oa-color-warning);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-warning-surface);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink);
}

.primary-summary b {
  font-weight: 500;
}

.tree-error {
  font: var(--oa-font-caption);
  color: var(--oa-color-error);
}

/* ---------------- 右详情 ---------------- */
.detail-pane {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
  min-width: 0;
}

.detail-head {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-sm);
}

.title-row {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

.path-crumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oa-space-xxs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-subtle);
}

.path-crumb .crumb {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--oa-color-primary);
  font: var(--oa-font-body-sm);
  cursor: pointer;
}

.path-crumb i {
  font-style: normal;
  color: var(--oa-color-ink-disabled);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
}

/* 警示条：语义色 surface 底 + 左侧 3px 竖条（常驻，不用 toast） */
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

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--oa-space-xs) var(--oa-space-lg);
  margin-top: var(--oa-space-sm);
}

.info-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: var(--oa-space-xs);
  align-items: baseline;
}

.info-row dt {
  font: var(--oa-font-label);
  color: var(--oa-color-ink-muted);
}

.info-row dd {
  margin: 0;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
  word-break: break-all;
}

.count-line {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-xs);
  margin-bottom: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.count-line b {
  font: var(--oa-font-amount);
  color: var(--oa-color-success);
}

.count-line b.is-danger {
  color: var(--oa-color-error);
}

.count-line .unit {
  margin-right: var(--oa-space-xs);
  color: var(--oa-color-ink-subtle);
}

.warn-list {
  margin: var(--oa-space-xs) 0;
  font: var(--oa-font-caption);
  color: var(--oa-color-warning);
}

.warn-list li::before {
  content: '· ';
}

.field-hint {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.fill {
  width: 100%;
}

/* H5：左右两级改为上下堆叠 */
@media (max-width: 768px) {
  .oa-org-page {
    grid-template-columns: minmax(0, 1fr);
  }

  .tree-pane {
    position: static;
    max-height: none;
  }
}
</style>
