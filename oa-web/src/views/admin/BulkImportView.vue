<script setup lang="ts">
/**
 * oa-web · 组织人员批量导入（阶段 1.8）
 * ----------------------------------------------------------------------------
 * 来源：`doc/import-spec.md`（V1.3）
 *   · §2.1 五步流水线（**顺序不可调换**）：①组织 → ②人员 → ③负责人 → ④岗位 → ⑤角色分配；
 *     每一步的输出是下一步的输入（组织解析为 id、账号解析为 id）。
 *   · §4.1 文件级硬约束：**UTF-8 带 BOM** 的 CSV、表头逐字一致（改名/增列/减列/换序一律拒绝）；
 *   · §5.1/§5.4 校验报告：逐行「行号 + 列 + 值 + 错误码 + 中文说明」，一次列全不中断；
 *   · §5.3 两段式：preview（干跑，不写任何业务表）→ commit（**单事务**；存在 error 时整批拒绝、错误零落库）；
 *   · §6.1 幂等：同文件重复导入不产生重复数据（第二次为 0 新增 / 0 更新 / N 跳过）；
 *   · §9.1/§9.2 导出：列与模板一致（往返编辑），主数据导出**仅系统管理员**。
 *
 * 权限（前端只决定「渲不渲染」，服务端才是裁决方）：
 *   系统管理员或分公司流程管理员；分公司管理员逐行受数据域约束，
 *   域外行会被服务端以 `E-XXX-020` fail-closed 拒绝整批（前端只如实展示该报告）。
 *
 * ⚠ 初始口令（仅新增人员时返回）**只在本次响应里出现**：本页把它放在「一次性口令清单」里
 *   提示管理员线下分发，不做本地持久化（import-spec T-03）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  commitImport,
  downloadBlob,
  exportMasterData,
  fetchImportKinds,
  previewImport,
} from '@/api/bulk'
import { canExportMasterData, canImportOrgUser } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import type { ImportKindMeta, ImportReport } from '@/types/bulk'

const userStore = useUserStore()

const importable = computed(() => canImportOrgUser(userStore))
const exportable = computed(() => canExportMasterData(userStore))

const kinds = ref<ImportKindMeta[]>([])
const activeKind = ref('org')
const file = ref<File | null>(null)
const report = ref<ImportReport | null>(null)
const busy = ref(false)
const exportBusy = ref('')

/** 五步顺序说明（§2.1：顺序不可调换，后一步依赖前一步的落库结果） */
const STEP_ORDER = ['org', 'user', 'org-leader', 'user-position', 'user-role'] as const

const currentMeta = computed(() => kinds.value.find((item) => item.kind === activeKind.value))
const stepIndex = computed(() => STEP_ORDER.indexOf(activeKind.value as (typeof STEP_ORDER)[number]) + 1)

const errorFindings = computed(
  () => report.value?.findings.filter((item) => item.severity === 'error') ?? [],
)
const warningFindings = computed(
  () => report.value?.findings.filter((item) => item.severity === 'warning') ?? [],
)

onMounted(async () => {
  try {
    kinds.value = await fetchImportKinds()
  } catch (error) {
    reportApiError(error, '导入类型加载失败')
  }
})

function pickKind(kind: string): void {
  activeKind.value = kind
  file.value = null
  report.value = null
}

function onFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  file.value = target.files && target.files.length > 0 ? target.files[0] : null
  report.value = null
}

/** 文件基本形态预检（编码/表头由服务端裁决，这里只挡住明显的手误） */
function fileLooksValid(): boolean {
  if (!file.value) {
    ElMessage.warning('请先选择 CSV 文件（UTF-8 带 BOM，可直接使用模板另存）')
    return false
  }
  if (!file.value.name.toLowerCase().endsWith('.csv')) {
    ElMessage.warning('导入只接受 CSV（UTF-8 BOM）；xlsx 请先「另存为 CSV UTF-8（逗号分隔）」')
    return false
  }
  if (currentMeta.value && file.value.name !== currentMeta.value.file) {
    ElMessage.warning(`当前步骤期望文件名为 ${currentMeta.value.file}，请核对模板（import-spec E-FILE-001）`)
    return false
  }
  return true
}

async function runPreview(): Promise<void> {
  if (!fileLooksValid()) return
  busy.value = true
  try {
    report.value = await previewImport(activeKind.value, file.value as File)
    ElMessage({
      type: report.value.ok ? 'success' : 'warning',
      message: report.value.ok
        ? `干跑校验通过：${report.value.totalRows} 行，未写任何数据`
        : `干跑校验发现 ${report.value.errors} 个 error（整批不会落库）`,
    })
  } catch (error) {
    reportApiError(error, '干跑校验失败')
  } finally {
    busy.value = false
  }
}

async function runCommit(): Promise<void> {
  if (!fileLooksValid()) return
  busy.value = true
  try {
    report.value = await commitImport(activeKind.value, file.value as File)
    ElMessage({
      type: report.value.ok ? 'success' : 'error',
      message: report.value.ok
        ? `导入完成：新增 ${report.value.added} / 更新 ${report.value.updated} / 跳过 ${report.value.skipped}`
        : `整批拒绝（错误零落库）：${report.value.errors} 个 error，请按报告修正后重传`,
    })
  } catch (error) {
    reportApiError(error, '导入失败')
  } finally {
    busy.value = false
  }
}

async function runExport(which: 'org' | 'user' | 'org-leader' | 'user-position' | 'user-role' | 'audit-log'): Promise<void> {
  exportBusy.value = which
  try {
    const { blob, fileName } = await exportMasterData(which)
    downloadBlob(blob, fileName)
    ElMessage.success(`已导出 ${fileName}（UTF-8 BOM，可直接修改后再导入）`)
  } catch (error) {
    reportApiError(error, '导出失败')
  } finally {
    exportBusy.value = ''
  }
}

function copyCredentials(): void {
  const lines = (report.value?.credentials ?? []).map(
    (item) => `${item.account}\t${item.name}\t${item.initialPassword}`,
  )
  if (lines.length === 0) return
  const text = ['账号\t姓名\t初始口令', ...lines].join('\n')
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success('初始口令清单已复制：请通过线下加密渠道分发（T-03）'))
    .catch(() => ElMessage.warning('复制失败，请手动抄录初始口令'))
}
</script>

<template>
  <section class="bulk-import">
    <header class="bulk-import__head">
      <h2>组织人员批量导入</h2>
      <p class="bulk-import__hint">
        五步流水线顺序不可调换（组织 → 人员 → 负责人 → 岗位 → 角色分配）。文件须为
        <strong>UTF-8 带 BOM 的 CSV</strong>，表头与模板逐字一致；先「干跑校验」再「正式导入」，
        任一行校验失败即整批拒绝（错误零落库）。
      </p>
    </header>

    <el-alert
      v-if="!importable"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号无批量导入权限"
      description="批量导入属高危写操作：仅系统管理员或分公司流程管理员可调用（REQ-ADMIN-001 / import-spec §2）。"
    />

    <div v-else class="bulk-import__steps">
      <el-radio-group :model-value="activeKind" @change="pickKind($event as string)">
        <el-radio-button v-for="meta in kinds" :key="meta.kind" :value="meta.kind">
          {{ STEP_ORDER.indexOf(meta.kind as (typeof STEP_ORDER)[number]) + 1 }}. {{ meta.label }}
        </el-radio-button>
      </el-radio-group>

      <el-card v-if="currentMeta" class="bulk-import__card" shadow="never">
        <template #header>
          <div class="bulk-import__card-head">
            <span>
              第 {{ stepIndex }} 步 · {{ currentMeta.label }}（模板
              <code>{{ currentMeta.file }}</code>）
            </span>
            <span class="bulk-import__columns">列：{{ currentMeta.columns.join(' , ') }}</span>
          </div>
        </template>

        <!--
          文件选择用原生 input（而非 el-upload）：导入契约要求「UTF-8 BOM 的 CSV 原文」，
          原生 input 的 File 对象可直接以 text/csv 提交，链路更短、也不需要 multipart 适配。
        -->
        <div class="bulk-import__picker">
          <input class="bulk-import__native" type="file" accept=".csv" @change="onFileChange" />
          <span v-if="file" class="bulk-import__file">{{ file.name }}（{{ file.size }} 字节）</span>
          <span v-else class="bulk-import__file">未选择文件</span>
        </div>

        <div class="bulk-import__actions">
          <el-button :loading="busy" @click="runPreview">干跑校验（preview）</el-button>
          <el-button type="primary" :loading="busy" @click="runCommit">正式导入（commit）</el-button>
        </div>
      </el-card>

      <el-card v-if="report" class="bulk-import__report" shadow="never">
        <template #header>
          <div class="bulk-import__card-head">
            <span>
              校验报告 · {{ report.file }} ·
              <strong :class="report.ok ? 'is-ok' : 'is-bad'">{{ report.ok ? '通过' : '未通过' }}</strong>
              （{{ report.dryRun ? '干跑，未写数据' : '正式导入' }}）
            </span>
            <span>
              总 {{ report.totalRows }} 行 / 通过 {{ report.passedRows }} / 失败 {{ report.failedRows }} /
              error {{ report.errors }} / warning {{ report.warnings }}
              <template v-if="!report.dryRun">
                · 新增 {{ report.added }} / 更新 {{ report.updated }} / 跳过 {{ report.skipped }}
              </template>
            </span>
          </div>
        </template>

        <el-table v-if="errorFindings.length" :data="errorFindings" size="small" border max-height="320">
          <el-table-column prop="line" label="行号" width="70" />
          <el-table-column prop="column" label="列" width="130" />
          <el-table-column prop="value" label="值" width="180" show-overflow-tooltip />
          <el-table-column prop="code" label="错误码" width="120" />
          <el-table-column prop="message" label="说明" show-overflow-tooltip />
        </el-table>

        <el-table v-if="warningFindings.length" :data="warningFindings" size="small" border max-height="220">
          <el-table-column prop="line" label="行号" width="70" />
          <el-table-column prop="column" label="列" width="130" />
          <el-table-column prop="value" label="值" width="180" show-overflow-tooltip />
          <el-table-column prop="code" label="警告码" width="120" />
          <el-table-column prop="message" label="说明" show-overflow-tooltip />
        </el-table>

        <div v-if="report.suggestions.length" class="bulk-import__suggestions">
          <h4>修正建议（按错误码去重，可整段复制给填报人）</h4>
          <ul>
            <li v-for="item in report.suggestions" :key="item.code">
              <code>{{ item.code }}</code> ×{{ item.count }} —— {{ item.suggestion }}
            </li>
          </ul>
        </div>

        <div v-if="report.credentials && report.credentials.length" class="bulk-import__credentials">
          <h4>一次性初始口令（仅本次返回，请线下加密分发；首次登录强制改密）</h4>
          <el-table :data="report.credentials" size="small" border>
            <el-table-column prop="account" label="账号" width="180" />
            <el-table-column prop="name" label="姓名" width="140" />
            <el-table-column prop="initialPassword" label="初始口令" />
          </el-table>
          <el-button size="small" @click="copyCredentials">复制清单</el-button>
        </div>

        <ul v-if="report.notes.length" class="bulk-import__notes">
          <li v-for="(note, index) in report.notes" :key="index">{{ note }}</li>
        </ul>
      </el-card>
    </div>

    <el-card class="bulk-import__export" shadow="never">
      <template #header>
        <span>主数据导出（仅系统管理员；列与模板一致，导出后可直接修改再导入）</span>
      </template>
      <template v-if="exportable">
        <el-button :loading="exportBusy === 'org'" @click="runExport('org')">组织 org.csv</el-button>
        <el-button :loading="exportBusy === 'user'" @click="runExport('user')">人员 user.csv</el-button>
        <el-button :loading="exportBusy === 'org-leader'" @click="runExport('org-leader')">
          负责人 org_leader.csv
        </el-button>
        <el-button :loading="exportBusy === 'user-position'" @click="runExport('user-position')">
          岗位 user_position.csv
        </el-button>
        <el-button :loading="exportBusy === 'user-role'" @click="runExport('user-role')">
          角色分配 user_role.csv
        </el-button>
        <el-button :loading="exportBusy === 'audit-log'" @click="runExport('audit-log')">
          审计日志（金额键已剔除）
        </el-button>
      </template>
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="主数据导出仅系统管理员可用"
        description="import-spec §9.2 T-11：组织/人员/负责人/岗位/角色分配导出仅系统管理员；单据与金额类导出适用「系统管理员与财务角色」。"
      />
    </el-card>
  </section>
</template>

<style scoped>
.bulk-import {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-4, 16px);
}

.bulk-import__head h2 {
  margin: 0 0 6px;
  font-size: var(--oa-font-size-lg, 18px);
}

.bulk-import__hint {
  margin: 0;
  color: var(--oa-color-text-secondary, #666);
  line-height: 1.6;
}

.bulk-import__steps,
.bulk-import__card,
.bulk-import__report,
.bulk-import__export {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bulk-import__card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.bulk-import__columns {
  color: var(--oa-color-text-secondary, #666);
  font-family: var(--oa-font-mono, monospace);
  font-size: 12px;
}

.bulk-import__picker {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.bulk-import__native {
  font: inherit;
}

.bulk-import__file {
  color: var(--oa-color-text-secondary, #666);
}

.bulk-import__actions {
  margin-top: 12px;
  display: flex;
  gap: 12px;
}

.is-ok {
  color: var(--el-color-success, #2e7d32);
}

.is-bad {
  color: var(--el-color-danger, #c62828);
}

.bulk-import__suggestions ul,
.bulk-import__notes {
  margin: 8px 0 0;
  padding-left: 20px;
  line-height: 1.7;
}

.bulk-import__credentials {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.bulk-import__export :deep(.el-card__body) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
