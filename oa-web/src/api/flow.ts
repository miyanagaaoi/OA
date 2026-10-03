/**
 * oa-web · 流程域接口（流程模板 / 节点定义 / 发布前校验 / 审批人解析规则）
 * ----------------------------------------------------------------------------
 * 路径为 2a.2 / 2a.3 已交付的**定稿契约**（全部在 `/api/v1` 之下），
 * 本文件把 `types/flow-wire.d.ts`（后端 DTO 镜像）映射为领域模型 `types/flow.d.ts`。
 *
 * 后端实现：
 *   · `com.oa.workflow.definition.api.FlowDefinitionController`（路由表见其类注释）
 *   · `com.oa.workflow.approver.api.ApproverRuleController`
 *
 * ── 模板与版本 ─────────────────────────────────────────────────────────────
 *   GET    /api/v1/flow-templates                                  读 admin:flow:template
 *   GET    /api/v1/flow-templates/{templateId}                     读（含节点）
 *   GET    /api/v1/flow-templates/{templateId}/nodes               读
 *   GET    /api/v1/flow-templates/{templateId}/versions            读（同 code 全部版本，倒序）
 *   GET    /api/v1/flow-templates/{templateId}/versions/{version}  读（按版本查询）
 *   POST   /api/v1/flow-templates/{templateId}/versions            写 admin:flow:publish（开新草稿）
 *   POST   /api/v1/flow-templates/{templateId}/publish             写（发布；先跑校验，失败 40008）
 *   POST   /api/v1/flow-templates/{templateId}/archive             写（归档；只阻止新实例）
 *   PUT    /api/v1/flow-templates/{templateId}                     写（草稿元数据）
 *   PUT    /api/v1/flow-templates/{templateId}/gate-policy         写（**Q6/Q7 闸门配置**）
 * ── 节点增删改 ─────────────────────────────────────────────────────────────
 *   POST   /api/v1/flow-templates/{templateId}/nodes               写（新增节点）
 *   PUT    /api/v1/flow-templates/{templateId}/nodes/order         写（换序，全排列）
 *   PUT    /api/v1/flow-nodes/{nodeId}                             写（节点全量更新）
 *   DELETE /api/v1/flow-nodes/{nodeId}                             写（主干节点 40008）
 * ── 节点行为配置（读 template / 写 publish）────────────────────────────────
 *   GET|PUT /api/v1/flow-nodes/{nodeId}/decision
 *   POST    /api/v1/flow-nodes/{nodeId}/decision/resolve?candidateCount=N   读（纯计算）
 *   GET|PUT /api/v1/flow-nodes/{nodeId}/policy
 *   POST    /api/v1/flow-nodes/{nodeId}/policy/validate                     读（不落库）
 *   GET|PUT /api/v1/flow-nodes/{nodeId}/skip-condition
 *   POST    /api/v1/flow-nodes/{nodeId}/skip-condition/validate             读
 *   GET|PUT /api/v1/flow-nodes/{nodeId}/approver-rule
 *   POST    /api/v1/flow-nodes/{nodeId}/approver-rule/validate              读
 * ── 发布前校验 ─────────────────────────────────────────────────────────────
 *   POST   /api/v1/flow-designs/{templateId}/pre-publish-check              读
 *   GET    /api/v1/flow-designs/{templateId}/pre-publish-check/latest       读
 *   GET    /api/v1/flow-designs/check-rules                                 读（12 条规则）
 * ── 审批人解析规则（2a.3，全部要求 admin:flow:node）───────────────────────
 *   GET    /api/v1/approver-rules                                           读（9 条）
 *   POST   /api/v1/approver-rules/{ruleCode}/resolve                        读（单规则解析）
 *
 * 两条实现约定（与 `api/authz.ts` 同口径）：
 *   1. **映射层承担 Long → string**：id 在 JSON 里是字符串（见 `types/flow-wire.d.ts`），
 *      领域模型里 id 恒为 `string`，页面不做二次转换；
 *   2. **不做演示数据降级**：流程模板是**影响全集团审批链的配置**，静默回落演示数据
 *      会让管理员误判真实配置；接口不可用时页面显式报错（`VITE_USE_MOCK` 亦不参与本域）。
 */
import { del, get, post, put, type OaRequestConfig } from './http'
import type {
  WireApproverRuleView,
  WireCandidateView,
  WireCheckItemView,
  WireCheckRuleView,
  WireDecisionResolveView,
  WireGatePolicyRequest,
  WireGatePolicyView,
  WireNewVersionRequest,
  WireNodeApproverRuleRequest,
  WireNodeDecisionRequest,
  WireNodePolicyRequest,
  WireNodeRequest,
  WireNodeSkipConditionRequest,
  WireNodeView,
  WirePrePublishReportView,
  WirePublishRequest,
  WireRuleResolveRequest,
  WireRuleResolveView,
  WireTemplateDetailView,
  WireTemplateUpdateRequest,
  WireTemplateView,
  WireValidationView,
} from '@/types/flow-wire'
import type {
  FlowApproverRuleItem,
  FlowApproverRulePayload,
  FlowApproverRuleResolve,
  FlowApproverRuleResolvePayload,
  FlowCandidate,
  FlowCheckItem,
  FlowCheckRule,
  FlowCheckStatus,
  FlowDecisionMode,
  FlowDecisionPayload,
  FlowDecisionResolve,
  FlowDeadlineType,
  FlowFormType,
  FlowGatePolicy,
  FlowGatePolicyPayload,
  FlowJsonValue,
  FlowNode,
  FlowNodeCreatePayload,
  FlowNodePolicyPayload,
  FlowNodeType,
  FlowPrePublishReport,
  FlowSignPolicy,
  FlowTemplate,
  FlowTemplateDetail,
  FlowTemplateQuery,
  FlowTemplateStatus,
  FlowThresholdBasis,
  FlowTimeoutAction,
  FlowValidation,
} from '@/types/flow'

// ---------------------------------------------------------------------------
// 传输层包装：错误提示由页面**唯一呈现**（含业务码，见任务书硬要求 3）
// ---------------------------------------------------------------------------
/**
 * 关闭 `api/http.ts` 拦截器的统一 toast。
 *
 * <p>原因：拦截器只在 403/409/429/5xx 弹提示且**不带业务码**，而流程域的错误几乎都是
 * 「HTTP 200/400 + 业务码」（40008 主干不可删、40906 已发布只读、40907 草稿已存在…）。
 * 若两处都提示会一次操作弹两次；只留拦截器又会丢掉业务码。
 * 因此本域一律关闭拦截器提示，由页面调用 `reportApiErrorWithCode` 统一呈现。
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

function fput<T>(url: string, data?: unknown, config?: OaRequestConfig): Promise<T> {
  return put<T>(url, data, { ...NO_INTERCEPTOR_TOAST, ...config })
}

function fdel<T>(url: string, config?: OaRequestConfig): Promise<T> {
  return del<T>(url, { ...NO_INTERCEPTOR_TOAST, ...config })
}

// ---------------------------------------------------------------------------
// 映射工具
// ---------------------------------------------------------------------------
/** `Long` → 字符串 id（缺失 → 空串，页面按「无」渲染） */
function sid(value: string | number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

/** `Integer` / `int` → number（非法值退回 fallback） */
function num(value: number | string | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** 可空的 `Integer` → `number | null` */
function numOrNull(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 可空字符串 → `string | null`（空串归一为 null，避免「有值但为空」的二义） */
function strOrNull(value: string | null | undefined): string | null {
  return value === null || value === undefined || value.trim() === '' ? null : value
}

/** 可空布尔 → 恒为 boolean（后端 `Boolean` 列在 `non_null` 下可能被省略） */
function boolOrFalse(value: boolean | null | undefined): boolean {
  return value === true
}

/**
 * 模板状态收窄。**未知取值一律按 `archived` 处理**（fail-safe）：
 * `archived` 是只读且不可被新实例使用，比误判成 `draft`（可写）安全得多。
 */
function toStatus(value: string | null | undefined): FlowTemplateStatus {
  switch (value) {
    case 'draft':
    case 'published':
    case 'archived':
      return value
    default:
      return 'archived'
  }
}

function toFormType(value: string | null | undefined): FlowFormType {
  switch (value) {
    case 'matter':
    case 'fund':
    case 'contract':
    case 'seal':
      return value
    default:
      return 'matter'
  }
}

function toDecisionMode(value: string | null | undefined): FlowDecisionMode | null {
  switch (value) {
    case 'any':
    case 'all':
    case 'sequence':
      return value
    default:
      return null
  }
}

function toSignPolicy(value: string | null | undefined): FlowSignPolicy | null {
  switch (value) {
    case 'required':
    case 'optional':
    case 'none':
      return value
    default:
      return null
  }
}

function toNodeType(value: string | null | undefined): FlowNodeType | null {
  switch (value) {
    case 'approve':
    case 'cc':
    case 'condition':
    case 'archive':
      return value
    default:
      return null
  }
}

function toDeadlineType(value: string | null | undefined): FlowDeadlineType | null {
  switch (value) {
    case 'calendar':
    case 'working':
      return value
    default:
      return null
  }
}

/** 超时处理：后端缺省即 `notify`（与 V0.4「超时仅催办」逐字一致），未知取值同样回落 `notify` */
function toTimeoutAction(value: string | null | undefined): FlowTimeoutAction {
  switch (value) {
    case 'auto_pass':
    case 'auto_return':
    case 'notify':
      return value
    default:
      return 'notify'
  }
}

function toThresholdBasis(value: string | null | undefined): FlowThresholdBasis {
  switch (value) {
    case 'any':
    case 'absolute':
    case 'percent':
    case 'majority':
      return value
    default:
      return 'majority'
  }
}

function toCheckStatus(value: string | null | undefined): FlowCheckStatus {
  switch (value) {
    case 'pass':
    case 'fail':
      return value
    case 'warn':
      return 'warn'
    default:
      return 'fail' // 未知结论按「不通过」呈现：宁可多拦一次，也不放过未识别的失败
  }
}

/** `JsonNode` → 领域模型的自由 JSON（键被 `non_null` 省略时为 null） */
function toJsonValue(value: FlowJsonValue | undefined): FlowJsonValue {
  return value === undefined ? null : value
}

function toStringArray(value: string[] | null | undefined): string[] {
  return value ?? []
}

// ---------------------------------------------------------------------------
// wire → 领域模型
// ---------------------------------------------------------------------------

function mapGatePolicy(wire: WireGatePolicyView | null | undefined): FlowGatePolicy {
  return {
    maxReturnCount: numOrNull(wire?.maxReturnCount),
    maxSupplementCount: numOrNull(wire?.maxSupplementCount),
    supplementDeadlineDays: numOrNull(wire?.supplementDeadlineDays),
    supplementDeadlineType: toDeadlineType(wire?.supplementDeadlineType),
    onSupplementTimeout: toTimeoutAction(wire?.onSupplementTimeout),
    unlimited: wire?.unlimited === true,
    v04Default: wire?.v04Default === true,
  }
}

function mapTemplate(wire: WireTemplateView): FlowTemplate {
  return {
    templateId: sid(wire.id),
    code: wire.code,
    name: wire.name,
    formType: toFormType(wire.formType),
    version: num(wire.version, 1),
    status: toStatus(wire.status),
    nodeCount: num(wire.nodeCount),
    publishedAt: strOrNull(wire.publishedAt),
    createdAt: strOrNull(wire.createdAt),
    updatedAt: strOrNull(wire.updatedAt),
    gatePolicy: mapGatePolicy(wire.gatePolicy),
    usableByNewInstance: wire.usableByNewInstance === true,
    // `readOnly` 以服务端为准；状态本身也能推出只读，两者取「或」以防服务端字段缺失
    readOnly: wire.readOnly === true || toStatus(wire.status) !== 'draft',
  }
}

function mapNode(wire: WireNodeView): FlowNode {
  return {
    nodeId: sid(wire.id),
    templateId: sid(wire.templateId),
    seq: num(wire.seq, 0),
    nodeCode: wire.nodeCode,
    nodeCodeLabel: strOrNull(wire.nodeCodeLabel),
    name: strOrNull(wire.name),
    nodeType: toNodeType(wire.nodeType),
    approverRule: strOrNull(wire.approverRule),
    approverRuleLabel: strOrNull(wire.approverRuleLabel),
    approverParam: toJsonValue(wire.approverParam),
    decisionMode: toDecisionMode(wire.decisionMode),
    passThreshold: strOrNull(wire.passThreshold),
    thresholdDescription: strOrNull(wire.thresholdDescription),
    signPolicy: toSignPolicy(wire.signPolicy),
    timeoutHours: numOrNull(wire.timeoutHours),
    timeoutCcSuperior: boolOrFalse(wire.timeoutCcSuperior),
    allowAddSign: boolOrFalse(wire.allowAddSign),
    allowJump: boolOrFalse(wire.allowJump),
    allowRoute: boolOrFalse(wire.allowRoute),
    skipCondition: toJsonValue(wire.skipCondition),
    createdAt: strOrNull(wire.createdAt),
    updatedAt: strOrNull(wire.updatedAt),
  }
}

function mapTemplateDetail(wire: WireTemplateDetailView): FlowTemplateDetail {
  return {
    template: mapTemplate(wire.template),
    nodes: (wire.nodes ?? []).map(mapNode),
  }
}

function mapValidation(wire: WireValidationView): FlowValidation {
  return {
    passed: wire.passed === true,
    problems: toStringArray(wire.problems),
    warnings: toStringArray(wire.warnings),
  }
}

function mapCheckItem(wire: WireCheckItemView): FlowCheckItem {
  return {
    rule: wire.rule,
    title: wire.title,
    status: toCheckStatus(wire.status),
    details: toStringArray(wire.details),
  }
}

function mapPrePublishReport(wire: WirePrePublishReportView): FlowPrePublishReport {
  return {
    templateId: sid(wire.templateId),
    code: wire.code,
    version: num(wire.version, 1),
    status: toStatus(wire.status),
    passed: wire.passed === true,
    generatedAt: wire.generatedAt,
    checks: (wire.checks ?? []).map(mapCheckItem),
    problems: toStringArray(wire.problems),
    warnings: toStringArray(wire.warnings),
  }
}

function mapCandidate(wire: WireCandidateView): FlowCandidate {
  return {
    userId: sid(wire.userId),
    name: wire.name ?? '',
    account: wire.account ?? '',
    employeeNo: wire.employeeNo ?? '',
    orgId: sid(wire.orgId),
    orgName: wire.orgName ?? '',
    orgPath: wire.orgPath ?? '',
    companyId: sid(wire.companyId),
    position: wire.position ?? '',
  }
}

// ---------------------------------------------------------------------------
// 请求体组装（领域模型 → wire）
// ---------------------------------------------------------------------------

function gatePolicyBody(payload: FlowGatePolicyPayload): WireGatePolicyRequest {
  return {
    maxReturnCount: payload.maxReturnCount,
    maxSupplementCount: payload.maxSupplementCount,
    supplementDeadlineDays: payload.supplementDeadlineDays,
    supplementDeadlineType: payload.supplementDeadlineType,
    onSupplementTimeout: payload.onSupplementTimeout,
  }
}

function nodeBody(payload: FlowNodeCreatePayload): WireNodeRequest {
  return {
    seq: payload.seq,
    nodeCode: payload.nodeCode,
    name: payload.name,
    nodeType: payload.nodeType,
    approverRule: payload.approverRule,
    approverParam: payload.approverParam,
    decisionMode: payload.decisionMode,
    passThreshold: payload.passThreshold,
    signPolicy: payload.signPolicy,
    timeoutHours: payload.timeoutHours,
    timeoutCcSuperior: payload.timeoutCcSuperior,
    allowAddSign: payload.allowAddSign,
    allowJump: payload.allowJump,
    allowRoute: payload.allowRoute,
    skipCondition: payload.skipCondition,
  }
}

// ===========================================================================
// 模板查询
// ===========================================================================

/** `GET /flow-templates`：按 code / formType / status 过滤（全部为空 = 全量） */
export async function listFlowTemplates(query: FlowTemplateQuery = {}): Promise<FlowTemplate[]> {
  const params: Record<string, string> = {}
  if (query.code && query.code.trim() !== '') params.code = query.code.trim()
  if (query.formType) params.formType = query.formType
  if (query.status) params.status = query.status
  const data = await fget<WireTemplateView[]>('/flow-templates', { params })
  return (data ?? []).map(mapTemplate)
}

/** `GET /flow-templates/{templateId}`：模板详情（含节点，按 seq） */
export async function getFlowTemplate(templateId: string): Promise<FlowTemplateDetail> {
  const data = await fget<WireTemplateDetailView>(`/flow-templates/${templateId}`)
  return mapTemplateDetail(data)
}

/** `GET /flow-templates/{templateId}/nodes`：节点清单（按 seq） */
export async function listFlowTemplateNodes(templateId: string): Promise<FlowNode[]> {
  const data = await fget<WireNodeView[]>(`/flow-templates/${templateId}/nodes`)
  return (data ?? []).map(mapNode)
}

/** `GET /flow-templates/{templateId}/versions`：版本历史（同 code 全部版本，倒序） */
export async function listFlowTemplateVersions(templateId: string): Promise<FlowTemplate[]> {
  const data = await fget<WireTemplateView[]>(`/flow-templates/${templateId}/versions`)
  return (data ?? []).map(mapTemplate)
}

/** `GET /flow-templates/{templateId}/versions/{version}`：**按版本查询**（含该版本节点） */
export async function getFlowTemplateVersion(templateId: string, version: number): Promise<FlowTemplateDetail> {
  const data = await fget<WireTemplateDetailView>(`/flow-templates/${templateId}/versions/${version}`)
  return mapTemplateDetail(data)
}

// ===========================================================================
// 版本管理（写 admin:flow:publish）
// ===========================================================================

/**
 * `POST /flow-templates/{templateId}/versions`：基于已发布/已归档版本**开新草稿**（version+1）。
 *
 * 失败口径：已有草稿 → `40907`；基于草稿开版本 / 当前本身就是草稿 → `40008`。
 */
export async function createFlowTemplateVersion(
  templateId: string,
  payload: WireNewVersionRequest = {},
): Promise<FlowTemplate> {
  const data = await fpost<WireTemplateView>(`/flow-templates/${templateId}/versions`, payload)
  return mapTemplate(data)
}

/**
 * `POST /flow-templates/{templateId}/publish`：发布草稿。
 *
 * ⚠ 服务端会在**同一事务内重新跑发布前校验**（不信任 `pre-publish-check` 的缓存结果）：
 * 校验不通过 → `40008`（`message` 里带全部问题）；非草稿 → `40906`。
 * 发布成功后，同一 `code` 的原 `published` 版本自动转 `archived`（**在途实例不受影响**）。
 */
export async function publishFlowTemplate(templateId: string, reason?: string): Promise<FlowTemplate> {
  const body: WirePublishRequest = { reason: reason ?? null }
  const data = await fpost<WireTemplateView>(`/flow-templates/${templateId}/publish`, body)
  return mapTemplate(data)
}

/** `POST /flow-templates/{templateId}/archive`：归档（只阻止**新**实例使用，在途继续执行） */
export async function archiveFlowTemplate(templateId: string, reason?: string): Promise<FlowTemplate> {
  const body: WirePublishRequest = { reason: reason ?? null }
  const data = await fpost<WireTemplateView>(`/flow-templates/${templateId}/archive`, body)
  return mapTemplate(data)
}

/** `PUT /flow-templates/{templateId}`：草稿元数据（名称 / 表单定义 / 闸门配置）；非草稿 → 40906 */
export async function updateFlowTemplate(
  templateId: string,
  payload: WireTemplateUpdateRequest,
): Promise<FlowTemplate> {
  const data = await fput<WireTemplateView>(`/flow-templates/${templateId}`, payload)
  return mapTemplate(data)
}

/**
 * `PUT /flow-templates/{templateId}/gate-policy`：**Q6/Q7 闸门配置**写入（`doc/templates.md` §1.7）。
 *
 * 校验：次数 ≥0（0 = 不限）、天数 ≥1（0 与负数拒绝）→ 违反时 `40001`（`@Min`，HTTP 400）
 * 或 `40008`（`>99` / 天数 `>365` 等业务校验）；非草稿 → `40906`。
 */
export async function putFlowGatePolicy(
  templateId: string,
  payload: FlowGatePolicyPayload,
): Promise<FlowTemplate> {
  const data = await fput<WireTemplateView>(`/flow-templates/${templateId}/gate-policy`, gatePolicyBody(payload))
  return mapTemplate(data)
}

// ===========================================================================
// 节点增删改（写 admin:flow:publish）
// ===========================================================================

/** `POST /flow-templates/{templateId}/nodes`：新增节点（可指定插入位置 seq） */
export async function addFlowNode(templateId: string, payload: FlowNodeCreatePayload): Promise<FlowNode> {
  const data = await fpost<WireNodeView>(`/flow-templates/${templateId}/nodes`, nodeBody(payload))
  return mapNode(data)
}

/**
 * `PUT /flow-templates/{templateId}/nodes/order`：节点换序。
 *
 * ⚠ `nodeIds` 必须是当前模板节点的**全排列**（长度与集合都相等，且覆盖全部节点），
 * 否则 `40008`；换序后主干 7 节点必须落在固定 seq 上（`RequiredNodePolicy`），否则同样 `40008`。
 * 入参用前端算好的权威顺序（`utils/flow.ts` 的 `planReorder`）。
 */
export async function reorderFlowNodes(templateId: string, nodeIds: string[]): Promise<FlowNode[]> {
  const data = await fput<WireNodeView[]>(`/flow-templates/${templateId}/nodes/order`, { nodeIds })
  return (data ?? []).map(mapNode)
}

/** `PUT /flow-nodes/{nodeId}`：节点**全量更新**（PUT 语义 = 传 null 即清空） */
export async function updateFlowNode(nodeId: string, payload: FlowNodeCreatePayload): Promise<FlowNode> {
  const data = await fput<WireNodeView>(`/flow-nodes/${nodeId}`, nodeBody(payload))
  return mapNode(data)
}

/** `DELETE /flow-nodes/{nodeId}`：删除节点（**主干必填节点 → 40008**） */
export async function deleteFlowNode(nodeId: string): Promise<void> {
  await fdel<null>(`/flow-nodes/${nodeId}`)
}

// ===========================================================================
// 决议模式与阈值
// ===========================================================================

/** `GET /flow-nodes/{nodeId}/decision`：读取（返回整个 NodeView） */
export async function getFlowNodeDecision(nodeId: string): Promise<FlowNode> {
  const data = await fget<WireNodeView>(`/flow-nodes/${nodeId}/decision`)
  return mapNode(data)
}

/**
 * `PUT /flow-nodes/{nodeId}/decision`：写入决议模式与阈值。
 *
 * 阈值口径（`doc/templates.md` T-07）：绝对人数与百分比同时给出时**绝对人数优先**，
 * 由 `ThresholdPolicy.compose` 裁决；页面用 `composeThresholdLiteral` 预先合成。
 *
 * ⚠ `passThreshold` 的三态（2026-10-04 统一，**逐字同**后端
 * `NodeDecisionRequest#passThreshold`）：
 *   · `null`（字段省略或显式 JSON `null`）= **清空**（落库 `NULL`）；
 *   · 空串 / 全空白串 = **清空**（与 `null` 同义）；
 *   · 非空字面量（`"2"` / `"50%"`）= **写入**（去首尾空白）。
 * `thresholdAbsolute` / `thresholdPercent` 任一非空时优先走它们（T-07：绝对人数优先）。
 * 因此「会签 → 或签」传 `null` 与传 `""` 等价，都不会停在「或签 + 残留阈值」上（服务端 40008）。
 */
export async function putFlowNodeDecision(nodeId: string, payload: FlowDecisionPayload): Promise<FlowNode> {
  const body: WireNodeDecisionRequest = {
    decisionMode: payload.decisionMode,
    passThreshold: payload.passThreshold,
    thresholdAbsolute: payload.thresholdAbsolute,
    thresholdPercent: payload.thresholdPercent,
  }
  const data = await fput<WireNodeView>(`/flow-nodes/${nodeId}/decision`, body)
  return mapNode(data)
}

/**
 * `POST /flow-nodes/{nodeId}/decision/resolve?candidateCount=N`：按候选人集合解析通过条件。
 *
 * **纯计算，不写库、不产生任务**（设计器用「这个阈值在 N 个候选人下要几个人同意」的即时预览）。
 */
export async function resolveFlowNodeDecision(nodeId: string, candidateCount: number): Promise<FlowDecisionResolve> {
  const data = await fpost<WireDecisionResolveView>(`/flow-nodes/${nodeId}/decision/resolve`, undefined, {
    params: { candidateCount },
  })
  return {
    nodeId: sid(data.nodeId),
    decisionMode: toDecisionMode(data.decisionMode),
    passThreshold: strOrNull(data.passThreshold),
    candidateCount: num(data.candidateCount),
    requiredApprovals: num(data.requiredApprovals),
    basis: toThresholdBasis(data.basis),
    satisfiable: data.satisfiable === true,
    description: data.description ?? '',
  }
}

// ===========================================================================
// 签名策略 / 超时 / 开关
// ===========================================================================

/** `GET /flow-nodes/{nodeId}/policy`：读取（返回整个 NodeView） */
export async function getFlowNodePolicy(nodeId: string): Promise<FlowNode> {
  const data = await fget<WireNodeView>(`/flow-nodes/${nodeId}/policy`)
  return mapNode(data)
}

/** `PUT /flow-nodes/{nodeId}/policy`：写入签名策略 / 超时 / 加签 / 跳转 / 流转开关（**不产生签名与定时器**） */
export async function putFlowNodePolicy(nodeId: string, payload: FlowNodePolicyPayload): Promise<FlowNode> {
  const body: WireNodePolicyRequest = {
    signPolicy: payload.signPolicy,
    timeoutHours: payload.timeoutHours,
    timeoutCcSuperior: payload.timeoutCcSuperior,
    allowAddSign: payload.allowAddSign,
    allowJump: payload.allowJump,
    allowRoute: payload.allowRoute,
  }
  const data = await fput<WireNodeView>(`/flow-nodes/${nodeId}/policy`, body)
  return mapNode(data)
}

/** `POST /flow-nodes/{nodeId}/policy/validate`：策略校验（**不落库**，供设计器即时反馈） */
export async function validateFlowNodePolicy(
  nodeId: string,
  payload: FlowNodePolicyPayload,
): Promise<FlowValidation> {
  const body: WireNodePolicyRequest = {
    signPolicy: payload.signPolicy,
    timeoutHours: payload.timeoutHours,
    timeoutCcSuperior: payload.timeoutCcSuperior,
    allowAddSign: payload.allowAddSign,
    allowJump: payload.allowJump,
    allowRoute: payload.allowRoute,
  }
  const data = await fpost<WireValidationView>(`/flow-nodes/${nodeId}/policy/validate`, body)
  return mapValidation(data)
}

// ===========================================================================
// 跳过条件
// ===========================================================================

/** `GET /flow-nodes/{nodeId}/skip-condition`：读取（返回整个 NodeView） */
export async function getFlowNodeSkipCondition(nodeId: string): Promise<FlowNode> {
  const data = await fget<WireNodeView>(`/flow-nodes/${nodeId}/skip-condition`)
  return mapNode(data)
}

/** `PUT /flow-nodes/{nodeId}/skip-condition`：写入跳过条件（`null` = 清空；仅事项单②可跳过） */
export async function putFlowNodeSkipCondition(
  nodeId: string,
  skipCondition: FlowJsonValue,
): Promise<FlowNode> {
  const body: WireNodeSkipConditionRequest = { skipCondition }
  const data = await fput<WireNodeView>(`/flow-nodes/${nodeId}/skip-condition`, body)
  return mapNode(data)
}

/** `POST /flow-nodes/{nodeId}/skip-condition/validate`：校验（字段存在性 + 操作符白名单，**不落库**） */
export async function validateFlowNodeSkipCondition(
  nodeId: string,
  skipCondition: FlowJsonValue,
): Promise<FlowValidation> {
  const body: WireNodeSkipConditionRequest = { skipCondition }
  const data = await fpost<WireValidationView>(`/flow-nodes/${nodeId}/skip-condition/validate`, body)
  return mapValidation(data)
}

// ===========================================================================
// 审批人解析规则（节点级）
// ===========================================================================

/** `GET /flow-nodes/{nodeId}/approver-rule`：读取（返回整个 NodeView） */
export async function getFlowNodeApproverRule(nodeId: string): Promise<FlowNode> {
  const data = await fget<WireNodeView>(`/flow-nodes/${nodeId}/approver-rule`)
  return mapNode(data)
}

/** `PUT /flow-nodes/{nodeId}/approver-rule`：写入解析规则与参数（`approverRule` 必填） */
export async function putFlowNodeApproverRule(
  nodeId: string,
  payload: FlowApproverRulePayload,
): Promise<FlowNode> {
  const body: WireNodeApproverRuleRequest = {
    approverRule: payload.approverRule,
    approverParam: payload.approverParam,
  }
  const data = await fput<WireNodeView>(`/flow-nodes/${nodeId}/approver-rule`, body)
  return mapNode(data)
}

/** `POST /flow-nodes/{nodeId}/approver-rule/validate`：校验（**不落库**） */
export async function validateFlowNodeApproverRule(
  nodeId: string,
  payload: FlowApproverRulePayload,
): Promise<FlowValidation> {
  const body: WireNodeApproverRuleRequest = {
    approverRule: payload.approverRule,
    approverParam: payload.approverParam,
  }
  const data = await fpost<WireValidationView>(`/flow-nodes/${nodeId}/approver-rule/validate`, body)
  return mapValidation(data)
}

// ===========================================================================
// 审批人解析规则清单与单规则解析（2a.3；两者都要求 admin:flow:node）
// ===========================================================================

/**
 * `GET /api/v1/approver-rules`：9 条解析规则清单（含出处章节）。
 *
 * ⚠ 该接口在后端要求 **`admin:flow:node`**（写权限，见控制器类注释），
 * 因此只有 `admin:flow:template` 的账号调用会 403；页面按 `canWriteFlowNode` 决定是否调用。
 */
export async function listApproverRules(): Promise<FlowApproverRuleItem[]> {
  const data = await fget<WireApproverRuleView[]>('/approver-rules')
  return (data ?? []).map((item) => ({
    rule: item.rule,
    label: item.label,
    source: item.source,
    trunkUsable: item.trunkUsable === true,
    requiresParam: item.requiresParam === true,
  }))
}

/**
 * `POST /api/v1/approver-rules/{ruleCode}/resolve`：单规则解析（设计期自检「现在能不能取到人」）。
 *
 * 给 `templateId + nodeSeq` 时用**真实节点配置**解析（含 `approver_param`）；
 * `initiatorId` 省略 = 以当前登录人为发起人。
 */
export async function resolveApproverRule(
  ruleCode: string,
  payload: FlowApproverRuleResolvePayload = {},
): Promise<FlowApproverRuleResolve> {
  const body: WireRuleResolveRequest = {
    templateId: payload.templateId ?? null,
    nodeSeq: payload.nodeSeq ?? null,
    approverParam: payload.approverParam ?? null,
    category: payload.category ?? null,
  }
  const data = await fpost<WireRuleResolveView>(`/approver-rules/${ruleCode}/resolve`, body)
  return {
    rule: data.rule,
    label: data.label,
    source: data.source,
    resolved: data.resolved === true,
    evidence: data.evidence ?? '',
    candidates: (data.candidates ?? []).map(mapCandidate),
    groups: (data.groups ?? []).map((group) => (group ?? []).map(mapCandidate)),
    missingConfig: toStringArray(data.missingConfig),
  }
}

// ===========================================================================
// 发布前校验
// ===========================================================================

/**
 * `POST /flow-designs/{templateId}/pre-publish-check`：发布前 dry-run 校验报告（12 条规则逐条结论）。
 *
 * ⚠ `checks[]` 的顺序后端不保证（`Map.copyOf` 不保留插入顺序），
 * 页面必须用 `utils/flow.ts` 的 `sortCheckItems` 按权威顺序重排。
 */
export async function prePublishCheck(templateId: string): Promise<FlowPrePublishReport> {
  const data = await fpost<WirePrePublishReportView>(`/flow-designs/${templateId}/pre-publish-check`)
  return mapPrePublishReport(data)
}

/** `GET /flow-designs/{templateId}/pre-publish-check/latest`：最近一次校验结果（未跑过则后端即时跑一次） */
export async function latestPrePublishCheck(templateId: string): Promise<FlowPrePublishReport> {
  const data = await fget<WirePrePublishReportView>(`/flow-designs/${templateId}/pre-publish-check/latest`)
  return mapPrePublishReport(data)
}

/** `GET /flow-designs/check-rules`：12 条校验规则清单（后端顺序同样不保证，页面自行重排） */
export async function listCheckRules(): Promise<FlowCheckRule[]> {
  const data = await fget<WireCheckRuleView[]>('/flow-designs/check-rules')
  return (data ?? []).map((item) => ({ rule: item.rule, title: item.title }))
}
