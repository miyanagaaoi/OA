/**
 * oa-web · 人员、岗位与通讯录接口（oa.identity.user / oa.identity.user-position）
 * ----------------------------------------------------------------------------
 * 路径与 `oa-server` 的 `identity/api/UserController.java` / `DirectoryController.java`
 * 逐条对齐；响应字段以后端 DTO 为准，本文件把 `types/identity-wire.d.ts` 映射为
 * 前端领域模型 `types/identity.d.ts`。
 *
 *   GET    /api/v1/identity/users                          人员列表（keyword/orgId/includeSubOrg/status/companyId/page/**size**）
 *   POST   /api/v1/identity/users                          新增人员（返回 UserCreatedView，含一次性初始口令）
 *   PUT    /api/v1/identity/users/{id}                     修改人员（**含 status：active/disabled**）
 *   GET    /api/v1/identity/users/{id}/positions           岗位列表（一人多岗）
 *   POST   /api/v1/identity/users/{id}/positions           新增岗位
 *   PUT    /api/v1/identity/users/{id}/positions/{pid}     设为主岗 / 改岗位名（**正式接口，不必先删后建**）
 *   DELETE /api/v1/identity/users/{id}/positions/{pid}     移除岗位
 *   POST   /api/v1/identity/users/{id}/resign              离职（待办非零 → 阻断，body 可带 reason/force）
 *   POST   /api/v1/identity/users/{id}/transfer            调岗（body 可带 reason/force）
 *   POST   /api/v1/identity/users/{id}/handover            交接（转办待办）
 *   GET    /api/v1/identity/users/{id}/pending-tasks       名下未处理待办（最小口径）
 *   GET    /api/v1/identity/users/{id}/in-flight-check     影响清单（待办数 + 在途数 + 明细行）
 *   GET    /api/v1/identity/users/export                   人员 CSV 导出（UTF-8 BOM，**仅系统管理员**）
 *   GET    /api/v1/identity/directory                      通讯录（按组织分组的树，非分页）
 *   GET    /api/v1/identity/users/{id}/leader-of           该用户负责的组织
 *   GET    /api/v1/identity/users/me/watermark             本人水印文本（已在 auth.ts 实现）
 *
 * ⚠ 前端编排（后端请求体里没有这些字段，必须由前端串起来）：
 *   1) `resign`/`transfer` 的请求体只有 `{reason, force}`，**没有** `handoverToUserId`：
 *      「先转办再离职/调岗」必须先调 `POST /users/{id}/handover`（AC-12 的放行条件）。
 *   2) `transfer` 的请求体**没有** `targetPositionName/isPrimary`：
 *      目标岗位在调岗成功后由前端调 `POST /users/{id}/positions` 落库。
 *
 * 口径来源：`doc/import-spec.md` §3.3 / §3.5 / §4.3 / §4.5 / §7 / §8.1 / §9；
 *           `doc/prd-0.1.md` 5.1 / 5.3 / 5.5、AC-12。
 */
import http, { USE_MOCK, del, get, post, put, withDemoFallback, type OaRequestConfig } from './http'
import { demoUserExportCsv, demoUserExportFilename, demoUserInFlightCheck } from './demo'
import type {
  DirectoryItem,
  DirectoryQuery,
  HandoverPayload,
  ImpactItem,
  LeaderOfItem,
  PageResult,
  PositionCreatePayload,
  PositionItem,
  PositionUpdatePayload,
  ResignPayload,
  TransferPayload,
  UserCreatePayload,
  UserImpact,
  UserItem,
  UserQuery,
  UserStatus,
  UserUpdatePayload,
} from '@/types/identity'
import type {
  WireDirectoryGroup,
  WireHandoverResult,
  WireId,
  WireInFlightItem,
  WireLeaderOfView,
  WireNumber,
  WirePageResult,
  WirePendingTaskView,
  WirePositionUpdateRequest,
  WirePositionView,
  WireResignResult,
  WireTransferResult,
  WireUserCreatedView,
  WireUserInFlightCheck,
  WireUserView,
} from '@/types/identity-wire'

// ---------------------------------------------------------------------------
// 映射工具
// ---------------------------------------------------------------------------
function sid(value: WireId | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

function sidOrNull(value: WireId | null | undefined): string | null {
  return value === null || value === undefined ? null : String(value)
}

function toUserStatus(value: string | null | undefined): UserStatus {
  switch (value) {
    case 'resigned':
      return 'resigned'
    case 'disabled':
      return 'disabled'
    default:
      return 'active'
  }
}

/**
 * 分页计数：后端 `PageResult.total/page/size/pages` 在 Java 侧是 `long`，
 * Jackson 序列化成**字符串**（`"20"`）。领域模型保持 number，统一在这里 `Number(...)`，
 * 页面不再做二次转换。非法/缺失值退回 fallback（缺省页大小用调用方请求值）。
 */
function toCount(value: WireNumber | null | undefined, fallback: number): number {
  if (value === null || value === undefined || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** 后端 `PageResult{records,total,page,size}` → 前端 `PageResult{list,total,page,pageSize}` */
function toPage<T, R>(
  wire: WirePageResult<T> | null | undefined,
  map: (row: T) => R,
  fallbackSize: number,
): PageResult<R> {
  return {
    list: (wire?.records ?? []).map(map),
    total: toCount(wire?.total, 0),
    page: toCount(wire?.page, 1),
    pageSize: toCount(wire?.size, fallbackSize),
  }
}

function toUserItem(view: WireUserView): UserItem {
  return {
    userId: sid(view.id),
    account: view.account,
    name: view.name,
    employeeNo: view.employeeNo,
    /*
     * 后端 `phoneMasked` 是**布尔标记**：true 表示 phone 已经是脱敏值。
     * 领域模型把两者收敛成「展示用字符串 + 是否脱敏」两个字段，页面直接展示。
     */
    phone: view.phone ?? undefined,
    phoneMasked: view.phone ?? '',
    phoneIsMasked: view.phoneMasked ?? false,
    email: view.email ?? undefined,
    companyId: sid(view.companyId),
    companyName: view.companyName ?? '',
    deptId: sidOrNull(view.orgId),
    deptName: view.orgName ?? null,
    orgPath: view.orgPath ?? undefined,
    positionName: view.position ?? undefined,
    status: toUserStatus(view.status),
    remark: view.remark ?? null,
    lastLoginAt: view.lastLoginAt ?? undefined,
    // 服务端恒回 0（不是 null）：列表列直接展示数字，无需空值分支
    pendingTaskCount: view.pendingTaskCount ?? 0,
  }
}

function toPosition(view: WirePositionView): PositionItem {
  return {
    positionId: sid(view.id),
    userId: sid(view.userId),
    orgId: sid(view.orgId),
    orgName: view.orgName,
    orgPath: view.orgPath ?? undefined,
    postName: view.position,
    isPrimary: view.isPrimary,
    remark: view.remark ?? null,
    createdAt: view.createdAt ?? undefined,
  }
}

/**
 * 单据类型：后端给 `flow_instance.form_type` 原值（code 或中文标签）。
 * 命中四类 code 收窄为 FormType；其余原样交给 `formTypeLabel`；都没有则显示「—」。
 */
function toFormType(raw: string | null | undefined): { formType?: ImpactItem['formType']; label?: string } {
  switch (raw) {
    case 'matter':
    case 'fund':
    case 'contract':
    case 'seal_cert':
      return { formType: raw }
    default: {
      const text = (raw ?? '').trim()
      return text ? { label: text } : {}
    }
  }
}

/** 在途/待办明细行（人员侧：单据类型 / 发起人 / 当前节点 / 状态） */
function toImpactItem(item: WireInFlightItem): ImpactItem {
  const type = toFormType(item.formType)
  return {
    instanceId: item.instanceId === null || item.instanceId === undefined ? undefined : String(item.instanceId),
    bizNo: item.bizNo,
    formType: type.formType,
    formTypeLabel: type.label,
    initiatorName: item.initiatorName ?? undefined,
    currentNodeName: item.currentNodeName ?? item.nodeName ?? undefined,
    status: item.status ?? undefined,
    // 影响程度/受影响原因不在出参里：界面显示「—」，不做推断
  }
}

/** 待办清单（`/pending-tasks`）的兜底映射：只有单号与节点名 */
function toPendingImpact(task: WirePendingTaskView): ImpactItem {
  return {
    bizNo: task.bizNo,
    currentNodeName: task.nodeName,
  }
}

// ---------------------------------------------------------------------------
// 人员
// ---------------------------------------------------------------------------
/** 人员列表：分页 + 关键字（姓名/工号/账号）+ 组织（含子树）+ 状态筛选 */
export function queryUsers(query: UserQuery): Promise<PageResult<UserItem>> {
  return get<WirePageResult<WireUserView>>('/identity/users', {
    params: {
      keyword: query.keyword,
      orgId: query.orgId,
      includeSubOrg: query.includeSubOrg ?? true,
      status: query.status,
      page: query.page,
      // ⚠ 后端参数名是 size（不是 pageSize）
      size: query.pageSize,
    },
  }).then((wire) => toPage(wire, toUserItem, query.pageSize))
}

/** 新增人员：后端返回 `UserCreatedView`（含一次性初始口令，线下分发、首登强制改密） */
export function createUser(payload: UserCreatePayload): Promise<UserItem> {
  return post<WireUserCreatedView>('/identity/users', {
    account: payload.account,
    name: payload.name,
    employeeNo: payload.employeeNo,
    phone: payload.phone,
    email: payload.email,
    // 服务端 @NotNull：必须是「公司」或「集团」节点（E-USER-005），由页面推导后传入
    companyId: payload.companyId,
    // 后端字段是 orgId（主归属组织）
    orgId: payload.deptId ?? null,
    position: payload.positionName,
    status: payload.status,
    remark: payload.remark,
  }).then((created) => toUserItem(created.user))
}

/**
 * 修改人员：账号与工号的唯一性由服务端裁决（E-USER-002 / E-USER-015）。
 * `status` 只提交 active/disabled：切 disabled 时服务端复用「在途/待办阻断」口径
 * （与离职同一判定），可能返回 409 —— 此时应先转办或改派。`resigned` 请走 `resignUser`。
 */
export function updateUser(userId: string, payload: UserUpdatePayload): Promise<UserItem> {
  return put<WireUserView>(`/identity/users/${userId}`, {
    name: payload.name,
    employeeNo: payload.employeeNo,
    phone: payload.phone,
    email: payload.email,
    // 归属公司：服务端仅在非空时更新，缺省（undefined）表示不变更
    companyId: payload.companyId,
    orgId: payload.deptId ?? undefined,
    position: payload.positionName,
    remark: payload.remark,
    // 离职不走本接口（服务端对 status='resigned' 回 400）
    status: payload.status === 'resigned' ? undefined : payload.status,
  }).then(toUserItem)
}

// ---------------------------------------------------------------------------
// 岗位（一人多岗，主岗唯一 E-POS-005）
// ---------------------------------------------------------------------------
export function fetchUserPositions(userId: string): Promise<PositionItem[]> {
  return get<WirePositionView[]>(`/identity/users/${userId}/positions`).then((views) =>
    (views ?? []).map(toPosition),
  )
}

/** 新增岗位：(user, org) 不得重复（E-POS-004）；设为主岗时服务端保证原主岗自动置否 */
export function createUserPosition(userId: string, payload: PositionCreatePayload): Promise<PositionItem> {
  return post<WirePositionView>(`/identity/users/${userId}/positions`, {
    orgId: payload.orgId,
    position: payload.postName,
    isPrimary: payload.isPrimary,
    remark: payload.remark,
  }).then(toPosition)
}

/**
 * 移除岗位：主岗被移除后服务端会把剩余岗位中 id 最小的一条提升为主岗
 * （避免「有岗位但无主岗」）；唯一主岗置副岗的请求会返回 409。
 * ⚠ 后端 `DELETE /users/{id}/positions/{positionId}` **未声明 @RequestBody**：
 *   `reason` 不会落库（审计只记路径参数与操作人），此处按约定保留参数以便后端补齐。
 */
export function deleteUserPosition(
  userId: string,
  positionId: string,
  payload: { reason: string } = { reason: '' },
): Promise<void> {
  return del<void>(`/identity/users/${userId}/positions/${positionId}`, { data: payload })
}

/**
 * 设为主岗 / 修改岗位名 / 改备注：`PUT /users/{id}/positions/{positionId}`（正式接口）。
 * 请求体用**规范名** `postName`；`isPrimary=true` 时服务端在同一事务内把该人旧主岗置 0，
 * 并回填 `sys_user.org_id/position`（import-spec §2.2 第 ④ 步 / T-12）。
 * 因此「设为主岗」**不需要**先删后建。
 */
export function updateUserPosition(
  userId: string,
  positionId: string,
  payload: PositionUpdatePayload,
): Promise<PositionItem> {
  const body: WirePositionUpdateRequest = {
    postName: payload.postName,
    isPrimary: payload.isPrimary,
    remark: payload.remark,
  }
  return put<WirePositionView>(`/identity/users/${userId}/positions/${positionId}`, body).then(toPosition)
}

// ---------------------------------------------------------------------------
// 离职 / 调岗 / 交接（PRD 5.5 + AC-12）
// ---------------------------------------------------------------------------
/**
 * 离职：名下待办 > 0 时服务端按 `block-on-inflight` 拒绝（409，E-USER-009 / AC-12）。
 * 请求体 `{reason, force}`：`force=true` + 非空原因 + 系统管理员 → **真正放行**在途/待办阻断，
 * 原因与操作人写入审计日志（AC-52）。
 * ⚠ 请求体没有 `handoverToUserId`：要「先转办再离职」必须调用方先 `handoverUser`。
 */
export function resignUser(userId: string, payload: ResignPayload): Promise<WireResignResult> {
  return post<WireResignResult>(`/identity/users/${userId}/resign`, {
    reason: payload.reason,
    force: payload.force ?? false,
  })
}

/**
 * 调岗：`keepOtherPositions=false` 时一并解除兼岗（后端语义）；
 * `reason`/`force` 口径同离职（force=true 时 reason 必填且须系统管理员）。
 * ⚠ 请求体没有目标岗位字段：`targetPositionName/isPrimary` 由调用方在成功后调
 *   `createUserPosition` 落库；`handoverToUserId` 由调用方先调 `handoverUser`。
 */
export function transferUser(userId: string, payload: TransferPayload): Promise<WireTransferResult> {
  return post<WireTransferResult>(`/identity/users/${userId}/transfer`, {
    targetOrgId: payload.targetOrgId,
    targetCompanyId: payload.targetCompanyId ?? null,
    keepOtherPositions: payload.keepOtherPositions ?? true,
    reason: payload.reason,
    force: payload.force ?? false,
  })
}

/** 交接：把名下未处理待办转办给他人 */
export function handoverUser(userId: string, payload: HandoverPayload): Promise<WireHandoverResult> {
  return post<WireHandoverResult>(`/identity/users/${userId}/handover`, {
    toUserId: payload.toUserId,
    reason: payload.reason,
    transferTasks: true,
  })
}

/**
 * 名下未处理待办（最小口径：`{taskId,bizNo,nodeName,createdAt}`）。
 * 离职/调岗的影响清单请用 `fetchUserImpact`（信息更全）。
 */
export function fetchUserPendingTasks(userId: string): Promise<ImpactItem[]> {
  return get<WirePendingTaskView[]>(`/identity/users/${userId}/pending-tasks`, {
    notify: { forbidden: false, conflict: false, serverError: false },
  }).then((tasks) => (tasks ?? []).map(toPendingImpact))
}

/**
 * 人员影响面（离职 / 调岗 / 停用前的二次确认）：
 * `GET /users/{id}/in-flight-check` → `{pendingTaskCount, inFlightInstanceCount, items[]}`，
 * 明细含**单据类型 / 发起人 / 当前节点**，可直接填满影响清单的列。
 * 只读的知情信息：`VITE_USE_MOCK=true`（开发态）且接口不可用时回落演示数据。
 */
export function fetchUserImpact(userId: string): Promise<UserImpact> {
  return withDemoFallback(
    () =>
      get<WireUserInFlightCheck>(`/identity/users/${userId}/in-flight-check`, {
        notify: { forbidden: false, conflict: false, serverError: false },
      }),
    () => demoUserInFlightCheck,
  ).then((view) => ({
    userId,
    pendingTaskCount: view.pendingTaskCount ?? 0,
    inFlightInstanceCount: view.inFlightInstanceCount ?? 0,
    items: (view.items ?? []).map(toImpactItem),
  }))
}

// ---------------------------------------------------------------------------
// 通讯录与归属查询
// ---------------------------------------------------------------------------
/**
 * 通讯录：后端按组织分组返回 `List<DirectoryGroup>`（**不是分页**），
 * 这里摊平成人员列表供选择器使用；手机号由服务端按角色决定是否脱敏（PRD 5.3）。
 */
export function fetchDirectory(query: DirectoryQuery): Promise<PageResult<DirectoryItem>> {
  return get<WireDirectoryGroup[]>('/identity/directory', {
    params: {
      keyword: query.keyword,
      orgId: query.orgId,
      includeSubOrg: true,
    },
  }).then((groups) => {
    const list: DirectoryItem[] = []
    for (const group of groups ?? []) {
      for (const user of group.users ?? []) {
        list.push({
          userId: sid(user.id),
          name: user.name,
          account: user.account,
          employeeNo: user.employeeNo,
          orgName: user.orgName ?? group.orgName,
          orgPath: group.orgPath ?? '',
          positionName: user.position ?? undefined,
          phone: user.phone ?? '',
          phoneIsMasked: user.phoneMasked ?? false,
        })
      }
    }
    // 前端做一次关键字过滤 + 截断：后端按组织分组返回，没有分页语义
    const keyword = (query.keyword ?? '').trim()
    const filtered = keyword
      ? list.filter((item) =>
          `${item.name}${item.account}${item.employeeNo}${item.orgName}`.includes(keyword),
        )
      : list
    const pageSize = query.pageSize > 0 ? query.pageSize : 20
    return {
      list: filtered.slice(0, pageSize),
      total: filtered.length,
      page: 1,
      pageSize,
    }
  })
}

/** 该用户负责的组织（一人可在多个组织任负责人，PRD 5.1） */
export function fetchLeaderOf(userId: string): Promise<LeaderOfItem[]> {
  return get<WireLeaderOfView[]>(`/identity/users/${userId}/leader-of`).then((views) =>
    (views ?? []).map((view) => ({
      orgId: sid(view.orgId),
      orgName: view.orgName ?? '',
      orgPath: view.orgPath ?? '',
      leaderType: view.leaderType === 'deputy' ? 'deputy' : 'primary',
      businessLine:
        view.category === 'business' ||
        view.category === 'economy' ||
        view.category === 'admin' ||
        view.category === 'hr' ||
        view.category === 'invest'
          ? view.category
          : null,
      sortNo: view.sortNo ?? 0,
    })),
  )
}

/** 导出结果：CSV 内容 + 服务端下发的文件名（`Content-Disposition`） */
export interface UserExportResult {
  blob: Blob
  /** 服务端文件名，缺省回落 `user.csv`（import-spec §9.1 的模板名） */
  filename: string
}

/**
 * 从 `Content-Disposition` 解析文件名：优先 RFC 5987 的 `filename*=UTF-8''...`，
 * 其次 `filename="..."`；都没有时回落到调用方给的默认名。
 */
function parseFilename(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback
  const extended = /filename\*=\s*UTF-8''([^;]+)/i.exec(disposition)
  if (extended?.[1]) {
    try {
      return decodeURIComponent(extended[1].trim().replace(/^"|"$/g, ''))
    } catch {
      return fallback
    }
  }
  const plain = /filename=\s*"?([^";]+)"?/i.exec(disposition)
  return plain?.[1]?.trim() || fallback
}

/**
 * 人员主数据导出（import-spec §9.1 九列；§9.2 T-11 **仅系统管理员**）。
 * `GET /identity/users/export` 返回 CSV（UTF-8 带 BOM，可直接往返再导入），
 * 文件名取响应头 `Content-Disposition`。
 * ⚠ 文件流不走 `{code,message,data}` 解包（`unwrap: false`）；演示数据回落仅在
 *   `VITE_USE_MOCK=true`（开发态）时生效，生产必须抛出。
 */
export async function exportUsers(query: Omit<UserQuery, 'page' | 'pageSize'>): Promise<UserExportResult> {
  const config: OaRequestConfig = {
    params: {
      keyword: query.keyword,
      orgId: query.orgId,
      includeSubOrg: query.includeSubOrg ?? true,
      status: query.status,
    },
    responseType: 'blob',
    // 文件流不走 { code, message, data } 解包
    unwrap: false,
  }
  const fallback: UserExportResult = {
    blob: new Blob([demoUserExportCsv], { type: 'text/csv;charset=utf-8' }),
    filename: demoUserExportFilename,
  }
  try {
    const response = (await http.get('/identity/users/export', config)) as unknown as {
      data: Blob
      headers?: Record<string, unknown>
    }
    const disposition = response.headers?.['content-disposition']
    return {
      blob: response.data,
      filename: parseFilename(typeof disposition === 'string' ? disposition : undefined, demoUserExportFilename),
    }
  } catch (error) {
    // 只在开发态（VITE_USE_MOCK=true）回落演示 CSV，生产必须抛出
    if (!USE_MOCK) throw error
    return fallback
  }
}
