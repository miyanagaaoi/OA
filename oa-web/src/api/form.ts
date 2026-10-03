/**
 * oa-web · 表单域接口（表单模板 schema / 字典 / 三态读写 / 二次校验 / 单据专属判定）
 * ----------------------------------------------------------------------------
 * 路径为阶段 2b **已交付**的定稿契约（全部在 `/api/v1` 之下），本文件把
 * `types/form-wire.d.ts`（后端 DTO 镜像）映射为领域模型 `types/form.d.ts`。
 *
 * 后端实现：
 *   · `com.oa.form.api.FormTemplateController`（schema 与字典）
 *   · `com.oa.form.api.FormDataController`（实例读写 + 印鉴单归还登记）
 *   · `com.oa.form.api.FormRuleController`（字段分组 + 四类单据专属判定）
 *   · `com.oa.workflow.approver.api.FlowInstanceController`（发起前预检，见 `api/flow-task.ts`）
 *
 * ── schema / 字典 ──────────────────────────────────────────────────────────
 *   GET    /api/v1/forms/templates                          读（四类已发布模板 + schema 版本）
 *   GET    /api/v1/forms/templates/{formType}/schema        读（?version= 缺省取当前 published）
 *   GET    /api/v1/forms/instances/{id}/schema              读（**实例锁定版本**，AC-09）
 *   GET    /api/v1/forms/dicts                              读（8 类字典白名单）
 *   GET    /api/v1/forms/dicts/{dictType}/items             读（**下拉取值的唯一来源**）
 *   GET    /api/v1/forms/{formType}/field-groups            读（sections + 金额角色策略）
 *   GET    /api/v1/forms/contract/required-text-fields      读（合同单必传文本字段披露）
 * ── 实例读写（三态白名单在此强制）──────────────────────────────────────────
 *   GET    /api/v1/forms/instances/{id}/draft               读（字段值 + 可写字段 + 专属派生）
 *   GET    /api/v1/forms/instances/{id}/writable-fields     读（置灰提示）
 *   PUT    /api/v1/forms/instances/{id}/draft?mode=DRAFT|SUBMIT   写（`flow`）
 *   POST   /api/v1/forms/instances/{id}/validate?mode=      写（干跑，**返回全部失败项**，不改库）
 *   POST   /api/v1/forms/{formType}/validate?mode=          写（新建前干跑）
 *   PUT    /api/v1/forms/seal/instances/{id}/return-status  写（三态唯一例外通道）
 *   POST   /api/v1/forms/matter/fields/involve-cost/evaluate 写（纯判定，不落库）
 *
 * 三条实现约定：
 *   1. **映射层承担 `Long` → string**（id 在 JSON 里是字符串），领域模型里 id 恒为 `string`；
 *   2. **金额与数字一律保持字符串**（后端把 JSON number 回读成字符串，`doc/forms.md` §11.5），
 *      本层绝不 `Number(...)` 金额；
 *   3. **不做演示数据降级**：表单 schema 决定「哪些字段能填、值怎么校验」，
 *      静默回落演示数据会让用户填出服务端必拒的载荷；接口不可用一律显式报错。
 */
import { get, post, put, type OaRequestConfig } from './http'
import type {
  WireCategoryLockView,
  WireContractRequiredTextFieldsView,
  WireDictItemView,
  WireDictTypeView,
  WireFieldGroupView,
  WireFormDraftView,
  WireFormFieldView,
  WireInstanceValidationView,
  WireFormSchemaView,
  WireFormSectionView,
  WireFormTypeValidationView,
  WireFormWriteStateView,
  WireInstanceSchemaView,
  WireInvolveCostRequest,
  WireInvolveCostView,
  WireJson,
  WireSealReturnRequest,
  WireValidationReportView,
  WireWritableFieldsView,
} from '@/types/form-wire'
import type {
  ContractRequiredTextFields,
  FormAmountPolicy,
  FormDictItem,
  FormDictType,
  FormDraft,
  FormField,
  FormFieldGroups,
  FormInvolveCostBranch,
  FormInstanceValidation,
  FormJsonValue,
  FormOption,
  FormSchema,
  FormSection,
  FormSnapshot,
  FormTypeValidation,
  FormValidationReport,
  FormWriteState,
  FormWriteStateCode,
  FormWritableFields,
} from '@/types/form'
import { toValidationReport, controlOfType, toFormFieldType } from '@/utils/form-rules'

// ---------------------------------------------------------------------------
// 传输层包装：错误提示由页面**唯一呈现**（含业务码 + 处置建议）
// ---------------------------------------------------------------------------
/**
 * 关闭 `api/http.ts` 拦截器的统一 toast。
 *
 * <p>理由与 `api/flow.ts` 逐条一致：本域的关键拒绝（40304 三态只读 / 40308 夹带未登记字段 /
 * 40306 金额只读 / 40011 逐字段校验 / 40007 预检拦截）都需要把**业务码 + 逐条明细**
 * 呈现给用户，而拦截器的 toast 只带 message。两处都提示会一次操作弹两次。
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

// ---------------------------------------------------------------------------
// 映射工具
// ---------------------------------------------------------------------------
/** `Long` → 字符串 id（缺失 → 空串） */
function sid(value: string | number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

/** `Integer` → number（非法值退回 fallback） */
function num(value: number | string | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** 可空字符串归一 */
function text(value: string | null | undefined): string {
  return value === null || value === undefined ? '' : value
}

/** 可空字符串归一为 `string | null`（"未给出" 与 "空串" 区分开） */
function nullableText(value: string | null | undefined): string | null {
  return value === null || value === undefined || value === '' ? null : value
}

/**
 * 表单值归一。
 *
 * 后端 `FormSnapshotService#jsonValue` 把**所有 JSON number 回读成字符串**
 * （金额/数字一律按字符串处理，`doc/forms.md` §11.5）；这里对写入路径的值也保持
 * **原样透传**：不做 `Number`、不做 `parseFloat`，唯一要做的是把 `undefined` 归一为 `null`
 * 之外的**原值保留**（未知形状按递归结构透传）。
 */
function formValue(value: WireJson | undefined): FormJsonValue {
  if (value === undefined) return null
  if (value === null) return null
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value
  if (Array.isArray(value)) return value.map((item) => formValue(item))
  const result: Record<string, FormJsonValue> = {}
  for (const [key, item] of Object.entries(value)) {
    result[key] = formValue(item)
  }
  return result
}

/** 规范化表单值表（键序保留） */
function formValueMap(source: Record<string, WireJson | undefined> | null | undefined): Record<string, FormJsonValue> {
  const result: Record<string, FormJsonValue> = {}
  if (!source) return result
  for (const [key, value] of Object.entries(source)) {
    result[key] = formValue(value)
  }
  return result
}

/** 字段选项归一（内联静态选项） */
function toOptions(field: WireFormFieldView): FormOption[] {
  return (field.options ?? []).map((option) => ({
    value: text(option.code),
    label: text(option.label),
    enabled: option.enabled !== false,
    sortNo: num(option.sortNo, 0),
  }))
}

/** 单个字段定义 → 领域模型 */
export function toFormField(wire: WireFormFieldView): FormField {
  return {
    code: text(wire.code),
    label: text(wire.label),
    printLabel: text(wire.printLabel),
    printVisible: wire.printVisible !== false,
    rawType: text(wire.type),
    type: toFormFieldType(wire.type),
    control: controlOfType(wire.type),
    required: wire.required === true,
    maxLength: num(wire.maxLength, 0),
    locked: wire.locked === true,
    readonlyAfterSubmit: wire.readonlyAfterSubmit !== false,
    dictType: nullableText(wire.dictType),
    options: toOptions(wire),
    hasDefaultValue: Object.prototype.hasOwnProperty.call(wire, 'defaultValue'),
    defaultValue: wire.defaultValue === undefined ? null : formValue(wire.defaultValue),
    placeholder: nullableText(wire.placeholder),
    unit: nullableText(wire.unit),
    rules: Array.isArray(wire.rules) ? wire.rules.map((rule) => text(rule)) : [],
  }
}

/** `sections[]` → 领域模型 */
function toSection(wire: WireFormSectionView): FormSection {
  return {
    id: text(wire.id),
    title: text(wire.title),
    printTitle: text(wire.printTitle),
    collapsible: wire.collapsible !== false,
    fieldCodes: Array.isArray(wire.fields) ? wire.fields.map((code) => text(code)) : [],
  }
}

/** schema 出参 → 领域模型 */
function toSchema(wire: WireFormSchemaView): FormSchema {
  const instance = wire as Partial<WireInstanceSchemaView>
  return {
    formType: text(wire.formType),
    templateCode: text(wire.templateCode),
    schemaVersion: num(wire.schemaVersion, 0),
    publishedAt: nullableText(wire.publishedAt),
    sections: (wire.sections ?? []).map(toSection),
    fields: (wire.fields ?? []).map(toFormField),
    instanceId: instance.instanceId === undefined ? null : sid(instance.instanceId),
    templateId: instance.templateId === undefined ? null : sid(instance.templateId),
    templateVersion:
      instance.templateVersion === undefined ? null : num(instance.templateVersion, 0),
    lockedVersionEvidence: nullableText(instance.lockedVersionEvidence),
  }
}

/** 三态上下文 → 领域模型 */
function toWriteState(wire: WireFormWriteStateView | null | undefined): FormWriteState {
  const raw = text(wire?.state)
  // 后端只发四个枚举值；未知/缺失时按最严的 CLOSED 处理（只读），避免误开可写
  const state: FormWriteStateCode =
    raw === 'DRAFT' || raw === 'APPROVING' || raw === 'PENDING_SUPPLEMENT' || raw === 'CLOSED'
      ? raw
      : 'CLOSED'
  return {
    state,
    formType: text(wire?.formType),
    isInitiator: wire?.isInitiator === true,
    isArchiveNode: wire?.isArchiveNode === true,
    writableFields: (wire?.writableFields ?? []).map((code) => text(code)),
    readonlyFields: (wire?.readonlyFields ?? []).map((code) => text(code)),
    stateLabel: text(wire?.stateLabel),
    evidence: text(wire?.evidence),
  }
}

/** 快照 → 领域模型 */
function toSnapshot(wire: WireFormDraftView['snapshot']): FormSnapshot {
  const displayNames: Record<string, FormJsonValue> = {}
  for (const [key, value] of Object.entries(wire?.displayNames ?? {})) {
    displayNames[key] = formValue(value)
  }
  return {
    formType: text(wire?.formType),
    templateCode: text(wire?.templateCode),
    schemaVersion: num(wire?.schemaVersion, 0),
    fields: formValueMap(wire?.fields),
    displayNames,
    snapshotEvidence: text(wire?.snapshotEvidence),
  }
}

/** 自由 JSON 对象归一（`formRules` / `sensitive`） */
function toFreeJson(source: Record<string, WireJson> | null | undefined): Record<string, FormJsonValue> {
  return formValueMap(source)
}

/**
 * `readView` 出参 → 领域模型。
 *
 * `GET /draft`、`PUT /draft`、`PUT /seal/.../return-status` 三个接口返回的是**同一个形状**
 * （后端 `FormDataService#readView`），因此共用这一处映射 —— 三处各写一份正是口径漂移的来源。
 */
function toDraft(wire: WireFormDraftView): FormDraft {
  return {
    instanceId: sid(wire.instanceId),
    bizNo: text(wire.bizNo),
    status: text(wire.status),
    subStatus: nullableText(wire.subStatus),
    templateId: sid(wire.templateId),
    templateVersion: num(wire.templateVersion, 0),
    snapshot: toSnapshot(wire.snapshot),
    state: toWriteState(wire.state),
    formRules: toFreeJson(wire.formRules),
    sensitive: toFreeJson(wire.sensitive),
  }
}

/** 校验报告 → 领域模型 */
function toReport(wire: WireValidationReportView | null | undefined): FormValidationReport {
  if (!wire) {
    return { passed: true, issueCount: 0, issues: [], parsedFromMessage: false, unrestored: '' }
  }
  return toValidationReport({
    passed: wire.passed === true,
    issueCount: num(wire.issueCount, 0),
    issues: (wire.issues ?? []).map((issue) => ({
      field: text(issue.field),
      label: text(issue.label),
      rule: text(issue.rule),
      message: text(issue.message),
    })),
  })
}

// ================================================================ schema / 字典

/** 四类单据的已发布模板摘要（`GET /forms/templates`） */
export interface FormTemplateSummary {
  formType: string
  templateCode: string
  schemaVersion: number
  fieldCount: number
  published: boolean
  message: string
}

export async function listFormTemplates(): Promise<FormTemplateSummary[]> {
  const wire = await fget<Array<Record<string, WireJson>>>('/forms/templates')
  return (wire ?? []).map((item) => ({
    formType: text(String(item.formType ?? '')),
    templateCode: text(String(item.templateCode ?? '')),
    schemaVersion: num(item.schemaVersion as number | undefined, 0),
    fieldCount: num(item.fieldCount as number | undefined, 0),
    published: item.published === true,
    message: text(String(item.message ?? '')),
  }))
}

/**
 * 取某单据类型的表单 schema。
 *
 * @param version 缺省 = 该类型**当前已发布**版本；给定版本则按 `(code, version)` 精确取
 *                （`doc/templates.md` §3.2 V-01）
 */
export async function fetchFormSchema(formType: string, version?: number): Promise<FormSchema> {
  const config = version === undefined || version === null ? {} : { params: { version } }
  const wire = await fget<WireFormSchemaView>(
    `/forms/templates/${encodeURIComponent(formType)}/schema`,
    config,
  )
  return toSchema(wire)
}

/** 取**实例锁定版本**的 schema（AC-09：不读当前 published） */
export async function fetchInstanceSchema(instanceId: string): Promise<FormSchema> {
  const wire = await fget<WireInstanceSchemaView>(
    `/forms/instances/${encodeURIComponent(instanceId)}/schema`,
  )
  return toSchema(wire)
}

/** 字典类型白名单（8 类） */
export async function fetchDictTypes(): Promise<FormDictType[]> {
  const wire = await fget<WireDictTypeView[]>('/forms/dicts')
  return (wire ?? []).map((item) => ({
    dictType: text(item.dictType),
    label: text(item.label),
    itemCount: num(item.itemCount, 0),
    evidence: text(item.evidence),
  }))
}

/** 某字典类型的**启用项**（下拉取值的唯一来源；**不得硬编码**） */
export async function fetchDictItems(dictType: string): Promise<FormDictItem[]> {
  const wire = await fget<WireDictItemView[]>(`/forms/dicts/${encodeURIComponent(dictType)}/items`)
  return (wire ?? []).map((item) => ({
    dictType: text(item.dictType),
    itemCode: text(item.itemCode),
    itemName: text(item.itemName),
    itemNameEn: nullableText(item.itemNameEn),
    sortNo: num(item.sortNo, 0),
    status: text(item.status),
  }))
}

/** 字段分组（含完整字段体与金额角色策略） */
export async function fetchFieldGroups(formType: string): Promise<FormFieldGroups> {
  const wire = await fget<WireFieldGroupView>(`/forms/${encodeURIComponent(formType)}/field-groups`)
  const amountPolicy: FormAmountPolicy = {
    writable: wire.amountPolicy?.writable === true,
    exportable: wire.amountPolicy?.exportable === true,
    writableRoles: (wire.amountPolicy?.writableRoles ?? []).map((role) => text(role)),
  }
  return {
    formType: text(wire.formType),
    schemaVersion: num(wire.schemaVersion, 0),
    sections: (wire.sections ?? []).map((section) => ({
      id: text(section.id),
      title: text(section.title),
      printTitle: text(section.printTitle),
      collapsible: section.collapsible !== false,
      fields: (section.fields ?? []).map(toFormField),
    })),
    amountPolicy,
  }
}

/** 合同单必传文本字段披露（对外契约；`doc/forms.md` §4） */
export async function fetchContractRequiredTextFields(): Promise<ContractRequiredTextFields> {
  const wire = await fget<WireContractRequiredTextFieldsView>('/forms/contract/required-text-fields')
  return {
    fields: (wire.fields ?? []).map((field) => text(field)),
    blankRule: text(wire.blankRule),
    creditCodePattern: text(wire.creditCodePattern),
  }
}

// ================================================================ 实例读写

/** 读取单据（字段值 + 快照版本 + 可写字段 + 专属派生信息） */
export async function fetchFormDraft(instanceId: string): Promise<FormDraft> {
  const wire = await fget<WireFormDraftView>(`/forms/instances/${encodeURIComponent(instanceId)}/draft`)
  return toDraft(wire)
}

/** 当前状态可写字段（前端置灰提示；**边界仍在服务端**） */
export async function fetchWritableFields(instanceId: string): Promise<FormWritableFields> {
  const wire = await fget<WireWritableFieldsView>(
    `/forms/instances/${encodeURIComponent(instanceId)}/writable-fields`,
  )
  return {
    instanceId: sid(wire.instanceId),
    bizNo: text(wire.bizNo),
    formType: text(wire.formType),
    schemaVersion: num(wire.schemaVersion, 0),
    templateVersion: num(wire.templateVersion, 0),
    state: toWriteState(wire.state),
    fieldCodes: (wire.fieldCodes ?? []).map((code) => text(code)),
  }
}

/**
 * 保存草稿 / 补件（`mode=DRAFT`）或按提交档保存（`mode=SUBMIT`）。
 *
 * ⚠ 服务端的处理顺序是「未登记字段 → 三态白名单/金额角色 → schema 校验 → 专属规则」，
 * 因此同一次调用可能以 403（40304 / 40306 / 40308）或 400（40011）失败；
 * 页面须**按业务码**分别呈现（`utils/form-rules.ts` 的 `errorHint`）。
 */
export async function saveFormDraft(
  instanceId: string,
  fields: Record<string, FormJsonValue>,
  mode: 'DRAFT' | 'SUBMIT' = 'DRAFT',
): Promise<FormDraft> {
  const wire = await fput<WireFormDraftView>(
    `/forms/instances/${encodeURIComponent(instanceId)}/draft`,
    { fields, mode: mode.toLowerCase() },
    { params: { mode } },
  )
  return toDraft(wire)
}

/** 新建前干跑校验（用当前已发布 schema；不落库） */
export async function validateFormByType(
  formType: string,
  fields: Record<string, FormJsonValue>,
  mode: 'DRAFT' | 'SUBMIT' = 'DRAFT',
): Promise<FormTypeValidation> {
  const wire = await fpost<WireFormTypeValidationView>(
    `/forms/${encodeURIComponent(formType)}/validate`,
    { fields, mode: mode.toLowerCase() },
    { params: { mode } },
  )
  return {
    formType: text(wire.formType),
    templateCode: text(wire.templateCode),
    schemaVersion: num(wire.schemaVersion, 0),
    mode: text(wire.mode) === 'SUBMIT' ? 'SUBMIT' : 'DRAFT',
    report: toReport(wire.report),
    normalized: formValueMap(wire.normalized),
  }
}

/** 实例级干跑校验（按锁定版本判合并后的最终态；**返回全部失败项**，不改库） */
export async function validateFormInstance(
  instanceId: string,
  fields: Record<string, FormJsonValue>,
  mode: 'DRAFT' | 'SUBMIT' = 'SUBMIT',
): Promise<FormInstanceValidation> {
  const wire = await fpost<WireInstanceValidationView>(
    `/forms/instances/${encodeURIComponent(instanceId)}/validate`,
    { fields, mode: mode.toLowerCase() },
    { params: { mode } },
  )
  return {
    instanceId: sid(wire.instanceId),
    bizNo: text(wire.bizNo),
    formType: text(wire.formType),
    schemaVersion: num(wire.schemaVersion, 0),
    templateVersion: num(wire.templateVersion, 0),
    state: toWriteState(wire.state),
    mode: text(wire.mode) === 'DRAFT' ? 'DRAFT' : 'SUBMIT',
    report: toReport(wire.report),
    merged: formValueMap(wire.merged),
  }
}

/**
 * 印鉴单归还登记（三态**唯一例外**的专用写入通道）。
 *
 * 只接受 `return_status` / `return_date` 两个键：其余键会被 `40304` + 明确文案拒绝
 * （`FormDataService#registerSealReturn`）。
 *
 * 写入后服务端会落一条轨迹动作 **`return_register`（归还登记）**，意见里带前后值
 * （`归还登记：归还状态=x → y；…`）。2026-10-04 起该轨迹**不再**复用 `archive_register`
 * —— 轨迹是用户可见的审计产物，把「归还登记」显示成「归档登记」等于在审计记录里说错话；
 * 节点⑦的「归档登记」动作本身仍是 `archive_register`，未变。
 * 调用方（填单页）应使用本函数返回的 `FormDraft` 刷新界面（它就是服务端写完后的读视图）。
 */
export async function registerSealReturn(
  instanceId: string,
  payload: { returnStatus?: string | null; returnDate?: string | null; reason?: string | null },
): Promise<FormDraft> {
  const body: WireSealReturnRequest = {
    returnStatus: payload.returnStatus ?? null,
    returnDate: payload.returnDate ?? null,
    reason: payload.reason ?? null,
  }
  const wire = await fput<WireFormDraftView>(
    `/forms/seal/instances/${encodeURIComponent(instanceId)}/return-status`,
    body,
  )
  return toDraft(wire)
}

// ================================================================ 单据专属判定（只读干跑）

/** 事项单唯一分支：`involve_cost` 决定节点②（财务部复核）是否跳过 */
export async function evaluateMatterInvolveCost(
  payload: WireInvolveCostRequest,
): Promise<FormInvolveCostBranch & { categoryLock: WireCategoryLockView | null }> {
  const wire = await fpost<WireInvolveCostView>('/forms/matter/fields/involve-cost/evaluate', payload)
  return {
    involveCost: wire.involveCost === true,
    skipFinanceReview: wire.skipFinanceReview === true,
    skippedNode: wire.skippedNode === null || wire.skippedNode === undefined ? null : num(wire.skippedNode, 0),
    skippedNodeCode: nullableText(wire.skippedNodeCode),
    skipCondition: nullableText(wire.skipCondition),
    branchIsUnique: wire.branchIsUnique === true,
    ownerDeptRecorded: nullableText(wire.ownerDeptRecorded),
    evidence: text(wire.evidence),
    flowMutation: text(wire.flowMutation),
    categoryMutable: wire.categoryMutable === true,
    categoryLockEvidence: text(wire.categoryLockEvidence),
    engineAgreement: wire.engineAgreement === true,
    categoryLock: wire.categoryLock ?? null,
  }
}

/** 导出金额策略的工具函数（页面按它决定金额是否置灰；判定仍在服务端） */
export function amountFieldCodes(schema: Pick<FormSchema, 'fields'>): string[] {
  return schema.fields.filter((field) => field.control === 'amount').map((field) => field.code)
}
