/**
 * oa-web · 身份域校验规则与枚举中文映射
 * ----------------------------------------------------------------------------
 * 来源：`doc/import-spec.md` §3（五个模板逐列说明）、§4（可判定校验规则编号）
 *       + `doc/prd-0.1.md` 5.1 / 5.5
 *
 * ⚠ 权威性声明（必须保留在代码里）：**服务端才是权威**。
 *   本文件的规则只用于表单的即时提示（`el-form` 的 rules），任何一条都可能被
 *   服务端的库内校验否决，典型如：
 *     · E-USER-002 / E-USER-015 —— account / employee_no 库内唯一（前端无法判定）
 *     · E-ORG-001 / E-ORG-003  —— org_path 唯一且与 parent_path 结构一致
 *     · E-POS-004 / E-POS-005  —— (user, org) 不重复、每人最多一个主岗
 *     · E-LEAD-004 / E-LEAD-009 —— 同组织同业务线只能一个正职
 *     · E-USER-009 / E-ORG-010 —— 离职前待办清零、停用前在途清零（服务端查库）
 *   前端只做「提交前尽早提示」，不得把校验通过当作业务保证。
 */
import type { FormType } from '@/types/api'
import type { BusinessLine, ImpactLevel, ImpactReason, OrgStatus, OrgType, UserStatus } from '@/types/identity'

// ---------------------------------------------------------------------------
// 1. 正则与长度（逐条对应 import-spec §4 的规则编号）
// ---------------------------------------------------------------------------
/** E-USER-001：8–64 位、字母开头，仅含字母/数字/下划线/点/连字符 */
export const ACCOUNT_PATTERN = /^[A-Za-z][A-Za-z0-9_.-]{7,63}$/
/** E-USER-014：1–32 字符，仅含字母、数字与 `-`（例 A0001） */
export const EMPLOYEE_NO_PATTERN = /^[A-Za-z0-9-]{1,32}$/
/** E-USER-003：11 位大陆手机号 */
export const PHONE_PATTERN = /^1[3-9]\d{9}$/
/** E-USER-004：邮箱（宽松格式，服务端再做权威校验） */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
/** E-ORG-008：组织路径不得含空段、不得有首尾 `/` */
export const ORG_PATH_PATTERN = /^[^/]+(?:\/[^/]+)*$/

export const FIELD_LIMIT = {
  /** E-ORG-012 */
  orgName: 100,
  /** E-ORG-008 */
  orgPath: 255,
  /** E-USER-010 */
  userName: 50,
  /** E-POS-006 */
  postName: 50,
  /** E-USER-004 */
  email: 128,
  /** E-ORG-013 / E-USER-012 / E-POS-007 / E-LEAD-010 统一 255 */
  remark: 255,
} as const

// ---------------------------------------------------------------------------
// 2. 即时提示文案（表单 placeholder / 校验失败说明共用同一份口径）
// ---------------------------------------------------------------------------
export const RULE_HINT = {
  account: '8–64 位，字母开头，仅可含字母、数字、下划线、点、连字符',
  employeeNo: '1–32 位，仅可含字母、数字、连字符；水印取「姓名 + 工号」',
  phone: '11 位大陆手机号，如 13800001234',
  postName: '岗位名称不超过 50 字',
  orgName: '组织名称不超过 100 字',
  remark: '备注不超过 255 字',
} as const

/** 返回空串表示通过；返回文案表示即时提示（服务端仍是权威） */
export function checkAccount(value: string): string {
  if (!value) return '账号必填'
  if (!ACCOUNT_PATTERN.test(value)) return `账号格式不合规：${RULE_HINT.account}`
  return ''
}

export function checkEmployeeNo(value: string): string {
  if (!value) return '工号必填'
  if (!EMPLOYEE_NO_PATTERN.test(value)) return `工号格式不合规：${RULE_HINT.employeeNo}`
  return ''
}

export function checkPhone(value: string): string {
  if (!value) return '手机号必填'
  if (!PHONE_PATTERN.test(value)) return `手机号格式不合规：${RULE_HINT.phone}`
  return ''
}

export function checkEmail(value: string): string {
  if (!value) return ''
  if (value.length > FIELD_LIMIT.email) return `邮箱不超过 ${FIELD_LIMIT.email} 字符`
  if (!EMAIL_PATTERN.test(value)) return '邮箱格式不合规'
  return ''
}

export function checkUserName(value: string): string {
  if (!value) return '姓名必填'
  if (value.length > FIELD_LIMIT.userName) return `姓名不超过 ${FIELD_LIMIT.userName} 字`
  return ''
}

export function checkOrgName(value: string): string {
  if (!value) return '组织名称必填'
  if (value.length > FIELD_LIMIT.orgName) return `组织名称不超过 ${FIELD_LIMIT.orgName} 字`
  return ''
}

export function checkPostName(value: string): string {
  if (!value) return '岗位名称必填'
  if (value.length > FIELD_LIMIT.postName) return `岗位名称不超过 ${FIELD_LIMIT.postName} 字`
  return ''
}

export function checkRemark(value: string): string {
  if (value && value.length > FIELD_LIMIT.remark) return `备注不超过 ${FIELD_LIMIT.remark} 字`
  return ''
}

/** 组织全路径拼接：与 import-spec §3.2 `org_path` 口径一致（段间 `/`，无首尾 `/`） */
export function joinOrgPath(parentPath: string, name: string): string {
  const parent = (parentPath || '').replace(/^\/+|\/+$/g, '')
  return parent ? `${parent}/${name}` : name
}

// ---------------------------------------------------------------------------
// 3. 层级约束（E-ORG-006）：集团→公司→部门→科室
//    公司必须挂在集团下；科室必须挂在部门下；部门可挂在公司或集团下
// ---------------------------------------------------------------------------
export function allowedChildTypes(parentType: OrgType | null): OrgType[] {
  switch (parentType) {
    case 'group':
      return ['company', 'dept']
    case 'company':
      return ['dept']
    case 'dept':
      return ['section']
    // 无父节点 = 新建根节点：整棵组织树有且仅有 1 个集团（E-ORG-015）
    case null:
    case 'section':
    default:
      return ['group']
  }
}

// ---------------------------------------------------------------------------
// 4. 枚举中文映射（与 import-spec §3 的中文标签逐字一致）
// ---------------------------------------------------------------------------
export const ORG_TYPE_LABEL: Record<OrgType, string> = {
  group: '集团',
  company: '公司',
  dept: '部门',
  section: '科室',
}

export const ORG_TYPE_OPTIONS: Array<{ value: OrgType; label: string }> = (
  ['group', 'company', 'dept', 'section'] as OrgType[]
).map((value) => ({ value, label: ORG_TYPE_LABEL[value] }))

export const ORG_STATUS_LABEL: Record<OrgStatus, string> = {
  active: '启用',
  disabled: '停用',
}

export const USER_STATUS_LABEL: Record<UserStatus, string> = {
  active: '在职',
  resigned: '离职',
  disabled: '停用',
}

export const USER_STATUS_OPTIONS: Array<{ value: UserStatus; label: string }> = (
  ['active', 'resigned', 'disabled'] as UserStatus[]
).map((value) => ({ value, label: USER_STATUS_LABEL[value] }))

export const LEADER_TYPE_LABEL: Record<'primary' | 'deputy', string> = {
  primary: '正职',
  deputy: '副职',
}

/** 事项类别五值中文标签；业务线直接复用，不新增枚举（T-06 定稿） */
export const BUSINESS_LINE_LABEL: Record<BusinessLine, string> = {
  business: '经营',
  economy: '经济',
  admin: '行政',
  hr: '人力',
  invest: '投资',
}

/** 集团层业务线绑定的展示顺序（经营/经济/行政/人力/投资） */
export const BUSINESS_LINE_ORDER: BusinessLine[] = ['business', 'economy', 'admin', 'hr', 'invest']

export const IMPACT_LEVEL_LABEL: Record<ImpactLevel, string> = {
  high: '高',
  medium: '中',
  low: '低',
}

export const IMPACT_REASON_LABEL: Record<ImpactReason, string> = {
  org_disable: '组织停用',
  org_move: '组织移动',
  initiator_transfer: '发起人调岗',
  initiator_resign: '发起人离职',
  approver_transfer: '审批人调岗',
  approver_resign: '审批人离职',
  leader_change: '负责人变更',
}

/** 影响程度 → 徽标类名（只复用全局 5 类状态色，不新造配色） */
export const IMPACT_LEVEL_CLASS: Record<ImpactLevel, 'is-rejected' | 'is-pending' | 'is-closed'> = {
  high: 'is-rejected',
  medium: 'is-pending',
  low: 'is-closed',
}

/** 单号前缀与导入导出的类型中文名（服务端下发 formTypeLabel 时以前者优先） */
export const FORM_TYPE_LABEL: Record<FormType, string> = {
  matter: '事项审批单',
  fund: '资金审批单',
  contract: '合同审批单',
  seal_cert: '印鉴证照审批单',
}

export function impactReasonText(reason: ImpactReason, fallback = '组织/人员变更'): string {
  return IMPACT_REASON_LABEL[reason] ?? fallback
}

/** 单据类型中文名：服务端下发优先；两边都缺时显示「—」（后端待办接口只回单号与节点名） */
export function formTypeText(formType: FormType | undefined, serverLabel?: string | null): string {
  if (serverLabel) return serverLabel
  if (!formType) return '—'
  return FORM_TYPE_LABEL[formType] ?? '—'
}
