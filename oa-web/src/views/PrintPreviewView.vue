<script setup lang="ts">
/**
 * oa-web · A4 打印预览
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md`「打印规格（审批单 A4）」整节 + `DESIGN.print-a4.html`
 *   · 版式映射：事项单统一走「子公司内部审批单」版式；合同/资金集团层用集团版式、
 *     子公司层用内部审批单；印鉴证照用印鉴证照版式
 *   · 纸张 A4 纵向 210×297mm，@page margin 0，版心由 padding 12mm 12mm 10mm 控制
 *   · 圆角全部 0、禁止投影、不使用任何主题色（全部黑色）、不使用状态色块
 *   · 正文 9.5pt / 附注 8.5pt / 抬头 16pt；表格全表 1pt（.35mm）实线
 *   · **正式 A4 打印稿的签名栏一律为空栏**：屏幕预览可显示已签缩略图与时间戳，
 *     打印时由 `print-a4.scss` 强制隐藏（.oa-sign-stamp）
 *   · 页脚三栏每页重复：左「系统名 · 单据名」/ 中「单号 · 模板版本 · 生成时间」/ 右「第 X / Y 页」
 *   · 复选框使用文字符号 ☑ / ☐，不使用 input[type=checkbox]
 *   · 写值区 3–4% 极浅灰底，标签列不填色
 * 实现约束：打印稿不复用业务界面组件类（.btn/.card/.pill），样式完全独立。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPrintDocument } from '@/api/task'
import { demoPrintDocument } from '@/api/demo'
import { formatAmount, formatWan } from '@/utils/format'
import type { FormType, PrintDocument, PrintRow } from '@/types/api'

const props = defineProps<{ id: string }>()

const route = useRoute()

const doc = ref<PrintDocument>({ ...demoPrintDocument, instanceId: props.id })
const loading = ref(false)
/** 屏幕预览专用：是否显示已签缩略图（打印稿永远空栏） */
const showSignedPreview = ref(true)

const variantOptions: Array<{ value: PrintDocument['variant']; label: string }> = [
  { value: 'subsidiary-internal', label: 'P3 子公司内部审批单（事项单复用）' },
  { value: 'group-contract', label: 'P1 集团合同类文件流转审批单' },
  { value: 'group-fund', label: 'P2 集团资金审批单' },
  { value: 'seal-license', label: 'P4 印鉴证照使用审批单' },
]

const formType = computed<FormType>(() => (route.query.type as FormType | undefined) ?? 'fund')

/** 版式来源说明：与 DESIGN.md「版式来源」表逐行对应 */
const variantNote = computed(() => {
  switch (formType.value) {
    case 'matter':
      return '事项审批单：不论集团层或子公司层办理，统一使用「子公司内部审批单」版式（V0.4 拍板）。'
    case 'fund':
      return '资金审批单：集团层办理用「集团资金审批单」版式，子公司层办理用「子公司内部审批单」版式。'
    case 'contract':
      return '合同审批单：集团层办理用「集团合同类文件流转审批单」版式（含公文接收及处理重复块），子公司层用内部审批单版式。'
    case 'seal_cert':
    default:
      return '印鉴证照审批单：实单无对应件，沿用集团单版式推导；归还状态 / 归还日期为三态只读的唯一例外。'
  }
})

const pages = computed(() => Math.max(1, doc.value.footer.totalPages))

onMounted(async () => {
  await load()
})

watch(
  () => [props.id, formType.value],
  async () => {
    await load()
  },
)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchPrintDocument(props.id, formType.value)
    doc.value = { ...result, instanceId: props.id }
  } catch {
    doc.value = { ...demoPrintDocument, instanceId: props.id }
  } finally {
    loading.value = false
  }
}

/** 打印：浏览器 A4 / 100% / 无边距（DESIGN.md 实现约束） */
function doPrint(): void {
  if (loading.value) return
  ElMessage({ type: 'info', message: '请在打印对话框中选择：A4、纵向、缩放 100%、边距「无」' })
  window.setTimeout(() => window.print(), 200)
}

function isGroupRow(row: PrintRow): boolean {
  return typeof row.group === 'string'
}

function cellSpan(row: PrintRow, index: number): number {
  if (row.cells.length === 1) return 3
  if (row.cells[index]?.span) return row.cells[index].span as number
  return 1
}
</script>

<template>
  <div class="oa-print-root">
    <!-- ============ 屏幕工具条（打印时自动隐藏） ============ -->
    <header class="oa-print-toolbar oa-screen-only">
      <b>A4 打印预览</b>
      <span class="mono">{{ doc.bizNo }}</span>
      <span class="oa-tag is-info">{{ doc.footer.documentName }}</span>

      <label class="tool-field">
        版式
        <select :value="doc.variant" @change="doc.variant = ($event.target as HTMLSelectElement).value as PrintDocument['variant']">
          <option v-for="opt in variantOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </label>

      <label class="tool-check">
        <input v-model="showSignedPreview" type="checkbox" />
        <span>屏幕预览显示已签缩略图（<b>正式打印一律空栏</b>）</span>
      </label>

      <span class="spacer" />

      <button class="tool-btn" type="button" @click="doPrint">打印 / 导出 PDF</button>
    </header>

    <p class="oa-print-variant-note oa-screen-only">{{ variantNote }}</p>

    <!-- ============ A4 纸面（1:1 毫米尺寸渲染） ============ -->
    <article v-for="page in pages" :key="page" class="oa-sheet">
      <!-- 顶部信息条：左「申请编号」、右「打印人 / 打印时间」 -->
      <div class="oa-print-meta">
        <span class="left">申请编号：<i class="mono">{{ doc.bizNo }}</i></span>
        <span class="right">
          打印人：{{ doc.printerName }}<br />
          打印时间：<i class="mono">{{ doc.generatedAt.slice(0, 16).replace('T', ' ') }}</i>
        </span>
      </div>

      <!-- 单据抬头：居中、加粗、16pt、字距 2px -->
      <h1 class="oa-print-title">
        {{ doc.title }}
        <span v-if="doc.subtitle" class="sub">{{ doc.subtitle }}</span>
      </h1>

      <!-- 主表：全表 1pt 实线、无圆角、标签列不填色、写值区极浅灰 -->
      <table class="oa-print-table">
        <colgroup>
          <col style="width: 22mm" />
          <col />
          <col style="width: 22mm" />
          <col />
        </colgroup>
        <tbody>
          <template v-for="(row, rowIndex) in doc.rows" :key="rowIndex">
            <tr v-if="isGroupRow(row)" class="grp">
              <td colspan="4">{{ row.group }}</td>
            </tr>
            <tr v-else>
              <template v-for="(cell, cellIndex) in row.cells" :key="cellIndex">
                <th class="lbl">{{ cell.label }}</th>
                <td
                  class="val"
                  :class="{ c: cell.align === 'center', r: cell.align === 'right' }"
                  :colspan="cellSpan(row, cellIndex) - 1 || undefined"
                >
                  <span :class="{ amount: cell.align === 'right', mono: cell.align === 'right' }">
                    {{ cell.value }}
                  </span>
                  <span
                    v-if="cell.align === 'right' && formatWan(cell.value.replace(/,/g, ''))"
                    class="wan"
                  >
                    （{{ formatWan(cell.value.replace(/,/g, '')) }}）
                  </span>
                </td>
              </template>
            </tr>
          </template>

          <!-- 附件清单（打印亦需可见） -->
          <tr class="grp">
            <td colspan="4">附件</td>
          </tr>
          <tr v-if="doc.attachments.length === 0">
            <td class="val tiny" colspan="4">无附件</td>
          </tr>
          <tr v-for="(file, index) in doc.attachments" :key="file.attachmentId">
            <th class="lbl">附件 {{ index + 1 }}</th>
            <td class="val tiny" colspan="3">
              {{ file.fileName }}
              <span class="mono">（{{ Math.round(file.fileSize / 1024) }} KB · 第 {{ file.round }} 轮）</span>
            </td>
          </tr>

          <!-- 审批记录流水（子公司内部审批单版式：阶段 · 处理人/动作/时间 · 意见） -->
          <tr class="grp">
            <td colspan="4">审批记录</td>
          </tr>
          <tr v-for="(node, index) in doc.routingBlocks.length ? doc.routingBlocks : []" :key="index">
            <td class="val tiny" colspan="4">{{ node }}</td>
          </tr>
          <tr v-if="doc.routingBlocks.length === 0">
            <th class="lbl">发起</th>
            <td class="val tiny" colspan="3">
              {{ doc.printerName }} / 提交 /
              <span class="mono">{{ doc.generatedAt.slice(0, 16).replace('T', ' ') }}</span>
            </td>
          </tr>
          <tr>
            <th class="lbl">审批中</th>
            <td class="val tiny" colspan="3">
              按 7 个审批节点依次流转；流转 + 回退合计 ≤5 次，同节点回退 ≤2 次，补件 ≤3 次。
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 打印签名栏：正文只输出空栏「签名：____ 年 月 日」 -->
      <section class="oa-sign-block">
        <div v-for="block in doc.signatureBlocks" :key="block.label" class="oa-sign-row">
          <div class="oa-sign-blank">
            <span class="lbl">{{ block.label }}：签名</span>
            <span class="line">______________________ 年 月 日</span>
          </div>

          <!-- 屏幕预览专用：已签缩略图 + 时间戳；@media print 中被强制隐藏 -->
          <div v-if="showSignedPreview" class="oa-sign-stamp oa-screen-only">
            <span class="ts">
              屏幕预览：{{ block.previewSignedBy || doc.printerName }} ·
              {{ block.previewSignedAt || doc.generatedAt.slice(0, 10) }}
            </span>
          </div>
        </div>
      </section>

      <!-- 复选框示例：使用文字符号，不使用 input[type=checkbox] -->
      <section class="oa-print-checkbox">
        <span class="box off">计划外</span>
        <span class="box on">计划内</span>
        <span class="box off">现金</span>
        <span class="box on">银行转账</span>
      </section>

      <!-- 页脚三栏：每页重复 -->
      <footer class="oa-print-footer">
        <span>{{ doc.footer.systemName }} · {{ doc.footer.documentName }}</span>
        <span class="mono">
          {{ doc.footer.bizNo }} · 模板 {{ doc.footer.templateVersion }} · 生成
          {{ doc.footer.generatedAt }}
        </span>
        <span class="pnum">第 {{ page }} / {{ pages }} 页</span>
      </footer>
    </article>

    <!-- ============ 规则说明（屏幕专属） ============ -->
    <section class="oa-rules oa-screen-only">
      <h2>打印规格自检</h2>
      <ul>
        <li>纸张 <code>@page { size: A4; margin: 0 }</code>，版心 <code>padding: 12mm 12mm 10mm</code>（186mm）。</li>
        <li>圆角全部 0px、完全禁止投影、不使用任何主题色与状态色块；黑白复印后信息不得丢失。</li>
        <li>正文 9.5pt / 附注 8.5pt / 抬头 16pt，不随屏幕缩放；表格全表 1pt（.35mm）实线。</li>
        <li>
          <b>正式打印签名栏一律空栏</b>（供手签）：空栏格式
          <code>签名：____ 年 月 日</code>，不打印任何签名图、缩略图、姓名或时间戳；
          屏幕预览与打印稿的差异仅此一处。
        </li>
        <li>复选框使用文字符号 ☑ / ☐，不使用 <code>input[type=checkbox]</code>。</li>
        <li>金额：<i class="mono">{{ formatAmount('1250000.00') }}</i>，≥100 万时同时给出万元换算。</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.oa-print-root {
  background: var(--oa-color-surface-1);
  min-height: 100%;
}

/* ---------------- 屏幕工具条 ---------------- */
.oa-print-toolbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-sm);
  padding: var(--oa-space-xs) var(--oa-space-md);
  background: var(--oa-color-inverse-canvas);
  color: var(--oa-color-inverse-ink);
  font: var(--oa-font-caption);
}

.oa-print-toolbar b {
  font: var(--oa-font-body-sm);
}

.oa-print-toolbar .mono,
.mono {
  font: var(--oa-font-mono);
  font-variant-numeric: tabular-nums;
}

.tool-field,
.tool-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--oa-color-inverse-ink-muted);
}

.tool-field select {
  height: 26px;
  border: 1px solid var(--oa-color-inverse-surface-2);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-inverse-surface-1);
  color: var(--oa-color-inverse-ink);
  font: var(--oa-font-caption);
}

.tool-check b {
  color: var(--oa-color-inverse-ink);
}

.spacer {
  flex: 1 1 auto;
}

.tool-btn {
  height: 28px;
  padding: 0 var(--oa-space-sm);
  border: 0;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
  font: var(--oa-font-caption);
  cursor: pointer;
}

.oa-print-variant-note {
  max-width: var(--oa-print-page-w);
  margin: var(--oa-space-sm) auto 0;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

/* ---------------- 纸面补充（版式主体在 print-a4.scss 里） ---------------- */
.oa-sign-block {
  margin-top: 6mm;
}

.oa-sign-row + .oa-sign-row {
  margin-top: 3mm;
}

.oa-print-checkbox {
  display: flex;
  flex-wrap: wrap;
  gap: 2mm;
  margin-top: 3mm;
}

.oa-print-table .wan {
  font-family: var(--oa-font-family);
  font-size: 8.5pt;
  font-weight: 400;
}

/* ---------------- 规则说明 ---------------- */
.oa-rules {
  max-width: var(--oa-print-page-w);
  margin: var(--oa-space-lg) auto var(--oa-space-section);
  padding: var(--oa-space-md);
  background: var(--oa-color-canvas);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-md);
}

.oa-rules h2 {
  margin-bottom: var(--oa-space-xs);
  font: var(--oa-font-title-section);
  color: var(--oa-color-ink);
}

.oa-rules ul {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.oa-rules code {
  padding: 0 4px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas-subtle);
  font: var(--oa-font-mono);
  color: var(--oa-color-ink);
}

.oa-rules b {
  color: var(--oa-color-ink);
}
</style>
