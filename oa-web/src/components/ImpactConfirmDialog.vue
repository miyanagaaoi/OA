<script setup lang="ts">
/**
 * oa-web · 危险操作确认（带受影响在途单据清单）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.5（组织停用前清空在途、员工离职前清空待办）、
 *     AC-11、AC-12
 *   · `doc/import-spec.md` §7.2（清单八列）、§7.4（影响程度=高 且未确认 → 阻断；
 *     确认动作写 sys_log）、§8.1 / §8.2（规范提示文案）
 *   · `DESIGN.md` › modal（确认类 400px / 表单类 720px、确认按钮文案要具体）、
 *     Agent Usage Rules 第 5 条「危险操作二次确认，确认按钮文案包含动作与对象」
 *
 * 行为契约（三个危险操作共用，不许各自实现）：
 *   1. 永远二次确认；确认按钮文案 = 「确认 + 动作」（如「确认停用」）。
 *   2. 在途/待办非零 → `blocked = true` → 默认**阻断**：按钮变为「强制继续」，
 *      且必须满足「具备管理员权限」+「必填原因」两个条件才可点。
 *   3. 原因随请求提交（`confirm` 事件把 `{reason, force}` 交给调用方），
 *      由服务端**真正放行**该次在途/待办阻断并写审计日志（AC-52）；
 *      因此必填原因校验与 force 语义必须一致：force 只在 blocked=true 时下发。
 *   4. 明细列以服务端下发的为准（单据类型 / 发起人 / 当前节点）；缺失列显示「—」，
 *      不用推断值填充「影响程度 / 受影响原因」。
 */
import { computed, ref, watch } from 'vue'
import type { ImpactItem } from '@/types/identity'
import {
  IMPACT_LEVEL_CLASS,
  IMPACT_LEVEL_LABEL,
  formTypeText,
  impactReasonText,
} from '@/utils/identity-rules'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 对话框标题 */
    title?: string
    /** 危险动作名：用于拼确认按钮文案（停用 / 离职 / 调岗 / 移除负责人） */
    actionLabel: string
    /** 作用对象：组织全路径 或 「姓名（账号）」 */
    target: string
    /** 服务端规范提示文案（优先展示）；缺省时用 summary 兜底 */
    message?: string
    /** 后端未给出文案时前端兜底说明 */
    summary?: string
    /** 是否默认阻断（在途/待办非零） */
    blocked?: boolean
    /** 在途/待办数量（在途单据数；与 pendingCount 一起展示） */
    count?: number
    /** 待处理待办数（服务端 pendingTaskCount；无则只展示 count） */
    pendingCount?: number
    /** 受影响的在途单据清单 */
    items?: ImpactItem[]
    /** 是否具备「强制继续」权限（管理员） */
    canForce?: boolean
    /** 原因是否必填（强制继续与调岗/交接恒为必填） */
    reasonRequired?: boolean
    /** 提交中：禁用按钮并显示 loading */
    submitting?: boolean
    /** 强制继续时的额外后果说明（如「在途单据仍由原快照审批人处理，不会自动改派」） */
    consequence?: string
  }>(),
  {
    title: '危险操作确认',
    message: '',
    summary: '',
    blocked: false,
    count: 0,
    pendingCount: 0,
    items: () => [],
    canForce: false,
    reasonRequired: false,
    submitting: false,
    consequence: '',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', payload: { reason: string; force: boolean }): void
  (e: 'cancel'): void
}>()

const reason = ref('')

/**
 * 默认阻断模式：服务端判定阻断（blocked）或有在途/待办数量即为阻断。
 * 阻断时按钮变「强制继续」，`force=true` 随请求下发 —— 服务端据此**放行**在途/待办拦截
 * 并把原因与操作人写入审计日志（AC-52）。
 */
const blocking = computed(() => props.blocked || props.count > 0 || props.pendingCount > 0)

const reasonFilled = computed(() => reason.value.trim().length > 0)

const needReason = computed(() => props.reasonRequired || blocking.value)

const confirmText = computed(() => (blocking.value ? '强制继续' : `确认${props.actionLabel}`))

const confirmDisabled = computed(() => {
  if (props.submitting) return true
  // 默认阻断：无权限时连强制继续都不可点（权限不可见优于不可用由调用方控制渲染）
  if (blocking.value && !props.canForce) return true
  if (needReason.value && !reasonFilled.value) return true
  return false
})

const reasonPlaceholder = computed(() =>
  blocking.value
    ? '必填：说明强制继续的理由（将放行在途阻断并写入审计日志）'
    : '必填：说明本次操作原因（将写入审计日志）',
)

// 每次打开重置原因：绝不把上一次的说明带到下一次危险操作上
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) reason.value = ''
  },
)

function onVisibleChange(value: boolean): void {
  emit('update:modelValue', value)
  if (!value) emit('cancel')
}

function onConfirm(): void {
  if (confirmDisabled.value) return
  emit('confirm', { reason: reason.value.trim(), force: blocking.value })
}

// ---------------------------------------------------------------------------
// 表格单元格的取值收窄：el-table 的作用域插槽 row 是 DefaultRow，
// 在模板里不能直接当 DTO 用，统一在这里换成具体类型（不使用 any）。
// 后端只回单号与节点名的场景，其余列一律显示「—」，绝不用假数据填充。
// ---------------------------------------------------------------------------
function asItem(row: unknown): ImpactItem {
  return row as ImpactItem
}

function typeText(row: unknown): string {
  const item = asItem(row)
  return formTypeText(item.formType, item.formTypeLabel)
}

function initiatorText(row: unknown): string {
  const item = asItem(row)
  if (!item.initiatorName) return '—'
  return item.initiatorAccount ? `${item.initiatorName}（${item.initiatorAccount}）` : item.initiatorName
}

function nodeText(row: unknown): string {
  return asItem(row).currentNodeName || '—'
}

function approverText(row: unknown): string {
  return asItem(row).currentApproverName || '—'
}

function reasonText(row: unknown): string {
  const reason = asItem(row).impactReason
  return reason ? impactReasonText(reason) : '—'
}

function levelClass(row: unknown): string {
  const level = asItem(row).impactLevel
  return level ? IMPACT_LEVEL_CLASS[level] : 'is-closed'
}

function levelText(row: unknown): string {
  const level = asItem(row).impactLevel
  return level ? IMPACT_LEVEL_LABEL[level] : '—'
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="720px"
    append-to-body
    :close-on-click-modal="false"
    class="oa-impact-dialog"
    @update:model-value="onVisibleChange"
  >
    <div class="head">
      <span class="oa-pill" :class="blocking ? 'is-rejected' : 'is-pending'">
        {{ blocking ? '默认阻断' : '二次确认' }}
      </span>
      <p class="target">
        操作对象：<b>{{ target }}</b>
      </p>
    </div>

    <!-- 规范提示文案：优先展示服务端文案（import-spec §8.1 / §8.2 的原文口径） -->
    <p v-if="message || summary" class="notice" :class="{ 'is-danger': blocking }">
      {{ message || summary }}
    </p>

    <template v-if="blocking">
      <div class="count-line">
        <span>受影响在途/待办</span>
        <template v-if="count > 0">
          <b class="oa-tnum">{{ count }}</b>
          <span class="unit">张在途单据</span>
        </template>
        <template v-if="pendingCount > 0">
          <b class="oa-tnum">{{ pendingCount }}</b>
          <span class="unit">条待处理待办</span>
        </template>
        <span v-if="consequence" class="consequence">{{ consequence }}</span>
      </div>

      <el-table
        v-if="items.length"
        class="el-table--compact"
        :data="items"
        size="small"
        max-height="320"
        border
      >
        <el-table-column prop="bizNo" label="单号" min-width="140" />
        <el-table-column label="单据类型" min-width="130">
          <template #default="{ row }">
            {{ typeText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="发起人" min-width="150">
          <template #default="{ row }">{{ initiatorText(row) }}</template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="150">
          <template #default="{ row }">
            <span class="oa-tnum">{{ nodeText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前审批人" min-width="110">
          <template #default="{ row }">{{ approverText(row) }}</template>
        </el-table-column>
        <el-table-column label="受影响原因" min-width="110">
          <template #default="{ row }">{{ reasonText(row) }}</template>
        </el-table-column>
        <el-table-column label="影响程度" width="90">
          <template #default="{ row }">
            <span class="oa-pill" :class="levelClass(row)">
              {{ levelText(row) }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <p v-else class="oa-text-caption oa-text-subtle">
        服务端未返回明细清单，仅返回数量；请联系系统管理员用「改派」兜底处理（AC-52）。
      </p>

      <!-- 强制继续的语义必须写清：force 会真正放行阻断，并且留痕 -->
      <p class="force-notice">
        「强制继续」将<b>放行</b>本次在途/待办阻断（服务端按 force=true + 必填原因 + 系统管理员
        三者同时满足才放行），操作原因与操作人一并写入审计日志（AC-52），事后可追溯、不可撤销。
        在途单据仍由原快照审批人处理，不会自动改派（PRD 5.4）。
      </p>

      <p v-if="!canForce" class="no-permission">
        你当前没有「强制继续」权限（仅系统管理员可用）：影响清单非空时无法绕过阻断。
        请先转办 / 改派 / 办结，或联系系统管理员。
      </p>
    </template>

    <el-form label-position="top" class="reason-form">
      <!-- 调用方可插入动作专属字段（如调岗的目标组织、交接的接收人），
           与影响清单、原因一起构成一次完整的危险操作确认 -->
      <slot />

      <el-form-item :required="needReason">
        <template #label>
          <span class="oa-text-label">{{ blocking ? '强制继续原因' : '操作原因' }}</span>
        </template>
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          :placeholder="reasonPlaceholder"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onVisibleChange(false)">取消</el-button>
      <el-button
        :type="blocking ? 'danger' : 'primary'"
        :plain="blocking"
        :loading="submitting"
        :disabled="confirmDisabled"
        @click="onConfirm"
      >
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  margin-bottom: var(--oa-space-sm);
}

.target {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.target b {
  font-weight: 500;
  color: var(--oa-color-ink);
}

/* 提示块用语义色 surface + 左侧 3px 竖条，不做整块饱和填充（DESIGN.md 状态色规则） */
.notice {
  margin-bottom: var(--oa-space-sm);
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border-left: 3px solid var(--oa-color-warning);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-warning-surface);
  color: var(--oa-color-ink);
  font: var(--oa-font-body-sm);
}

.notice.is-danger {
  border-left-color: var(--oa-color-error);
  background: var(--oa-color-error-surface);
}

.count-line {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-xs);
  margin-bottom: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.count-line b {
  font: var(--oa-font-amount);
  color: var(--oa-color-error);
}

.count-line .unit {
  margin-right: var(--oa-space-xs);
  color: var(--oa-color-ink-subtle);
}

.consequence {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

/* 强制继续的后果说明：与提示块同构（语义色竖条），常驻而不是一次性 toast */
.force-notice {
  margin-top: var(--oa-space-xs);
  padding: var(--oa-space-xs) var(--oa-space-sm);
  border-left: 3px solid var(--oa-color-error);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-error-surface);
  color: var(--oa-color-ink);
  font: var(--oa-font-body-sm);
}

.force-notice b {
  font-weight: 500;
}

.no-permission {
  margin-top: var(--oa-space-xs);
  font: var(--oa-font-caption);
  color: var(--oa-color-error);
}

.reason-form {
  margin-top: var(--oa-space-md);
}
</style>
