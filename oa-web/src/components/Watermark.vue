<script setup lang="ts">
/**
 * oa-web · 水印层（「姓名 + 工号」防截屏泄露水印）
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md` › Components › Feedback & Overlays › watermark
 *       + `doc/prd-0.1.md` 6.8「水印（REQ-USER-004）」与 **验收标准 AC-44**
 *       + `DESIGN.md` 附录 C（6.8 由 `watermark` 令牌承接）
 *       + `normify-oa/modules/oa/portal/detail/watermark.md`
 *         `normify-oa/modules/oa/portal/h5/watermark.md`
 *
 * 硬性口径（逐条对齐 AC-44）：
 *   · 内容为「姓名 + 工号」
 *   · 透明度 **5%–8%**（越界自动夹回区间，见 safeOpacity）
 *   · 旋转 **-24°**（固定口径，props 仅用于调试）
 *   · 平铺间距 **240 × 160px**
 *   · `pointer-events: none` + `user-select: none`
 *   · **不遮挡按钮与表单值**：
 *       ① 透明度恒 ≤8%，不足影响阅读；
 *       ② 水印层 `pointer-events: none`，绝不拦截点击与输入；
 *       ③ 文件末尾的全局规则把按钮、输入控件、表单值与状态徽标抬到水印层之上
 *          （z-index 3 > 水印 2），使水印像素永不覆盖在可交互元素之上。
 *   · H5 与桌面详情页**都启用**：同一个组件，按断点切换平铺密度（H5 更疏）。
 *
 * 实现方式：内联 SVG（data URI）平铺——纯字符串、零依赖、任意 DPR 下清晰，
 *           不引用任何外部图片或 CDN（REQ-NFR-001 私有化无公网）。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 姓名（与工号一起构成水印文本） */
    name?: string
    /** 工号 */
    employeeNo?: string
    /** 开关：关闭时整层不渲染 */
    enabled?: boolean
    /** 透明度，取值必须落在 0.05–0.08；越界自动夹回 */
    opacity?: number
    /** 旋转角，默认 -24°（口径固定，仅调试用） */
    rotate?: number
    /** 平铺间距 */
    gapX?: number
    gapY?: number
    /** 直接指定完整文本，优先于 name/employeeNo 拼接 */
    text?: string
    /** 字号，默认 12px（typography.caption） */
    fontSize?: number
    /**
     * 覆盖范围：
     *   'parent'（默认）填满最近的定位祖先，用于详情页内容区
     *   'viewport'      固定覆盖整个视口，用于 H5 全屏页
     */
    scope?: 'parent' | 'viewport'
    /** 水印文字色，默认 {colors.ink} */
    color?: string
  }>(),
  {
    name: '',
    employeeNo: '',
    enabled: true,
    opacity: 0.06,
    rotate: -24,
    gapX: 240,
    gapY: 160,
    text: '',
    fontSize: 12,
    scope: 'parent',
    color: '#14181f', // {colors.ink}
  },
)

/** 透明度硬约束：5%–8%（AC-44），任何来源越界都夹回区间 */
const OPACITY_MIN = 0.05
const OPACITY_MAX = 0.08

const safeOpacity = computed(() => {
  const value = Number(props.opacity)
  if (!Number.isFinite(value)) return 0.06
  return Math.min(OPACITY_MAX, Math.max(OPACITY_MIN, value))
})

/** 水印文本：姓名 + 工号 */
const watermarkText = computed(() => {
  const explicit = props.text.trim()
  if (explicit) return explicit
  const parts = [props.name.trim(), props.employeeNo.trim()].filter(Boolean)
  return parts.join(' · ')
})

const visible = computed(() => props.enabled && watermarkText.value.length > 0)

const tileWidth = computed(() => Math.max(80, props.gapX))
const tileHeight = computed(() => Math.max(60, props.gapY))

/**
 * 内联 SVG 平铺图案（data URI）。
 * 文字锚点落在单元格中心，旋转 -24° 后仍在单元格内。
 */
const svgBackground = computed(() => {
  const w = tileWidth.value
  const h = tileHeight.value
  const escaped = watermarkText.value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')

  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
    `<g transform="translate(${w / 2} ${h / 2}) rotate(${props.rotate})">` +
    `<text x="0" y="0" text-anchor="middle" dominant-baseline="middle" ` +
    `font-family="Inter, PingFang SC, Microsoft YaHei, Noto Sans SC, system-ui, sans-serif" ` +
    `font-size="${props.fontSize}" fill="${props.color}">${escaped}</text>` +
    `</g></svg>`

  return `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`
})

const layerStyle = computed(() => ({
  opacity: String(safeOpacity.value),
  backgroundImage: svgBackground.value,
  backgroundRepeat: 'repeat',
  backgroundSize: `${tileWidth.value}px ${tileHeight.value}px`,
}))
</script>

<template>
  <!--
    结构：内容在前、水印在后（水印在内容之上平铺，但指针穿透且低于可交互控件）。
    aria-hidden：水印是防泄露标记，不应进入无障碍树，避免读屏重复播报。
  -->
  <div class="oa-watermark-host">
    <slot />
    <div
      v-if="visible"
      class="oa-watermark-layer"
      :class="{ 'is-viewport': scope === 'viewport' }"
      :style="layerStyle"
      aria-hidden="true"
    />
  </div>
</template>

<style scoped>
/* 定位宿主：为水印层提供 contain block，不改变自身布局 */
.oa-watermark-host {
  position: relative;
}

.oa-watermark-layer {
  position: absolute;
  inset: 0;
  /* 水印在内容之上（保证可见），但低于一切可交互控件与表单值（z-index: 3） */
  z-index: 2;
  pointer-events: none; /* 绝不拦截点击与输入 */
  user-select: none;
  -webkit-user-select: none;
  overflow: hidden;
}

.oa-watermark-layer.is-viewport {
  position: fixed;
}

/* H5：水印平铺更疏，避免小屏文字过密 */
@media (max-width: 768px) {
  .oa-watermark-layer {
    background-size: 160px 120px !important;
  }
}

/* 打印时水印不进入纸面：防泄露水印只约束屏幕 */
@media print {
  .oa-watermark-layer {
    display: none !important;
  }
}
</style>

<!--
  非 scoped：把「按钮、表单值与状态徽标」抬到水印层之上，
  逐字落实 AC-44 的「水印不遮挡按钮与表单值」。
  这些选择器必须作用于 Element Plus 生成的真实 DOM（含 teleport 内容），
  因此不能加 scoped。
-->
<style>
.oa-watermark-host .el-button,
.oa-watermark-host .el-input,
.oa-watermark-host .el-textarea,
.oa-watermark-host .el-select,
.oa-watermark-host .el-date-editor,
.oa-watermark-host .el-checkbox,
.oa-watermark-host .el-radio,
.oa-watermark-host .el-switch,
.oa-watermark-host .el-upload,
.oa-watermark-host .el-pagination,
.oa-watermark-host .el-tabs,
.oa-watermark-host .el-table,
.oa-watermark-host .el-form-item__error,
.oa-watermark-host .oa-wm-raise,
.oa-watermark-host .oa-pill,
.oa-watermark-host .oa-tag,
.oa-watermark-host .oa-sign-stamp,
.oa-watermark-host .oa-amount {
  position: relative;
  z-index: 3;
}
</style>
