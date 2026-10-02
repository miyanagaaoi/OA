<script setup lang="ts">
/**
 * oa-web · 一人多岗抽屉（岗位列表 + 设为主岗）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.1（一名员工可同时挂职于多个组织节点）
 *   · `doc/import-spec.md` §3.5 / §4.5：`(user, org)` 不得重复（E-POS-004）、
 *     每人最多一个主岗（E-POS-005）、`post_name` ≤50（E-POS-006）、
 *     主岗的 `post_name` 同时回填 `sys_user.position`；停用组织不得作为人员归属
 *   · `DESIGN.md` › Feedback & Overlays › drawer（右侧抽屉 480px，不打断上下文的详情查看）
 *
 * 关键交互（任务书硬要求）：**设为主岗时提示「原主岗将自动置否」**，确认后才提交；
 * 服务端语义是「置 is_primary=1 时同用户其余岗位自动置 0」，前端只负责把后果说清楚。
 * 设为主岗/改岗位名**走正式接口** `PUT /users/{id}/positions/{positionId}`（`postName`/`isPrimary`）——
 * **不先删后建**（后端在同一事务内重置旧主岗并回填 sys_user.position，T-12）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import { useOrgStore } from '@/stores/org'
import { createUserPosition, deleteUserPosition, fetchUserPositions, updateUserPosition } from '@/api/user'
import { checkPostName, checkRemark } from '@/utils/identity-rules'
import { reportApiError } from '@/utils/feedback'
import type { PositionItem, UserItem } from '@/types/identity'

const props = defineProps<{
  modelValue: boolean
  /** 目标人员；为空表示抽屉未绑定对象 */
  user: UserItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 岗位变更后通知父级刷新列表（主岗会回填 sys_user.position） */
  (e: 'changed'): void
}>()

const orgStore = useOrgStore()

const positions = ref<PositionItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const submitting = ref(false)

const primaryPosition = computed(() => positions.value.find((item) => item.isPrimary) ?? null)

/** el-table 作用域插槽的 row 是 DefaultRow；进出业务函数前统一收窄（不用 any） */
function asPosition(row: unknown): PositionItem {
  return row as PositionItem
}

/** 新增岗位表单 */
const form = ref({
  orgId: '',
  postName: '',
  isPrimary: false,
  remark: '',
})

/** 可归属组织：只允许**启用**节点（停用组织不得作为人员归属，import-spec §3.2） */
const orgOptions = computed(() => orgStore.activeTree)

async function load(): Promise<void> {
  const user = props.user
  if (!user) {
    positions.value = []
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    positions.value = await fetchUserPositions(user.userId)
  } catch (error) {
    positions.value = []
    errorMessage.value = (error as Error).message || '岗位加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    form.value = { orgId: '', postName: '', isPrimary: false, remark: '' }
    if (!orgStore.hasTree) await orgStore.loadTree()
    await load()
  },
)

function close(): void {
  emit('update:modelValue', false)
}

function onVisibleChange(value: boolean): void {
  emit('update:modelValue', value)
}

async function submit(): Promise<void> {
  const user = props.user
  if (!user) return
  if (!form.value.orgId) {
    ElMessage({ type: 'warning', message: '请选择任职组织' })
    return
  }
  const nameError = checkPostName(form.value.postName)
  if (nameError) {
    ElMessage({ type: 'warning', message: nameError })
    return
  }
  const remarkError = checkRemark(form.value.remark)
  if (remarkError) {
    ElMessage({ type: 'warning', message: remarkError })
    return
  }
  // E-POS-004 前端预检：同一 (user, org) 不得重复；服务端仍是权威
  if (positions.value.some((item) => item.orgId === form.value.orgId)) {
    ElMessage({ type: 'warning', message: '该员工在此组织已有岗位，(user, org) 不得重复（E-POS-004）' })
    return
  }
  // E-POS-005 前端预检：新增主岗会顶掉原主岗
  if (form.value.isPrimary && primaryPosition.value) {
    const ok = await confirmPrimary(primaryPosition.value)
    if (!ok) return
  }
  submitting.value = true
  try {
    await createUserPosition(user.userId, {
      orgId: form.value.orgId,
      postName: form.value.postName.trim(),
      isPrimary: form.value.isPrimary,
      remark: form.value.remark.trim() || undefined,
    })
    ElMessage({ type: 'success', message: '岗位已新增' })
    form.value = { orgId: '', postName: '', isPrimary: false, remark: '' }
    await load()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  } finally {
    submitting.value = false
  }
}

/** 设为主岗前的确认：必须写明「原主岗将自动置否」（E-POS-005） */
async function confirmPrimary(oldPrimary: PositionItem): Promise<boolean> {
  try {
    await ElMessageBox.confirm(
      `设为主岗后，原主岗「${oldPrimary.postName}（${oldPrimary.orgName}）」将自动置为否 —— 每人最多只能有一个主岗（E-POS-005），主岗的岗位名会回填为该员工的当前职务。`,
      '确认设为主岗',
      { confirmButtonText: '确认设为主岗', cancelButtonText: '取消', type: 'warning' },
    )
    return true
  } catch {
    return false
  }
}

/** 设为主岗：正式接口 PUT（服务端同事务把旧主岗置 0 并回填 sys_user.position），不先删后建 */
async function setPrimary(item: PositionItem): Promise<void> {
  const user = props.user
  if (!user || item.isPrimary) return
  const oldPrimary = primaryPosition.value
  if (oldPrimary && !(await confirmPrimary(oldPrimary))) return
  submitting.value = true
  try {
    await updateUserPosition(user.userId, item.positionId, { isPrimary: true })
    ElMessage({ type: 'success', message: '已设为主岗' })
    await load()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  } finally {
    submitting.value = false
  }
}

async function remove(item: PositionItem): Promise<void> {
  const user = props.user
  if (!user) return
  try {
    await ElMessageBox.confirm(
      `确认移除岗位「${item.postName}（${item.orgName}）」？` +
        (item.isPrimary ? '该岗位是当前主岗，移除后该员工将没有主岗，请及时指定新的主岗。' : ''),
      '确认移除岗位',
      { confirmButtonText: '确认移除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await deleteUserPosition(user.userId, item.positionId, { reason: '管理后台移除岗位' })
    ElMessage({ type: 'success', message: '岗位已移除' })
    await load()
    emit('changed')
  } catch (error) {
    reportApiError(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="岗位管理（一人多岗）"
    size="480px"
    direction="rtl"
    @update:model-value="onVisibleChange"
  >
    <div class="drawer-body">
      <p class="who">
        <b>{{ user?.name || '—' }}</b>
        <span class="oa-text-caption oa-text-subtle">
          {{ user?.account }} · 工号 <span class="oa-tnum">{{ user?.employeeNo }}</span>
        </span>
      </p>

      <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />

      <el-table v-loading="loading" :data="positions" size="small" border>
        <el-table-column label="任职组织" min-width="150">
          <template #default="{ row }">
            <span class="org-name">{{ row.orgName }}</span>
            <span v-if="row.orgPath" class="oa-text-caption oa-text-subtle">{{ row.orgPath }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="postName" label="岗位" min-width="110" />
        <el-table-column label="主岗" width="80">
          <template #default="{ row }">
            <StatusPill kind="position" :is-primary="row.isPrimary" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isPrimary" link type="primary" @click="setPrimary(asPosition(row))">
              设为主岗
            </el-button>
            <el-button link type="danger" @click="remove(asPosition(row))">移除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="oa-empty">
            <p>该员工暂无岗位记录</p>
          </div>
        </template>
      </el-table>

      <p class="oa-text-caption oa-text-subtle">
        主岗唯一：每人最多一个主岗（E-POS-005）；主岗岗位名会回填该员工的当前职务，离职/调岗时以主岗判定归属。
      </p>

      <div class="oa-section-band">新增岗位</div>

      <el-form label-position="top">
        <el-form-item label="任职组织" required>
          <el-tree-select
            v-model="form.orgId"
            :data="orgOptions"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            filterable
            clearable
            class="fill"
            placeholder="仅可选启用中的组织节点"
          />
        </el-form-item>

        <el-form-item label="岗位名称" required>
          <el-input v-model="form.postName" maxlength="50" show-word-limit placeholder="不超过 50 字（E-POS-006）" />
        </el-form-item>

        <el-form-item label="设为主岗">
          <el-switch v-model="form.isPrimary" />
          <span class="switch-hint">勾选后，原主岗将自动置否（E-POS-005）</span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" show-word-limit placeholder="不超过 255 字" />
        </el-form-item>
      </el-form>

      <div class="drawer-actions">
        <el-button @click="close">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认新增岗位</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-sm);
}

.who {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-xs);
  font: var(--oa-font-body);
  color: var(--oa-color-ink);
}

.who b {
  font: var(--oa-font-title-section);
}

.org-name {
  display: block;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.switch-hint {
  margin-left: var(--oa-space-xs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--oa-space-xs);
  padding-top: var(--oa-space-xs);
  border-top: 1px solid var(--oa-color-hairline);
}

.fill {
  width: 100%;
}
</style>
