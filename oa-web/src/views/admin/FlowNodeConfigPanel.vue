<script setup lang="ts">
/**
 * oa-web · 流程设计器 · 节点配置面板（中栏）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/templates.md` §1（四类模板 × 7 节点配置表：决议模式 / 阈值 / 签名策略 / 超时 /
 *     加签 / 跳转 / 流转 / 跳过条件）、§0 T-07（阈值口径）、§0 T-08（自由跳转默认关闭）、
 *     §1.7（Q6/Q7 属**模板级**，不在本面板）
 *   · `doc/enums.md` §3（9 条审批人解析规则）、§7（决议模式 / 签名策略）
 *   · `doc/prd-0.1.md` §5.4（决议模式与「配置约束」）
 *
 * 后端调用（逐项对应一个 PUT，失败展示**后端业务码 + 文案**）：
 *   · 决议模式与阈值      PUT /flow-nodes/{nodeId}/decision-模式与阈值写入
 *   · 阈值即时解析（只读）POST /flow-nodes/{nodeId}/decision/resolve?candidateCount=N
 *   · 签名/超时/开关      PUT /flow-nodes/{nodeId}/policy（另有 policy/validate 即时校验）
 *   · 跳过条件            PUT /flow-nodes/{nodeId}/skip-condition（另有 validate）
 *   · 审批人解析规则      PUT /flow-nodes/{nodeId}/approver-rule（另有 validate 与
 *                        POST /approver-rules/{ruleCode}/resolve 候选人预览）
 *   · 节点基础信息        PUT /flow-nodes/{nodeId}（全量更新：名称 / 节点类型）
 *
 * 两条界面纪律：
 *   1. **只读优于可点**：模板为 published/archived（后端一切写入回 40906）或无
 *      `admin:flow:node` 时，所有保存按钮不渲染，字段置灰并说明原因——不让用户白点；
 *   2. **提示不能代替裁决**：本地校验（`utils/flow.ts`）只为少一次往返，真正的裁决与
 *      错误码一律来自后端（本面板只如实转述）。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  putFlowNodeApproverRule,
  putFlowNodeDecision,
  putFlowNodePolicy,
  putFlowNodeSkipCondition,
  resolveApproverRule,
  resolveFlowNodeDecision,
  updateFlowNode,
  validateFlowNodeApproverRule,
  validateFlowNodePolicy,
  validateFlowNodeSkipCondition,
} from '@/api/flow'
import { reportApiErrorWithCode } from '@/utils/feedback'
import {
  FLOW_DECISION_MODE_LABEL,
  FLOW_DECISION_MODE_OPTIONS,
  FLOW_NODE_TYPE_LABEL,
  FLOW_SIGN_POLICY_LABEL,
  FLOW_SIGN_POLICY_OPTIONS,
  FLOW_THRESHOLD_BASIS_LABEL,
  checkThresholdLiteral,
  composeThresholdLiteral,
  formatJsonValue,
  isTrunkNodeCode,
  parseJsonText,
} from '@/utils/flow'
import type {
  FlowApproverRuleItem,
  FlowDecisionMode,
  FlowDecisionResolve,
  FlowJsonValue,
  FlowNode,
  FlowNodeCreatePayload,
  FlowNodeType,
  FlowSignPolicy,
} from '@/types/flow'

const props = defineProps<{
  /** 当前选中的节点（父组件按选中项传入；本组件不改 props，保存后由父组件整体刷新） */
  node: FlowNode
  /** 节点所属模板 id（解析规则「按真实节点配置解析」需要） */
  templateId: string
  /** 模板是否只读（published/archived → 后端 40906 拒绝一切写入） */
  templateReadOnly: boolean
  /** 是否持有 `admin:flow:node`（写权限） */
  canWriteNode: boolean
  /** 9 条解析规则（父组件在持有 admin:flow:node 时拉取；失败时给 error） */
  approverRules: FlowApproverRuleItem[]
  /** 解析规则清单的加载错误（如 403：该接口要求 admin:flow:node） */
  approverRulesError: string
}>()

const emit = defineEmits<{ (event: 'saved', node: FlowNode): void }>()

/** 是否可编辑：模板可写 **且** 持有节点写权限 */
const editable = computed(() => !props.templateReadOnly && props.canWriteNode)

/** 不可编辑的原因（用于面板顶部说明，避免「按钮为什么不见了」） */
const readonlyReason = computed(() => {
  if (props.templateReadOnly) {
    return '当前模板版本为已发布 / 已归档（只读）：后端对任何节点写入一律返回 40906，需先「开新草稿」再改。'
  }
  if (!props.canWriteNode) {
    return '当前账号没有 admin:flow:node（节点配置）权限：保存入口不渲染；服务端同样会 403 拒绝。'
  }
  return ''
})

/** ⑦ 归档登记：登记节点无决议（doc/templates.md §0 B-01） */
const isArchiveNode = computed(() => props.node.nodeType === 'archive')

// ---------------------------------------------------------------------------
// 保存进度（每个分区一个，互不干扰）
// ---------------------------------------------------------------------------
const saving = reactive({
  decision: false,
  policy: false,
  skip: false,
  rule: false,
  base: false,
})

// ---------------------------------------------------------------------------
// 决议模式与阈值（PUT /flow-nodes/{id}/decision）；阈值口径见 templates.md T-07
// ---------------------------------------------------------------------------
type ThresholdKind = 'none' | 'absolute' | 'percent'

const decision = reactive({
  mode: 'any' as FlowDecisionMode,
  thresholdKind: 'none' as ThresholdKind,
  absolute: 2 as number,
  percent: 60 as number,
})

/** 阈值字面量（「绝对人数优先」由 `composeThresholdLiteral` 承担，与后端同口径） */
const decisionLiteral = computed(() =>
  composeThresholdLiteral(
    decision.thresholdKind === 'absolute' ? decision.absolute : null,
    decision.thresholdKind === 'percent' ? decision.percent : null,
  ),
)
const decisionLiteralError = computed(() => checkThresholdLiteral(decisionLiteral.value))

/**
 * 「决议模式 × 阈值」的非法组合前置拦截（与后端 `ThresholdPolicy.violations` 同口径）。
 *
 * <p>服务端只在「会签（all）」下接受 `pass_threshold`：或签 / 依次审批下带着阈值会被 40008 拒绝
 * （curl 实测：`pass_threshold 仅在「会签（all）」下参与判定，当前决议模式为 any，该阈值不会生效`）。
 * 因此这里在点保存前就说明白，并把「改回不配置」这条出路写在提示里。
 */
const decisionModeConflict = computed(() => {
  if (decision.mode === 'all' || decision.thresholdKind === 'none') return null
  return (
    `pass_threshold 仅在「会签（all）」下参与判定，当前决议模式为 ${decision.mode}，该阈值不会生效：` +
    '请改为「会签」，或把阈值切到「不配置（过半）」——切到不配置会**清空**已落库的阈值（服务端 40008，curl 实测）。'
  )
})

/** 阈值即时解析（POST .../decision/resolve，**纯计算不落库**） */
const resolveCandidateCount = ref(3)
const resolveResult = ref<FlowDecisionResolve | null>(null)
const resolving = ref(false)

function syncFromNode(node: FlowNode): void {
  decision.mode = node.decisionMode ?? 'any'
  if (node.passThreshold === null) {
    decision.thresholdKind = 'none'
  } else if (node.passThreshold.trim().endsWith('%')) {
    decision.thresholdKind = 'percent'
    decision.percent = Number.parseInt(node.passThreshold.trim().slice(0, -1), 10) || 60
  } else {
    decision.thresholdKind = 'absolute'
    decision.absolute = Number.parseInt(node.passThreshold.trim(), 10) || 2
  }
  policy.signPolicy = node.signPolicy ?? 'optional'
  policy.timeoutHours = node.timeoutHours
  policy.timeoutCcSuperior = node.timeoutCcSuperior
  policy.allowAddSign = node.allowAddSign
  policy.allowJump = node.allowJump
  policy.allowRoute = node.allowRoute
  skip.text = formatJsonValue(node.skipCondition)
  rule.approverRule = node.approverRule ?? ''
  rule.approverParamText = formatJsonValue(node.approverParam)
  base.name = node.name ?? ''
  base.nodeType = node.nodeType ?? 'approve'
  resolveResult.value = null
  ruleResolve.value = null
  validation.value = null
}

// ---------------------------------------------------------------------------
// 签名策略 / 超时 / 开关（PUT /flow-nodes/{id}/policy）
// ---------------------------------------------------------------------------
const policy = reactive({
  signPolicy: 'optional' as FlowSignPolicy,
  timeoutHours: 24 as number | null,
  timeoutCcSuperior: false,
  allowAddSign: true,
  allowJump: false,
  allowRoute: false,
})

// ---------------------------------------------------------------------------
// 跳过条件（PUT /flow-nodes/{id}/skip-condition）
// ---------------------------------------------------------------------------
const skip = reactive({ text: '' })

// ---------------------------------------------------------------------------
// 审批人解析规则（PUT /flow-nodes/{id}/approver-rule）
// ---------------------------------------------------------------------------
const rule = reactive({ approverRule: '', approverParamText: '' })

const ruleResolve = ref<{
  resolved: boolean
  evidence: string
  missingConfig: string[]
  candidates: { userId: string; name: string; orgName: string; position: string }[]
  source: string
} | null>(null)
const ruleResolving = ref(false)

/** 当前规则的元数据（是否必须给参数 / 是否可用于主干节点） */
const currentRuleMeta = computed(() => props.approverRules.find((item) => item.rule === rule.approverRule) ?? null)

// ---------------------------------------------------------------------------
// 节点基础信息（PUT /flow-nodes/{id} 全量更新）
// ---------------------------------------------------------------------------
const base = reactive({ name: '', nodeType: 'approve' as FlowNodeType })

/** 最近一次校验结论（policy / skip / rule 三个 validate 接口共用一块展示区） */
const validation = ref<{ source: string; passed: boolean; problems: string[]; warnings: string[] } | null>(null)

watch(
  () => props.node,
  (node) => syncFromNode(node),
  { immediate: true },
)

// ---------------------------------------------------------------------------
// 动作
// ---------------------------------------------------------------------------

/** 统一收尾：把后端**最新的那个节点视图**回给父组件（父组件据此刷新左栏与发布前检查） */
function afterSaved(node: FlowNode, message: string): void {
  ElMessage({ type: 'success', message })
  emit('saved', node)
}

async function saveDecision(): Promise<void> {
  if (decisionLiteralError.value) {
    ElMessage({ type: 'warning', message: decisionLiteralError.value })
    return
  }
  if (decisionModeConflict.value) {
    ElMessage({ type: 'warning', message: decisionModeConflict.value, duration: 6000, showClose: true })
    return
  }
  saving.decision = true
  try {
    const saved = await putFlowNodeDecision(props.node.nodeId, {
      decisionMode: decision.mode,
      // 「不配置」传 `null` 即清空 pass_threshold（与传空串同义，2026-10-04 后端三态统一）；
      // absolute / percent 由下面两个字段给出（T-07：绝对人数优先）。
      passThreshold: null,
      thresholdAbsolute: decision.thresholdKind === 'absolute' ? decision.absolute : null,
      thresholdPercent: decision.thresholdKind === 'percent' ? decision.percent : null,
    })
    afterSaved(saved, '决议模式与阈值已保存')
  } catch (error) {
    reportApiErrorWithCode(error, '保存决议模式与阈值')
  } finally {
    saving.decision = false
  }
}

async function runDecisionResolve(): Promise<void> {
  resolving.value = true
  try {
    resolveResult.value = await resolveFlowNodeDecision(props.node.nodeId, resolveCandidateCount.value)
    ElMessage({ type: 'success', message: '阈值解析完成（纯计算，未改动任何配置）' })
  } catch (error) {
    reportApiErrorWithCode(error, '解析阈值')
  } finally {
    resolving.value = false
  }
}

async function savePolicy(): Promise<void> {
  saving.policy = true
  try {
    const saved = await putFlowNodePolicy(props.node.nodeId, {
      signPolicy: policy.signPolicy,
      timeoutHours: policy.timeoutHours,
      timeoutCcSuperior: policy.timeoutCcSuperior,
      allowAddSign: policy.allowAddSign,
      allowJump: policy.allowJump,
      allowRoute: policy.allowRoute,
    })
    afterSaved(saved, '签名策略与超时配置已保存')
  } catch (error) {
    reportApiErrorWithCode(error, '保存签名策略与超时')
  } finally {
    saving.policy = false
  }
}

async function runPolicyValidate(): Promise<void> {
  try {
    const result = await validateFlowNodePolicy(props.node.nodeId, {
      signPolicy: policy.signPolicy,
      timeoutHours: policy.timeoutHours,
      timeoutCcSuperior: policy.timeoutCcSuperior,
      allowAddSign: policy.allowAddSign,
      allowJump: policy.allowJump,
      allowRoute: policy.allowRoute,
    })
    validation.value = { source: '签名策略 / 超时', ...result }
    ElMessage({ type: result.passed ? 'success' : 'warning', message: result.passed ? '校验通过（未落库）' : '校验未通过，见结论区' })
  } catch (error) {
    reportApiErrorWithCode(error, '校验签名策略与超时')
  }
}

async function saveSkipCondition(): Promise<void> {
  const parsed = parseJsonText(skip.text)
  if (!parsed.ok) {
    ElMessage({ type: 'warning', message: parsed.error ?? 'JSON 语法错误' })
    return
  }
  saving.skip = true
  try {
    const saved = await putFlowNodeSkipCondition(props.node.nodeId, parsed.value)
    afterSaved(saved, parsed.value === null ? '跳过条件已清空' : '跳过条件已保存')
  } catch (error) {
    reportApiErrorWithCode(error, '保存跳过条件')
  } finally {
    saving.skip = false
  }
}

async function runSkipValidate(): Promise<void> {
  const parsed = parseJsonText(skip.text)
  if (!parsed.ok) {
    ElMessage({ type: 'warning', message: parsed.error ?? 'JSON 语法错误' })
    return
  }
  try {
    const result = await validateFlowNodeSkipCondition(props.node.nodeId, parsed.value)
    validation.value = { source: '跳过条件', ...result }
    ElMessage({ type: result.passed ? 'success' : 'warning', message: result.passed ? '校验通过（未落库）' : '校验未通过，见结论区' })
  } catch (error) {
    reportApiErrorWithCode(error, '校验跳过条件')
  }
}

/** 解析规则入参（清空文本 = 传 null 清空 approver_param） */
function approverParamValue(): { ok: boolean; error: string | null; value: FlowJsonValue } {
  const parsed = parseJsonText(rule.approverParamText)
  return { ok: parsed.ok, error: parsed.error, value: parsed.value }
}

async function saveApproverRule(): Promise<void> {
  if (!rule.approverRule.trim()) {
    ElMessage({ type: 'warning', message: 'approver_rule 不能为空' })
    return
  }
  const param = approverParamValue()
  if (!param.ok) {
    ElMessage({ type: 'warning', message: param.error ?? 'approver_param 的 JSON 语法错误' })
    return
  }
  saving.rule = true
  try {
    const saved = await putFlowNodeApproverRule(props.node.nodeId, {
      approverRule: rule.approverRule.trim(),
      approverParam: param.value,
    })
    afterSaved(saved, '审批人解析规则已保存')
  } catch (error) {
    reportApiErrorWithCode(error, '保存审批人解析规则')
  } finally {
    saving.rule = false
  }
}

async function runApproverRuleValidate(): Promise<void> {
  const param = approverParamValue()
  if (!param.ok) {
    ElMessage({ type: 'warning', message: param.error ?? 'approver_param 的 JSON 语法错误' })
    return
  }
  try {
    const result = await validateFlowNodeApproverRule(props.node.nodeId, {
      approverRule: rule.approverRule.trim(),
      approverParam: param.value,
    })
    validation.value = { source: '审批人解析规则', ...result }
    ElMessage({ type: result.passed ? 'success' : 'warning', message: result.passed ? '校验通过（未落库）' : '校验未通过，见结论区' })
  } catch (error) {
    reportApiErrorWithCode(error, '校验审批人解析规则')
  }
}

/** 候选人预览：用**真实节点配置**（templateId + nodeSeq）解析「这条规则现在能不能取到人」 */
async function runApproverRuleResolve(): Promise<void> {
  if (!rule.approverRule.trim()) {
    ElMessage({ type: 'warning', message: '请先选择审批人解析规则' })
    return
  }
  ruleResolving.value = true
  try {
    const result = await resolveApproverRule(rule.approverRule.trim(), {
      templateId: props.templateId,
      nodeSeq: props.node.seq,
    })
    ruleResolve.value = {
      resolved: result.resolved,
      evidence: result.evidence,
      missingConfig: result.missingConfig,
      source: result.source,
      candidates: result.candidates.map((item) => ({
        userId: item.userId,
        name: item.name,
        orgName: item.orgName,
        position: item.position,
      })),
    }
  } catch (error) {
    reportApiErrorWithCode(error, '解析候选人')
  } finally {
    ruleResolving.value = false
  }
}

/**
 * 节点基础信息保存：**全量更新**（PUT /flow-nodes/{id}，PUT 语义 = 传 null 即清空）。
 *
 * <p>这里把当前节点的全部配置原样回传（只改名称 / 节点类型），避免全量语义把其它字段清空；
 * 主干节点的 `nodeCode` 与 `seq` 由本面板**原样回传**——服务端明令主干不可改码/改序（40008）。
 */
async function saveBase(): Promise<void> {
  const payload: FlowNodeCreatePayload = {
    seq: props.node.seq,
    nodeCode: props.node.nodeCode,
    name: base.name.trim() === '' ? null : base.name.trim(),
    nodeType: base.nodeType,
    approverRule: props.node.approverRule ?? '',
    approverParam: props.node.approverParam,
    decisionMode: props.node.decisionMode,
    passThreshold: props.node.passThreshold,
    signPolicy: props.node.signPolicy,
    timeoutHours: props.node.timeoutHours,
    timeoutCcSuperior: props.node.timeoutCcSuperior,
    allowAddSign: props.node.allowAddSign,
    allowJump: props.node.allowJump,
    allowRoute: props.node.allowRoute,
    skipCondition: props.node.skipCondition,
  }
  if (!payload.approverRule) {
    ElMessage({ type: 'warning', message: 'approver_rule 不能为空：请先在「审批人解析规则」分区配置后保存' })
    return
  }
  saving.base = true
  try {
    const saved = await updateFlowNode(props.node.nodeId, payload)
    afterSaved(saved, '节点基础信息已保存')
  } catch (error) {
    reportApiErrorWithCode(error, '保存节点基础信息')
  } finally {
    saving.base = false
  }
}
</script>

<template>
  <div class="oa-node-panel">
    <header class="panel-head">
      <h2 class="oa-text-title-section">
        <span class="oa-mono">{{ node.nodeCode }}</span>
        <span class="seq">seq {{ node.seq }}</span>
      </h2>
      <span class="oa-text-caption oa-text-subtle">
        {{ node.nodeCodeLabel || '非主干节点' }} · {{ node.name || '未命名' }}
        <template v-if="node.approverRuleLabel"> · {{ node.approverRuleLabel }}</template>
      </span>
      <p v-if="readonlyReason" class="readonly-note">{{ readonlyReason }}</p>
      <p v-else-if="isArchiveNode" class="readonly-note is-soft">
        ⑦ 归档登记为登记节点（doc/templates.md §0 T-03）：默认「仅登记不审批」，
        <code>decision_mode</code> / <code>pass_threshold</code> 为 NULL，不产生审批决议、不计入审批时长统计。
      </p>
    </header>

    <!-- ==================== 1. 节点基础信息（PUT /flow-nodes/{id} 全量更新） ==================== -->
    <section class="oa-card block">
      <h3>节点基础信息</h3>
      <el-form label-position="top" class="form">
        <el-form-item label="节点码 node_code">
          <el-input :model-value="node.nodeCode" class="oa-mono" disabled />
          <p class="hint">
            <template v-if="isTrunkNodeCode(node.nodeCode)">
              主干必填节点：码与 seq 固定不可改（doc/enums.md §2），服务端改码/改序一律 40008。
            </template>
            <template v-else>非主干节点：码一经使用不建议再改（服务端会查重）。</template>
          </p>
        </el-form-item>
        <el-form-item label="节点名称 name">
          <el-input v-model="base.name" :disabled="!editable" maxlength="50" show-word-limit placeholder="不超过 50 字" />
        </el-form-item>
        <el-form-item label="节点类型 node_type">
          <el-select v-model="base.nodeType" class="fill" :disabled="!editable">
            <el-option value="approve" :label="FLOW_NODE_TYPE_LABEL.approve" />
            <el-option value="cc" :label="FLOW_NODE_TYPE_LABEL.cc" />
            <el-option value="archive" :label="FLOW_NODE_TYPE_LABEL.archive" />
          </el-select>
          <p class="hint">condition 条件节点为二期预留，一期服务端一律拒绝（R-NODE-TYPE）。</p>
        </el-form-item>
      </el-form>
      <div v-if="editable" class="actions">
        <el-button type="primary" :loading="saving.base" @click="saveBase">保存基础信息（全量更新）</el-button>
      </div>
    </section>

    <!-- ==================== 2. 决议模式与阈值 ==================== -->
    <section class="oa-card block">
      <h3>决议模式与会签阈值</h3>
      <el-form label-position="top" class="form">
        <el-form-item label="决议模式 decision_mode">
          <el-radio-group v-model="decision.mode" :disabled="!editable || isArchiveNode">
            <el-radio-button v-for="mode in FLOW_DECISION_MODE_OPTIONS" :key="mode" :value="mode">
              {{ FLOW_DECISION_MODE_LABEL[mode] }}
            </el-radio-button>
          </el-radio-group>
          <p class="hint">
            <template v-if="isArchiveNode">
              ⑦ 归档登记无决议，<code>decision_mode</code> 为 NULL（B-01）；如需改为需审批请先在「节点类型」里改类型。
            </template>
            <template v-else>
              或签 = 任一人通过即节点通过（默认）；会签 = 同意人数达阈值；依次审批 = 按顺序串行、全部通过。
            </template>
          </p>
        </el-form-item>

        <el-form-item label="会签阈值（绝对人数优先 / 百分比）">
          <el-radio-group v-model="decision.thresholdKind" :disabled="!editable || isArchiveNode">
            <el-radio-button value="none">不配置（过半）</el-radio-button>
            <el-radio-button value="absolute">绝对人数</el-radio-button>
            <el-radio-button value="percent">百分比</el-radio-button>
          </el-radio-group>
          <div class="threshold-inputs">
            <el-input-number
              v-if="decision.thresholdKind === 'absolute'"
              v-model="decision.absolute"
              :min="1"
              :max="99"
              :disabled="!editable || isArchiveNode"
            />
            <el-input-number
              v-if="decision.thresholdKind === 'percent'"
              v-model="decision.percent"
              :min="1"
              :max="100"
              :disabled="!editable || isArchiveNode"
            />
            <span class="oa-text-caption oa-text-subtle">
              落库字面量 <code class="oa-mono">{{ decisionLiteral ?? 'null' }}</code>
            </span>
          </div>
          <p class="hint">
            口径（T-07）：绝对人数与百分比同时给出时**绝对人数优先**；百分比**向上取整**；
            留空 = 过半（<code>floor(候选人数/2)+1</code>）。当前：{{ node.thresholdDescription || '—' }}
          </p>
          <p v-if="decisionLiteralError" class="hint error">{{ decisionLiteralError }}</p>
          <p v-else-if="decisionModeConflict" class="hint error">{{ decisionModeConflict }}</p>
        </el-form-item>
      </el-form>
      <div v-if="editable" class="actions">
        <el-button
          type="primary"
          :loading="saving.decision"
          :disabled="!!decisionModeConflict || !!decisionLiteralError"
          @click="saveDecision"
        >
          保存决议模式与阈值
        </el-button>
        <span class="oa-text-caption oa-text-subtle">
          「不配置（过半）」会**清空**已落库的 <code>pass_threshold</code>——传 <code>null</code>（本页当前下发）
          与传空串同义（服务端三态，2026-10-04 统一）。
        </span>
      </div>

      <!-- 阈值即时解析（纯计算接口，不落库） -->
      <div class="sub-block">
        <p class="sub-title">阈值即时解析（按候选人数量，服务端纯计算）</p>
        <div class="resolve-row">
          <el-input-number v-model="resolveCandidateCount" :min="0" :max="99" />
          <el-button :loading="resolving" @click="runDecisionResolve">解析已保存的配置</el-button>
        </div>
        <div v-if="resolveResult" class="resolve-result">
          <p>
            <b>{{ FLOW_THRESHOLD_BASIS_LABEL[resolveResult.basis] }}</b>
            ｜候选人 <span class="oa-tnum">{{ resolveResult.candidateCount }}</span> 人 →
            需 <span class="oa-tnum">{{ resolveResult.requiredApprovals }}</span> 人同意
          </p>
          <p v-if="!resolveResult.satisfiable" class="hint error">
            ⚠ 阈值大于候选人数：该节点在当前候选人下**永远无法通过**，请下调阈值或补足候选人。
          </p>
          <p class="oa-text-caption oa-text-subtle">{{ resolveResult.description }}</p>
        </div>
      </div>
    </section>

    <!-- ==================== 3. 审批人解析规则 ==================== -->
    <section class="oa-card block">
      <h3>审批人解析规则</h3>
      <el-alert
        v-if="approverRulesError"
        type="error"
        :closable="false"
        show-icon
        :title="`解析规则清单（GET /approver-rules）加载失败：${approverRulesError}`"
        description="该接口在后端要求 admin:flow:node（节点配置）权限；无此权限时规则下拉不可用，但只读浏览节点仍然正常。"
      />
      <el-form label-position="top" class="form">
        <el-form-item label="解析规则 approver_rule（doc/enums.md §3 共 9 条）">
          <el-select
            v-model="rule.approverRule"
            class="fill"
            filterable
            :disabled="!editable"
            :loading="!approverRules.length && !approverRulesError"
            placeholder="选择审批人解析规则"
          >
            <el-option
              v-for="item in approverRules"
              :key="item.rule"
              :value="item.rule"
              :label="`${item.label}（${item.rule}）`"
            >
              <span>{{ item.label }}</span>
              <span class="oa-text-caption oa-text-subtle opt-note">
                {{ item.rule }}
                <template v-if="!item.trunkUsable"> · 仅②的并行子任务</template>
                <template v-if="item.requiresParam"> · 需参数</template>
              </span>
            </el-option>
          </el-select>
          <p v-if="currentRuleMeta" class="hint">
            出处：{{ currentRuleMeta.source }}
            <template v-if="!currentRuleMeta.trunkUsable">
              <br />⚠ 该规则仅用于②的并行子任务组（协同部门负责人），用于主干节点会被服务端拒绝。
            </template>
          </p>
        </el-form-item>
        <el-form-item label="规则参数 approver_param（JSON，留空 = 清空）">
          <el-input
            v-model="rule.approverParamText"
            type="textarea"
            :rows="3"
            class="oa-mono"
            :disabled="!editable"
            placeholder='{"role_code":"admin"} 或 {"user_ids":[1,2]}'
          />
          <p class="hint">
            <code>designated</code> 必须给参数（<code>role_code</code> 或 <code>user_ids</code>）；
            其余规则留空即可。保存时传 <code>null</code> 即清空（PUT 全量语义）。
          </p>
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button :disabled="!editable" @click="runApproverRuleValidate">校验（不落库）</el-button>
        <el-button :loading="ruleResolving" @click="runApproverRuleResolve">预览候选人（按真实节点配置解析）</el-button>
        <el-button v-if="editable" type="primary" :loading="saving.rule" @click="saveApproverRule">保存解析规则</el-button>
      </div>
      <div v-if="ruleResolve" class="resolve-result">
        <p>
          <b>{{ ruleResolve.resolved ? '解析成功' : '解析失败（取不到候选人）' }}</b>
          <span class="oa-text-caption oa-text-subtle"> · {{ ruleResolve.source }}</span>
        </p>
        <p v-if="ruleResolve.missingConfig.length" class="hint error">
          缺配置：{{ ruleResolve.missingConfig.join('；') }}
        </p>
        <p v-if="ruleResolve.evidence" class="oa-text-caption oa-text-subtle">{{ ruleResolve.evidence }}</p>
        <ul v-if="ruleResolve.candidates.length" class="candidates">
          <li v-for="candidate in ruleResolve.candidates" :key="candidate.userId">
            {{ candidate.name }}<span class="oa-text-caption oa-text-subtle">（{{ candidate.orgName }} {{ candidate.position }}）</span>
          </li>
        </ul>
      </div>
    </section>

    <!-- ==================== 4. 签名策略 / 超时 / 开关 ==================== -->
    <section class="oa-card block">
      <h3>签名策略与超时</h3>
      <el-form label-position="top" class="form">
        <el-form-item label="签名策略 sign_policy">
          <el-select v-model="policy.signPolicy" class="fill" :disabled="!editable">
            <el-option
              v-for="item in FLOW_SIGN_POLICY_OPTIONS"
              :key="item"
              :value="item"
              :label="`${FLOW_SIGN_POLICY_LABEL[item]}（${item}）`"
            />
          </el-select>
          <p class="hint">⑤ 集团分管领导、⑥ 集团董事长默认强制签名；⑦ 默认不签名（templates.md §1.0）。</p>
        </el-form-item>
        <el-form-item label="超时时长 timeout_hours（小时）">
          <el-input-number
            v-model="policy.timeoutHours"
            :min="0"
            :max="720"
            :disabled="!editable"
            controls-position="right"
          />
          <p class="hint">
            服务端要求 ≥24h（T-01；② 财务部复核默认 48h，其余 24h）；填 0 = 不设超时（服务端归一为 null）。
            超时**仅催办**，不自动跳过、不升级。
          </p>
        </el-form-item>
        <el-form-item label="超时抄送上级 timeout_cc_superior">
          <el-switch v-model="policy.timeoutCcSuperior" :disabled="!editable" />
          <p class="hint">默认关闭；开启后超时同时抄送审批人上级（test-cases.md TC-MSG-005）。</p>
        </el-form-item>
        <el-form-item label="节点开关">
          <div class="switches">
            <label>允许加签 allow_add_sign<el-switch v-model="policy.allowAddSign" :disabled="!editable" /></label>
            <label>允许流转 allow_route<el-switch v-model="policy.allowRoute" :disabled="!editable" /></label>
            <label>允许自由跳转 allow_jump<el-switch v-model="policy.allowJump" :disabled="!editable" /></label>
          </div>
          <p class="hint">
            流转/回退能力默认仅 ②⑤⑥ 开启（T-02）；自由跳转一期默认**全关闭**（T-08），
            开启后发布前检查会给**提示项**（不阻止发布），跳转必须填原因并留痕（AC-46）。
          </p>
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button :disabled="!editable" @click="runPolicyValidate">校验（不落库）</el-button>
        <el-button v-if="editable" type="primary" :loading="saving.policy" @click="savePolicy">保存签名策略与超时</el-button>
      </div>
    </section>

    <!-- ==================== 5. 跳过条件 ==================== -->
    <section class="oa-card block">
      <h3>跳过条件 skip_condition</h3>
      <el-input
        v-model="skip.text"
        type="textarea"
        :rows="4"
        class="oa-mono"
        :disabled="!editable"
        placeholder='{"field":"involve_cost","op":"eq","value":false}（留空 = 无跳过条件）'
      />
      <p class="hint">
        仅**事项审批单的②**可跳过（`doc/templates.md` §1.0）；字段必须存在于表单模板的
        <code>form_schema_json.fields[].code</code>，操作符须在
        <code>eq/ne/in/notIn/gt/gte/lt/lte/empty/notEmpty/checked</code> 白名单内。
        服务端 <code>R-SKIP</code> 规则会在发布前复检。
      </p>
      <div class="actions">
        <el-button :disabled="!editable" @click="runSkipValidate">校验（不落库）</el-button>
        <el-button v-if="editable" type="primary" :loading="saving.skip" @click="saveSkipCondition">保存跳过条件</el-button>
      </div>
    </section>

    <!-- ==================== 6. 校验结论（各 validate 接口共用） ==================== -->
    <section v-if="validation" class="oa-card block">
      <h3>校验结论 · {{ validation.source }}</h3>
      <p>
        <span class="oa-pill" :class="validation.passed ? 'is-approved' : 'is-rejected'">
          {{ validation.passed ? '通过' : '不通过' }}
        </span>
        <span class="oa-text-caption oa-text-subtle">（validate 接口不落库，仅即时反馈）</span>
      </p>
      <ul v-if="validation.problems.length" class="problems">
        <li v-for="(item, index) in validation.problems" :key="`p-${index}`">{{ item }}</li>
      </ul>
      <ul v-if="validation.warnings.length" class="warnings">
        <li v-for="(item, index) in validation.warnings" :key="`w-${index}`">提示：{{ item }}</li>
      </ul>
    </section>

    <p class="tail-note oa-text-caption oa-text-subtle">
      本面板每一项保存都走独立的 PUT 接口（决议 / 策略 / 跳过条件 / 解析规则 / 基础信息），
      失败时如实展示后端业务码与文案（40001 参数校验、40008 配置非法、40906 已发布只读）。
    </p>
  </div>
</template>

<style scoped>
.oa-node-panel {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
  min-width: 0;
}

.panel-head h2 {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-xs);
  color: var(--oa-color-ink);
}

.panel-head .seq {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.readonly-note {
  margin-top: var(--oa-space-xxs);
  padding: 6px var(--oa-space-xs);
  border-left: 2px solid var(--oa-color-warning);
  background: var(--oa-color-warning-surface);
  border-radius: var(--oa-radius-xs);
  font: var(--oa-font-caption);
  color: var(--oa-color-warning);
}

.readonly-note.is-soft {
  border-left-color: var(--oa-color-info);
  background: var(--oa-color-info-surface);
  color: var(--oa-color-ink-muted);
}

.block {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-sm) var(--oa-space-md);
}

.block h3 {
  font: var(--oa-font-title-section);
  color: var(--oa-color-ink);
}

.form :deep(.el-form-item) {
  margin-bottom: var(--oa-space-xs);
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

.fill {
  width: 100%;
}

.opt-note {
  margin-left: var(--oa-space-xs);
}

.threshold-inputs {
  display: flex;
  align-items: center;
  gap: var(--oa-space-sm);
  margin-top: var(--oa-space-xs);
}

.resolve-row {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  margin-top: var(--oa-space-xxs);
}

.sub-block {
  margin-top: var(--oa-space-xs);
  padding-top: var(--oa-space-xs);
  border-top: 1px dashed var(--oa-color-hairline);
}

.sub-title {
  font: var(--oa-font-label);
  color: var(--oa-color-ink);
}

.resolve-result {
  margin-top: var(--oa-space-xxs);
  padding: var(--oa-space-xs);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.candidates {
  margin-top: 4px;
  padding-left: var(--oa-space-md);
  list-style: disc;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.switches {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-md);
}

.switches label {
  display: inline-flex;
  align-items: center;
  gap: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs);
  margin-top: var(--oa-space-xs);
}

.problems,
.warnings {
  padding-left: var(--oa-space-md);
  list-style: disc;
  font: var(--oa-font-caption);
}

.problems {
  color: var(--oa-color-error);
}

.warnings {
  color: var(--oa-color-warning);
}

.tail-note {
  display: block;
}

code {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-mono);
}
</style>
