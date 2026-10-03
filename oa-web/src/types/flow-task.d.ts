/**
 * oa-web · 流程运行时与任务域领域模型（页面只依赖本文件）
 * ----------------------------------------------------------------------------
 * 由 `api/flow-task.ts` 从 `types/flow-task-wire.d.ts` 映射而来。映射口径：
 *   · id 一律 string；`Integer` 一律 number；`PageResult.total`（后端 `long`）→ number；
 *   · 可空键统一归一为 `null`，列表项里的名称类字段归一为 `''`；
 *   · 自由 JSON（快照 `basis`）用 `FormJsonValue` 递归联合表达，**不用 `any`**。
 *
 * ⚠ 与 `types/api.d.ts` 的关系：既有文件是**阶段 1 骨架**的门户模型（`WorkbenchItem` 等），
 *   本轮起审批中心改由**真实引擎**驱动，页面只依赖本文件；`types/api.d.ts` 保持不动，
 *   供打印预览等既有页面继续使用（硬要求 4：既有页面行为不得改变）。
 */
import type { FormJsonValue } from './form'

/**
 * 动作码（**与后端 `FlowAction.code()` 逐字一致**，见 `oa-server` 的
 * `com.oa.workflow.runtime.domain.FlowAction`）。
 *
 * 这里的联合**只是类型提示**：可用性与文案一律以 `GET /flow-actions` 的服务端返回为准
 * （`ActionCatalogItem`），未知动作码也必须能被如实渲染 —— 因此 `ActionCode` 在
 * 边界处收窄，展示层用 `string` 兼容新动作。
 *
 * ⚠ **轨迹动作**（`sys_thread.action`）与动作码**不是同一个值域**：
 * 例如 `return_register`（印鉴单归还登记）只是一条**轨迹动作**，没有对应的动作端点
 * （写入口是 `PUT /forms/seal/instances/{id}/return-status`），因此它只出现在
 * {@link ThreadActionCode} 里，不在本联合内。把两者混在一个联合里会让「有哪些可调用的动作」
 * 这个语义失真。
 */
export type ActionCode =
  | 'submit'
  | 'approve'
  | 'reject'
  | 'rollback'
  | 'route'
  | 'back_home'
  | 'jump'
  | 'add_sign'
  | 'transfer'
  | 'reassign'
  | 'supplement_request'
  | 'supplement_submit'
  | 'withdraw'
  | 'terminate'
  | 'archive_register'
  | 'cc'
  | 'reopen'

/**
 * 轨迹动作码（`doc/enums.md` §9 的 **17 值**，与后端 `RuntimeEnums.ThreadAction` 逐字一致）。
 *
 * 2026-10-04 由 16 值扩到 17 值：新增 {@link ThreadActionCode} 的 `return_register`（归还登记）
 * —— 印鉴单归还登记此前落 `archive_register`，那会在**用户可见的审计轨迹**里把「归还登记」
 * 说成「归档登记」。节点⑦的「归档登记」动作本身仍是 `archive_register`，未变。
 *
 * `reopen`（回到草稿）**不在**本值域内（`doc/enums.md` §9 无该值，后端只写审计日志），
 * 因此 `ThreadView.action` 可能是 `string`：展示层以服务端下发的 `actionLabel` 为准，
 * 未知值原样回显（`utils/flow-task.ts#threadActionLabel`）。
 */
export type ThreadActionCode =
  | 'submit'
  | 'approve'
  | 'reject'
  | 'rollback'
  | 'route'
  | 'back_home'
  | 'skip'
  | 'add_sign'
  | 'transfer'
  | 'reassign'
  | 'supplement_request'
  | 'supplement_submit'
  | 'withdraw'
  | 'terminate'
  /** 印鉴单归还登记（AC-28 三态例外的轨迹动作；语义上早于归档登记） */
  | 'return_register'
  | 'archive_register'
  | 'cc'

/** 动作面清单项（来自 `GET /flow-actions`） */
export interface ActionCatalogItem {
  action: string
  label: string
  permission: string
  requiresReason: boolean
  /** 意见/原因的字数下限（驳回 = 5；0 = 不设下限但必填非空） */
  minOpinionChars: number
  taskStatusAfter: string | null
  threadAction: string | null
  transition: string
}

/**
 * 动作可用性（前端逐条算出的**提示**）。
 *
 * `enabled=false` 时 `reason` **必须**非空：任务书要求「不可用的要说明原因，
 * 而不是点了报错」（见 B 段末）。服务端仍是裁决方。
 */
export interface ActionAvailability {
  action: string
  label: string
  permission: string
  enabled: boolean
  reason: string
  /**
   * 「可点但有前置风险」的提示（`enabled=true` 时展示，例如撤回窗口、
   * 流转禁止回流、Q6 剩余次数）。空串 = 无提示。
   */
  note: string
  requiresReason: boolean
  minOpinionChars: number
  /** 危险动作（驳回 / 终止）在界面上用 ghost 红字而非实心红按钮 */
  danger: boolean
}

/** Q6 预算 / 闸门剩余 */
export interface FlowGate {
  key: string
  max: number | null
  used: number | null
  remaining: number | null
  unlimited: boolean
  note: string
}

/** Q7 补件时限（`timeoutExecuted=false` = 就位但不调度，阶段 3 才执行） */
export interface FlowSupplementDeadline {
  dueAt: string | null
  days: number | null
  type: string | null
  holidayCalendarMissing: boolean
  calendar: string | null
  note: string | null
  timeoutAction: string | null
  timeoutExecuted: boolean
  executionTodo: string | null
}

/** 动作结果（三层状态 + 闸门剩余 + 补件时限） */
export interface FlowActionResult {
  action: string
  actionLabel: string
  instanceId: string
  instanceStatus: string
  subStatus: string | null
  currentNodeSeq: number | null
  nodeInstanceId: string
  nodeStatus: string
  taskId: string
  taskStatus: string
  message: string
  gate: FlowGate | null
  deadline: FlowSupplementDeadline | null
}

/** 节点实例 */
export interface FlowNodeInstance {
  id: string
  nodeSeq: number
  nodeCode: string
  nodeName: string
  nodeKey: string
  deptId: string
  status: string
  statusLabel: string
  decisionMode: string
  passThreshold: string
  approverIds: string[]
  returnedCount: number
  supplementRequested: boolean
  addSignChain: string
  addSignOpen: boolean
  startedAt: string | null
  finishedAt: string | null
}

/** 任务记录（该单全部任务） */
export interface FlowTaskRecord {
  id: string
  nodeInstanceId: string
  nodeSeq: number | null
  nodeName: string
  assigneeId: string
  assigneeName: string
  originAssigneeId: string
  delegateFrom: string
  addSignType: string
  status: string
  statusLabel: string
  opinion: string
  handoverReason: string
  createdAt: string | null
  decidedAt: string | null
}

/** 审批轨迹项 */
export interface FlowThreadItem {
  seq: number
  action: string
  actionLabel: string
  nodeInstanceId: string
  actorId: string
  actorName: string
  actorPosition: string
  opinion: string
  signatureId: string
  createdAt: string | null
}

/** 流转链项 */
export interface FlowRoutingItem {
  seq: number
  actionType: string
  actionLabel: string
  fromDeptId: string
  toDeptId: string
  fromNodeSeq: number | null
  toNodeSeq: number | null
  designatedBy: string
  reason: string
  status: string
  createdAt: string | null
  finishedAt: string | null
}

/** 补件记录 */
export interface FlowSupplementItem {
  id: string
  round: number
  status: string
  reason: string
  requestedBy: string
  nodeInstanceId: string
  deadline: string | null
  overdue: boolean
  submittedAt: string | null
  submittedBy: string
  submittedNote: string
}

/** 抄送记录 */
export interface FlowCcItem {
  userId: string
  userName: string
  source: string
  readAt: string | null
}

/** 实例运行态总览 */
export interface FlowRuntime {
  instanceId: string
  bizNo: string
  instanceStatus: string
  subStatus: string | null
  currentNodeSeq: number | null
  currentDeptId: string
  ownerDeptId: string
  routingSeq: number | null
  routingCount: number
  supplementCount: number
  returnGate: FlowGate | null
  supplementGate: FlowGate | null
  nodes: FlowNodeInstance[]
  tasks: FlowTaskRecord[]
  thread: FlowThreadItem[]
  routing: FlowRoutingItem[]
  supplements: FlowSupplementItem[]
  cc: FlowCcItem[]
  hasOpenAddSign: boolean
}

/** 快照候选人 */
export interface FlowSnapshotApprover {
  userId: string
  name: string
  account: string
  employeeNo: string
  orgId: string
  orgName: string
  orgPath: string
  companyId: string
  position: string
}

/** 快照节点（运行时权威数据：节点开关 allowAddSign/allowRoute/allowJump 取自这里） */
export interface FlowSnapshotNode {
  nodeSeq: number | null
  nodeCode: string
  nodeName: string
  nodeType: string
  rule: string
  decisionMode: string
  passThreshold: string
  signPolicy: string
  timeoutHours: number | null
  allowAddSign: boolean
  allowJump: boolean
  allowRoute: boolean
  skipped: boolean
  skipReason: string
  evidence: string
  approvers: FlowSnapshotApprover[]
  groupIds: string[][]
  requiredApprovals: number | null
  thresholdBasis: string
  satisfiable: boolean | null
}

/** 审批人快照 */
export interface FlowApproverSnapshot {
  templateId: string
  templateCode: string
  templateVersion: number | null
  parsedAt: string | null
  basis: Record<string, FormJsonValue>
  nodes: FlowSnapshotNode[]
}

/** 实例摘要（`GET /flow-instances/{id}`） */
export interface FlowInstance {
  id: string
  bizNo: string
  templateId: string
  templateVersion: number | null
  templateCode: string
  formType: string
  category: string
  initiatorId: string
  initiatorOrgId: string
  initiatorCompanyId: string
  initiatorOrgPath: string
  status: string
  subStatus: string | null
  currentNodeSeq: number | null
  ownerDeptId: string
  submittedAt: string | null
  createdAt: string | null
  snapshot: FlowApproverSnapshot | null
}

/** 任务列表项（待办 / 已办 / 我发起的） */
export interface FlowTaskListItem {
  /** 待办/已办有 taskId；「我发起的」在无在途任务时可能为 null */
  taskId: string
  instanceId: string
  bizNo: string
  formType: string
  category: string
  nodeSeq: number | null
  nodeName: string
  assigneeId: string
  originAssigneeId: string
  addSignType: string
  taskStatus: string
  taskStatusLabel: string
  opinion: string
  taskCreatedAt: string | null
  decidedAt: string | null
  initiatorId: string
  initiatorName: string
  currentNodeSeq: number | null
  instanceStatus: string
  subStatus: string | null
  /** 单据标题（`form_data.fields_json.title`；2026-10-04 随后端出参追加） */
  title: string
}

/**
 * 「抄送我的一览」列表项（`GET /flow-tasks/cc`）。
 *
 * 七项出参 + 已读标记：单号 / 类型 / 标题 / 发起人 / 发起时间 / 当前状态 / 抄送时间 +
 * `readAt` / `read`。抄送**只读可见、不产生待办**（REQ-MSG-003 / AC-54）。
 */
export interface FlowCcListItem {
  ccId: string
  instanceId: string
  bizNo: string
  formType: string
  category: string
  title: string
  initiatorId: string
  initiatorName: string
  /** 单据发起时间（`instanceCreatedAt`） */
  instanceCreatedAt: string | null
  currentNodeSeq: number | null
  instanceStatus: string
  subStatus: string | null
  /** 抄送时间 */
  ccCreatedAt: string | null
  ccSource: string
  readAt: string | null
  read: boolean
}

/**
 * 四个列表的统一筛选入参（`page/size/keyword/formType/status/dateFrom/dateTo`）。
 *
 * 空串 = 不筛（映射层不下发该参数）；`total` 是**筛选后**的总数，过滤在服务端 SQL 层完成。
 */
export interface FlowListFilter {
  keyword?: string
  /** 单据类型（`matter / fund / contract / seal`） */
  formType?: string
  /** 单据状态码，另接受子状态 `pending_supplement` */
  status?: string
  /** `YYYY-MM-DD`，口径是单据**发起时间**（四个列表同义） */
  dateFrom?: string
  dateTo?: string
}

/** 分页结果（`total` 已由映射层从字符串归一为 number） */
export interface FlowPage<T> {
  items: T[]
  total: number
  page: number
  size: number
}

/** 列表类型（**四个真实接口**：待我审批 / 我已审批 / 我发起的 / 抄送我的） */
export type FlowListKind = 'todo' | 'done' | 'initiated' | 'cc'

// ---------------------------------------------------------------------------
// 接口入参（页面 → api 层）
// ---------------------------------------------------------------------------

/** 发起前预检入参 */
export interface FlowPrecheckPayload {
  templateId?: string | null
  templateVersion?: number | null
  initiatorId?: string | null
  formType?: string | null
  category?: string | null
  involveCost?: boolean | null
  initiatorPicks?: string[] | null
  collabDeptIds?: string[] | null
  collabSelfExcludeDeptIds?: string[] | null
  formValues?: Record<string, FormJsonValue> | null
}

/** 建草稿实例入参（`fields` 与 `formValues` 传同一份值） */
export interface FlowCreateInstancePayload {
  templateId?: string | null
  templateVersion?: number | null
  initiatorId?: string | null
  bizNo?: string | null
  formType: string
  category?: string | null
  involveCost?: boolean | null
  initiatorPicks?: string[] | null
  collabDeptIds?: string[] | null
  collabSelfExcludeDeptIds?: string[] | null
  fields?: Record<string, FormJsonValue> | null
}
