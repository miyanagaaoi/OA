<script setup lang="ts">
/**
 * oa-web · 人员管理（列表 + 离职 / 调岗 / 交接）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.1（一人可挂多组织）、5.3（手机号默认脱敏 `138****8888`，
 *     仅本人与系统管理员可见完整值）、5.5（员工离职前必须处理完名下全部待办，
 *     系统提示未处理任务数量）、AC-12（名下有待办 → 阻止离职，需先转办或改派）
 *   · `doc/import-spec.md` §3.3（字段与中文↔code）、§4.3（校验编号）、
 *     §7.1 场景 3/4 与 §7.2（影响清单与影响程度判定）、§8.1（离职拦截文案）、
 *     §9.1 / §9.2（导出列与模板一致；**主数据导出仅系统管理员**）
 *   · `DESIGN.md` › Data Display › table（行高 44px、首列固定、操作列右固定、
 *     行内操作最多 3 个）、Agent Usage Rules 第 5/6 条
 *   · 阶段 1 · 1.4：行操作追加「角色」——打开 `UserRolesDrawer` 做多角色分配
 *     （`sys_user_role`：多角色 + `scope_org_path` 生效范围 + 重复分配 409）
 *
 * 阻断口径（与后端默认行为一致，前端只做前置表达）：
 *   · 离职：名下未处理待办 > 0 → **默认阻断**（AC-12），放行条件 = 全部转办/改派/办结，
 *     或由系统管理员填写原因后「强制继续」（`force=true` 会真正放行并写审计日志，AC-52）。
 *   · 调岗：审批人已快照，**不自动改派**（PRD 5.4）；命中在途/待办时同样默认阻断并可强制继续。
 *   · 交接：本身就是补救动作，不阻断，但接收人与原因必填。
 *   · 「强制继续」与「待办交接」是两条不同的放行路径：前者放行阻断并留痕（AC-52），
 *     后者把待办转给别人从而满足 AC-12；两者都传了则先转办再提交离职/调岗。
 *   · `resign`/`transfer` 的请求体只有 `{reason, force}`：目标岗位与交接都由本页编排
 *     （先 `/handover`，调岗成功后用 `POST /users/{id}/positions` 落目标岗位）。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import ImpactConfirmDialog from '@/components/ImpactConfirmDialog.vue'
import UserPositionsDrawer from './UserPositionsDrawer.vue'
import UserRolesDrawer from './UserRolesDrawer.vue'
import { useOrgStore } from '@/stores/org'
import { useUserStore } from '@/stores/user'
import { fetchOrgSelector } from '@/api/org'
import {
  createUser,
  createUserPosition,
  exportUsers,
  fetchDirectory,
  fetchUserImpact,
  handoverUser,
  queryUsers,
  resignUser,
  transferUser,
  updateUser,
} from '@/api/user'
import { canAssignUserRole, canExportMasterData, canForceChange, canManageUser } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import {
  RULE_HINT,
  USER_STATUS_OPTIONS,
  checkAccount,
  checkEmail,
  checkEmployeeNo,
  checkPhone,
  checkRemark,
  checkUserName,
} from '@/utils/identity-rules'
import type {
  DirectoryItem,
  ImpactItem,
  OrgBrief,
  OrgTreeNode,
  UserImpact,
  UserItem,
  UserStatus,
} from '@/types/identity'

const userStore = useUserStore()
const orgStore = useOrgStore()

const editable = computed(() => canManageUser(userStore))
const canForce = computed(() => canForceChange(userStore))
/** 主数据导出仅系统管理员（import-spec §9.2 T-11）→ 入口不可见优于不可用 */
const showExport = computed(() => canExportMasterData(userStore))
/**
 * 角色分配入口（阶段 1 · 1.4）：权限码 `admin:authz:assign`（角色分配是独立权限项，
 * 权限树勾选 `admin:role:grant` 与它互不覆盖）；集团级角色是否可分配由抽屉内部
 * 按 `canEditRole` 逐个过滤（服务端仍是裁决方）。
 */
const canAssignRole = computed(() => canAssignUserRole(userStore))

/**
 * 编辑态可选状态：只能 `在职 ⇄ 停用`。
 * 离职（resigned）**只能**走 `POST /users/{id}/resign`（服务端对 `PUT` 传 resigned 一律 400），
 * 停用与离职同在途/待办拦截口径（命中且 block-on-inflight 时 409）。
 */
const editStatusOptions = computed(() => USER_STATUS_OPTIONS.filter((item) => item.value !== 'resigned'))

// ---------------------------------------------------------------------------
// 列表与筛选
// ---------------------------------------------------------------------------
const filters = reactive({
  keyword: '',
  orgId: '',
  status: '' as UserStatus | '',
})

const list = ref<UserItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const orgFilterOptions = ref<OrgBrief[]>([])
const orgFilterLoading = ref(false)

const emptyValue = computed(() => !filters.keyword && !filters.orgId && !filters.status)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await queryUsers({
      keyword: filters.keyword.trim() || undefined,
      orgId: filters.orgId || undefined,
      status: filters.status || undefined,
      includeSubOrg: true,
      page: page.value,
      pageSize: pageSize.value,
    })
    list.value = result.list ?? []
    total.value = result.total ?? 0
  } catch (error) {
    list.value = []
    total.value = 0
    errorMessage.value = (error as Error).message || '人员列表加载失败'
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
  filters.orgId = ''
  filters.status = ''
  page.value = 1
  void load()
}

/**
 * 组织筛选：`/orgs/selector` 返回数据域内可见节点的树（后端不支持关键字），
 * 这里一次取回、扁平化后用 el-select 的本地 filterable 过滤（避免每次输入都打后端）。
 */
async function loadOrgOptions(): Promise<void> {
  orgFilterLoading.value = true
  try {
    orgFilterOptions.value = await fetchOrgSelector({ includeDisabled: false })
  } catch {
    orgFilterOptions.value = []
  } finally {
    orgFilterLoading.value = false
  }
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
  if (!orgStore.hasTree) void orgStore.loadTree()
  void loadOrgOptions()
  void load()
})

// ---------------------------------------------------------------------------
// 新增 / 编辑
// ---------------------------------------------------------------------------
const editDialog = reactive({
  visible: false,
  mode: 'edit' as 'create' | 'edit',
  userId: '',
  account: '',
  name: '',
  employeeNo: '',
  phone: '',
  email: '',
  /** 归属公司：服务端 create 必填（必须是公司/集团节点），由所选部门向上推导或手工选择 */
  companyId: '',
  deptId: '',
  status: 'active' as UserStatus,
  remark: '',
  submitting: false,
})

const editTitle = computed(() => (editDialog.mode === 'create' ? '新增人员' : '编辑人员'))

/** 归属公司候选：只保留「集团 / 公司」节点（部门、科室不能作为 company_id，E-USER-005） */
const companyTree = computed<OrgTreeNode[]>(() => {
  const filter = (list: OrgTreeNode[]): OrgTreeNode[] =>
    list
      .filter((node) => node.orgType === 'group' || node.orgType === 'company')
      .map((node) => ({
        ...node,
        children: node.children?.length ? filter(node.children) : undefined,
      }))
  return filter(orgStore.tree)
})

/**
 * 归属公司推导（E-USER-005 / E-USER-006）：从所选部门/科室沿 parentId 向上取**最近的**
 * 「公司」节点；集团本部人员则取集团节点。推导失败返回空串，交由用户手工选择。
 */
function deriveCompanyId(orgId: string): string {
  let cursor: OrgTreeNode | undefined = orgStore.flatMap[orgId]
  let guard = 0
  while (cursor && guard < 32) {
    if (cursor.orgType === 'company' || cursor.orgType === 'group') return cursor.id
    const parentId: string | null = cursor.parentId
    cursor = parentId ? orgStore.flatMap[parentId] : undefined
    guard += 1
  }
  return ''
}

/** 选部门后同步推导归属公司（用户手工改过就保留其选择） */
watch(
  () => editDialog.deptId,
  (deptId) => {
    if (!deptId) return
    const derived = deriveCompanyId(deptId)
    if (derived) editDialog.companyId = derived
  },
)

function openCreate(): void {
  Object.assign(editDialog, {
    visible: true,
    mode: 'create',
    userId: '',
    account: '',
    name: '',
    employeeNo: '',
    phone: '',
    email: '',
    companyId: '',
    deptId: '',
    status: 'active',
    remark: '',
    submitting: false,
  })
}

function openEdit(row: UserItem): void {
  Object.assign(editDialog, {
    visible: true,
    mode: 'edit',
    userId: row.userId,
    account: row.account,
    name: row.name,
    employeeNo: row.employeeNo,
    phone: row.phone ?? '',
    email: row.email ?? '',
    companyId: row.companyId || deriveCompanyId(row.deptId ?? ''),
    deptId: row.deptId ?? '',
    status: row.status,
    remark: row.remark ?? '',
    submitting: false,
  })
}

/** 即时提示（服务端才是权威）：账号唯一性、工号唯一性前端无法判定 */
function validateEdit(): string {
  // 归属公司是服务端 @NotNull（必须是公司/集团节点，E-USER-005）
  if (!editDialog.companyId) return '请选择所属公司（公司或集团节点）'
  const problems = [
    checkUserName(editDialog.name),
    checkEmployeeNo(editDialog.employeeNo),
    checkPhone(editDialog.phone),
    checkEmail(editDialog.email),
    checkRemark(editDialog.remark),
    // 账号只在新建时可填：格式 8–64 位、字母开头（E-USER-001）
    editDialog.mode === 'create' ? checkAccount(editDialog.account) : '',
  ]
  return problems.find((item) => item !== '') ?? ''
}

async function submitEdit(): Promise<void> {
  const problem = validateEdit()
  if (problem) {
    ElMessage({ type: 'warning', message: problem })
    return
  }
  editDialog.submitting = true
  try {
    if (editDialog.mode === 'create') {
      await createUser({
        account: editDialog.account.trim(),
        employeeNo: editDialog.employeeNo.trim(),
        name: editDialog.name.trim(),
        phone: editDialog.phone.trim(),
        email: editDialog.email.trim() || undefined,
        // 服务端 @NotNull：必须是公司/集团节点（E-USER-005）
        companyId: editDialog.companyId,
        deptId: editDialog.deptId || null,
        status: editDialog.status,
        remark: editDialog.remark.trim() || undefined,
      })
      ElMessage({ type: 'success', message: '人员已新增（首次登录须强制修改初始口令）' })
    } else {
      await updateUser(editDialog.userId, {
        name: editDialog.name.trim(),
        employeeNo: editDialog.employeeNo.trim(),
        phone: editDialog.phone.trim() || undefined,
        email: editDialog.email.trim() || undefined,
        companyId: editDialog.companyId || undefined,
        deptId: editDialog.deptId || null,
        // 状态：服务端 PUT 支持 active/disabled（离职只能走「离职」操作）
        status: editDialog.status,
        remark: editDialog.remark,
      })
      ElMessage({ type: 'success', message: '人员已更新' })
    }
    editDialog.visible = false
    await load()
  } catch (error) {
    reportApiError(error)
  } finally {
    editDialog.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 岗位抽屉
// ---------------------------------------------------------------------------
const positionsVisible = ref(false)
const positionsUser = ref<UserItem | null>(null)

function openPositions(row: UserItem): void {
  positionsUser.value = row
  positionsVisible.value = true
}

// ---------------------------------------------------------------------------
// 角色分配抽屉（阶段 1 · 1.4）
// ---------------------------------------------------------------------------
const rolesVisible = ref(false)
const rolesUser = ref<UserItem | null>(null)

function openRoles(row: UserItem): void {
  rolesUser.value = row
  rolesVisible.value = true
}

// ---------------------------------------------------------------------------
// 离职 / 调岗 / 交接
// ---------------------------------------------------------------------------
type OpMode = 'resign' | 'transfer' | 'handover'

const OP_META: Record<OpMode, { title: string; action: string; consequence: string }> = {
  resign: {
    title: '员工离职',
    action: '离职',
    consequence: '在途单据仍由原快照审批人处理，不会自动改派（PRD 5.4）',
  },
  transfer: {
    title: '员工调岗',
    action: '调岗',
    consequence: '在途单据仍由原快照审批人处理；调岗只影响后续新发起单据的数据域可见性',
  },
  handover: {
    title: '待办交接（转办）',
    action: '交接',
    consequence: '转办后原任务关闭，接收人成为该节点处理人；轨迹保留转办记录',
  },
}

const op = reactive({
  visible: false,
  mode: 'resign' as OpMode,
  submitting: false,
  row: null as UserItem | null,
  reason: '',
  targetOrgId: '',
  targetPositionName: '',
  isPrimary: false,
  handoverToUserId: '',
  count: 0,
  /** 在途单据数（服务端 inFlightInstanceCount；与待办数分开展示） */
  inFlightCount: 0,
  items: [] as ImpactItem[],
  message: '',
  /** 影响清单是否已取到（false = 走降级路径，只用列表行上的待办数） */
  impactLoaded: false,
})

const directoryOptions = ref<DirectoryItem[]>([])
const directoryLoading = ref(false)

const opMeta = computed(() => OP_META[op.mode])

/** 交接接收人候选（通讯录，手机号脱敏） */
async function searchDirectory(keyword: string): Promise<void> {
  directoryLoading.value = true
  try {
    const result = await fetchDirectory({ keyword, page: 1, pageSize: 20 })
    directoryOptions.value = result.list ?? []
  } catch {
    directoryOptions.value = []
  } finally {
    directoryLoading.value = false
  }
}

/**
 * 默认阻断判定（见文件头口径）：
 *   · 离职：名下待办非零即阻断（AC-12 的硬要求）
 *   · 调岗：待办或在途单据非零即阻断（服务端 transfer 同在途口径）
 *   · 交接：本身就是补救动作，不阻断
 * 例外：离职/调岗时若已选定「待办交接给」的接收人，则已满足 AC-12 的放行条件，不再阻断。
 */
const opBlocked = computed(() => {
  if (op.mode === 'handover') return false
  if (op.handoverToUserId) return false
  if (op.mode === 'resign') return op.count > 0
  return op.count > 0 || op.inFlightCount > 0 || (!op.impactLoaded && op.count > 0)
})

/** 影响清单文案：离职沿用 import-spec §8.1 的规范提示文案 */
const opMessage = computed(() => {
  if (op.message) return op.message
  const row = op.row
  if (!row) return ''
  const bizNos = op.items.map((item) => item.bizNo).join('、')
  const inFlight = op.inFlightCount > 0 ? `；另涉及 ${op.inFlightCount} 张在途单据` : ''
  if (op.mode === 'resign') {
    if (op.count > 0) {
      return `离职前必须清空名下待办：${row.name}（${row.account}）名下仍有 ${op.count} 条未处理待办，请先转办或改派${inFlight}${bizNos ? `；涉及单号：${bizNos}` : ''}。`
    }
    return `「${row.name}（${row.account}）」名下当前无未处理待办，可正常办理离职。离职后该账号不可登录，历史单据保留。`
  }
  if (op.mode === 'transfer') {
    if (op.count > 0 || op.inFlightCount > 0) {
      return `调岗会影响数据域可见性与后续发起归属：${row.name}（${row.account}）名下有 ${op.count} 条在途/待办记录${bizNos ? `（涉及单号：${bizNos}）` : ''}${inFlight}。审批人已快照，在途单据不会自动改派（PRD 5.4）。`
    }
    return `「${row.name}（${row.account}）」当前无在途/待办记录，可正常调岗。`
  }
  return `将「${row.name}（${row.account}）」名下未处理待办转办给接收人；转办后原任务关闭，接收人成为该节点处理人。`
})

async function openOp(mode: OpMode, row: UserItem): Promise<void> {
  op.visible = true
  op.mode = mode
  op.row = row
  op.reason = ''
  op.targetOrgId = row.deptId ?? ''
  op.targetPositionName = ''
  op.isPrimary = true
  op.handoverToUserId = ''
  // 列表行已带服务端下发的待办数（UserView.pendingTaskCount，恒有值）
  op.count = row.pendingTaskCount
  op.inFlightCount = 0
  op.items = []
  op.message = ''
  op.impactLoaded = false

  // 交接接收人候选：先给一页默认结果，之后由输入触发远程检索
  await searchDirectory('')

  // 影响清单：`GET /users/{id}/in-flight-check`（待办数 + 在途数 + 单据类型/发起人/当前节点）
  try {
    const impact: UserImpact = await fetchUserImpact(row.userId)
    op.count = impact.pendingTaskCount
    op.inFlightCount = impact.inFlightInstanceCount
    op.items = impact.items ?? []
    op.message = impact.message || ''
    op.impactLoaded = true
  } catch {
    op.impactLoaded = false
  }
}

/**
 * 「待办交接给」是前端编排：后端 `resign`/`transfer` 请求体里没有 handoverToUserId，
 * 因此先调 `POST /users/{id}/handover` 把待办转出去（AC-12 的放行条件），再提交离职/调岗。
 */
async function handoverFirst(row: UserItem, reason: string): Promise<void> {
  if (!op.handoverToUserId) return
  await handoverUser(row.userId, {
    toUserId: op.handoverToUserId,
    reason: reason || '离职/调岗前转办名下待办',
  })
}

async function submitOp(payload: { reason: string; force: boolean }): Promise<void> {
  const row = op.row
  if (!row) return

  if (op.mode === 'transfer' && !op.targetOrgId) {
    ElMessage({ type: 'warning', message: '请选择目标组织' })
    return
  }
  if (op.mode === 'handover' && !op.handoverToUserId) {
    ElMessage({ type: 'warning', message: '请选择接收人' })
    return
  }
  if (op.mode !== 'handover' && op.count > 0 && !op.handoverToUserId && !payload.force) {
    ElMessage({
      type: 'warning',
      message: '名下仍有未处理待办：请选择「待办交接给」的接收人，或由系统管理员填写原因后强制继续',
    })
    return
  }

  op.submitting = true
  try {
    if (op.mode === 'resign') {
      await handoverFirst(row, payload.reason)
      await resignUser(row.userId, {
        reason: payload.reason,
        // 强制继续：服务端按 force + reason + 系统管理员放行待办阻断并写审计（AC-52）
        force: payload.force,
      })
      ElMessage({
        type: 'success',
        message: payload.force
          ? `已强制为「${row.name}」办理离职（待办阻断已放行，原因已写入审计日志）`
          : `已为「${row.name}」办理离职`,
      })
    } else if (op.mode === 'transfer') {
      await handoverFirst(row, payload.reason)
      await transferUser(row.userId, {
        targetOrgId: op.targetOrgId,
        keepOtherPositions: true,
        reason: payload.reason,
        force: payload.force,
      })
      // 目标岗位不在 transfer 请求体里：调岗成功后单独落一条岗位记录（失败不回滚调岗，只提示）
      const postName = op.targetPositionName.trim()
      if (postName) {
        try {
          await createUserPosition(row.userId, {
            orgId: op.targetOrgId,
            postName,
            isPrimary: op.isPrimary,
          })
        } catch (error) {
          reportApiError(error, '目标岗位登记')
        }
      }
      ElMessage({ type: 'success', message: `已为「${row.name}」办理调岗` })
    } else {
      await handoverUser(row.userId, {
        toUserId: op.handoverToUserId,
        reason: payload.reason,
      })
      ElMessage({ type: 'success', message: `「${row.name}」的待办已转办` })
    }
    op.visible = false
    await load()
  } catch (error) {
    reportApiError(error)
  } finally {
    op.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 导出（仅系统管理员，import-spec §9.2 T-11）
// ---------------------------------------------------------------------------
const exporting = ref(false)

/**
 * 导出（仅系统管理员，import-spec §9.2 T-11）：
 * `GET /identity/users/export` 返回 CSV（UTF-8 BOM，九列与 user.csv 模板一致），
 * 文件名取响应头 `Content-Disposition`。
 */
async function doExport(): Promise<void> {
  exporting.value = true
  try {
    const { blob, filename } = await exportUsers({
      keyword: filters.keyword.trim() || undefined,
      orgId: filters.orgId || undefined,
      status: filters.status || undefined,
      includeSubOrg: true,
    })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename
    anchor.click()
    URL.revokeObjectURL(url)
    ElMessage({
      type: 'success',
      message: `导出已开始（${filename}）：列与 user.csv 模板一致，可直接往返再导入（import-spec §9.1）`,
    })
  } catch (error) {
    reportApiError(error, '导出')
  } finally {
    exporting.value = false
  }
}

// ---------------------------------------------------------------------------
// 展示辅助
// ---------------------------------------------------------------------------
/** 手机号：服务端已按角色脱敏，这里只兜底（PRD 5.3） */
function phoneText(row: UserItem): string {
  return row.phoneMasked || row.phone || '—'
}

/** 有未处理待办的行必须在列表上可见（AC-12 的前提）：服务端 UserView.pendingTaskCount 恒有值 */
function pendingText(row: UserItem): string {
  return String(row.pendingTaskCount)
}

/** el-table 作用域插槽的 row 是 DefaultRow；进出业务函数前统一收窄（不用 any） */
function asUser(row: unknown): UserItem {
  return row as UserItem
}

function phoneCell(row: unknown): string {
  return phoneText(asUser(row))
}

function pendingCell(row: unknown): string {
  return pendingText(asUser(row))
}
</script>

<template>
  <div class="oa-user-page">
    <header class="page-head">
      <h1 class="oa-text-title-page">人员管理</h1>
      <span class="oa-text-caption oa-text-subtle">
        一人可挂多组织；主岗唯一；离职前必须清空名下待办（AC-12）
      </span>
      <div class="head-actions">
        <!-- 导出入口仅系统管理员可见（import-spec §9.2） -->
        <el-button v-if="showExport" :loading="exporting" @click="doExport">导出</el-button>
        <el-button v-if="editable" type="primary" @click="openCreate">新增人员</el-button>
      </div>
    </header>

    <!-- 筛选条：关键字 + 组织 + 状态 -->
    <div class="oa-card filter-bar">
      <el-input
        v-model="filters.keyword"
        class="kw"
        clearable
        placeholder="关键字：姓名 / 工号 / 账号"
        @keyup.enter="search"
      />

      <el-select
        v-model="filters.orgId"
        class="org-filter"
        filterable
        clearable
        :loading="orgFilterLoading"
        placeholder="所属组织（含子树）"
      >
        <el-option v-for="item in orgFilterOptions" :key="item.id" :value="item.id" :label="item.path" />
      </el-select>

      <el-select v-model="filters.status" class="status-filter" clearable placeholder="在岗状态">
        <el-option
          v-for="item in USER_STATUS_OPTIONS"
          :key="item.value"
          :value="item.value"
          :label="item.label"
        />
      </el-select>

      <el-button @click="search">查询</el-button>
      <el-button :disabled="emptyValue" @click="resetFilters">重置</el-button>

      <span class="oa-text-caption oa-text-subtle count">
        共 <b class="oa-tnum">{{ total }}</b> 人
      </span>
    </div>

    <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />

    <el-table v-loading="loading" :data="list" border size="default">
      <el-table-column label="姓名 / 账号" min-width="180" fixed="left">
        <template #default="{ row }">
          <b class="oa-text-body-sm">{{ row.name }}</b>
          <span class="oa-text-caption oa-text-subtle sub-line">{{ row.account }}</span>
        </template>
      </el-table-column>
      <el-table-column label="工号" width="110">
        <template #default="{ row }">
          <span class="oa-tnum">{{ row.employeeNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="companyName" label="所属公司" min-width="140" />
      <el-table-column label="所属部门 / 科室" min-width="150">
        <template #default="{ row }">{{ row.deptName || '—' }}</template>
      </el-table-column>
      <el-table-column label="主岗职务" min-width="130">
        <template #default="{ row }">{{ row.positionName || '—' }}</template>
      </el-table-column>
      <el-table-column label="手机号" width="130">
        <template #default="{ row }">
          <span class="oa-tnum">{{ phoneCell(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <StatusPill kind="user" :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="未处理待办" width="110">
        <template #default="{ row }">
          <span class="oa-tnum" :class="{ 'is-danger': (row.pendingTaskCount ?? 0) > 0 }">
            {{ pendingCell(row) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="editable" label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(asUser(row))">编辑</el-button>
          <el-button link type="primary" @click="openPositions(asUser(row))">岗位管理</el-button>
          <!-- 角色分配（1.4）：能维护人员即可进入；集团级角色在抽屉内按分级可见性过滤 -->
          <el-button v-if="canAssignRole" link type="primary" @click="openRoles(asUser(row))">
            角色
          </el-button>
          <el-dropdown trigger="click">
            <el-button link type="primary">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openOp('resign', asUser(row))">离职</el-dropdown-item>
                <el-dropdown-item @click="openOp('transfer', asUser(row))">调岗</el-dropdown-item>
                <el-dropdown-item @click="openOp('handover', asUser(row))">交接（转办待办）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      <template #empty>
        <div class="oa-empty">
          <p>没有符合条件的人员</p>
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

  <!-- ==================== 新增 / 编辑 ==================== -->
  <el-dialog v-model="editDialog.visible" :title="editTitle" width="720px" append-to-body>
    <el-form label-position="top" class="edit-form">
      <el-form-item label="姓名" required>
        <el-input v-model="editDialog.name" maxlength="50" show-word-limit placeholder="不超过 50 字" />
      </el-form-item>

      <el-form-item
        :label="`账号${editDialog.mode === 'edit' ? '（登录名，创建后不可改）' : ''}`"
        :required="editDialog.mode === 'create'"
      >
        <el-input
          v-model="editDialog.account"
          :disabled="editDialog.mode === 'edit'"
          placeholder="8–64 位，字母开头，仅字母/数字/下划线/点/连字符"
        />
        <p class="hint">唯一性由服务端裁决（E-USER-002）；首次登录强制修改初始口令。</p>
      </el-form-item>

      <el-form-item label="工号" required>
        <el-input v-model="editDialog.employeeNo" placeholder="1–32 位，仅字母/数字/连字符，如 A0001" />
        <p class="hint">
          工号与水印取「姓名 + 工号」（REQ-USER-004 / AC-44）；唯一性由服务端裁决（E-USER-015）。
        </p>
      </el-form-item>

      <el-form-item label="手机号" required>
        <el-input v-model="editDialog.phone" :placeholder="RULE_HINT.phone" />
        <p class="hint">
          手机号加密存储；通讯录默认脱敏（138****8888），仅本人与系统管理员可见完整值（PRD 5.3）。
        </p>
      </el-form-item>

      <el-form-item label="邮箱">
        <el-input v-model="editDialog.email" placeholder="选填，不超过 128 字符" />
      </el-form-item>

      <el-form-item label="所属公司" required>
        <el-tree-select
          v-model="editDialog.companyId"
          :data="companyTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          filterable
          clearable
          class="fill"
          placeholder="归属公司路径节点：公司或集团（集团本部人员选集团，E-USER-005）"
        />
        <p class="hint">
          服务端必填：类型必须是「公司」或「集团」；选择部门后自动推导为最近的上级公司，可手工调整。
        </p>
      </el-form-item>

      <el-form-item label="所属部门 / 科室">
        <el-tree-select
          v-model="editDialog.deptId"
          :data="orgStore.activeTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          filterable
          clearable
          class="fill"
          placeholder="仅可选启用中的组织节点（停用节点不得作为人员归属）"
        />
        <p class="hint">主归属部门/科室必须落在所选公司子树内（E-USER-006 / E-USER-011）。</p>
      </el-form-item>

      <el-form-item v-if="editDialog.mode === 'create'" label="在岗状态" required>
        <el-select v-model="editDialog.status" class="fill">
          <el-option
            v-for="item in USER_STATUS_OPTIONS"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-else label="在岗状态">
        <el-select
          v-if="editDialog.status !== 'resigned'"
          v-model="editDialog.status"
          class="fill"
        >
          <el-option
            v-for="item in editStatusOptions"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </el-select>
        <span v-else class="readonly-state">
          <StatusPill kind="user" :status="editDialog.status" />
        </span>
        <p class="hint">
          状态走「修改人员」接口，只接受「在职 ⇄ 停用」（离职不可回改，服务端 409）；
          切到停用与离职同一口径：名下有在途/待办时服务端会拒绝（返回数量与单号），需先转办或改派。
          离职请用列表行的「离职」操作（AC-12）。
        </p>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="editDialog.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="editDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="editDialog.submitting" @click="submitEdit">
        {{ editDialog.mode === 'create' ? '确认新增' : '确认保存' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- ==================== 离职 / 调岗 / 交接（危险操作 + 影响清单） ==================== -->
  <ImpactConfirmDialog
    v-model="op.visible"
    :title="opMeta.title"
    :action-label="opMeta.action"
    :target="op.row ? `${op.row.name}（${op.row.account}）` : ''"
    :message="opMessage"
    :blocked="opBlocked"
    :count="op.inFlightCount"
    :pending-count="op.count"
    :items="op.items"
    :can-force="canForce"
    :reason-required="true"
    :submitting="op.submitting"
    :consequence="opMeta.consequence"
    @confirm="submitOp"
  >
    <el-form label-position="top">
      <el-form-item v-if="op.mode === 'transfer'" label="目标组织" required>
        <el-tree-select
          v-model="op.targetOrgId"
          :data="orgStore.activeTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          :render-after-expand="false"
          filterable
          class="fill"
          placeholder="仅可选启用中的组织节点"
        />
      </el-form-item>

      <el-form-item v-if="op.mode === 'transfer'" label="目标岗位名称">
        <el-input
          v-model="op.targetPositionName"
          maxlength="50"
          placeholder="选填；调岗成功后按此名称新增一条岗位记录"
        />
        <p class="hint">
          调岗接口的请求体不含岗位字段：本页会在调岗成功后调
          「新增岗位」接口落库（失败只提示，不回滚调岗）。
        </p>
      </el-form-item>

      <el-form-item v-if="op.mode === 'transfer'" label="设为主岗">
        <el-switch v-model="op.isPrimary" />
        <span class="hint inline">勾选后原主岗自动置否（E-POS-005）</span>
      </el-form-item>

      <el-form-item
        v-if="op.mode === 'resign' || op.mode === 'transfer'"
        label="待办交接给（先转办再离职 / 调岗）"
      >
        <el-select
          v-model="op.handoverToUserId"
          filterable
          remote
          clearable
          :remote-method="searchDirectory"
          :loading="directoryLoading"
          class="fill"
          placeholder="按姓名 / 工号检索接收人（留空则不转办）"
        >
          <el-option
            v-for="item in directoryOptions"
            :key="item.userId"
            :value="item.userId"
            :label="`${item.name}（${item.employeeNo}）`"
            :disabled="item.userId === op.row?.userId"
          >
            <span>{{ item.name }}</span>
            <span class="oa-text-caption oa-text-subtle"> {{ item.orgName }} · {{ item.employeeNo }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item v-if="op.mode === 'handover'" label="转办给" required>
        <el-select
          v-model="op.handoverToUserId"
          filterable
          remote
          :remote-method="searchDirectory"
          :loading="directoryLoading"
          class="fill"
          placeholder="按姓名 / 工号检索接收人"
        >
          <el-option
            v-for="item in directoryOptions"
            :key="item.userId"
            :value="item.userId"
            :label="`${item.name}（${item.employeeNo}）`"
            :disabled="item.userId === op.row?.userId"
          />
        </el-select>
      </el-form-item>
    </el-form>
  </ImpactConfirmDialog>

  <!-- ==================== 岗位抽屉 ==================== -->
  <UserPositionsDrawer v-model="positionsVisible" :user="positionsUser" @changed="load" />

  <!-- ==================== 角色分配抽屉（阶段 1 · 1.4） ==================== -->
  <UserRolesDrawer v-model="rolesVisible" :user="rolesUser" @changed="load" />
</template>

<style scoped>
.oa-user-page {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-sm);
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
  width: 260px;
}

.org-filter {
  width: 240px;
}

.status-filter {
  width: 140px;
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

/* 待办非零必须一眼可见（AC-12 的前提） */
.is-danger {
  color: var(--oa-color-error);
  font-weight: 500;
}

.pager {
  justify-content: flex-end;
}

.edit-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--oa-space-lg);
}

.readonly-state {
  display: inline-flex;
  align-items: center;
  min-height: var(--oa-space-control);
}

.hint {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.hint.inline {
  margin-left: var(--oa-space-xs);
}

.fill {
  width: 100%;
}

@media (max-width: 768px) {
  .edit-form {
    grid-template-columns: minmax(0, 1fr);
  }

  .kw,
  .org-filter,
  .status-filter {
    width: 100%;
  }
}
</style>
