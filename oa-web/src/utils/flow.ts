/**
 * oa-web · 流程设计器纯逻辑（无副作用、无框架依赖）
 * ----------------------------------------------------------------------------
 * 本文件刻意**只做计算**：不 import 任何运行时模块（仅 `import type`），不碰 DOM / axios / Pinia。
 * 之所以把逻辑集中在这里：
 *   1. `oa-web` 目前**没有前端测试框架**（`package.json` 无 vitest / jest，且任务书要求
 *      「没有测试框架就不要新建」），因此把可测逻辑抽成本模块，便于用任何方式（含
 *      `node` 直接执行）复核；页面组件只负责渲染与调用。
 *   2. 口径必须与后端**逐条一致**——每条函数都在注释里写明对应的服务端落点，
 *      两边一旦漂移，界面就会给出「前端说能过、后端说不能过」的假提示。
 *
 * 对齐清单（后端 → 本文件）：
 *   · `ThresholdPolicy.compose` / `parse` / `resolve` → {@link composeThresholdLiteral}
 *     / {@link checkThresholdLiteral} / {@link resolveThreshold}
 *   · `FlowGatePolicy.violations` / `isUnlimited` → {@link checkGatePolicy} / {@link isGateUnlimited}
 *   · `RequiredNodePolicy.violations` / `trunkMap` → {@link checkNodeOrder} / {@link FLOW_TRUNK_NODES}
 *   · `PrePublishChecker.RULES`（12 条）→ {@link FLOW_CHECK_RULE_ORDER}
 *
 * 真源：`doc/templates.md` §1.0 / §1.7 / §3.3、`doc/enums.md` §2 / §3 / §7、`doc/prd-0.1.md` §5.4。
 */
import type {
  FlowCheckItem,
  FlowDecisionMode,
  FlowDeadlineType,
  FlowFormType,
  FlowGatePolicy,
  FlowGatePolicyPayload,
  FlowJsonValue,
  FlowNode,
  FlowNodeType,
  FlowSignPolicy,
  FlowTemplateStatus,
  FlowThresholdBasis,
  FlowTimeoutAction,
  FlowTrunkNodeCode,
  FlowWithdrawWindow,
} from '@/types/flow'

// ================================================================ 枚举中文 / 候选

/** 模板状态（`doc/templates.md` §3.3） */
export const FLOW_STATUS_LABEL: Record<FlowTemplateStatus, string> = {
  draft: '草稿',
  published: '已发布',
  archived: '已归档',
}

/** 单据类型（`doc/enums.md` §10.2） */
export const FLOW_FORM_TYPE_LABEL: Record<FlowFormType, string> = {
  matter: '事项审批单',
  fund: '资金审批单',
  contract: '合同审批单',
  seal: '印鉴证照审批单',
}

/** 决议模式（`doc/prd-0.1.md` §5.4） */
export const FLOW_DECISION_MODE_LABEL: Record<FlowDecisionMode, string> = {
  any: '或签',
  all: '会签',
  sequence: '依次审批',
}

/** 签名策略（`doc/templates.md` §1.0） */
export const FLOW_SIGN_POLICY_LABEL: Record<FlowSignPolicy, string> = {
  required: '强制',
  optional: '可选',
  none: '不签名',
}

/** 节点类型（`doc/data-model.md` §4.2） */
export const FLOW_NODE_TYPE_LABEL: Record<FlowNodeType, string> = {
  approve: '审批',
  cc: '抄送',
  condition: '条件（二期）',
  archive: '归档登记',
}

/** 补件时限口径（`doc/templates.md` §1.7） */
export const FLOW_DEADLINE_TYPE_LABEL: Record<FlowDeadlineType, string> = {
  calendar: '自然日',
  working: '工作日',
}

/** 补件超时处理（`doc/templates.md` §1.7） */
export const FLOW_TIMEOUT_ACTION_LABEL: Record<FlowTimeoutAction, string> = {
  notify: '仅提醒',
  auto_pass: '自动通过',
  auto_return: '自动退回',
}

/** 撤回窗口口径（`doc/templates.md` §1.8，2026-10-04 裁定） */
export const FLOW_WITHDRAW_WINDOW_LABEL: Record<FlowWithdrawWindow, string> = {
  until_finance_approved: '②通过前（含②审批中）',
  until_finance_started: '②开始前（受理后不可撤回）',
}

/** 撤回窗口口径的两句说明（面板下拉与提示共用，逐字对齐 `WithdrawWindow.label()`） */
export const FLOW_WITHDRAW_WINDOW_HINT: Record<FlowWithdrawWindow, string> = {
  until_finance_approved:
    '默认口径（REQ-FLOW-009）：财务部复核（节点②）**通过之前**都可撤回，②审批中也能撤回。与既有行为逐字一致。',
  until_finance_started:
    '严格口径（AC-16）：仅在节点②**开始处理之前**可撤回；②一旦受理（active / 待补件 / 被回退）即不可撤回。',
}

/** 阈值判定依据（`ThresholdPolicy.Threshold.basis`） */
export const FLOW_THRESHOLD_BASIS_LABEL: Record<FlowThresholdBasis, string> = {
  any: '或签（阈值不参与判定）',
  absolute: '绝对人数',
  percent: '百分比（向上取整）',
  majority: '未配置 → 过半',
}

/** 发布前校验单条结论（`CheckItemView.status`） */
export const FLOW_CHECK_STATUS_LABEL: Record<string, string> = {
  pass: '通过',
  fail: '不通过',
  warn: '提示',
}

/** 模板状态候选（`code` 即 `flow_template.status`） */
export const FLOW_STATUS_OPTIONS: readonly FlowTemplateStatus[] = ['draft', 'published', 'archived']

/** 单据类型候选 */
export const FLOW_FORM_TYPE_OPTIONS: readonly FlowFormType[] = ['matter', 'fund', 'contract', 'seal']

/** 决议模式候选（⑦ 归档登记不适用，页面另行禁用） */
export const FLOW_DECISION_MODE_OPTIONS: readonly FlowDecisionMode[] = ['any', 'all', 'sequence']

/** 签名策略候选 */
export const FLOW_SIGN_POLICY_OPTIONS: readonly FlowSignPolicy[] = ['required', 'optional', 'none']

/** 补件时限口径候选 */
export const FLOW_DEADLINE_TYPE_OPTIONS: readonly FlowDeadlineType[] = ['calendar', 'working']

/** 补件超时处理候选 */
export const FLOW_TIMEOUT_ACTION_OPTIONS: readonly FlowTimeoutAction[] = ['notify', 'auto_pass', 'auto_return']

/** 撤回窗口口径候选（顺序 = 文档 §1.8 的表格顺序：默认口径在前） */
export const FLOW_WITHDRAW_WINDOW_OPTIONS: readonly FlowWithdrawWindow[] = [
  'until_finance_approved',
  'until_finance_started',
]

/**
 * 主干 7 节点（`doc/enums.md` §2）：**码 → 固定 seq → 中文名**。
 *
 * 顺序即主干顺序，`seq` 固定不可改（`RequiredNodePolicy`）。这 7 个节点在任何模板版本中
 * 都必须存在，**删除接口一律 40008 拒绝**。
 */
export const FLOW_TRUNK_NODES: readonly { code: FlowTrunkNodeCode; seq: number; label: string }[] = [
  { code: 'dept_leader', seq: 1, label: '直属部门负责人' },
  { code: 'finance_review', seq: 2, label: '财务部复核' },
  { code: 'branch_leader', seq: 3, label: '分公司分管领导' },
  { code: 'subsidiary_gm', seq: 4, label: '子公司总经理' },
  { code: 'group_leader', seq: 5, label: '集团分管领导' },
  { code: 'chairman', seq: 6, label: '集团董事长' },
  { code: 'archive_register', seq: 7, label: '归档登记' },
]

/** 主干节点数（一期固定 7 个） */
export const FLOW_TRUNK_NODE_COUNT = FLOW_TRUNK_NODES.length

/**
 * 发布前校验的**权威规则顺序**（逐条对应 `PrePublishChecker.RULES` 的 `LinkedHashMap` 插入顺序）。
 *
 * ⚠ 为什么前端要自己排：后端用 `Map.copyOf` 聚合规则，`Map.copyOf` **不保留插入顺序**，
 * `GET /flow-designs/check-rules` 与 `pre-publish-check` 的 `checks[]` 返回顺序实测是乱的
 * （R-THRESHOLD 排在第一）。界面按本常量重排，保证「逐条展示 12 条规则」的顺序稳定可读。
 */
export const FLOW_CHECK_RULE_ORDER: readonly string[] = [
  'R-METADATA',
  'R-TRUNK',
  'R-SEQ',
  'R-NODE-CODE',
  'R-NODE-TYPE',
  'R-APPROVER-RULE',
  'R-DECISION',
  'R-THRESHOLD',
  'R-SIGN',
  'R-TIMEOUT',
  'R-SKIP',
  'R-GATE',
]

/** 规则 id 的排序权重（未知规则排到最后，保持稳定顺序） */
export function checkRuleRank(rule: string): number {
  const index = FLOW_CHECK_RULE_ORDER.indexOf(rule)
  return index < 0 ? FLOW_CHECK_RULE_ORDER.length : index
}

/**
 * 按权威顺序重排校验结论。
 *
 * @param checks 后端返回的 `checks[]`（顺序不保证）
 * @returns 新数组（不修改入参）；未知规则排在已知规则之后，同权按原顺序稳定排列
 */
export function sortCheckItems(checks: readonly FlowCheckItem[]): FlowCheckItem[] {
  return checks
    .map((item, index) => ({ item, index }))
    .sort((left, right) => {
      const diff = checkRuleRank(left.item.rule) - checkRuleRank(right.item.rule)
      return diff !== 0 ? diff : left.index - right.index
    })
    .map((entry) => entry.item)
}

// ================================================================ 节点码 / 删除禁用

/** 节点码是否为主干必填节点码 */
export function isTrunkNodeCode(code: string | null | undefined): boolean {
  if (!code) return false
  const normalized = code.trim().toLowerCase()
  return FLOW_TRUNK_NODES.some((item) => item.code === normalized)
}

/** 主干节点的固定 seq；非主干返回 `null` */
export function trunkNodeSeq(code: string | null | undefined): number | null {
  if (!code) return null
  const normalized = code.trim().toLowerCase()
  return FLOW_TRUNK_NODES.find((item) => item.code === normalized)?.seq ?? null
}

/**
 * 节点删除按钮的**禁用原因**（`null` = 可删除）。
 *
 * 文案与后端 `RequiredNodePolicy.assertDeletable` 的 40008 说明逐条对应，避免
 * 「界面说不能删、后端其实能删」或反过来的错位。
 */
export function nodeDeleteDisabledReason(node: Pick<FlowNode, 'nodeCode' | 'seq'>): string | null {
  if (isTrunkNodeCode(node.nodeCode)) {
    return (
      `主干必填节点不可删除（seq=${node.seq} code=${node.nodeCode}）：` +
      '主干固定 7 个、顺序固定（doc/enums.md §2）；如需调整请改决议模式/阈值或新增节点。' +
      '服务端会以 40008 拒绝该请求。'
    )
  }
  return null
}

/** 单节点摘要（左栏与版本对比的副标题）：决议模式 / 解析规则 / 超时 */
export function nodeSummary(node: FlowNode): string {
  const parts: string[] = []
  parts.push(
    node.decisionMode ? FLOW_DECISION_MODE_LABEL[node.decisionMode] : node.nodeType === 'archive' ? '登记（无决议）' : '未设决议',
  )
  if (node.signPolicy) parts.push(`签名：${FLOW_SIGN_POLICY_LABEL[node.signPolicy]}`)
  if (node.timeoutHours !== null) parts.push(`${node.timeoutHours}h`)
  if (node.allowRoute) parts.push('可流转')
  if (node.allowAddSign) parts.push('可加签')
  if (node.allowJump) parts.push('可跳转')
  return parts.join(' · ')
}

// ================================================================ 阈值（T-07）

/** 阈值的两种写法：绝对人数 / 百分比（二者可同时给出，绝对人数优先） */
export interface FlowThresholdParts {
  absolute: number | null
  percent: number | null
}

/**
 * 阈值字面量的**合法性检查**（镜像 `ThresholdPolicy.parse` 的取值边界）。
 *
 * @param literal `"2"` / `"66%"` / `null`（= 过半）
 * @returns 问题文案；`null` = 合法
 */
export function checkThresholdLiteral(literal: string | null | undefined): string | null {
  if (literal === null || literal === undefined || literal.trim() === '') return null
  const text = literal.trim()
  if (/^[0-9]{1,3}%$/.test(text)) {
    const number = Number.parseInt(text.slice(0, -1), 10)
    if (number < 1 || number > 100) {
      return `会签百分比阈值必须在 1%~100% 之间，实际「${text}」`
    }
    return null
  }
  if (/^[0-9]{1,3}$/.test(text)) {
    const number = Number.parseInt(text, 10)
    if (number < 1 || number > 99) {
      return `会签绝对人数阈值必须在 1~99 之间，实际「${text}」`
    }
    return null
  }
  return `会签阈值只支持「绝对人数」（如 "2"）或「百分比」（如 "66%"）两种写法，实际「${text}」`
}

/**
 * 解析阈值字面量（不做优先裁决，且**不抛异常**：非法/空值返回两者皆空）。
 *
 * ⚠ 空值必须返回「两者皆空」而不是 `NaN`：`Number.parseInt('')` 是 `NaN`，
 * 而 `NaN !== null` 会被 {@link resolveThreshold} 误判成「配了绝对人数阈值」。
 */
export function parseThresholdLiteral(literal: string | null | undefined): FlowThresholdParts {
  const text = (literal ?? '').trim()
  if (text === '' || checkThresholdLiteral(text) !== null) return { absolute: null, percent: null }
  if (text.endsWith('%')) {
    const percent = Number.parseInt(text.slice(0, -1), 10)
    return Number.isFinite(percent) ? { absolute: null, percent } : { absolute: null, percent: null }
  }
  const absolute = Number.parseInt(text, 10)
  return Number.isFinite(absolute) ? { absolute, percent: null } : { absolute: null, percent: null }
}

/**
 * 写入侧合成（镜像 `ThresholdPolicy.compose`）：**绝对人数优先**（`doc/templates.md` T-07）。
 *
 * @returns 落库字面量；两者皆空返回 `null`
 */
export function composeThresholdLiteral(
  absolute: number | null | undefined,
  percent: number | null | undefined,
): string | null {
  if (absolute !== null && absolute !== undefined) return String(absolute)
  if (percent !== null && percent !== undefined) return `${percent}%`
  return null
}

/** 阈值解析结果（镜像 `ThresholdPolicy.Threshold`） */
export interface FlowThresholdResolved {
  requiredApprovals: number
  basis: FlowThresholdBasis
  satisfiable: boolean
}

/**
 * 按候选人数量解析通过条件（镜像 `ThresholdPolicy.resolve`）。
 *
 * 口径：或签 → 任一人通过（阈值不参与）；绝对人数 → 该人数；百分比 → **向上取整**；
 * 未配置 → **过半**（`floor(候选人数/2)+1`）。
 * `satisfiable=false` 表示阈值大于候选人数，该节点在当前候选人下**永远无法通过**。
 */
export function resolveThreshold(
  mode: FlowDecisionMode | null,
  literal: string | null | undefined,
  candidateCount: number,
): FlowThresholdResolved {
  const candidates = Math.max(candidateCount, 0)
  const effective: FlowDecisionMode = mode ?? 'any'
  const parsed = parseThresholdLiteral(literal)
  if (effective === 'any') {
    return { requiredApprovals: candidates === 0 ? 0 : 1, basis: 'any', satisfiable: true }
  }
  if (parsed.absolute !== null) {
    return {
      requiredApprovals: parsed.absolute,
      basis: 'absolute',
      satisfiable: candidates === 0 || parsed.absolute <= candidates,
    }
  }
  if (parsed.percent !== null) {
    const required = Math.ceil((candidates * parsed.percent) / 100)
    return {
      requiredApprovals: required,
      basis: 'percent',
      satisfiable: candidates === 0 || required <= candidates,
    }
  }
  const required = candidates === 0 ? 0 : Math.floor(candidates / 2) + 1
  return { requiredApprovals: required, basis: 'majority', satisfiable: candidates === 0 || required <= candidates }
}

// ================================================================ Q6 / Q7 闸门配置（§1.7）

/** 次数上限的上界（镜像 `FlowGateEnums.MAX_COUNT_LIMIT`，防空转） */
export const FLOW_GATE_MAX_COUNT = 99

/** 补件时限天数上界（镜像 `FlowGateEnums.MAX_DEADLINE_DAYS`） */
export const FLOW_GATE_MAX_DEADLINE_DAYS = 365

/** `null` / `0` → `null`（= 不限）；其余原样（镜像 `FlowGatePolicy.normalizeCount`） */
export function normalizeGateCount(value: number | null | undefined): number | null {
  if (value === null || value === undefined || value === 0) return null
  return value
}

/**
 * 闸门配置的**前端提示**（镜像 `FlowGatePolicy.violations`，逐条文案对齐后端）。
 *
 * ⚠ 这里只是**即时提示**：真正的裁决在后端 `PUT .../gate-policy`（`40008` 带同义文案）。
 * 前端拦住是为了少一次往返，不是为了代替服务端校验。
 *
 * @returns 问题清单（空 = 合法）
 */
export function checkGatePolicy(payload: FlowGatePolicyPayload): string[] {
  const problems: string[] = []
  const checkCount = (field: 'maxReturnCount' | 'maxSupplementCount', value: number | null | undefined): void => {
    if (value === null || value === undefined) return
    if (!Number.isInteger(value)) {
      problems.push(`${field} 必须为整数，实际 ${value}`)
      return
    }
    if (value < 0) {
      // 文案与后端 `GatePolicyRequest` 的 @Min 消息逐字一致，便于对照
      problems.push(
        field === 'maxReturnCount'
          ? '回退次数上限不得为负数（0 或留空 = 不限）'
          : '补件次数上限不得为负数（0 或留空 = 不限）',
      )
    } else if (value > FLOW_GATE_MAX_COUNT) {
      problems.push(`${field} 不得超过 ${FLOW_GATE_MAX_COUNT}（防空转），实际 ${value}`)
    }
  }
  checkCount('maxReturnCount', payload.maxReturnCount)
  checkCount('maxSupplementCount', payload.maxSupplementCount)

  const days = payload.supplementDeadlineDays
  if (days !== null && days !== undefined) {
    if (!Number.isInteger(days)) {
      problems.push(`supplementDeadlineDays 必须为整数，实际 ${days}`)
    } else if (days <= 0) {
      // 同上：文案取自后端 `@Min(value = 1, message = "补件时限天数必须 ≥1（不设时限请留空）")`
      problems.push('补件时限天数必须 ≥1（不设时限请留空）')
    } else if (days > FLOW_GATE_MAX_DEADLINE_DAYS) {
      problems.push(`supplementDeadlineDays 不得超过 ${FLOW_GATE_MAX_DEADLINE_DAYS} 天，实际 ${days}`)
    }
  }
  // 配了口径却没配天数：`doc/templates.md` §1.7 列为「提示项」，但服务端
  // `FlowGatePolicy.violations` 把它算作**阻断项**（会 40008 拒绝），因此这里按阻断处理。
  if ((days === null || days === undefined) && payload.supplementDeadlineType) {
    problems.push(
      `supplementDeadlineType=${payload.supplementDeadlineType} 已配置但 supplementDeadlineDays 为空 → ` +
        '不设时限，口径不生效（服务端会拒绝该组合）',
    )
  }
  // 撤回窗口（`doc/templates.md` §1.8）：前端只挡「既不是两个合法值、也不是留空」的脏值；
  // 服务端对非法枚举回 `40008`，文案含两个合法取值（见 `FlowDefinitionService#toGatePolicy`）。
  if (
    payload.withdrawWindow !== null &&
    payload.withdrawWindow !== undefined &&
    payload.withdrawWindow !== 'until_finance_approved' &&
    payload.withdrawWindow !== 'until_finance_started'
  ) {
    problems.push(
      `withdrawWindow 非法（until_finance_approved ②通过前，含②审批中 / until_finance_started ②开始前）：` +
        `${payload.withdrawWindow}`,
    )
  }
  return problems
}

/** 是否「次数不限且补件无时限」（镜像 `FlowGatePolicy.isUnlimited`） */
export function isGateUnlimited(payload: FlowGatePolicyPayload): boolean {
  return (
    normalizeGateCount(payload.maxReturnCount) === null &&
    normalizeGateCount(payload.maxSupplementCount) === null &&
    payload.supplementDeadlineDays === null
  )
}

/** 闸门配置的一句话摘要（右栏标题与列表页共用） */
export function describeGatePolicy(policy: FlowGatePolicy): string {
  const parts: string[] = []
  parts.push(policy.maxReturnCount === null ? '回退不限' : `回退 ≤${policy.maxReturnCount}`)
  parts.push(policy.maxSupplementCount === null ? '补件不限' : `补件 ≤${policy.maxSupplementCount}`)
  if (policy.supplementDeadlineDays === null) {
    parts.push('补件不设时限')
  } else {
    const type = policy.supplementDeadlineType
    parts.push(`补件时限 ${policy.supplementDeadlineDays} ${type ? FLOW_DEADLINE_TYPE_LABEL[type] : '工作日'}`)
  }
  parts.push(`超时${FLOW_TIMEOUT_ACTION_LABEL[policy.onSupplementTimeout]}`)
  // 撤回窗口（`doc/templates.md` §1.8）：摘要里带上口径，并标注「默认 / 已配置」，
  // 让管理员一眼看出该模板与默认行为是否一致（AC-16 严格口径必须显式可见）。
  parts.push(
    `撤回${FLOW_WITHDRAW_WINDOW_LABEL[policy.withdrawWindow]}` +
      (policy.withdrawWindowConfigured ? '' : '（默认）'),
  )
  return parts.join(' · ')
}

/**
 * 撤回窗口的一句话说明（设计器右栏「闸门配置」面板下方，与「在途实例按发起时版本」提示并列）。
 *
 * 依据 `doc/templates.md` §1.8：默认口径是 REQ-FLOW-009，严格口径是 AC-16。
 */
export function describeWithdrawWindow(policy: FlowGatePolicy): string {
  const base = FLOW_WITHDRAW_WINDOW_HINT[policy.withdrawWindow]
  return policy.withdrawWindowConfigured
    ? base
    : `${base}（当前**未显式配置**，数据库列为 NULL，按默认口径生效）`
}

// ================================================================ 节点换序（纯数组重排）

/** 换序计划（页面据此决定「能不能提交」与「提交什么」） */
export interface FlowReorderPlan {
  /** 重排后的节点数组（seq 已按新位置重写为 1..n） */
  nodes: FlowNode[]
  /** 提交给 `PUT /flow-templates/{id}/nodes/order` 的 `nodeIds`（目标顺序的全排列） */
  nodeIds: string[]
  /** 主干顺序是否被破坏（非空 = **禁止提交**，后端也会 40008 拒绝） */
  problems: string[]
}

/**
 * 把 `fromIndex` 处的元素移动到 `toIndex`（纯函数，不改入参）。
 *
 * @returns 新数组；索引越界时**原样返回浅拷贝**（不抛异常，避免拖拽边界把页面打崩）
 */
export function moveItem<T>(items: readonly T[], fromIndex: number, toIndex: number): T[] {
  const next = items.slice()
  if (fromIndex < 0 || fromIndex >= next.length) return next
  if (toIndex < 0 || toIndex >= next.length) return next
  if (fromIndex === toIndex) return next
  const [moved] = next.splice(fromIndex, 1)
  next.splice(toIndex, 0, moved)
  return next
}

/**
 * 节点顺序的**主干完整性校验**（镜像 `RequiredNodePolicy.violations`）。
 *
 * 判据（`doc/enums.md` §2 / `doc/templates.md` §1.0）：
 *   · 7 个主干节点必须齐备，且 `seq` 必须等于其固定序号；
 *   · 非主干节点不得占用 1–7 的主干序号。
 *
 * @param ordered 目标顺序（`seq` 按 `index + 1` 折算，与后端 `reorder` 的投影口径一致）
 */
export function checkNodeOrder(ordered: readonly FlowNode[]): string[] {
  const problems: string[] = []
  const seqOfCode = new Map<string, number>()
  ordered.forEach((node, index) => {
    if (!node.nodeCode) return
    seqOfCode.set(node.nodeCode.trim().toLowerCase(), index + 1)
  })
  for (const trunk of FLOW_TRUNK_NODES) {
    const actual = seqOfCode.get(trunk.code)
    if (actual === undefined) {
      problems.push(`缺少主干必填节点「${trunk.code}」（应为 seq=${trunk.seq}）`)
    } else if (actual !== trunk.seq) {
      problems.push(`主干节点「${trunk.code}」的 seq 必须为 ${trunk.seq}（主干顺序固定），实际 ${actual}`)
    }
  }
  for (const [code, seq] of seqOfCode) {
    if (!isTrunkNodeCode(code) && seq <= FLOW_TRUNK_NODE_COUNT) {
      problems.push(`非主干节点「${code}」占用了主干序号 ${seq}（主干 1–${FLOW_TRUNK_NODE_COUNT} 必须保留给 7 个主干节点）`)
    }
  }
  return problems
}

/**
 * 生成换序计划：把 `fromIndex` 拖到 `toIndex` 后的新顺序、待提交的 id 全排列与主干校验结论。
 *
 * 页面用法：`problems` 非空 → 复原拖拽并提示；为空 → 调 `reorderFlowNodes`。
 */
export function planReorder(nodes: readonly FlowNode[], fromIndex: number, toIndex: number): FlowReorderPlan {
  const moved = moveItem(nodes, fromIndex, toIndex)
  const renumbered = moved.map((node, index) => ({ ...node, seq: index + 1 }))
  return {
    nodes: renumbered,
    nodeIds: renumbered.map((node) => node.nodeId),
    problems: checkNodeOrder(renumbered),
  }
}

// ================================================================ 自由 JSON（参数 / 跳过条件）

/** JSON 文本的解析结果（`ok=false` 时给出可直接展示的错误文案） */
export interface FlowJsonParseResult {
  ok: boolean
  value: FlowJsonValue
  error: string | null
}

/**
 * 解析设计器里的 JSON 文本（`approverParam` / `skipCondition` 的文本框）。
 *
 * 约定：空白文本 = `null`（清空该配置，与后端 PUT 的「传 null 即清空」语义一致）。
 */
export function parseJsonText(text: string | null | undefined): FlowJsonParseResult {
  const raw = (text ?? '').trim()
  if (raw === '') return { ok: true, value: null, error: null }
  try {
    return { ok: true, value: JSON.parse(raw) as FlowJsonValue, error: null }
  } catch (error) {
    return { ok: false, value: null, error: `JSON 语法错误：${(error as Error).message}` }
  }
}

/** 把自由 JSON 值渲染成可编辑文本（`null` → 空串，页面显示占位符） */
export function formatJsonValue(value: FlowJsonValue): string {
  if (value === null || value === undefined) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return ''
  }
}

/** 时间字符串原样展示（后端已按 `yyyy-MM-dd HH:mm:ss` 下发），空值显示「—」 */
export function formatFlowTime(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : '—'
}
