/**
 * oa-web · 后端 workflow.definition / workflow.approver 域 DTO 镜像（wire 层）
 * ----------------------------------------------------------------------------
 * 逐字段对齐 oa-server 的
 *   · `com.oa.workflow.definition.api.dto.FlowDefinitionDtos`（2a.2，模板/节点/发布前校验）
 *   · `com.oa.workflow.approver.api.dto.ApproverDtos`（2a.3，解析规则清单与单规则解析）
 *
 * 契约来源（文档真源）：
 *   · `doc/templates.md` §1（四类模板 × 7 节点配置表）、§1.7（Q6/Q7 闸门五键）、
 *     §3.3（模板状态机）、§4.1（六步变更流程）、V-01…V-08（快照与锁版本）
 *   · `doc/enums.md` §2（主干 7 节点码）、§3（9 条审批人解析规则）§7（决议模式 / 签名策略）
 *   · `doc/prd-0.1.md` §5.4（决议模式与阈值）、§6.3（主干审批链）、§7（版本与快照）、
 *     6.4 REQ-FLOW-006 / AC-09（在途实例锁版本）
 *   · 控制器路由表见 `oa-server/.../FlowDefinitionController.java` 类注释
 *
 * ⚠ 序列化口径（全项目统一，见 `types/identity-wire.d.ts`）：
 *   `com.oa.common.config.JacksonConfig` 对 `Long`/`long`/`BigInteger` 注册了
 *   `ToStringSerializer`，因此 **id 在 JSON 里是字符串**（`"1"`）；
 *   `Integer`/`int`（seq / version / nodeCount / timeoutHours / candidateCount…）
 *   仍是 JSON number。映射层（`api/flow.ts`）统一 `String(...)` 成 id、`Number(...)` 成数值。
 *
 * ⚠⚠ 空值口径：`application.yml` 配了 `spring.jackson.default-property-inclusion: non_null`，
 *   因此**可空的引用类型字段（Long / Integer / Boolean / String / JsonNode / List）在后端为 null 时
 *   整个键会被省略**（实测：⑦ 归档节点的 `approverParam` / `skipCondition` 不出现在响应里）。
 *   本文件对这类字段一律写成 `?: T | null`；映射层必须把 `undefined` 归一为领域模型里的 `null`。
 *   基本类型（`boolean` / `int`）永远不会被省略，所以 `usableByNewInstance` / `readOnly` /
 *   `unlimited` / `v04Default` / `passed` / `requiredApprovals` 是必填字段。
 *
 * ⚠ 本文件是**忠实镜像**：后端改 DTO 时必须同步这里，页面只依赖 `types/flow.d.ts` 的领域模型。
 */
import type { WireId } from './identity-wire'

export type { WireId }

/**
 * `com.fasterxml.jackson.databind.JsonNode` 的 JSON 形态。
 *
 * 用于 `approverParam`（如 `{"role_code":"admin"}` / `{"user_ids":[1,2]}`）与
 * `skipCondition`（如 `{"field":"involve_cost","op":"eq","value":false}`）——
 * 二者是**后端不约束形状的自由 JSON**（形状由 `NodeDefinitionValidator` /
 * `SkipConditionEvaluator` 在服务端校验），因此这里给出递归联合类型而不是 `any`。
 */
export type WireJson = string | number | boolean | null | WireJson[] | { [key: string]: WireJson }

// ================================================================ Q6 / Q7 闸门配置

/**
 * `GatePolicyView` —— 模板级 Q6/Q7 闸门配置（`doc/templates.md` §1.7）。
 *
 * 语义：次数 `null`/`0` = 不限；`supplementDeadlineDays` `null` = 不设时限。
 * `unlimited` / `v04Default` 是后端给出的**可读判断**（前端直接渲染「不限」，
 * 不再自己拼口径，避免与 `FlowGatePolicy.isUnlimited()/isV04Default()` 判据漂移）。
 */
export interface WireGatePolicyView {
  /** 回退次数上限（`null`/`0` = 不限）；`Integer` → JSON number，为 null 时键被省略 */
  maxReturnCount?: number | null
  /** 补件次数上限（`null`/`0` = 不限） */
  maxSupplementCount?: number | null
  /** 补件时限天数（`null` = 不设时限；值域 1..365） */
  supplementDeadlineDays?: number | null
  /** `calendar` 自然日 / `working` 工作日；无时限时为 null（键省略） */
  supplementDeadlineType?: string | null
  /** `notify` 仅提醒 / `auto_pass` 自动通过 / `auto_return` 自动退回（恒非空） */
  onSupplementTimeout: string
  /** 次数是否不限（含补件无时限） */
  unlimited: boolean
  /** 是否等价于 V0.4 定稿默认值（5 / 3 / 3 工作日 / notify） */
  v04Default: boolean
}

/**
 * `GatePolicyRequest` —— `PUT /flow-templates/{id}/gate-policy` 请求体。
 *
 * 校验（`@Min`，失败回 `40001 参数校验失败`，HTTP 400）：
 *   · `maxReturnCount` / `maxSupplementCount` ≥ 0（0 = 不限）；
 *   · `supplementDeadlineDays` ≥ 1（**0 与负数一律拒绝**：不设时限请留空）。
 */
export interface WireGatePolicyRequest {
  maxReturnCount?: number | null
  maxSupplementCount?: number | null
  supplementDeadlineDays?: number | null
  supplementDeadlineType?: string | null
  onSupplementTimeout?: string | null
}

// ================================================================ 模板

/** `TemplateView` —— 模板列表项 / 详情 / 版本历史项 */
export interface WireTemplateView {
  id: WireId
  code: string
  name: string
  formType: string
  /** `Integer`（JSON number） */
  version: number
  /** `draft` / `published` / `archived`（`doc/templates.md` §3.3） */
  status: string
  /** `Integer`（JSON number） */
  nodeCount: number
  publishedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  gatePolicy: WireGatePolicyView
  /** 是否可被新实例使用（仅 published 为 true） */
  usableByNewInstance: boolean
  /** 是否只读（published / archived） */
  readOnly: boolean
}

/** `TemplateDetailView` —— 模板详情（含节点清单，按 seq） */
export interface WireTemplateDetailView {
  template: WireTemplateView
  nodes: WireNodeView[]
}

/** `NodeView` —— 节点配置视图（读接口返回；写接口也回同一个形状） */
export interface WireNodeView {
  id: WireId
  templateId: WireId
  /** `Integer`（JSON number） */
  seq: number
  nodeCode: string
  /** 主干节点的中文名（`doc/enums.md` §2）；非主干节点为 null（键省略） */
  nodeCodeLabel?: string | null
  name?: string | null
  /** `approve` / `cc` / `condition` / `archive` */
  nodeType?: string | null
  approverRule?: string | null
  /** 9 条解析规则的中文名（`doc/enums.md` §3） */
  approverRuleLabel?: string | null
  approverParam?: WireJson
  /** `any` 或签 / `all` 会签 / `sequence` 依次审批；⑦ 归档登记为 null */
  decisionMode?: string | null
  /** 阈值字面量：`"2"` 绝对人数 / `"66%"` 百分比 / null = 过半 */
  passThreshold?: string | null
  thresholdDescription?: string | null
  /** `required` 强制 / `optional` 可选 / `none` 不签名 */
  signPolicy?: string | null
  /** `Integer`（JSON number）；⑦ 等节点可为 null */
  timeoutHours?: number | null
  timeoutCcSuperior?: boolean | null
  allowAddSign?: boolean | null
  allowJump?: boolean | null
  allowRoute?: boolean | null
  skipCondition?: WireJson
  createdAt?: string | null
  updatedAt?: string | null
}

/** `NewVersionRequest` —— `POST /flow-templates/{id}/versions` 请求体 */
export interface WireNewVersionRequest {
  /** 源版本号；为空则取当前已发布版本 */
  fromVersion?: number | null
  /** 新版本名称；为空则沿用源版本名称（≤80 字符） */
  name?: string | null
}

/** `PublishRequest` —— `POST .../publish`、`POST .../archive` 请求体（原因可选，便于留痕） */
export interface WirePublishRequest {
  /** ≤255 字符 */
  reason?: string | null
}

/** `TemplateUpdateRequest` —— `PUT /flow-templates/{id}` 请求体（仅草稿可写） */
export interface WireTemplateUpdateRequest {
  /** ≤80 字符 */
  name?: string | null
  formSchemaJson?: WireJson
  gatePolicy?: WireGatePolicyRequest | null
}

// ================================================================ 节点写

/**
 * `NodeRequest` —— `POST /flow-templates/{id}/nodes`、`PUT /flow-nodes/{id}` 请求体。
 *
 * PUT 语义 = **全量覆盖**：显式传 `null` 即清空（`FlowDefinitionService.applyRequest`）。
 * 阈值三选一：`passThreshold` 字面量 / `thresholdAbsolute` / `thresholdPercent`，
 * 三者同时给出时 **绝对人数优先**（`doc/templates.md` T-07）。
 */
export interface WireNodeRequest {
  /** `Integer`：新增时的插入位置（1..现有节点数+1） */
  seq?: number | null
  /** 必填，≤32 字符，小写蛇形（服务端会 `trim().toLowerCase()`） */
  nodeCode: string
  /** ≤50 字符 */
  name?: string | null
  /** `approve` / `cc` / `archive`（`condition` 为二期预留，一期一律拒绝） */
  nodeType?: string | null
  /** 必填，9 条解析规则之一 */
  approverRule: string
  approverParam?: WireJson
  decisionMode?: string | null
  passThreshold?: string | null
  thresholdAbsolute?: number | null
  thresholdPercent?: number | null
  signPolicy?: string | null
  timeoutHours?: number | null
  timeoutCcSuperior?: boolean | null
  allowAddSign?: boolean | null
  allowJump?: boolean | null
  allowRoute?: boolean | null
  skipCondition?: WireJson
}

/**
 * `NodeOrderRequest` —— `PUT /flow-templates/{id}/nodes/order` 请求体。
 *
 * `nodeIds` 必须是当前模板节点的**全排列**（长度与集合都相等），否则 `40008`。
 * Java 侧是 `List<Long>`，JSON 里 id 是**字符串**（Long → ToStringSerializer）。
 */
export interface WireNodeOrderRequest {
  nodeIds: string[]
}

/** `NodeDecisionRequest` —— `PUT /flow-nodes/{id}/decision` 请求体 */
export interface WireNodeDecisionRequest {
  decisionMode?: string | null
  passThreshold?: string | null
  /** 与 `thresholdPercent` 同时给出时绝对人数优先 */
  thresholdAbsolute?: number | null
  thresholdPercent?: number | null
}

/** `DecisionResolveView` —— `POST /flow-nodes/{id}/decision/resolve?candidateCount=N` 出参 */
export interface WireDecisionResolveView {
  nodeId: WireId
  decisionMode?: string | null
  passThreshold?: string | null
  /** `Integer`（JSON number） */
  candidateCount?: number | null
  /** `int`（JSON number，恒非空；或签且无候选人时为 0） */
  requiredApprovals: number
  /** `any` / `absolute` / `percent` / `majority` */
  basis: string
  /** `false` = 阈值大于候选人数，该节点在当前候选人下**永远无法通过** */
  satisfiable: boolean
  description: string
}

/** `NodePolicyRequest` —— `PUT /flow-nodes/{id}/policy` 与 `.../policy/validate` 请求体 */
export interface WireNodePolicyRequest {
  signPolicy?: string | null
  /** `Integer`；服务端把 `0` 归一为 `null`（不设超时） */
  timeoutHours?: number | null
  timeoutCcSuperior?: boolean | null
  allowAddSign?: boolean | null
  allowJump?: boolean | null
  allowRoute?: boolean | null
}

/** `NodeSkipConditionRequest` —— `PUT /flow-nodes/{id}/skip-condition`（`null` = 清空） */
export interface WireNodeSkipConditionRequest {
  skipCondition?: WireJson
}

/** `NodeApproverRuleRequest` —— `PUT /flow-nodes/{id}/approver-rule` 请求体 */
export interface WireNodeApproverRuleRequest {
  approverRule: string
  approverParam?: WireJson
}

/** `ValidationView` —— 写接口的即时校验与各 `validate` 接口的共用出参 */
export interface WireValidationView {
  passed: boolean
  problems: string[]
  warnings: string[]
}

// ================================================================ 发布前校验

/** `CheckItemView` —— 单条校验规则结论（`status`：`pass` / `fail` / `warn`） */
export interface WireCheckItemView {
  rule: string
  title: string
  status: string
  details: string[]
}

/**
 * `PrePublishReportView` —— `POST /flow-designs/{id}/pre-publish-check` 出参。
 *
 * ⚠ 实测：`checks` 的顺序**不保证**与 `doc/templates.md`/`PrePublishChecker.RULES` 一致
 * （后端用 `Map.copyOf` 聚合，`Map.copyOf` 不保留插入顺序）。
 * 界面必须按 `GET /flow-designs/check-rules` 或前端权威顺序重排，见 `utils/flow.ts`
 * 的 `FLOW_CHECK_RULE_ORDER`。
 */
export interface WirePrePublishReportView {
  templateId: WireId
  code: string
  /** `Integer`（JSON number） */
  version: number
  status: string
  /** `problems` 为空即 true；**false 时必须禁用发布按钮** */
  passed: boolean
  generatedAt: string
  checks: WireCheckItemView[]
  /** 阻断发布的全部问题（规则已聚合过的原文） */
  problems: string[]
  /** 不阻止发布的提示项（如「已开启自由跳转」「Q6/Q7 未配置」） */
  warnings: string[]
}

/** `CheckRuleView` —— `GET /flow-designs/check-rules`（12 条规则清单） */
export interface WireCheckRuleView {
  rule: string
  title: string
}

/** `LockedInstanceView` —— 在途实例锁版本（`GET /flow-templates/{id}/locked-by`；本期后端未实现） */
export interface WireLockedInstanceView {
  instanceId: WireId
  bizNo?: string | null
  /** `Integer`（JSON number） */
  templateVersion?: number | null
  status?: string | null
}

// ================================================================ 审批人解析规则（2a.3）

/** `RuleView` —— `GET /api/v1/approver-rules` 单条（9 条，含出处章节） */
export interface WireApproverRuleView {
  rule: string
  label: string
  /** 权威出处章节（便于审计对照文档） */
  source: string
  /** 是否可用于主干节点（`collab_dept_leader` 为 false：只作②的并行子任务） */
  trunkUsable: boolean
  /** 是否必须给出 `approver_param`（`designated` 需要） */
  requiresParam: boolean
}

/** `CandidateView` —— 候选人（含姓名 / 工号 / 组织） */
export interface WireCandidateView {
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

/**
 * `RuleResolveRequest` —— `POST /api/v1/approver-rules/{ruleCode}/resolve` 请求体。
 *
 * 两种用法（见控制器注释）：
 *   1. 给 `templateId + nodeSeq`：用**真实节点配置**解析（设计期自检「这条规则现在能不能取到人」）；
 *   2. 只给发起上下文 + `approverParam`：用临时节点配置解析（联调/排障）。
 * `initiatorId` 为空 = 以**当前登录人**为发起人。
 */
export interface WireRuleResolveRequest {
  initiatorId?: WireId | null
  initiatorOrgId?: WireId | null
  initiatorCompanyId?: WireId | null
  category?: string | null
  initiatorPicks?: string[] | null
  collabDeptIds?: string[] | null
  collabSelfExcludeDeptIds?: string[] | null
  formValues?: Record<string, unknown> | null
  approverParam?: WireJson
  templateId?: WireId | null
  nodeSeq?: number | null
}

/** `RuleResolveView` —— 单规则解析结果 */
export interface WireRuleResolveView {
  rule: string
  label: string
  source: string
  /** 是否取到了候选人 */
  resolved: boolean
  evidence?: string | null
  candidates?: WireCandidateView[] | null
  /** 分组候选人（协同场景按部门分组） */
  groups?: WireCandidateView[][] | null
  /** 缺失的配置项（`resolved=false` 时给出「缺什么」） */
  missingConfig?: string[] | null
}
