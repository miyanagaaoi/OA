<script setup lang="ts">
/**
 * oa-web · 流程设计器（2a.2 `FlowDefinitionController`）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/templates.md` §1（四类模板 × 7 节点）、§1.7（Q6/Q7 闸门五键）、§3.3（状态机）、
 *     §4.1（六步变更流程）、§3.2 V-01…V-08（版本累积 / 在途锁版本）
 *   · `doc/enums.md` §2（主干 7 节点码）
 *   · `doc/prd-0.1.md` §6.4 REQ-FLOW-006 / AC-09（**在途实例按发起时版本运行**）、§7
 *   · `normify-oa/modules/oa/workflow/definition/**`
 *
 * 版面：左（节点序列，可拖拽换序）｜中（节点配置，`FlowNodeConfigPanel`）｜右（模板级：
 * 闸门配置 Q6/Q7 + 发布前检查 12 条 + 版本历史）。
 *
 * 三条界面纪律：
 *   1. **只读优于可点**：`published` / `archived` 版本后端一律 40906 拒绝写入，
 *      因此这些版本的**所有写入口都不渲染**，并在页顶说明「修改需先开新版本」；
 *   2. **错误码如实呈现**：所有写操作 catch 后走 `reportApiErrorWithCode`
 *      （40008 主干不可删 / 40906 已发布只读 / 40907 草稿已存在 / 40001 参数校验…），
 *      并且本域已在 `api/flow.ts` 关闭拦截器 toast，避免同一错误弹两次；
 *   3. **在途锁版本必须显式说明**：页顶常驻提示「在途实例仍按发起时版本运行」
 *      （REQ-FLOW-006 / AC-09），避免管理员以为改模板会影响在办单据。
 */
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import FlowNodeConfigPanel from './FlowNodeConfigPanel.vue'
import { useUserStore } from '@/stores/user'
import { canPublishFlowTemplate, canWriteFlowNode } from '@/utils/admin'
import {
  addFlowNode,
  archiveFlowTemplate,
  createFlowTemplateVersion,
  deleteFlowNode,
  getFlowTemplate,
  getFlowTemplateVersion,
  latestPrePublishCheck,
  listApproverRules,
  listCheckRules,
  listFlowTemplateVersions,
  prePublishCheck,
  publishFlowTemplate,
  putFlowGatePolicy,
  reorderFlowNodes,
  updateFlowTemplate,
} from '@/api/flow'
import { reportApiErrorWithCode } from '@/utils/feedback'
import {
  FLOW_CHECK_STATUS_LABEL,
  FLOW_DEADLINE_TYPE_LABEL,
  FLOW_DEADLINE_TYPE_OPTIONS,
  FLOW_DECISION_MODE_LABEL,
  FLOW_DECISION_MODE_OPTIONS,
  FLOW_FORM_TYPE_LABEL,
  FLOW_NODE_TYPE_LABEL,
  FLOW_SIGN_POLICY_LABEL,
  FLOW_SIGN_POLICY_OPTIONS,
  FLOW_STATUS_LABEL,
  FLOW_TIMEOUT_ACTION_LABEL,
  FLOW_TIMEOUT_ACTION_OPTIONS,
  FLOW_TRUNK_NODES,
  FLOW_TRUNK_NODE_COUNT,
  FLOW_WITHDRAW_WINDOW_HINT,
  FLOW_WITHDRAW_WINDOW_LABEL,
  FLOW_WITHDRAW_WINDOW_OPTIONS,
  checkGatePolicy,
  checkRuleRank,
  describeGatePolicy,
  formatFlowTime,
  isTrunkNodeCode,
  nodeDeleteDisabledReason,
  nodeSummary,
  normalizeGateCount,
  planReorder,
} from '@/utils/flow'
import type {
  FlowApproverRuleItem,
  FlowCheckRule,
  FlowCheckStatus,
  FlowDecisionMode,
  FlowDeadlineType,
  FlowGatePolicyPayload,
  FlowNode,
  FlowNodeType,
  FlowPrePublishReport,
  FlowSignPolicy,
  FlowTemplate,
  FlowTemplateDetail,
  FlowTimeoutAction,
  FlowWithdrawWindow,
} from '@/types/flow'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const templateId = computed(() => String(route.params.templateId ?? ''))

/** 写节点配置（决议/策略/跳过条件/解析规则/换序/增删）：`admin:flow:node` */
const canWriteNode = computed(() => canWriteFlowNode(userStore))
/** 开新版本 / 发布 / 归档 / 写闸门配置：`admin:flow:publish` */
const canPublish = computed(() => canPublishFlowTemplate(userStore))

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------
const loading = ref(false)
const loadError = ref('')
/**
 * 模板与节点用 `shallowRef`：二者都是「整块替换」的数据（每次写操作后由接口返回的新对象覆盖），
 * 不需要深层响应式；同时避免 `Ref<UnwrapRefSimple<FlowNode>[]>` 的深层类型实例化
 * （`vue-tsc` 会报 TS2589）。
 */
const template = shallowRef<FlowTemplate | null>(null)
const nodes = shallowRef<FlowNode[]>([])
const selectedNodeId = ref('')

const selectedNode = computed(() => nodes.value.find((node) => node.nodeId === selectedNodeId.value) ?? null)

/** 模板只读（published / archived）——后端对一切写入回 40906 */
const templateReadOnly = computed(() => template.value?.readOnly ?? true)
/** 节点可编辑 = 模板可写 且 有节点写权限 */
const editableNodes = computed(() => !templateReadOnly.value && canWriteNode.value)

const approverRules = ref<FlowApproverRuleItem[]>([])
const approverRulesError = ref('')

const actionLoading = reactive({ newVersion: false, publish: false, archive: false, order: false })

// ---------------------------------------------------------------------------
// 模板元数据（PUT /flow-templates/{id}；仅草稿可写）
// ---------------------------------------------------------------------------
const templateName = ref('')
const savingMeta = ref(false)

/**
 * 保存模板名称：`PUT /flow-templates/{id}`（`TemplateUpdateRequest`）。
 *
 * <p>只发 `name`：`form_schema_json`（表单模板）属 2a.6 范围外，闸门配置走独立接口
 * （`PUT .../gate-policy`），避免同一次提交里混入两个口径。非草稿 → 服务端 40906。
 */
async function saveMeta(): Promise<void> {
  const name = templateName.value.trim()
  if (name === '') {
    ElMessage({ type: 'warning', message: '模板名称不能为空（≤80 字）' })
    return
  }
  if (name.length > 80) {
    ElMessage({ type: 'warning', message: '模板名称不得超过 80 字符（服务端 @Size 校验）' })
    return
  }
  savingMeta.value = true
  try {
    const updated = await updateFlowTemplate(templateId.value, { name })
    applyTemplate(updated)
    ElMessage({ type: 'success', message: '模板名称已保存' })
    await Promise.all([loadLatestCheck(), loadVersions()])
  } catch (error) {
    reportApiErrorWithCode(error, '保存模板名称')
  } finally {
    savingMeta.value = false
  }
}

// ---------------------------------------------------------------------------
// 加载
// ---------------------------------------------------------------------------
function applyTemplate(next: FlowTemplate): void {
  template.value = next
  templateName.value = next.name
  Object.assign(gate, {
    maxReturnCount: next.gatePolicy.maxReturnCount,
    maxSupplementCount: next.gatePolicy.maxSupplementCount,
    supplementDeadlineDays: next.gatePolicy.supplementDeadlineDays,
    supplementDeadlineType: next.gatePolicy.supplementDeadlineType,
    onSupplementTimeout: next.gatePolicy.onSupplementTimeout,
    // 撤回窗口（doc/templates.md §1.8）：列 NULL（未显式配置）时映射为 `null`（而不是把生效值写回），
    // 以保留「留空 = 取默认口径」的三态语义 —— 否则一次保存就会把历史模板「升级」成已配置。
    withdrawWindow: next.gatePolicy.withdrawWindowConfigured ? next.gatePolicy.withdrawWindow : null,
  })
}

async function loadDetail(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const detail = await getFlowTemplate(templateId.value)
    applyTemplate(detail.template)
    nodes.value = detail.nodes
    if (!nodes.value.some((node) => node.nodeId === selectedNodeId.value)) {
      selectedNodeId.value = nodes.value[0]?.nodeId ?? ''
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '模板详情加载失败'
    reportApiErrorWithCode(error, '加载流程模板')
  } finally {
    loading.value = false
  }
}

async function loadVersions(): Promise<void> {
  try {
    versions.value = await listFlowTemplateVersions(templateId.value)
  } catch (error) {
    versionsError.value = error instanceof Error ? error.message : '版本历史加载失败'
  }
}

async function loadCheckRules(): Promise<void> {
  try {
    checkRules.value = await listCheckRules()
  } catch {
    // 规则清单只是「标题字典」，失败不影响逐条结论渲染（报告自带 title）
    checkRules.value = []
  }
}

async function loadLatestCheck(): Promise<void> {
  try {
    checkReport.value = await latestPrePublishCheck(templateId.value)
  } catch {
    checkReport.value = null
  }
}

/** 解析规则清单：后端要求 `admin:flow:node`，无权限时如实报错而不是静默空列表 */
async function loadApproverRules(): Promise<void> {
  if (!canWriteNode.value) {
    approverRulesError.value = '当前账号没有 admin:flow:node 权限，无法读取 9 条解析规则清单'
    return
  }
  try {
    approverRules.value = await listApproverRules()
    approverRulesError.value = ''
  } catch (error) {
    approverRulesError.value = error instanceof Error ? error.message : '解析规则清单加载失败'
    approverRules.value = []
  }
}

async function loadAll(): Promise<void> {
  await loadDetail()
  await Promise.all([loadVersions(), loadCheckRules(), loadApproverRules(), loadLatestCheck()])
}

onMounted(() => {
  void loadAll()
})

// 同一组件实例内切换模板时（/admin/flow/template/1 → /2）重置选中与报告
watch(templateId, (next, previous) => {
  if (!next || next === previous) return
  selectedNodeId.value = ''
  checkReport.value = null
  versions.value = []
  versionsError.value = ''
  void loadAll()
})

// ---------------------------------------------------------------------------
// 模板级：开新草稿 / 发布 / 归档
// ---------------------------------------------------------------------------

/** 「开新草稿」：`POST /flow-templates/{id}/versions`（已有草稿 → 40907） */
async function newVersion(): Promise<void> {
  if (!template.value) return
  try {
    await ElMessageBox.confirm(
      `基于 v${template.value.version} 开一个新草稿版本（version ${template.value.version + 1}）？` +
        '新草稿不会影响任何在途单据；同一单据类型同时只能存在一个草稿（否则服务端返回 40907）。',
      '开新草稿版本',
      { confirmButtonText: '开新草稿', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actionLoading.newVersion = true
  try {
    const draft = await createFlowTemplateVersion(templateId.value, { fromVersion: template.value.version })
    ElMessage({ type: 'success', message: `已创建草稿 v${draft.version}（${draft.code}），即将打开它` })
    await router.push(`/admin/flow/template/${draft.templateId}`)
  } catch (error) {
    reportApiErrorWithCode(error, '开新草稿版本')
  } finally {
    actionLoading.newVersion = false
  }
}

/**
 * 发布：`POST /flow-templates/{id}/publish`。
 *
 * 服务端会在同一事务内**重新**跑发布前校验（不信任页面上的缓存结论），
 * 不通过即 40008；发布成功后原 `published` 版本自动转 `archived`，**在途实例不受影响**。
 */
async function publish(): Promise<void> {
  if (!template.value) return
  if (!checkReport.value?.passed) {
    ElMessage({ type: 'warning', message: '发布前检查未通过：请先修复下方 fail 项（服务端同样会拒绝发布）' })
    return
  }
  try {
    await ElMessageBox.confirm(
      `发布「${template.value.name}」v${template.value.version}？` +
        '发布后该版本转为只读，同单据类型的原已发布版本自动归档（archived）；' +
        '**在途实例仍按发起时锁定的版本执行，不受影响**（REQ-FLOW-006 / AC-09）。',
      '确认发布',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  actionLoading.publish = true
  try {
    const published = await publishFlowTemplate(templateId.value, '管理后台流程设计器发布')
    ElMessage({ type: 'success', message: `已发布 v${published.version}（原已发布版本已转归档）` })
    await loadAll()
  } catch (error) {
    reportApiErrorWithCode(error, '发布流程模板')
  } finally {
    actionLoading.publish = false
  }
}

/**
 * 归档：`POST /flow-templates/{id}/archive`（只阻止**新**实例；在途继续执行 V-05）。
 *
 * <p>⚠ curl 实测：该接口**不做** `assertEditable`——对一个 `published` 版本归档会**直接成功（200）**，
 * 而其他写接口都会回 40906。若该 `code` 下没有别的 `published` 版本，「新单据将无法发起」
 * （pre-publish-check 的「模板已发布版本」口径 + 发起预检都会拦）。因此这里必须把后果说清楚。
 */
async function archive(): Promise<void> {
  if (!template.value) return
  const otherPublished = versions.value.filter(
    (item) => item.status === 'published' && item.templateId !== template.value?.templateId,
  ).length
  const draft = versions.value.find((item) => item.status === 'draft')
  const risk =
    template.value.status === 'published' && otherPublished === 0
      ? `\n\n⚠ 严重：这是单据类型「${template.value.code}」当前**唯一**的已发布版本，归档后**新单据将无法发起**` +
        (draft ? `；请先发布草稿 v${draft.version}。` : '；请先「开新草稿」并发布一个新版本。')
      : ''
  try {
    await ElMessageBox.confirm(
      `归档「${template.value.name}」v${template.value.version}？` +
        '归档后不再有**新**单据使用该版本，但在途实例继续按原版本执行（templates.md V-05）。' +
        '注意：归档接口**不会**返回 40906（服务端未做「只读」检查），而是直接生效，无法撤销。' +
        risk,
      '确认归档',
      { confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  actionLoading.archive = true
  try {
    await archiveFlowTemplate(templateId.value, '管理后台流程设计器归档')
    ElMessage({ type: 'success', message: '已归档（在途实例继续执行）' })
    await loadAll()
  } catch (error) {
    reportApiErrorWithCode(error, '归档流程模板')
  } finally {
    actionLoading.archive = false
  }
}

// ---------------------------------------------------------------------------
// 左栏：节点序列（拖拽换序 / 删除 / 新增）
// ---------------------------------------------------------------------------
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

/**
 * 拖拽换序（原生 HTML5 DnD，不引入排序库）。
 *
 * 落库前先用 `planReorder`（镜像后端 `RequiredNodePolicy`）校验：主干 7 节点顺序固定，
 * 把非主干节点拖进 1–7 会被后端 40008 拒绝——前端**先拦住**并说明原因，不浪费一次往返。
 */
function onDragStart(index: number, event: DragEvent): void {
  if (!editableNodes.value) {
    event.preventDefault()
    return
  }
  dragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(index: number): void {
  if (dragIndex.value === null) return
  dragOverIndex.value = index
}

function onDragEnd(): void {
  dragIndex.value = null
  dragOverIndex.value = null
}

async function onDrop(index: number): Promise<void> {
  const from = dragIndex.value
  onDragEnd()
  if (from === null || from === index || !editableNodes.value) return
  const plan = planReorder(nodes.value, from, index)
  if (plan.problems.length) {
    ElMessage({
      type: 'warning',
      message: `换序被本页拦截（服务端同样会以 40008 拒绝）：${plan.problems[0]}`,
      duration: 6000,
      showClose: true,
    })
    return
  }
  actionLoading.order = true
  try {
    nodes.value = await reorderFlowNodes(templateId.value, plan.nodeIds)
    ElMessage({ type: 'success', message: '节点顺序已保存' })
    await Promise.all([loadLatestCheck(), loadVersions()])
  } catch (error) {
    reportApiErrorWithCode(error, '调整节点顺序')
    await loadDetail()
  } finally {
    actionLoading.order = false
  }
}

/** 删除节点（主干必填节点按钮禁用并说明原因；服务端仍会 40008 兜底） */
async function removeNode(node: FlowNode): Promise<void> {
  const reason = nodeDeleteDisabledReason(node)
  if (reason) {
    ElMessage({ type: 'warning', message: reason, duration: 6000, showClose: true })
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除节点「${node.nodeCode}（${node.name ?? '未命名'}）」？删除后 seq 会自动重排为 1..n。`,
      '确认删除节点',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteFlowNode(node.nodeId)
    ElMessage({ type: 'success', message: '节点已删除' })
    selectedNodeId.value = ''
    await loadAll()
  } catch (error) {
    reportApiErrorWithCode(error, '删除节点')
  }
}

// 新增非主干节点（POST /flow-templates/{id}/nodes；主干 7 节点已存在，不可重复）
const addDialog = reactive({
  visible: false,
  submitting: false,
  nodeCode: '',
  name: '',
  nodeType: 'approve' as FlowNodeType,
  approverRule: '',
  decisionMode: 'any' as FlowDecisionMode,
  signPolicy: 'optional' as FlowSignPolicy,
  timeoutHours: 24 as number | null,
})

function openAdd(): void {
  Object.assign(addDialog, {
    visible: true,
    submitting: false,
    nodeCode: '',
    name: '',
    nodeType: 'approve' as FlowNodeType,
    // 默认取第一条「可用于主干节点」的规则，避免用户选到 collab_dept_leader 被服务端拒绝
    approverRule: approverRules.value.find((item) => item.trunkUsable)?.rule ?? '',
    decisionMode: 'any' as FlowDecisionMode,
    signPolicy: 'optional' as FlowSignPolicy,
    timeoutHours: 24,
  })
}

const addDialogError = computed(() => {
  if (!/^[a-z][a-z0-9_]{1,31}$/.test(addDialog.nodeCode.trim())) {
    return 'node_code 必须小写字母开头、2–32 位（字母/数字/下划线）'
  }
  if (isTrunkNodeCode(addDialog.nodeCode)) {
    return '该节点码属于主干必填节点（已存在，服务端会因重复而拒绝）'
  }
  if (!addDialog.approverRule) {
    return '必须选择审批人解析规则（enums.md §3 共 9 条）'
  }
  return ''
})

async function submitAdd(): Promise<void> {
  if (addDialogError.value) {
    ElMessage({ type: 'warning', message: addDialogError.value })
    return
  }
  addDialog.submitting = true
  try {
    const created = await addFlowNode(templateId.value, {
      // ⚠ 必须显式给 seq：服务端 `addNode` 先做 `NodeDefinitionValidator.assertNode`，
      // 而 `applyRequest` 只对**主干节点码**补默认 seq；非主干节点传 null 会被判
      // 「seq 必须为正整数」→ 40008（已用 curl 实测）。因此取「当前节点数 + 1」追加到主干之后。
      seq: nodes.value.length + 1,
      nodeCode: addDialog.nodeCode.trim(),
      name: addDialog.name.trim() === '' ? null : addDialog.name.trim(),
      nodeType: addDialog.nodeType,
      approverRule: addDialog.approverRule,
      approverParam: null,
      // 归档登记节点不适用决议模式（B-01）；其余节点必须给出 decision_mode
      decisionMode: addDialog.nodeType === 'archive' ? null : addDialog.decisionMode,
      passThreshold: null,
      signPolicy: addDialog.nodeType === 'archive' ? 'none' : addDialog.signPolicy,
      timeoutHours: addDialog.timeoutHours,
      timeoutCcSuperior: false,
      allowAddSign: true,
      allowJump: false,
      allowRoute: false,
      skipCondition: null,
    })
    addDialog.visible = false
    ElMessage({ type: 'success', message: `节点 ${created.nodeCode} 已新增（seq ${created.seq}）` })
    await loadAll()
    selectedNodeId.value = created.nodeId
  } catch (error) {
    reportApiErrorWithCode(error, '新增节点')
  } finally {
    addDialog.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 右栏 · 闸门配置（Q6/Q7，模板级；PUT /flow-templates/{id}/gate-policy）
// ---------------------------------------------------------------------------
const gate = reactive<FlowGatePolicyPayload>({
  maxReturnCount: null,
  maxSupplementCount: null,
  supplementDeadlineDays: null,
  supplementDeadlineType: null,
  onSupplementTimeout: 'notify',
  withdrawWindow: null,
})

/** 归一后的提交体（`0` → `null` = 不限，与后端 `FlowGatePolicy.normalizeCount` 同口径） */
const gatePayload = computed<FlowGatePolicyPayload>(() => ({
  maxReturnCount: normalizeGateCount(gate.maxReturnCount),
  maxSupplementCount: normalizeGateCount(gate.maxSupplementCount),
  supplementDeadlineDays: gate.supplementDeadlineDays ?? null,
  supplementDeadlineType:
    gate.supplementDeadlineDays === null ? null : (gate.supplementDeadlineType ?? null),
  onSupplementTimeout: gate.onSupplementTimeout,
  // 留空 = 取默认口径（后端落 NULL；`until_finance_approved` 亦与之语义等价）
  withdrawWindow: gate.withdrawWindow ?? null,
}))

/** 生效的撤回窗口口径（留空 = 默认 REQ-FLOW-009 口径），用于面板回显与提示 */
const effectiveWithdrawWindow = computed<FlowWithdrawWindow>(
  () => gate.withdrawWindow ?? 'until_finance_approved',
)
/** 是否显式配置过（`false` = 数据库列为 NULL，按默认口径生效） */
const withdrawWindowConfigured = computed(() => gate.withdrawWindow !== null)
/** 面板下方的口径说明（两句 + 未配置时的显式标注） */
const withdrawWindowHint = computed(
  () =>
    FLOW_WITHDRAW_WINDOW_HINT[effectiveWithdrawWindow.value] +
    (withdrawWindowConfigured.value
      ? ''
      : '（当前未显式配置 → 按默认口径生效，与既有单据行为完全一致）'),
)

const gateProblems = computed(() => checkGatePolicy(gatePayload.value))
const gateUnlimited = computed(
  () =>
    gatePayload.value.maxReturnCount === null &&
    gatePayload.value.maxSupplementCount === null &&
    gatePayload.value.supplementDeadlineDays === null,
)
const savingGate = ref(false)

/** 「0 或留空 = 不限」的即时提示（不阻断保存） */
const gateUnlimitedHints = computed(() => {
  const hints: string[] = []
  if (gate.maxReturnCount === 0 || gate.maxReturnCount === null) hints.push('回退次数不限')
  if (gate.maxSupplementCount === 0 || gate.maxSupplementCount === null) hints.push('补件次数不限')
  if (gate.supplementDeadlineDays === null) hints.push('补件不设时限')
  return hints
})

function fillV04Defaults(): void {
  Object.assign(gate, {
    maxReturnCount: 5,
    maxSupplementCount: 3,
    supplementDeadlineDays: 3,
    supplementDeadlineType: 'working' as FlowDeadlineType,
    onSupplementTimeout: 'notify' as FlowTimeoutAction,
    // V0.4 默认 = REQ-FLOW-009 口径 = 留空取默认（`null` 落库为 NULL，不写显式值）
    withdrawWindow: null,
  })
}

function fillUnlimited(): void {
  Object.assign(gate, {
    maxReturnCount: null,
    maxSupplementCount: null,
    supplementDeadlineDays: null,
    supplementDeadlineType: null,
    onSupplementTimeout: 'notify' as FlowTimeoutAction,
    withdrawWindow: null,
  })
}

async function saveGate(): Promise<void> {
  if (gateProblems.value.length) {
    ElMessage({ type: 'warning', message: gateProblems.value[0], duration: 6000, showClose: true })
    return
  }
  savingGate.value = true
  try {
    const updated = await putFlowGatePolicy(templateId.value, gatePayload.value)
    applyTemplate(updated)
    ElMessage({ type: 'success', message: 'Q6/Q7 闸门配置已保存' })
    await Promise.all([loadLatestCheck(), loadVersions()])
  } catch (error) {
    reportApiErrorWithCode(error, '保存闸门配置')
  } finally {
    savingGate.value = false
  }
}

// ---------------------------------------------------------------------------
// 右栏 · 发布前检查（POST /flow-designs/{id}/pre-publish-check）
// ---------------------------------------------------------------------------
const checkReport = ref<FlowPrePublishReport | null>(null)
const checking = ref(false)
const checkRules = ref<FlowCheckRule[]>([])

/**
 * 12 条规则的逐条展示行。
 *
 * 合并两个来源：`GET /flow-designs/check-rules`（权威 12 条清单）与报告里的 `checks[]`
 * （后者的顺序后端不保证，用 `checkRuleRank` 按权威顺序重排）。
 * 后端将来新增规则也能显示（报告里有、清单里没有的规则追加在后面）。
 */
interface CheckRow {
  rule: string
  title: string
  status: FlowCheckStatus | null
  details: string[]
}

const checkRows = computed<CheckRow[]>(() => {
  const report = checkReport.value
  const byRule = new Map(report?.checks.map((item) => [item.rule, item]) ?? [])
  const rows: CheckRow[] = checkRules.value.map((rule) => {
    const hit = byRule.get(rule.rule)
    return {
      rule: rule.rule,
      title: hit?.title || rule.title,
      status: hit?.status ?? null,
      details: hit?.details ?? [],
    }
  })
  for (const item of report?.checks ?? []) {
    if (!rows.some((row) => row.rule === item.rule)) {
      rows.push({ rule: item.rule, title: item.title, status: item.status, details: item.details })
    }
  }
  return rows.sort((left, right) => checkRuleRank(left.rule) - checkRuleRank(right.rule))
})

const failedChecks = computed(() => (checkReport.value?.checks ?? []).filter((item) => item.status === 'fail'))

/** 发布按钮不可用的原因（有 fail 时禁用并说明） */
const publishBlockedReason = computed(() => {
  if (!template.value) return '模板未加载'
  if (template.value.status !== 'draft') return `当前状态为「${FLOW_STATUS_LABEL[template.value.status]}」，只有草稿可发布`
  if (!canPublish.value) return '当前账号没有 admin:flow:publish 权限，发布入口不渲染'
  if (!checkReport.value) return '尚未运行发布前检查：请先点「运行发布前检查」'
  if (!checkReport.value.passed) {
    return `发布前检查未通过（${failedChecks.value.length} 条 fail）：服务端在发布时会再次校验并返回 40008`
  }
  return ''
})

async function runCheck(notify = true): Promise<void> {
  checking.value = true
  try {
    checkReport.value = await prePublishCheck(templateId.value)
    if (notify) {
      ElMessage({
        type: checkReport.value.passed ? 'success' : 'warning',
        message: checkReport.value.passed
          ? '发布前检查全部通过（服务端发布时会再校验一次）'
          : `发布前检查未通过：${checkReport.value.problems.length} 个问题`,
      })
    }
  } catch (error) {
    reportApiErrorWithCode(error, '运行发布前检查')
  } finally {
    checking.value = false
  }
}

// ---------------------------------------------------------------------------
// 右栏 · 版本历史（GET /versions、GET /versions/{version}）
// ---------------------------------------------------------------------------
const versions = ref<FlowTemplate[]>([])
const versionsError = ref('')

const versionDialog = reactive({
  visible: false,
  loading: false,
  error: '',
  version: 0,
})

/**
 * 历史版本详情用 `shallowRef`：整块替换、无需深层响应式，
 * 且避免 `reactive` 对 `FlowTemplateDetail` 做 UnwrapRef 时的深层类型实例化（TS2589）。
 */
const versionDetail = shallowRef<FlowTemplateDetail | null>(null)

/** 按版本查询（**只读**）：已发布/已归档版本没有任何写入口（后端 40906） */
async function openVersion(version: number): Promise<void> {
  versionDialog.visible = true
  versionDialog.loading = true
  versionDialog.error = ''
  versionDialog.version = version
  versionDetail.value = null
  try {
    versionDetail.value = await getFlowTemplateVersion(templateId.value, version)
  } catch (error) {
    versionDialog.error = error instanceof Error ? error.message : '版本详情加载失败'
    reportApiErrorWithCode(error, `加载 v${version} 详情`)
  } finally {
    versionDialog.loading = false
  }
}

/** 基于历史版本开新草稿（回滚口径：把旧版本重新发布为新版本，不改历史行，templates.md V-01） */
async function newVersionFrom(version: number): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `基于 v${version} 开新草稿？历史版本行不会被修改（templates.md V-01：回滚 = 把旧版本重新发布为新版本）。`,
      '基于历史版本开新草稿',
      { confirmButtonText: '开新草稿', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const draft = await createFlowTemplateVersion(templateId.value, { fromVersion: version })
    versionDialog.visible = false
    ElMessage({ type: 'success', message: `已创建草稿 v${draft.version}` })
    await router.push(`/admin/flow/template/${draft.templateId}`)
  } catch (error) {
    reportApiErrorWithCode(error, '基于历史版本开新草稿')
  }
}

// ---------------------------------------------------------------------------
// 节点面板保存后的刷新（节点视图、发布前检查、版本列表的 updatedAt 都可能变）
// ---------------------------------------------------------------------------
async function onNodeSaved(saved: FlowNode): Promise<void> {
  nodes.value = nodes.value.map((node) => (node.nodeId === saved.nodeId ? saved : node))
  selectedNodeId.value = saved.nodeId
  await loadLatestCheck()
}

/** 左侧列表里主干节点的固定 seq（用于展示 ①–⑦ 与「主干」标记） */
function nodeGlyph(seq: number): string {
  const glyphs = ['①', '②', '③', '④', '⑤', '⑥', '⑦']
  return seq >= 1 && seq <= glyphs.length ? glyphs[seq - 1] : String(seq)
}

// el-table 作用域插槽的 row 是 any 形状；进出业务函数前统一收窄（与 RoleListView 同口径，不用 any）
function asFlowNode(row: unknown): FlowNode {
  return row as FlowNode
}

function decisionModeText(row: unknown): string {
  const node = asFlowNode(row)
  return node.decisionMode ? FLOW_DECISION_MODE_LABEL[node.decisionMode] : '登记（无决议）'
}

function signPolicyText(row: unknown): string {
  const node = asFlowNode(row)
  return node.signPolicy ? FLOW_SIGN_POLICY_LABEL[node.signPolicy] : '—'
}

function timeoutText(row: unknown): string {
  const node = asFlowNode(row)
  return node.timeoutHours === null ? '—' : `${node.timeoutHours}h`
}
</script>

<template>
  <div class="oa-flow-designer" v-loading="loading">
    <!-- ==================== 页头 ==================== -->
    <header class="page-head">
      <el-button link type="primary" @click="router.push('/admin/flow/template')">← 模板列表</el-button>
      <template v-if="template">
        <h1 class="oa-text-title-page">
          <span class="oa-mono">{{ template.code }}</span>
          <span class="name">{{ template.name }}</span>
        </h1>
        <span class="oa-text-caption oa-text-subtle">
          {{ FLOW_FORM_TYPE_LABEL[template.formType] }} · v{{ template.version }} · 节点
          <b class="oa-tnum">{{ template.nodeCount }}</b> 个
        </span>
        <StatusPill kind="flow-status" :status="template.status" />
        <span v-if="templateReadOnly" class="oa-tag is-warning">只读（后端写入一律 40906）</span>
        <span v-else class="oa-tag is-info">草稿：可编辑</span>
      </template>

      <div class="head-actions">
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button
          v-if="canPublish && template && template.status !== 'archived'"
          :loading="actionLoading.newVersion"
          @click="newVersion"
        >
          开新草稿
        </el-button>
        <el-button
          v-if="canPublish && template && template.status === 'draft'"
          type="primary"
          :loading="actionLoading.publish"
          :disabled="!checkReport?.passed"
          :title="publishBlockedReason"
          @click="publish"
        >
          发布
        </el-button>
        <el-button
          v-if="canPublish && template && template.status !== 'archived'"
          :loading="actionLoading.archive"
          @click="archive"
        >
          归档
        </el-button>
      </div>
    </header>

    <!-- ==================== 在途锁版本提示（REQ-FLOW-006 / AC-09，常驻不可关闭） ==================== -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="修改需先开新版本；在途实例仍按发起时版本运行（REQ-FLOW-006 / AC-09）"
      description="模板按 (code, version) 累积、不覆盖历史（V-01）；实例在**发起时**锁定 template_version，其剩余节点全部按该版本执行（V-02），节点配置也在发起时冻结（V-07）。因此改模板不会影响任何在办单据——如需变更请先「开新草稿」，改完再发布（原已发布版本自动转归档）。"
    />

    <el-alert
      v-if="templateReadOnly"
      type="warning"
      :closable="false"
      show-icon
      title="当前版本只读：所有写入口已隐藏，服务端对任何写入返回 40906（FLOW_DEFINITION_IMMUTABLE）"
      description="已发布（published）/ 已归档（archived）版本按 templates.md §3.3 与 §4.3 禁止直接修改（会让在途单据不可复现）。要改配置请点右上角「开新草稿」。"
    />

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <p v-if="!canWriteNode && template" class="oa-text-caption oa-text-subtle">
      当前账号没有 <code>admin:flow:node</code>（节点配置）权限：节点写入口不渲染，仍可只读浏览节点、
      版本历史与发布前检查结论；服务端同样会 403 拒绝越权请求。
    </p>

    <!-- ==================== 三栏主体 ==================== -->
    <div v-if="template" class="layout">
      <!-- ---------- 左：节点序列（拖拽换序） ---------- -->
      <section class="oa-card column column-left">
        <header class="col-head">
          <h2 class="oa-text-title-section">节点序列</h2>
          <el-button v-if="editableNodes" size="small" type="primary" @click="openAdd">新增节点</el-button>
        </header>

        <p class="oa-text-caption oa-text-subtle tip">
          主干 {{ FLOW_TRUNK_NODE_COUNT }} 个节点（①–⑦）顺序固定（doc/enums.md §2）；
          换序只对非主干节点生效，把非主干节点拖进 1–7 会被服务端 40008 拒绝。
          <template v-if="editableNodes">拖动卡片即可换序。</template>
        </p>

        <ul class="node-list" :class="{ 'is-readonly': !editableNodes }">
          <li
            v-for="(node, index) in nodes"
            :key="node.nodeId"
            class="node-item"
            :class="{
              'is-active': node.nodeId === selectedNodeId,
              'is-dragging': dragIndex === index,
              'is-drop-target': dragOverIndex === index && dragIndex !== null && dragIndex !== index,
            }"
            :draggable="editableNodes"
            @click="selectedNodeId = node.nodeId"
            @dragstart="onDragStart(index, $event)"
            @dragover.prevent="onDragOver(index)"
            @drop.prevent="onDrop(index)"
            @dragend="onDragEnd"
          >
            <span class="grip" :class="{ 'is-disabled': !editableNodes }" :title="editableNodes ? '拖动换序' : '只读版本不可换序'">⋮⋮</span>
            <span class="seq oa-tnum">{{ nodeGlyph(node.seq) }}</span>
            <span class="node-body">
              <b class="oa-mono">{{ node.nodeCode }}</b>
              <i>{{ node.name || '未命名' }}</i>
              <span class="oa-text-caption oa-text-subtle">{{ nodeSummary(node) }}</span>
            </span>
            <span class="oa-tag" :class="{ 'is-info': node.decisionMode === 'all' }">
              {{ node.decisionMode ? FLOW_DECISION_MODE_LABEL[node.decisionMode] : '登记' }}
            </span>
            <el-button
              v-if="editableNodes"
              link
              type="danger"
              size="small"
              :disabled="isTrunkNodeCode(node.nodeCode)"
              :title="nodeDeleteDisabledReason(node) ?? '删除该非主干节点'"
              @click.stop="removeNode(node)"
            >
              删除
            </el-button>
            <span
              v-else-if="isTrunkNodeCode(node.nodeCode)"
              class="oa-text-caption oa-text-subtle"
              title="主干必填节点不可删除"
            >
              主干
            </span>
          </li>
          <li v-if="!nodes.length" class="oa-empty">该模板暂无节点</li>
        </ul>
      </section>

      <!-- ---------- 中：节点配置 ---------- -->
      <section class="column column-middle">
        <FlowNodeConfigPanel
          v-if="selectedNode"
          :key="selectedNode.nodeId"
          :node="selectedNode"
          :template-id="templateId"
          :template-read-only="templateReadOnly"
          :can-write-node="canWriteNode"
          :approver-rules="approverRules"
          :approver-rules-error="approverRulesError"
          @saved="onNodeSaved"
        />
        <div v-else class="oa-card oa-empty">请选择左侧节点进行配置</div>
      </section>

      <!-- ---------- 右：模板级配置 / 发布前检查 / 版本历史 ---------- -->
      <section class="column column-right">
        <!-- 模板元数据（PUT /flow-templates/{id}，仅草稿可写） -->
        <div class="oa-card block">
          <header class="col-head">
            <h2 class="oa-text-title-section">模板元数据</h2>
            <span class="oa-text-caption oa-text-subtle">仅草稿可写</span>
          </header>
          <el-form label-position="top" class="form">
            <el-form-item label="模板名称 name（≤80 字）">
              <el-input
                v-model="templateName"
                :disabled="!canPublish || templateReadOnly"
                maxlength="80"
                show-word-limit
              />
              <p class="hint">
                key 是 <span class="oa-mono">(code, version)</span>：
                <span class="oa-mono">{{ template.code }}</span> v{{ template.version }} 与
                <code>form_type</code> 一律不可改（templates.md V-08 版本号单调递增）；
                表单定义（<code>form_schema_json</code>）与闸门配置各有独立入口。
              </p>
            </el-form-item>
          </el-form>
          <div class="actions">
            <el-button
              v-if="canPublish && !templateReadOnly"
              type="primary"
              size="small"
              :loading="savingMeta"
              @click="saveMeta"
            >
              保存模板名称
            </el-button>
            <span v-else class="oa-text-caption oa-text-subtle">
              {{ templateReadOnly ? '只读版本不可改名（40906）' : '无 admin:flow:publish 权限' }}
            </span>
          </div>
        </div>

        <!-- 闸门配置（Q6/Q7） -->
        <div class="oa-card block">
          <header class="col-head">
            <h2 class="oa-text-title-section">闸门配置（Q6 / Q7）</h2>
            <span class="oa-text-caption oa-text-subtle">模板级</span>
          </header>
          <p class="oa-text-caption oa-text-subtle">
            落点：<code>flow_template</code> 的 5 个可空列（doc/templates.md §1.7 / data-model.md §4.1）。
            <b>0 或留空 = 不限</b>；天数取值 1..365（0 与负数一律拒绝，不设时限请留空）。
          </p>

          <el-form label-position="top" class="form">
            <el-form-item label="回退次数上限 max_return_count">
              <el-input-number
                v-model="gate.maxReturnCount"
                :min="0"
                :max="99"
                :disabled="!canPublish || templateReadOnly"
                controls-position="right"
              />
              <span class="oa-text-caption oa-text-subtle unit">0 / 留空 = 不限（1..99）</span>
            </el-form-item>
            <el-form-item label="补件次数上限 max_supplement_count">
              <el-input-number
                v-model="gate.maxSupplementCount"
                :min="0"
                :max="99"
                :disabled="!canPublish || templateReadOnly"
                controls-position="right"
              />
              <span class="oa-text-caption oa-text-subtle unit">0 / 留空 = 不限（1..99）</span>
            </el-form-item>
            <el-form-item label="补件时限天数 supplement_deadline_days">
              <el-input-number
                v-model="gate.supplementDeadlineDays"
                :disabled="!canPublish || templateReadOnly"
                controls-position="right"
              />
              <span class="oa-text-caption oa-text-subtle unit">留空 = 不设时限；1..365</span>
              <p class="hint">
                服务端同样会拦：<code>补件时限天数必须 ≥1（不设时限请留空）</code>（40001）。
              </p>
            </el-form-item>
            <el-form-item label="补件时限口径 supplement_deadline_type">
              <el-select
                v-model="gate.supplementDeadlineType"
                class="fill"
                clearable
                :disabled="!canPublish || templateReadOnly || gate.supplementDeadlineDays === null"
                placeholder="留空 = 按工作日"
              >
                <el-option
                  v-for="item in FLOW_DEADLINE_TYPE_OPTIONS"
                  :key="item"
                  :value="item"
                  :label="`${FLOW_DEADLINE_TYPE_LABEL[item]}（${item}）`"
                />
              </el-select>
              <p class="hint">给了天数但未给口径时按**工作日**（V0.4 定稿）；配了口径却没配天数会被服务端拒绝。</p>
            </el-form-item>
            <el-form-item label="补件超时处理 on_supplement_timeout">
              <el-select v-model="gate.onSupplementTimeout" class="fill" :disabled="!canPublish || templateReadOnly">
                <el-option
                  v-for="item in FLOW_TIMEOUT_ACTION_OPTIONS"
                  :key="item"
                  :value="item"
                  :label="`${FLOW_TIMEOUT_ACTION_LABEL[item]}（${item}）`"
                />
              </el-select>
              <p class="hint">默认「仅提醒」，与 V0.4「超时仅催办」逐字一致（默认行为不变）。</p>
            </el-form-item>
            <el-form-item label="撤回窗口 withdraw_window">
              <el-select
                v-model="gate.withdrawWindow"
                class="fill"
                clearable
                :disabled="!canPublish || templateReadOnly"
                placeholder="留空 = 取默认口径 until_finance_approved"
              >
                <el-option
                  v-for="item in FLOW_WITHDRAW_WINDOW_OPTIONS"
                  :key="item"
                  :value="item"
                  :label="`${FLOW_WITHDRAW_WINDOW_LABEL[item]}（${item}）`"
                />
              </el-select>
              <p class="hint">{{ withdrawWindowHint }}</p>
              <p class="hint">
                生效口径：
                <code>{{ effectiveWithdrawWindow }}</code>
                <span v-if="!withdrawWindowConfigured">（未显式配置 → 数据库中该列为 NULL）</span>
                <span v-else>（已显式配置）</span>
              </p>
              <p class="hint">
                变更只影响**之后发起**的新单据；**在途实例按发起时锁定的模板版本**判定（AC-09 / templates.md V-02），
                本面板的修改不会改变它们。
              </p>
            </el-form-item>
          </el-form>

          <div class="gate-state">
            <span class="oa-text-caption oa-text-subtle">
              当前：{{ template ? describeGatePolicy(template.gatePolicy) : '—' }}
            </span>
            <span v-if="gateUnlimited" class="oa-tag is-warning">提交后为「不限」口径</span>
            <span v-else-if="gateUnlimitedHints.length" class="oa-tag">
              {{ gateUnlimitedHints.join(' · ') }}
            </span>
          </div>

          <ul v-if="gateProblems.length" class="problems">
            <li v-for="(item, index) in gateProblems" :key="`g-${index}`">{{ item }}</li>
          </ul>

          <div class="actions">
            <el-button size="small" :disabled="!canPublish || templateReadOnly" @click="fillV04Defaults">
              V0.4 默认值（5 / 3 / 3 工作日 / notify）
            </el-button>
            <el-button size="small" :disabled="!canPublish || templateReadOnly" @click="fillUnlimited">全部不限</el-button>
            <el-button
              v-if="canPublish && !templateReadOnly"
              type="primary"
              size="small"
              :loading="savingGate"
              :disabled="gateProblems.length > 0"
              @click="saveGate"
            >
              保存闸门配置
            </el-button>
          </div>
          <p v-if="!canPublish" class="oa-text-caption oa-text-subtle">
            当前账号没有 <code>admin:flow:publish</code> 权限：闸门写入入口不渲染。
          </p>
        </div>

        <!-- 发布前检查 -->
        <div class="oa-card block">
          <header class="col-head">
            <h2 class="oa-text-title-section">发布前检查</h2>
            <el-button size="small" :loading="checking" @click="runCheck()">运行发布前检查</el-button>
          </header>
          <p class="oa-text-caption oa-text-subtle">
            POST /flow-designs/{{ templateId }}/pre-publish-check：**dry-run，不落库**；
            12 条规则逐条给出 pass / fail。发布时服务端会在同一事务内**再跑一遍**（不信任本页结论）。
          </p>

          <div v-if="checkReport" class="check-head">
            <span class="oa-pill" :class="checkReport.passed ? 'is-approved' : 'is-rejected'">
              {{ checkReport.passed ? '全部通过' : `${failedChecks.length} 条不通过` }}
            </span>
            <span class="oa-text-caption oa-text-subtle">
              生成于 {{ formatFlowTime(checkReport.generatedAt) }} · v{{ checkReport.version }} ·
              {{ FLOW_STATUS_LABEL[checkReport.status] }}
            </span>
          </div>
          <p v-else class="oa-text-caption oa-text-subtle">尚未获取检查结论：点上方「运行发布前检查」。</p>

          <ul class="check-list">
            <li v-for="row in checkRows" :key="row.rule" class="check-item">
              <span class="oa-pill" :class="row.status === 'pass' ? 'is-approved' : row.status === 'fail' ? 'is-rejected' : row.status === 'warn' ? 'is-pending' : ''">
                {{ row.status ? FLOW_CHECK_STATUS_LABEL[row.status] : '未检查' }}
              </span>
              <span class="rule oa-mono">{{ row.rule }}</span>
              <span class="title">{{ row.title }}</span>
              <ul v-if="row.details.length" class="details">
                <li v-for="(detail, index) in row.details" :key="`d-${index}`">{{ detail }}</li>
              </ul>
            </li>
          </ul>

          <div v-if="checkReport?.problems.length" class="problems-block">
            <b>阻断发布的问题（{{ checkReport.problems.length }}）</b>
            <ul class="problems">
              <li v-for="(item, index) in checkReport.problems" :key="`p-${index}`">{{ item }}</li>
            </ul>
          </div>
          <div v-if="checkReport?.warnings.length" class="warnings-block">
            <b>提示项（不阻止发布）</b>
            <ul class="warnings">
              <li v-for="(item, index) in checkReport.warnings" :key="`w-${index}`">{{ item }}</li>
            </ul>
          </div>

          <p v-if="publishBlockedReason" class="publish-note">{{ publishBlockedReason }}</p>
        </div>

        <!-- 版本历史 -->
        <div class="oa-card block">
          <header class="col-head">
            <h2 class="oa-text-title-section">版本历史</h2>
            <span class="oa-text-caption oa-text-subtle">(code, version) 累积、不覆盖（V-01）</span>
          </header>
          <el-alert v-if="versionsError" type="error" :closable="false" show-icon :title="versionsError" />
          <el-table :data="versions" size="small" border>
            <el-table-column label="版本" width="70">
              <template #default="{ row }">v{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <StatusPill kind="flow-status" :status="row.status" />
              </template>
            </el-table-column>
            <el-table-column label="节点" width="60" align="right">
              <template #default="{ row }">
                <span class="oa-tnum">{{ row.nodeCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="发布时间" min-width="150">
              <template #default="{ row }">
                <span class="oa-text-caption">{{ formatFlowTime(row.publishedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openVersion(row.version)">只读查看</el-button>
                <el-button
                  v-if="canPublish && row.status !== 'draft'"
                  link
                  type="primary"
                  size="small"
                  @click="newVersionFrom(row.version)"
                >
                  开草稿
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="oa-empty">暂无版本</div>
            </template>
          </el-table>
          <p class="oa-text-caption oa-text-subtle">
            「只读查看」按版本查询（GET /flow-templates/{id}/versions/{version}）；已发布 / 已归档版本
            **没有**任何写入口——服务端对它们的写入一律回 40906（UI 不提供白点的按钮）。
          </p>
        </div>
      </section>
    </div>

    <!-- ==================== 新增节点 ==================== -->
    <el-dialog v-model="addDialog.visible" title="新增节点（非主干）" width="620px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="节点码 node_code" required>
          <el-input v-model="addDialog.nodeCode" class="oa-mono" maxlength="32" placeholder="小写蛇形，如 extra_cc" />
          <p class="hint">主干 7 个节点码已存在，重复会被服务端拒绝（40008）。</p>
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="addDialog.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="节点类型 node_type" required>
          <el-select v-model="addDialog.nodeType" class="fill">
            <el-option value="approve" :label="FLOW_NODE_TYPE_LABEL.approve" />
            <el-option value="cc" :label="FLOW_NODE_TYPE_LABEL.cc" />
            <el-option value="archive" :label="FLOW_NODE_TYPE_LABEL.archive" />
          </el-select>
          <p class="hint">condition 条件节点属二期，一期服务端一律拒绝。</p>
        </el-form-item>
        <el-form-item label="审批人解析规则 approver_rule" required>
          <el-select v-model="addDialog.approverRule" class="fill" filterable>
            <el-option
              v-for="item in approverRules"
              :key="item.rule"
              :value="item.rule"
              :label="`${item.label}（${item.rule}）`"
              :disabled="!item.trunkUsable"
            />
          </el-select>
          <p class="hint">只列出可用于节点的规则；<code>collab_dept_leader</code> 仅用于②的并行子任务。</p>
        </el-form-item>
        <el-form-item v-if="addDialog.nodeType !== 'archive'" label="决议模式 decision_mode" required>
          <el-select v-model="addDialog.decisionMode" class="fill">
            <el-option
              v-for="item in FLOW_DECISION_MODE_OPTIONS"
              :key="item"
              :value="item"
              :label="`${FLOW_DECISION_MODE_LABEL[item]}（${item}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="addDialog.nodeType !== 'archive'" label="签名策略 sign_policy" required>
          <el-select v-model="addDialog.signPolicy" class="fill">
            <el-option
              v-for="item in FLOW_SIGN_POLICY_OPTIONS"
              :key="item"
              :value="item"
              :label="`${FLOW_SIGN_POLICY_LABEL[item]}（${item}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="超时时长 timeout_hours（≥24）">
          <el-input-number v-model="addDialog.timeoutHours" :min="0" :max="8760" controls-position="right" />
        </el-form-item>
        <p v-if="addDialogError" class="hint error">{{ addDialogError }}</p>
      </el-form>
      <template #footer>
        <el-button @click="addDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="addDialog.submitting"
          :disabled="addDialogError !== ''"
          @click="submitAdd"
        >
          确认新增
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 历史版本只读查看 ==================== -->
    <el-dialog
      v-model="versionDialog.visible"
      :title="`v${versionDialog.version} · 只读快照`"
      width="880px"
      append-to-body
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="只读：该版本按版本号查询返回，界面不提供任何编辑入口（服务端对已发布/已归档版本的写入一律 40906）"
        description="如需基于它改配置，请用「基于此版本开新草稿」——历史行不会被修改（templates.md V-01）。"
      />
      <div v-loading="versionDialog.loading" class="version-body">
        <el-alert v-if="versionDialog.error" type="error" :closable="false" show-icon :title="versionDialog.error" />
        <template v-if="versionDetail">
          <p class="oa-text-body-sm">
            <span class="oa-mono">{{ versionDetail.template.code }}</span>
            {{ versionDetail.template.name }} ·
            <StatusPill kind="flow-status" :status="versionDetail.template.status" />
            · 节点 {{ versionDetail.template.nodeCount }} 个 · 发布时间
            {{ formatFlowTime(versionDetail.template.publishedAt) }}
          </p>
          <p class="oa-text-caption oa-text-subtle">
            闸门配置：{{ describeGatePolicy(versionDetail.template.gatePolicy) }}
          </p>
          <el-table :data="versionDetail.nodes" size="small" border>
            <el-table-column label="seq" width="60">
              <template #default="{ row }">{{ row.seq }}</template>
            </el-table-column>
            <el-table-column label="节点码" min-width="150">
              <template #default="{ row }">
                <span class="oa-mono">{{ row.nodeCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="名称" min-width="130">
              <template #default="{ row }">{{ row.name || '—' }}</template>
            </el-table-column>
            <el-table-column label="决议模式" width="100">
              <template #default="{ row }">
                {{ decisionModeText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="阈值" width="90">
              <template #default="{ row }">
                <span class="oa-mono">{{ asFlowNode(row).passThreshold ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="签名" width="90">
              <template #default="{ row }">
                {{ signPolicyText(row) }}
              </template>
            </el-table-column>
            <el-table-column label="超时" width="80">
              <template #default="{ row }">
                <span class="oa-tnum">{{ timeoutText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="解析规则" min-width="150">
              <template #default="{ row }">
                <span class="oa-mono">{{ row.approverRule || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="versionDialog.visible = false">关闭</el-button>
        <el-button
          v-if="canPublish && versionDetail && versionDetail.template.status !== 'draft'"
          type="primary"
          @click="newVersionFrom(versionDialog.version)"
        >
          基于此版本开新草稿
        </el-button>
      </template>
    </el-dialog>

    <!-- 主干节点固定序号的说明（文档对照用，便于验收核对） -->
    <p class="oa-text-caption oa-text-subtle trunk-note">
      主干 7 节点（doc/enums.md §2）：
      <span v-for="item in FLOW_TRUNK_NODES" :key="item.code">
        {{ item.seq }}.{{ item.label }}（<span class="oa-mono">{{ item.code }}</span>）
      </span>
    </p>
  </div>
</template>

<style scoped>
.oa-flow-designer {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.page-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
}

.page-head h1 {
  display: inline-flex;
  align-items: baseline;
  gap: var(--oa-space-xs);
  color: var(--oa-color-ink);
}

.head-actions {
  margin-left: auto;
  display: flex;
  gap: var(--oa-space-xs);
}

.layout {
  display: grid;
  grid-template-columns: minmax(250px, 300px) minmax(0, 1fr) minmax(320px, 400px);
  gap: var(--oa-space-md);
  align-items: start;
}

.column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.column-left,
.column-right .block {
  padding: var(--oa-space-sm) var(--oa-space-md);
}

.column-left {
  position: sticky;
  top: 0;
}

.col-head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

.col-head h2 {
  color: var(--oa-color-ink);
}

.col-head > :last-child {
  margin-left: auto;
}

.tip {
  display: block;
  line-height: 1.6;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xxs);
  margin-top: var(--oa-space-xs);
}

.node-item {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-xs);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas);
  cursor: pointer;
}

.node-item:hover {
  border-color: var(--oa-color-primary-border);
}

.node-item.is-active {
  border-color: var(--oa-color-primary);
  background: var(--oa-color-primary-subtle);
}

.node-item.is-dragging {
  opacity: 0.5;
}

.node-item.is-drop-target {
  border-style: dashed;
  border-color: var(--oa-color-primary);
}

.node-list.is-readonly .node-item {
  cursor: default;
}

.grip {
  flex: none;
  color: var(--oa-color-ink-disabled);
  cursor: grab;
  letter-spacing: -2px;
}

.grip.is-disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.seq {
  flex: none;
  width: 20px;
  text-align: center;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.node-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1 1 auto;
}

.node-body b {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.node-body i {
  font: var(--oa-font-body-sm);
  font-style: normal;
  color: var(--oa-color-ink-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-body .oa-text-caption {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-right .block {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
}

.form :deep(.el-form-item) {
  margin-bottom: var(--oa-space-xs);
}

.fill {
  width: 100%;
}

.unit {
  margin-left: var(--oa-space-xs);
}

.hint {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  line-height: 1.6;
  color: var(--oa-color-ink-subtle);
}

.hint.error {
  color: var(--oa-color-error);
}

.gate-state {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  flex-wrap: wrap;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
}

.problems,
.warnings,
.details {
  padding-left: var(--oa-space-md);
  list-style: disc;
  font: var(--oa-font-caption);
  line-height: 1.7;
}

.problems {
  color: var(--oa-color-error);
}

.warnings {
  color: var(--oa-color-warning);
}

.check-head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  flex-wrap: wrap;
}

.check-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 320px;
  overflow: auto;
}

.check-item {
  display: grid;
  grid-template-columns: 62px 116px minmax(0, 1fr);
  gap: var(--oa-space-xs);
  align-items: baseline;
  padding: 3px 0;
  border-bottom: 1px dashed var(--oa-color-hairline);
}

.check-item .rule {
  font: var(--oa-font-mono);
  font-size: 12px;
  color: var(--oa-color-ink-muted);
}

.check-item .title {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink);
}

.check-item .details {
  grid-column: 1 / -1;
}

.problems-block b {
  color: var(--oa-color-error);
  font: var(--oa-font-label);
}

.warnings-block b {
  color: var(--oa-color-warning);
  font: var(--oa-font-label);
}

.publish-note {
  padding: 6px var(--oa-space-xs);
  border-left: 2px solid var(--oa-color-warning);
  background: var(--oa-color-warning-surface);
  border-radius: var(--oa-radius-xs);
  font: var(--oa-font-caption);
  color: var(--oa-color-warning);
}

.version-body {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  margin-top: var(--oa-space-xs);
  min-height: 80px;
}

.trunk-note {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-sm);
}

code {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-mono);
}

@media (max-width: 1440px) {
  .layout {
    grid-template-columns: minmax(230px, 280px) minmax(0, 1fr);
  }

  .column-right {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1024px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .column-left {
    position: static;
  }
}
</style>
