/**
 * oa-web · 接口契约类型
 * 来源：`doc/tech-design.md` §5 + `normify-oa/api-index.json` 的 API 契约
 *      + `normify-oa/modules/oa/portal/**`、`oa/identity/**`、`oa/workflow/**`
 *
 * 命名约定：后端统一响应体（`oa.integration.gateway.error`）为
 *   { code, message, data, traceId }
 * 前端在 `api/http.ts` 里解包，因此本文件里的 DTO 只描述 `data` 部分。
 */

// ---------------------------------------------------------------------------
// 统一响应与错误
// ---------------------------------------------------------------------------
export interface ApiEnvelope<T> {
  /** 0 表示成功；非 0 为稳定业务错误码 */
  code: number | string
  message?: string
  data?: T
  /** 追踪 ID：使审计记录能回溯到原始请求（gateway/error） */
  traceId?: string
}

export interface ApiErrorPayload {
  code: number | string
  message: string
  traceId?: string
  httpStatus?: number
}

// ---------------------------------------------------------------------------
// 身份与鉴权（oa.identity.session / oa.identity.user）
// ---------------------------------------------------------------------------
/** POST /api/v1/auth/login */
export interface LoginRequest {
  account: string
  password: string
  /** REQ-USER-002：「记住我」7 天内免登录 */
  rememberMe?: boolean
  deviceFingerprint?: string
  captcha?: string
}

export interface LoginResult {
  user: CurrentUser
  /** 会话到期时间（记住我 = 7 天） */
  expiresAt?: string
  /** 被软踢出的最早会话（REQ-USER-003 多设备上限，默认 3） */
  revokedSessionId?: string
}

/** GET /api/v1/auth/lock-status —— 失败 5 次锁 15 分钟（REQ-NFR-005） */
export interface LockStatus {
  account: string
  locked: boolean
  failedAttempts: number
  maxAttempts: number
  /** 剩余锁定秒数 */
  lockRemainingSeconds: number
  lockedUntil?: string
}

export interface PasswordPolicy {
  minLength: number
  requireLetter: boolean
  requireDigit: boolean
  requireSpecial: boolean
  maxAttempts: number
  lockMinutes: number
}

/** GET /api/v1/auth/sessions —— 多设备会话列表 */
export interface SessionDevice {
  sessionId: string
  deviceFingerprint: string
  deviceLabel: string
  ip: string
  userAgent: string
  loginAt: string
  lastActiveAt: string
  expiresAt: string
  current: boolean
  revokedAt?: string | null
  revokedReason?: string | null
}

/** GET /api/v1/auth/client-config */
export interface ClientConfig {
  /** 多设备同时在线上限，默认 3 */
  maxDevices: number
  /** 「记住我」天数，默认 7 */
  rememberMeDays: number
  upload: {
    maxFileSizeMb: number
    maxFilesPerSubmit: number
    maxFilesPerDocument: number
    allowedExtensions: string[]
  }
}

// ---------------------------------------------------------------------------
// 当前用户与数据域（oa.authz.scope / oa.authz.visibility）
// ---------------------------------------------------------------------------
export type DataScope = 'self' | 'dept' | 'company' | 'group_all' | 'group_category'

/** 归口类别：资金 / 合同 / 印鉴 恒经财务部（②） */
export type CategoryCode =
  | 'matter'
  | 'fund'
  | 'contract'
  | 'seal_cert'
  | 'cert_license'

export interface CurrentUser {
  userId: string
  name: string
  /** 工号：水印「姓名 + 工号」的工号来源（REQ-USER-004 / AC-44） */
  employeeNo: string
  account: string
  avatarText: string
  phoneMasked: string
  email?: string
  orgId: string
  orgName: string
  companyId: string
  companyName: string
  /** 组织路径，用于数据域前缀匹配（tech-design §5.3） */
  orgPath: string
  positionName?: string
  roles: RoleBrief[]
  /** 数据域集合，取并集 */
  dataScopes: DataScope[]
  /** 归口类别（集团职能部门用） */
  categories: CategoryCode[]
  permissions: string[]
  /** 是否已预存手写签名（oa.sign.preset，签名预存标记） */
  signaturePresetReady: boolean
  /** 水印开关（个人偏好，默认开启） */
  watermarkEnabled: boolean
  watermarkOpacity: number
  /** 管理员兜底能力（oa.admin.boundary） */
  isSuperAdmin: boolean
}

export interface RoleBrief {
  roleId: string
  roleCode: string
  roleName: string
  dataScope: DataScope
}

/** GET /api/v1/portal/watermark/profile —— 水印文本与透明度区间 */
export interface WatermarkProfile {
  /** 姓名 + 工号，例如 "降泽宇 · 10086" */
  text: string
  name: string
  employeeNo: string
  /** 服务端下发区间，前端必须收敛到 0.05–0.08 */
  opacityMin: number
  opacityMax: number
  rotate: number
  gapX: number
  gapY: number
  enabled: boolean
}

// ---------------------------------------------------------------------------
// 审批工作台（oa.portal.workbench）
// ---------------------------------------------------------------------------
export type WorkbenchTab = 'pending' | 'approved' | 'initiated' | 'cc'

export type FormType = 'matter' | 'fund' | 'contract' | 'seal_cert'

/** 附录 B：状态与颜色一一映射且全局唯一 */
export type DocumentStatus =
  | 'draft'
  | 'processing'
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'closed'
  | 'timeout'

/** 三态读写 + 终态（tech-design §5.4） */
export type ReadWriteState = 'draft' | 'approving' | 'supplement' | 'finished'

export interface WorkbenchQuery {
  tab: WorkbenchTab
  keyword?: string
  formTypes?: FormType[]
  statuses?: DocumentStatus[]
  /** 发起人 / 审批人 / 部门筛选 */
  initiatorId?: string
  orgId?: string
  dateFrom?: string
  dateTo?: string
  amountMin?: string
  amountMax?: string
  page: number
  pageSize: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface WorkbenchItem {
  instanceId: string
  taskId?: string
  bizNo: string
  title: string
  formType: FormType
  formTypeLabel: string
  status: DocumentStatus
  statusLabel: string
  initiatorId: string
  initiatorName: string
  initiatorEmployeeNo: string
  deptName: string
  companyName: string
  /** 金额：字符串定点数，禁止浮点（tech-design §5.4） */
  amount?: string | null
  amountCurrency?: string
  /** 当前节点序号 ①–⑦ */
  currentNodeNo?: number
  currentNodeName?: string
  createdAt: string
  submittedAt?: string
  dueAt?: string
  timeoutRisk?: boolean
  ccOnly?: boolean
  /** 协同审批分组进度，如 "3/4" */
  collaborationProgress?: string
}

export interface WorkbenchSummary {
  pending: number
  approved: number
  initiated: number
  cc: number
  timeoutRisk: number
}

export interface WorkbenchFilterOption {
  key: string
  label: string
  options: Array<{ value: string; label: string }>
}

export interface BatchApprovePayload {
  taskIds: string[]
  opinion: string
  /** 会签节点需签名 */
  signatureId?: string
}

export interface BatchTransferPayload {
  taskIds: string[]
  targetUserId: string
  reason: string
}

export interface TaskDecisionPayload {
  opinion: string
  /** 同意 / 驳回时的签名记录 id（oa.sign.record） */
  signatureId?: string
  /** 请求补件时的补件说明 */
  supplementNote?: string
  /** 待补件截止日期，默认 3 个工作日 */
  supplementDeadline?: string
  /** 流转目标部门 */
  toDeptId?: string
  /** 加签人 */
  addSignUserIds?: string[]
  addSignType?: 'pre' | 'post'
  /** 回退 / 流转原因（必填） */
  reason?: string
}

// ---------------------------------------------------------------------------
// 单据详情（oa.portal.detail）
// ---------------------------------------------------------------------------
export interface DetailHeader {
  instanceId: string
  bizNo: string
  title: string
  formType: FormType
  formTypeLabel: string
  status: DocumentStatus
  statusLabel: string
  readWriteState: ReadWriteState
  templateVersion: string
  initiatorName: string
  initiatorEmployeeNo: string
  deptName: string
  companyName: string
  createdAt: string
  /** 归口部门（恒财务部） */
  ownerDeptName: string
  amount?: string | null
  /** 是否是本人发起的单据（决定印鉴单 return_status 是否可改） */
  initiatedByMe: boolean
  /** 当前用户所处节点序号（⑦ archive_register = 7） */
  currentNodeNo?: number
}

/** 表单字段：form_schema_json 驱动的渲染单元（oa.form.template.schema） */
export interface FormField {
  fieldId: string
  label: string
  /** 控件类型：文本 / 多行 / 金额 / 日期 / 下拉 / 多选 / 复选 / 附件 / 人员 */
  control:
    | 'text'
    | 'textarea'
    | 'amount'
    | 'date'
    | 'select'
    | 'multiselect'
    | 'checkbox'
    | 'upload'
    | 'user'
  value: string | number | boolean | string[] | null
  required?: boolean
  readonly: boolean
  /** 服务端给出的只读原因，用于 tooltip（oa.authz.readonly-reasons） */
  readonlyReason?: string
  options?: Array<{ value: string; label: string }>
  maxLength?: number
  unit?: string
  /** 是否参与打印（print_visible） */
  printVisible?: boolean
}

export interface FormSection {
  sectionId: string
  title: string
  collapsible: boolean
  fields: FormField[]
}

export interface DetailForm {
  instanceId: string
  sections: FormSection[]
  /** 服务端白名单：当前用户可写字段（tech-design §5.4） */
  writableFieldIds: string[]
}

/** 审批轨迹节点四态（oa.portal.detail.trail） */
export type TrailNodeState = 'done' | 'current' | 'pending' | 'timeout' | 'skipped'

export interface TrailNode {
  nodeKey: string
  /** 编号 ①–⑦；发起者与结束不计入节点编号 */
  nodeNo?: number
  nodeName: string
  state: TrailNodeState
  approverName?: string
  approverPosition?: string
  decisionMode?: string
  action?: string
  opinion?: string
  signatureUrl?: string
  actedAt?: string
  /** 会签进度 / 并行协同进度，如 "3/4" */
  progress?: string
  parallelGroupId?: string
  parallelGroupTitle?: string
  timeoutAt?: string
}

export interface TrailResult {
  instanceId: string
  nodes: TrailNode[]
  /** 流转 + 回退累计，上限 5（routing_count） */
  routingCount: number
  routingLimit: number
  /** 同节点回退次数，≤2 */
  returnedCount: number
  /** 补件轮次，全单 ≤3 */
  supplementRound: number
  supplementLimit: number
}

export interface ActionAvailability {
  action: DetailAction
  label: string
  /** 是否可用；不可用不渲染（不可见优于不可用） */
  enabled: boolean
  reason?: string
  /** 危险操作需二次确认，且确认文案写明动作与对象 */
  danger?: boolean
}

/** 详情页可用动作（oa.portal.detail.actions） */
export type DetailAction =
  | 'approve'
  | 'reject'
  | 'route'
  | 'rollback'
  | 'return-to-department'
  | 'supplement'
  | 'terminate'
  | 'transfer'
  | 'add-sign'
  | 'withdraw'
  | 'print'

export interface SupplementState {
  instanceId: string
  pending: boolean
  round: number
  limit: number
  deadline?: string
  overdue?: boolean
  note?: string
  /** 待补件期唯一可写字段：附件 + 补件说明 */
  writableFieldIds: string[]
}

export interface AttachmentItem {
  attachmentId: string
  fileName: string
  fileSize: number
  ext: string
  round: number
  uploadedBy: string
  uploadedAt: string
  sha256?: string
  /** 私有化存储，下载必须走鉴权接口 */
  downloadUrl: string
}

/** 印鉴证照单归还信息：三态只读的唯一例外（tech-design §5.4） */
export interface SealReturnInfo {
  returnStatus: string | null
  returnStatusLabel?: string
  returnDate: string | null
  /** 发起人或节点⑦（archive_register）可改 */
  editable: boolean
  editableReason?: string
}

// ---------------------------------------------------------------------------
// 打印（oa.form.print / oa.design.print）
// ---------------------------------------------------------------------------
export interface PrintSignatureBlock {
  /** 签名栏在正式打印稿上一律为空栏，这里只保留栏位定义 */
  label: string
  /** 屏幕预览用的已签缩略图与时间戳；打印时被 print-a4.scss 隐藏 */
  previewSignatureUrl?: string
  previewSignedBy?: string
  previewSignedAt?: string
}

export interface PrintFieldValue {
  label: string
  value: string
  /** 标签列不填色，写值区 3–4% 浅灰 */
  span?: number
  align?: 'left' | 'center' | 'right'
}

export interface PrintRow {
  cells: PrintFieldValue[]
  group?: string
}

export interface PrintDocument {
  instanceId: string
  bizNo: string
  title: string
  subtitle?: string
  /** P1 集团合同 / P2 集团资金 / P3 子公司内部（事项单复用）/ P4 印鉴证照 */
  variant: 'group-contract' | 'group-fund' | 'subsidiary-internal' | 'seal-license'
  templateVersion: string
  generatedAt: string
  printerName: string
  rows: PrintRow[]
  routingBlocks: PrintRow[][]
  signatureBlocks: PrintSignatureBlock[]
  attachments: AttachmentItem[]
  /** 页脚三栏 */
  footer: {
    systemName: string
    documentName: string
    bizNo: string
    templateVersion: string
    generatedAt: string
    page: number
    totalPages: number
  }
}
