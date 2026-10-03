/**
 * oa-web · 表单域纯逻辑（无 Vue / 无 DOM —— 可直接用 Node 跑断言自测）
 * ----------------------------------------------------------------------------
 * 分工：本文件只做**判定与归一**，不发请求、不碰组件状态。
 *   · 字段类型 → 控件映射（14 种，`doc/enums.md` §11）；
 *   · 值的三态归一（读取 / 编辑 / 提交），**金额全程字符串、绝不经 `Number`**；
 *   · 三态只读原因（`doc/forms.md` §1.2 / §5 / §7）；
 *   · 服务端校验报告 → 逐字段错误索引；`40011` 的 `details.errors[]`（结构化，**首选**）
 *     与 message 文本还原（老响应 / 其它码的降级路径）；
 *   · `user` / `org` 选择器的多值归一与数量上限（模板 `rules[pickerLimit]`，缺省回落 20）；
 *   · 发起前预检拦截项 → 可读文案。
 *
 * 真源：
 *   · `doc/enums.md` §11（14 种字段类型）、§10.2（四类 form_type）
 *   · `doc/forms.md` §1.2（三态读写）、§1.5（金额 `DECIMAL(18,2)`、禁浮点）、§2（`cc_users` ≤20 人）、
 *     §5（印鉴单例外）
 *   · `doc/templates.md` §2.2（字段项结构）、§2.3（`rules[pickerLimit]`）、§2.5（未知键 40308 / 状态白名单 40304）
 *   · 后端：`FormFieldType` / `FormWritePolicy` / `FormValidationReport` / `AmountText` /
 *     `FormPayloadValidator`（`pickerLimit` / `pickerValue` / `canonicalizePickers`）
 */
import type {
  FormDocType,
  FormField,
  FormFieldIssue,
  FormFieldType,
  FormJsonValue,
  FormSection,
  FormSchema,
  FormValidationReport,
  FormWriteState,
  FormWriteStateCode,
  FormControlKind,
  FlowPrecheckBlocker,
} from '@/types/form'
import type { ApiErrorDetails, ApiFieldErrorDetail } from '@/types/api'

// ================================================================ 常量

/** 四类单据的中文名（`doc/prd-0.1.md` §6.2） */
export const FORM_DOC_TYPE_LABEL: Record<FormDocType, string> = {
  matter: '事项审批单',
  fund: '资金审批单',
  contract: '合同审批单',
  seal: '印鉴证照审批单',
}

/** 四类单据的取值顺序 */
export const FORM_DOC_TYPES: readonly FormDocType[] = ['matter', 'fund', 'contract', 'seal']

/**
 * 四类单据 → 图标字符（沿用 `DESIGN.md` 附录 B「单一强调色 + 图标形状区分类型」的口径）。
 *
 * 为什么另起一份而不是复用 `utils/status.ts` 的 `FORM_TYPE_GLYPH`：后者的键是**旧门户口径**
 * （`seal_cert`，后端并不存在该取值），直接复用会把 `seal` 渲染成空。既有页面的行为不受影响。
 */
export const FORM_DOC_TYPE_GLYPH: Record<FormDocType, string> = {
  matter: '事',
  fund: '资',
  contract: '合',
  seal: '印',
}

/** 14 种字段类型的中文名（`doc/enums.md` §11） */
export const FORM_FIELD_TYPE_LABEL: Record<FormFieldType, string> = {
  text: '单行文本',
  textarea: '多行文本',
  number: '数字',
  amount: '金额（定点两位小数）',
  select: '单选下拉',
  multiselect: '多选',
  date: '日期',
  daterange: '日期区间',
  user: '人员选择',
  org: '组织选择',
  tag: '标签',
  boolean: '布尔勾选',
  file: '单个附件',
  files: '多个附件',
}

/** 字段类型 → 控件（14 种全覆盖；未登记的类型落到 `unsupported`，界面须如实提示） */
export const FORM_CONTROL_BY_TYPE: Record<FormFieldType, FormControlKind> = {
  text: 'text',
  textarea: 'textarea',
  number: 'number',
  amount: 'amount',
  select: 'select',
  multiselect: 'multiselect',
  date: 'date',
  daterange: 'daterange',
  user: 'user',
  org: 'org',
  tag: 'tag',
  boolean: 'boolean',
  file: 'attachment',
  files: 'attachment',
}

/** 待补件期唯一可写的两个字段（后端 `FormWritePolicy.SUPPLEMENT_FIELDS`） */
export const SUPPLEMENT_FIELD_CODES: readonly string[] = ['attachments', 'supplement_note']

/** 补件说明字段码（`doc/forms.md` §8 的跨表单共用字段，不属于任何 schema） */
export const SUPPLEMENT_NOTE_FIELD = 'supplement_note'

/** 印鉴单归还登记的两个字段（三态**唯一例外**，仅审批中 + 仅发起人/节点⑦） */
export const SEAL_RETURN_FIELD_CODES: readonly string[] = ['return_status', 'return_date']

/** 后端「未登记字段」规则名（`FormValidationReport` 里 40308 / 干跑都用到） */
export const RULE_UNKNOWN_FIELD = 'unknownField'

/** 选择器数量上限的规则名（`doc/templates.md` §2.3 `rules[pickerLimit]`） */
export const PICKER_LIMIT_RULE = 'pickerLimit'

/** 选择器元素存在性校验的规则名（`FormPayloadValidator.RULE_PICKER_VALUE`） */
export const RULE_PICKER_VALUE = 'pickerValue'

/** 选择器数量上限的规则名（`FormPayloadValidator.RULE_PICKER_LIMIT`） */
export const RULE_PICKER_LIMIT = 'pickerLimit'

/**
 * `user` / `org` 选择器的**默认**数量上限，与后端 `FormPayloadValidator.PICKER_MAX_DEFAULT`
 * 同值（`doc/forms.md` §2 `cc_users` 行「≤20 人」）。
 *
 * ⚠ 为什么前端用「默认值」而不是模板里声明的 `max`：schema 出参的 `rules` 是**规则类型名清单**
 * （后端 `FormFieldDef#view()` 只 put `rule.type`），模板声明的 `max` 与 `message` **不在出参里**。
 * 因此当 `rules` 含 `pickerLimit` 时，UI 只能按 20 兜底并把「以服务端为准」讲清楚
 * （见 {@link resolvePickerLimit} 的 `maxUnknownFromTemplate`）。
 */
export const PICKER_LIMIT_DEFAULT = 20

/** 附件上传接口属阶段 2b.7 —— 本轮的显式「待接入」文案（**不伪造上传**） */
export const ATTACHMENT_PENDING_HINT =
  '附件上传接口属阶段 2b.7（尚未交付），本页只展示已落库的附件元数据，不提供上传入口。'

/** 三态的中文标签与提示（与后端 `WriteContext#stateLabel` 同口径，用于无服务端响应时的兜底） */
export const FORM_WRITE_STATE_LABEL: Record<FormWriteStateCode, string> = {
  DRAFT: '草稿（全部可写）',
  APPROVING: '审批中（全部只读，仅印鉴单归还状态/日期对发起人与节点⑦例外）',
  PENDING_SUPPLEMENT: '待补件（仅附件与补件说明可写；归还状态/日期同样只读）',
  CLOSED: '已完结（只读）',
}

// ================================================================ 类型与值

/** `formType` 归一（后端只认四类；未知返回 `null`，页面须如实报错而不是猜） */
export function toFormDocType(value: string | null | undefined): FormDocType | null {
  if (!value) return null
  const normalized = value.trim().toLowerCase()
  return (FORM_DOC_TYPES as readonly string[]).includes(normalized) ? (normalized as FormDocType) : null
}

/** 字段类型归一（未知返回 `null`，界面按 `unsupported` 渲染并提示模板类型非法） */
export function toFormFieldType(value: string | null | undefined): FormFieldType | null {
  if (!value) return null
  const normalized = value.trim().toLowerCase()
  return normalized in FORM_CONTROL_BY_TYPE ? (normalized as FormFieldType) : null
}

/** 字段 → 控件（未知类型 = `unsupported`） */
export function controlOfType(value: string | null | undefined): FormControlKind {
  const type = toFormFieldType(value)
  return type === null ? 'unsupported' : FORM_CONTROL_BY_TYPE[type]
}

/** 值是否「视为未填」（与后端 `ConditionEvaluator.isEmpty` 同口径：null / 空串 / 空数组 / 全空白） */
export function isEmptyFormValue(value: FormJsonValue | undefined): boolean {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim().length === 0
  if (Array.isArray(value)) return value.length === 0
  return false
}

/** 任意表单值 → 展示字符串（金额/数字保字符串，布尔转「是/否」） */
export function asDisplayText(value: FormJsonValue | undefined): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map((item) => asDisplayText(item)).join('、')
  if (typeof value === 'boolean') return value ? '是' : '否'
  return String(value)
}

/** 任意表单值 → 字符串（编辑器用；数组取首项，布尔转 `''`） */
export function asInputText(value: FormJsonValue | undefined): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.length > 0 ? String(value[0]) : ''
  if (typeof value === 'boolean') return value ? 'true' : ''
  return String(value)
}

/** 任意表单值 → 字符串数组（多选 / 附件 / 日期区间 / 人员多选） */
export function asTextList(value: FormJsonValue | undefined): string[] {
  if (value === null || value === undefined) return []
  if (Array.isArray(value)) return value.map((item) => asDisplayText(item))
  return [asDisplayText(value)]
}

/** 任意表单值 → 布尔（缺省 `false`；字符串 `'true'`/`'1'` 视为真） */
export function asBool(value: FormJsonValue | undefined): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    return normalized === 'true' || normalized === '1' || normalized === 'yes'
  }
  return false
}

// ================================================================ user / org 选择器（多值）

/**
 * 人员 / 组织字段的元素清单：**trim + 去空 + 去重（保序）**。
 *
 * 与后端 `FormPayloadValidator#pickerElements` / `FormDataService#canonicalizePickers`
 * 同口径：重复选同一个人不该把用户顶到上限外，也不该产生两遍存在性报错。
 * **单值形态返回单元素清单**（历史草稿里存的是字符串，读法不变）。
 */
export function normalizePickerValues(value: FormJsonValue | undefined): string[] {
  const unique: string[] = []
  for (const raw of asTextList(value)) {
    const item = raw.trim()
    if (item === '' || unique.includes(item)) continue
    unique.push(item)
  }
  return unique
}

/** 选择器上限判定结果（UI 用；服务端 `pickerLimit` 仍是裁决方） */
export interface PickerLimit {
  /** UI 侧上限；`0` = 本字段不限制（非 user / org） */
  max: number
  /** 上限来源：`template`（模板声明了 `pickerLimit`）/ `default`（后端默认回落）/ `none` */
  source: 'template' | 'default' | 'none'
  /** 面向用户的提示（含「服务端仍是裁决方」） */
  hint: string
  /**
   * 模板声明了 `pickerLimit`，但 schema 出参只回规则类型名、**不回 `max`**，
   * 因此 UI 用的是默认 20；此时界面必须写明「以服务端为准」而不是假装知道确切上限。
   */
  maxUnknownFromTemplate: boolean
}

/** 字段是否属于人员 / 组织选择器（按类型判，不依赖字段码命名） */
export function isPickerField(field: Pick<FormField, 'control' | 'type'>): boolean {
  return field.control === 'user' || field.control === 'org' || field.type === 'user' || field.type === 'org'
}

/**
 * 人员 / 组织字段的多选上限（`rules[pickerLimit]`；后端缺省回落 20）。
 *
 * <p>判定顺序与后端 `FormPayloadValidator#validatePickerLimit` 一致：模板声明 → 缺省 20；
 * 非 `user` / `org` 字段**不判**（返回 `max = 0`，界面不做限制，保持既有行为）。
 */
export function resolvePickerLimit(field: Pick<FormField, 'control' | 'type' | 'rules'>): PickerLimit {
  if (!isPickerField(field)) {
    return { max: 0, source: 'none', hint: '', maxUnknownFromTemplate: false }
  }
  const declared = (field.rules ?? []).includes(PICKER_LIMIT_RULE)
  const unit = field.control === 'org' || field.type === 'org' ? '个' : '人'
  const hint = declared
    ? `最多 ${PICKER_LIMIT_DEFAULT} ${unit}（模板声明了 rules[pickerLimit]；具体上限由服务端裁决，` +
      '重复选择自动去重）'
    : `最多 ${PICKER_LIMIT_DEFAULT} ${unit}（服务端默认上限 pickerLimit，重复选择自动去重）`
  return {
    max: PICKER_LIMIT_DEFAULT,
    source: declared ? 'template' : 'default',
    hint,
    maxUnknownFromTemplate: declared,
  }
}

/** 选择数量是否超限（`limit.max <= 0` = 不限制；按去重后的条数计，与服务端同口径） */
export function exceedsPickerLimit(
  values: readonly string[],
  limit: Pick<PickerLimit, 'max'>,
): boolean {
  return limit.max > 0 && values.length > limit.max
}

/**
 * 手填 id 的输入 → 元素清单（通讯录 / 组织选择器不可用时的降级路径）。
 *
 * 分隔符支持中英文逗号、顿号、分号与空白：降级路径里用户是从别处**抄 id** 过来的，
 * 只认逗号会让他们反复猜格式。
 */
export function parsePickerInput(raw: string): string[] {
  return normalizePickerValues(
    (raw ?? '')
      .split(/[,，、;；\s]+/)
      .filter((item) => item.trim() !== ''),
  )
}

// ================================================================ 金额（字符串定点，绝不用 Number）

/** 金额字面量：最多两位小数的定点十进制（可带负号；**不接受**指数、千分位、科学计数） */
const AMOUNT_PATTERN = /^-?\d+(\.\d{1,2})?$/

/** 千分位 / 空白分隔的「看起来像数字但有杂质」的输入 */
const AMOUNT_DIRTY_PATTERN = /^-?[\d,，\s]+(\.\d+)?$/

/**
 * 编辑金额输入：只保留数字、小数点与负号，**不做任何数值转换**。
 *
 * 为什么不用 `Number`：`doc/forms.md` §1.5 明确「以 `DECIMAL(18,2)` 存储，**不使用浮点数**」；
 * 一旦经 `Number` 往返，`0.1+0.2` 这类误差就会被写进请求体，服务端按「禁浮点」拒收。
 */
export function normalizeAmountInput(raw: string): string {
  let next = ''
  for (const char of raw) {
    if (char >= '0' && char <= '9') {
      next += char
      continue
    }
    if (char === '.' && !next.includes('.')) {
      next += char
      continue
    }
    if (char === '-' && next.length === 0) {
      next += char
    }
  }
  return next
}

/**
 * 金额的**字符串层面**补零到两位小数（`"1250000"` → `"1250000.00"`）。
 *
 * 与后端 `FormDataService#canonicalizeAmounts` 同口径：只做补零，不掩盖错误
 * （非法金额原样返回 `null`，交由校验报错）。
 */
export function padAmountScale(text: string, scale = 2): string | null {
  const trimmed = (text ?? '').trim()
  if (trimmed === '') return null
  if (!AMOUNT_PATTERN.test(trimmed)) {
    // 允许「整数位之后多于 scale 位」的输入走校验报错路径；这里只处理合法字面量
    if (!/^-?\d+(\.\d+)?$/.test(trimmed)) return null
  }
  const negative = trimmed.startsWith('-')
  const body = negative ? trimmed.slice(1) : trimmed
  const [intPart, fracPart = ''] = body.split('.')
  if (fracPart.length > scale) return null
  const padded = `${intPart}.${fracPart.padEnd(scale, '0')}`
  return negative ? `-${padded}` : padded
}

/** 金额校验结论（前端提示；**服务端才是裁决方**） */
export interface AmountCheck {
  ok: boolean
  /** 输入为空（未填）时为 true —— 是否允许留空由必填规则决定，不在本函数判 */
  empty: boolean
  message: string
}

/**
 * 金额的前端提示校验（字符串定点，两位小数）。
 *
 * 覆盖后端 `AmountText` 的**全局默认**拒绝口径（`doc/forms.md` §1.3）：
 *   · 空（未填，是否允许留空由必填规则决定）；
 *   · 非定点十进制（含千分位杂质 / 科学计数 / 多小数点）；
 *   · 超过两位小数（`scale = 2`）；
 *   · **必须大于 0**（`MIN = 0.01`）；
 *   · 超出上限 `99,999,999,999.99`（`MAX`）。
 *
 * ⚠ 模板可在 `rules[amountRange]` 里给出更严的 `min` / `max` / `scale`，前端**读不到规则体**
 * （schema 只回规则**类型名**），因此这里按全局默认判；服务端始终是裁决方。
 */
export function checkAmountText(raw: string, unitHint = '元'): AmountCheck {
  const text = (raw ?? '').trim()
  if (text === '') return { ok: true, empty: true, message: '' }
  if (/[eE]/.test(text)) {
    return { ok: false, empty: false, message: '金额不支持科学计数法，请填写定点十进制（如 1250000.00）' }
  }
  if (AMOUNT_DIRTY_PATTERN.test(text) && /[,，\s]/.test(text)) {
    return { ok: false, empty: false, message: '金额不能包含千分位或空格，请填写如 1250000.00' }
  }
  if (!/^-?\d+(\.\d+)?$/.test(text)) {
    return { ok: false, empty: false, message: '金额必须是定点十进制数字（如 1250000.00）' }
  }
  const negative = text.startsWith('-')
  const body = negative ? text.slice(1) : text
  const [intPart, fracPart = ''] = body.split('.')
  if (fracPart.length > 2) {
    return { ok: false, empty: false, message: '金额最多两位小数（DECIMAL(18,2)），请勿提交更多小数位' }
  }
  // 「金额必须大于 0」（doc/forms.md §1.3；后端 AmountText.MIN = 0.01）
  // 纯字符串判零，全程不出现 Number/BigDecimal —— 与本文件「金额绝不经浮点」的口径一致
  if (negative || (/^0*$/.test(intPart) && /^0*$/.test(fracPart))) {
    return { ok: false, empty: false, message: '金额必须大于 0（doc/forms.md §1.3，最小 0.01 元）' }
  }
  if (intPart.replace(/^0+/, '').length > 11) {
    return { ok: false, empty: false, message: `金额超出上限 99,999,999,999.99 ${unitHint}` }
  }
  return { ok: true, empty: false, message: '' }
}

// ================================================================ 提交值归一

/**
 * 按字段类型归一**提交值**（返回 `undefined` = 该键不提交，后端 `merge` 语义为「不动」）。
 *
 * 归一口径逐条对齐后端 `FormPayloadValidator#typeMatches`：
 *   · `TEXT/TEXTAREA/TAG/DATE/SELECT` → `CharSequence`（字符串）；
 *   · `USER/ORG` → `CharSequence | Collection`（**多值上送数组**，服务端再去重保序并逐个判存在性）；
 *   · `NUMBER/AMOUNT` → `Number | CharSequence` —— **一律字符串**（禁浮点）；
 *   · `BOOLEAN` → 布尔；
 *   · `MULTISELECT/FILES/FILE/DATERANGE` → 集合。
 *
 * `null` 的语义是「删除该键」（后端 `merge`），因此空值统一回 `null` 而不是 `undefined`
 * —— 只有 `undefined` 才表示「本次不改」。
 */
export function toSubmitValue(
  field: Pick<FormField, 'control' | 'type'>,
  value: FormJsonValue | undefined,
): FormJsonValue | undefined {
  switch (field.control) {
    case 'amount':
      if (isEmptyFormValue(value)) return null
      return padAmountScale(asInputText(value)) ?? asInputText(value)
    case 'number':
      if (isEmptyFormValue(value)) return null
      return asInputText(value).trim()
    case 'boolean':
      return asBool(value)
    case 'multiselect':
    case 'daterange':
    case 'attachment':
      return isEmptyFormValue(value) ? null : asTextList(value).filter((item) => item !== '')
    case 'user':
    case 'org': {
      // 多值：一律上送**数组**（本地先按服务端口径去重保序，服务端仍会兜底一次）。
      // 空 → null（清空该键），不回空串：空串在服务端会被当成「一个元素」去做存在性判定。
      const list = normalizePickerValues(value)
      return list.length === 0 ? null : list
    }
    default:
      return isEmptyFormValue(value) ? null : asDisplayText(value)
  }
}

/** 整表归一（提交前用；未登记字段已在渲染层过滤，这里不再加白名单） */
export function toSubmitFields(
  fields: readonly Pick<FormField, 'code' | 'control' | 'type'>[],
  values: Record<string, FormJsonValue>,
): Record<string, FormJsonValue> {
  const payload: Record<string, FormJsonValue> = {}
  for (const field of fields) {
    const normalized = toSubmitValue(field, values[field.code])
    if (normalized === undefined) continue
    payload[field.code] = normalized
  }
  return payload
}

/**
 * 模板默认值的前端预填（**只补 `boolean` 与 `select` 两类**）。
 *
 * 与后端 `FormDataService#applyDefaults` 同口径：其余类型不自动补
 * （避免把 `cost_bearer` 的符号型占位值 `"initiator_company"` 当成可落库取值写进去）。
 */
export function applyFieldDefaults(
  schema: Pick<FormSchema, 'fields'>,
  values: Record<string, FormJsonValue>,
): Record<string, FormJsonValue> {
  const next: Record<string, FormJsonValue> = { ...values }
  for (const field of schema.fields) {
    if (!field.hasDefaultValue || field.defaultValue === null) continue
    if (!isEmptyFormValue(next[field.code])) continue
    if (field.control === 'boolean' && typeof field.defaultValue === 'boolean') {
      next[field.code] = field.defaultValue
      continue
    }
    if (field.control === 'select' && typeof field.defaultValue === 'string') {
      next[field.code] = field.defaultValue
    }
  }
  return next
}

// ================================================================ 金额角色判定

/**
 * 可写金额的角色码（权威源 `com.oa.authz.visibility.VisibilityRoles`：
 * `ADMIN = "admin"`（系统管理员）、`FINANCE_OWNER = "finance_owner"`（集团归口/财务部负责人
 * = PRD 所称「财务角色」））。
 *
 * 与 `AmountFieldPolicy#canWriteAmounts` 逐字一致：
 * `principal.hasRole(ADMIN) || roleCodes.contains(FINANCE_OWNER)`。
 */
export const AMOUNT_WRITABLE_ROLES: readonly string[] = ['admin', 'finance_owner']

/** 金额写权限判定的最小只读投影（便于脱离 Pinia 自测） */
export interface AmountWriteSubject {
  readonly isSuperAdmin: boolean
  readonly permissions: readonly string[]
  readonly roleCodes: readonly string[]
}

/** 金额写权限结论 */
export interface AmountWriteVerdict {
  writable: boolean
  /** 面向用户的判定文案（可写时也给，用于解释「为什么这个框能填」） */
  reason: string
  /** 兜底提示（可写时给出「以服务端 40306 为准」；只读时为空串） */
  note: string
}

/**
 * 金额字段是否可写（PRD §5.3）。
 *
 * <p><b>判定顺序</b>：**① 服务端 `amountPolicy.writable`（权威）→ ② 角色码兜底**。
 *
 * <p>2026-10-04（后端 7c409ea）起 `GET /forms/{formType}/field-groups` 的 `amountPolicy`
 * 改为**按当前登录主体**计算（`FormRuleController` 注入 `FormFieldWriteGuard`），
 * 实测 admin / finance_owner → `writable:true`，employee → `false`，与写路径（40306）同结论。
 * 因此前端不再需要「角色码优先 + 披露不一致」的那套绕行逻辑：
 * 服务端下发了就以它为准（**删除**此前「服务端 amountPolicy 与角色口径不一致」的披露文案）。
 *
 * <p>只在**取不到** `amountPolicy`（字段分组接口失败 / 实例页静默降级为 `null`）时，
 * 才回落到角色码判定 —— 否则一次网络抖动就会把 admin 的金额框锁死，
 * 那正是此前「发起页金额框填不了」的成因。回落时明确写出该判据来自本地角色码。
 *
 * <p>⚠ 前端只决定「渲不渲染 / 放不放行」：越权写入仍由服务端按 403/40306 拒绝。
 */
export function resolveAmountWrite(
  subject: AmountWriteSubject | null | undefined,
  serverPolicy: { writable: boolean } | null | undefined,
): AmountWriteVerdict {
  const roleWritable =
    subject != null &&
    (subject.isSuperAdmin || subject.roleCodes.some((role) => AMOUNT_WRITABLE_ROLES.includes(role)))
  /** 兜底提示：前端放行不等于一定能写（服务端 40306 仍是裁决方） */
  const serverFallbackNote = '能否写入以服务端 40306 为准（前端置灰只是提示，不是边界）。'

  if (serverPolicy != null) {
    return serverPolicy.writable
      ? {
          writable: true,
          reason: '服务端下发 amountPolicy.writable=true（按当前登录主体计算，与写路径 40306 同结论）',
          note: serverFallbackNote,
        }
      : {
          writable: false,
          reason: `服务端下发 amountPolicy.writable=false：金额对当前主体只读（可写角色：${AMOUNT_WRITABLE_ROLES.join(' / ')}；PRD §5.3）`,
          note: '',
        }
  }

  if (roleWritable) {
    return {
      writable: true,
      reason: `未取到服务端 amountPolicy，按本地角色码兜底放行（${AMOUNT_WRITABLE_ROLES.join(' / ')}；PRD §5.3）`,
      note: `服务端字段分组接口未返回 amountPolicy，已按角色码放行；${serverFallbackNote}`,
    }
  }
  return {
    writable: false,
    reason: `金额字段对非财务类角色只读（可写角色：${AMOUNT_WRITABLE_ROLES.join(' / ')}；PRD §5.3，服务端按 40306 拒绝）`,
    note: '',
  }
}

// ================================================================ 新建草稿的合成状态

/**
 * 为新键草稿合成「草稿态全可写」的三态上下文。
 *
 * <p><b>为什么必须有它</b>：`/form/new/:formType` 时实例**尚不存在**，没有 `instanceId`，
 * 也就没有 `GET /forms/instances/{id}/writable-fields` 可取。若把 `state` 传 `null`，
 * 渲染器会按「白名单未知 → 全部只读」处理（保守但对新建页是**错**的：新建时全部字段都可写），
 * 结果就是发起页一个字都填不进去。
 *
 * <p>白名单语义只在**已有实例**时生效；新建时的字段级限制**只有角色维度**那些
 * （金额对非财务角色只读，见 {@link resolveAmountWrite}）。服务端的 `FormStateWriteGuard`
 * 仍是边界：越权/非法载荷由 40304 / 40306 / 40011 拒绝。
 */
export function syntheticDraftState(
  formType: string | null | undefined,
  fieldCodes: readonly string[],
): FormWriteState {
  const type = (formType ?? '').trim().toUpperCase()
  return {
    state: 'DRAFT',
    formType: type === '' ? 'MATTER' : type,
    isInitiator: true,
    isArchiveNode: false,
    writableFields: [...fieldCodes],
    readonlyFields: [],
    stateLabel: FORM_WRITE_STATE_LABEL.DRAFT,
    evidence:
      '前端合成：新建草稿尚无实例，按「草稿态全部可写」渲染（doc/forms.md §1.2）；' +
      '服务端 FormStateWriteGuard 仍是边界（越权写入 403/40304、金额角色 40306）。',
  }
}

// ================================================================ 字段可写性（状态 ∧ 金额角色）

/** 字段可写性判定的上下文 */
export interface FieldEditabilityContext {
  /** 三态白名单（新建页传合成态；详情页可传 `null` 表示「白名单未知」） */
  state: FormWriteState | null
  /** 金额字段是否可写（由 {@link resolveAmountWrite} 得出，与状态正交） */
  amountWritable: boolean
  /** 金额只读时的专用原因（缺省回落到状态层原因） */
  amountReadonlyReason?: string
  /** 强制只读（详情页只读视图）；非空时优先级最高 */
  forceReadonlyReason?: string
}

/** 字段是否属于金额字段（按字段类型判，不依赖后端字段码命名） */
export function isAmountField(field: Pick<FormField, 'control' | 'type'>): boolean {
  return field.control === 'amount' || field.type === 'amount'
}

/**
 * 字段只读原因（`null` = 可写）。判定顺序：**强制只读 → 金额角色 → 状态白名单**。
 *
 * <p>为什么金额角色优先于状态：两者**正交**（PRD §5.3「金额对非财务类角色只读，
 * 草稿态同样拒绝」）。金额只读时给出**角色**原因，用户才知道该换谁来做，
 * 而不会被误导成「等状态变成草稿就能填了」。
 */
export function resolveFieldReadonlyReason(
  field: Pick<FormField, 'code' | 'label' | 'locked' | 'control' | 'type'>,
  ctx: FieldEditabilityContext,
): string | null {
  if (ctx.forceReadonlyReason) return ctx.forceReadonlyReason
  if (isAmountField(field) && !ctx.amountWritable) {
    return ctx.amountReadonlyReason ?? readonlyReason(field, ctx.state, { amountWritable: false })
  }
  return readonlyReason(field, ctx.state, { amountWritable: true })
}

// ================================================================ 分组

/**
 * 把 schema 的 `fields[]` 按 `sections[]` 分组；**未列入任何分组的字段补到末尾**。
 *
 * 为什么要兜底：`sections` 在 schema 里是**可选**键（`doc/templates.md` §2.1），
 * 且分组只列字段码、字段体在 `fields[]`（`FormSchema.Section`）。若渲染器只按
 * `sections` 渲染，未分组字段会**静默消失** —— 那是「表单少了一格」这类最难查的缺陷。
 */
export function groupFields(
  schema: Pick<FormSchema, 'fields' | 'sections'>,
): Array<{ section: FormSection; fields: FormField[] }> {
  const byCode = new Map(schema.fields.map((field) => [field.code, field]))
  const used = new Set<string>()
  const groups: Array<{ section: FormSection; fields: FormField[] }> = []

  for (const section of schema.sections) {
    const fields: FormField[] = []
    for (const code of section.fieldCodes) {
      const field = byCode.get(code)
      if (!field) continue
      if (used.has(code)) continue
      used.add(code)
      fields.push(field)
    }
    if (fields.length > 0) groups.push({ section, fields })
  }

  const rest = schema.fields.filter((field) => !used.has(field.code))
  if (rest.length > 0) {
    groups.push({
      section: {
        id: '__ungrouped__',
        title: '未分组字段',
        printTitle: '未分组字段',
        collapsible: false,
        fieldCodes: rest.map((field) => field.code),
      },
      fields: rest,
    })
  }
  return groups
}

// ================================================================ 三态只读原因

/** 单据状态的展示（服务端未给 `stateLabel` 时的兜底） */
export function writeStateLabel(state: FormWriteStateCode | null | undefined): string {
  return state ? FORM_WRITE_STATE_LABEL[state] : '未知状态'
}

/**
 * 字段只读原因（`null` = 可写）。
 *
 * **必须给原因**（任务书 A 段：「只读字段要**明确置灰并给原因**，不要只是 disabled」）。
 * 原因文本逐条对应真源章节，便于用户据此走正确路径（如「驳回 → 修改 → 重新提交」）。
 */
export function readonlyReason(
  field: Pick<FormField, 'code' | 'label' | 'locked'>,
  state: FormWriteState | null,
  options: { amountWritable?: boolean; amountFieldCodes?: readonly string[] } = {},
): string | null {
  if (!state) return '尚未取得服务端下发的可写字段白名单，已按只读渲染'
  const writable = new Set(state.writableFields)
  if (writable.has(field.code)) {
    if (field.locked) return '模板将该字段标记为锁定（locked=true），仅展示默认值'
    return null
  }
  if (field.locked) return '模板锁定字段（locked=true）：任何状态都不可编辑，仅展示默认值'
  if (options.amountFieldCodes?.includes(field.code) && options.amountWritable === false) {
    return '金额字段对非财务类角色只读（PRD §5.3；越权写入按 40306 拒绝）'
  }
  switch (state.state) {
    case 'APPROVING':
      return '审批中：主字段一律只读；印鉴单归还状态/日期对发起人与节点⑦是唯一例外（doc/forms.md §5）'
    case 'PENDING_SUPPLEMENT':
      return '待补件期仅「附件」与「补件说明」可写；改主字段请走「驳回 → 修改 → 重新提交」（doc/forms.md §7）'
    case 'CLOSED':
      return '单据已完结（通过/驳回/终止后），全部字段只读（doc/data-model.md §10）'
    case 'DRAFT':
    default:
      return '不在服务端下发的可写字段白名单内（越权写入按 403/40304 拒绝）'
  }
}

/** 字段是否可写（与 `readonlyReason` 同源，避免两处判据漂移） */
export function isFieldWritable(
  field: Pick<FormField, 'code' | 'label' | 'locked'>,
  state: FormWriteState | null,
  options: { amountWritable?: boolean; amountFieldCodes?: readonly string[] } = {},
): boolean {
  return readonlyReason(field, state, options) === null
}

/**
 * 单据是否处于「附件与补件说明可写」的补件态。
 * 用于在界面上把补件通道单独标出来（该通道的写入口是 `PUT /forms/instances/{id}/draft`）。
 */
export function isSupplementState(state: FormWriteState | null): boolean {
  return state?.state === 'PENDING_SUPPLEMENT'
}

// ================================================================ 校验报告

/** 把服务端结构化报告归一为领域模型（`parsedFromMessage=false`） */
export function toValidationReport(wire: {
  passed: boolean
  issueCount: number
  issues: Array<{ field: string; label: string; rule: string; message: string }>
}): FormValidationReport {
  return {
    passed: wire.passed,
    issueCount: wire.issueCount,
    issues: wire.issues.map((issue) => ({
      field: issue.field ?? '',
      label: issue.label ?? '',
      rule: issue.rule ?? '',
      message: issue.message ?? '',
    })),
    parsedFromMessage: false,
    unrestored: '',
  }
}

/**
 * `40011` 响应体里的**结构化**逐字段明细（`details.errors[]`）→ 本域报告。
 *
 * <p>服务端 `FormValidationReport#issueViews()` 是干跑 `report.issues[]` 与 40011
 * `details.errors[]` 的**唯一生产者**，两者同源同形；因此这条路径产出的报告与干跑
 * 200 出参的报告可直接互换（`parsedFromMessage=false`）。
 *
 * <p>返回 `null` 表示「响应里没有可用的结构化明细」（老响应 / 其它错误码 / 形状未知），
 * 调用方此时**必须**回落到 {@link parseValidationMessage} 的文本还原，不能把错误吞掉。
 */
export function validationReportFromDetails(
  details: ApiErrorDetails | null | undefined,
): FormValidationReport | null {
  const raw = details?.errors
  if (!Array.isArray(raw)) return null
  const issues: FormFieldIssue[] = []
  for (const item of raw) {
    if (item === null || typeof item !== 'object') continue
    const record = item as ApiFieldErrorDetail
    const field = typeof record.field === 'string' ? record.field : ''
    const message = typeof record.message === 'string' ? record.message : ''
    if (field === '' && message === '') continue
    issues.push({
      field,
      label: typeof record.label === 'string' ? record.label : '',
      rule: typeof record.rule === 'string' ? record.rule : '',
      message,
    })
  }
  if (issues.length === 0) return null
  return {
    passed: false,
    issueCount: issues.length,
    issues,
    parsedFromMessage: false,
    unrestored: '',
  }
}

/** 报告 → 逐字段错误索引（渲染器据此把错误挂到对应控件上） */
export function buildIssueIndex(report: FormValidationReport | null): Record<string, FormFieldIssue[]> {
  const index: Record<string, FormFieldIssue[]> = {}
  if (!report) return index
  for (const issue of report.issues) {
    const key = issue.field || UNBOUND_ISSUE_KEY
    if (!index[key]) index[key] = []
    index[key].push(issue)
  }
  return index
}

/**
 * `buildIssueIndex` 里**没有字段码**的那些条目（`field` 为空）的键。
 *
 * 服务端逐字段明细总带字段码；`field` 为空的只可能来自 message 文本还原
 * （`parseValidationMessage` 的兜底路径）或整单级拒绝。它们**必须**进「整单错误区」——
 * 挂在 `__unbound__` 而渲染器只读 `errors[field.code]`，等于把错误静默丢掉。
 */
export const UNBOUND_ISSUE_KEY = '__unbound__'

/** 报告里无法归到具体字段的文案（整单错误区用） */
export function unboundIssueMessages(report: FormValidationReport | null): string[] {
  const list = buildIssueIndex(report)[UNBOUND_ISSUE_KEY] ?? []
  return list.map((issue) => issue.message || issue.label || issue.field).filter((message) => message !== '')
}

/** 逐字段错误的第一条文案（挂到控件下方） */
export function firstIssueMessage(report: FormValidationReport | null, fieldCode: string): string {
  const index = buildIssueIndex(report)
  const list = index[fieldCode]
  return list && list.length > 0 ? list[0].message : ''
}

/**
 * `40011`（`FORM_VALIDATION_FAILED`）的 message 文本还原 —— **降级路径**。
 *
 * <p><b>首选路径已改变</b>（2026-10-04，后端 7c409ea）：40011 的响应体现在带
 * `details.errors[]`（`{field,label,rule,message}`，与干跑 `report.issues[]` 同源），
 * 因此正常情况用 {@link validationReportFromDetails} 即可。
 *
 * <p>本函数只在**结构化明细不可得**时使用：老响应（未部署该版本的服务端）、
 * 其它错误码、或 `details` 形状未知。`FormValidationReport#summary()` 的文案形如
 * `表单字段校验未通过（3 项）：title（事项标题）：请填写事项标题；…`，
 * 字段码与原因都在文本里，因此做**尽力还原**：
 * 能定位到字段的挂到该字段上，定位不到的放进 `unrestored` 原样展示（不丢信息）。
 */
export function parseValidationMessage(message: string): FormValidationReport {
  const text = (message ?? '').trim()
  const report: FormValidationReport = {
    passed: false,
    issueCount: 0,
    issues: [],
    parsedFromMessage: true,
    unrestored: '',
  }
  if (text === '') return report

  // 去掉「表单字段校验未通过（N 项）：」前缀（若有）
  let body = text
  const prefix = text.match(/^[^：:]*（\s*\d+\s*项\s*）\s*[：:]/)
  if (prefix) body = text.slice(prefix[0].length)

  const unrestored: string[] = []
  for (const chunk of body.split('；')) {
    const item = chunk.trim()
    if (item === '') continue
    const matched = item.match(/^([^（(：:]+)\s*(?:[（(]([^）)]*)[）)])?\s*[：:]\s*(.*)$/)
    if (matched && matched[1] && matched[3]) {
      report.issues.push({
        field: matched[1].trim(),
        label: (matched[2] ?? '').trim() || matched[1].trim(),
        rule: '',
        message: matched[3].trim(),
      })
      continue
    }
    unrestored.push(item)
  }
  report.issueCount = report.issues.length
  report.unrestored = unrestored.join('；')
  return report
}

/**
 * 错误码 → 中文语义补充（在服务端 message 之外给出的「这是什么、该怎么办」）。
 *
 * 覆盖任务书点名要如实展示的错误码：`40011` / `40304` / `40308` / `40309` / `40301` /
 * `40306` / `40007` / `40908`–`40915` / `40009`。
 */
export const FORM_ERROR_HINT: Record<number, string> = {
  40007: '发起前预检拦截：存在节点解析不出有效审批人 —— 请先补齐组织负责人等配置（清单见下方逐条拦截项）',
  40009: '驳回意见不足 5 个字（REQ-FLOW-013 / AC-50），请补足后重试',
  40011: '表单服务端二次校验未通过：下方按字段列出了全部不合格项（明细取自响应的 details.errors[]，与干跑接口同源）',
  40301: '当前账号没有该操作所需的权限码（服务端仍是裁决方）',
  40304: '三态白名单拒绝写入：该字段在当前单据状态下不可写',
  40306: '金额字段对非财务类角色只读（PRD §5.3）—— 与单据状态无关，草稿态同样拒绝',
  40308: '载荷里夹带了未在表单模板中登记的字段，整单请求已被拒绝（doc/templates.md §2.5）',
  40309: '事项类别一经发起不可改判；唯一处理路径是「驳回 → 修改 → 重新提交」',
  40908: 'Q6 闸门：全单「流转 + 回退」次数已达模板配置上限',
  40909: 'Q6 闸门：全单补件次数已达模板配置上限',
  40910: '当前节点或单据状态不允许该动作（节点开关关闭 / 任务非待处理 / ⑦登记节点不接受「通过」）',
  40913: 'Q7：同一节点已请求过补件，不能再请求（同节点 ≤1）',
  40914: '归档守卫：该版本是此单据类型唯一的已发布版本，归档后无法发起新单据',
  40915: '恢复冲突：该单据类型已有已发布版本，无法再恢复历史版本',
}

/** 错误码的补充说明（无则空串） */
export function errorHint(code: number | string | null | undefined): string {
  const numeric = typeof code === 'string' ? Number(code) : code
  if (numeric === null || numeric === undefined || !Number.isFinite(numeric)) return ''
  return FORM_ERROR_HINT[numeric as number] ?? ''
}

/**
 * 服务端校验规则名 → 中文短标签（报告面板里 `issue.rule` 的展示用）。
 *
 * <p>后端在 `rule` 里回的是**规则类型名**（`FormPayloadValidator` 的 `RULE_*`）；
 * 直接把 `pickerLimit` 这类英文码丢给用户等于没解释。未登记的规则**原样回显**（不猜）。
 */
export const FORM_RULE_LABEL: Record<string, string> = {
  required: '必填',
  unknownField: '未登记字段',
  typeMismatch: '类型不匹配',
  pickerValue: '通讯录/组织存在性',
  pickerLimit: '选择数量上限',
  amountRange: '金额区间',
  numberRange: '数字区间',
  maxLength: '长度上限',
  minLength: '长度下限',
  pattern: '格式',
  inDict: '字典取值',
  unique: '唯一性',
  conditionalRequired: '条件必填',
  conditionalMinLength: '条件长度下限',
  dateNotBefore: '不早于指定日期',
  dateNotBeforeField: '日期先后关系',
  filePolicy: '附件策略',
}

/** 规则名 → 中文标签（未登记原样回显） */
export function formRuleLabel(rule: string | null | undefined): string {
  const code = (rule ?? '').trim()
  if (code === '') return ''
  return FORM_RULE_LABEL[code] ?? code
}

/** 预检拦截项 → 一行可读文案（节点 / 规则 / 缺什么配置） */
export function describeBlocker(blocker: FlowPrecheckBlocker): string {
  const node = blocker.nodeSeq === null ? blocker.nodeName : `节点${blocker.nodeSeq} ${blocker.nodeName}`
  const rule = blocker.ruleLabel || blocker.rule || '（规则未标注）'
  return `${node} · ${rule}：${blocker.reason}`
}

/** 校验报告 → 面向用户的一行摘要 */
export function summarizeReport(report: FormValidationReport | null): string {
  if (!report) return ''
  if (report.passed) return '校验通过'
  const parts = report.issues.map((issue) => `${issue.label || issue.field}：${issue.message}`)
  if (report.unrestored) parts.push(report.unrestored)
  return `共 ${report.issueCount} 项不合格 —— ${parts.join('；')}`
}
