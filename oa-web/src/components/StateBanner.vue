<script setup lang="ts">
/**
 * oa-web · 三态提示条
 * ----------------------------------------------------------------------------
 * 来源：`doc/tech-design.md` §5.4「三态读写与表单模板」
 *       + `doc/prd-0.1.md` 6.6 异常路径（驳回 / 回退 / 补件）
 *       + `DESIGN.md` 附录 C「6.6 异常路径 → 状态徽标 + 补件态提示；
 *         待补件期主字段只读（印鉴单的归还状态/归还日期为唯一例外）」
 *
 * 四态语义（供详情页与列表页共用）：
 *   草稿        —— 可编辑（info）
 *   审批中      —— 只读（info）
 *   待补件      —— 仅「补件说明 + 附件」可编辑（warning）
 *   已办结      —— 只读（success）
 *
 * 颜色只从状态语义色里选（附录 B），不新造配色。
 */
import { computed } from 'vue'
import { describeState, type ReadWriteContext, type StateBannerModel } from '@/utils/readwrite'

const props = withDefaults(
  defineProps<{
    /** 三态上下文（由详情头部织出，见 utils/readwrite.contextFromHeader） */
    context: ReadWriteContext
    /** 覆盖默认文案（例如补件截止日期） */
    detailExtra?: string
    /** 是否可关闭；默认常驻（三态是单据的固有状态，不应被关掉） */
    closable?: boolean
    /** 紧凑模式：用于列表栏内联展示 */
    compact?: boolean
  }>(),
  {
    detailExtra: '',
    closable: false,
    compact: false,
  },
)

const emit = defineEmits<{ (e: 'dismiss'): void }>()

const model = computed<StateBannerModel>(() => describeState(props.context))

const detail = computed(() =>
  props.detailExtra ? `${model.value.detail} ${props.detailExtra}` : model.value.detail,
)

/** 三态 → 语义样式（沿用全局 .oa-* 状态类，不引入新色值） */
const toneClass = computed(() => `is-${model.value.tone}`)
</script>

<template>
  <section class="oa-state-banner" :class="[toneClass, { 'is-compact': compact }]" role="status">
    <span class="dot" aria-hidden="true" />

    <div class="body">
      <p class="title">{{ model.title }}</p>
      <p v-if="!compact" class="detail">{{ detail }}</p>
    </div>

    <ul v-if="!compact" class="legend">
      <li v-if="context.state === 'draft'">主字段：<b>可编辑</b></li>
      <li v-else>主字段：<b>只读</b></li>

      <li v-if="model.showSupplementInputs">可写：<b>补件说明 + 附件</b></li>
      <li v-else-if="context.state === 'supplement'">可写：<b>仅补件说明 + 附件</b></li>

      <li v-if="model.showApprovalActions">审批操作：<b>可用</b></li>
      <li v-if="model.showSupplementInputs">审批操作：<b>暂停</b></li>

      <li v-if="model.showSealReturnException" class="exception">
        印鉴单例外：<b>归还状态 / 归还日期可改</b>
      </li>
    </ul>

    <button v-if="closable" class="close" type="button" aria-label="关闭提示" @click="emit('dismiss')">
      ×
    </button>
  </section>
</template>

<style scoped>
.oa-state-banner {
  display: flex;
  align-items: flex-start;
  gap: var(--oa-space-sm);
  padding: var(--oa-space-sm) var(--oa-space-md);
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-info-surface);
  color: var(--oa-color-ink);
  font: var(--oa-font-body-sm);
}

.oa-state-banner.is-compact {
  align-items: center;
  padding: var(--oa-space-xs) var(--oa-space-sm);
}

/* 状态色只出现在左侧竖条与圆点上，不做整块饱和填充（DESIGN.md 状态色规则） */
.dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: var(--oa-radius-full);
  background: var(--oa-color-info);
}

.is-compact .dot {
  margin-top: 0;
}

.body {
  flex: 1 1 auto;
  min-width: 0;
}

.title {
  margin: 0;
  font-weight: 500;
  color: var(--oa-color-ink);
}

.detail {
  margin: 2px 0 0;
  color: var(--oa-color-ink-muted);
  line-height: var(--oa-line-height-body-sm);
}

.legend {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  gap: var(--oa-space-xs) var(--oa-space-md);
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--oa-color-ink-muted);
  font: var(--oa-font-caption);
}

.legend b {
  font-weight: 500;
  color: var(--oa-color-ink);
}

.legend .exception {
  color: var(--oa-color-warning);
}

.legend .exception b {
  color: var(--oa-color-warning);
}

.close {
  flex: none;
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  color: var(--oa-color-ink-subtle);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  border-radius: var(--oa-radius-xs);
}

.close:hover {
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink);
}

/* 三态语义底：全部取状态语义色的 surface 令牌 */
.oa-state-banner.is-info {
  background: var(--oa-color-info-surface);
}

.oa-state-banner.is-info .dot {
  background: var(--oa-color-info);
}

.oa-state-banner.is-warning {
  background: var(--oa-color-warning-surface);
}

.oa-state-banner.is-warning .dot {
  background: var(--oa-color-warning);
}

.oa-state-banner.is-success {
  background: var(--oa-color-success-surface);
}

.oa-state-banner.is-success .dot {
  background: var(--oa-color-success);
}

.oa-state-banner.is-neutral {
  background: var(--oa-color-neutral-surface);
}

.oa-state-banner.is-neutral .dot {
  background: var(--oa-color-neutral);
}

/* H5：图例折行显示，保持信息不丢 */
@media (max-width: 768px) {
  .oa-state-banner {
    flex-wrap: wrap;
  }

  .legend {
    width: 100%;
  }
}
</style>
