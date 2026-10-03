/**
 * oa-web · 后端 form 域 DTO 镜像（wire 层）
 * ----------------------------------------------------------------------------
 * 逐字段对齐 oa-server 的
 *   · `com.oa.form.api.dto.FormDtos`（2b.1–2b.4：写入请求 / 字典 / 校验报告）
 *   · `com.oa.form.api.FormTemplateController`（schema 与字典路由）
 *   · `com.oa.form.api.FormDataController`（实例读写与归还登记）
 *   · `com.oa.form.api.FormRuleController`（四类单据专属判定 + 字段分组）
 *   · `com.oa.form.template.schema.FormFieldDef#view()` / `FormSchema#view()`（字段与分组出参）
 *   · `com.oa.form.template.snapshot.FormSnapshotService#snapshotView`（快照出参）
 *   · `com.oa.form.template.writemodel.WriteContext#view()`（三态可写字段出参）
 *   · `com.oa.workflow.approver.api.dto.ApproverDtos.PrecheckReportView`（发起前预检出参）
 *
 * 契约来源（文档真源）：
 *   · `doc/templates.md` §2.1（schema 顶层键）、§2.2（字段项结构）、§2.5（未知键 / 状态白名单）、
 *     §3.1（三层版本）、§3.2 V-01/V-02/V-03/V-08
 *   · `doc/enums.md` §11（14 种字段类型）、§10.2（四类单据 form_type 取值）
 *   · `doc/forms.md` §1.2（三态读写模型）、§1.3（必填）、§1.5（金额定点）、
 *     §5（印鉴单归还唯一例外）、§11.1/§11.2（表单驱动与二次校验）
 *   · `doc/prd-0.1.md` §5.3（金额对非财务角色只读）、§6.1（事项单分支）
 *
 * ⚠ 序列化口径（全项目统一，见 `types/flow-wire.d.ts` 与 `types/identity-wire.d.ts`）：
 *   `com.oa.common.config.JacksonConfig` 对 `Long`/`long` 注册了 `ToStringSerializer`，
 *   因此 **id 在 JSON 里是字符串**（`"46"`）；`Integer`/`int`（schemaVersion / issueCount /
 *   sortNo / nodeSeq / requiredApprovals…）仍是 JSON number。
 *
 * ⚠⚠ 空值口径：`spring.jackson.default-property-inclusion: non_null`，因此**可空引用类型字段
 *   在后端为 null 时整个键被省略**。本文件对这类字段一律写成 `?: T | null`；
 *   映射层（`api/form.ts`）必须把 `undefined` 归一为领域模型里的 `null` / 空串。
 *
 * ⚠ 本文件是**忠实镜像**：后端改 DTO 时必须同步这里，页面只依赖 `types/form.d.ts` 的领域模型。
 */
import type { WireId } from './identity-wire'

export type { WireId }

/**
 * `com.fasterxml.jackson.databind.JsonNode` 的 JSON 形态（自由 JSON，**不用 `any`**）。
 *
 * 用于 `linkage`（条件联动）与 `rules[].*` 这类后端不约束形状的自由 JSON。
 */
export type WireJson = string | number | boolean | null | WireJson[] | { [key: string]: WireJson }

// ================================================================ 字典（2b.4）

/** `DictTypeView` —— `GET /forms/dicts` 单条（8 类白名单 + 行数）。 */
export interface WireDictTypeView {
  dictType: string
  label: string
  /** `int`（JSON number） */
  itemCount: number
  evidence: string
}

/** `DictItemView` —— `GET /forms/dicts/{dictType}/items` 单条（**仅启用项**，按 `sortNo`）。 */
export interface WireDictItemView {
  dictType: string
  itemCode: string
  itemName: string
  itemNameEn?: string | null
  /** `Integer`（JSON number，键在 null 时被省略） */
  sortNo?: number | null
  status: string
}

// ================================================================ 表单模板 schema（2b.1）

/** `FormFieldDef.Option` —— `fields[].options[]` 项。 */
export interface WireFieldOptionView {
  code: string
  label: string
  enabled: boolean
  /** `int`（JSON number） */
  sortNo: number
}

/**
 * `FormFieldDef#view()` —— 单个字段定义出参（`doc/templates.md` §2.2）。
 *
 * 键的存在性本身就是语义：
 *   · `dictType` 只在 `optionsSource.dictType` 存在时出现 → 选项**必须**取自
 *     `GET /forms/dicts/{dictType}/items`，前端不得硬编码；
 *   · `options` 只在模板内联了静态选项时出现（与 `dictType` 互斥）；
 *   · `defaultValue` 只在模板**声明了**该键时出现（可能是 `null`）；
 *   · `rules` 是**规则类型名数组**（不是规则体），条件必填等细则在服务端；
 *   · `type` 未知取值时后端原样回显模板里的原始文本（`FormFieldDef#view()` 的
 *     `type == null ? rawType : type.code()`），因此这里不能收窄成 14 值联合。
 */
export interface WireFormFieldView {
  code: string
  label: string
  printLabel: string
  printVisible: boolean
  /** 14 种字段类型之一（未知类型时为模板原文，见类注释） */
  type: string
  required: boolean
  /** `int`（JSON number）：非文本类型为 0 = 不适用 */
  maxLength: number
  /** 任何状态都不可编辑（仅展示 `defaultValue`） */
  locked: boolean
  /** 提交发起后是否只读 */
  readonlyAfterSubmit: boolean
  /** 仅选项类字段带字典绑定 */
  dictType?: string | null
  /** 仅模板内联静态选项时出现 */
  options?: WireFieldOptionView[] | null
  /** 仅模板声明了 `defaultValue` 时出现（值可能为 `null`） */
  defaultValue?: WireJson
  placeholder?: string | null
  unit?: string | null
  /** 规则**类型名**清单（顺序即服务端执行顺序） */
  rules: string[]
}

/** `FormSchema.Section` —— `sections[]` 一项（**只给字段码**，字段体在 `fields[]`）。 */
export interface WireFormSectionView {
  id: string
  title: string
  printTitle: string
  collapsible: boolean
  /** 字段 `code` 列表 */
  fields: string[]
}

/**
 * `FormSchema#view()` —— `GET /forms/templates/{formType}/schema` 与
 * `GET /forms/instances/{id}/schema` 的共用出参。
 *
 * `publishedAt` 是 `String`（后端 `published_at` 原样输出，可能为 null）；`schemaVersion`
 * 必须等于对应 `flow_template.version`（V-08）。
 */
export interface WireFormSchemaView {
  formType: string
  templateCode: string
  /** `int`（JSON number） */
  schemaVersion: number
  publishedAt?: string | null
  sections: WireFormSectionView[]
  /** **顺序即界面与打印稿的字段顺序** */
  fields: WireFormFieldView[]
}

/** `GET /forms/instances/{id}/schema` 在 schema 之上追加的锁定版本证据字段。 */
export interface WireInstanceSchemaView extends WireFormSchemaView {
  instanceId: WireId
  templateId: WireId
  /** `Integer`（JSON number） */
  templateVersion: number
  lockedVersionEvidence: string
}

// ================================================================ 字段分组（2b.3）

/** `GET /forms/{formType}/field-groups` 的分组项（字段体是**完整** `FormFieldDef.view()`）。 */
export interface WireFieldGroupSectionView {
  id: string
  title: string
  printTitle: string
  collapsible: boolean
  fields: WireFormFieldView[]
}

/** `FormDataService.amountPolicyOf` —— 金额角色的只读披露（**不是**裁决）。 */
export interface WireAmountPolicyView {
  writable: boolean
  exportable: boolean
  writableRoles: string[]
}

/** `GET /forms/{formType}/field-groups` 出参。 */
export interface WireFieldGroupView {
  formType: string
  /** `int`（JSON number） */
  schemaVersion: number
  sections: WireFieldGroupSectionView[]
  amountPolicy: WireAmountPolicyView
}

// ================================================================ 三态读写（2b.2）

/**
 * `WriteContext#view()` —— 服务端算出的**可写字段白名单**与状态标签。
 *
 * 这是「前端置灰提示」的权威输入，但**不是边界**：边界在 `FormStateWriteGuard#assertWritable`
 * （越权写入 403 / `40304`）。`state` 四态：`DRAFT` / `APPROVING` / `PENDING_SUPPLEMENT` / `CLOSED`。
 */
export interface WireFormWriteStateView {
  state: string
  /** `MATTER` / `FUND` / `CONTRACT` / `SEAL` */
  formType: string
  isInitiator: boolean
  isArchiveNode: boolean
  writableFields: string[]
  readonlyFields: string[]
  stateLabel: string
  evidence: string
}

/** `snapshotView` —— 表单快照（字段值 + 选项码的中文名解析）。 */
export interface WireFormSnapshotView {
  formType: string
  templateCode: string
  /** `int`（JSON number） */
  schemaVersion: number
  /** 字段值（键 = 字段 `code`）；金额/数字一律**字符串**回读（`FormSnapshotService#jsonValue`） */
  fields: Record<string, WireJson>
  /** 选项类字段的中文名（`字段码 → 名称或名称列表`）；字典改动不回溯已落库的 code */
  displayNames: Record<string, WireJson>
  snapshotEvidence: string
}

/** `FormDataService#readView` —— `GET /forms/instances/{id}/draft` 出参。 */
export interface WireFormDraftView {
  instanceId: WireId
  bizNo: string
  status: string
  subStatus?: string | null
  templateId: WireId
  /** `Integer`（JSON number） */
  templateVersion: number
  snapshot: WireFormSnapshotView
  state: WireFormWriteStateView
  /** 单据专属派生信息（四类各不相同：事项分支 / 资金默认值 / 合同必传文本 / 印鉴归还闭环） */
  formRules: Record<string, WireJson>
  /** 敏感字段（`payee_account`）：仅财务角色与系统管理员见全值，其余脱敏 */
  sensitive: Record<string, WireJson>
}

/** `FormDataService#writableFields` —— `GET /forms/instances/{id}/writable-fields` 出参。 */
export interface WireWritableFieldsView {
  instanceId: WireId
  bizNo: string
  formType: string
  /** `int`（JSON number） */
  schemaVersion: number
  /** `Integer`（JSON number） */
  templateVersion: number
  state: WireFormWriteStateView
  /** schema 字段全集（保序） */
  fieldCodes: string[]
}

// ================================================================ 写入与校验（2b.1）

/**
 * `FormWriteRequest` —— `PUT /forms/instances/{id}/draft` 与两处 `validate` 的请求体。
 *
 * `fields` 的键必须是 schema 已登记的 `code`：**未登记的键一律 403 / `40308`**
 * （不是静默忽略）。值为 `null` 表示**删除该键**（后端 `merge` 语义）。
 */
export interface WireFormWriteRequest {
  fields: Record<string, WireJson | undefined>
  /** `draft` / `submit`；缺省 = `draft` */
  mode?: string | null
}

/** `FieldIssue` —— 逐字段失败项（`FormValidationReport#view` 的 `issues[]` 项）。 */
export interface WireFieldIssueView {
  /** 字段码（未登记字段时就是客户端提交的那个键） */
  field: string
  label: string
  /** 规则名（`required` / `maxLength` / `amountRange` / `unknownField` …） */
  rule: string
  message: string
}

/** `FormValidationReport#view()` —— **一次给全所有失败项**。 */
export interface WireValidationReportView {
  passed: boolean
  /** `int`（JSON number） */
  issueCount: number
  issues: WireFieldIssueView[]
}

/** `FormDataService#validateByFormType` 出参（`POST /forms/{formType}/validate`）。 */
export interface WireFormTypeValidationView {
  formType: string
  templateCode: string
  /** `int`（JSON number） */
  schemaVersion: number
  /** `DRAFT` / `SUBMIT` */
  mode: string
  report: WireValidationReportView
  /** 服务端归一化后的最终态（含默认值补全与金额定点补零） */
  normalized: Record<string, WireJson>
}

/** `FormDataService#validateInstance` 出参（`POST /forms/instances/{id}/validate`）。 */
export interface WireInstanceValidationView {
  instanceId: WireId
  bizNo: string
  formType: string
  /** `int`（JSON number） */
  schemaVersion: number
  /** `Integer`（JSON number） */
  templateVersion: number
  state: WireFormWriteStateView
  mode: string
  report: WireValidationReportView
  /** 合并后的最终态（敏感字段 `payee_account` 已剔除） */
  merged: Record<string, WireJson>
}

// ================================================================ 单据专属判定（2b.3）

/**
 * `MatterFormRules.branchOf` —— 事项单唯一分支判定出参
 * （`POST /forms/matter/fields/involve-cost/evaluate`）。
 *
 * `skipFinanceReview=true` 表示 `involve_cost=false` 时流程跳过节点②（财务部复核）。
 */
export interface WireInvolveCostView {
  field: string
  involveCost: boolean
  skipFinanceReview: boolean
  /** `Integer`（JSON number） */
  skippedNode?: number | null
  skippedNodeCode?: string | null
  skipCondition?: string | null
  branchIsUnique: boolean
  ownerDeptRecorded?: string | null
  evidence: string
  flowMutation: string
  categoryMutable: boolean
  categoryLockEvidence: string
  /** 声明口径与服务端 `shouldSkipFinanceReview` 是否一致（自检字段） */
  engineAgreement: boolean
  categoryLock: WireCategoryLockView
}

/** 事项单类别改判判定出参（`POST /forms/matter/fields/category/lock` 内嵌）。 */
export interface WireCategoryLockView {
  field: string
  currentCategory?: string | null
  newCategory?: string | null
  immutableAfterSubmit: boolean
  allowed: boolean
  /** 拒绝时给出 `40309`，否则 null（键省略） */
  errorCode?: number | null
  remedy: string
  evidence: string
}

/** `GET /forms/contract/required-text-fields` 出参（合同单必传文本字段披露）。 */
export interface WireContractRequiredTextFieldsView {
  fields: string[]
  blankRule: string
  creditCodePattern: string
}

/** `SealFormRules.threeStateException()` —— 印鉴单三态例外的出参形态。 */
export interface WireSealThreeStateExceptionView {
  formType: string
  fields: string[]
  draft: string
  approving: string
  pendingSupplement: string
  closed: string
  evidence: string
}

/** `POST /forms/seal/fields/seal-type/linkage` 出参（联动机检 + 归还闭环说明）。 */
export interface WireSealLinkageView {
  sealType?: string | null
  certNameRequired?: boolean | null
  sealCountRequired?: boolean | null
  usageStart?: string | null
  usageEnd?: string | null
  returnStatus?: string | null
  returnDate?: string | null
  returnClosure: string
  threeStateException: WireSealThreeStateExceptionView
}

/** `SealReturnRequest` —— `PUT /forms/seal/instances/{id}/return-status` 请求体。 */
export interface WireSealReturnRequest {
  returnStatus?: string | null
  returnDate?: string | null
  reason?: string | null
}

/** `InvolveCostRequest` —— `POST /forms/matter/fields/involve-cost/evaluate` 请求体。 */
export interface WireInvolveCostRequest {
  involveCost?: boolean | null
  fields?: Record<string, WireJson> | null
}

// ================================================================ 发起前预检（2a.3）

/** `CandidateView` —— 候选人（与 `ApproverDtos.CandidateView` 同形）。 */
export interface WirePrecheckCandidateView {
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

/** `PrecheckRequest` —— `POST /flow-instances/precheck` 请求体。 */
export interface WirePrecheckRequest {
  templateId?: WireId | null
  /** `Integer`：缺省取当前 published 版本 */
  templateVersion?: number | null
  /** 为空 = 以当前登录人为发起人 */
  initiatorId?: WireId | null
  formType?: string | null
  category?: string | null
  involveCost?: boolean | null
  initiatorPicks?: string[] | null
  collabDeptIds?: string[] | null
  collabSelfExcludeDeptIds?: string[] | null
  formValues?: Record<string, WireJson> | null
}

/** `PrecheckBlockerView` —— **哪个节点、命中哪条规则、缺什么配置**（AC-11 / AC-19）。 */
export interface WirePrecheckBlockerView {
  /** `Integer`（JSON number） */
  nodeSeq?: number | null
  nodeCode?: string | null
  nodeName?: string | null
  rule?: string | null
  ruleLabel?: string | null
  reason?: string | null
  missingConfig?: string[] | null
}

/** `PrecheckNodeView` —— 预检中的单节点结论。 */
export interface WirePrecheckNodeView {
  /** `Integer`（JSON number） */
  nodeSeq?: number | null
  nodeCode?: string | null
  nodeName?: string | null
  nodeType?: string | null
  rule?: string | null
  skipped: boolean
  skipReason?: string | null
  blocker: boolean
  candidates?: WirePrecheckCandidateView[] | null
  groups?: WirePrecheckCandidateView[][] | null
  /** `int`（JSON number） */
  requiredApprovals: number
  thresholdBasis?: string | null
  satisfiable?: boolean | null
  evidence?: string | null
  missingConfig?: string[] | null
}

/** `PrecheckReportView` —— `POST /flow-instances/precheck` 出参。 */
export interface WirePrecheckReportView {
  allowed: boolean
  templateId?: WireId | null
  templateCode?: string | null
  /** `Integer`（JSON number） */
  templateVersion?: number | null
  initiatorId?: WireId | null
  initiatorName?: string | null
  blockers: WirePrecheckBlockerView[]
  nodes: WirePrecheckNodeView[]
  warnings: string[]
}

// ================================================================ 实例（发起）

/**
 * `CreateInstanceRequest` —— `POST /flow-instances` 请求体（建草稿）。
 *
 * `fields` 与 `formValues` 是同一份表单值的两个键（后端 `FlowInstanceService` 同时读）
 * —— 前端**两个都传**同一份值，避免依赖后端读哪一个的实现细节。
 */
export interface WireCreateInstanceRequest {
  templateId?: WireId | null
  /** `Integer` */
  templateVersion?: number | null
  /** 为空 = 当前登录人（管理员代发起/排障） */
  initiatorId?: WireId | null
  bizNo?: string | null
  formType?: string | null
  category?: string | null
  involveCost?: boolean | null
  initiatorPicks?: string[] | null
  collabDeptIds?: string[] | null
  collabSelfExcludeDeptIds?: string[] | null
  formValues?: Record<string, WireJson> | null
  fields?: Record<string, WireJson> | null
}

/** `SubmitRequest` —— `POST /flow-instances/{id}/submit` 请求体（**原因必填**）。 */
export interface WireSubmitRequest {
  reason: string
}
