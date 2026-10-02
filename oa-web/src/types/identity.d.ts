/**
 * oa-web · 身份域 DTO（组织 / 人员 / 岗位 / 负责人）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/import-spec.md` §3（五个模板逐列说明与中文↔code 口径）、
 *     §4（校验规则编号）、§7.2（受影响在途单据清单字段）、§8（离职/停用拦截文案）
 *   · `doc/prd-0.1.md` 5.1（组织架构模型）、5.3（字段级限制）、5.4（审批人快照）、
 *     5.5（组织与人员变更处理）、AC-11（审批人空缺拦截）、AC-12（离职前待办清空）
 *   · 后端定稿契约 `/api/v1/identity/**`（路径已冻结，字段名以后端实现为准）
 *
 * 命名约定与 `src/types/api.d.ts` 一致：统一响应体为 `{ code, message, data, traceId }`，
 * 本文件只描述 `data` 部分；`api/http.ts` 负责解包。
 */
import type { FormType, PageResult } from './api'

/** 分页结果复用网关统一结构，避免与 api.d.ts 各写一份 */
export type { PageResult }

// ---------------------------------------------------------------------------
// 枚举（中文 ↔ code 口径逐条对齐 import-spec §3）
// ---------------------------------------------------------------------------
/** `org.csv.org_type`：集团/公司/部门/科室 → group/company/dept/section */
export type OrgType = 'group' | 'company' | 'dept' | 'section'
/** `org.csv.status`：启用/停用 → active/disabled */
export type OrgStatus = 'active' | 'disabled'
/** `user.csv.status`：在职/离职 → active/resigned；disabled 由后台单条停用（T-05） */
export type UserStatus = 'active' | 'resigned' | 'disabled'
/** `org_leader.csv.leader_type`：正职/副职 → primary/deputy */
export type LeaderType = 'primary' | 'deputy'
/** `org_leader.csv.business_line`：经营/经济/行政/人力/投资（事项类别五值，仅集团层可填） */
export type BusinessLine = 'business' | 'economy' | 'admin' | 'hr' | 'invest'

/** 影响程度：高（审批人失效需改派）/ 中（发起人归属变更）/ 低（仅历史留痕），import-spec §7.2 */
export type ImpactLevel = 'high' | 'medium' | 'low'

/** 受影响原因枚举文案，import-spec §7.2 */
export type ImpactReason =
  | 'org_disable'
  | 'org_move'
  | 'initiator_transfer'
  | 'initiator_resign'
  | 'approver_transfer'
  | 'approver_resign'
  | 'leader_change'

// ---------------------------------------------------------------------------
// 组织（oa.identity.org）
// ---------------------------------------------------------------------------
/** 组织节点最小投影：选择器 / 面包屑 / 祖先链共用 */
export interface OrgBrief {
  id: string
  name: string
  orgType: OrgType
  /** 父节点 id；集团根节点为 null（import-spec §3.2 parent_path 留空） */
  parentId: string | null
  /** 组织全路径，段间 `/`，不含首尾 `/`；组织的唯一业务键（T-01：无 org_code） */
  path: string
  /** 1 集团 / 2 公司 / 3 部门 / 4 科室（由 path 段数推导，import-spec §3.2） */
  depth: number
  status: OrgStatus
}

export interface OrgNode extends OrgBrief {
  remark?: string | null
  /** 同层排序号 */
  sortNo?: number
  /** 直接子节点数（树上懒加载时用于判断有无下级） */
  childCount?: number
  /** 该节点（不含子树）在职人数 */
  userCount?: number
  /**
   * 是否已设正职负责人：AC-11 / W-ORG-014 —— 部门/科室未设正职时，
   * 该部门成员发起审批会被服务端拦截，前端必须在树上与详情里可见地提示。
   * **权威来源**：树接口的 `OrgView.hasPrimaryLeader`（服务端按 `sys_org_leader`
   * 中 `leader_type='primary' AND category IS NULL` 全树一次批量查询聚合）。
   * 不再用 `leaderId` 推断（那只是 `sys_org` 的冗余列，业务线分管领导不算正职）。
   */
  hasPrimaryLeader?: boolean
  primaryLeaderNames?: string[]
  leaderCount?: number
  updatedAt?: string
}

/** 组织树节点：`GET /identity/orgs/tree` 递归结构 */
export interface OrgTreeNode extends OrgNode {
  children?: OrgTreeNode[]
}

export interface OrgSelectorQuery {
  keyword?: string
  orgType?: OrgType
  /** 默认不含停用节点：无数据权限的组织节点不可见（DESIGN.md cascader 规则） */
  includeDisabled?: boolean
  limit?: number
}

/** `POST /identity/orgs` 请求体（E-ORG-006 层级约束由前端预检 + 服务端强制） */
export interface OrgCreatePayload {
  name: string
  orgType: OrgType
  /** 新建根节点（集团）时为 null */
  parentId: string | null
  status?: OrgStatus
  remark?: string
}

/** `PUT /identity/orgs/{id}`：改名与备注（结构调整走 move） */
export interface OrgUpdatePayload {
  name?: string
  remark?: string
}

/** `POST /identity/orgs/{id}/move`：父节点变更触发 import-spec §7.1 场景 1 的影响清单 */
export interface OrgMovePayload {
  targetParentId: string
  reason: string
  /** 管理员强制继续（影响程度=高 且未确认时服务端默认阻断，§7.4） */
  force?: boolean
}

/** 停用 / 启用请求体：危险操作必须带原因（AC-52 留痕口径） */
export interface OrgStateChangePayload {
  reason: string
  force?: boolean
}

/** `GET /identity/orgs/{id}/path` */
export interface OrgPathResult {
  orgId: string
  path: string
  depth: number
  /** 从根到自身的祖先链（含自身） */
  segments: OrgBrief[]
}

/**
 * 受影响在途单据清单行（import-spec §7.2 八列）。
 *
 * 来源：`GET /orgs/{id}/in-flight-check` 与 `GET /users/{id}/in-flight-check` 的 `items`。
 * 组织侧一行给 `bizNo/formType/initiatorName/nodeName`；人员侧一行给
 * `instanceId/bizNo/formType/initiatorName/currentNodeName/status`。
 * 服务端未下发的列一律显示「—」，**绝不用推断值填充**（影响程度/受影响原因不在后端出参里，
 * 因此这两列常态为「—」，除非调用方有明确依据）。
 */
export interface ImpactItem {
  /** 在途实例 id（服务端 Long → 字符串） */
  instanceId?: string
  /** 单号（流程实例编号） */
  bizNo: string
  /** 单据类型 code；服务端给中文标签或未知 code 时走 `formTypeLabel` */
  formType?: FormType
  /** 服务端下发的类型原值；缺省时前端用 FORM_TYPE_LABEL 兜底 */
  formTypeLabel?: string
  initiatorName?: string
  initiatorAccount?: string
  /** 当前节点序号 ①–⑦（服务端未下发时为空） */
  currentNodeNo?: number
  currentNodeName?: string
  currentApproverName?: string
  /** 单据 / 任务状态 code（服务端原值，如 approving；界面按需展示） */
  status?: string
  impactReason?: ImpactReason
  impactLevel?: ImpactLevel
  /** 处理建议（如「由系统管理员改派（AC-52）」） */
  suggestion?: string
}

/**
 * `GET /identity/orgs/{id}/in-flight-check`：停用前的在途检查（E-ORG-010 / PRD 5.5）。
 * 新接口额外给出 `activeStaffCount`（子树在职人数）与 `items` 明细（单据类型/发起人/当前节点）。
 */
export interface InFlightCheck {
  orgId?: string
  /** 服务端判定是否阻断：在途数量或待办数量 > 0 → true */
  blocking: boolean
  /** 该节点及其子树下的在途单据数（服务端规范名 inFlightInstanceCount） */
  inFlightCount: number
  /** 待处理任务数（服务端规范名 pendingTaskCount） */
  pendingTasks?: number
  /** 明细清单（新接口下发单据类型/发起人/节点名；旧接口只回单号时其余列显示「—」） */
  items: ImpactItem[]
  /** 该子树下在职人数（W-ORG-017：停用后仍需先转岗，仅警告不阻断）；服务端字段 activeStaffCount */
  activeStaffCount: number
  /** 非阻断性提示（如 W-ORG-017 文案） */
  warnings: string[]
  /** 规范提示文案（import-spec §8.2），前端优先直接展示服务端文案 */
  message?: string
}

// ---------------------------------------------------------------------------
// 负责人（oa.identity.org-leader）
// ---------------------------------------------------------------------------
export interface OrgLeader {
  leaderId: string
  orgId: string
  orgPath?: string
  userId: string
  userName: string
  account: string
  employeeNo?: string
  /** 用户在组织内的职务名（sys_user.position 回填口径） */
  positionName?: string
  leaderType: LeaderType
  /** 排序号 0–9999（模板列 sort → 表列 sort_no） */
  sortNo: number
  /** 分管业务线：仅 org_type=集团 可填（E-LEAD-008） */
  businessLine?: BusinessLine | null
  dutyTitle?: string | null
  remark?: string | null
  /** 负责人不得为离职人员（E-LEAD-006） */
  userStatus?: UserStatus
  effectiveFrom?: string | null
  effectiveTo?: string | null
}

/** `POST /identity/orgs/{id}/leaders`：同一组织同一业务线只能有一个正职（E-LEAD-004） */
export interface OrgLeaderCreatePayload {
  userId: string
  leaderType: LeaderType
  sortNo: number
  businessLine?: BusinessLine | null
  remark?: string
}

export type OrgLeaderUpdatePayload = Partial<OrgLeaderCreatePayload>

/** `GET /identity/orgs/{id}/leader-candidates` */
export interface LeaderCandidate {
  userId: string
  name: string
  account: string
  employeeNo: string
  orgName: string
  positionName?: string
  status: UserStatus
  /** 已在当前组织任职的领导（前端置灰，避免重复绑定 E-LEAD-009） */
  boundLeaderType?: LeaderType | null
}

/** 集团层业务线分管领导绑定（PRD 5.1：集团层按业务线绑定分管领导） */
export interface LeaderLineBinding {
  category: BusinessLine
  /** 服务端可回传中文标签，缺省时前端用 BUSINESS_LINE_LABEL */
  categoryLabel?: string
  /** 绑定到哪个集团职能部门节点 */
  orgId?: string | null
  orgName?: string | null
  orgPath?: string | null
  leaderId?: string | null
  leaderName?: string | null
  leaderType?: LeaderType | null
  /** 「未设正职」警示（AC-11）：该业务线无正职分管领导 */
  hasPrimary?: boolean
  updatedAt?: string
}

/** `PUT /identity/leaders/lines/{category}`：替换语义（AC-52 留痕字段 reason/force 一并提交） */
export interface LeaderLineUpdatePayload {
  orgId: string
  userId: string
  leaderType: LeaderType
  reason: string
  /** 强制继续（仅在有在途阻断且调用人为系统管理员时使用） */
  force?: boolean
}

// ---------------------------------------------------------------------------
// 人员（oa.identity.user）
// ---------------------------------------------------------------------------
export interface UserItem {
  userId: string
  /** 登录名：8–64 位、字母开头（E-USER-001），库内唯一（E-USER-002） */
  account: string
  name: string
  /** 工号：≤32 位且唯一（E-USER-014 / E-USER-015），水印取「姓名 + 工号」（AC-44） */
  employeeNo: string
  /** 脱敏手机号（PRD 5.3：通讯录默认脱敏，仅本人与系统管理员可见完整值） */
  phoneMasked: string
  /** 服务端标记：phone 的值是否已被脱敏（后端 `UserView.phoneMasked` 是布尔标记） */
  phoneIsMasked?: boolean
  /** 完整手机号：仅系统管理员请求时可返回 */
  phone?: string
  email?: string
  companyId: string
  companyName: string
  /** 主归属部门/科室（dept_path → sys_user.org_id），可为空 */
  deptId?: string | null
  deptName?: string | null
  orgPath?: string
  /** 主岗职务名（sys_user.position，由主岗 post_name 回填） */
  positionName?: string
  status: UserStatus
  remark?: string | null
  /**
   * 名下未处理待办数（AC-12 离职拦截的依据）。
   * 服务端 `UserView.pendingTaskCount` 恒有值（无待办回 0，不返回 null），
   * 映射层 `?? 0` 兜底，因此这里是**必填 number**，列表列直接展示。
   * 精确明细（单据类型/发起人/节点）走 `GET /users/{id}/in-flight-check`。
   */
  pendingTaskCount: number
  /** 是否涉及在途单据（列表页据此提前给离职/调岗提示） */
  hasInFlight?: boolean
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface UserQuery {
  keyword?: string
  /** 组织节点：含子树（含子部门/科室） */
  orgId?: string
  includeSubOrg?: boolean
  status?: UserStatus
  page: number
  pageSize: number
}

/** `POST /identity/users` */
export interface UserCreatePayload {
  account: string
  employeeNo: string
  name: string
  phone: string
  email?: string
  /**
   * 所属公司路径节点 id（company_path → sys_user.company_id）。
   * ⚠ 服务端 `UserCreateRequest.companyId` 是 `@NotNull`，且类型必须是「公司」或「集团」
   *   （集团本部人员填集团节点，E-USER-005）：**必填**，由页面按所选部门向上取最近的
   *   公司/集团节点推导（`dept_path` 必须落在 company 子树内，E-USER-006 / E-USER-011）。
   */
  companyId: string
  /** 主归属组织（后端字段名 orgId） */
  deptId?: string | null
  /** 职务名（写入 sys_user.position；一般由主岗回填） */
  positionName?: string
  status: UserStatus
  remark?: string
}

/**
 * `PUT /identity/users/{id}`：姓名/联系方式/职务/备注为整体覆盖，工号与归属为条件更新。
 * `status` 只接受 `active`/`disabled`（服务端 `UserUpdateRequest.status`）；
 * 切到 `disabled` 与离职同在途/待办拦截口径（block-on-inflight），
 * `resigned` **只能**走 `POST /users/{id}/resign`。
 */
export interface UserUpdatePayload {
  name?: string
  employeeNo?: string
  phone?: string
  email?: string
  /** 归属公司；改主归属部门时必须与之一致（服务端只在非空时更新） */
  companyId?: string
  deptId?: string | null
  positionName?: string
  status?: UserStatus
  remark?: string
}

// ---------------------------------------------------------------------------
// 岗位（一人多岗，oa.identity.user-position）
// ---------------------------------------------------------------------------
export interface PositionItem {
  positionId: string
  userId: string
  orgId: string
  orgName: string
  orgPath?: string
  /** 岗位名称 ≤50（E-POS-006） */
  postName: string
  /** 主岗唯一（E-POS-005）；主岗 post_name 同时回填 sys_user.position */
  isPrimary: boolean
  remark?: string | null
  createdAt?: string
}

/** `POST /identity/users/{id}/positions`：(user, org) 不得重复（E-POS-004） */
export interface PositionCreatePayload {
  orgId: string
  postName: string
  isPrimary: boolean
  remark?: string
}

/** `PUT /identity/users/{id}/positions/{positionId}`：设为主岗 / 改岗位名（正式接口） */
export interface PositionUpdatePayload {
  /** 规范名；映射层按后端 `postName` 提交（旧别名 position 由后端兼容，不再使用） */
  postName?: string
  isPrimary?: boolean
  remark?: string
}

// ---------------------------------------------------------------------------
// 离职 / 调岗 / 交接（PRD 5.5 + AC-12）
// ---------------------------------------------------------------------------
/**
 * `POST /identity/users/{id}/resign`：名下待办 > 0 默认阻断（E-USER-009）。
 * 请求体只有 `{reason, force}`；`handoverToUserId` 是**前端编排字段**——
 * 提交前先调 `POST /users/{id}/handover` 把待办转办出去（AC-12 的放行条件），
 * 后端离职请求体里没有该字段。
 */
export interface ResignPayload {
  reason: string
  /** 交接接收人：先转办再离职是 AC-12 要求的放行条件（由前端先调 /handover） */
  handoverToUserId?: string
  /** 强制继续：reason 必填且调用人须为系统管理员，服务端据此放行在途/待办阻断并留痕 */
  force?: boolean
}

/**
 * `POST /identity/users/{id}/transfer`：跨公司/跨部门调动触发影响清单（§7.1 场景 3）。
 * 请求体为 `{targetOrgId, targetCompanyId, keepOtherPositions, reason, force}`；
 * `targetPositionName`/`isPrimary`/`handoverToUserId` 是**前端编排字段**：
 * 目标岗位由前端在调岗成功后调 `POST /users/{id}/positions` 落库，
 * 待办交接由前端先调 `POST /users/{id}/handover`。
 */
export interface TransferPayload {
  targetOrgId: string
  /** 目标公司；留空由服务端按目标组织最近的祖先公司推导 */
  targetCompanyId?: string | null
  targetPositionName?: string
  /** 调岗后是否设为主岗（原主岗自动置否） */
  isPrimary?: boolean
  /** 是否保留其它任职（兼岗）；false 时一并解除（后端 keepOtherPositions） */
  keepOtherPositions?: boolean
  reason: string
  /** 原主岗是否交由他人接手（先交接再调岗；由前端先调 /handover） */
  handoverToUserId?: string
  /** 强制继续：reason 必填且调用人须为系统管理员，服务端据此放行在途/待办阻断并留痕 */
  force?: boolean
}

/** `POST /identity/users/{id}/handover`：把名下待办转办给他人 */
export interface HandoverPayload {
  toUserId: string
  /** 留空 = 全部待办 */
  taskIds?: string[]
  reason: string
}

/**
 * 人员维度的在途/待办影响面（`GET /users/{id}/in-flight-check`）。
 * `name`/`account` 不由该接口返回，由调用方用列表行的值补齐（可选）。
 */
export interface UserImpact {
  userId: string
  name?: string
  account?: string
  /** 名下未处理待办数（flow_task.status='pending'） */
  pendingTaskCount: number
  /** 在途单据数（flow_instance 未完结态；服务端字段 inFlightInstanceCount） */
  inFlightInstanceCount: number
  items: ImpactItem[]
  /** 服务端规范提示文案（import-spec §8.1）；该接口不下发时为 undefined，由页面兜底 */
  message?: string
}

// ---------------------------------------------------------------------------
// 通讯录与「我是谁的负责人」
// ---------------------------------------------------------------------------
export interface DirectoryQuery {
  keyword?: string
  orgId?: string
  page: number
  pageSize: number
}

/** `GET /identity/directory`：全集团通讯录（手机号默认脱敏） */
export interface DirectoryItem {
  userId: string
  name: string
  employeeNo: string
  account: string
  orgName: string
  orgPath: string
  positionName?: string
  phone: string
  /** 服务端标记：phone 是否已脱敏 */
  phoneIsMasked?: boolean
}

/** `GET /identity/users/{id}/leader-of`：该用户负责的组织（一人可在多组织任负责人） */
export interface LeaderOfItem {
  orgId: string
  orgName: string
  orgPath: string
  leaderType: LeaderType
  businessLine?: BusinessLine | null
  sortNo: number
}
