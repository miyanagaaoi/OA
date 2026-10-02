/**
 * oa-web · 后端 identity 域 DTO 镜像（wire 层）
 * ----------------------------------------------------------------------------
 * 逐字段对齐 `oa-server` 的 `com.oa.identity.api.dto.*`（OrgDtos / UserDtos /
 * LeaderDtos / PositionDtos / DirectoryDtos）与 `com.oa.common.api.PageResult`。
 *
 * 为什么要有这一层：
 *   · 后端并行实现时只冻结了**路径**，字段名与类型由后端定稿；本文件是那些字段的
 *     忠实镜像，`api/org.ts` / `api/user.ts` 负责把它映射成前端领域模型
 *     （`types/identity.d.ts`）。**后端字段再变只改映射层，页面与领域模型不受影响。**
 *   · 映射层同时承担三件后端不做的事：
 *       1. `Long`/`long` → **JSON 字符串**（`com.oa.common.config.JacksonConfig` 对
 *          `Long.class`/`Long.TYPE`/`BigInteger` 注册了 `ToStringSerializer`），
 *          前端统一 `String(...)` 成 id、`Number(...)` 成数值；
 *       2. `PageResult{records,size}` → `PageResult{list,pageSize}`；
 *       3. 枚举 code 收窄到前端联合类型（`orgType`/`status`/`leaderType`/`category`）。
 *
 * ⚠ 本文件字段名与**类型**以 oa-server 现有实现为准；后端改 DTO 时必须同步这里。
 *
 * ⚠⚠ 分页字段也是字符串：`PageResult.total/page/size/pages` 在 Java 侧是 `long`，
 *      因此 JSON 里是 `"20"` 而不是 `20`。映射层必须 `Number(...)` 后再进领域模型
 *      （领域模型 `PageResult.total/page/pageSize` 保持 number，页面不做二次转换）。
 *      只有 `Integer`/`int` 才是 JSON number（如 `pendingTaskCount`、`sortNo`）。
 */

/** 后端 `Long` 主键 / `long` 计数：Jackson 下是字符串，兼容旧配置仍是 number 的情况 */
export type WireId = number | string

/** 后端 `Long`/`long` 数值（分页字段、计数）：字符串为准，number 仅作兼容 */
export type WireNumber = number | string

/** `com.oa.common.api.PageResult` 的真实形状（注意是 records + size，不是 list + pageSize） */
export interface WirePageResult<T> {
  records?: T[] | null
  /** `long` → JSON 字符串 */
  total?: WireNumber | null
  /** `long` → JSON 字符串 */
  page?: WireNumber | null
  /** `long` → JSON 字符串 */
  size?: WireNumber | null
  /** `long` → JSON 字符串 */
  pages?: WireNumber | null
}

// ---------------------------------------------------------------------------
// 组织（OrgDtos）
// ---------------------------------------------------------------------------
export interface WireOrgView {
  id: WireId
  parentId?: WireId | null
  orgType: string
  orgTypeLabel?: string | null
  name: string
  path: string
  businessPath?: string | null
  depth?: number | null
  /** sys_org.leader_id 的**冗余**值（权威在 sys_org_leader）；树接口不展开姓名，避免 N+1 */
  leaderId?: WireId | null
  sortNo?: number | null
  status: string
  statusLabel?: string | null
  remark?: string | null
  /** 树接口非空；平铺接口（search/ancestors/descendants）为 null */
  children?: WireOrgView[] | null
  /**
   * 是否已设正职负责人（AC-11 / W-ORG-014）——**权威判定**：
   * 服务端按 `sys_org_leader` 中 `leader_type='primary' AND category IS NULL` 是否存在聚合，
   * 全树一次批量查询（无 N+1）。`leaderId` 只是 `sys_org.leader_id` 的冗余列，
   * **不可**用来推断本字段（业务线分管领导不算正职）。
   * Java 侧是基本类型 `boolean`，出参恒有值。
   */
  hasPrimaryLeader?: boolean | null
}

/** 选择器节点：树形结构，`value/label` 是给 el-tree-select 直接用的 */
export interface WireOrgOption {
  value: WireId
  label: string
  orgType?: string | null
  orgTypeLabel?: string | null
  path: string
  depth?: number | null
  disabled?: boolean
  children?: WireOrgOption[] | null
}

export interface WireOrgPathSegment {
  id: WireId
  name: string
  orgType?: string | null
  orgTypeLabel?: string | null
}

export interface WireOrgPathView {
  id: WireId
  path: string
  businessPath?: string | null
  depth?: number | null
  segments?: WireOrgPathSegment[] | null
}

export interface WireMoveItem {
  id: WireId
  oldPath: string
  newPath: string
  depth?: number | null
}

export interface WireMoveResult {
  id: WireId
  newParentId?: WireId | null
  oldPath: string
  newPath: string
  newDepth?: number | null
  movedNodes?: number
  items?: WireMoveItem[] | null
}

/**
 * 影响清单单行（人员侧与组织侧共用同一结构，`InFlightDtos.InFlightItemView`）。
 * 字段为空时服务端按 `spring.jackson.default-property-inclusion=non_null` 省略：
 *   · 组织侧一行通常只有 `bizNo/formType/initiatorName/nodeName`；
 *   · 人员侧一行含 `instanceId/currentNodeName/status`。
 */
export interface WireInFlightItem {
  /** `Long` → JSON 字符串 */
  instanceId?: WireId | null
  bizNo: string
  /** 单据类型（`flow_instance.form_type`，code 或中文标签，由服务端给出） */
  formType?: string | null
  /** 发起人姓名 */
  initiatorName?: string | null
  /** 所在 / 待处理节点名（组织侧口径） */
  nodeName?: string | null
  /** 当前节点名（人员侧口径） */
  currentNodeName?: string | null
  /** 单据 / 任务状态 code */
  status?: string | null
}

/**
 * 停用前的在途检查（`GET /orgs/{id}/in-flight-check`）。
 *
 * 旧字段（`inFlightInstances/pendingTasks/total/bizNos`）**全部保留**，
 * 新增同值规范名 `inFlightInstanceCount/pendingTaskCount`、子树在职人数 `activeStaffCount`
 * 与明细行 `items`。数量字段在 Java 侧是 `int`，JSON 里是 number（不是字符串）。
 */
export interface WireInFlightCheck {
  blocked: boolean
  /** 服务端是否直接拒绝（blocked 且 blockOnInflight 时的语义） */
  rejected?: boolean
  blockOnInflight?: boolean
  /** 旧字段名：在途单据数 */
  inFlightInstances?: number
  /** 旧字段名：待处理待办数 */
  pendingTasks?: number
  total?: number
  bizNos?: string[] | null
  message?: string | null
  /** 规范名：在途单据数（与 inFlightInstances 同值） */
  inFlightInstanceCount?: number
  /** 规范名：待处理待办数（与 pendingTasks 同值） */
  pendingTaskCount?: number
  /** 该节点**子树**下的在职人数（W-ORG-017） */
  activeStaffCount?: number
  /** 影响清单明细（无在途/待办时为空数组，服务端不返回 null） */
  items?: WireInFlightItem[] | null
}

/**
 * 人员影响清单（`GET /users/{id}/in-flight-check`）——离职 / 调岗 / 停用前的二次确认。
 * 比 `GET /users/{id}/pending-tasks` 更全：给出在途单据数与明细行
 * （单据类型 / 发起人 / 当前节点 / 状态）。计数是 `int`，JSON 里是 number。
 */
export interface WireUserInFlightCheck {
  pendingTaskCount: number
  inFlightInstanceCount: number
  items?: WireInFlightItem[] | null
}

/** 启用 / 停用结果（含 warnings 与影响面） */
export interface WireStateResult {
  id: WireId
  status: string
  statusLabel?: string | null
  warnings?: string[] | null
  impact?: WireInFlightCheck | null
}

/** `POST /orgs` 请求体（字段名与后端一致：name/orgType/parentId/sortNo/status/remark） */
export interface WireOrgCreateRequest {
  name: string
  orgType: string
  parentId?: WireId | null
  sortNo?: number
  status?: string
  remark?: string
}

/** `PUT /orgs/{id}` 请求体：改名 / 排序 / 备注（类型不可改，需换类型走 move） */
export interface WireOrgUpdateRequest {
  name?: string
  sortNo?: number
  remark?: string
}

/**
 * `POST /orgs/{id}/move` 请求体：`newParentId` 之外还有 AC-52 的留痕字段
 * `reason`/`force`（`force=true` 时 reason 必填且调用人须为系统管理员）。
 */
export interface WireOrgMoveRequest {
  newParentId?: WireId | null
  reason?: string
  force?: boolean
}

/**
 * 危险操作的可选请求体（`{reason?, force?}`）——共用同一结构
 * （`OrgDtos.StateChangeRequest`）：
 *   · `POST /orgs/{id}/disable`、`POST /orgs/{id}/enable`
 *   · `DELETE /orgs/{id}/leaders/{leaderId}`
 * 旧调用不带 body 仍然可用；`force=true` 时 `reason` 必填（否则 400）且须系统管理员（否则 403）。
 */
export interface WireStateChangeRequest {
  reason?: string
  force?: boolean
}

// ---------------------------------------------------------------------------
// 负责人（LeaderDtos）
// ---------------------------------------------------------------------------
export interface WireLeaderView {
  id: WireId
  orgId: WireId
  orgPath?: string | null
  orgName?: string | null
  userId: WireId
  userName: string
  account: string
  employeeNo?: string | null
  leaderType: string
  leaderTypeLabel?: string | null
  dutyTitle?: string | null
  /** 分管业务线 = 事项类别五值 code（business/economy/admin/hr/invest） */
  category?: string | null
  categoryLabel?: string | null
  sortNo?: number | null
  remark?: string | null
  userStatus?: string | null
}

export interface WireLeaderCreateRequest {
  userId: WireId
  leaderType: string
  dutyTitle?: string
  category?: string
  sortNo?: number
  remark?: string
  effectiveFrom?: string
  effectiveTo?: string
}

export type WireLeaderUpdateRequest = Partial<Omit<WireLeaderCreateRequest, 'userId'>>

export interface WireLeaderCandidateView {
  userId: WireId
  name: string
  account: string
  employeeNo?: string | null
  userStatus?: string | null
  orgId?: WireId | null
  position?: string | null
  primaryPosition?: boolean
  /** 是否已是该组织负责人 */
  leader?: boolean
  leaderId?: WireId | null
  leaderType?: string | null
  leaderTypeLabel?: string | null
  category?: string | null
  categoryLabel?: string | null
}

/** 集团层业务线绑定：一个业务线可能挂多名领导（`leaders` 数组） */
export interface WireLeaderLineView {
  category: string
  categoryLabel?: string | null
  orgId?: WireId | null
  orgName?: string | null
  configured?: boolean
  leaders?: WireLeaderView[] | null
}

export interface WireLeaderLineUpsertRequest {
  orgId: WireId
  userId: WireId
  leaderType: string
  dutyTitle?: string
  sortNo?: number
  remark?: string
  /** AC-52 留痕（替换语义属高影响变更）：force=true 时必填且须系统管理员 */
  reason?: string
  force?: boolean
}

export interface WireLeaderOfView {
  orgId: WireId
  orgPath?: string | null
  orgName?: string | null
  orgType?: string | null
  leaderType: string
  leaderTypeLabel?: string | null
  category?: string | null
  categoryLabel?: string | null
  sortNo?: number | null
  dutyTitle?: string | null
}

// ---------------------------------------------------------------------------
// 人员（UserDtos）
// ---------------------------------------------------------------------------
export interface WireUserView {
  id: WireId
  account: string
  name: string
  employeeNo: string
  /** 手机号；`phoneMasked=true` 时该值已脱敏 */
  phone?: string | null
  phoneMasked?: boolean
  email?: string | null
  orgId?: WireId | null
  orgName?: string | null
  orgPath?: string | null
  companyId?: WireId | null
  companyName?: string | null
  /** 职务名（主岗 position 回填） */
  position?: string | null
  status: string
  statusLabel?: string | null
  remark?: string | null
  lastLoginAt?: string | null
  /**
   * 名下待处理待办数（`flow_task.status='pending'`）——列表页直接展示，
   * 服务端**回 0 而不是 null**（`Integer`，JSON number，不是字符串）。
   */
  pendingTaskCount?: number | null
}

export interface WireUserCreatedView {
  user: WireUserView
  /** 随机初始口令（仅创建时返回一次，线下分发；首登强制改密） */
  initialPassword?: string
  note?: string
}

export interface WireUserCreateRequest {
  account: string
  name: string
  employeeNo: string
  phone: string
  email?: string
  companyId?: WireId | null
  orgId?: WireId | null
  position?: string
  status?: string
  remark?: string
}

/**
 * `PUT /users/{id}`：name/phone/email/position/remark 为**整体覆盖**；employeeNo/companyId/orgId 为条件更新；
 * `status` 只接受 `active`/`disabled`（`null` = 不变更）；`resigned` 只能走 `POST /users/{id}/resign`。
 */
export interface WireUserUpdateRequest {
  name?: string
  employeeNo?: string
  phone?: string
  email?: string
  companyId?: WireId | null
  orgId?: WireId | null
  position?: string
  remark?: string
  /** active / disabled（中文「在职/停用」亦可）；切 disabled 时服务端同在途拦截口径 */
  status?: string
}

/** `POST /users/{id}/resign`：只有 reason 与 force（**没有** handoverToUserId，先转办须另调 /handover） */
export interface WireResignRequest {
  reason?: string
  force?: boolean
}

export interface WireTransferRequest {
  targetOrgId: WireId
  targetCompanyId?: WireId | null
  /** false 时一并解除其它任职（兼岗） */
  keepOtherPositions?: boolean
  reason?: string
  force?: boolean
}

export interface WireHandoverRequest {
  toUserId: WireId
  reason?: string
  transferTasks?: boolean
}

export interface WirePendingTaskView {
  taskId: WireId
  bizNo: string
  nodeName: string
  createdAt?: string | null
}

export interface WireResignResult {
  userId: WireId
  status: string
  statusLabel?: string | null
  pendingTasks?: number
  inFlightInstances?: number
  bizNos?: string[] | null
  leaderOf?: WireLeaderOfView[] | null
  message?: string | null
}

export interface WireTransferResult {
  userId: WireId
  oldOrgId?: WireId | null
  newOrgId?: WireId | null
  oldCompanyId?: WireId | null
  newCompanyId?: WireId | null
  primaryPositionId?: WireId | null
  removedPositionIds?: WireId[] | null
  note?: string | null
}

export interface WireHandoverResult {
  fromUserId: WireId
  toUserId: WireId
  taskCount?: number
  transferred?: number
  pendingTasks?: WirePendingTaskView[] | null
  note?: string | null
}

// ---------------------------------------------------------------------------
// 岗位（PositionDtos）
// ---------------------------------------------------------------------------
export interface WirePositionView {
  id: WireId
  userId: WireId
  orgId: WireId
  orgName: string
  orgPath?: string | null
  orgType?: string | null
  orgTypeLabel?: string | null
  /** 岗位名称（对应模板列 post_name） */
  position: string
  isPrimary: boolean
  remark?: string | null
  createdAt?: string | null
}

export interface WirePositionCreateRequest {
  orgId: WireId
  position: string
  isPrimary?: boolean
  remark?: string
}

/**
 * `PUT /users/{id}/positions/{positionId}`：字段全部可选，`null` = 不变更。
 *   · `postName` 是**规范名**，`position` 是同一字段的旧别名（两者同时出现以 `postName` 为准）；
 *   · `isPrimary=true` 设为主岗（同事务把旧主岗置 0 并回填 `sys_user.org_id/position`）；
 *   · `isPrimary=false` 置为副岗：若是唯一主岗则 409，否则把剩余岗位中 id 最小的一条提升为主岗。
 */
export interface WirePositionUpdateRequest {
  postName?: string
  position?: string
  isPrimary?: boolean
  remark?: string
}

// ---------------------------------------------------------------------------
// 通讯录与水印（DirectoryDtos）
// ---------------------------------------------------------------------------
export interface WireDirectoryUser {
  id: WireId
  name: string
  account: string
  employeeNo: string
  phone?: string | null
  phoneMasked?: boolean
  position?: string | null
  email?: string | null
  orgName?: string | null
}

export interface WireDirectoryGroup {
  orgId: WireId
  orgName: string
  orgPath?: string | null
  orgType?: string | null
  orgTypeLabel?: string | null
  depth?: number | null
  userCount?: number
  users?: WireDirectoryUser[] | null
}
