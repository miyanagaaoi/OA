<script setup lang="ts">
/**
 * oa-web · 单个表单字段的控件（14 种字段类型各给对应控件）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/enums.md` §11（14 种字段类型）
 *   · `doc/forms.md` §1.2（三态读写：可写/只读都要**给出原因**）、§1.5（金额定点、禁浮点）、
 *     §1.4（附件全局限制）、§5（印鉴单归还唯一例外）
 *   · `DESIGN.md` › Components › Forms（字段标签 12px muted、错误文案 error 色、金额等宽右对齐）
 *
 * 三条硬约束（本组件是它们的落点）：
 *   1. **金额全程字符串**：`modelValue` 是字符串，编辑只做字符级过滤与补零，
 *      **绝不 `Number()` / `parseFloat`** —— 浮点一旦往返就会写出服务端必拒的载荷（40306/40011）。
 *   2. **只读必须给原因**：`readonlyReason` 非空时控件 disabled，并在字段下方显示原因
 *      （不是「只是灰掉」，而是告诉用户为什么、以及该走哪条路径）。
 *   3. **附件如实标注「待接入」**：阶段 2b.7 才有上传接口，本组件只展示已落库的附件元数据，
 *      **不伪造上传入口**。
 *
 * `user` / `org` 是**单值**控件：后端 `FormPayloadValidator#typeMatches` 对
 * `USER` / `ORG` 只接受 `CharSequence`（实测数组形态回 `typeMismatch`），
 * 与 `doc/forms.md` 的「≤20 人」存在口径差 —— 界面按**当前服务端契约**渲染并在下方标注，
 * 多值入口请走「抄送」动作（`POST /flow-instances/{id}/cc`）。
 */
import { computed } from 'vue'
import type { FormField, FormJsonValue, FormOption } from '@/types/form'
import { ATTACHMENT_PENDING_HINT, asBool, asDisplayText, asInputText, asTextList, checkAmountText, normalizeAmountInput, padAmountScale } from '@/utils/form-rules'

const props = defineProps<{
  field: FormField
  modelValue: FormJsonValue
  /** 是否可写（由 `utils/form-rules.ts` 的 `readonlyReason` 判定；服务端仍是边界） */
  writable: boolean
  /** 只读原因（可写时为空串） */
  readonlyReason: string
  /** 该字段的服务端错误文案（`FormValidationReport` 逐字段映射而来） */
  errors: string[]
  /** 选项（字典项或模板内联选项；**字典取值来自接口，不硬编码**） */
  options: FormOption[]
  /** 选项是否正在加载（字典接口未返回时） */
  optionsLoading: boolean
  /** 金额字段对当前角色是否可写（`utils/form-rules.ts#resolveAmountWrite`；与状态正交） */
  amountWritable: boolean
  /** 金额判定说明（可写/只读都能解释清楚；含与服务端 amountPolicy 不一致时的披露） */
  amountNote: string
  /** 通讯录候选人（`user` 字段） */
  userOptions: FormOption[]
  /** 组织候选人（`org` 字段） */
  orgOptions: FormOption[]
  /** 候选人数据源是否加载失败（失败时降级为手填 id 并说明） */
  pickerUnavailable: boolean
}>()

const emit = defineEmits<{ (event: 'update:modelValue', value: FormJsonValue): void }>()

const disabled = computed(() => !props.writable)

const textValue = computed(() => asInputText(props.modelValue))
const boolValue = computed(() => asBool(props.modelValue))
const listValue = computed(() => asTextList(props.modelValue))
const rangeValue = computed<string[]>(() => {
  const list = asTextList(props.modelValue)
  return [list[0] ?? '', list[1] ?? '']
})

const maxLength = computed(() => (props.field.maxLength > 0 ? props.field.maxLength : undefined))

/** 金额实时提示（服务端仍是裁决方） */
const amountCheck = computed(() => checkAmountText(textValue.value, props.field.unit ?? '元'))
const amountHint = computed(() => {
  if (!props.amountWritable) {
    return props.amountNote || '金额对非财务类角色只读（PRD §5.3；服务端按 40306 拒绝）'
  }
  if (amountCheck.value.message) return amountCheck.value.message
  return '定点两位小数（DECIMAL(18,2)），不支持千分位/科学计数；提交时按字符串上送，绝不经浮点'
})
/** 角色判定的补充披露（服务端 amountPolicy 与角色口径不一致时给用户一个解释） */
const amountRoleNote = computed(() => (props.amountWritable ? props.amountNote : ''))

/** 附件已落库的元数据（`{fileName,fileSize}`；上传接口未交付，只读展示） */
interface AttachmentMeta {
  name: string
  size: string
}
const attachmentItems = computed<AttachmentMeta[]>(() => {
  const value = props.modelValue
  if (!Array.isArray(value)) {
    return value === null || value === undefined ? [] : [{ name: asDisplayText(value), size: '' }]
  }
  return value.map((item) => {
    if (item !== null && typeof item === 'object' && !Array.isArray(item)) {
      const record = item as Record<string, FormJsonValue>
      const name = asDisplayText(record.fileName ?? record.name ?? '')
      const size = asDisplayText(record.fileSize ?? record.size ?? '')
      return { name: name || '（未命名附件）', size }
    }
    return { name: asDisplayText(item), size: '' }
  })
})

/** 单选下拉的当前值（选项类字段归一为字符串） */
const selectValue = computed(() => textValue.value)

function update(value: FormJsonValue): void {
  emit('update:modelValue', value)
}

/** 逗号/顿号分隔的标签输入 → 字符串（后端 TAG 为 CharSequence，单值） */
function onTagInput(raw: string): void {
  update(raw)
}

/** 金额输入：字符级过滤（保留字符串），失焦补零到两位小数 */
function onAmountInput(raw: string): void {
  update(normalizeAmountInput(raw))
}

function onAmountBlur(): void {
  const padded = padAmountScale(textValue.value)
  if (padded !== null && padded !== textValue.value) update(padded)
}

/** 数字（非金额）：同样是字符串，避免浮点 */
function onNumberInput(raw: string): void {
  update(normalizeAmountInput(raw))
}

/** 日期区间：两个日期合成 `[start, end]` */
function onRangePart(index: number, value: string): void {
  const next = [...rangeValue.value]
  next[index] = value
  update([next[0] ?? '', next[1] ?? ''])
}

function onMultiSelect(value: string[]): void {
  update(value)
}

function onBoolean(value: string | number | boolean): void {
  // `el-checkbox` 的 model-value 载荷是 `CheckboxValueType`（boolean | string | number），
  // 本字段只承载布尔：`asBool` 负责归一（'true'/'1' → true），避免把字符串写进布尔字段
  update(asBool(value))
}
</script>

<template>
  <div class="field-control" :class="{ 'is-readonly': disabled, 'has-error': errors.length > 0 }">
    <p class="label-row">
      <span class="label">{{ field.label }}</span>
      <i v-if="field.required" class="req">必填</i>
      <i v-if="field.locked" class="flag">模板锁定</i>
      <i v-if="disabled" class="flag">只读</i>
      <i v-if="field.unit" class="unit">单位：{{ field.unit }}</i>
      <i class="code oa-mono">{{ field.code }}</i>
    </p>

    <!-- 金额：字符串定点 + 等宽右对齐；失焦补零，绝不经 Number -->
    <template v-if="field.control === 'amount'">
      <el-input
        class="oa-amount-input"
        :model-value="textValue"
        :disabled="disabled || !amountWritable"
        inputmode="decimal"
        placeholder="如 1250000.00"
        @update:model-value="onAmountInput"
        @blur="onAmountBlur"
      >
        <template #prefix>¥</template>
      </el-input>
      <p class="hint" :class="{ 'is-error': !amountCheck.ok }">{{ amountHint }}</p>
      <p v-if="amountRoleNote" class="hint capability">{{ amountRoleNote }}</p>
    </template>

    <!-- 数字（非金额）：同样按字符串上送 -->
    <template v-else-if="field.control === 'number'">
      <el-input
        :model-value="textValue"
        :disabled="disabled"
        inputmode="decimal"
        placeholder="请输入数字"
        @update:model-value="onNumberInput"
      />
      <p class="hint">数字同样按字符串上送（服务端会做区间与整数位校验）</p>
    </template>

    <!-- 多行文本 -->
    <el-input
      v-else-if="field.control === 'textarea'"
      type="textarea"
      :rows="4"
      :maxlength="maxLength"
      show-word-limit
      :model-value="textValue"
      :disabled="disabled"
      :placeholder="field.placeholder ?? ''"
      @update:model-value="(value: string) => update(value)"
    />

    <!-- 单行文本 -->
    <el-input
      v-else-if="field.control === 'text'"
      :maxlength="maxLength"
      :model-value="textValue"
      :disabled="disabled"
      :placeholder="field.placeholder ?? ''"
      @update:model-value="(value: string) => update(value)"
    />

    <!-- 标签（自由文本，服务端去重） -->
    <el-input
      v-else-if="field.control === 'tag'"
      :maxlength="maxLength"
      :model-value="textValue"
      :disabled="disabled"
      placeholder="自由文本标签"
      @update:model-value="onTagInput"
    />

    <!-- 单选下拉：选项来自字典接口（dictType）或模板内联 options，**不硬编码** -->
    <template v-else-if="field.control === 'select'">
      <el-select
        class="full"
        :model-value="selectValue"
        :disabled="disabled"
        :loading="optionsLoading"
        :placeholder="field.placeholder ?? '请选择'"
        filterable
        clearable
        @update:model-value="(value: string) => update(value ?? '')"
      >
        <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <p v-if="optionsLoading" class="hint">正在从字典接口取选项…</p>
      <p v-else-if="options.length === 0" class="hint is-error">
        选项为空：该字段未返回任何启用项（字典 {{ field.dictType ?? '（内联 options 为空）' }}）
      </p>
    </template>

    <!-- 多选 -->
    <template v-else-if="field.control === 'multiselect'">
      <el-select
        class="full"
        multiple
        collapse-tags
        :model-value="listValue"
        :disabled="disabled"
        :loading="optionsLoading"
        placeholder="请选择（可多选）"
        @update:model-value="onMultiSelect"
      >
        <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <p v-if="options.length === 0 && !optionsLoading" class="hint is-error">选项为空：字典未返回启用项</p>
    </template>

    <!-- 日期 -->
    <el-date-picker
      v-else-if="field.control === 'date'"
      class="full"
      type="date"
      value-format="YYYY-MM-DD"
      :model-value="textValue"
      :disabled="disabled"
      placeholder="请选择日期"
      @update:model-value="(value: string) => update(value ?? '')"
    />

    <!-- 日期区间：[start, end]，结束 ≥ 开始由服务端再判一次 -->
    <div v-else-if="field.control === 'daterange'" class="range">
      <el-date-picker
        type="date"
        value-format="YYYY-MM-DD"
        :model-value="rangeValue[0]"
        :disabled="disabled"
        placeholder="开始日期"
        @update:model-value="(value: string) => onRangePart(0, value ?? '')"
      />
      <span class="tilde">—</span>
      <el-date-picker
        type="date"
        value-format="YYYY-MM-DD"
        :model-value="rangeValue[1]"
        :disabled="disabled"
        placeholder="结束日期"
        @update:model-value="(value: string) => onRangePart(1, value ?? '')"
      />
    </div>

    <!-- 人员（单值：后端 typeMatches 只接受 CharSequence；多值走「抄送」动作） -->
    <template v-else-if="field.control === 'user'">
      <el-select
        v-if="!pickerUnavailable"
        class="full"
        :model-value="selectValue"
        :disabled="disabled"
        filterable
        clearable
        placeholder="从通讯录选择人员"
        @update:model-value="(value: string) => update(value ?? '')"
      >
        <el-option v-for="option in userOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-input
        v-else
        :model-value="textValue"
        :disabled="disabled"
        placeholder="通讯录不可用，请填写用户 id"
        @update:model-value="(value: string) => update(value)"
      />
      <p class="hint capability">
        <b>多值（≤20 人）待后端支持</b>，当前请用抄送。
        现状：服务端 `FormPayloadValidator#typeMatches` 对 user 字段只接受单值 CharSequence，
        数组形态会回 `typeMismatch`（doc/forms.md 的「≤20 人」口径待后端补齐）；
        需要多人知会时请走「抄送」动作（`POST /flow-instances/{id}/cc`，抄送只读可见、不产生待办）。
      </p>
    </template>

    <!-- 组织（单值，同 user 的口径说明） -->
    <template v-else-if="field.control === 'org'">
      <el-select
        v-if="!pickerUnavailable"
        class="full"
        :model-value="selectValue"
        :disabled="disabled"
        filterable
        clearable
        placeholder="选择组织节点"
        @update:model-value="(value: string) => update(value ?? '')"
      >
        <el-option v-for="option in orgOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-input
        v-else
        :model-value="textValue"
        :disabled="disabled"
        placeholder="组织选择器不可用，请填写组织 id 或约定符号"
        @update:model-value="(value: string) => update(value)"
      />
      <p class="hint capability">
        <b>多值组织选择待后端支持</b>，当前为单值。
        现状：服务端 `FormPayloadValidator#typeMatches` 对 org 字段只接受单值 CharSequence（同 user）。
      </p>
      <p class="hint">
        单值组织 id；模板默认值里的符号型占位（如 <code>initiator_company</code>）是界面预填指令，
        服务端不会据此写库（doc/templates.md §2.2 扩展键说明）。
      </p>
    </template>

    <!-- 布尔 -->
    <el-checkbox
      v-else-if="field.control === 'boolean'"
      :model-value="boolValue"
      :disabled="disabled"
      @update:model-value="onBoolean"
    >
      {{ field.label }}
    </el-checkbox>

    <!-- 附件（file / files）：阶段 2b.7 才有上传接口 → 明确「待接入」，不伪造上传 -->
    <div v-else-if="field.control === 'attachment'" class="attachment">
      <p class="pending">待接入（阶段 2b.7）</p>
      <ul v-if="attachmentItems.length > 0" class="attach-list">
        <li v-for="(item, index) in attachmentItems" :key="`${item.name}-${index}`">
          <span class="attach-name">{{ item.name }}</span>
          <span v-if="item.size" class="attach-meta oa-mono">{{ item.size }} B</span>
        </li>
      </ul>
      <p v-else class="hint">尚无已落库的附件元数据。</p>
      <p class="hint">{{ ATTACHMENT_PENDING_HINT }}</p>
    </div>

    <!-- 未知类型：如实提示，不静默渲染成文本框（否则用户会以为能填） -->
    <div v-else class="unsupported">
      <p class="is-error">
        模板里的字段类型「{{ field.rawType || '（空）' }}」不在 14 种已登记类型内，
        前端没有对应控件；服务端会按「类型非法」拒绝提交。
      </p>
    </div>

    <!-- 只读原因（硬要求：明确置灰并给原因） -->
    <p v-if="disabled && readonlyReason" class="reason">只读原因：{{ readonlyReason }}</p>

    <!-- 服务端逐字段错误 -->
    <ul v-if="errors.length > 0" class="errors">
      <li v-for="(message, index) in errors" :key="index">{{ message }}</li>
    </ul>
  </div>
</template>

<style scoped>
.field-control {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.label-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font: var(--oa-font-label);
  color: var(--oa-color-ink-muted);
}

.label-row .label {
  color: var(--oa-color-ink);
  font-weight: 500;
}

.req {
  color: var(--oa-color-error);
  font: var(--oa-font-caption);
  font-style: normal;
}

.flag {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
  font-style: normal;
}

.unit,
.code {
  font: var(--oa-font-caption);
  font-style: normal;
  color: var(--oa-color-ink-subtle);
}

.code {
  margin-left: auto;
}

.full {
  width: 100%;
}

.range {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

.tilde {
  color: var(--oa-color-ink-disabled);
}

.hint {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

/* 能力缺失说明（不是界面坏了）：给底色，便于用户一眼分辨「待支持」与「填错了」 */
.hint.capability {
  padding: 2px 6px;
  border-left: 2px solid var(--oa-color-warning);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  color: var(--oa-color-ink-muted);
}

.hint.capability b {
  color: var(--oa-color-warning);
}

.hint code {
  font: var(--oa-font-mono);
  color: var(--oa-color-ink);
}

.is-error {
  color: var(--oa-color-error);
}

.reason {
  padding: 2px 6px;
  border-left: 2px solid var(--oa-color-hairline-strong);
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
}

.errors {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--oa-color-error);
  font: var(--oa-font-caption);
}

.attachment {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--oa-space-xs);
  border: 1px dashed var(--oa-color-hairline-strong);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
}

.pending {
  align-self: flex-start;
  padding: 1px 6px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-warning-subtle, var(--oa-color-surface-1));
  color: var(--oa-color-warning);
  font: var(--oa-font-caption);
}

.attach-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.attach-list li {
  display: flex;
  gap: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
}

.attach-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-meta {
  color: var(--oa-color-ink-subtle);
  font: var(--oa-font-caption);
}

.unsupported {
  padding: var(--oa-space-xs);
  border: 1px solid var(--oa-color-error);
  border-radius: var(--oa-radius-sm);
  font: var(--oa-font-caption);
}

:deep(.oa-amount-input .el-input__inner) {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}
</style>
