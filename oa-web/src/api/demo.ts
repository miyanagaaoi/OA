/**
 * oa-web · 离线演示数据（阶段 1 骨架用）
 * ----------------------------------------------------------------------------
 * 用途：后端接口尚未就绪时，页面仍按 `DESIGN.md` / PRD 口径完整渲染，
 *       用于评审视觉、三态只读与打印版式。
 * 开关：`VITE_USE_MOCK=true`（开发环境默认开；生产环境必须为 false）。
 * 约束：本文件只提供**演示数据**，不承担任何业务规则——业务规则见
 *       `src/utils/readwrite.ts`（三态白名单）与 `src/utils/format.ts`。
 *       所有字段口径来自 `doc/forms.md` 与 `doc/prd-0.1.md` 6.2。
 */
import type {
  ActionAvailability,
  AttachmentItem,
  ClientConfig,
  CurrentUser,
  DetailForm,
  DetailHeader,
  FormField,
  FormSection,
  FormType,
  LockStatus,
  PasswordPolicy,
  PrintDocument,
  SealReturnInfo,
  SessionDevice,
  SupplementState,
  TrailResult,
  WatermarkProfile,
  WorkbenchFilterOption,
  WorkbenchItem,
  WorkbenchSummary,
} from '@/types/api'
import type { WireInFlightCheck, WireInFlightItem, WireUserInFlightCheck } from '@/types/identity-wire'

// ---------------------------------------------------------------------------
// 当前用户
// ---------------------------------------------------------------------------
export const demoCurrentUser: CurrentUser = {
  userId: 'U10086',
  name: '降泽宇',
  employeeNo: '10086',
  account: 'jiangzeyu',
  avatarText: '降',
  phoneMasked: '138****8888',
  email: 'jiangzeyu@example.com',
  orgId: 'ORG-GRP-FIN',
  orgName: '集团财务部',
  companyId: 'C-GRP',
  companyName: '集团有限公司',
  orgPath: '/集团/集团本部/财务部',
  positionName: '财务部主管',
  roles: [
    { roleId: 'R01', roleCode: 'FINANCE', roleName: '集团财务部', dataScope: 'group_category' },
    { roleId: 'R02', roleCode: 'APPROVER', roleName: '审批人', dataScope: 'dept' },
  ],
  dataScopes: ['group_category', 'dept', 'self'],
  categories: ['fund', 'contract', 'seal_cert'],
  permissions: [
    // 权限码**一律冒号风格**（权威源：`oa-deploy/sql/04-permissions.sql` 的 94 项种子
    // 与 `doc/data-model.md` 3.4 的示例 `flow:task:approve`）；点号码在这套种子里不存在。
    'portal:workbench:todo',
    'portal:workbench:done',
    'portal:workbench:mine',
    'portal:workbench:cc',
    'portal:detail:thread',
    'flow:task:approve',
    'flow:task:reject',
    'flow:task:route',
    'admin:audit:operation',
    // 阶段 1 · 1.1：管理后台（组织架构 / 人员管理）演示入口。
    // 演示账号被授予身份域管理权限，便于在无后端时打开 /admin/orgs 与 /admin/users；
    // 但**不**授予 `admin:user:export` —— import-spec §9.2（T-11 定稿）规定
    // 主数据导出**仅系统管理员**，导出入口应保持不可见；如需验证导出，
    // 请把本对象的 isSuperAdmin 置为 true（或由后端给系统管理员角色）。
    // 阶段 1 · 1.4：同时给 `admin:role:list` 与 `admin:role:grant`，便于无后端时验证
    // 「角色与权限」页与权限树逐级勾选；**故意不给** `admin:authz:scope`、
    // `admin:user:export`、`admin:audit:permission`，便于验证「无权限入口不渲染」。
    'admin:org:tree',
    'admin:org:leader',
    'admin:org:position',
    'admin:user:profile',
    'admin:user:handover',
    'admin:role:list',
    'admin:role:grant',
    'admin:authz:assign',
  ],
  signaturePresetReady: true,
  watermarkEnabled: true,
  watermarkOpacity: 0.06,
  isSuperAdmin: false,
}

export const demoLockStatus: LockStatus = {
  account: '',
  locked: false,
  failedAttempts: 0,
  maxAttempts: 5,
  lockRemainingSeconds: 0,
}

export const demoPasswordPolicy: PasswordPolicy = {
  minLength: 8,
  requireLetter: true,
  requireDigit: true,
  requireSpecial: false,
  maxAttempts: 5,
  lockMinutes: 15,
}

/**
 * 演示用客户端运行期配置（`VITE_USE_MOCK=true` 且接口不可用时使用）。
 *
 * ⚠ 字段形状必须与服务端 `GET /api/v1/auth/client-config` 的领域模型一致（见
 * `types/api.d.ts` 的 `ClientConfig`）；标题/环境/API 前缀优先取构建期环境变量
 * （`.env.*` 的 `VITE_APP_TITLE` / `VITE_APP_ENV` / `VITE_API_BASE_URL`），
 * 保证「演示模式」与「真实后端」显示同一套口径。
 */
export const demoClientConfig: ClientConfig = {
  title: import.meta.env.VITE_APP_TITLE || '集团OA审批系统',
  env: import.meta.env.VITE_APP_ENV || 'development',
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  sessionCookieName: import.meta.env.VITE_SESSION_COOKIE_NAME || 'OA_SESSION',
  forceHttps: import.meta.env.VITE_FORCE_HTTPS === 'true',
  watermarkOpacity: 0.06,
  session: { maxDevices: 3, rememberMeDays: 7 },
  password: {
    minLength: 8,
    requireLetter: true,
    requireDigit: true,
    lockThreshold: 5,
    lockMinutes: 15,
  },
  maxDevices: 3,
  rememberMeDays: 7,
  upload: {
    maxFileSizeMb: 50,
    maxFilesPerSubmit: 20,
    maxFilesPerDocument: 50,
    allowedExtensions: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'jpeg', 'png', 'heic'],
  },
}

export const demoSessions: SessionDevice[] = [
  {
    sessionId: 'S-1',
    deviceFingerprint: 'fp-win-chrome',
    deviceLabel: 'Windows · Chrome',
    ip: '10.12.3.41',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0',
    loginAt: '2026-10-02T08:31:00+08:00',
    lastActiveAt: '2026-10-02T14:02:00+08:00',
    expiresAt: '2026-10-09T08:31:00+08:00',
    current: true,
  },
  {
    sessionId: 'S-2',
    deviceFingerprint: 'fp-iphone-safari',
    deviceLabel: 'iPhone · Safari（H5）',
    ip: '10.12.9.77',
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Safari/604.1',
    loginAt: '2026-10-01T19:12:00+08:00',
    lastActiveAt: '2026-10-02T09:40:00+08:00',
    expiresAt: '2026-10-08T19:12:00+08:00',
    current: false,
  },
]

/** 水印：姓名 + 工号，透明度 6%（区间 5%–8%）、旋转 -24°、间距 240×160（REQ-USER-004 / AC-44） */
export const demoWatermarkProfile: WatermarkProfile = {
  text: `${demoCurrentUser.name} ${demoCurrentUser.employeeNo}`,
  name: demoCurrentUser.name,
  employeeNo: demoCurrentUser.employeeNo,
  opacity: 0.06,
  opacityMin: 0.05,
  opacityMax: 0.08,
  rotate: -24,
  gapX: 240,
  gapY: 160,
  enabled: true,
}

// ---------------------------------------------------------------------------
// 工作台
// ---------------------------------------------------------------------------
export const demoWorkbenchSummary: WorkbenchSummary = {
  pending: 12,
  approved: 148,
  initiated: 26,
  cc: 9,
  timeoutRisk: 2,
}

export const demoWorkbenchFilters: WorkbenchFilterOption[] = [
  {
    key: 'formType',
    label: '单据类型',
    options: [
      { value: 'matter', label: '事项审批单' },
      { value: 'fund', label: '资金审批单' },
      { value: 'contract', label: '合同审批单' },
      { value: 'seal_cert', label: '印鉴证照审批单' },
    ],
  },
  {
    key: 'status',
    label: '单据状态',
    options: [
      { value: 'pending', label: '待我审批' },
      { value: 'processing', label: '审批中' },
      { value: 'approved', label: '已通过' },
      { value: 'rejected', label: '已驳回' },
      { value: 'closed', label: '已撤回 / 已关闭' },
      { value: 'draft', label: '草稿' },
    ],
  },
]

export const demoWorkbenchItems: WorkbenchItem[] = [
  {
    instanceId: 'I-2026-000123',
    taskId: 'T-2026-000123-2',
    bizNo: 'OA-2026-000123',
    title: '关于采购一批生产设备的资金申请',
    formType: 'fund',
    formTypeLabel: '资金审批单',
    status: 'pending',
    statusLabel: '待我审批',
    initiatorId: 'U10998',
    initiatorName: '李海涛',
    initiatorEmployeeNo: '10998',
    deptName: '生产运营部',
    companyName: '某某实业有限公司',
    amount: '1250000.00',
    amountCurrency: 'CNY',
    currentNodeNo: 2,
    currentNodeName: '集团财务部复核',
    createdAt: '2026-10-01T09:12:00+08:00',
    submittedAt: '2026-10-01T09:20:00+08:00',
    dueAt: '2026-10-03T09:20:00+08:00',
    timeoutRisk: true,
  },
  {
    instanceId: 'I-2026-000124',
    taskId: 'T-2026-000124-3',
    bizNo: 'OA-2026-000124',
    title: '某某科技大厦幕墙工程合同审批',
    formType: 'contract',
    formTypeLabel: '合同审批单',
    status: 'pending',
    statusLabel: '待我审批',
    initiatorId: 'U10771',
    initiatorName: '王雪',
    initiatorEmployeeNo: '10771',
    deptName: '工程管理部',
    companyName: '某某建设有限公司',
    amount: '8600000.00',
    amountCurrency: 'CNY',
    currentNodeNo: 2,
    currentNodeName: '集团财务部复核',
    createdAt: '2026-09-30T15:40:00+08:00',
    submittedAt: '2026-09-30T15:52:00+08:00',
    dueAt: '2026-10-02T15:52:00+08:00',
  },
  {
    instanceId: 'I-2026-000125',
    taskId: 'T-2026-000125-1',
    bizNo: 'OA-2026-000125',
    title: '关于厂区消防隐患整改的事项申请',
    formType: 'matter',
    formTypeLabel: '事项审批单',
    status: 'pending',
    statusLabel: '待我审批',
    initiatorId: 'U10612',
    initiatorName: '赵启明',
    initiatorEmployeeNo: '10612',
    deptName: '安全环保部',
    companyName: '某某实业有限公司',
    amount: null,
    currentNodeNo: 1,
    currentNodeName: '部门负责人审核',
    createdAt: '2026-10-02T08:05:00+08:00',
    submittedAt: '2026-10-02T08:10:00+08:00',
    dueAt: '2026-10-04T08:10:00+08:00',
  },
  {
    instanceId: 'I-2026-000126',
    taskId: 'T-2026-000126-4',
    bizNo: 'OA-2026-000126',
    title: '借用集团公章办理工商变更',
    formType: 'seal_cert',
    formTypeLabel: '印鉴证照审批单',
    status: 'pending',
    statusLabel: '待我审批',
    initiatorId: 'U10833',
    initiatorName: '陈静',
    initiatorEmployeeNo: '10833',
    deptName: '综合办公室',
    companyName: '集团有限公司',
    amount: null,
    currentNodeNo: 4,
    currentNodeName: '集团分管领导审批',
    createdAt: '2026-09-29T10:22:00+08:00',
    submittedAt: '2026-09-29T10:30:00+08:00',
    dueAt: '2026-10-01T10:30:00+08:00',
    timeoutRisk: true,
  },
  {
    instanceId: 'I-2026-000127',
    taskId: 'T-2026-000127-5',
    bizNo: 'OA-2026-000127',
    title: '2026 年第四季度办公用品采购款支付',
    formType: 'fund',
    formTypeLabel: '资金审批单',
    status: 'pending',
    statusLabel: '待我审批',
    initiatorId: 'U10504',
    initiatorName: '孙博',
    initiatorEmployeeNo: '10504',
    deptName: '行政管理部',
    companyName: '集团有限公司',
    amount: '98200.00',
    amountCurrency: 'CNY',
    currentNodeNo: 2,
    currentNodeName: '集团财务部复核',
    createdAt: '2026-10-02T09:02:00+08:00',
    submittedAt: '2026-10-02T09:08:00+08:00',
    dueAt: '2026-10-04T09:08:00+08:00',
  },
  {
    instanceId: 'I-2026-000118',
    taskId: 'T-2026-000118-2',
    bizNo: 'OA-2026-000118',
    title: '厂房屋面防水维修工程合同',
    formType: 'contract',
    formTypeLabel: '合同审批单',
    status: 'processing',
    statusLabel: '审批中',
    initiatorId: 'U10771',
    initiatorName: '王雪',
    initiatorEmployeeNo: '10771',
    deptName: '工程管理部',
    companyName: '某某建设有限公司',
    amount: '468000.00',
    amountCurrency: 'CNY',
    currentNodeNo: 5,
    currentNodeName: '子公司总经理审批',
    createdAt: '2026-09-26T13:30:00+08:00',
    submittedAt: '2026-09-26T13:44:00+08:00',
    collaborationProgress: '3/4',
  },
  {
    instanceId: 'I-2026-000109',
    taskId: 'T-2026-000109-1',
    bizNo: 'OA-2026-000109',
    title: '关于 2027 年度培训计划的立项事项',
    formType: 'matter',
    formTypeLabel: '事项审批单',
    status: 'approved',
    statusLabel: '已通过',
    initiatorId: 'U10220',
    initiatorName: '刘倩',
    initiatorEmployeeNo: '10220',
    deptName: '人力资源部',
    companyName: '集团有限公司',
    amount: null,
    currentNodeNo: 7,
    currentNodeName: '登记归档',
    createdAt: '2026-09-20T08:40:00+08:00',
    submittedAt: '2026-09-20T08:52:00+08:00',
  },
  {
    instanceId: 'I-2026-000101',
    taskId: 'T-2026-000101-3',
    bizNo: 'OA-2026-000101',
    title: '关于临时借用营业执照副本的事项',
    formType: 'seal_cert',
    formTypeLabel: '印鉴证照审批单',
    status: 'rejected',
    statusLabel: '已驳回',
    initiatorId: 'U10833',
    initiatorName: '陈静',
    initiatorEmployeeNo: '10833',
    deptName: '综合办公室',
    companyName: '集团有限公司',
    amount: null,
    currentNodeNo: 3,
    currentNodeName: '集团财务部会签',
    createdAt: '2026-09-15T16:10:00+08:00',
    submittedAt: '2026-09-15T16:24:00+08:00',
  },
  {
    instanceId: 'I-2026-000098',
    taskId: 'T-2026-000098-6',
    bizNo: 'OA-2026-000098',
    title: '办公设备租赁费用支付申请',
    formType: 'fund',
    formTypeLabel: '资金审批单',
    status: 'closed',
    statusLabel: '已撤回',
    initiatorId: 'U10504',
    initiatorName: '孙博',
    initiatorEmployeeNo: '10504',
    deptName: '行政管理部',
    companyName: '集团有限公司',
    amount: '36000.00',
    amountCurrency: 'CNY',
    currentNodeNo: 2,
    currentNodeName: '集团财务部复核',
    createdAt: '2026-09-12T11:00:00+08:00',
    submittedAt: '2026-09-12T11:12:00+08:00',
  },
  {
    instanceId: 'I-2026-000130',
    bizNo: 'OA-2026-000130',
    title: '关于技术中心研发设备采购的事项申请',
    formType: 'matter',
    formTypeLabel: '事项审批单',
    status: 'draft',
    statusLabel: '草稿',
    initiatorId: 'U10086',
    initiatorName: '降泽宇',
    initiatorEmployeeNo: '10086',
    deptName: '集团财务部',
    companyName: '集团有限公司',
    amount: null,
    createdAt: '2026-10-02T10:15:00+08:00',
  },
  {
    instanceId: 'I-2026-000131',
    bizNo: 'OA-2026-000131',
    title: '关于集团档案室消防改造的事项申请',
    formType: 'matter',
    formTypeLabel: '事项审批单',
    status: 'processing',
    statusLabel: '审批中',
    initiatorId: 'U10086',
    initiatorName: '降泽宇',
    initiatorEmployeeNo: '10086',
    deptName: '集团财务部',
    companyName: '集团有限公司',
    amount: null,
    currentNodeNo: 3,
    currentNodeName: '集团财务部会签',
    createdAt: '2026-10-01T17:20:00+08:00',
    submittedAt: '2026-10-01T17:31:00+08:00',
  },
  {
    instanceId: 'I-2026-000132',
    taskId: 'T-2026-000132-2',
    bizNo: 'OA-2026-000132',
    title: '关于某某产业园区合作框架协议的合同审批',
    formType: 'contract',
    formTypeLabel: '合同审批单',
    status: 'pending',
    statusLabel: '待我审批',
    initiatorId: 'U10998',
    initiatorName: '李海涛',
    initiatorEmployeeNo: '10998',
    deptName: '战略投资部',
    companyName: '集团有限公司',
    amount: '30000000.00',
    amountCurrency: 'CNY',
    currentNodeNo: 2,
    currentNodeName: '集团财务部复核',
    createdAt: '2026-10-02T11:40:00+08:00',
    submittedAt: '2026-10-02T11:52:00+08:00',
    dueAt: '2026-10-04T11:52:00+08:00',
    ccOnly: true,
  },
]

// ---------------------------------------------------------------------------
// 表单字段（字段口径来自 doc/forms.md：事项 / 资金 / 合同 / 印鉴证照）
// ---------------------------------------------------------------------------
function field(
  fieldId: string,
  label: string,
  control: FormField['control'],
  value: FormField['value'],
  extra: Partial<FormField> = {},
): FormField {
  return {
    fieldId,
    label,
    control,
    value,
    required: false,
    readonly: false,
    printVisible: true,
    ...extra,
  }
}

const matterSections: FormSection[] = [
  {
    sectionId: 'basic',
    title: '审批详情',
    collapsible: true,
    fields: [
      field('matter_title', '事项标题', 'text', '关于厂区消防隐患整改的事项申请', { required: true, maxLength: 100 }),
      field('matter_category', '事项分类', 'select', 'safety', {
        required: true,
        options: [
          { value: 'production', label: '生产经营' },
          { value: 'safety', label: '安全环保' },
          { value: 'admin', label: '行政后勤' },
          { value: 'invest', label: '投资' },
        ],
      }),
      field('matter_desc', '事项描述', 'textarea', '厂区 3 号库房消防喷淋管道老化，需整体更换并补充灭火器 40 具。', {
        required: true,
        maxLength: 500,
      }),
      field('involve_cost', '是否涉及费用', 'checkbox', true, { required: true }),
      field('cost_amount', '涉及金额', 'amount', '186000.00', { unit: 'CNY' }),
      field('cost_bearer', '费用承担主体', 'select', '某某实业有限公司', {
        options: [
          { value: '某某实业有限公司', label: '某某实业有限公司' },
          { value: '集团有限公司', label: '集团有限公司' },
        ],
      }),
      field('expect_date', '期望完成日期', 'date', '2026-11-30'),
      field('cc_users', '抄送人', 'user', ['王雪', '陈静']),
    ],
  },
  {
    sectionId: 'attachments',
    title: '附件',
    collapsible: true,
    fields: [
      field('attachments', '附件', 'upload', null),
      field('supplement_note', '补件说明', 'textarea', ''),
    ],
  },
]

const fundSections: FormSection[] = [
  {
    sectionId: 'basic',
    title: '基本信息',
    collapsible: true,
    fields: [
      field('fund_title', '申请事由', 'text', '关于采购一批生产设备的资金申请', { required: true, maxLength: 100 }),
      field('fund_amount', '申请金额', 'amount', '1250000.00', { required: true, unit: 'CNY' }),
      field('plan_category', '计划类别', 'checkbox', true),
      field('payment_belong', '付款归属', 'checkbox', false),
    ],
  },
  {
    sectionId: 'payee',
    title: '收款方信息',
    collapsible: true,
    fields: [
      field('payee_name', '收款单位', 'text', '某某装备制造有限公司', { required: true }),
      field('payee_account', '收款账号', 'text', '6222 **** **** 1234', { required: true }),
      field('payee_bank', '开户银行', 'text', '中国银行某某支行'),
    ],
  },
  {
    sectionId: 'payment',
    title: '付款信息',
    collapsible: true,
    fields: [
      field('pay_method', '付款方式', 'select', 'transfer', {
        required: true,
        options: [
          { value: 'transfer', label: '银行转账' },
          { value: 'draft', label: '银行汇票' },
          { value: 'cash', label: '现金' },
        ],
      }),
      field('expect_pay_date', '期望付款日期', 'date', '2026-10-20', { required: true }),
      field('pay_remark', '付款说明', 'textarea', '按合同约定支付预付款 30%，到货验收后付 60%，质保金 10%。'),
    ],
  },
  {
    sectionId: 'attachments',
    title: '附件',
    collapsible: true,
    fields: [
      field('attachments', '附件', 'upload', null),
      field('supplement_note', '补件说明', 'textarea', ''),
    ],
  },
]

const contractSections: FormSection[] = [
  {
    sectionId: 'basic',
    title: '合同基本信息',
    collapsible: true,
    fields: [
      field('contract_title', '合同名称', 'text', '某某科技大厦幕墙工程合同', { required: true, maxLength: 100 }),
      field('contract_type', '合同类型', 'select', 'construction', {
        required: true,
        options: [
          { value: 'construction', label: '工程类' },
          { value: 'purchase', label: '采购类' },
          { value: 'service', label: '服务类' },
        ],
      }),
      field('is_framework', '是否框架合同', 'checkbox', false),
      field('contract_amount', '合同金额', 'amount', '8600000.00', { required: true, unit: 'CNY' }),
      field('other_review_depts', '其他会审部门', 'multiselect', ['经发部', '财务部'], {
        options: [
          { value: '经发部', label: '经发部' },
          { value: '财务部', label: '财务部' },
          { value: '集团办', label: '集团办' },
          { value: '法务部', label: '法务部' },
        ],
      }),
    ],
  },
  {
    sectionId: 'period',
    title: '合同有效期',
    collapsible: true,
    fields: [
      field('period_start', '生效日期', 'date', '2026-11-01', { required: true }),
      field('period_end', '到期日期', 'date', '2028-10-31', { required: true }),
    ],
  },
  {
    sectionId: 'counterparty',
    title: '对方信息',
    collapsible: true,
    fields: [
      field('counterparty_name', '对方单位名称', 'text', '某某幕墙工程有限公司', { required: true }),
      field('counterparty_credit', '统一社会信用代码', 'text', '91310000MA1FL***XX', { required: true }),
      field('counterparty_contact', '联系人', 'text', '周工'),
      field('counterparty_phone', '联系电话', 'text', '139****2233'),
    ],
  },
  {
    sectionId: 'attachments',
    title: '附件',
    collapsible: true,
    fields: [
      field('attachments', '附件', 'upload', null),
      field('supplement_note', '补件说明', 'textarea', ''),
    ],
  },
]

const sealSections: FormSection[] = [
  {
    sectionId: 'basic',
    title: '用印 / 证照基本信息',
    collapsible: true,
    fields: [
      field('seal_title', '申请事由', 'text', '借用集团公章办理工商变更', { required: true }),
      field('seal_type', '用印类型', 'select', 'company_seal', {
        required: true,
        options: [
          { value: 'company_seal', label: '公司公章' },
          { value: 'contract_seal', label: '合同专用章' },
          { value: 'finance_seal', label: '财务专用章' },
        ],
      }),
      field('seal_count', '用印份数', 'text', '3', { required: true }),
      field('purpose', '用途说明', 'textarea', '办理全资子公司法定代表人变更登记，需在工商材料上加盖公章三处。', {
        required: true,
        maxLength: 200,
      }),
    ],
  },
  {
    sectionId: 'cert',
    title: '证照信息',
    collapsible: true,
    fields: [
      field('cert_name', '证照名称', 'select', 'business_license', {
        options: [
          { value: 'business_license', label: '营业执照' },
          { value: 'org_code', label: '组织机构代码证' },
          { value: 'tax_reg', label: '税务登记证' },
        ],
      }),
      field('usage_period', '使用期限', 'date', '2026-10-15'),
      field('borrow_mode', '是否外带', 'checkbox', true),
    ],
  },
  {
    sectionId: 'return',
    title: '归还信息（三态只读的唯一例外）',
    collapsible: true,
    fields: [
      field('return_status', '归还状态', 'select', 'borrowed', {
        options: [
          { value: 'borrowed', label: '借出未还' },
          { value: 'returned', label: '已归还' },
          { value: 'partial', label: '部分归还' },
        ],
      }),
      field('return_date', '归还日期', 'date', null),
    ],
  },
  {
    sectionId: 'attachments',
    title: '附件',
    collapsible: true,
    fields: [
      field('attachments', '附件', 'upload', null),
      field('supplement_note', '补件说明', 'textarea', ''),
    ],
  },
]

export const demoFormSectionsByType: Record<FormType, FormSection[]> = {
  matter: matterSections,
  fund: fundSections,
  contract: contractSections,
  seal_cert: sealSections,
}

/** 深拷贝一份表单，避免演示态切换时互相污染 */
export function buildDemoForm(formType: FormType): DetailForm {
  const sections = JSON.parse(JSON.stringify(demoFormSectionsByType[formType])) as FormSection[]
  return {
    instanceId: '',
    sections,
    writableFieldIds: [],
  }
}

export const demoDetailForm: DetailForm = buildDemoForm('matter')

export const demoDetailHeader: DetailHeader = {
  instanceId: '',
  bizNo: 'OA-2026-000123',
  title: '关于采购一批生产设备的资金申请',
  formType: 'fund',
  formTypeLabel: '资金审批单',
  status: 'pending',
  statusLabel: '待我审批',
  readWriteState: 'approving',
  templateVersion: 'v1.3',
  initiatorName: '李海涛',
  initiatorEmployeeNo: '10998',
  deptName: '生产运营部',
  companyName: '某某实业有限公司',
  createdAt: '2026-10-01T09:12:00+08:00',
  ownerDeptName: '集团财务部',
  amount: '1250000.00',
  initiatedByMe: false,
  currentNodeNo: 2,
}

export const demoActionAvailability: ActionAvailability[] = [
  { action: 'approve', label: '同意', enabled: true },
  { action: 'reject', label: '驳回', enabled: true, danger: true },
  { action: 'add-sign', label: '加签', enabled: true },
  { action: 'route', label: '流转', enabled: true },
  { action: 'rollback', label: '回退上一节点', enabled: false, reason: '本节点已是首个审批节点' },
  { action: 'return-to-department', label: '回到本部门', enabled: false, reason: '本单据尚未发生过流转' },
  { action: 'supplement', label: '请求补件', enabled: true },
  { action: 'terminate', label: '终止', enabled: false, reason: '仅系统管理员与集团分管领导可终止' },
  { action: 'transfer', label: '转办', enabled: true },
  { action: 'print', label: '打印', enabled: true },
]

export const demoTrail: TrailResult = {
  instanceId: '',
  routingCount: 0,
  routingLimit: 5,
  returnedCount: 0,
  supplementRound: 0,
  supplementLimit: 3,
  nodes: [
    {
      nodeKey: 'initiate',
      nodeName: '发起',
      state: 'done',
      approverName: '李海涛',
      approverPosition: '生产运营部 · 主管',
      action: '提交',
      opinion: '按年度设备更新计划发起，附件含三家比价单。',
      actedAt: '2026-10-01T09:20:00+08:00',
      signatureUrl: '',
    },
    {
      nodeKey: 'dept_leader',
      nodeNo: 1,
      nodeName: '① 部门负责人审核',
      state: 'done',
      approverName: '赵启明',
      approverPosition: '生产运营部 · 负责人',
      decisionMode: '或签',
      action: '同意',
      opinion: '设备确已到报废年限，同意上报。',
      actedAt: '2026-10-01T10:05:00+08:00',
    },
    {
      nodeKey: 'finance_review',
      nodeNo: 2,
      nodeName: '② 集团财务部复核',
      state: 'current',
      approverName: '降泽宇',
      approverPosition: '集团财务部 · 主管',
      decisionMode: '或签',
      timeoutAt: '2026-10-03T09:20:00+08:00',
      progress: undefined,
    },
    {
      nodeKey: 'collaboration',
      nodeNo: 3,
      nodeName: '③ 协同部门会签',
      state: 'pending',
      parallelGroupId: 'pg-1',
      parallelGroupTitle: '协同审批 · 3 个部门',
      progress: '1/3',
    },
    {
      nodeKey: 'group_exec',
      nodeNo: 4,
      nodeName: '④ 集团分管领导审批',
      state: 'pending',
    },
    {
      nodeKey: 'company_gm',
      nodeNo: 5,
      nodeName: '⑤ 子公司总经理审批',
      state: 'pending',
    },
    {
      nodeKey: 'chairman',
      nodeNo: 6,
      nodeName: '⑥ 集团董事长审批',
      state: 'pending',
    },
    {
      nodeKey: 'archive_register',
      nodeNo: 7,
      nodeName: '⑦ 登记归档',
      state: 'pending',
    },
  ],
}

export const demoAttachments: AttachmentItem[] = [
  {
    attachmentId: 'A-1',
    fileName: '设备采购比价单.pdf',
    fileSize: 512_000,
    ext: 'pdf',
    round: 0,
    uploadedBy: '李海涛',
    uploadedAt: '2026-10-01T09:18:00+08:00',
    sha256: '9f2c…a71b',
    downloadUrl: '/api/v1/attachments/A-1/download',
  },
  {
    attachmentId: 'A-2',
    fileName: '设备清单.xlsx',
    fileSize: 88_400,
    ext: 'xlsx',
    round: 0,
    uploadedBy: '李海涛',
    uploadedAt: '2026-10-01T09:19:00+08:00',
    sha256: '3b81…ce04',
    downloadUrl: '/api/v1/attachments/A-2/download',
  },
]

export const demoSupplementState: SupplementState = {
  instanceId: '',
  pending: false,
  round: 0,
  limit: 3,
  writableFieldIds: ['attachments', 'supplement_note'],
}

/** 印鉴单归还信息：三态只读的唯一例外（发起人与节点⑦可改） */
export const demoSealReturnInfo: SealReturnInfo = {
  returnStatus: 'borrowed',
  returnStatusLabel: '借出未还',
  returnDate: null,
  editable: false,
  editableReason: '仅发起人与节点⑦（登记归档）可修改归还状态与归还日期',
}

// ---------------------------------------------------------------------------
// 身份域管理后台：在途/待办影响清单与主数据导出（阶段 1 骨架演示）
// ---------------------------------------------------------------------------
/**
 * 影响清单明细行（人员侧与组织侧共用结构）。
 * 只回服务端**真实会下发**的列（单据类型 / 发起人 / 节点名 / 状态）；
 * 「影响程度 / 受影响原因」不在后端出参里，界面显示「—」，演示数据也不造。
 */
const demoInFlightItems: WireInFlightItem[] = [
  {
    instanceId: '9001',
    bizNo: 'OA-2026-000118',
    formType: 'matter',
    initiatorName: '李海涛',
    nodeName: '部门负责人',
    currentNodeName: '部门负责人',
    status: 'approving',
  },
  {
    instanceId: '9002',
    bizNo: 'OA-2026-000121',
    formType: 'fund',
    initiatorName: '王晓敏',
    nodeName: '财务部审核',
    currentNodeName: '财务部审核',
    status: 'approving',
  },
]

/** 组织侧在途检查：`GET /orgs/{id}/in-flight-check` 的演示回落（`VITE_USE_MOCK=true` 时） */
export const demoOrgInFlightCheck: WireInFlightCheck = {
  blocked: true,
  rejected: true,
  blockOnInflight: true,
  // 旧字段名与规范名同值（服务端两个都下发）
  inFlightInstances: 2,
  pendingTasks: 1,
  total: 3,
  bizNos: ['OA-2026-000118', 'OA-2026-000121'],
  message:
    '组织停用前必须清空在途单据：集团本部/财务部 及其子树下仍有 2 张在途单据（另有 1 条待处理待办），请先办结或流转处理；涉及单号：OA-2026-000118、OA-2026-000121',
  inFlightInstanceCount: 2,
  pendingTaskCount: 1,
  activeStaffCount: 6,
  items: demoInFlightItems,
}

/** 人员侧在途检查：`GET /users/{id}/in-flight-check` 的演示回落（`VITE_USE_MOCK=true` 时） */
export const demoUserInFlightCheck: WireUserInFlightCheck = {
  pendingTaskCount: 2,
  inFlightInstanceCount: 2,
  items: demoInFlightItems,
}

/**
 * 人员主数据导出 CSV（import-spec §9.1 九列 + UTF-8 BOM）。
 * 仅在开发态（`VITE_USE_MOCK=true`）接口不可用时回落，用于查看导出交互；
 * 生产（`VITE_USE_MOCK=false`）必须严格抛出，不允许静默给出假数据。
 */
export const demoUserExportCsv =
  '\ufeffaccount,employee_no,name,phone,email,company_path,dept_path,status,remark\n' +
  'jiangzeyu,10086,降泽宇,13800008888,jiangzeyu@example.com,集团有限公司,集团有限公司/集团本部/财务部,在职,演示数据\n'

/** 服务端导出的缺省文件名（`Content-Disposition` 缺失时的回落值） */
export const demoUserExportFilename = 'user.csv'

// ---------------------------------------------------------------------------
// 打印（A4）：正式打印稿签名栏一律空栏
// ---------------------------------------------------------------------------
export const demoPrintDocument: PrintDocument = {
  instanceId: '',
  bizNo: 'OA-2026-000123',
  title: '资金申请',
  subtitle: '子公司内部审批单版式（P3）',
  variant: 'subsidiary-internal',
  templateVersion: 'v1.3',
  generatedAt: '2026-10-02T14:20:00+08:00',
  printerName: '降泽宇',
  rows: [
    {
      cells: [
        { label: '申请人', value: '李海涛' },
        { label: '申请时间', value: '2026-10-01 09:20' },
      ],
    },
    {
      cells: [
        { label: '所属部门', value: '生产运营部' },
        { label: '审批状态', value: '审批中' },
      ],
    },
    { group: '审批详情', cells: [] },
    {
      cells: [
        { label: '申请事由', value: '关于采购一批生产设备的资金申请', span: 3 },
      ],
    },
    {
      cells: [
        { label: '申请金额', value: '1,250,000.00', align: 'right' },
        { label: '费用承担主体', value: '某某实业有限公司' },
      ],
    },
    {
      cells: [
        { label: '计划类别', value: '☑ 计划内   ☐ 计划外' },
        { label: '付款归属', value: '☐ 本月度   ☑ 次月度' },
      ],
    },
    { group: '收款方信息', cells: [] },
    {
      cells: [
        { label: '收款单位', value: '某某装备制造有限公司' },
        { label: '开户银行', value: '中国银行某某支行' },
      ],
    },
    {
      cells: [
        { label: '收款账号', value: '6222 **** **** 1234', span: 3 },
      ],
    },
    { group: '审批记录', cells: [] },
  ],
  routingBlocks: [],
  signatureBlocks: [
    { label: '集团职能部门' },
    { label: '集团分管领导' },
    { label: '集团董事长' },
  ],
  attachments: demoAttachments,
  footer: {
    systemName: '集团OA审批系统',
    documentName: '资金审批单',
    bizNo: 'OA-2026-000123',
    templateVersion: 'v1.3',
    generatedAt: '2026-10-02 14:20',
    page: 1,
    totalPages: 1,
  },
}
