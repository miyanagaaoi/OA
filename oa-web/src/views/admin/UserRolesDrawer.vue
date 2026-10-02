<script setup lang="ts">
/**
 * oa-web · 用户角色分配抽屉（多角色 + 公司范围 + 撤销）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/data-model.md` 3.2 `sys_user_role`：`scope_org_id`（该角色生效的组织范围，
 *     为空 = 按角色默认）、生成列 `scope_org_key = IFNULL(scope_org_id,0)` 与
 *     唯一键 `uk_sys_user_role (user_id, role_id, scope_org_key)`
 *   · `doc/import-spec.md` §3.6（`scope_org_path` 留空 = 按角色默认数据域；
 *     `(user_account, role_code, scope_org_path)` 不得重复 = E-ROLE-003）、§4.6（E-ROLE-005 备注 ≤255）
 *   · `doc/prd-0.1.md` 5.2（逐级分配；分公司流程管理员不可再授权）、
 *     REQ-ADMIN-001（维护…角色分配）、REQ-LOG-004（分配变更留痕）
 *
 * 三条硬约束：
 *   1. **重复分配提示 409**：同一人 + 同一角色 + 相同组织范围会被 `uk_sys_user_role` 判重，
 *      服务端回 409；前端先做一次本地预检（提示「已有相同范围的分配」），
 *      并捕获 409 再补一次说明——**服务端才是裁决方**。
 *   2. **分级可见性**：`company_admin` 不渲染集团级角色的分配与撤销入口（服务端同口径）。
 *   3. **变更留痕 + 缓存失效**：分配/撤销后提示「已写入审计日志」与
 *      「相关用户权限缓存已失效，下次请求生效」。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import { useAuthzStore } from '@/stores/authz'
import { useOrgStore } from '@/stores/org'
import { useUserStore } from '@/stores/user'
import { ApiError } from '@/api/http'
import { assignUserRole, fetchUserRoles, invalidatePermissionCache, revokeUserRole } from '@/api/authz'
import { canEditRole } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import { checkAssignmentRemark, dataScopeLabel, roleScopeLabel } from '@/utils/authz'
import type { UserItem } from '@/types/identity'
import type { RoleAssignment, RoleItem } from '@/types/authz'

const props = defineProps<{
  modelValue: boolean
  /** 目标人员；为空表示抽屉未绑定对象 */
  user: UserItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 角色分配变更（父级可据此刷新列表或做其他联动） */
  (e: 'changed'): void
}>()

const userStore = useUserStore()
const authzStore = useAuthzStore()
const orgStore = useOrgStore()

const assignments = ref<RoleAssignment[]>([])
const loading = ref(false)
const errorMessage = ref('')
const submitting = ref(false)

const form = ref({
  roleId: '',
  scopeOrgId: '',
  remark: '',
})

/** 可分配角色：集团级角色对分公司管理员不渲染（分级可见性，服务端同口径） */
const roleOptions = computed<RoleItem[]>(() =>
  authzStore.roles.filter((role) => canEditRole(userStore, role)),
)

/** 组织范围候选：仅启用节点（停用组织不得作为新的数据域绑定） */
const orgOptions = computed(() => orgStore.activeTree)

function asAssignment(row: unknown): RoleAssignment {
  return row as RoleAssignment
}

/** 撤销入口可见性：与分配同口径，按分配行的角色层级判断 */
function canRevoke(row: RoleAssignment): boolean {
  return canEditRole(userStore, { roleScope: row.roleScope ?? 'company' })
}

function scopeText(row: RoleAssignment): string {
  if (row.scopeOrgPath) return row.scopeOrgPath
  if (row.scopeOrgName) return row.scopeOrgName
  return '按角色默认数据域'
}

function scopeNote(row: RoleAssignment): string {
  if (row.scopeOrgId) return '在该组织范围内生效'
  return row.dataScope ? `留空 = 按角色默认（${dataScopeLabel(row.dataScope)}）` : '留空 = 按角色默认'
}

async function load(): Promise<void> {
  const current = props.user
  if (!current) {
    assignments.value = []
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    assignments.value = await fetchUserRoles(current.userId)
  } catch (error) {
    assignments.value = []
    errorMessage.value = (error as Error).message || '角色分配加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    form.value = { roleId: '', scopeOrgId: '', remark: '' }
    if (!orgStore.hasTree) void orgStore.loadTree()
    await authzStore.loadRoles()
    await load()
  },
)

function close(): void {
  emit('update:modelValue', false)
}

function onVisibleChange(value: boolean): void {
  emit('update:modelValue', value)
}

/** 变更后失效该用户的权限缓存（否则「刚分配的角色不生效」会被当成 bug） */
async function invalidateCacheForUser(reason: string): Promise<string> {
  const current = props.user
  if (!current) return ''
  try {
    const result = await invalidatePermissionCache({ userIds: [current.userId], reason })
    return result.note ?? ''
  } catch {
    return ''
  }
}

async function submit(): Promise<void> {
  const current = props.user
  if (!current) return
  if (!form.value.roleId) {
    ElMessage({ type: 'warning', message: '请选择要分配的角色' })
    return
  }
  const remarkError = checkAssignmentRemark(form.value.remark)
  if (remarkError) {
    ElMessage({ type: 'warning', message: remarkError })
    return
  }

  // 本地预检：同一人 + 同一角色 + 相同组织范围不得重复（E-ROLE-003 / uk_sys_user_role）
  const duplicated = assignments.value.some(
    (item) => item.roleId === form.value.roleId && (item.scopeOrgId ?? '') === (form.value.scopeOrgId || ''),
  )
  if (duplicated) {
    ElMessage({
      type: 'warning',
      message: '该角色已分配（同一人 + 同一角色 + 相同组织范围不可重复，uk_sys_user_role / E-ROLE-003）',
    })
    return
  }

  submitting.value = true
  try {
    await assignUserRole(current.userId, {
      roleId: form.value.roleId,
      scopeOrgId: form.value.scopeOrgId || null,
      remark: form.value.remark.trim() || undefined,
    })
    ElMessage({ type: 'success', message: '角色已分配，变更已写入审计日志（REQ-LOG-004）' })
    const cacheNote = await invalidateCacheForUser('用户角色分配变更')
    ElMessage({
      type: 'info',
      message: cacheNote || '相关用户权限缓存已失效，下次请求生效',
      duration: 5000,
      showClose: true,
    })
    form.value = { roleId: '', scopeOrgId: '', remark: '' }
    await load()
    await authzStore.reloadRoles()
    emit('changed')
  } catch (error) {
    // 服务端仍是裁决方：409 = 唯一键冲突（并发下的重复分配）
    if (error instanceof ApiError && error.httpStatus === 409) {
      ElMessage({
        type: 'warning',
        message: '服务端判定该角色已存在相同组织范围的分配（409）：请刷新后再看',
      })
    } else {
      reportApiError(error, '分配角色')
    }
  } finally {
    submitting.value = false
  }
}

async function revoke(row: RoleAssignment): Promise<void> {
  const current = props.user
  if (!current) return
  try {
    await ElMessageBox.confirm(
      `确认撤销「${row.roleName}（${row.roleCode}）」的分配？` +
        `生效范围：${scopeText(row)}。撤销后该用户立即失去该角色带来的权限（下次请求生效），撤销会写入审计日志。`,
      '确认撤销角色分配',
      { confirmButtonText: '确认撤销', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await revokeUserRole(current.userId, row.assignmentId)
    ElMessage({ type: 'success', message: '角色分配已撤销，变更已写入审计日志（REQ-LOG-004）' })
    const cacheNote = await invalidateCacheForUser('用户角色分配撤销')
    ElMessage({
      type: 'info',
      message: cacheNote || '相关用户权限缓存已失效，下次请求生效',
      duration: 5000,
      showClose: true,
    })
    await load()
    await authzStore.reloadRoles()
    emit('changed')
  } catch (error) {
    reportApiError(error, '撤销角色分配')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="角色分配（多角色 + 组织范围）"
    size="620px"
    direction="rtl"
    @update:model-value="onVisibleChange"
  >
    <div class="drawer-body">
      <p class="who">
        <b>{{ user?.name || '—' }}</b>
        <span class="oa-text-caption oa-text-subtle">
          {{ user?.account }} · 工号 <span class="oa-tnum">{{ user?.employeeNo }}</span>
          <template v-if="user?.companyName"> · {{ user.companyName }}</template>
        </span>
      </p>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="角色决定功能权限与数据域；生效组织范围（scope_org_path）留空 = 按角色默认"
        description="同一人 + 同一角色 + 相同组织范围不可重复分配（uk_sys_user_role / E-ROLE-003）。分配与撤销均写入审计日志（REQ-LOG-004）。"
      />

      <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />

      <el-table v-loading="loading" :data="assignments" size="small" border>
        <el-table-column label="角色" min-width="170">
          <template #default="{ row }">
            <b class="oa-text-body-sm">{{ row.roleName }}</b>
            <span class="oa-mono oa-text-caption sub-line">{{ row.roleCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="92">
          <template #default="{ row }">
            <StatusPill
              kind="role-scope"
              :status="row.roleScope || 'company'"
              :label="roleScopeLabel(row.roleScope || 'company')"
            />
          </template>
        </el-table-column>
        <el-table-column label="数据域" width="120">
          <template #default="{ row }">
            <span class="oa-text-caption">{{ dataScopeLabel(row.dataScope || 'self') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生效组织范围" min-width="150">
          <template #default="{ row }">
            <span class="oa-text-body-sm">{{ scopeText(asAssignment(row)) }}</span>
            <span class="oa-text-caption oa-text-subtle sub-line">{{ scopeNote(asAssignment(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canRevoke(asAssignment(row))" link type="danger" @click="revoke(asAssignment(row))">
              撤销
            </el-button>
            <span v-else class="oa-text-caption oa-text-subtle">—</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="oa-empty">
            <p>该用户暂未分配任何角色（无角色 ⇒ 数据域为空 ⇒ 看不到任何业务数据）</p>
          </div>
        </template>
      </el-table>

      <p class="oa-text-caption oa-text-subtle">
        一人可多角色，数据域取并集；角色的数据域口径见《PRD》5.3。
        分配后如需立即生效，请在角色/权限变更后走一次缓存失效（本页已自动调用）。
      </p>

      <div class="oa-section-band">新增角色分配</div>

      <el-form label-position="top">
        <el-form-item label="角色" required>
          <el-select
            v-model="form.roleId"
            filterable
            clearable
            class="fill"
            placeholder="选择角色（集团级角色仅系统管理员可分配）"
          >
            <el-option
              v-for="item in roleOptions"
              :key="item.roleId"
              :value="item.roleId"
              :label="`${item.name}（${item.code}）`"
            >
              <span>{{ item.name }}</span>
              <span class="oa-text-caption oa-text-subtle">
                {{ item.code }} · {{ roleScopeLabel(item.roleScope) }} · {{ dataScopeLabel(item.dataScope) }}
              </span>
            </el-option>
          </el-select>
          <p v-if="!roleOptions.length" class="hint">
            没有可分配的角色：可能角色列表尚未加载，或当前账号对现有角色均无分配权限。
          </p>
        </el-form-item>

        <el-form-item label="生效组织范围（scope_org_path）">
          <el-tree-select
            v-model="form.scopeOrgId"
            :data="orgOptions"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            :render-after-expand="false"
            filterable
            clearable
            class="fill"
            placeholder="留空 = 按角色默认数据域（如员工=本人、子公司总经理=本公司）"
          />
          <p class="hint">
            仅可选启用中的组织节点；非空时该角色只在该组织范围内生效（`sys_user_role.scope_org_id`）。
          </p>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" show-word-limit placeholder="选填，不超过 255 字（E-ROLE-005）" />
        </el-form-item>
      </el-form>

      <div class="drawer-actions">
        <el-button @click="close">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认分配</el-button>
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

.sub-line {
  display: block;
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

.hint {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}
</style>
