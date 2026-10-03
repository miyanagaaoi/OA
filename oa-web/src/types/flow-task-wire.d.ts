/**
 * oa-web · 后端 workflow.runtime / workflow.task 域 DTO 镜像（wire 层）
 * ----------------------------------------------------------------------------
 * 逐字段对齐 oa-server 的
 *   · `com.oa.workflow.runtime.api.dto.RuntimeDtos`（2a.4 / 2a.5：动作面 / 动作结果 / 运行态 / 列表 / 分页）
 *   · `com.oa.workflow.runtime.api.dto.RuntimeRequests`（动作入参）
 *   · `com.oa.workflow.approver.api.dto.ApproverDtos.InstanceView` 与 `ApproverSnapshot`（2a.3 实例与快照）
 *   · `com.oa.workflow.runtime.api.FlowRuntimeController` / `com.oa.workflow.task.api.FlowTaskController`
 *     / `com.oa.workflow.approver.api.FlowInstanceController` 的路由表
 *
 * 契约来源（文档真源）：
 *   · `doc/enums.md` §2（主干 7 节点）、§7（决议模式 / 签名策略）、§8（三层状态）、
 *     §9（**17 值**轨迹动作：2026-10-04 由 16 值加入 `return_register` 归还登记）
 *   · `doc/prd-0.1.md` §6.3（主干审批链）、§6.4（流转/回退/加签/跳转）、§6.6（驳回/补件）、
 *     §6.7（抄送）、AC-49（终止）
 *   · `doc/templates.md` §1.7（Q6/Q7 闸门）、§1.8（撤回窗口）
 *
 * ⚠ 序列化口径（与 `types/form-wire.d.ts` 逐条一致）：
 *   · `Long`/`long` → **JSON 字符串**（id）；`Integer`/`int` → JSON number；
 *   · ⚠ `PageResult.total` 是 `long` → **JSON 字符串**（实测 `"11"`），映射层须 `Number(...)`；
 *   · `non_null` 生效：可空引用类型为 null 时整键省略 → 本文件一律写 `?: T | null`。
 *
 * ⚠ 本文件是**忠实镜像**；页面只依赖 `types/flow-task.d.ts` 的领域模型。
 */
import type { WireId } from './identity-wire'
import type { WireJson } from './form-wire'

export type { WireId, WireJson }

// ================================================================ 分页（列表）

/**
 * `PageResult<T>` —— `GET /flow-tasks/todo|done|initiated` 的共用分页壳。
 *
 * `page` 从 **1** 起；`total` 在后端是 `long`，JSON 里是**字符串**（`"11"`）。
 */
export interface WirePageResult<T> {
  items: T[]
  total: number | string
  page: number
  size: number
}

/**
 * `TaskListItemView` —— 待办 / 已办 / 我发起的列表项。
 *
 * 同一形状服务三个列表：`todo`（`taskStatus=pending` 且我是处理人）、
 * `done`（我处理过的任务）、`initiated`（我发起的单据的当前在途任务）。
 */
export interface WireTaskListItemView {
  taskId?: WireId | null
  instanceId: WireId
  bizNo?: string | null
  formType?: string | null
  category?: string | null
  /** `Integer`（JSON number） */
  nodeSeq?: number | null
  nodeName?: string | null
  assigneeId?: WireId | null
  originAssigneeId?: WireId | null
  /** `pre` / `post`（加签产生的任务才有） */
  addSignType?: string | null
  taskStatus?: string | null
  taskStatusLabel?: string | null
  opinion?: string | null
  taskCreatedAt?: string | null
  decidedAt?: string | null
  initiatorId?: WireId | null
  initiatorName?: string | null
  /** `Integer`（JSON number） */
  currentNodeSeq?: number | null
  instanceStatus?: string | null
  /** `pending_supplement` 等子状态 */
  subStatus?: string | null
}

// ================================================================ 动作面清单

/**
 * `ActionCatalogView` —— `GET /flow-actions` 单条，由后端 `FlowAction` 枚举投影。
 *
 * 这是**可用性判定的服务端真源**：`permission` / `requiresReason` / `minOpinionChars`
 * 都取自这里，前端不再硬编码（例如驳回 ≥5 字来自 `minOpinionChars=5`）。
 */
export interface WireActionCatalogView {
  action: string
  label: string
  /** 权限码（冒号风格；`flow` 为门户基础权限） */
  permission: string
  requiresReason: boolean
  /** `int`（JSON number）：驳回为 5，其余为 0（不设下限但必填非空） */
  minOpinionChars: number
  /** 动作后任务状态（实例级动作为 null，键省略） */
  taskStatusAfter?: string | null
  /** 轨迹动作码（`reopen` 为 null） */
  threadAction?: string | null
  transition: string
}

// ================================================================ 闸门 / 时限

/** `GateView` —— Q6 预算出参（`unlimited=true` 时 `max/remaining` 为 null）。 */
export interface WireGateView {
  key: string
  /** `Integer`（JSON number） */
  max?: number | null
  /** `Integer`（JSON number） */
  used?: number | null
  /** `Integer`（JSON number） */
  remaining?: number | null
  unlimited: boolean
  note: string
}

/** `SupplementDeadlineView` —— Q7 补件时限出参（`timeoutExecuted=false` = 就位但不调度）。 */
export interface WireSupplementDeadlineView {
  dueAt?: string | null
  /** `Integer`（JSON number） */
  days?: number | null
  type?: string | null
  holidayCalendarMissing: boolean
  calendar?: string | null
  note?: string | null
  timeoutAction?: string | null
  timeoutExecuted: boolean
  executionTodo?: string | null
}

// ================================================================ 动作结果

/** `ActionResult` —— 每个动作统一返回「三层状态 + 闸门剩余 + 补件时限」。 */
export interface WireActionResult {
  action: string
  actionLabel: string
  instanceId?: WireId | null
  instanceStatus?: string | null
  subStatus?: string | null
  /** `Integer`（JSON number） */
  currentNodeSeq?: number | null
  nodeInstanceId?: WireId | null
  nodeStatus?: string | null
  taskId?: WireId | null
  taskStatus?: string | null
  message?: string | null
  gate?: WireGateView | null
  deadline?: WireSupplementDeadlineView | null
}

// ================================================================ 运行态视图

/** `NodeInstanceView` —— 节点实例。 */
export interface WireNodeInstanceView {
  id: WireId
  /** `Integer`（JSON number） */
  nodeSeq: number
  nodeCode: string
  nodeName: string
  nodeKey: string
  deptId?: WireId | null
  status: string
  statusLabel: string
  /** `any` / `all` / `sequence` */
  decisionMode?: string | null
  passThreshold?: string | null
  approverIds?: WireId[] | null
  /** `Integer`（JSON number） */
  returnedCount: number
  supplementRequested: boolean
  addSignChain?: string | null
  addSignOpen: boolean
  startedAt?: string | null
  finishedAt?: string | null
}

/** `TaskView` —— 该单的全部任务。 */
export interface WireTaskView {
  id: WireId
  nodeInstanceId?: WireId | null
  /** `Integer`（JSON number） */
  nodeSeq?: number | null
  nodeName?: string | null
  assigneeId?: WireId | null
  assigneeName?: string | null
  originAssigneeId?: WireId | null
  delegateFrom?: WireId | null
  addSignType?: string | null
  status: string
  statusLabel: string
  opinion?: string | null
  handoverReason?: string | null
  createdAt?: string | null
  decidedAt?: string | null
}

/**
 * `ThreadView` —— 审批轨迹（**17 值**动作 + 中文名）。
 *
 * ⚠ `action` 是**轨迹值域**（`RuntimeEnums.ThreadAction`），与 `GET /flow-actions` 的
 * **动作码值域**（`FlowAction`）不是同一个集合：例如 `return_register`（印鉴单归还登记）
 * 只有轨迹值、没有动作端点。展示一律以服务端下发的 `actionLabel` 为准。
 */
export interface WireThreadView {
  /** `Integer`（JSON number） */
  seq: number
  action: string
  actionLabel: string
  nodeInstanceId?: WireId | null
  actorId?: WireId | null
  actorName?: string | null
  actorPosition?: string | null
  opinion?: string | null
  signatureId?: WireId | null
  createdAt?: string | null
}

/** `RoutingView` —— 流转链。 */
export interface WireRoutingView {
  /** `Integer`（JSON number） */
  seq: number
  actionType: string
  actionLabel: string
  fromDeptId?: WireId | null
  toDeptId?: WireId | null
  /** `Integer`（JSON number） */
  fromNodeSeq?: number | null
  /** `Integer`（JSON number） */
  toNodeSeq?: number | null
  designatedBy?: WireId | null
  reason?: string | null
  status: string
  createdAt?: string | null
  finishedAt?: string | null
}

/** `SupplementView` —— 补件记录。 */
export interface WireSupplementView {
  id: WireId
  /** `Integer`（JSON number） */
  round: number
  status: string
  reason?: string | null
  requestedBy?: WireId | null
  nodeInstanceId?: WireId | null
  deadline?: string | null
  overdue: boolean
  submittedAt?: string | null
  submittedBy?: WireId | null
  submittedNote?: string | null
}

/** `CcView` —— 抄送记录。 */
export interface WireCcView {
  userId: WireId
  userName?: string | null
  source?: string | null
  readAt?: string | null
}

/**
 * `InstanceRuntimeView` —— 实例运行态总览（端到端验收的证据载体）。
 *
 * ⚠ 实测：`nodes` / `tasks` / `thread` 等数组**可能是空数组**（数据域过滤或该实例没有节点实例），
 * 前端不得据此推断「实例不存在」——存在性以 `GET /flow-instances/{id}` 为准。
 */
export interface WireInstanceRuntimeView {
  instanceId: WireId
  bizNo?: string | null
  instanceStatus: string
  subStatus?: string | null
  /** `Integer`（JSON number） */
  currentNodeSeq?: number | null
  currentDeptId?: WireId | null
  ownerDeptId?: WireId | null
  /** `Integer`（JSON number） */
  routingSeq?: number | null
  /** `Integer`（JSON number） */
  routingCount: number
  /** `Integer`（JSON number） */
  supplementCount: number
  returnGate?: WireGateView | null
  supplementGate?: WireGateView | null
  nodes: WireNodeInstanceView[]
  tasks: WireTaskView[]
  thread: WireThreadView[]
  routing: WireRoutingView[]
  supplements: WireSupplementView[]
  cc: WireCcView[]
  hasOpenAddSign: boolean
}

// ================================================================ 实例（2a.3）

/** `SnapshotApprover` —— 快照里的候选人。 */
export interface WireSnapshotApprover {
  userId?: WireId | null
  name?: string | null
  account?: string | null
  employeeNo?: string | null
  orgId?: WireId | null
  orgName?: string | null
  orgPath?: string | null
  companyId?: WireId | null
  position?: string | null
}

/** `ApproverSnapshot.SnapshotNode` —— 快照里的节点（**运行时权威数据**）。 */
export interface WireSnapshotNode {
  /** `Integer`（JSON number） */
  nodeSeq?: number | null
  nodeCode?: string | null
  nodeName?: string | null
  nodeType?: string | null
  rule?: string | null
  decisionMode?: string | null
  passThreshold?: string | null
  signPolicy?: string | null
  /** `Integer`（JSON number） */
  timeoutHours?: number | null
  timeoutCcSuperior?: boolean | null
  allowAddSign?: boolean | null
  allowJump?: boolean | null
  allowRoute?: boolean | null
  skipped?: boolean | null
  skipReason?: string | null
  evidence?: string | null
  approvers?: WireSnapshotApprover[] | null
  /** `List<List<Long>>` → JSON 里是字符串二维数组 */
  groups?: string[][] | null
  /** `Integer`（JSON number） */
  requiredApprovals?: number | null
  thresholdBasis?: string | null
  satisfiable?: boolean | null
}

/** `ApproverSnapshot` —— 审批人快照（建实例时固化，改组织/模板不影响它）。 */
export interface WireApproverSnapshotView {
  templateId?: WireId | null
  templateCode?: string | null
  /** `Integer`（JSON number） */
  templateVersion?: number | null
  parsedAt?: string | null
  basis?: Record<string, WireJson> | null
  nodes?: WireSnapshotNode[] | null
}

/**
 * `InstanceView` —— 实例视图（`GET /flow-instances/{id}` 与建/提交接口的共用出参）。
 *
 * `templateCode` 与 `ownerDeptId` 等键**在 null 时整键省略**（实测 id=46 无 `templateCode`）。
 */
export interface WireInstanceView {
  id: WireId
  bizNo?: string | null
  templateId?: WireId | null
  /** `Integer`（JSON number） */
  templateVersion?: number | null
  templateCode?: string | null
  formType?: string | null
  category?: string | null
  initiatorId?: WireId | null
  initiatorOrgId?: WireId | null
  initiatorCompanyId?: WireId | null
  initiatorOrgPath?: string | null
  /** `draft` / `approving` / `rejected` / `approved` / `terminated` / `withdrawn` */
  status?: string | null
  subStatus?: string | null
  /** `Integer`（JSON number） */
  currentNodeSeq?: number | null
  ownerDeptId?: WireId | null
  submittedAt?: string | null
  createdAt?: string | null
  snapshot?: WireApproverSnapshotView | null
}

// ================================================================ 动作入参（RuntimeRequests）

/** `ApproveRequest` —— 通过（`opinion` 可选；`collabDeptIds` 仅 ② 生效）。 */
export interface WireApproveRequest {
  opinion?: string | null
  collabDeptIds?: string[] | null
}

/** `RejectRequest` —— 驳回（意见在引擎侧过 ≥5 字闸门）。 */
export interface WireRejectRequest {
  opinion?: string | null
}

/** `ArchiveRegisterRequest` —— ⑦ 归档登记（意见可选）。 */
export interface WireArchiveRegisterRequest {
  opinion?: string | null
}

/** `ReasonRequest` —— 回退 / 终止 / 撤回 / 回到本部门（`reason` **必填**，≤255）。 */
export interface WireReasonRequest {
  reason: string
}

/** `RouteRequest` —— 流转（承接部门 + 原因）。 */
export interface WireRouteRequest {
  toDeptId: string
  reason: string
}

/** `JumpRequest` —— 自由跳转（目标节点序号 + 原因）。 */
export interface WireJumpRequest {
  /** `Integer` */
  targetSeq: number
  reason: string
}

/** `AddSignRequest` —— 加签（`pre` / `post` + 加签人 + 原因）。 */
export interface WireAddSignRequest {
  type: 'pre' | 'post'
  delegateUserId: string
  reason: string
}

/** `HandoverRequest` —— 转办 / 改派（目标处理人 + 原因）。 */
export interface WireHandoverRequest {
  toUserId: string
  reason: string
}

/** `SupplementSubmitRequest` —— 提交补件（备注可选）。 */
export interface WireSupplementSubmitRequest {
  note?: string | null
}

/** `CcRequest` —— 抄送登记（抄送人不能为空）。 */
export interface WireCcRequest {
  userIds: string[]
}

// ================================================================ 实例列表查询

/**
 * `GET /flow-instances` 的查询参数（全部可选；`@RequestParam`）。
 *
 * 注意 `status` 是**实例状态**（`draft` / `approving` / …），不是模板状态；
 * `limit` 是 `Integer`，缺省由服务端决定。
 */
export interface WireFlowInstanceListQuery {
  templateId?: string
  /** `Integer` */
  templateVersion?: number
  status?: string
  initiatorId?: string
  /** `Integer` */
  limit?: number
}
