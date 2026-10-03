/**
 * oa-web · 审批动作与任务列表纯逻辑（无 Vue / 无 DOM —— 可直接用 Node 跑断言自测）
 * ----------------------------------------------------------------------------
 * 分工：本文件只做**判定与归一**，不发请求、不碰组件状态。它回答两个问题：
 *   ① 某个动作**该不该出现**、不可用时**原因是什么**（任务书 B 段：「不可用的要说明原因，
 *      而不是点了报错」）；
 *   ② 意见/原因在**提交前**是否满足服务端口径（驳回 ≥5 字等）。
 *
 * ⚠ 本文件**不是鉴权**：服务端（`FlowTaskController` 的 `@PreAuthorize` 动作码 +
 *   `FlowEngineService` 的身份/状态机/闸门）才是裁决方。这里的每一条判断都能在
 *   真源里找到出处，且**只用于渲染**。
 *
 * 真源：
 *   · `oa-server` `com.oa.workflow.runtime.domain.FlowAction`（动作码 / 权限码 / 必填原因 / 意见下限）
 *   · `GET /flow-actions`（上面那个枚举的接口投影，前端**以它为准**而不是硬编码）
 *   · `doc/enums.md` §8（三层状态）、§9（**17 值**轨迹动作，2026-10-04 由 16 值加入
 *     `return_register` 归还登记）
 *   · `doc/prd-0.1.md` §6.3/§6.4（主干链与流转回退）、§6.6（驳回/补件）、AC-49（终止）
 *   · `doc/prd-0.1.md` §6.3/§6.4（主干链与流转回退）、§6.6（驳回/补件）、AC-49（终止）
 *   · `doc/templates.md` §1.7（Q6/Q7 闸门）、§1.8（撤回窗口）、T-08（自由跳转一期全关）
 */
import type {
  ActionAvailability,
  ActionCatalogItem,
  FlowInstance,
  FlowRuntime,
  FlowSnapshotNode,
  FlowTaskListItem,
} from '@/types/flow-task'

// ================================================================ 状态文案

/** 流程实例状态（`doc/enums.md` §8 第一层：实例状态） */
export const INSTANCE_STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  approving: '审批中',
  rejected: '已驳回',
  approved: '已通过',
  terminated: '已终止',
  withdrawn: '已撤回',
}

/** 任务状态（`doc/enums.md` §8 第三层：任务状态） */
export const TASK_STATUS_LABEL: Record<string, string> = {
  pending: '待处理',
  agreed: '已同意',
  rejected: '已驳回',
  rolled_back: '已回退',
  routed: '已流转',
  added_sign: '已加签',
  transferred: '已转办',
  reassigned: '已改派',
  supplement_requested: '已请求补件',
  auto_closed: '自动关闭',
  cancelled: '已取消',
}

/** 节点实例状态 */
export const NODE_STATUS_LABEL: Record<string, string> = {
  pending: '待激活',
  active: '审批中',
  approved: '已通过',
  rejected: '已驳回',
  returned: '已回退',
  waiting_supplement: '待补件',
  skipped: '已跳过',
  cancelled: '已取消',
}

/** 实例子状态（`doc/enums.md` §8 第二层） */
export const SUB_STATUS_LABEL: Record<string, string> = {
  pending_supplement: '待补件',
}

/**
 * 轨迹动作的中文名（`doc/enums.md` §9 的 **17 值**，与后端 `RuntimeEnums.ThreadAction` 同源）。
 *
 * 用途：`ThreadView.actionLabel` 缺失时的**兜底文案**。优先仍用服务端下发的 `actionLabel`
 * （单一真源）；本表只在字段缺失/未知动作时兜底，未知动作**原样回显动作码**（不猜中文）。
 */
export const THREAD_ACTION_LABEL: Record<string, string> = {
  submit: '提交',
  approve: '通过',
  reject: '驳回',
  route: '流转',
  rollback: '回退上一节点',
  back_home: '回到本部门',
  supplement_request: '请求补件',
  supplement_submit: '提交补件',
  transfer: '转办',
  reassign: '改派',
  add_sign: '加签',
  withdraw: '撤回',
  terminate: '终止',
  skip: '跳过',
  /** 印鉴单归还登记（AC-28 三态例外的轨迹动作；**不要**显示成「归档登记」） */
  return_register: '归还登记',
  archive_register: '归档登记',
  cc: '抄送',
}

/**
 * 轨迹动作的**展示顺序**（17 值；与后端枚举声明顺序**刻意不同**）。
 *
 * 为什么把 `return_register` 放在 `archive_register` **之前**：归还登记是 AC-28 三态例外
 * 触发的动作，语义上发生在归档登记之前（印鉴单先归还、再由 ⑦ 归档登记）；
 * 后端 `ThreadAction` 的声明顺序里 `RETURN_REGISTER` 在 `ARCHIVE_REGISTER` 之后，
 * 那是**新增枚举值的追加顺序**，不是业务时序。图例与筛选按本顺序渲染。
 */
export const THREAD_ACTION_ORDER: readonly string[] = [
  'submit',
  'approve',
  'reject',
  'rollback',
  'route',
  'back_home',
  'skip',
  'add_sign',
  'transfer',
  'reassign',
  'supplement_request',
  'supplement_submit',
  'withdraw',
  'terminate',
  'return_register',
  'archive_register',
  'cc',
]

/** 轨迹动作的中文名（服务端 `actionLabel` 优先；未知动作原样回显动作码） */
export function threadActionLabel(action: string, fallback?: string | null): string {
  if (fallback && fallback.trim() !== '') return fallback
  return THREAD_ACTION_LABEL[action] ?? action
}

/** 轨迹动作在展示顺序里的位次（未知动作排在最后） */
export function threadActionRank(action: string): number {
  const index = THREAD_ACTION_ORDER.indexOf(action)
  return index < 0 ? THREAD_ACTION_ORDER.length : index
}

/** 实例状态的中文名（未知状态**原样回显**，不猜） */
export function instanceStatusLabel(status: string | null | undefined): string {
  if (!status) return '未知'
  return INSTANCE_STATUS_LABEL[status] ?? status
}

/** 任务状态的中文名（优先用服务端下发的 `taskStatusLabel`） */
export function taskStatusLabel(status: string | null | undefined, fallback?: string | null): string {
  if (fallback && fallback.trim() !== '') return fallback
  if (!status) return '未知'
  return TASK_STATUS_LABEL[status] ?? status
}

/** 子状态的中文名 */
export function subStatusLabel(subStatus: string | null | undefined): string {
  if (!subStatus) return ''
  return SUB_STATUS_LABEL[subStatus] ?? subStatus
}

/** 实例状态 → 既有 `statusPillClass`（`utils/status.ts`）可用的单据状态码 */
export function instancePillStatus(status: string | null | undefined): string {
  switch (status) {
    case 'draft':
      return 'draft'
    case 'approving':
      return 'processing'
    case 'approved':
      return 'approved'
    case 'rejected':
      return 'rejected'
    case 'terminated':
    case 'withdrawn':
      return 'closed'
    default:
      return status ?? 'closed'
  }
}

// ================================================================ 意见 / 原因校验

/**
 * 各动作的原因/意见**长度上限**（后端 `RuntimeRequests` 的 `@Size`）：
 *   · 通过 / 驳回 / 归档登记 → 1000（`opinion`）；
 *   · 其余必填原因类动作 → 255（`reason`）。
 * 缺省 255：新增动作若未被这里收录，取更严的一档，宁可前端先拦也不让用户白填。
 */
export const ACTION_REASON_MAX: Record<string, number> = {
  approve: 1000,
  reject: 1000,
  archive_register: 1000,
}

/** 原因/原因字数上限 */
export function reasonMaxLength(action: string): number {
  return ACTION_REASON_MAX[action] ?? 255
}

/** 意见校验结论 */
export interface OpinionCheck {
  ok: boolean
  message: string
  /** 当前字数（`trim()` 后） */
  length: number
  max: number
  min: number
}

/**
 * 意见 / 原因的**前端**校验（服务端仍会再判一次，见 `ApprovalOpinionPolicy`）。
 *
 * 逐条口径：
 *   · `requiresReason=true` 且空白 → 拒绝（后端 40010 `FLOW_REASON_REQUIRED`）；
 *   · `reject`：`trim()` 后 **≥5 字**（后端 40009 `FLOW_OPINION_TOO_SHORT`，AC-50）；
 *   · 超过动作上限 → 拒绝（后端 40001 参数校验失败）。
 *
 * ⚠ 字数按 **`trim()` 后的字符数**（`Array.from` 计码点，避免 emoji 被算成 2）。
 */
export function checkOpinion(
  action: string,
  text: string,
  requiresReason: boolean,
  minOpinionChars = 0,
): OpinionCheck {
  const trimmed = (text ?? '').trim()
  const length = Array.from(trimmed).length
  const max = reasonMaxLength(action)
  const min = Math.max(minOpinionChars, requiresReason ? 1 : 0)
  if (length === 0 && requiresReason) {
    return { ok: false, message: '该动作必须填写原因/意见（后端 40010）', length, max, min }
  }
  if (minOpinionChars > 0 && length > 0 && length < minOpinionChars) {
    return {
      ok: false,
      message: `驳回意见至少 ${minOpinionChars} 个字，当前 ${length} 个字（后端 40009）`,
      length,
      max,
      min,
    }
  }
  if (length > max) {
    return { ok: false, message: `意见/原因不得超过 ${max} 字，当前 ${length} 字`, length, max, min }
  }
  return { ok: true, message: '', length, max, min }
}

// ================================================================ 权限

/** 权限判定的最小只读投影（便于脱离 Pinia 自测） */
export interface ActionPermissionSubject {
  readonly isSuperAdmin: boolean
  readonly permissions: readonly string[]
  readonly roleCodes: readonly string[]
}

/** 权限码是否命中（系统管理员一律命中） */
export function permissionHeld(subject: ActionPermissionSubject, permission: string): boolean {
  if (subject.isSuperAdmin) return true
  return subject.permissions.includes(permission)
}

/** AC-49「终止」主体：系统管理员 ∪ `group_leader` 角色 ∪ `flow:task:terminate` 权限（并集） */
export function isTerminateSubject(subject: ActionPermissionSubject): boolean {
  if (subject.isSuperAdmin) return true
  if (subject.roleCodes.includes('group_leader')) return true
  return subject.permissions.includes('flow:task:terminate')
}

// ================================================================ 可用性判定

/** 任务级动作（需要「分配给我的待处理任务」；实例级动作不需要） */
export const TASK_SCOPED_ACTIONS: readonly string[] = [
  'approve',
  'reject',
  'rollback',
  'route',
  'back_home',
  'jump',
  'add_sign',
  'transfer',
  'reassign',
  'supplement_request',
  'archive_register',
]

/** 危险动作（界面用 ghost 红字，避免误触实心红按钮） */
export const DANGER_ACTIONS: readonly string[] = ['reject', 'terminate']

/** 节点⑦（归档登记）节点码 —— `doc/enums.md` §2 */
export const ARCHIVE_NODE_CODE = 'archive_register'

/** 可用性判定的输入 */
export interface ActionAvailabilityInput {
  /** `GET /flow-actions` 的动作面清单（**服务端真源**，不可得时传空数组并如实提示） */
  catalog: readonly ActionCatalogItem[]
  subject: ActionPermissionSubject
  /** 当前登录人 id（与 `task.assigneeId` / `instance.initiatorId` 比较） */
  currentUserId: string
  instance: Pick<FlowInstance, 'status' | 'subStatus' | 'initiatorId' | 'currentNodeSeq'>
  /** 运行态（取「我的待处理任务」与节点实例）；未加载时传 `null` */
  runtime: Pick<FlowRuntime, 'tasks' | 'nodes' | 'hasOpenAddSign'> | null
  /**
   * **实例锁定版本**的快照节点（`GET /flow-instances/{id}` 的 `snapshot.nodes`）。
   *
   * ⚠ 节点开关 `allowAddSign` / `allowRoute` / `allowJump` **只在快照里**
   * （`RuntimeDtos.NodeInstanceView` 不含这三个键，见 `types/flow-task-wire.d.ts`）——
   * 从运行态节点实例上取会取到 `undefined`，把「节点开关关闭」误判成「可用」。
   */
  snapshotNodes: readonly FlowSnapshotNode[]
}

/** 我在这张单上的待处理任务（含所处节点码） */
export interface MyPendingTask {
  taskId: string
  nodeSeq: number | null
  nodeName: string
  nodeCode: string
  addSignType: string
}

/**
 * 找出「我在这张单上的待处理任务」。
 *
 * 为什么不用 `GET /flow-tasks/todo` 过滤：那个接口是**全局分页**列表，
 * 详情页按页扫描既慢又可能漏（待办数超过一页）。`GET /flow-instances/{id}/task-list`
 * 直接给出该单全部任务，且 `assigneeId` 可与当前登录人比对 —— 一次请求定论。
 */
export function findMyPendingTask(input: ActionAvailabilityInput): MyPendingTask | null {
  const runtime = input.runtime
  if (!runtime) return null
  const nodeByInstanceId = new Map(runtime.nodes.map((node) => [node.id, node]))
  for (const task of runtime.tasks) {
    if (task.status !== 'pending') continue
    if (task.assigneeId !== input.currentUserId) continue
    const node = nodeByInstanceId.get(task.nodeInstanceId)
    return {
      taskId: task.id,
      nodeSeq: task.nodeSeq ?? node?.nodeSeq ?? null,
      nodeName: task.nodeName ?? node?.nodeName ?? '',
      nodeCode: node?.nodeCode ?? snapshotNodeCodeAt(input.snapshotNodes, task.nodeSeq ?? null),
      addSignType: task.addSignType ?? '',
    }
  }
  return null
}

/** 按序号在快照里取节点码（运行态节点实例缺失时的兜底） */
function snapshotNodeCodeAt(nodes: readonly FlowSnapshotNode[], seq: number | null): string {
  if (seq === null) return ''
  return nodes.find((node) => node.nodeSeq === seq)?.nodeCode ?? ''
}

/**
 * 当前活动节点的**运行态实例**（`GET /flow-instances/{id}/runtime` 的 `nodes[]`）。
 *
 * 与 {@link currentNodeOf} 分工明确：
 *   · 快照节点（`currentNodeOf`）给**配置**：`allowAddSign` / `allowRoute` / `allowJump` / 决议模式；
 *   · 运行态节点（本函数）给**进度**：`status` / `supplementRequested` / `returnedCount`。
 * 两者按键 `nodeSeq` 对齐，不可互相替代（`NodeInstanceView` 不含节点开关，
 * `SnapshotNode` 不含运行进度）。
 */
export function currentRuntimeNodeOf(
  input: ActionAvailabilityInput,
  myTask: MyPendingTask | null,
): FlowRuntime['nodes'][number] | null {
  const runtime = input.runtime
  if (!runtime) return null
  const seq = input.instance.currentNodeSeq
  if (seq !== null && seq !== undefined) {
    const matched = runtime.nodes.find((node) => node.nodeSeq === seq)
    if (matched) return matched
  }
  if (myTask && myTask.nodeSeq !== null) {
    return runtime.nodes.find((node) => node.nodeSeq === myTask.nodeSeq) ?? null
  }
  return null
}

/**
 * 当前活动**节点**（快照，按实例 `currentNodeSeq` 匹配；缺失时退回我的任务所在节点）。
 *
 * 返回快照节点而不是运行态节点实例：节点开关（`allowAddSign` / `allowRoute` / `allowJump`）
 * 与决议模式只在这里，且是**运行时权威数据**（建实例时固化，改模板不影响在途单据）。
 * 运行进度请用 {@link currentRuntimeNodeOf}。
 */
export function currentNodeOf(
  input: ActionAvailabilityInput,
  myTask: MyPendingTask | null,
): FlowSnapshotNode | null {
  const seq = input.instance.currentNodeSeq
  if (seq !== null && seq !== undefined) {
    const matched = input.snapshotNodes.find((node) => node.nodeSeq === seq)
    if (matched) return matched
  }
  if (myTask && myTask.nodeSeq !== null) {
    return input.snapshotNodes.find((node) => node.nodeSeq === myTask.nodeSeq) ?? null
  }
  return null
}

const ALLOW_SWITCH_REASON: Record<string, string> = {
  jump: '自由跳转在模板里默认关闭（templates.md T-08，一期全关）；当前节点 allowJump=false，服务端按 40910 拒绝',
  add_sign: '当前节点未开启加签（模板 allowAddSign=false），服务端按 40910 拒绝',
  route: '当前节点未开启流转（模板 allowRoute=false，默认仅 ②⑤⑥ 开启，templates.md T-02），服务端按 40910 拒绝',
  back_home: '当前节点未开启流转开关（模板 allowRoute=false），服务端按 40910 拒绝',
}

/**
 * 逐个动作算出**可用性与不可用原因**。
 *
 * 判定顺序（每一层都能在真源里找到出处）：
 *   ① `GET /flow-actions` 里有没有这个动作（没有 = 引擎不认识，不渲染）；
 *   ② 权限码是否命中（`FlowAction.permission()`；不命中 → 服务端 403/40301）；
 *   ③ 任务级动作需要「分配给我的待处理任务」（否则服务端按 40910 / 非任务本人拒绝）；
 *   ④ 节点开关（`allowRoute` / `allowAddSign` / `allowJump`，取自快照）；
 *   ⑤ 实例状态机（草稿才能提交、终态不能撤回/终止、待补件才能提交补件…）。
 */
export function resolveActionAvailability(input: ActionAvailabilityInput): ActionAvailability[] {
  const myTask = findMyPendingTask(input)
  const node = currentNodeOf(input, myTask)
  const runtimeNode = currentRuntimeNodeOf(input, myTask)
  const status = input.instance.status ?? ''
  const isInitiator = input.instance.initiatorId !== '' && input.instance.initiatorId === input.currentUserId
  const isTerminal = status === 'approved' || status === 'terminated'

  return input.catalog.map((item) => {
    const base: ActionAvailability = {
      action: item.action,
      label: item.label,
      permission: item.permission,
      enabled: true,
      reason: '',
      note: '',
      requiresReason: item.requiresReason,
      minOpinionChars: item.minOpinionChars,
      danger: DANGER_ACTIONS.includes(item.action),
    }

    // ② 权限
    // 终止是**唯一例外**：入口闸门不是权限码本身，而是 AC-49 专用判据
    // （`WorkflowPermissionService#requireTerminate` = 系统管理员 ∪ group_leader ∪ flow:task:terminate），
    // 因此与其它动作分开判 —— 否则非管理员会收到「缺少权限码」这种不准确的原因。
    if (item.action === 'terminate') {
      if (!isTerminateSubject(input.subject)) {
        return {
          ...base,
          enabled: false,
          reason: '「终止流程」仅系统管理员与集团分管领导可执行（AC-49 / REQ-FLOW-010）；服务端按 403/40301 拒绝',
        }
      }
    } else if (!permissionHeld(input.subject, item.permission)) {
      return { ...base, enabled: false, reason: `当前账号缺少权限码 ${item.permission}（服务端按 403/40301 拒绝）` }
    }

    // ③ 任务级动作
    if (TASK_SCOPED_ACTIONS.includes(item.action)) {
      if (!input.runtime) {
        return { ...base, enabled: false, reason: '运行态尚未加载：无法判定「分配给我的待处理任务」，请先刷新' }
      }
      if (!myTask) {
        return { ...base, enabled: false, reason: '该动作只对「分配给我的待处理任务」开放；本单当前没有你的待处理任务' }
      }
      // ⑦ 归档登记节点：只接受「归档登记」，不接受「通过」
      if (item.action === 'approve' && myTask.nodeCode === ARCHIVE_NODE_CODE) {
        return {
          ...base,
          enabled: false,
          reason: '⑦ 归档登记节点只接受「归档登记」，不接受「通过」（服务端按 40910 拒绝）',
        }
      }
      if (item.action === 'archive_register' && myTask.nodeCode !== ARCHIVE_NODE_CODE) {
        return {
          ...base,
          enabled: false,
          reason: '「归档登记」只在 ⑦ 归档登记节点（archive_register）产生；当前节点不是归档节点',
        }
      }
      // ④ 节点开关
      if (node) {
        if (item.action === 'jump' && !node.allowJump) {
          return { ...base, enabled: false, reason: ALLOW_SWITCH_REASON.jump }
        }
        if (item.action === 'add_sign' && !node.allowAddSign) {
          return { ...base, enabled: false, reason: ALLOW_SWITCH_REASON.add_sign }
        }
        if ((item.action === 'route' || item.action === 'back_home') && !node.allowRoute) {
          return { ...base, enabled: false, reason: ALLOW_SWITCH_REASON[item.action] }
        }
      }
      if (item.action === 'supplement_request') {
        // `supplementRequested` / `status` 是**运行态**字段（在 NodeInstanceView 上，不在快照里）
        if (runtimeNode?.supplementRequested) {
          return { ...base, enabled: false, reason: '该节点已请求过补件（同节点 ≤1，REQ-FLOW-023）；服务端按 40913 拒绝' }
        }
        if (runtimeNode?.status === 'waiting_supplement' || input.instance.subStatus === 'pending_supplement') {
          return { ...base, enabled: false, reason: '单据已处于待补件状态，需先由发起人提交补件（服务端按 40910 拒绝）' }
        }
      }
      if (item.action === 'reassign' && !input.subject.isSuperAdmin) {
        // 改派仅系统管理员（`FlowAction.REASSIGN` + REQ-ADMIN-003）；非管理员即使持权限码也按 403 拒
        return {
          ...base,
          enabled: false,
          reason: '改派仅系统管理员可执行（REQ-ADMIN-003 / 后端 FlowAction.REASSIGN）；当前账号不是系统管理员',
        }
      }
      const note =
        item.action === 'route'
          ? '禁止流转回已处理过的部门（40911）；流转与回退共用 Q6 全单预算'
          : item.action === 'rollback'
            ? '同一节点被回退 ≤2 次（40912）；回退计入 Q6 全单预算'
            : item.action === 'jump'
              ? '自由跳转会把中间节点记为 skipped，并在轨迹里留痕'
              : ''
      return { ...base, note }
    }

    // ⑤ 实例级动作
    switch (item.action) {
      case 'submit':
        if (!isInitiator) return { ...base, enabled: false, reason: '只有发起人本人（或系统管理员）可以提交该单据' }
        if (status !== 'draft') {
          return { ...base, enabled: false, reason: `只有草稿状态可以提交，当前状态：${instanceStatusLabel(status)}` }
        }
        return {
          ...base,
          note: '提交前必须通过表单 SUBMIT 档校验（不通过按 40011 逐字段列出）；首次提交会按锁定版本建立节点实例',
        }
      case 'resubmit':
        if (!isInitiator) return { ...base, enabled: false, reason: '只有发起人本人（或系统管理员）可以重新提交' }
        if (status !== 'draft' && status !== 'rejected' && status !== 'withdrawn') {
          return { ...base, enabled: false, reason: `当前状态不可重提：${instanceStatusLabel(status)}` }
        }
        return { ...base, note: '重提会**重新解析**审批人快照与流程版本（REQ-FLOW-017），旧快照进审计日志' }
      case 'reopen':
        if (!isInitiator) return { ...base, enabled: false, reason: '只有发起人本人（或系统管理员）可以回到草稿' }
        if (status !== 'rejected' && status !== 'withdrawn') {
          return { ...base, enabled: false, reason: `只有已驳回 / 已撤回可以回到草稿，当前状态：${instanceStatusLabel(status)}` }
        }
        return { ...base, note: '回到草稿后可编辑主字段，再以「重提」回到审批流' }
      case 'withdraw':
        if (!isInitiator && !input.subject.isSuperAdmin) {
          return { ...base, enabled: false, reason: '只有发起人本人（或系统管理员）可以撤回（REQ-FLOW-009）' }
        }
        if (status === 'draft') return { ...base, enabled: false, reason: '草稿状态的单据无需撤回' }
        if (status === 'approved' || status === 'terminated') {
          return { ...base, enabled: false, reason: `单据已处于终态（${instanceStatusLabel(status)}），不可撤回` }
        }
        return {
          ...base,
          note: '撤回仅限「财务部复核（节点②）」通过之前（撤回窗口按模板锁定版本，doc/templates.md §1.8）',
        }
      case 'terminate':
        // AC-49 主体判据已在 ② 权限层判过（终止的入口闸门不是权限码本身）
        if (isTerminal) {
          return { ...base, enabled: false, reason: `单据已处于终态（${instanceStatusLabel(status)}），不可再终止` }
        }
        return { ...base, note: '终止后不可再提交（REQ-FLOW-010）；必须填写原因' }
      case 'supplement_submit':
        if (!isInitiator) return { ...base, enabled: false, reason: '只有发起人可以提交补件' }
        if (input.instance.subStatus !== 'pending_supplement') {
          return { ...base, enabled: false, reason: '当前单据不在待补件状态，无需提交补件' }
        }
        return { ...base, note: '补件只补充「附件 + 补件说明」，主字段只读（doc/forms.md §7）' }
      case 'cc':
        if (!isInitiator && !input.subject.isSuperAdmin) {
          return { ...base, enabled: false, reason: '只有发起人本人（或系统管理员）可以登记抄送人' }
        }
        if (isTerminal) {
          return { ...base, enabled: false, reason: '单据已处于终态，不能再增加抄送人' }
        }
        return { ...base, note: '抄送只读可见：不产生待办、不参与审批决议（doc/enums.md §8）' }
      case 'approve':
      case 'reject':
      case 'rollback':
      case 'route':
      case 'back_home':
      case 'jump':
      case 'add_sign':
      case 'transfer':
      case 'reassign':
      case 'supplement_request':
      case 'archive_register':
        // 已在上面任务级分支处理；走到这里说明动作面与任务清单不一致
        return { ...base, enabled: false, reason: '该动作需要「分配给我的待处理任务」，当前无法判定' }
      default:
        // 未知动作码：如实展示但默认不可用（服务端可能新增动作，前端不猜语义）
        return { ...base, enabled: false, reason: '前端尚未登记该动作的可用性判据，请以服务端返回为准' }
    }
  })
}

// ================================================================ 列表

/** 列表项 → 单据类型（后端可能返回 null） */
export function listItemFormType(item: Pick<FlowTaskListItem, 'formType'>): string {
  return item.formType ?? ''
}

/**
 * 「我发起的」列表里同一张单可能出现多行（每个待办任务一行）；
 * 按实例去重并保留**最新时间**的那一行，避免用户看到重复单据。
 */
export function dedupeByInstance(items: readonly FlowTaskListItem[]): FlowTaskListItem[] {
  const byInstance = new Map<string, FlowTaskListItem>()
  for (const item of items) {
    const key = item.instanceId
    const existing = byInstance.get(key)
    if (!existing) {
      byInstance.set(key, item)
      continue
    }
    const existingTime = existing.decidedAt ?? existing.taskCreatedAt ?? ''
    const nextTime = item.decidedAt ?? item.taskCreatedAt ?? ''
    if (nextTime > existingTime) byInstance.set(key, item)
  }
  return Array.from(byInstance.values())
}

/** 动作面清单（`GET /flow-actions`）取不到时的显式提示（**不伪造动作清单**） */
export const ACTION_CATALOG_FALLBACK_HINT =
  '动作面清单取不到时**不提供任何动作按钮**：权限码、必填原因与意见下限都以该接口为准，' +
  '凭前端硬编码发请求会出现「按钮点了被 403/40910 拒」的错位。请检查网络或权限后重试。'

/** 分页信息（`total` 后端是 `long` → JSON 字符串，这里统一成 number） */
export function toCount(value: number | string | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** 页码保护（后端 `page` 从 1 起；非法值退回 1） */
export function safePage(page: number | null | undefined, totalPages: number): number {
  const current = Math.max(1, Math.trunc(Number(page ?? 1)) || 1)
  if (totalPages <= 0) return 1
  return Math.min(current, totalPages)
}

// ================================================================ 动作的入参规格

/**
 * 动作码 → `POST /flow-tasks/{taskId}/{子路径}` 的子路径（与后端 `FlowTaskController`
 * 的 11 个端点**逐条对应**）。
 *
 * 放在 utils 而不是 api 层的原因：这张表是契约骨架，必须能脱离 axios 单独复核
 * （`api/flow-task.ts` 依赖 axios，Node 里直跑不了；本表可以）。
 */
export const TASK_ACTION_ENDPOINTS: Record<string, string> = {
  approve: 'approve',
  reject: 'reject',
  rollback: 'rollback',
  route: 'route',
  back_home: 'back-home',
  jump: 'jump',
  add_sign: 'add-sign',
  transfer: 'transfer',
  reassign: 'reassign',
  supplement_request: 'supplement-request',
  archive_register: 'archive-register',
}

/**
 * 实例级动作 → `POST /flow-instances/{id}/{子路径}` 的子路径
 * （`FlowInstanceController` + `FlowRuntimeController` 的两个端点集）。
 * `submit` 属发起链路（`FlowInstanceController`），其余属运行态（`FlowRuntimeController`）。
 */
export const INSTANCE_ACTION_ENDPOINTS: Record<string, string> = {
  submit: 'submit',
  reopen: 'reopen',
  resubmit: 'resubmit',
  withdraw: 'withdraw',
  terminate: 'terminate',
  supplement_submit: 'supplement',
  cc: 'cc',
}

/** 任务级动作是否需要一个「分配给我的待处理任务」 */
export function isTaskScopedAction(action: string): boolean {
  return action in TASK_ACTION_ENDPOINTS
}

/** 实例级动作是否需要实例 id（全部动作都需要；本函数用于自检端点表覆盖度） */
export function isInstanceScopedAction(action: string): boolean {
  return action in INSTANCE_ACTION_ENDPOINTS
}

/**
 * 一个动作需要收集哪些输入（与后端 `RuntimeRequests` 的各 record **逐字对应**）。
 *
 * 为什么把这张表放在 utils 而不是组件里：入参形状是**契约**（哪个动作要原因、哪个要部门、
 * 哪个要目标节点序号），它必须能脱离 Vue 单独复核 —— 靠组件里的 `v-if` 拼参数，
 * 一旦漏掉一个必填项，用户看到的就是「填完点了没反应 / 40001 参数校验失败」。
 */
export interface ActionInputSpec {
  /** 需要意见 / 原因（一个文本框） */
  opinion: boolean
  /** 该文本框的语义：`opinion` 审批意见（≤1000）/ `reason` 原因（≤255） */
  opinionKind: 'opinion' | 'reason' | 'none'
  opinionLabel: string
  /** 是否必填（空白即拒绝） */
  opinionRequired: boolean
  /** 意见最小字数（驳回 = 5） */
  minOpinionChars: number
  /** 意见最大字数（按动作分别 1000 / 255） */
  maxOpinionChars: number
  /** 需要「承接部门」 */
  toDept: boolean
  /** 需要「目标节点序号」 */
  targetSeq: boolean
  /** 需要「加签类型 + 加签人」 */
  addSign: boolean
  /** 需要「目标处理人」 */
  toUser: boolean
  /** 需要「抄送人清单」 */
  ccUsers: boolean
  /** 是否必填原因类动作（用于给用户解释为什么必须填） */
  reasonRequiredEvidence: string
}

const NO_INPUT: ActionInputSpec = {
  opinion: false,
  opinionKind: 'none',
  opinionLabel: '',
  opinionRequired: false,
  minOpinionChars: 0,
  maxOpinionChars: 255,
  toDept: false,
  targetSeq: false,
  addSign: false,
  toUser: false,
  ccUsers: false,
  reasonRequiredEvidence: '',
}

/**
 * 需要填写原因/意见的动作码（**兜底常量**）。
 *
 * ⚠ 权威来源是 `GET /flow-actions` 的 `requiresReason`（逐个动作取），本常量只在**接口尚未返回**
 * 时兜底，且必须与后端 `FlowAction` 的 `requiresReason` 完全一致
 * （实测 `/flow-actions`：reject / rollback / route / back_home / jump / add_sign /
 * transfer / reassign / supplement_request / withdraw / terminate 为 true，
 * submit / approve / archive_register / supplement_submit / cc / reopen 为 false）。
 *
 * 为什么需要兜底：若拿不到清单就一律按「非必填」处理，用户可能把空原因提交上去，
 * 服务端再以 40010 拒绝 —— 那是本可避免的一次往返。
 */
export const REASON_REQUIRED_ACTIONS: readonly string[] = [
  'reject',
  'rollback',
  'route',
  'back_home',
  'jump',
  'add_sign',
  'transfer',
  'reassign',
  'supplement_request',
  'withdraw',
  'terminate',
]

/**
 * 取某动作的入参规格。
 *
 * `catalogItem` 由 `GET /flow-actions` 提供 —— `requiresReason` 与 `minOpinionChars`
 * **以服务端为准**（本函数只补「哪种输入控件」这类界面语义）；
 * 清单未取到时用 {@link REASON_REQUIRED_ACTIONS} 兜底。
 */
export function actionInputSpec(
  action: string,
  catalogItem?: Pick<ActionCatalogItem, 'requiresReason' | 'minOpinionChars'>,
): ActionInputSpec {
  const requiresReason = catalogItem?.requiresReason ?? REASON_REQUIRED_ACTIONS.includes(action)
  const minOpinionChars = catalogItem?.minOpinionChars ?? (action === 'reject' ? 5 : 0)
  const maxOpinionChars = reasonMaxLength(action)
  switch (action) {
    case 'approve':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'opinion',
        opinionLabel: '审批意见（可选）',
        // 服务端清单说了必填就必填（`FlowAction.requiresReason` 是唯一真源）
        opinionRequired: requiresReason,
        maxOpinionChars,
      }
    case 'reject':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'opinion',
        opinionLabel: `驳回意见（必填，至少 ${minOpinionChars} 字）`,
        opinionRequired: true,
        minOpinionChars,
        maxOpinionChars,
        reasonRequiredEvidence: 'REQ-FLOW-013 / AC-50：驳回意见 ≥5 字（服务端 40009）',
      }
    case 'archive_register':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'opinion',
        opinionLabel: '登记说明（可选）',
        opinionRequired: requiresReason,
        maxOpinionChars,
      }
    case 'rollback':
    case 'back_home':
    case 'supplement_request':
    case 'withdraw':
    case 'terminate':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: requiresReason ? '原因（必填）' : '原因',
        opinionRequired: requiresReason,
        maxOpinionChars,
        reasonRequiredEvidence: requiresReason ? '服务端 40010：该动作必须填写原因' : '',
      }
    case 'route':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '流转原因（必填）',
        opinionRequired: true,
        maxOpinionChars,
        toDept: true,
        reasonRequiredEvidence: '服务端 40010：流转必须填写原因；且禁止流转回已处理过的部门（40911）',
      }
    case 'jump':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '跳转原因（必填）',
        opinionRequired: true,
        maxOpinionChars,
        targetSeq: true,
        reasonRequiredEvidence: '服务端 40010：自由跳转必须填写原因；中间节点会记为 skipped',
      }
    case 'add_sign':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '加签原因（必填）',
        opinionRequired: true,
        maxOpinionChars,
        addSign: true,
        reasonRequiredEvidence: '服务端 40010：加签必须填写原因，并指定加签人与前/后加签',
      }
    case 'transfer':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '转办原因（必填）',
        opinionRequired: true,
        maxOpinionChars,
        toUser: true,
        reasonRequiredEvidence: '服务端 40010：转办必须填写原因，并指定同数据域内的接收人',
      }
    case 'reassign':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '改派原因（必填）',
        opinionRequired: true,
        maxOpinionChars,
        toUser: true,
        reasonRequiredEvidence: '服务端 40010：改派必须填写原因（仅系统管理员）',
      }
    case 'resubmit':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '重提说明（可选）',
        opinionRequired: requiresReason,
        maxOpinionChars,
      }
    case 'supplement_submit':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'opinion',
        opinionLabel: '补件说明（必填，≥5 字 / ≤500 字）',
        // 补件说明的 ≥5 字下限在服务端 `FormPayloadValidator` 里强制（`SUPPLEMENT_NOTE_MIN`），
        // 与 `FlowAction.requiresReason`（false）无关，因此这里独立置为必填
        opinionRequired: true,
        minOpinionChars: 5,
        maxOpinionChars: 500,
        reasonRequiredEvidence: 'doc/forms.md §8：补件说明 ≥5 字、≤500 字（服务端 40011）',
      }
    case 'cc':
      return {
        ...NO_INPUT,
        ccUsers: true,
        reasonRequiredEvidence: '抄送人不参与决议、不产生待办；不能为空（服务端 40001）',
      }
    case 'submit':
      return {
        ...NO_INPUT,
        opinion: true,
        opinionKind: 'reason',
        opinionLabel: '提交说明（≤255；留空按「提交审批」落轨迹）',
        opinionRequired: requiresReason,
        maxOpinionChars,
      }
    case 'reopen':
    default:
      return { ...NO_INPUT }
  }
}

/** 动作入参的校验结论 */
export interface ActionInputCheck {
  ok: boolean
  message: string
}

/** 动作入参的前端校验（必填项、字数、id 形态；服务端仍会再判一次） */
export function checkActionInputs(
  spec: ActionInputSpec,
  inputs: { opinion?: string; toDeptId?: string; targetSeq?: string; delegateUserId?: string; toUserId?: string; ccUserIds?: string; addSignType?: string },
): ActionInputCheck {
  if (spec.opinion) {
    const text = (inputs.opinion ?? '').trim()
    const length = Array.from(text).length
    if (spec.opinionRequired && length === 0) {
      return { ok: false, message: `「${spec.opinionLabel}」不能为空（${spec.reasonRequiredEvidence || '服务端会拒绝'}）` }
    }
    if (spec.minOpinionChars > 0 && length > 0 && length < spec.minOpinionChars) {
      return { ok: false, message: `至少 ${spec.minOpinionChars} 个字，当前 ${length} 个字（服务端 40009）` }
    }
    if (length > spec.maxOpinionChars) {
      return { ok: false, message: `不得超过 ${spec.maxOpinionChars} 字，当前 ${length} 字（服务端 40001）` }
    }
  }
  if (spec.toDept && !isIdLike(inputs.toDeptId)) {
    return { ok: false, message: '必须选择承接部门（服务端 40001：必须选择承接部门）' }
  }
  if (spec.targetSeq) {
    const seq = Number(inputs.targetSeq ?? '')
    if (!Number.isInteger(seq) || seq < 1) {
      return { ok: false, message: '必须给出目标节点序号（正整数；服务端 40001）' }
    }
  }
  if (spec.addSign) {
    if (inputs.addSignType !== 'pre' && inputs.addSignType !== 'post') {
      return { ok: false, message: '必须选择加签类型（pre=前加签 / post=后加签）' }
    }
    if (!isIdLike(inputs.delegateUserId)) {
      return { ok: false, message: '必须指定加签人（服务端 40001：必须指定加签人）' }
    }
  }
  if (spec.toUser && !isIdLike(inputs.toUserId)) {
    return { ok: false, message: '必须指定目标处理人（服务端 40001：必须指定目标处理人）' }
  }
  if (spec.ccUsers && splitIds(inputs.ccUserIds).length === 0) {
    return { ok: false, message: '抄送人不能为空（服务端 40001）' }
  }
  return { ok: true, message: '' }
}

/** 形如用户/组织 id 的字符串（后端 `Long`；前端只校验形态，存在性由服务端判） */
export function isIdLike(value: string | null | undefined): boolean {
  const trimmed = (value ?? '').trim()
  return /^\d+$/.test(trimmed) && trimmed !== '0'
}

/** 逗号 / 顿号 / 空格 / 换行分隔的 id 串 → 去重后的 id 数组（非法片段直接丢弃） */
export function splitIds(value: string | null | undefined): string[] {
  const seen = new Set<string>()
  for (const part of (value ?? '').split(/[,，、\s]+/)) {
    const trimmed = part.trim()
    if (isIdLike(trimmed)) seen.add(trimmed)
  }
  return Array.from(seen)
}
