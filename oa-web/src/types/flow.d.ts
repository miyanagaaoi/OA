/**
 * oa-web · 流程设计器领域模型
 * ----------------------------------------------------------------------------
 * 由 `api/flow.ts` 从 `types/flow-wire.d.ts`（后端 DTO 镜像）映射而来；
 * 页面**只依赖本文件**，后端改字段名时只改映射层。
 *
 * 映射层的三件事（与 `api/authz.ts` 同口径）：
 *   1. `Long`/`long` → JSON 字符串 → 本模型里 id 恒为 `string`；
 *      `Integer`/`int`（seq / version / nodeCount / timeoutHours）→ `number`；
 *   2. 枚举 code 收窄成联合类型（`draft|published|archived`、`any|all|sequence` …），
 *      未知取值回落到 `null` 并保留原文（`raw`）以便如实呈现，不静默改写；
 *   3. `undefined`（后端 `non_null` 省略的键）统一归一为 `null`。
 *
 * 真源：`doc/templates.md` §1 / §1.7 / §3.3 / §4.1、`doc/enums.md` §2 / §3 / §7。
 */

/** 模板状态机（`doc/templates.md` §3.3） */
export type FlowTemplateStatus = 'draft' | 'published' | 'archived'

/** 单据类型码（`doc/enums.md` §10.2；`flow_template.code` 与 `form_type` 一致） */
export type FlowFormType = 'matter' | 'fund' | 'contract' | 'seal'

/** 流程节点码（主干 7 个，`doc/enums.md` §2） */
export type FlowTrunkNodeCode =
  | 'dept_leader'
  | 'finance_review'
  | 'branch_leader'
  | 'subsidiary_gm'
  | 'group_leader'
  | 'chairman'
  | 'archive_register'

/** `flow_node.node_type`（`doc/data-model.md` §4.2；`condition` 为二期预留） */
export type FlowNodeType = 'approve' | 'cc' | 'condition' | 'archive'

/** 节点决议模式（`doc/prd-0.1.md` §5.4） */
export type FlowDecisionMode = 'any' | 'all' | 'sequence'

/** 节点签名策略（`doc/templates.md` §1.0） */
export type FlowSignPolicy = 'required' | 'optional' | 'none'

/** 补件时限口径（`doc/templates.md` §1.7） */
export type FlowDeadlineType = 'calendar' | 'working'

/** 补件超时处理（`doc/templates.md` §1.7） */
export type FlowTimeoutAction = 'notify' | 'auto_pass' | 'auto_return'

/** 阈值判定依据（`ThresholdPolicy.Threshold.basis`） */
export type FlowThresholdBasis = 'any' | 'absolute' | 'percent' | 'majority'

/** 发布前校验单条结论（`CheckItemView.status`） */
export type FlowCheckStatus = 'pass' | 'fail' | 'warn'

/**
 * 自由 JSON 值（`approverParam` / `skipCondition`）。
 *
 * 与 wire 层同形：不引入 `any`，形状由服务端校验器负责。
 */
export type FlowJsonValue = string | number | boolean | null | FlowJsonValue[] | { [key: string]: FlowJsonValue }

/** Q6 / Q7 闸门配置（模板级；`doc/templates.md` §1.7 的五键 + 两个可读判断） */
export interface FlowGatePolicy {
  /** 回退次数上限；`null` = 不限（`0` 亦为不限，映射层归一为 `null`） */
  maxReturnCount: number | null
  /** 补件次数上限；`null` = 不限 */
  maxSupplementCount: number | null
  /** 补件时限天数；`null` = 不设时限（值域 1..365） */
  supplementDeadlineDays: number | null
  /** 补件时限口径；无时限时为 `null` */
  supplementDeadlineType: FlowDeadlineType | null
  /** 超时处理：恒有值（后端缺省 `notify`） */
  onSupplementTimeout: FlowTimeoutAction
  /** 次数是否不限（含补件无时限）——后端判断，界面直接渲染「不限」 */
  unlimited: boolean
  /** 是否等价于 V0.4 定稿默认值（5 / 3 / 3 工作日 / notify） */
  v04Default: boolean
}

/** 闸门配置写入入参（0 或留空 = 不限；天数留空 = 不设时限） */
export interface FlowGatePolicyPayload {
  maxReturnCount: number | null
  maxSupplementCount: number | null
  supplementDeadlineDays: number | null
  supplementDeadlineType: FlowDeadlineType | null
  onSupplementTimeout: FlowTimeoutAction
}

/** 模板（列表项 / 版本历史项 / 详情头） */
export interface FlowTemplate {
  templateId: string
  code: string
  name: string
  formType: FlowFormType
  /** 版本号（`flow_template.version`，每次发布 +1，V-08 单调递增） */
  version: number
  status: FlowTemplateStatus
  /** 节点数（后端 `node_count` 冗余列） */
  nodeCount: number
  publishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
  gatePolicy: FlowGatePolicy
  /** 是否可被新实例使用（仅 published 为 true，AC-09） */
  usableByNewInstance: boolean
  /** 是否只读（published / archived）——**后端会以 40906 拒绝一切写入** */
  readOnly: boolean
}

/** 模板详情（含节点清单） */
export interface FlowTemplateDetail {
  template: FlowTemplate
  nodes: FlowNode[]
}

/** 节点（`flow_node` 一行） */
export interface FlowNode {
  nodeId: string
  templateId: string
  /** 主干序号（1–7；非主干节点排在 7 之后） */
  seq: number
  nodeCode: string
  /** 主干节点码的中文名（`doc/enums.md` §2）；非主干为 `null` */
  nodeCodeLabel: string | null
  name: string | null
  nodeType: FlowNodeType | null
  /** 9 条解析规则之一（`doc/enums.md` §3） */
  approverRule: string | null
  approverRuleLabel: string | null
  /** 解析规则参数（`designated` 的 `{"user_ids":[…]}` / `{"role_code":"…"}`） */
  approverParam: FlowJsonValue
  /** ⑦ 归档登记为 `null`（登记节点无决议，B-01） */
  decisionMode: FlowDecisionMode | null
  /** 阈值字面量：`"2"` 绝对人数 / `"66%"` 百分比 / `null` = 过半（T-07） */
  passThreshold: string | null
  thresholdDescription: string | null
  signPolicy: FlowSignPolicy | null
  timeoutHours: number | null
  timeoutCcSuperior: boolean
  allowAddSign: boolean
  allowJump: boolean
  allowRoute: boolean
  skipCondition: FlowJsonValue
  createdAt: string | null
  updatedAt: string | null
}

/** 新增节点入参（`POST /flow-templates/{id}/nodes`） */
export interface FlowNodeCreatePayload {
  /** 插入位置（1..现有节点数+1）；省略则追加到末尾 */
  seq: number | null
  nodeCode: string
  name: string | null
  nodeType: FlowNodeType
  approverRule: string
  approverParam: FlowJsonValue
  decisionMode: FlowDecisionMode | null
  passThreshold: string | null
  signPolicy: FlowSignPolicy | null
  timeoutHours: number | null
  timeoutCcSuperior: boolean
  allowAddSign: boolean
  allowJump: boolean
  allowRoute: boolean
  skipCondition: FlowJsonValue
}

/** 决议模式与阈值入参（`PUT /flow-nodes/{id}/decision`） */
export interface FlowDecisionPayload {
  decisionMode: FlowDecisionMode
  /**
   * 阈值**字面量**（`"2"` 绝对人数 / `"50%"` 百分比）。
   *
   * ⚠ 三态（2026-10-04 统一，**逐字同**后端 `NodeDecisionRequest#passThreshold`）：
   *   · `null`（字段省略或显式 JSON `null`）= **清空**（落库 `NULL`）；
   *   · 空串 / 全空白串 = **清空**（与 `null` 同义）；
   *   · 非空字面量 = **写入**（去首尾空白）。
   * `thresholdAbsolute` / `thresholdPercent` 任一非空时优先走它们（T-07：绝对人数优先）。
   * 「会签 → 或签」传 `null` 即可清空，不会停在「或签 + 残留阈值」的非法组合上（服务端 40008）。
   */
  passThreshold: string | null
  /** 绝对人数阈值（与百分比同时给出时**绝对人数优先**，T-07） */
  thresholdAbsolute: number | null
  thresholdPercent: number | null
}

/** 决议解析结果（`POST /flow-nodes/{id}/decision/resolve`） */
export interface FlowDecisionResolve {
  nodeId: string
  decisionMode: FlowDecisionMode | null
  passThreshold: string | null
  candidateCount: number
  /** 需要多少人同意（或签恒为 0/1） */
  requiredApprovals: number
  basis: FlowThresholdBasis
  /** `false` = 阈值大于候选人数，该节点在当前候选人下**永远无法通过** */
  satisfiable: boolean
  description: string
}

/** 签名策略 / 超时 / 开关入参（`PUT /flow-nodes/{id}/policy`） */
export interface FlowNodePolicyPayload {
  signPolicy: FlowSignPolicy
  /** 小时；`null` = 不设超时（服务端把 0 也归一为 null） */
  timeoutHours: number | null
  timeoutCcSuperior: boolean
  allowAddSign: boolean
  allowJump: boolean
  allowRoute: boolean
}

/** 审批人解析规则入参（`PUT /flow-nodes/{id}/approver-rule`） */
export interface FlowApproverRulePayload {
  approverRule: string
  approverParam: FlowJsonValue
}

/** 校验结论（各 `validate` 接口与写接口的即时校验） */
export interface FlowValidation {
  passed: boolean
  problems: string[]
  warnings: string[]
}

/** 发布前校验：单条规则结论 */
export interface FlowCheckItem {
  rule: string
  title: string
  status: FlowCheckStatus
  details: string[]
}

/** 发布前校验报告（12 条规则逐条结论 + 阻断项 + 提示项） */
export interface FlowPrePublishReport {
  templateId: string
  code: string
  version: number
  status: FlowTemplateStatus
  /** `false` → 必须禁用「发布」按钮 */
  passed: boolean
  generatedAt: string
  checks: FlowCheckItem[]
  problems: string[]
  warnings: string[]
}

/** 校验规则清单项（`GET /flow-designs/check-rules`） */
export interface FlowCheckRule {
  rule: string
  title: string
}

/** 审批人解析规则清单项（`GET /api/v1/approver-rules`，9 条） */
export interface FlowApproverRuleItem {
  rule: string
  label: string
  /** 权威出处章节 */
  source: string
  trunkUsable: boolean
  requiresParam: boolean
}

/** 候选人（解析预览用） */
export interface FlowCandidate {
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

/** 单规则解析结果（`POST /api/v1/approver-rules/{ruleCode}/resolve`） */
export interface FlowApproverRuleResolve {
  rule: string
  label: string
  source: string
  resolved: boolean
  evidence: string
  candidates: FlowCandidate[]
  groups: FlowCandidate[][]
  missingConfig: string[]
}

/** 单规则解析入参（设计期自检：给 `templateId + nodeSeq` 用真实节点配置解析） */
export interface FlowApproverRuleResolvePayload {
  templateId?: string
  nodeSeq?: number
  approverParam?: FlowJsonValue
  category?: string
}

/** 模板列表筛选条件（全部为空 = 全量） */
export interface FlowTemplateQuery {
  code?: string
  formType?: FlowFormType
  status?: FlowTemplateStatus
}
