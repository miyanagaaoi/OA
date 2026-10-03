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
 *   3. **附件是真接口（阶段 2b.7 起）**：`file` / `files` 字段渲染 `AttachmentPanel`
 *      ——上传（拖拽/多选 + 前端预检）、按 round 分组的清单、鉴权下载/预览、本人删除都在面板里；
 *      本组件只负责把「字段 → 面板」的上下文（实例 id、三态、身份、模板 filePolicy）透传。
 *      表单值里若还存着历史的附件元数据，**照旧只读展示**（读法不变，不静默丢弃）。
 *
 * `user` / `org` 是**多值**控件（2026-10-04 后端 7c409ea 起）：
 * 服务端 `FormPayloadValidator#typeMatches` 对 `USER` / `ORG` 同时接受单值与数组，
 * 落库前按**去重保序**规范化（`canonicalizePickers`），并按 `rules[pickerLimit]` 计数量
 * ——模板未声明时回落 20（`doc/forms.md` §2 `cc_users` 行「≤20 人」）。
 * 因此界面用多选，提交时一律上送**数组**；元素存在性（`pickerValue`）与数量上限（`pickerLimit`）
 * 由服务端逐条裁决。单值历史数据的读法不变（`asTextList` 把字符串当单元素清单）。
 */
import { computed, ref, watch } from 'vue'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import type { FormField, FormJsonValue, FormOption, FormWriteStateCode } from '@/types/form'
import {
  asBool,
  asDisplayText,
  asInputText,
  asTextList,
  checkAmountText,
  exceedsPickerLimit,
  normalizeAmountInput,
  normalizePickerValues,
  padAmountScale,
  parsePickerInput,
  resolvePickerLimit,
} from '@/utils/form-rules'

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
  /** 金额判定说明（可写/只读都能解释清楚；可写时给「以服务端 40306 为准」的兜底提示） */
  amountNote: string
  /** 通讯录候选人（`user` 字段） */
  userOptions: FormOption[]
  /** 组织候选人（`org` 字段） */
  orgOptions: FormOption[]
  /** 候选人数据源是否加载失败（失败时降级为手填 id 并说明） */
  pickerUnavailable: boolean
  /**
   * 附件面板上下文（仅 `file` / `files` 字段用）：
   *   · `instanceId`：附件接口挂在实例上（发起页在「保存草稿」之前为空串）；
   *   · `currentUserId`：删除入口的判据（只能删自己传的）；
   *   · `stateCode`：三态码，用于给出附件专属的只读原因；
   *   · `identity`：发起人 / 系统管理员 / 其他（镜像后端 `requireInitiatorOrAdmin`）。
   */
  instanceId?: string
  currentUserId?: string
  writeStateCode?: FormWriteStateCode | null
  attachmentIdentity?: 'initiator' | 'admin' | 'other'
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
/** 角色判定的补充披露（**已删除**与服务端 amountPolicy 的不一致披露：后端已按当前主体计算） */
const amountRoleNote = computed(() => (props.amountWritable ? props.amountNote : ''))

// ---------------------------------------------------------------------------
// 人员 / 组织选择器（多值）
// ---------------------------------------------------------------------------
/** 多选上限（模板 `rules[pickerLimit]`；后端未声明时回落 20，与服务端同口径） */
const pickerLimit = computed(() => resolvePickerLimit(props.field))
/** 归一后的已选元素（trim + 去空 + 去重保序，与服务端 `canonicalizePickers` 同口径） */
const pickerValues = computed(() => normalizePickerValues(props.modelValue))
/** 是否已超出上限（选择器本身有 multiple-limit；手填路径与历史超限数据仍会命中） */
const pickerOver = computed(() => exceedsPickerLimit(pickerValues.value, pickerLimit.value))

/**
 * 手填文本（通讯录 / 组织选择器不可用时的降级路径）。
 *
 * **为什么不直接显示 `pickerValues.join(', ')`**：那样每敲一个分隔符就会立刻被归一掉
 * （输入 `304,` → 解析成 `['304']` → 文本回退成 `304`），用户根本敲不出第二个 id。
 * 因此这里保留一份**原始文本**，只在「外部值与自己解析的结果不一致」时才回填
 * （例如服务端读了别的值、或字段被清空）。
 */
const pickerDraft = ref(pickerValues.value.join(', '))

watch(
  () => props.modelValue,
  () => {
    const parsedSelf = parsePickerInput(pickerDraft.value)
    if (parsedSelf.join('\u0000') !== pickerValues.value.join('\u0000')) {
      pickerDraft.value = pickerValues.value.join(', ')
    }
  },
)

/** 选择器回传：去重保序后上送**数组**（服务端仍会兜底一次） */
function onPickerSelect(value: string[]): void {
  update(normalizePickerValues(value))
}

/** 手填回传：按逗号/顿号/空白切分（降级路径），保留原始文本以免打断输入，同时归一为数组 */
function onPickerText(raw: string): void {
  pickerDraft.value = raw
  update(parsePickerInput(raw))
}

/** 附件字段的表单值元数据（2b.7 之前的落库形态：`{fileName,fileSize}` / 字符串） */
interface AttachmentMeta {
  name: string
  size: string
}
/**
 * 表单值里的历史附件元数据（**只读展示**）。
 *
 * <p>附件实体自 2b.7 起由 `flow_attachment` + 附件接口承载（见 `AttachmentPanel`）；
 * 但历史草稿的表单值里可能仍存着 `{fileName,fileSize}` 这类元数据。
 * 该读法**保持不变**（不静默丢弃、不改写成附件实体），只在面板上方如实标注来源。
 */
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

/** 附件面板上下文（缺省值集中在模板里判，避免每个调用点都传全） */
const attachContext = computed(() => ({
  instanceId: props.instanceId ?? '',
  fieldCode: props.field.code,
  fieldLabel: props.field.label,
  currentUserId: props.currentUserId ?? '',
  stateCode: props.writeStateCode ?? null,
  identity: props.attachmentIdentity ?? 'other',
  filePolicy: props.field.ruleParams?.filePolicy,
}))

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
  <div
    class="field-control"
    :class="{ 'is-readonly': disabled, 'has-error': errors.length > 0, 'is-attachment': field.control === 'attachment' }"
  >
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

    <!-- 人员（多值：服务端接受数组并按去重保序规范化；上限 pickerLimit，缺省 20） -->
    <template v-else-if="field.control === 'user'">
      <el-select
        v-if="!pickerUnavailable"
        class="full"
        multiple
        collapse-tags
        collapse-tags-tooltip
        filterable
        clearable
        :multiple-limit="pickerLimit.max"
        :model-value="pickerValues"
        :disabled="disabled"
        placeholder="从通讯录选择人员（可多选）"
        @update:model-value="onPickerSelect"
      >
        <el-option v-for="option in userOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-input
        v-else
        :model-value="pickerDraft"
        :disabled="disabled"
        placeholder="通讯录不可用，请填写用户 id（多个用逗号分隔）"
        @update:model-value="onPickerText"
      />
      <p class="hint capability">
        <b>支持多值</b>：{{ pickerLimit.hint }}；提交时按数组上送，由服务端去重并逐个校验
        「是否在职人员」（rule=<code>pickerValue</code>）。
      </p>
      <p v-if="pickerOver" class="hint is-error">
        已选 {{ pickerValues.length }} 个，超过上限 {{ pickerLimit.max }}；请删掉多余的，
        否则服务端按 rule=<code>pickerLimit</code> 拒绝整单。
      </p>
    </template>

    <!-- 组织（多值，口径同 user；单位是「个组织节点」） -->
    <template v-else-if="field.control === 'org'">
      <el-select
        v-if="!pickerUnavailable"
        class="full"
        multiple
        collapse-tags
        collapse-tags-tooltip
        filterable
        clearable
        :multiple-limit="pickerLimit.max"
        :model-value="pickerValues"
        :disabled="disabled"
        placeholder="选择组织节点（可多选）"
        @update:model-value="onPickerSelect"
      >
        <el-option v-for="option in orgOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-input
        v-else
        :model-value="pickerDraft"
        :disabled="disabled"
        placeholder="组织选择器不可用，请填写组织 id（多个用逗号分隔）"
        @update:model-value="onPickerText"
      />
      <p class="hint capability">
        <b>支持多值</b>：{{ pickerLimit.hint }}；提交时按数组上送，由服务端去重并逐个校验
        「是否为有效组织节点」（rule=<code>pickerValue</code>）。
      </p>
      <p v-if="pickerOver" class="hint is-error">
        已选 {{ pickerValues.length }} 个，超过上限 {{ pickerLimit.max }}；请删掉多余的，
        否则服务端按 rule=<code>pickerLimit</code> 拒绝整单。
      </p>
      <p class="hint">
        组织 id 可多值；模板默认值里的符号型占位（如 <code>initiator_company</code>）是界面预填指令，
        服务端不会据此写库（doc/templates.md §2.2 扩展键说明），也不能作为提交取值。
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

    <!-- 附件（file / files）：阶段 2b.7 起是真接口（上传 / 清单 / 下载预览 / 删除） -->
    <div v-else-if="field.control === 'attachment'" class="attachment">
      <AttachmentPanel
        :instance-id="attachContext.instanceId"
        :field-code="attachContext.fieldCode"
        :field-label="attachContext.fieldLabel"
        :writable="writable"
        :state-code="attachContext.stateCode"
        :current-user-id="attachContext.currentUserId"
        :identity="attachContext.identity"
        :file-policy="attachContext.filePolicy"
      />

      <template v-if="attachmentItems.length > 0">
        <p class="hint">
          表单值里还存着 {{ attachmentItems.length }} 条历史附件元数据（2b.7 之前的落库形态，只读）：
        </p>
        <ul class="attach-list">
          <li v-for="(item, index) in attachmentItems" :key="`${item.name}-${index}`">
            <span class="attach-name">{{ item.name }}</span>
            <span v-if="item.size" class="attach-meta oa-mono">{{ item.size }} B</span>
          </li>
        </ul>
      </template>
    </div>

    <!-- 未知类型：如实提示，不静默渲染成文本框（否则用户会以为能填） -->
    <div v-else class="unsupported">
      <p class="is-error">
        模板里的字段类型「{{ field.rawType || '（空）' }}」不在 14 种已登记类型内，
        前端没有对应控件；服务端会按「类型非法」拒绝提交。
      </p>
    </div>

    <!-- 只读原因（硬要求：明确置灰并给原因）——
         附件字段的只读原因是**附件专属**的（审批中附件只读 / 身份不符），由 AttachmentPanel
         给出并说明；这里不再叠加通用原因，避免同一件事说两遍、口径还不一样。 -->
    <p v-if="disabled && readonlyReason && field.control !== 'attachment'" class="reason">
      只读原因：{{ readonlyReason }}
    </p>

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
}

/*
 * 附件面板要放下「档位 + 拖拽区 + 逐文件结果 + 按 round 分组的清单」，
 * 挤在渲染器的两列网格里会读不了 —— 让它**独占整行**（其余字段布局不变）。
 */
.field-control.is-attachment {
  grid-column: 1 / -1;
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
