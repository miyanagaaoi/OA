/**
 * oa-web · 组织与负责人接口（oa.identity.org / oa.identity.org-leader）
 * ----------------------------------------------------------------------------
 * 路径与 `oa-server` 的 `identity/api/OrgController.java` / `LeaderLineController.java`
 * 逐条对齐；**响应字段以后端 DTO 为准**，本文件把 `types/identity-wire.d.ts`
 * （后端 DTO 镜像）映射为前端领域模型 `types/identity.d.ts`。
 *
 *   GET    /api/v1/identity/orgs/tree                     组织树（四级，含 hasPrimaryLeader）
 *   GET    /api/v1/identity/orgs/search                   组织检索（只有 keyword/includeDisabled）
 *   GET    /api/v1/identity/orgs/selector                 选择器数据源（树形 OrgOption，**支持 keyword**）
 *   POST   /api/v1/identity/orgs                          新增组织节点
 *   PUT    /api/v1/identity/orgs/{id}                     改名 / 排序 / 备注
 *   POST   /api/v1/identity/orgs/{id}/move                移动节点（newParentId + reason/force）
 *   POST   /api/v1/identity/orgs/{id}/disable             停用（可选 body {reason?,force?}）
 *   POST   /api/v1/identity/orgs/{id}/enable              启用（可选 body {reason?,force?}）
 *   GET    /api/v1/identity/orgs/{id}/path                全路径
 *   GET    /api/v1/identity/orgs/{id}/ancestors           祖先链
 *   GET    /api/v1/identity/orgs/{id}/descendants         子树（平铺 OrgView）
 *   GET    /api/v1/identity/orgs/{id}/in-flight-check     停用前在途检查（含 items / activeStaffCount）
 *   GET    /api/v1/identity/orgs/export                   组织 CSV 导出（列与 org.csv 模板一致）
 *   GET    /api/v1/identity/orgs/{id}/leaders             负责人列表
 *   POST   /api/v1/identity/orgs/{id}/leaders             新增负责人
 *   PUT    /api/v1/identity/orgs/{id}/leaders/{leaderId}  调整负责人
 *   DELETE /api/v1/identity/orgs/{id}/leaders/{leaderId}  移除负责人（可选 body {reason?,force?}）
 *   GET    /api/v1/identity/orgs/{id}/leader-candidates   负责人候选人（**支持 keyword**）
 *   GET    /api/v1/identity/leaders/lines                 集团层业务线绑定（五值）
 *   PUT    /api/v1/identity/leaders/lines/{category}      绑定某业务线的分管领导（可带 reason/force）
 *
 * 口径来源：`doc/import-spec.md` §3.2 / §3.4、§4.2 / §4.4、§7、§8.2；
 *           `doc/prd-0.1.md` 5.1 / 5.5、AC-11。
 * 说明：本文件对**主数据**（组织树 / 检索 / 写操作）**不做演示数据降级**（与 auth.ts / task.ts 的
 *       withDemoFallback 不同）——组织与人员是主数据，静默回落演示数据会让管理员误判真实状态。
 *       唯一的例外是只读的「在途/待办影响清单」（`checkOrgInFlight`）：它只是操作前的知情信息，
 *       且在 `VITE_USE_MOCK=true`（开发态）时才回落，方便无后端时查看新加的明细列。
 */
import { del, get, post, put, withDemoFallback } from './http'
import { demoOrgInFlightCheck } from './demo'
import type {
  BusinessLine,
  ImpactItem,
  InFlightCheck,
  LeaderCandidate,
  LeaderLineBinding,
  LeaderLineUpdatePayload,
  LeaderType,
  OrgBrief,
  OrgCreatePayload,
  OrgLeader,
  OrgLeaderCreatePayload,
  OrgLeaderUpdatePayload,
  OrgMovePayload,
  OrgPathResult,
  OrgSelectorQuery,
  OrgStateChangePayload,
  OrgStatus,
  OrgTreeNode,
  OrgType,
  OrgUpdatePayload,
  UserStatus,
} from '@/types/identity'
import type {
  WireId,
  WireInFlightCheck,
  WireInFlightItem,
  WireLeaderCandidateView,
  WireLeaderLineView,
  WireLeaderView,
  WireOrgOption,
  WireOrgPathView,
  WireOrgView,
} from '@/types/identity-wire'

// ---------------------------------------------------------------------------
// 映射工具：后端 Long → 字符串 id；枚举 code 收窄
// ---------------------------------------------------------------------------
function sid(value: WireId | null | undefined): string {
  return value === null || value === undefined ? '' : String(value)
}

function sidOrNull(value: WireId | null | undefined): string | null {
  return value === null || value === undefined ? null : String(value)
}

function toOrgType(value: string | null | undefined): OrgType {
  switch (value) {
    case 'group':
    case 'company':
    case 'dept':
    case 'section':
      return value
    default:
      return 'dept'
  }
}

function toOrgStatus(value: string | null | undefined): OrgStatus {
  return value === 'disabled' ? 'disabled' : 'active'
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

function toLeaderType(value: string | null | undefined): LeaderType {
  return value === 'deputy' ? 'deputy' : 'primary'
}

/** 业务线 = 事项类别五值 code；空值表示未按业务线绑定 */
function toBusinessLine(value: string | null | undefined): BusinessLine | null {
  switch (value) {
    case 'business':
    case 'economy':
    case 'admin':
    case 'hr':
    case 'invest':
      return value
    default:
      return null
  }
}

function toOrgBrief(view: WireOrgView): OrgBrief {
  return {
    id: sid(view.id),
    name: view.name,
    orgType: toOrgType(view.orgType),
    parentId: sidOrNull(view.parentId),
    path: view.path,
    depth: view.depth ?? 0,
    status: toOrgStatus(view.status),
  }
}

function toOrgNode(view: WireOrgView): OrgTreeNode {
  const children = view.children?.map(toOrgNode)
  return {
    ...toOrgBrief(view),
    remark: view.remark ?? null,
    sortNo: view.sortNo ?? undefined,
    childCount: children?.length ?? 0,
    /*
     * 「未设正职」提示（AC-11 / W-ORG-014）：
     * 权威值 = 树接口的 `hasPrimaryLeader`（服务端按 sys_org_leader 中
     * leader_type='primary' AND category IS NULL 全树**一次批量查询**聚合，无 N+1）。
     * ⚠ 不再用 `leaderId` 推断：那只是 sys_org.leader_id 的冗余列，
     *   业务线分管领导（category 非空）不算正职，会让判定偏「已设正职」。
     */
    hasPrimaryLeader: view.hasPrimaryLeader === true,
    children: children?.length ? children : undefined,
  }
}

/**
 * 单据类型：后端给的是 `flow_instance.form_type` 原值（code 或中文标签）。
 * 命中四类 code 时收窄成领域模型的 FormType；其余原样放进 `formTypeLabel` 由界面直接展示；
 * 都没有则不填（界面显示「—」）——不猜、不造。
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

/** 影响清单单行：人员侧与组织侧共用（组织侧无 currentNodeName 时退回 nodeName） */
function toImpactItem(item: WireInFlightItem): ImpactItem {
  const type = toFormType(item.formType)
  const nodeName = item.currentNodeName ?? item.nodeName ?? undefined
  return {
    instanceId: item.instanceId === null || item.instanceId === undefined ? undefined : String(item.instanceId),
    bizNo: item.bizNo,
    formType: type.formType,
    formTypeLabel: type.label,
    initiatorName: item.initiatorName ?? undefined,
    currentNodeName: nodeName,
    status: item.status ?? undefined,
    // 影响程度 / 受影响原因不在出参里：界面显示「—」，由操作人结合上下文判断（不做推断）
  }
}

function toInFlight(view: WireInFlightCheck | null | undefined, orgId: string): InFlightCheck {
  if (!view) {
    return {
      orgId,
      blocking: false,
      inFlightCount: 0,
      pendingTasks: 0,
      items: [],
      activeStaffCount: 0,
      warnings: [],
    }
  }
  // 规范名优先，旧字段名兜底（服务端两者同值，保留旧名只为兼容旧版本）
  const inFlightCount = view.inFlightInstanceCount ?? view.inFlightInstances ?? view.total ?? 0
  const pendingTasks = view.pendingTaskCount ?? view.pendingTasks ?? 0
  const items: ImpactItem[] = view.items?.length
    ? view.items.map(toImpactItem)
    : // 旧版本接口只回单号：合成最小行，其余列在界面显示「—」
      (view.bizNos ?? []).map((bizNo) => ({ bizNo }))
  return {
    orgId,
    blocking: view.blocked || inFlightCount > 0 || pendingTasks > 0,
    inFlightCount,
    pendingTasks,
    items,
    activeStaffCount: view.activeStaffCount ?? 0,
    warnings:
      view.blocked && view.blockOnInflight === false
        ? ['服务端未将本条判定为硬阻断，请确认后继续']
        : [],
    message: view.message ?? undefined,
  }
}

function toLeader(view: WireLeaderView): OrgLeader {
  return {
    leaderId: sid(view.id),
    orgId: sid(view.orgId),
    orgPath: view.orgPath ?? undefined,
    userName: view.userName,
    userId: sid(view.userId),
    account: view.account,
    employeeNo: view.employeeNo ?? undefined,
    positionName: view.dutyTitle ?? undefined,
    leaderType: toLeaderType(view.leaderType),
    sortNo: view.sortNo ?? 0,
    businessLine: toBusinessLine(view.category),
    dutyTitle: view.dutyTitle ?? null,
    remark: view.remark ?? null,
    userStatus: view.userStatus ? toUserStatus(view.userStatus) : undefined,
  }
}

function toCandidate(view: WireLeaderCandidateView): LeaderCandidate {
  return {
    userId: sid(view.userId),
    name: view.name,
    account: view.account,
    employeeNo: view.employeeNo ?? '',
    // 后端候选人视图不含所属组织名（只有 orgId），页面用职务名做补充信息
    orgName: '',
    positionName: view.position ?? undefined,
    status: toUserStatus(view.userStatus),
    boundLeaderType: view.leader ? toLeaderType(view.leaderType) : null,
  }
}

/** 业务线绑定视图 → 领域模型：后端一个业务线可挂多名领导，取首位代表 + 是否有正职 */
function toLeaderLine(view: WireLeaderLineView): LeaderLineBinding {
  const leaders = (view.leaders ?? []).map(toLeader)
  const primary = leaders.find((item) => item.leaderType === 'primary') ?? null
  const leader = primary ?? leaders[0] ?? null
  return {
    category: toBusinessLine(view.category) ?? 'business',
    categoryLabel: view.categoryLabel ?? undefined,
    orgId: sidOrNull(view.orgId),
    orgName: view.orgName ?? null,
    leaderId: leader ? leader.leaderId : null,
    leaderName: leader ? leader.userName : null,
    leaderType: leader ? leader.leaderType : null,
    hasPrimary: primary !== null,
    updatedAt: undefined,
  }
}

/** 扁平化选择器树：el-select / 远程筛选都用「全量可见节点」列表 */
function flattenOptions(options: WireOrgOption[], out: OrgBrief[] = []): OrgBrief[] {
  for (const option of options) {
    out.push({
      id: sid(option.value),
      name: option.label,
      orgType: toOrgType(option.orgType),
      parentId: null,
      path: option.path,
      depth: option.depth ?? 0,
      status: option.disabled ? 'disabled' : 'active',
    })
    if (option.children?.length) flattenOptions(option.children, out)
  }
  return out
}

// ---------------------------------------------------------------------------
// 组织树与检索
// ---------------------------------------------------------------------------
/** 组织树；`includeDisabled` 默认 true —— 管理后台必须能看到停用节点才知道要启用谁 */
export function fetchOrgTree(params: { includeDisabled?: boolean; rootId?: string } = {}): Promise<OrgTreeNode[]> {
  return get<WireOrgView[]>('/identity/orgs/tree', {
    params: { includeDisabled: params.includeDisabled ?? true, rootId: params.rootId },
  }).then((views) => (views ?? []).map(toOrgNode))
}

/** 组织检索：后端只接受 keyword / includeDisabled（无分页、无 limit） */
export function searchOrgs(keyword: string): Promise<OrgBrief[]> {
  return get<WireOrgView[]>('/identity/orgs/search', {
    params: { keyword, includeDisabled: true },
  }).then((views) => (views ?? []).map(toOrgBrief))
}

/**
 * 组织选择器数据源：后端返回**树形** OrgOption，**支持 `keyword`**（服务端按名称/路径过滤）。
 * 这里扁平化后返回；`keyword` 缺省时仍可用页面本地的 filterable 过滤，
 * 传了 keyword 则交给服务端（数据域外的节点本就不下发）。
 * 只返回数据域内可见节点（DESIGN.md › cascader：无权限节点不可见而非禁用）。
 */
export function fetchOrgSelector(query: OrgSelectorQuery = {}): Promise<OrgBrief[]> {
  return get<WireOrgOption[]>('/identity/orgs/selector', {
    params: {
      includeDisabled: query.includeDisabled ?? false,
      keyword: query.keyword?.trim() || undefined,
    },
  }).then((options) => flattenOptions(options ?? []))
}

// ---------------------------------------------------------------------------
// 组织维护
// ---------------------------------------------------------------------------
export function createOrg(payload: OrgCreatePayload): Promise<OrgBrief> {
  return post<WireOrgView>('/identity/orgs', {
    name: payload.name,
    orgType: payload.orgType,
    parentId: payload.parentId,
    status: payload.status,
    remark: payload.remark,
  }).then(toOrgBrief)
}

/** 改名 / 备注；结构调整必须走 move（保持 org_path 与 parent_path 一致，E-ORG-003） */
export function updateOrg(orgId: string, payload: OrgUpdatePayload): Promise<OrgBrief> {
  return put<WireOrgView>(`/identity/orgs/${orgId}`, {
    name: payload.name,
    remark: payload.remark,
  }).then(toOrgBrief)
}

/**
 * 移动节点：触发 import-spec §7.1 场景 1 的影响清单（在途单据不自动改派）。
 * `reason`/`force` 是 AC-52 的留痕字段：`force=true` 时 reason 必填、调用人须为系统管理员，
 * 两者随 `@Audited` 写进 `sys_log`（不改变 block-on-inflight 的硬阻断判定）。
 */
export function moveOrg(orgId: string, payload: OrgMovePayload): Promise<OrgBrief> {
  return post<WireOrgView>(`/identity/orgs/${orgId}/move`, {
    newParentId: payload.targetParentId,
    reason: payload.reason,
    force: payload.force ?? false,
  }).then(toOrgBrief)
}

/**
 * 停用组织：PRD 5.5 / E-ORG-010 —— 停用前必须清空该节点及其子树下的在途单据。
 * 调用前必须先 `checkOrgInFlight`。
 * 请求体 `{reason, force}`：`force=true` + 非空原因 + 系统管理员 → 服务端**放行在途阻断**
 * 并把原因与操作人写入审计日志（AC-52）。
 */
export function disableOrg(orgId: string, payload: OrgStateChangePayload): Promise<void> {
  return post<void>(`/identity/orgs/${orgId}/disable`, {
    reason: payload.reason,
    force: payload.force ?? false,
  })
}

/** 启用组织：同为危险操作，可带 `{reason, force}`（服务端可选 body，语义同 disable） */
export function enableOrg(orgId: string, payload: OrgStateChangePayload): Promise<void> {
  return post<void>(`/identity/orgs/${orgId}/enable`, {
    reason: payload.reason,
    force: payload.force ?? false,
  })
}

// ---------------------------------------------------------------------------
// 路径与子树
// ---------------------------------------------------------------------------
export function fetchOrgPath(orgId: string): Promise<OrgPathResult> {
  return get<WireOrgPathView>(`/identity/orgs/${orgId}/path`).then((view) => ({
    orgId: sid(view.id),
    path: view.path,
    depth: view.depth ?? 0,
    segments: (view.segments ?? []).map((segment) => ({
      id: sid(segment.id),
      name: segment.name,
      orgType: toOrgType(segment.orgType),
      parentId: null,
      path: view.path,
      depth: 0,
      status: 'active' as OrgStatus,
    })),
  }))
}

export function fetchOrgAncestors(orgId: string): Promise<OrgBrief[]> {
  return get<WireOrgView[]>(`/identity/orgs/${orgId}/ancestors`).then((views) =>
    (views ?? []).map(toOrgBrief),
  )
}

/** 子树：后端 descendants 返回**平铺** OrgView 列表（无 flat 参数） */
export function fetchOrgDescendants(
  orgId: string,
  params: { includeDisabled?: boolean } = {},
): Promise<OrgBrief[]> {
  return get<WireOrgView[]>(`/identity/orgs/${orgId}/descendants`, {
    params: { includeDisabled: params.includeDisabled ?? true },
  }).then((views) => (views ?? []).map(toOrgBrief))
}

/**
 * 停用前的在途检查（E-ORG-010 / import-spec §8.2）。
 * `blocking = true` 时页面**默认阻断**，只有具备「强制继续」权限的管理员
 * 才能填写原因后放行；`force=true` + 原因随 disable 请求提交，服务端放行并写审计（AC-52）。
 * 只读的知情信息：`VITE_USE_MOCK=true`（开发态）且接口不可用时回落演示数据。
 */
export function checkOrgInFlight(orgId: string): Promise<InFlightCheck> {
  return withDemoFallback(
    () =>
      get<WireInFlightCheck>(`/identity/orgs/${orgId}/in-flight-check`, {
        notify: { serverError: false },
      }),
    () => demoOrgInFlightCheck,
  ).then((view) => toInFlight(view, orgId))
}

// ---------------------------------------------------------------------------
// 负责人（正职 / 副职）
// ---------------------------------------------------------------------------
export function fetchOrgLeaders(orgId: string): Promise<OrgLeader[]> {
  return get<WireLeaderView[]>(`/identity/orgs/${orgId}/leaders`).then((views) =>
    (views ?? []).map(toLeader),
  )
}

/** 新增负责人：同组织同业务线只能有一个正职（E-LEAD-004），负责人不得为离职人员（E-LEAD-006） */
export function createOrgLeader(orgId: string, payload: OrgLeaderCreatePayload): Promise<OrgLeader> {
  return post<WireLeaderView>(`/identity/orgs/${orgId}/leaders`, {
    userId: payload.userId,
    leaderType: payload.leaderType,
    // 后端字段名是 category（= 事项类别五值），仅集团层允许填写（E-LEAD-008）
    category: payload.businessLine ?? undefined,
    sortNo: payload.sortNo,
    remark: payload.remark,
  }).then(toLeader)
}

/** 调整负责人：升/降正副职、改排序、改分管业务线（集团层五值） */
export function updateOrgLeader(
  orgId: string,
  leaderId: string,
  payload: OrgLeaderUpdatePayload,
): Promise<OrgLeader> {
  return put<WireLeaderView>(`/identity/orgs/${orgId}/leaders/${leaderId}`, {
    leaderType: payload.leaderType,
    category: payload.businessLine ?? undefined,
    sortNo: payload.sortNo,
    remark: payload.remark,
  }).then(toLeader)
}

/**
 * 移除负责人：危险操作，必须二次确认。
 * 请求体 `{reason, force}`（后端可选 body）：`force=true` 时 reason 必填、调用人须为系统管理员，
 * 两者随 `@Audited` 落 `sys_log`。
 */
export function deleteOrgLeader(
  orgId: string,
  leaderId: string,
  payload: { reason: string; force?: boolean },
): Promise<void> {
  return del<void>(`/identity/orgs/${orgId}/leaders/${leaderId}`, {
    data: { reason: payload.reason, force: payload.force ?? false },
  })
}

/** 负责人候选人：`keyword` 为**服务端过滤**（姓名/账号/工号）；不传则返回该组织全部候选人 */
export function fetchLeaderCandidates(orgId: string, keyword?: string): Promise<LeaderCandidate[]> {
  return get<WireLeaderCandidateView[]>(`/identity/orgs/${orgId}/leader-candidates`, {
    params: { keyword: keyword?.trim() || undefined },
  }).then((views) => (views ?? []).map(toCandidate))
}

// ---------------------------------------------------------------------------
// 集团层业务线绑定（经营 / 经济 / 行政 / 人力 / 投资）
// ---------------------------------------------------------------------------
export function fetchLeaderLines(): Promise<LeaderLineBinding[]> {
  return get<WireLeaderLineView[]>('/identity/leaders/lines').then((views) =>
    (views ?? []).map(toLeaderLine),
  )
}

/**
 * 绑定某业务线的分管领导：`category` 为事项类别五值 code（不新增枚举，T-06 定稿）。
 * 语义是**替换**；`reason`/`force` 为 AC-52 留痕字段（`force=true` 时 reason 必填且须系统管理员）。
 */
export function updateLeaderLine(
  category: BusinessLine,
  payload: LeaderLineUpdatePayload,
): Promise<LeaderLineBinding> {
  return put<WireLeaderLineView>(`/identity/leaders/lines/${category}`, {
    orgId: payload.orgId,
    userId: payload.userId,
    leaderType: payload.leaderType,
    reason: payload.reason,
    force: payload.force ?? false,
  }).then(toLeaderLine)
}
