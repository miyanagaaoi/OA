<script setup lang="ts">
/**
 * oa-web · 角色管理（列表 + 新建/编辑/删除 + 授权入口）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/data-model.md` 3.1 `sys_role`（`code` 列注释 = **9 个权威角色码**、`role_scope`、
 *     `data_scope` 五值 CHECK 约束）、3.2 `sys_user_role`（角色分配唯一键）
 *   · `doc/prd-0.1.md` 5.2（RBAC + 数据域双层；**逐级分配**；分公司流程管理员**不可再授权**）、
 *     5.3（数据域口径表）、6.10 REQ-ADMIN-003（权限配置）/ REQ-ADMIN-006（管理员边界）、
 *     AC-59（权限配置与变更留痕）
 *   · `doc/import-spec.md` §2.3（9 个角色码白名单与建议数据域）、§3.6（`role_code` 格式）、
 *     §4.6（E-ROLE-001 ~ E-ROLE-005）
 *
 * 三条硬约束：
 *   1. **分级授权可见性**：分公司流程管理员（`company_admin`）**不渲染**集团级角色的
 *      编辑/删除入口，也不渲染权限树维护入口（`RolePermissionPanel` 内部再判一次）。
 *      本页只决定「渲不渲染」——**服务端才是裁决方**，越权请求一律 403。
 *   2. **受保护角色**：9 个内置角色禁止删除，`code` 与 `role_scope` 只读。
 *   3. **`group_category` 必须配类别**：数据域选它时至少勾一个事项类别，保存前置拦截并说明原因。
 *
 * 列表口径：角色总量很小（9 个内置 + 少量自定义），因此**一次取回、本地过滤**
 * （`stores/authz.ts` 缓存，覆盖 `UserRolesDrawer` 的角色下拉），避免每输入一个字就打后端。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import RolePermissionPanel from './RolePermissionPanel.vue'
import { useAuthzStore } from '@/stores/authz'
import { useUserStore } from '@/stores/user'
import { createRole, deleteRole, updateRole } from '@/api/authz'
import { canDeleteRole, canEditRole, canGrantRolePermission, canManageRole } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import {
  BUILT_IN_ROLE_META,
  MATTER_CATEGORY_OPTIONS,
  ROLE_SCOPE_LABEL,
  checkDataScopeCategories,
  checkRoleCode,
  checkRoleName,
  checkRoleRemark,
  dataScopeDescription,
  dataScopeLabel,
  isBuiltInRoleCode,
} from '@/utils/authz'
import type { DataScope } from '@/types/api'
import type { MatterCategory, RoleItem, RoleScope } from '@/types/authz'

const userStore = useUserStore()
const authzStore = useAuthzStore()

/** 角色维护入口（含只读浏览） */
const canManage = computed(() => canManageRole(userStore))
/** 权限树/数据域维护入口：分公司流程管理员不渲染 */
const canGrant = computed(() => canGrantRolePermission(userStore))

const filters = reactive({
  keyword: '',
  roleScope: '' as RoleScope | '',
})

/** 本地过滤（角色量小；服务端 keyword 参数仍保留在 api 层以备后端启用分页） */
const list = computed<RoleItem[]>(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return authzStore.roles.filter((role) => {
    if (filters.roleScope && role.roleScope !== filters.roleScope) return false
    if (!keyword) return true
    return (
      role.code.toLowerCase().includes(keyword) ||
      role.name.toLowerCase().includes(keyword) ||
      (role.remark ?? '').toLowerCase().includes(keyword)
    )
  })
})

const builtInCount = computed(() => authzStore.roles.filter((role) => role.builtIn).length)

/** 各行的入口可见性（分级授权可见性，见文件头约束 1） */
function canEdit(row: RoleItem): boolean {
  return canEditRole(userStore, row)
}

function canDelete(row: RoleItem): boolean {
  return canDeleteRole(userStore, row) && !row.builtIn
}

function canOpenGrant(row: RoleItem): boolean {
  return canGrant.value && canEditRole(userStore, row)
}

function scopeText(row: RoleItem): string {
  return dataScopeLabel(row.dataScope)
}

function scopeNote(row: RoleItem): string {
  return dataScopeDescription(row.dataScope)
}

/** 类别码 → 中文（五值顺序固定：经营/经济/行政/人力/投资） */
function categoryText(row: RoleItem): string {
  return row.categories
    .map((code) => MATTER_CATEGORY_OPTIONS.find((item) => item.value === code)?.label ?? code)
    .join('、')
}

/** el-table 作用域插槽的 row 是 any 形状；进出业务函数前统一收窄（不用 any） */
function asRole(row: unknown): RoleItem {
  return row as RoleItem
}

function resetFilters(): void {
  filters.keyword = ''
  filters.roleScope = ''
}

/** 数据域候选：服务端字典优先，缺省用常量表（与 PRD 5.3 逐字一致） */
const dataScopeOptions = computed<
  Array<{ value: DataScope; label: string; description: string; requiresCategory: boolean }>
>(() => {
  if (authzStore.dataScopes.length) {
    return authzStore.dataScopes.map((item) => ({
      value: item.code,
      label: item.label || dataScopeLabel(item.code),
      description: item.description || dataScopeDescription(item.code),
      requiresCategory: item.requiresCategory,
    }))
  }
  return (['self', 'dept', 'company', 'group_all', 'group_category'] as DataScope[]).map((code) => ({
    value: code,
    label: dataScopeLabel(code),
    description: dataScopeDescription(code),
    requiresCategory: code === 'group_category',
  }))
})

onMounted(() => {
  void authzStore.loadRoles()
  // 数据域字典用于「数据域」列与编辑表单；失败不阻断列表（常量表兜底）
  void authzStore.loadDictionaries().catch(() => undefined)
})

// ---------------------------------------------------------------------------
// 新建 / 编辑
// ---------------------------------------------------------------------------
const editDialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  roleId: '',
  code: '',
  name: '',
  roleScope: 'company' as RoleScope,
  dataScope: 'self' as DataScope,
  categories: [] as MatterCategory[],
  remark: '',
  submitting: false,
})

const editTitle = computed(() => (editDialog.mode === 'create' ? '新增角色' : `编辑角色 · ${editDialog.name}`))
/** 编辑内置角色时 code 与 role_scope 只读（硬约束 2） */
const codeReadonly = computed(() => editDialog.mode === 'edit' && isBuiltInRoleCode(editDialog.code))
const categoryRequired = computed(() => editDialog.dataScope === 'group_category')
const categoryError = computed(() => checkDataScopeCategories(editDialog.dataScope, editDialog.categories))

function openCreate(): void {
  Object.assign(editDialog, {
    visible: true,
    mode: 'create',
    roleId: '',
    code: '',
    name: '',
    roleScope: 'company' as RoleScope,
    dataScope: 'self' as DataScope,
    categories: [] as MatterCategory[],
    remark: '',
    submitting: false,
  })
}

function openEdit(row: RoleItem): void {
  Object.assign(editDialog, {
    visible: true,
    mode: 'edit',
    roleId: row.roleId,
    code: row.code,
    name: row.name,
    roleScope: row.roleScope,
    dataScope: row.dataScope,
    categories: [...row.categories],
    remark: row.remark ?? '',
    submitting: false,
  })
}

/** 内置角色的兜底提示（服务端未初始化时也能告诉用户「这是 9 个权威角色之一」） */
const builtInNote = computed(() => {
  if (editDialog.mode !== 'edit') return ''
  const meta = BUILT_IN_ROLE_META[editDialog.code as keyof typeof BUILT_IN_ROLE_META]
  if (!meta) return ''
  return `内置受保护角色：${meta.name}（${ROLE_SCOPE_LABEL[meta.roleScope]}）。角色码与层级只读、不可删除；建议数据域为 ${dataScopeLabel(meta.dataScope)}。`
})

function onScopeChange(value: string | number | boolean | undefined): void {
  const code = String(value ?? '')
  if (!code) return
  editDialog.dataScope = code as DataScope
}

function onCategoriesChange(value: Array<string | number | boolean>): void {
  editDialog.categories = value.map((item) => String(item)) as MatterCategory[]
}

async function submitEdit(): Promise<void> {
  const codeError = checkRoleCode(editDialog.code)
  if (codeError) {
    ElMessage({ type: 'warning', message: codeError })
    return
  }
  const nameError = checkRoleName(editDialog.name)
  if (nameError) {
    ElMessage({ type: 'warning', message: nameError })
    return
  }
  const remarkError = checkRoleRemark(editDialog.remark)
  if (remarkError) {
    ElMessage({ type: 'warning', message: remarkError })
    return
  }
  // 硬约束 3：group_category 必须至少一个类别
  if (categoryError.value) {
    ElMessage({ type: 'warning', message: categoryError.value })
    return
  }

  editDialog.submitting = true
  try {
    const payload = {
      code: editDialog.code.trim(),
      name: editDialog.name.trim(),
      roleScope: editDialog.roleScope,
      dataScope: editDialog.dataScope,
      remark: editDialog.remark.trim() || null,
      // 非 group_category 的类别一律提交为空，避免残留上一次的类别
      categories: editDialog.dataScope === 'group_category' ? editDialog.categories : [],
    }
    if (editDialog.mode === 'create') {
      await createRole(payload)
      ElMessage({ type: 'success', message: '角色已新增，变更已写入审计日志（REQ-LOG-004）' })
    } else {
      await updateRole(editDialog.roleId, payload)
      ElMessage({ type: 'success', message: '角色已保存，变更已写入审计日志（REQ-LOG-004）' })
    }
    editDialog.visible = false
    await authzStore.reloadRoles()
  } catch (error) {
    reportApiError(error, editDialog.mode === 'create' ? '新增角色' : '保存角色')
  } finally {
    editDialog.submitting = false
  }
}

// ---------------------------------------------------------------------------
// 删除
// ---------------------------------------------------------------------------
async function remove(row: RoleItem): Promise<void> {
  if (row.builtIn) {
    // 双保险：入口本就不渲染（硬约束 2）
    ElMessage({ type: 'warning', message: `「${row.name}」是内置受保护角色，不可删除` })
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${row.name}（${row.code}）」？该角色当前已分配给 ${row.userCount} 名用户、` +
        `含 ${row.permissionCount} 项权限授权；删除后这些分配一并失效（服务端可能因仍有用户分配而拒绝）。` +
        '删除会写入审计日志。',
      '确认删除角色',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteRole(row.roleId, '后台删除自定义角色')
    ElMessage({ type: 'success', message: '角色已删除，变更已写入审计日志（REQ-LOG-004）' })
    await authzStore.reloadRoles()
  } catch (error) {
    reportApiError(error, '删除角色')
  }
}

// ---------------------------------------------------------------------------
// 授权面板（权限树逐级勾选 + 数据域 + 类别 + 组织节点）
// ---------------------------------------------------------------------------
const panelVisible = ref(false)
const panelRole = ref<RoleItem | null>(null)

function openGrant(row: RoleItem): void {
  panelRole.value = row
  panelVisible.value = true
}

/** 授权保存后刷新角色列表（权限数与数据域可能已变） */
async function onGrantSaved(): Promise<void> {
  await authzStore.reloadRoles()
  if (panelRole.value) panelRole.value = authzStore.roleById(panelRole.value.roleId)
}
</script>

<template>
  <div class="oa-role-page">
    <header class="page-head">
      <h1 class="oa-text-title-page">角色与权限</h1>
      <span class="oa-text-caption oa-text-subtle">
        逐级分配：先给角色勾权限树与数据域，再把角色分配给用户（REQ-ADMIN-003）；
        {{ builtInCount }} 个内置角色受保护
      </span>
      <div class="head-actions">
        <el-button v-if="canManage" type="primary" @click="openCreate">新增角色</el-button>
      </div>
    </header>

    <!-- 分级授权可见性说明（不可见优于不可用） -->
    <el-alert
      v-if="canManage && !canGrant"
      type="info"
      :closable="false"
      show-icon
      title="当前账号只可浏览角色与公司级角色的基础信息，不渲染权限树维护入口"
      description="按 PRD 5.2，分公司流程管理员不可再向下分配权限；集团级角色的编辑/删除入口同样不渲染。服务端才是裁决方——绕过界面直接提交会被 403 拒绝。"
    />

    <!-- 筛选条 -->
    <div class="oa-card filter-bar">
      <el-input
        v-model="filters.keyword"
        class="kw"
        clearable
        placeholder="关键字：角色码 / 角色名称"
      />
      <el-select v-model="filters.roleScope" class="scope-filter" clearable placeholder="角色层级">
        <el-option value="group" label="集团级" />
        <el-option value="company" label="公司级" />
      </el-select>
      <el-button :disabled="!filters.keyword && !filters.roleScope" @click="resetFilters">重置</el-button>
      <span class="oa-text-caption oa-text-subtle count">
        共 <b class="oa-tnum">{{ list.length }}</b> 个角色
      </span>
    </div>

    <el-alert
      v-if="authzStore.rolesError"
      type="error"
      :closable="false"
      show-icon
      :title="authzStore.rolesError"
    />

    <el-table v-loading="authzStore.rolesLoading" :data="list" border size="default">
      <el-table-column label="角色码" width="180" fixed="left">
        <template #default="{ row }">
          <span class="oa-mono">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色名称" min-width="180">
        <template #default="{ row }">
          <b class="oa-text-body-sm">{{ row.name }}</b>
          <span v-if="row.remark" class="oa-text-caption oa-text-subtle sub-line">{{ row.remark }}</span>
        </template>
      </el-table-column>
      <el-table-column label="层级" width="100">
        <template #default="{ row }">
          <StatusPill kind="role-scope" :status="row.roleScope" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <StatusPill kind="role-builtin" :is-built-in="row.builtIn" />
        </template>
      </el-table-column>
      <el-table-column label="数据域" min-width="190">
        <template #default="{ row }">
          <span class="oa-text-body-sm">{{ scopeText(asRole(row)) }}</span>
          <span class="oa-text-caption oa-text-subtle sub-line">{{ scopeNote(asRole(row)) }}</span>
          <span v-if="asRole(row).categories.length" class="oa-text-caption oa-text-subtle sub-line">
            类别：{{ categoryText(asRole(row)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="权限数" width="90" align="right">
        <template #default="{ row }">
          <span class="oa-tnum">{{ row.permissionCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用户数" width="90" align="right">
        <template #default="{ row }">
          <span class="oa-tnum">{{ row.userCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <!-- 权限树/数据域维护入口：分公司流程管理员与集团级角色均不渲染 -->
          <el-button v-if="canOpenGrant(asRole(row))" link type="primary" @click="openGrant(asRole(row))">
            权限与数据域
          </el-button>
          <el-button v-if="canEdit(asRole(row))" link type="primary" @click="openEdit(asRole(row))">
            编辑
          </el-button>
          <el-button v-if="canDelete(asRole(row))" link type="danger" @click="remove(asRole(row))">
            删除
          </el-button>
          <span
            v-if="!canOpenGrant(asRole(row)) && !canEdit(asRole(row)) && !canDelete(asRole(row))"
            class="oa-text-caption oa-text-subtle"
          >
            {{ asRole(row).builtIn ? '内置角色受保护' : '不可维护' }}
          </span>
        </template>
      </el-table-column>
      <template #empty>
        <div class="oa-empty">
          <p>没有符合条件的角色</p>
        </div>
      </template>
    </el-table>

    <p class="oa-text-caption oa-text-subtle">
      数据域口径见《PRD》5.3：普通员工=本人；部门/科室负责人=本部门；子公司总经理/分公司管理员=本公司；
      集团董事长与系统管理员=全集团；集团分管领导/归口负责人=全集团按归口类别。
    </p>
  </div>

  <!-- ==================== 新增 / 编辑角色 ==================== -->
  <el-dialog v-model="editDialog.visible" :title="editTitle" width="680px" append-to-body>
    <el-form label-position="top">
      <el-form-item label="角色码" required>
        <el-input
          v-model="editDialog.code"
          class="oa-mono"
          :disabled="codeReadonly"
          maxlength="32"
          placeholder="小写蛇形，2–32 位，字母开头，如 company_admin"
        />
        <p class="hint">
          唯一性由服务端裁决（`uk_sys_role_code`）；
          {{ codeReadonly ? '内置角色的角色码不可修改（改动会让既有分配与导入白名单同时失效）。' : '角色码写入后不建议再改。' }}
        </p>
      </el-form-item>

      <el-form-item label="角色名称" required>
        <el-input v-model="editDialog.name" maxlength="50" show-word-limit placeholder="不超过 50 字" />
      </el-form-item>

      <el-form-item label="角色层级（role_scope）" required>
        <el-select v-model="editDialog.roleScope" class="fill" :disabled="codeReadonly">
          <el-option value="group" label="集团级（group）" />
          <el-option value="company" label="公司级（company）" />
        </el-select>
        <p class="hint">
          集团级角色对分公司流程管理员不可见：其编辑/删除入口不渲染（服务端同样会 403）。
        </p>
      </el-form-item>

      <el-form-item label="数据域（data_scope）" required>
        <el-select :model-value="editDialog.dataScope" class="fill" @change="onScopeChange">
          <el-option
            v-for="item in dataScopeOptions"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </el-select>
        <p class="hint">
          {{ dataScopeOptions.find((item) => item.value === editDialog.dataScope)?.description }}
        </p>
      </el-form-item>

      <el-form-item v-if="categoryRequired" label="事项类别" required>
        <el-checkbox-group :model-value="editDialog.categories" @change="onCategoriesChange">
          <el-checkbox v-for="item in MATTER_CATEGORY_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-checkbox>
        </el-checkbox-group>
        <p v-if="categoryError" class="hint error">{{ categoryError }}</p>
        <p v-else class="hint">
          数据域「全集团按归口类别」必须至少勾选一个类别（五值：经营/经济/行政/人力/投资）。
        </p>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="editDialog.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
      </el-form-item>

      <p v-if="builtInNote" class="oa-text-caption oa-text-subtle">{{ builtInNote }}</p>
    </el-form>

    <template #footer>
      <el-button @click="editDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="editDialog.submitting" @click="submitEdit">
        {{ editDialog.mode === 'create' ? '确认新增' : '确认保存' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- ==================== 权限与数据域（逐级勾选） ==================== -->
  <RolePermissionPanel v-model="panelVisible" :role="panelRole" @saved="onGrantSaved" />
</template>

<style scoped>
.oa-role-page {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-md);
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: var(--oa-space-sm);
}

.head-actions {
  margin-left: auto;
  display: flex;
  gap: var(--oa-space-xs);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-xs);
  padding: var(--oa-space-sm) var(--oa-space-md);
}

.kw {
  width: 260px;
}

.scope-filter {
  width: 160px;
}

.count {
  margin-left: auto;
}

.count b {
  font-weight: 500;
  color: var(--oa-color-ink);
}

.sub-line {
  display: block;
}

.hint {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.hint.error {
  color: var(--oa-color-error);
}

.fill {
  width: 100%;
}

@media (max-width: 768px) {
  .kw,
  .scope-filter {
    width: 100%;
  }
}
</style>
