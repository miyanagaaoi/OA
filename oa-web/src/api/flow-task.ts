/**
 * oa-web · 流程实例与审批任务接口（发起前预检 / 建草稿 / 提交 / 运行态 / 待办已办 / 审批动作）
 * ----------------------------------------------------------------------------
 * 路径为阶段 2a **已交付**的定稿契约（全部在 `/api/v1` 之下），本文件把
 * `types/flow-task-wire.d.ts`（后端 DTO 镜像）映射为领域模型 `types/flow-task.d.ts`。
 *
 * 后端实现：
 *   · `com.oa.workflow.approver.api.FlowInstanceController`（预检 / 建草稿 / 提交 / 列表 / 详情 / 快照）
 *   · `com.oa.workflow.runtime.api.FlowRuntimeController`（动作面 / 运行态 / 实例级动作）
 *   · `com.oa.workflow.task.api.FlowTaskController`（待办已办我发起 / 任务级动作）
 *
 * ── 发起（写 `flow`，只读 `flow ∪ admin:flow`）───────────────────────────────
 *   POST   /api/v1/flow-instances/precheck                        读（只读干跑，逐节点解析候选人）
 *   POST   /api/v1/flow-instances                                 写（建草稿：锁版本 + 固化快照）
 *   POST   /api/v1/flow-instances/{id}/submit                     写（draft → approving；**原因必填**）
 *   GET    /api/v1/flow-instances/{id}                            读（实例详情，含快照）
 * ── 运行态（只读）──────────────────────────────────────────────────────────
 *   GET    /api/v1/flow-actions                                   读（动作面清单：权限/必填原因/意见下限）
 *   GET    /api/v1/flow-instances/{id}/runtime                    读（三层状态 + 轨迹 + 流转 + 补件 + 抄送）
 *   GET    /api/v1/flow-instances/{id}/node-instances|task-list|thread|routing|supplements|cc
 * ── 实例级动作 ─────────────────────────────────────────────────────────────
 *   POST   .../{id}/reopen | resubmit | withdraw | terminate | supplement | cc
 * ── 任务列表与任务级动作 ───────────────────────────────────────────────────
 *   GET    /api/v1/flow-tasks/todo | done | initiated             读（分页）
 *   POST   /api/v1/flow-tasks/{taskId}/approve | reject | rollback | route | back-home | jump
 *          | add-sign | supplement-request | transfer | reassign | archive-register
 *
 * ⚠ **抄送我的列表没有接口**（`FlowTaskController` 只有 todo/done/initiated 三个列表；
 *   `flow_instance_cc` 只能在**单实例**维度通过 `/{id}/cc` 读）。本文件因此**不伪造**
 *   `listCcTasks()`；页面如实标注「待实现」。
 *
 * 两条实现约定：
 *   1. **映射层承担 `Long` → string**，并负责把 `PageResult.total`（后端 `long` → JSON 字符串）
 *      归一为 number；
 *   2. **不做演示数据降级**：审批动作会真实改变单据状态，静默回落演示数据等于「点了没反应还以为成功了」。
 */
import { get, post, type OaRequestConfig } from './http'
import type {
  WireActionResult,
  WireActionCatalogView,
  WireCcView,
  WireFlowInstanceListQuery,
  WireGateView,
  WireInstanceRuntimeView,
  WireInstanceView,
  WireNodeInstanceView,
  WirePageResult,
  WireRoutingView,
  WireSupplementView,
  WireTaskListItemView,
  WireTaskView,
  WireThreadView,
} from '@/types/flow-task-wire'
import type {
  ActionCatalogItem,
  FlowActionResult,
  FlowApproverSnapshot,
  FlowCcItem,
  FlowCreateInstancePayload,
  FlowGate,
  FlowInstance,
  FlowNodeInstance,
  FlowPage,
  FlowPrecheckPayload,
  FlowRoutingItem,
  FlowRuntime,
  FlowSnapshotNode,
  FlowSupplementDeadline,
  FlowSupplementItem,
  FlowTaskListItem,
  FlowTaskRecord,
  FlowThreadItem,
} from '@/types/flow-task'
import type {
  FlowPrecheckBlocker,
  FlowPrecheckNode,
  FlowPrecheckReport,
  FormJsonValue,
} from '@/types/form'
import { toCount, splitIds, TASK_ACTION_ENDPOINTS } from '@/utils/flow-task'

// ---------------------------------------------------------------------------
// 传输层包装：错误提示由页面**唯一呈现**（含业务码）
// ---------------------------------------------------------------------------
/**
 * 关闭 `api/http.ts` 拦截器的统一 toast。
 *
 * <p>本域的关键拒绝都是「业务码 + 可操作信息」：`40009` 驳回意见不足 5 字、
 * `40007` 预检拦截（逐节点缺配）、`40908`–`40915` 各类闸门、`40304/40308/40306` 写入拒绝。
 * 拦截器的 toast 不带业务码，且与页面提示会重复弹两次，故本域一律关闭、由页面呈现。
 * 401 不受影响（仍走 `setUnauthorizedHandler` 的跳登录逻辑）。
 */
const NO_INTERCEPTOR_TOAST: OaRequestConfig = {
  notify: { forbidden: false, conflict: false, rateLimited: false, serverError: false },
}

function fget<T>(url: string, config?: OaRequestConfig): Promise<T> {
  return get<T>(url, { ...NO_INTERCEPTOR_TOAST, ...config })
}

function fpost<T>(url: string, data?: unknown, config?: OaRequestConfig): Promise<T> {
  return post<T>(url, data, { ...NO_INTERCEPTOR_TOAST, ...config })
}

// ---------------------------------------------------------------------------
// 映射工具
// ---------------------------------------------------------------------------
/** `Long` → 字符串 id（缺失 → 空串） */
function sid(value: string | number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

/** `Long` → 字符串 id，**保留 null 语义**（区分「没有」与「空串」） */
function nullableId(value: string | number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

/** `Integer` → number */
function num(value: number | string | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** 可空 `Integer` → `number | null` */
function nullableNum(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 字符串归一（缺失 → 空串） */
function text(value: string | null | undefined): string {
  return value === null || value === undefined ? '' : value
}

/** 可空字符串归一 */
function nullableText(value: string | null | undefined): string | null {
  return value === null || value === undefined || value === '' ? null : value
}

/** 字符串数组归一 */
function strings(value: Array<string | number> | null | undefined): string[] {
  return (value ?? []).map((item) => sid(item))
}

/** 自由 JSON 归一（递归联合，不用 `any`） */
function jsonValue(value: unknown): FormJsonValue {
  if (value === null || value === undefined) return null
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value
  if (Array.isArray(value)) return value.map((item) => jsonValue(item))
  if (typeof value === 'object') {
    const result: Record<string, FormJsonValue> = {}
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      result[key] = jsonValue(item)
    }
    return result
  }
  return String(value)
}

/** 闸门归一 */
function toGate(wire: WireGateView | null | undefined): FlowGate | null {
  if (!wire) return null
  return {
    key: text(wire.key),
    max: nullableNum(wire.max),
    used: nullableNum(wire.used),
    remaining: nullableNum(wire.remaining),
    unlimited: wire.unlimited === true,
    note: text(wire.note),
  }
}

/** 补件时限归一 */
function toDeadline(wire: {
  dueAt?: string | null
  days?: number | null
  type?: string | null
  holidayCalendarMissing?: boolean
  calendar?: string | null
  note?: string | null
  timeoutAction?: string | null
  timeoutExecuted?: boolean
  executionTodo?: string | null
} | null | undefined): FlowSupplementDeadline | null {
  if (!wire) return null
  return {
    dueAt: nullableText(wire.dueAt),
    days: nullableNum(wire.days),
    type: nullableText(wire.type),
    holidayCalendarMissing: wire.holidayCalendarMissing === true,
    calendar: nullableText(wire.calendar),
    note: nullableText(wire.note),
    timeoutAction: nullableText(wire.timeoutAction),
    timeoutExecuted: wire.timeoutExecuted === true,
    executionTodo: nullableText(wire.executionTodo),
  }
}

// ================================================================ 动作面清单

/** 动作面清单（`GET /flow-actions`；可用性判定的**服务端真源**） */
export async function fetchActionCatalog(): Promise<ActionCatalogItem[]> {
  const wire = await fget<WireActionCatalogView[]>('/flow-actions')
  return (wire ?? []).map((item) => ({
    action: text(item.action),
    label: text(item.label),
    permission: text(item.permission),
    requiresReason: item.requiresReason === true,
    minOpinionChars: num(item.minOpinionChars, 0),
    taskStatusAfter: nullableText(item.taskStatusAfter),
    threadAction: nullableText(item.threadAction),
    transition: text(item.transition),
  }))
}

// ================================================================ 任务列表

/** 列表项归一 */
function toListItem(wire: WireTaskListItemView): FlowTaskListItem {
  return {
    taskId: sid(wire.taskId),
    instanceId: sid(wire.instanceId),
    bizNo: text(wire.bizNo),
    formType: text(wire.formType),
    category: text(wire.category),
    nodeSeq: nullableNum(wire.nodeSeq),
    nodeName: text(wire.nodeName),
    assigneeId: nullableId(wire.assigneeId),
    originAssigneeId: nullableId(wire.originAssigneeId),
    addSignType: text(wire.addSignType),
    taskStatus: text(wire.taskStatus),
    taskStatusLabel: text(wire.taskStatusLabel),
    opinion: text(wire.opinion),
    taskCreatedAt: nullableText(wire.taskCreatedAt),
    decidedAt: nullableText(wire.decidedAt),
    initiatorId: nullableId(wire.initiatorId),
    initiatorName: text(wire.initiatorName),
    currentNodeSeq: nullableNum(wire.currentNodeSeq),
    instanceStatus: text(wire.instanceStatus),
    subStatus: nullableText(wire.subStatus),
  }
}

/** 分页壳归一（`total` 在后端是 `long` → JSON 字符串） */
function toPage(wire: WirePageResult<WireTaskListItemView> | null | undefined): FlowPage<FlowTaskListItem> {
  return {
    items: (wire?.items ?? []).map(toListItem),
    total: toCount(wire?.total, 0),
    page: num(wire?.page, 1),
    size: num(wire?.size, 20),
  }
}

/** 待我审批（`pending` 且我是处理人；分页 + 数据域） */
export async function listTodoTasks(page = 1, size = 20): Promise<FlowPage<FlowTaskListItem>> {
  const wire = await fget<WirePageResult<WireTaskListItemView>>('/flow-tasks/todo', {
    params: { page, size },
  })
  return toPage(wire)
}

/** 我已审批（我处理过的任务） */
export async function listDoneTasks(page = 1, size = 20): Promise<FlowPage<FlowTaskListItem>> {
  const wire = await fget<WirePageResult<WireTaskListItemView>>('/flow-tasks/done', {
    params: { page, size },
  })
  return toPage(wire)
}

/** 我发起的 */
export async function listInitiatedTasks(page = 1, size = 20): Promise<FlowPage<FlowTaskListItem>> {
  const wire = await fget<WirePageResult<WireTaskListItemView>>('/flow-tasks/initiated', {
    params: { page, size },
  })
  return toPage(wire)
}

// ================================================================ 实例详情与运行态

/** 快照节点归一 */
function toSnapshotNode(wire: NonNullable<NonNullable<WireInstanceView['snapshot']>['nodes']>[number]): FlowSnapshotNode {
  return {
    nodeSeq: nullableNum(wire.nodeSeq),
    nodeCode: text(wire.nodeCode),
    nodeName: text(wire.nodeName),
    nodeType: text(wire.nodeType),
    rule: text(wire.rule),
    decisionMode: text(wire.decisionMode),
    passThreshold: text(wire.passThreshold),
    signPolicy: text(wire.signPolicy),
    timeoutHours: nullableNum(wire.timeoutHours),
    allowAddSign: wire.allowAddSign === true,
    allowJump: wire.allowJump === true,
    allowRoute: wire.allowRoute === true,
    skipped: wire.skipped === true,
    skipReason: text(wire.skipReason),
    evidence: text(wire.evidence),
    approvers: (wire.approvers ?? []).map((approver) => ({
      userId: sid(approver.userId),
      name: text(approver.name),
      account: text(approver.account),
      employeeNo: text(approver.employeeNo),
      orgId: sid(approver.orgId),
      orgName: text(approver.orgName),
      orgPath: text(approver.orgPath),
      companyId: sid(approver.companyId),
      position: text(approver.position),
    })),
    groupIds: (wire.groups ?? []).map((group) => strings(group)),
    requiredApprovals: nullableNum(wire.requiredApprovals),
    thresholdBasis: text(wire.thresholdBasis),
    satisfiable: wire.satisfiable === null || wire.satisfiable === undefined ? null : wire.satisfiable === true,
  }
}

/**
 * `InstanceView` → 领域模型（`GET /flow-instances/{id}`、建草稿、提交、列表共用）。
 *
 * 抽成一处的原因：`InstanceView` 出现在 4 个接口上，各写一份映射正是「同一个字段在不同
 * 页面口径不一致」这类缺陷的温床。
 */
function toInstance(wire: WireInstanceView): FlowInstance {
  const snapshot: FlowApproverSnapshot | null = wire.snapshot
    ? {
        templateId: sid(wire.snapshot.templateId),
        templateCode: text(wire.snapshot.templateCode),
        templateVersion: nullableNum(wire.snapshot.templateVersion),
        parsedAt: nullableText(wire.snapshot.parsedAt),
        basis: (() => {
          const basis: Record<string, FormJsonValue> = {}
          for (const [key, value] of Object.entries(wire.snapshot?.basis ?? {})) {
            basis[key] = jsonValue(value)
          }
          return basis
        })(),
        nodes: (wire.snapshot.nodes ?? []).map(toSnapshotNode),
      }
    : null
  return {
    id: sid(wire.id),
    bizNo: text(wire.bizNo),
    templateId: sid(wire.templateId),
    templateVersion: nullableNum(wire.templateVersion),
    templateCode: text(wire.templateCode),
    formType: text(wire.formType),
    category: text(wire.category),
    initiatorId: nullableId(wire.initiatorId),
    initiatorOrgId: sid(wire.initiatorOrgId),
    initiatorCompanyId: sid(wire.initiatorCompanyId),
    initiatorOrgPath: text(wire.initiatorOrgPath),
    status: text(wire.status),
    subStatus: nullableText(wire.subStatus),
    currentNodeSeq: nullableNum(wire.currentNodeSeq),
    ownerDeptId: sid(wire.ownerDeptId),
    submittedAt: nullableText(wire.submittedAt),
    createdAt: nullableText(wire.createdAt),
    snapshot,
  }
}

/** 实例详情（含审批人快照；快照是运行时权威数据） */
export async function fetchFlowInstance(instanceId: string): Promise<FlowInstance> {
  const wire = await fget<WireInstanceView>(`/flow-instances/${encodeURIComponent(instanceId)}`)
  return toInstance(wire)
}

/** 节点实例归一 */
function toNodeInstance(wire: WireNodeInstanceView): FlowNodeInstance {
  return {
    id: sid(wire.id),
    nodeSeq: num(wire.nodeSeq, 0),
    nodeCode: text(wire.nodeCode),
    nodeName: text(wire.nodeName),
    nodeKey: text(wire.nodeKey),
    deptId: sid(wire.deptId),
    status: text(wire.status),
    statusLabel: text(wire.statusLabel),
    decisionMode: text(wire.decisionMode),
    passThreshold: text(wire.passThreshold),
    approverIds: strings(wire.approverIds),
    returnedCount: num(wire.returnedCount, 0),
    supplementRequested: wire.supplementRequested === true,
    addSignChain: text(wire.addSignChain),
    addSignOpen: wire.addSignOpen === true,
    startedAt: nullableText(wire.startedAt),
    finishedAt: nullableText(wire.finishedAt),
  }
}

/** 任务归一 */
function toTaskRecord(wire: WireTaskView): FlowTaskRecord {
  return {
    id: sid(wire.id),
    nodeInstanceId: sid(wire.nodeInstanceId),
    nodeSeq: nullableNum(wire.nodeSeq),
    nodeName: text(wire.nodeName),
    assigneeId: nullableId(wire.assigneeId),
    assigneeName: text(wire.assigneeName),
    originAssigneeId: nullableId(wire.originAssigneeId),
    delegateFrom: nullableId(wire.delegateFrom),
    addSignType: text(wire.addSignType),
    status: text(wire.status),
    statusLabel: text(wire.statusLabel),
    opinion: text(wire.opinion),
    handoverReason: text(wire.handoverReason),
    createdAt: nullableText(wire.createdAt),
    decidedAt: nullableText(wire.decidedAt),
  }
}

/** 轨迹归一 */
function toThreadItem(wire: WireThreadView): FlowThreadItem {
  return {
    seq: num(wire.seq, 0),
    action: text(wire.action),
    actionLabel: text(wire.actionLabel),
    nodeInstanceId: sid(wire.nodeInstanceId),
    actorId: nullableId(wire.actorId),
    actorName: text(wire.actorName),
    actorPosition: text(wire.actorPosition),
    opinion: text(wire.opinion),
    signatureId: sid(wire.signatureId),
    createdAt: nullableText(wire.createdAt),
  }
}

/** 流转链归一 */
function toRoutingItem(wire: WireRoutingView): FlowRoutingItem {
  return {
    seq: num(wire.seq, 0),
    actionType: text(wire.actionType),
    actionLabel: text(wire.actionLabel),
    fromDeptId: sid(wire.fromDeptId),
    toDeptId: sid(wire.toDeptId),
    fromNodeSeq: nullableNum(wire.fromNodeSeq),
    toNodeSeq: nullableNum(wire.toNodeSeq),
    designatedBy: nullableId(wire.designatedBy),
    reason: text(wire.reason),
    status: text(wire.status),
    createdAt: nullableText(wire.createdAt),
    finishedAt: nullableText(wire.finishedAt),
  }
}

/** 补件归一 */
function toSupplementItem(wire: WireSupplementView): FlowSupplementItem {
  return {
    id: sid(wire.id),
    round: num(wire.round, 0),
    status: text(wire.status),
    reason: text(wire.reason),
    requestedBy: nullableId(wire.requestedBy),
    nodeInstanceId: sid(wire.nodeInstanceId),
    deadline: nullableText(wire.deadline),
    overdue: wire.overdue === true,
    submittedAt: nullableText(wire.submittedAt),
    submittedBy: nullableId(wire.submittedBy),
    submittedNote: text(wire.submittedNote),
  }
}

/** 抄送归一 */
function toCcItem(wire: WireCcView): FlowCcItem {
  return {
    userId: sid(wire.userId),
    userName: text(wire.userName),
    source: text(wire.source),
    readAt: nullableText(wire.readAt),
  }
}

/** 运行态总览（**详情页与可用性判定的唯一取数口**） */
export async function fetchInstanceRuntime(instanceId: string): Promise<FlowRuntime> {
  const wire = await fget<WireInstanceRuntimeView>(
    `/flow-instances/${encodeURIComponent(instanceId)}/runtime`,
  )
  return {
    instanceId: sid(wire.instanceId),
    bizNo: text(wire.bizNo),
    instanceStatus: text(wire.instanceStatus),
    subStatus: nullableText(wire.subStatus),
    currentNodeSeq: nullableNum(wire.currentNodeSeq),
    currentDeptId: sid(wire.currentDeptId),
    ownerDeptId: sid(wire.ownerDeptId),
    routingSeq: nullableNum(wire.routingSeq),
    routingCount: num(wire.routingCount, 0),
    supplementCount: num(wire.supplementCount, 0),
    returnGate: toGate(wire.returnGate),
    supplementGate: toGate(wire.supplementGate),
    nodes: (wire.nodes ?? []).map(toNodeInstance),
    tasks: (wire.tasks ?? []).map(toTaskRecord),
    thread: (wire.thread ?? []).map(toThreadItem),
    routing: (wire.routing ?? []).map(toRoutingItem),
    supplements: (wire.supplements ?? []).map(toSupplementItem),
    cc: (wire.cc ?? []).map(toCcItem),
    hasOpenAddSign: wire.hasOpenAddSign === true,
  }
}

/** 节点实例清单（`/runtime` 已含；单列出来供按需刷新） */
export async function listInstanceNodes(instanceId: string): Promise<FlowNodeInstance[]> {
  const wire = await fget<WireNodeInstanceView[]>(
    `/flow-instances/${encodeURIComponent(instanceId)}/node-instances`,
  )
  return (wire ?? []).map(toNodeInstance)
}

/** 该单全部任务 */
export async function listInstanceTasks(instanceId: string): Promise<FlowTaskRecord[]> {
  const wire = await fget<WireTaskView[]>(`/flow-instances/${encodeURIComponent(instanceId)}/task-list`)
  return (wire ?? []).map(toTaskRecord)
}

/** 审批轨迹 */
export async function listInstanceThread(instanceId: string): Promise<FlowThreadItem[]> {
  const wire = await fget<WireThreadView[]>(`/flow-instances/${encodeURIComponent(instanceId)}/thread`)
  return (wire ?? []).map(toThreadItem)
}

/** 流转链 */
export async function listInstanceRouting(instanceId: string): Promise<FlowRoutingItem[]> {
  const wire = await fget<WireRoutingView[]>(`/flow-instances/${encodeURIComponent(instanceId)}/routing`)
  return (wire ?? []).map(toRoutingItem)
}

/** 补件记录 */
export async function listInstanceSupplements(instanceId: string): Promise<FlowSupplementItem[]> {
  const wire = await fget<WireSupplementView[]>(
    `/flow-instances/${encodeURIComponent(instanceId)}/supplements`,
  )
  return (wire ?? []).map(toSupplementItem)
}

/**
 * 抄送记录（**单实例维度**）。
 *
 * ⚠ 「抄送我的」**列表**接口不存在（后端只有 todo/done/initiated 三个列表）；
 * 本函数只读**指定单据**的抄送清单，用于详情页展示。
 */
export async function listInstanceCc(instanceId: string): Promise<FlowCcItem[]> {
  const wire = await fget<WireCcView[]>(`/flow-instances/${encodeURIComponent(instanceId)}/cc`)
  return (wire ?? []).map(toCcItem)
}

// ================================================================ 发起前预检与建草稿

/** 预检拦截项归一 */
function toBlocker(wire: {
  nodeSeq?: number | null
  nodeCode?: string | null
  nodeName?: string | null
  rule?: string | null
  ruleLabel?: string | null
  reason?: string | null
  missingConfig?: string[] | null
}): FlowPrecheckBlocker {
  return {
    nodeSeq: nullableNum(wire.nodeSeq),
    nodeCode: text(wire.nodeCode),
    nodeName: text(wire.nodeName),
    rule: text(wire.rule),
    ruleLabel: text(wire.ruleLabel),
    reason: text(wire.reason),
    missingConfig: (wire.missingConfig ?? []).map((item) => text(item)),
  }
}

/**
 * 发起前预检（**只读干跑**）：逐节点解析候选人，任一为空即 `allowed=false`。
 *
 * `allowed=false` 时服务端会以 `40007`（`APPROVER_RESOLUTION_BLOCKED`）拦在 `POST /flow-instances`；
 * 前端在此**提前**呈现「哪个节点、命中哪条规则、缺什么配置」（AC-11 / AC-19）。
 */
export async function precheckFlowInstance(payload: FlowPrecheckPayload): Promise<FlowPrecheckReport> {
  const wire = await fpost<{
    allowed: boolean
    templateId?: string | null
    templateCode?: string | null
    templateVersion?: number | null
    initiatorId?: string | null
    initiatorName?: string | null
    blockers?: Array<Parameters<typeof toBlocker>[0]>
    nodes?: Array<{
      nodeSeq?: number | null
      nodeCode?: string | null
      nodeName?: string | null
      nodeType?: string | null
      rule?: string | null
      skipped?: boolean
      skipReason?: string | null
      blocker?: boolean
      candidates?: Array<{ name?: string | null }> | null
      requiredApprovals?: number
      thresholdBasis?: string | null
      satisfiable?: boolean | null
      evidence?: string | null
      missingConfig?: string[] | null
    }>
    warnings?: string[] | null
  }>('/flow-instances/precheck', payload)

  const nodes: FlowPrecheckNode[] = (wire.nodes ?? []).map((node) => ({
    nodeSeq: nullableNum(node.nodeSeq),
    nodeCode: text(node.nodeCode),
    nodeName: text(node.nodeName),
    nodeType: text(node.nodeType),
    rule: text(node.rule),
    skipped: node.skipped === true,
    skipReason: text(node.skipReason),
    blocker: node.blocker === true,
    candidateNames: (node.candidates ?? []).map((candidate) => text(candidate.name)),
    candidateCount: (node.candidates ?? []).length,
    requiredApprovals: num(node.requiredApprovals, 0),
    thresholdBasis: text(node.thresholdBasis),
    satisfiable: node.satisfiable === null || node.satisfiable === undefined ? null : node.satisfiable === true,
    evidence: text(node.evidence),
    missingConfig: (node.missingConfig ?? []).map((item) => text(item)),
  }))

  return {
    allowed: wire.allowed === true,
    templateId: sid(wire.templateId),
    templateCode: text(wire.templateCode),
    templateVersion: num(wire.templateVersion, 0),
    initiatorId: sid(wire.initiatorId),
    initiatorName: text(wire.initiatorName),
    blockers: (wire.blockers ?? []).map(toBlocker),
    nodes,
    warnings: (wire.warnings ?? []).map((warning) => text(warning)),
  }
}

/**
 * 建草稿实例：预检通过 → **锁定模板版本** → 固化审批人快照。
 *
 * `fields` 与 `formValues` 传**同一份值**：后端 `CreateInstanceRequest` 两个键都有，
 * 前端不依赖它读哪一个（纯追加/实现细节不该成为契约）。
 */
export async function createFlowInstance(payload: FlowCreateInstancePayload): Promise<FlowInstance> {
  const body = {
    templateId: payload.templateId ?? null,
    templateVersion: payload.templateVersion ?? null,
    initiatorId: payload.initiatorId ?? null,
    bizNo: payload.bizNo ?? null,
    formType: payload.formType,
    category: payload.category ?? null,
    involveCost: payload.involveCost ?? null,
    initiatorPicks: payload.initiatorPicks ?? null,
    collabDeptIds: payload.collabDeptIds ?? null,
    collabSelfExcludeDeptIds: payload.collabSelfExcludeDeptIds ?? null,
    fields: payload.fields ?? {},
    formValues: payload.fields ?? {},
  }
  const wire = await fpost<WireInstanceView>('/flow-instances', body)
  return toInstance(wire)
}

/** 提交（`draft → approving`；**原因必填**，`SubmitRequest.reason` 有 `@NotBlank`） */
export async function submitFlowInstance(instanceId: string, reason: string): Promise<FlowInstance> {
  const wire = await fpost<WireInstanceView>(
    `/flow-instances/${encodeURIComponent(instanceId)}/submit`,
    { reason },
  )
  return toInstance(wire)
}

// ================================================================ 实例级动作

/** 动作结果归一 */
function toActionResult(wire: WireActionResult): FlowActionResult {
  return {
    action: text(wire.action),
    actionLabel: text(wire.actionLabel),
    instanceId: sid(wire.instanceId),
    instanceStatus: text(wire.instanceStatus),
    subStatus: nullableText(wire.subStatus),
    currentNodeSeq: nullableNum(wire.currentNodeSeq),
    nodeInstanceId: sid(wire.nodeInstanceId),
    nodeStatus: text(wire.nodeStatus),
    taskId: sid(wire.taskId),
    taskStatus: text(wire.taskStatus),
    message: text(wire.message),
    gate: toGate(wire.gate ?? null),
    deadline: toDeadline(wire.deadline),
  }
}

/** 回到草稿（驳回/撤回后编辑） */
export async function reopenFlowInstance(instanceId: string): Promise<FlowInstance> {
  const wire = await fpost<WireInstanceView>(`/flow-instances/${encodeURIComponent(instanceId)}/reopen`)
  return toInstance(wire)
}

/** 重提：重新解析快照与版本 → 回草稿 → 提交（REQ-FLOW-017） */
export async function resubmitFlowInstance(instanceId: string, reason: string): Promise<FlowInstance> {
  await fpost<WireInstanceView>(`/flow-instances/${encodeURIComponent(instanceId)}/resubmit`, { reason })
  return fetchFlowInstance(instanceId)
}

/** 撤回（仅发起人本人或系统管理员；仅节点②通过前） */
export async function withdrawFlowInstance(instanceId: string, reason: string): Promise<FlowInstance> {
  await fpost<WireInstanceView>(`/flow-instances/${encodeURIComponent(instanceId)}/withdraw`, { reason })
  return fetchFlowInstance(instanceId)
}

/** 终止（仅系统管理员 / 集团分管领导；必填原因；终态） */
export async function terminateFlowInstance(instanceId: string, reason: string): Promise<FlowInstance> {
  await fpost<WireInstanceView>(`/flow-instances/${encodeURIComponent(instanceId)}/terminate`, { reason })
  return fetchFlowInstance(instanceId)
}

/** 提交补件（仅发起人；只补附件与备注，主字段只读） */
export async function supplementSubmitFlowInstance(
  instanceId: string,
  note: string,
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(
    `/flow-instances/${encodeURIComponent(instanceId)}/supplement`,
    { note },
  )
  return toActionResult(wire)
}

/** 抄送登记（发起人指定；只读可见，不产生待办） */
export async function addCcFlowInstance(instanceId: string, userIds: string[]): Promise<string[]> {
  const wire = await fpost<{ ccUserIds?: string[] | null }>(
    `/flow-instances/${encodeURIComponent(instanceId)}/cc`,
    { userIds },
  )
  return strings(wire?.ccUserIds)
}

// ================================================================ 任务级动作

/** 通过（`opinion` 可选；② 通过时可勾选协同部门） */
export async function approveFlowTask(
  taskId: string,
  payload: { opinion?: string; collabDeptIds?: string[] },
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/approve`, {
    opinion: payload.opinion ?? null,
    collabDeptIds: payload.collabDeptIds ?? null,
  })
  return toActionResult(wire)
}

/** 驳回（意见 **≥5 字**；服务端按 40009 拒绝不足 5 字） */
export async function rejectFlowTask(taskId: string, opinion: string): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/reject`, { opinion })
  return toActionResult(wire)
}

/** ⑦ 归档登记（登记，不产生审批决议） */
export async function archiveRegisterFlowTask(taskId: string, opinion: string): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(
    `/flow-tasks/${encodeURIComponent(taskId)}/archive-register`,
    { opinion },
  )
  return toActionResult(wire)
}

/** 回退上一节点（Q6 预算 + 同节点 ≤2；必填原因） */
export async function rollbackFlowTask(taskId: string, reason: string): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/rollback`, { reason })
  return toActionResult(wire)
}

/** 流转（指定承接部门；禁止回流） */
export async function routeFlowTask(
  taskId: string,
  payload: { toDeptId: string; reason: string },
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/route`, payload)
  return toActionResult(wire)
}

/** 回到本部门（连续 ≤2，不计入 Q6） */
export async function backHomeFlowTask(taskId: string, reason: string): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/back-home`, { reason })
  return toActionResult(wire)
}

/** 自由跳转（节点开关默认关闭；必填原因） */
export async function jumpFlowTask(
  taskId: string,
  payload: { targetSeq: number; reason: string },
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/jump`, payload)
  return toActionResult(wire)
}

/** 加签（`pre` 前加签 / `post` 后加签） */
export async function addSignFlowTask(
  taskId: string,
  payload: { type: 'pre' | 'post'; delegateUserId: string; reason: string },
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/add-sign`, payload)
  return toActionResult(wire)
}

/** 请求补件（同节点 ≤1；Q6 全单预算） */
export async function supplementRequestFlowTask(taskId: string, reason: string): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(
    `/flow-tasks/${encodeURIComponent(taskId)}/supplement-request`,
    { reason },
  )
  return toActionResult(wire)
}

/** 转办（同数据域可见性校验） */
export async function transferFlowTask(
  taskId: string,
  payload: { toUserId: string; reason: string },
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/transfer`, payload)
  return toActionResult(wire)
}

/** 改派（**仅系统管理员**；用于人员离职、快照审批人不可用） */
export async function reassignFlowTask(
  taskId: string,
  payload: { toUserId: string; reason: string },
): Promise<FlowActionResult> {
  const wire = await fpost<WireActionResult>(`/flow-tasks/${encodeURIComponent(taskId)}/reassign`, payload)
  return toActionResult(wire)
}

/** 实例列表（按模板/版本/状态/发起人过滤；数据域过滤） */
export async function listFlowInstances(query: WireFlowInstanceListQuery = {}): Promise<FlowInstance[]> {
  const wire = await fget<WireInstanceView[]>('/flow-instances', { params: query })
  return (wire ?? []).map(toInstance)
}

// ================================================================ 动作派发（动作码 → 端点 + 载荷）

/**
 * 动作派发的输入（由详情页的动作弹窗收集；字段名与弹窗表单一致）。
 *
 * `taskId` 对**任务级动作**必填（`approve` / `reject` / `rollback` / `route` / `back_home` /
 * `jump` / `add_sign` / `transfer` / `reassign` / `supplement_request` / `archive_register`），
 * 对**实例级动作**（`submit` / `reopen` / `resubmit` / `withdraw` / `terminate` /
 * `supplement_submit` / `cc`）忽略。
 */
export interface CompileActionInput {
  action: string
  instanceId: string
  taskId?: string
  opinion?: string
  toDeptId?: string
  targetSeq?: string
  addSignType?: 'pre' | 'post'
  delegateUserId?: string
  toUserId?: string
  ccUserIds?: string
}

/** 编译结果：`run()` 执行请求，`message` 成功后展示，`endpoint` 便于排障与自检 */
export interface CompiledActionRequest {
  action: string
  endpoint: string
  run: () => Promise<void>
  successMessage: string
}

/**
 * 把「动作 + 弹窗输入」编译成一次真实的 HTTP 调用。
 *
 * <p><b>为什么集中在一处</b>：动作 → 端点 → 载荷形状是**契约**（`RuntimeRequests` 的各个 record）。
 * 散落在组件的 `v-if` 分支里，任何一处漏字段都会表现成「填完点了没反应」或 40001，
 * 而排障时看不出是哪一层拼错了。集中之后：端点表可枚举、载荷可逐条对照后端 record。
 *
 * @throws Error 未知动作码，或任务级动作缺少 taskId（前端可先拦，避免发一个必被拒的请求）
 */
export function compileActionRequest(input: CompileActionInput): CompiledActionRequest {
  const action = input.action
  const instancePath = `/flow-instances/${encodeURIComponent(input.instanceId)}`
  const taskId = (input.taskId ?? '').trim()

  if (action in TASK_ACTION_ENDPOINTS) {
    if (taskId === '') {
      throw new Error(`动作「${action}」需要「分配给我的待处理任务」，当前没有可取的任务 id（服务端会按 40910 拒绝）`)
    }
    const path = `/flow-tasks/${encodeURIComponent(taskId)}/${TASK_ACTION_ENDPOINTS[action]}`
    switch (action) {
      case 'approve':
        return {
          action,
          endpoint: path,
          successMessage: '已通过',
          run: async () => {
            await approveFlowTask(taskId, { opinion: input.opinion ?? '' })
          },
        }
      case 'reject':
        return {
          action,
          endpoint: path,
          successMessage: '已驳回',
          run: async () => {
            await rejectFlowTask(taskId, input.opinion ?? '')
          },
        }
      case 'archive_register':
        return {
          action,
          endpoint: path,
          successMessage: '已归档登记',
          run: async () => {
            await archiveRegisterFlowTask(taskId, input.opinion ?? '')
          },
        }
      case 'rollback':
        return {
          action,
          endpoint: path,
          successMessage: '已回退上一节点',
          run: async () => {
            await rollbackFlowTask(taskId, input.opinion ?? '')
          },
        }
      case 'back_home':
        return {
          action,
          endpoint: path,
          successMessage: '已回到本部门',
          run: async () => {
            await backHomeFlowTask(taskId, input.opinion ?? '')
          },
        }
      case 'route':
        return {
          action,
          endpoint: path,
          successMessage: '已流转',
          run: async () => {
            await routeFlowTask(taskId, { toDeptId: (input.toDeptId ?? '').trim(), reason: input.opinion ?? '' })
          },
        }
      case 'jump':
        return {
          action,
          endpoint: path,
          successMessage: '已跳转',
          run: async () => {
            await jumpFlowTask(taskId, {
              targetSeq: Number((input.targetSeq ?? '').trim()),
              reason: input.opinion ?? '',
            })
          },
        }
      case 'add_sign':
        return {
          action,
          endpoint: path,
          successMessage: '已加签',
          run: async () => {
            await addSignFlowTask(taskId, {
              type: input.addSignType === 'pre' ? 'pre' : 'post',
              delegateUserId: (input.delegateUserId ?? '').trim(),
              reason: input.opinion ?? '',
            })
          },
        }
      case 'supplement_request':
        return {
          action,
          endpoint: path,
          successMessage: '已请求补件',
          run: async () => {
            await supplementRequestFlowTask(taskId, input.opinion ?? '')
          },
        }
      case 'transfer':
        return {
          action,
          endpoint: path,
          successMessage: '已转办',
          run: async () => {
            await transferFlowTask(taskId, { toUserId: (input.toUserId ?? '').trim(), reason: input.opinion ?? '' })
          },
        }
      case 'reassign':
        return {
          action,
          endpoint: path,
          successMessage: '已改派',
          run: async () => {
            await reassignFlowTask(taskId, { toUserId: (input.toUserId ?? '').trim(), reason: input.opinion ?? '' })
          },
        }
      default:
        break
    }
  }

  switch (action) {
    case 'submit':
      return {
        action,
        endpoint: `${instancePath}/submit`,
        successMessage: '已提交',
        run: async () => {
          await submitFlowInstance(input.instanceId, input.opinion ?? '提交审批')
        },
      }
    case 'reopen':
      return {
        action,
        endpoint: `${instancePath}/reopen`,
        successMessage: '已回到草稿',
        run: async () => {
          await reopenFlowInstance(input.instanceId)
        },
      }
    case 'resubmit':
      return {
        action,
        endpoint: `${instancePath}/resubmit`,
        successMessage: '已重提（快照已重新解析）',
        run: async () => {
          await resubmitFlowInstance(input.instanceId, input.opinion ?? '驳回后重新提交')
        },
      }
    case 'withdraw':
      return {
        action,
        endpoint: `${instancePath}/withdraw`,
        successMessage: '已撤回（回到草稿）',
        run: async () => {
          await withdrawFlowInstance(input.instanceId, input.opinion ?? '')
        },
      }
    case 'terminate':
      return {
        action,
        endpoint: `${instancePath}/terminate`,
        successMessage: '已终止',
        run: async () => {
          await terminateFlowInstance(input.instanceId, input.opinion ?? '')
        },
      }
    case 'supplement_submit':
      return {
        action,
        endpoint: `${instancePath}/supplement`,
        successMessage: '补件已提交',
        run: async () => {
          await supplementSubmitFlowInstance(input.instanceId, input.opinion ?? '')
        },
      }
    case 'cc':
      return {
        action,
        endpoint: `${instancePath}/cc`,
        successMessage: '已登记抄送人',
        run: async () => {
          await addCcFlowInstance(input.instanceId, splitIds(input.ccUserIds))
        },
      }
    default:
      throw new Error(`未知的动作码「${action}」：` + '本前端未登记其端点，已拒绝发起请求（服务端动作面清单见 GET /flow-actions）')
  }
}
