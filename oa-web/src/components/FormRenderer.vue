<script setup lang="ts">
/**
 * oa-web · schema 驱动的表单渲染器（四类单据共用一套）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/forms.md` §11.1「表单由 `form_schema_json` 驱动，**不在前端硬编码字段**」
 *   · `doc/templates.md` §2.1（`sections` 分组）/ §2.2（字段项结构）/ §2.5（状态白名单）
 *   · `doc/forms.md` §1.2（三态读写）、§5（印鉴单归还唯一例外）、§7（补件期可写字段）
 *   · `doc/enums.md` §11（14 种字段类型）
 *
 * 本组件只负责「分组 + 状态 + 错误」的编排，具体控件在 `FormFieldControl.vue`：
 *   · **分组**：按 `sections[].fields` 渲染，未列入分组的字段补到「未分组字段」
 *     （兜底逻辑在 `utils/form-rules.ts#groupFields`，避免字段静默消失）；
 *   · **三态**：`state.writableFields` 是服务端下发的白名单，只读字段**必须给出原因**
 *     （`utils/form-rules.ts#readonlyReason`）；
 *   · **错误**：`errors[字段码]` 挂到对应控件下方（含 40308 这类「未登记字段」的键）；
 *   · **字典**：选项来自 `GET /forms/dicts/{dictType}/items`（由页面统一取好后按 `dictType` 传入），
 *     组件**不做任何硬编码选项**。
 *
 * ⚠ 本组件不是边界：服务端 `FormStateWriteGuard` 才是（越权写入 403/40304）。
 */
import { computed, ref } from 'vue'
import FormFieldControl from '@/components/FormFieldControl.vue'
import type {
  FormDictCache,
  FormField,
  FormFieldIssue,
  FormJsonValue,
  FormOption,
  FormSchema,
  FormWriteState,
} from '@/types/form'
import { groupFields, resolveFieldReadonlyReason } from '@/utils/form-rules'

const props = defineProps<{
  schema: FormSchema
  modelValue: Record<string, FormJsonValue>
  state: FormWriteState | null
  /** 逐字段错误索引（`utils/form-rules.ts#buildIssueIndex`） */
  errors: Record<string, FormFieldIssue[]>
  /**
   * 金额字段是否可写（PRD §5.3）。
   *
   * 由页面用 `utils/form-rules.ts#resolveAmountWrite` 算出：**以服务端
   * `GET /forms/{formType}/field-groups` 的 `amountPolicy.writable` 为准**
   * （2026-10-04 起该字段按当前登录主体计算，与写路径 40306 同结论），
   * 接口取不到时才回落到本地角色码（admin / finance_owner）兜底。
   */
  amountWritable: boolean
  /** 金额判定说明（可写/只读都要能解释清楚；可写时给「以服务端 40306 为准」的兜底提示） */
  amountNote: string
  /** 字典缓存（按 dictType） */
  dictCache: FormDictCache
  /** 字典加载中标记（按 dictType） */
  dictLoading: Record<string, boolean>
  /** 通讯录候选人（user 字段） */
  userOptions: FormOption[]
  /** 组织候选人（org 字段） */
  orgOptions: FormOption[]
  /** 候选人数据源不可用（降级为手填并说明） */
  pickerUnavailable: boolean
  /** 未绑定到任何字段的错误（如 40308 夹带整单拒绝时的兜底展示口径） */
  unboundErrors: string[]
  /**
   * 强制只读的原因（非空时**所有字段**置灰并显示该原因）。
   *
   * 用途：单据详情页是只读视图（编辑在「填单」页，那里才按服务端白名单开放字段）。
   * 用「状态白名单」表达只读会给出误导性原因（例如草稿态本该全可写），
   * 因此这里用一个显式的强制只读原因，理由与展示都给得准确。
   */
  forceReadonlyReason?: string
}>()

const emit = defineEmits<{ (event: 'update:modelValue', value: Record<string, FormJsonValue>): void }>()

/** 折叠的分组 id（`collapsible=false` 的分组不允许折叠） */
const collapsed = ref<Set<string>>(new Set())

const groups = computed(() => groupFields(props.schema))

function isCollapsed(sectionId: string): boolean {
  return collapsed.value.has(sectionId)
}

function toggleSection(sectionId: string, collapsible: boolean): void {
  if (!collapsible) return
  const next = new Set(collapsed.value)
  if (next.has(sectionId)) next.delete(sectionId)
  else next.add(sectionId)
  collapsed.value = next
}

/** 某字段的候选项（字典优先；字典未返回时退回模板内联选项） */
function optionsOf(field: FormField): FormOption[] {
  if (field.dictType) {
    const items = props.dictCache[field.dictType] ?? []
    return items.map((item) => ({
      value: item.itemCode,
      label: item.itemName,
      enabled: item.status === 'active',
      sortNo: item.sortNo,
    }))
  }
  return field.options
}

function optionsLoadingOf(field: FormField): boolean {
  return field.dictType ? props.dictLoading[field.dictType] === true : false
}

function errorsOf(field: FormField): string[] {
  const list = props.errors[field.code] ?? []
  return list.map((issue) => issue.message)
}

/**
 * 字段是否可写 / 只读原因（**状态白名单 ∧ 金额角色**，两条正交规则一次判清）。
 *
 * 判定集中在 `utils/form-rules.ts#resolveFieldReadonlyReason`（可脱离 Vue 自测），
 * 组件只负责把结果铺到控件上。
 */
function editabilityOf(field: FormField): { writable: boolean; reason: string } {
  const reason = resolveFieldReadonlyReason(field, {
    state: props.state,
    amountWritable: props.amountWritable,
    amountReadonlyReason: props.amountNote,
    forceReadonlyReason: props.forceReadonlyReason,
  })
  return { writable: reason === null, reason: reason ?? '' }
}

function writableOf(field: FormField): boolean {
  return editabilityOf(field).writable
}

function reasonOf(field: FormField): string {
  return editabilityOf(field).reason
}

function updateField(code: string, value: FormJsonValue): void {
  emit('update:modelValue', { ...props.modelValue, [code]: value })
}
</script>

<template>
  <div class="form-renderer">
    <!-- 整单级错误（如 40308 夹带未登记字段时无法归到某个已渲染字段） -->
    <div v-if="unboundErrors.length > 0" class="unbound">
      <p class="unbound-title">无法归到具体字段的校验/拒绝信息</p>
      <ul>
        <li v-for="(message, index) in unboundErrors" :key="index">{{ message }}</li>
      </ul>
    </div>

    <section v-for="group in groups" :key="group.section.id" class="group">
      <header class="group-head">
        <button
          class="group-title"
          type="button"
          :disabled="!group.section.collapsible"
          @click="toggleSection(group.section.id, group.section.collapsible)"
        >
          <span class="caret" aria-hidden="true">{{ group.section.collapsible ? (isCollapsed(group.section.id) ? '▸' : '▾') : '·' }}</span>
          {{ group.section.title }}
          <i class="count oa-tnum">{{ group.fields.length }}</i>
        </button>
        <span class="group-print">打印稿分组：{{ group.section.printTitle }}</span>
      </header>

      <div v-show="!isCollapsed(group.section.id)" class="fields">
        <FormFieldControl
          v-for="field in group.fields"
          :key="field.code"
          :field="field"
          :model-value="modelValue[field.code] ?? null"
          :writable="writableOf(field)"
          :readonly-reason="reasonOf(field)"
          :errors="errorsOf(field)"
          :options="optionsOf(field)"
          :options-loading="optionsLoadingOf(field)"
          :amount-writable="amountWritable"
          :amount-note="amountNote"
          :user-options="userOptions"
          :org-options="orgOptions"
          :picker-unavailable="pickerUnavailable"
          @update:model-value="(value: FormJsonValue) => updateField(field.code, value)"
        />
      </div>
    </section>

    <p v-if="schema.fields.length === 0" class="empty">该表单模板没有任何字段（schema 为空）。</p>
  </div>
</template>

<style scoped>
.form-renderer {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.group {
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-md);
  background: var(--oa-color-canvas);
  overflow: hidden;
}

.group-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--oa-space-sm);
  padding: var(--oa-space-xs) var(--oa-space-md);
  background: var(--oa-color-canvas-subtle);
  border-bottom: 1px solid var(--oa-color-hairline);
}

.group-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--oa-color-ink);
  font: var(--oa-font-title-section);
  cursor: pointer;
}

.group-title:disabled {
  cursor: default;
}

.caret {
  color: var(--oa-color-ink-subtle);
  font-size: 12px;
}

.count {
  padding: 0 5px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
  font-style: normal;
}

.group-print {
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--oa-space-md) var(--oa-space-lg);
  padding: var(--oa-space-md);
}

.unbound {
  padding: var(--oa-space-sm);
  border: 1px solid var(--oa-color-error);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-canvas-subtle);
}

.unbound-title {
  font: var(--oa-font-label);
  color: var(--oa-color-error);
}

.unbound ul {
  margin: 4px 0 0;
  padding-left: 18px;
  font: var(--oa-font-caption);
}

.empty {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-subtle);
}

@media (max-width: 1024px) {
  .fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
