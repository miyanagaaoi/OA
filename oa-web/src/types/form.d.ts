/**
 * oa-web · 表单域领域模型（页面只依赖本文件）
 * ----------------------------------------------------------------------------
 * 由 `api/form.ts` 从 `types/form-wire.d.ts`（后端 DTO 镜像）映射而来。映射口径：
 *   · **id 一律 string**（后端 `Long` → JSON 字符串）；
 *   · **金额与数字一律 string**（`FormSnapshotService#jsonValue` 把 JSON number 回读成字符串，
 *     见 `doc/forms.md` §11.5「禁止浮点」）→ 领域模型里金额没有 number 形态；
 *   · 缺失的可空键统一归一为 `null`（wire 层因 `non_null` 会整键省略）；
 *   · 自由 JSON（`formRules` / `linkage`）用递归联合 `FormJsonValue` 表达，**不用 `any`**。
 *
 * ⚠ 命名避让：`types/api.d.ts` 已有一个面向「门户工作台」的 `FormType`
 * （含 `seal_cert` 这个**后端不存在**的取值）。本域一律用 `FormDocType`
 * （`matter` / `fund` / `contract` / `seal`，与 `doc/enums.md` §10.2 逐字一致），
 * 两者**不要混用**：混用会把 `seal` 与 `seal_cert` 这类口径差异带进请求体。
 */

/** 表单值（自由 JSON，但**不含 undefined**：未填 = 键不存在或 `null`） */
export type FormJsonValue =
  | string
  | number
  | boolean
  | null
  | FormJsonValue[]
  | { [key: string]: FormJsonValue }

/** 单据类型（`doc/enums.md` §10.2：`matter` / `fund` / `contract` / `seal`） */
export type FormDocType = 'matter' | 'fund' | 'contract' | 'seal'

// 四类单据的中文名与取值顺序是**运行期常量**，放在 `utils/form-rules.ts`
// （`FORM_DOC_TYPE_LABEL` / `FORM_DOC_TYPES`）—— `.d.ts` 只承载类型，不承载值。

/**
 * 14 种字段类型（`doc/enums.md` §11，与后端 `FormFieldType` 逐字一致）。
 * 后端遇到未知类型时会把模板原文回显在 `type` 上，因此领域模型额外允许 `string`。
 */
export type FormFieldType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'amount'
  | 'select'
  | 'multiselect'
  | 'date'
  | 'daterange'
  | 'user'
  | 'org'
  | 'tag'
  | 'boolean'
  | 'file'
  | 'files'

// 14 种字段类型的中文名是**运行期常量**，放在 `utils/form-rules.ts`
// （`FORM_FIELD_TYPE_LABEL`）—— `.d.ts` 只承载类型，不承载值。

/**
 * 渲染器支持的控件种类。
 *
 * `attachment`（`file` / `files` 字段）渲染 `components/AttachmentPanel.vue`：
 * 上传（拖拽/多选 + 前端预检）、按 round 分组的清单、鉴权下载与预览、本人删除
 * 都是真接口（阶段 2b.7 起）；只读态只给查看与下载，并说明原因。
 */
export type FormControlKind =
  | 'text'
  | 'textarea'
  | 'number'
  | 'amount'
  | 'select'
  | 'multiselect'
  | 'date'
  | 'daterange'
  | 'user'
  | 'org'
  | 'tag'
  | 'boolean'
  | 'attachment'
  | 'unsupported'

/** 选项项（内联静态选项与字典项统一成同一形状） */
export interface FormOption {
  value: string
  label: string
  enabled: boolean
  sortNo: number
}

/** 字段定义（领域模型） */
export interface FormField {
  code: string
  label: string
  printLabel: string
  printVisible: boolean
  /** 模板里的原始类型文本（未知类型也保留，供如实展示） */
  rawType: string
  type: FormFieldType | null
  control: FormControlKind
  required: boolean
  maxLength: number
  /** 任何状态都不可编辑（仅展示默认值） */
  locked: boolean
  /** 提交发起后只读（`false` 仅允许用于服务端字段级白名单内的字段） */
  readonlyAfterSubmit: boolean
  /** 字典绑定（选项**必须**来自 `GET /forms/dicts/{dictType}/items`） */
  dictType: string | null
  /** 模板内联的静态选项（与 `dictType` 互斥） */
  options: FormOption[]
  /** 模板声明的默认值（键不存在 = 未声明） */
  hasDefaultValue: boolean
  defaultValue: FormJsonValue
  placeholder: string | null
  unit: string | null
  /** 规则类型名清单 */
  rules: string[]
  /**
   * 规则**参数**（键 = 规则类型名）。
   *
   * <p>来源是 schema 出参的 `ruleDetails[]`（与 `rules` 下标一一对应，**只含模板声明过的键**，
   * 2026-10-05 起下发）。此前前端只拿得到规则**类型名**，于是「模板声明的
   * `filePolicy.maxCount` / `pickerLimit.max` / `conditionalRequired.when`」在前端
   * 完全不可见，只能按服务端缺省兜底或让用户试错。
   *
   * <p>⚠ 键**缺失** ≠ 值为空：模板没声明该键时这里就没有它，此时生效的是
   * 服务端缺省（另一层口径），调用方必须回落而**不得**假装模板声明过。
   * 例：`ruleParams.filePolicy` → `{maxSizeMb, maxCount, allowExt, denyExt, message}`
   * （`utils/attachment.ts#resolveAttachmentBounds` 按此解析附件档位）。
   */
  ruleParams: Record<string, FormJsonValue>
}

/** 字段分组（`sections[]`；`fields` 为字段码列表，按分组顺序） */
export interface FormSection {
  id: string
  title: string
  printTitle: string
  collapsible: boolean
  fieldCodes: string[]
}

/** 表单 schema（`GET /forms/templates/{formType}/schema` 或实例锁定版本） */
export interface FormSchema {
  formType: string
  templateCode: string
  schemaVersion: number
  publishedAt: string | null
  sections: FormSection[]
  fields: FormField[]
  /** 实例锁定版本时才有的字段 */
  instanceId: string | null
  templateId: string | null
  templateVersion: number | null
  lockedVersionEvidence: string | null
}

/** 金额角色只读披露（`amountPolicy`） */
export interface FormAmountPolicy {
  writable: boolean
  exportable: boolean
  writableRoles: string[]
}

/** 字段分组接口的返回（含完整字段体 + 金额策略） */
export interface FormFieldGroups {
  formType: string
  schemaVersion: number
  sections: Array<{ id: string; title: string; printTitle: string; collapsible: boolean; fields: FormField[] }>
  amountPolicy: FormAmountPolicy
}

/** 字典类型（8 类白名单） */
export interface FormDictType {
  dictType: string
  label: string
  itemCount: number
  evidence: string
}

/** 字典项 */
export interface FormDictItem {
  dictType: string
  itemCode: string
  itemName: string
  itemNameEn: string | null
  sortNo: number
  status: string
}

/** 字典缓存（按 `dictType` 索引；下拉取值唯一来源） */
export type FormDictCache = Record<string, FormDictItem[]>

/**
 * 三态读写状态（服务端 `FormWritePolicy.FormState`）。
 * `CLOSED` = 已完结 / 已归档（含 `approved` / `terminated`）。
 */
export type FormWriteStateCode = 'DRAFT' | 'APPROVING' | 'PENDING_SUPPLEMENT' | 'CLOSED'

/** 服务端算出的可写字段白名单与状态标签（**提示**，边界在服务端） */
export interface FormWriteState {
  state: FormWriteStateCode
  formType: string
  isInitiator: boolean
  isArchiveNode: boolean
  writableFields: string[]
  readonlyFields: string[]
  stateLabel: string
  evidence: string
}

/** 表单快照 */
export interface FormSnapshot {
  formType: string
  templateCode: string
  schemaVersion: number
  fields: Record<string, FormJsonValue>
  displayNames: Record<string, FormJsonValue>
  snapshotEvidence: string
}

/** 单据（草稿读取出参） */
export interface FormDraft {
  instanceId: string
  bizNo: string
  status: string
  subStatus: string | null
  templateId: string
  templateVersion: number
  snapshot: FormSnapshot
  state: FormWriteState
  /** 单据专属派生信息（自由 JSON，四类各不相同） */
  formRules: Record<string, FormJsonValue>
  /** 敏感字段（`payee_account`）：仅财务角色与系统管理员见全值 */
  sensitive: Record<string, FormJsonValue>
}

/** 可写字段出参 */
export interface FormWritableFields {
  instanceId: string
  bizNo: string
  formType: string
  schemaVersion: number
  templateVersion: number
  state: FormWriteState
  fieldCodes: string[]
}

/** 逐字段失败项 */
export interface FormFieldIssue {
  field: string
  label: string
  rule: string
  message: string
}

/**
 * 校验报告（**一次给全所有失败项**）。
 *
 * 来源有两条，`api/form.ts` 会统一成同一形状：
 *   ① 干跑接口 200 出参里的 `report`（结构化，**首选**）；
 *   ② `40011` 错误响应 —— **首选** `details.errors[]`（后端 2026-10-04 起随响应体下发，
 *      与 ① 的 `issues[]` 同源同形，见 `utils/form-rules.ts#validationReportFromDetails`）；
 *      仅在明细缺失（老响应 / 其它码）时才由 `parseValidationMessage()` 从 message 文本
 *      还原成逐字段条目（此时 `parsedFromMessage=true`）。
 */
export interface FormValidationReport {
  passed: boolean
  issueCount: number
  issues: FormFieldIssue[]
  /** `true` = 由 40011 的 message 文本还原而来（结构化明细不可得时的降级路径） */
  parsedFromMessage: boolean
  /** message 里无法归到具体字段的剩余文本 */
  unrestored: string
}

/** 干跑校验结果（按单据类型） */
export interface FormTypeValidation {
  formType: string
  templateCode: string
  schemaVersion: number
  mode: 'DRAFT' | 'SUBMIT'
  report: FormValidationReport
  normalized: Record<string, FormJsonValue>
}

/** 干跑校验结果（按实例） */
export interface FormInstanceValidation {
  instanceId: string
  bizNo: string
  formType: string
  schemaVersion: number
  templateVersion: number
  state: FormWriteState
  mode: 'DRAFT' | 'SUBMIT'
  report: FormValidationReport
  merged: Record<string, FormJsonValue>
}

/** 事项单分支判定 */
export interface FormInvolveCostBranch {
  involveCost: boolean
  skipFinanceReview: boolean
  skippedNode: number | null
  skippedNodeCode: string | null
  skipCondition: string | null
  branchIsUnique: boolean
  ownerDeptRecorded: string | null
  evidence: string
  flowMutation: string
  categoryMutable: boolean
  categoryLockEvidence: string
  engineAgreement: boolean
}

/** 合同单必传文本字段披露 */
export interface ContractRequiredTextFields {
  fields: string[]
  blankRule: string
  creditCodePattern: string
}

/** 发起前预检拦截项（节点 / 规则 / 缺什么配置） */
export interface FlowPrecheckBlocker {
  nodeSeq: number | null
  nodeCode: string
  nodeName: string
  rule: string
  ruleLabel: string
  reason: string
  missingConfig: string[]
}

/** 发起前预检的单节点结论 */
export interface FlowPrecheckNode {
  nodeSeq: number | null
  nodeCode: string
  nodeName: string
  nodeType: string
  rule: string
  skipped: boolean
  skipReason: string
  blocker: boolean
  candidateNames: string[]
  candidateCount: number
  requiredApprovals: number
  thresholdBasis: string
  satisfiable: boolean | null
  evidence: string
  missingConfig: string[]
}

/** 发起前预检报告（`allowed=false` 时逐条展示 `blockers`） */
export interface FlowPrecheckReport {
  allowed: boolean
  templateId: string
  templateCode: string
  templateVersion: number
  initiatorId: string
  initiatorName: string
  blockers: FlowPrecheckBlocker[]
  nodes: FlowPrecheckNode[]
  warnings: string[]
}
