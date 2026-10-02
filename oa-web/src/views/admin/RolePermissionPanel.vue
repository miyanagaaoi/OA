<script setup lang="ts">
/**
 * oa-web · 权限树逐级勾选面板（角色授权）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `doc/prd-0.1.md` 5.2「IT 部门在后台为每个角色**勾选可访问的组织节点和功能菜单**，
 *     支持**逐级分配**」「分公司流程管理员…**不可再向下分配权限**」、
 *     6.10 REQ-ADMIN-003（为角色勾选权限树节点与数据域）、REQ-ADMIN-006（管理员边界）
 *   · `doc/prd-0.1.md` 6.9 REQ-LOG-004 / AC-59：权限变更必须留痕（含变更前后值）
 *   · `doc/data-model.md` 3.3（`sys_role_category`：类别五值，仅 `group_category` 需要）、
 *     3.4（`sys_permission.perm_type`：menu/button/api）
 *   · `DESIGN.md` › Agent Usage Rules 第 6 条「权限不可见优于不可用」
 *
 * 五条硬约束：
 *   1. **逐级勾选**：`el-tree` + `show-checkbox` + `check-strictly=false`（父子联动）+ 半选态；
 *      保存时按后端约定提交（后端尚未说明存叶子还是全量，当前提交
 *      **全量已勾选 id 集合** = checkedKeys ∪ halfCheckedKeys，并附 `leafIds` 副本，
 *      见 `api/authz.ts` 的「待对齐」注释）。
 *   2. **保存前展示差异**：`新增 N 项 / 移除 N 项`，可展开逐条查看（名称 + 权限码 + 所属分支）。
 *   3. **保存后提示已写审计**：`已写入审计日志（REQ-LOG-004）` +
 *      `相关用户权限缓存已失效，下次请求生效`（硬约束 5）。
 *   4. **只读态**：无授权权限者（`admin:role:grant` / `admin:authz:scope` 都没有，
 *      如分公司流程管理员）**不渲染**保存与节点维护入口，树只能看不能勾并常驻说明——
 *      但**服务端才是裁决方**，越权请求一律 403。
 *   5. **`group_category` 必须配类别**：选择该数据域时至少勾一个事项类别，否则保存前拦截。
 *
 * 实现要点：`el-tree` 的展开态与勾选态都用「**声明式 + 换 key 重挂载**」维护
 * （`:default-checked-keys` / `:default-expanded-keys`），因为 Element Plus 的
 * `el-tree` 没有公开的 `expandAll/checkAll` 方法；重挂载时再用草稿回填，
 * 勾选状态不会丢（草稿是唯一数据源）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import StatusPill from '@/components/StatusPill.vue'
import { useAuthzStore } from '@/stores/authz'
import { useOrgStore } from '@/stores/org'
import { useUserStore } from '@/stores/user'
import { createPermission, deletePermission, updatePermission } from '@/api/authz'
import { canGrantRolePermission, canManageRole } from '@/utils/admin'
import { reportApiError } from '@/utils/feedback'
import {
  DATA_SCOPE_LABEL,
  MATTER_CATEGORY_LABEL,
  MATTER_CATEGORY_OPTIONS,
  PERMISSION_TYPE_LABEL,
  collectPermissionIds,
  collectPermissionLeafIds,
  dataScopeDescription,
  dataScopeLabel,
  permissionPathText,
  permissionTypeLabel,
  resolveCheckedStateFromLeaves,
} from '@/utils/authz'
import type { DataScope } from '@/types/api'
import type { MatterCategory, PermissionNode, PermissionType, RoleItem } from '@/types/authz'

const props = defineProps<{
  modelValue: boolean
  /** 当前要维护授权的角色；为空表示抽屉未绑定对象 */
  role: RoleItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 授权保存成功（父级据此刷新角色列表的权限数与数据域） */
  (e: 'saved', roleId: string): void
}>()

const userStore = useUserStore()
const authzStore = useAuthzStore()
const orgStore = useOrgStore()

/** 「权限树维护入口」可见性：`admin:role:grant` 或 `admin:authz:scope`（分公司管理员不渲染；
 *  服务端才是裁决方——越权请求一律 403，前端只决定「渲不渲染」） */
const canGrant = computed(() => canGrantRolePermission(userStore))
/** 角色维度可维护性：集团级角色对分公司管理员不渲染 */
const roleEditable = computed(() => (props.role ? canManageRole(userStore) : false))
const readonlyPanel = computed(() => !canGrant.value || !roleEditable.value)

const treeRef = ref<InstanceType<typeof ElTree> | null>(null)
const filterKeyword = ref('')
/** 默认展开的节点（打开面板时展开第一层） */
const expandedKeys = ref<string[]>([])
/** 展开/折叠全部靠重挂载实现，版本号参与 treeKey */
const treeVersion = ref(0)

const draft = computed(() => authzStore.draft)
const diff = computed(() => authzStore.permissionDiff)

const treeKey = computed(() => `${draft.value.roleId || 'empty'}#${treeVersion.value}`)

const treeProps = { label: 'name', children: 'children' } as const

/** 数据域候选：服务端字典优先，未取回时用 `utils/authz.ts` 的常量表兜底（与 PRD 5.3 逐字一致） */
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
  return (Object.keys(DATA_SCOPE_LABEL) as DataScope[]).map((code) => ({
    value: code,
    label: dataScopeLabel(code),
    description: dataScopeDescription(code),
    requiresCategory: code === 'group_category',
  }))
})

const currentScope = computed(
  () => dataScopeOptions.value.find((item) => item.value === draft.value.dataScope) ?? null,
)

/** 类别是否必填（硬约束 5：`group_category` 必须配类别） */
const categoryRequired = computed(
  () => currentScope.value?.requiresCategory ?? draft.value.dataScope === 'group_category',
)
const categoryError = computed(() => authzStore.draftValidationError)

const totalPermissionCount = computed(() => collectPermissionIds(authzStore.permissionTree).length)
const checkedPermissionCount = computed(() => draft.value.permissionIds.length)

/** 组织节点候选：仅启用节点（停用节点不得作为新的授权范围） */
const orgNodeOptions = computed(() => orgStore.activeTree)

// ---------------------------------------------------------------------------
// el-tree 交互（勾选态写入草稿，展开态换 key 重挂载）
// ---------------------------------------------------------------------------
function asPermission(data: unknown): PermissionNode {
  return data as PermissionNode
}

/** 父子联动后的勾选结果：全选中的 id ∪ 半选父节点 id（= 提交口径的「全量已勾选集合」） */
function syncCheckedFromTree(): void {
  const tree = treeRef.value
  if (!tree) return
  const checked = (tree.getCheckedKeys(false) as Array<string | number>).map((id) => String(id))
  const half = (tree.getHalfCheckedKeys() as Array<string | number>).map((id) => String(id))
  authzStore.setCheckedPermissionIds([...checked, ...half], half)
}

function onCheck(): void {
  syncCheckedFromTree()
}

/**
 * 全选 / 反选 / 清空：直接改草稿并重挂载树。
 * 不调 `setCheckedKeys` 的原因同上——声明式重挂载是本页唯一的勾选写入路径，行为可预测。
 * 勾选集合必须用 `resolveCheckedStateFromLeaves` 推导（叶子 + 全选父节点 / 半选父节点），
 * 与 el-tree 的联动语义保持一致，否则差异清单会把父节点误报为「移除」。
 */
function applyLeaves(leafIds: string[]): void {
  const state = resolveCheckedStateFromLeaves(authzStore.permissionTree, leafIds)
  authzStore.setCheckedPermissionIds(state.checkedIds, state.halfCheckedIds)
  treeVersion.value += 1
}

function currentCheckedLeafIds(): string[] {
  const leaves = new Set(collectPermissionLeafIds(authzStore.permissionTree))
  return draft.value.permissionIds.filter((id) => leaves.has(id))
}

function selectAll(): void {
  if (readonlyPanel.value) return
  applyLeaves(collectPermissionLeafIds(authzStore.permissionTree))
}

function invertSelection(): void {
  if (readonlyPanel.value) return
  const checked = new Set(currentCheckedLeafIds())
  applyLeaves(collectPermissionLeafIds(authzStore.permissionTree).filter((id) => !checked.has(id)))
}

function clearSelection(): void {
  if (readonlyPanel.value) return
  applyLeaves([])
}

function expandAll(): void {
  expandedKeys.value = collectPermissionIds(authzStore.permissionTree)
  treeVersion.value += 1
}

function collapseAll(): void {
  expandedKeys.value = []
  treeVersion.value += 1
}

function filterNode(value: string, data: unknown): boolean {
  const text = value.trim()
  if (!text) return true
  const node = asPermission(data)
  return node.name.includes(text) || node.code.includes(text)
}

watch(filterKeyword, (value) => {
  treeRef.value?.filter(value)
})

// ---------------------------------------------------------------------------
// 表单事件：Element Plus 的 change 事件是宽类型，统一在这里收窄（不用 any）
// ---------------------------------------------------------------------------
function onScopeChange(value: string | number | boolean | undefined): void {
  const code = String(value ?? '')
  if (code in DATA_SCOPE_LABEL) authzStore.setDataScope(code as DataScope)
}

function onCategoriesChange(value: Array<string | number | boolean>): void {
  const codes = value
    .map((item) => String(item))
    .filter((item): item is MatterCategory => item in MATTER_CATEGORY_LABEL)
  authzStore.setCategories(codes)
}

// ---------------------------------------------------------------------------
// 打开抽屉：加载权限树 / 字典 / 草稿
// ---------------------------------------------------------------------------
watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible || !props.role) return
    filterKeyword.value = ''
    treeVersion.value += 1
    await authzStore.loadPermissionTree()
    // 字典失败不阻断面板：数据域与类别都有本地常量兜底
    await authzStore.loadDictionaries().catch(() => undefined)
    if (!orgStore.hasTree) void orgStore.loadTree()
    await authzStore.loadGrant(props.role)
    expandedKeys.value = authzStore.permissionTree.map((node) => node.id)
  },
)

function close(): void {
  emit('update:modelValue', false)
}

async function onVisibleChange(value: boolean): Promise<void> {
  if (!value && authzStore.dirty) {
    try {
      await ElMessageBox.confirm(
        '当前授权有未保存的修改，关闭后这些修改会丢失（不会写入审计日志）。确认放弃修改？',
        '放弃未保存的授权修改',
        { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning' },
      )
    } catch {
      return
    }
  }
  if (!value) authzStore.resetDraft()
  emit('update:modelValue', value)
}

// ---------------------------------------------------------------------------
// 保存（权限树 + 组织节点 + 类别 + 数据域）
// ---------------------------------------------------------------------------
const saving = computed(() => authzStore.saving)

async function submit(): Promise<void> {
  const role = props.role
  if (!role || readonlyPanel.value) return
  // 硬约束 5：group_category 必须至少一个类别，保存前置拦截并说明原因
  if (categoryError.value) {
    ElMessage({ type: 'warning', message: categoryError.value })
    return
  }
  if (!authzStore.dirty) {
    ElMessage({ type: 'info', message: '当前没有需要保存的变更' })
    return
  }

  const pieces: string[] = []
  if (authzStore.permissionDirty) {
    pieces.push(`权限树 +${diff.value.addedIds.length} / −${diff.value.removedIds.length} 项`)
  }
  if (authzStore.dataScopeDirty) {
    pieces.push(`数据域 ${dataScopeLabel(draft.value.savedDataScope)} → ${dataScopeLabel(draft.value.dataScope)}`)
  }
  if (authzStore.categoryDirty) pieces.push('事项类别范围变更')
  if (authzStore.orgNodeDirty) pieces.push('可访问组织节点变更')

  try {
    await ElMessageBox.confirm(
      `将为角色「${role.name}（${role.code}）」保存以下变更：${pieces.join('；')}。` +
        '变更前后值会写入审计日志（REQ-LOG-004），保存后相关用户的权限缓存将失效。',
      '确认保存角色授权',
      { confirmButtonText: '确认保存', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  const addedCount = diff.value.addedIds.length
  const removedCount = diff.value.removedIds.length
  try {
    const result = await authzStore.saveGrant('后台角色授权调整')
    ElMessage({
      type: 'success',
      message: result.permissionChanged
        ? `授权已保存（新增 ${addedCount} 项、移除 ${removedCount} 项），变更已写入审计日志（REQ-LOG-004）`
        : '授权已保存，变更已写入审计日志（REQ-LOG-004）',
    })
    ElMessage({
      type: 'info',
      message: result.cacheNote || '相关用户权限缓存已失效，下次请求生效',
      duration: 5000,
      showClose: true,
    })
    emit('saved', role.roleId)
  } catch (error) {
    reportApiError(error, '保存角色授权')
  }
}

// ---------------------------------------------------------------------------
// 权限节点维护（POST/PUT/DELETE /authz/permissions）
// ---------------------------------------------------------------------------
interface PermissionOption {
  value: string
  label: string
  children?: PermissionOption[]
}

function toPermissionOption(node: PermissionNode): PermissionOption {
  return {
    value: node.id,
    label: `${node.name}（${node.code}）`,
    children: node.children?.length ? node.children.map(toPermissionOption) : undefined,
  }
}

/** 「上级节点」选择器数据（value/label 形状，供 el-tree-select 直接使用） */
const permissionSelectTree = computed<PermissionOption[]>(() =>
  authzStore.permissionTree.map(toPermissionOption),
)

const PERMISSION_TYPES: PermissionType[] = ['menu', 'button', 'api']

const nodeDialog = ref({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  permissionId: '',
  parentId: '',
  permType: 'button' as PermissionType,
  code: '',
  name: '',
  url: '',
  sortNo: 0,
  submitting: false,
})

function openCreateNode(parent: PermissionNode | null): void {
  nodeDialog.value = {
    visible: true,
    mode: 'create',
    permissionId: '',
    parentId: parent?.id ?? '',
    // 默认值跟着父节点走：菜单下多挂按钮，按钮/接口下多挂接口
    permType: parent?.permType === 'menu' ? 'button' : 'api',
    code: '',
    name: '',
    url: '',
    sortNo: 0,
    submitting: false,
  }
}

function openEditNode(node: PermissionNode): void {
  nodeDialog.value = {
    visible: true,
    mode: 'edit',
    permissionId: node.id,
    parentId: node.parentId ?? '',
    permType: node.permType,
    code: node.code,
    name: node.name,
    url: node.url ?? '',
    sortNo: node.sortNo,
    submitting: false,
  }
}

async function submitNode(): Promise<void> {
  const form = nodeDialog.value
  if (!form.name.trim()) {
    ElMessage({ type: 'warning', message: '权限名称为必填' })
    return
  }
  if (!form.code.trim()) {
    ElMessage({ type: 'warning', message: '权限码为必填（`sys_permission.code` 唯一键）' })
    return
  }
  form.submitting = true
  try {
    const payload = {
      parentId: form.parentId || null,
      permType: form.permType,
      code: form.code.trim(),
      name: form.name.trim(),
      url: form.url.trim() || null,
      sortNo: form.sortNo,
    }
    if (form.mode === 'create') {
      await createPermission(payload)
      ElMessage({ type: 'success', message: '权限节点已新增，变更已写入审计日志（REQ-LOG-004）' })
    } else {
      await updatePermission(form.permissionId, payload)
      ElMessage({ type: 'success', message: '权限节点已更新，变更已写入审计日志（REQ-LOG-004）' })
    }
    form.visible = false
    await authzStore.loadPermissionTree(true)
    treeVersion.value += 1
  } catch (error) {
    reportApiError(error, form.mode === 'create' ? '新增权限节点' : '更新权限节点')
  } finally {
    form.submitting = false
  }
}

async function removeNode(node: PermissionNode): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除权限节点「${node.name}（${node.code}）」？已授予该权限的角色会同时失去它；` +
        '子节点与角色引用由服务端级联校验（可能被拒绝）。删除会写入审计日志。',
      '确认删除权限节点',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deletePermission(node.id, '后台删除冗余权限节点')
    ElMessage({ type: 'success', message: '权限节点已删除，变更已写入审计日志（REQ-LOG-004）' })
    await authzStore.loadPermissionTree(true)
    treeVersion.value += 1
  } catch (error) {
    reportApiError(error, '删除权限节点')
  }
}

// ---------------------------------------------------------------------------
// 组织节点范围（el-tree-select 多选）
// ---------------------------------------------------------------------------
const orgNodeSelection = computed<string[]>({
  get: () => draft.value.orgNodeIds,
  set: (value) => authzStore.setOrgNodeIds(value ?? []),
})

function onOrgNodeChange(value: unknown): void {
  authzStore.setOrgNodeIds(Array.isArray(value) ? value.map((item) => String(item)) : [])
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="720px"
    direction="rtl"
    :title="role ? `权限与数据域 · ${role.name}` : '权限与数据域'"
    @update:model-value="onVisibleChange"
  >
    <div v-if="role" class="panel">
      <!-- ============ 角色头部 ============ -->
      <header class="role-head">
        <div class="line">
          <b class="oa-text-title-section">{{ role.name }}</b>
          <span class="oa-mono oa-text-caption">{{ role.code }}</span>
          <StatusPill kind="role-scope" :status="role.roleScope" />
          <StatusPill kind="role-builtin" :is-built-in="role.builtIn" />
        </div>
        <p class="oa-text-caption oa-text-subtle">
          已授权限 <b class="oa-tnum">{{ checkedPermissionCount }}</b> / 权限树共
          <b class="oa-tnum">{{ totalPermissionCount }}</b> 项 · 已分配用户
          <b class="oa-tnum">{{ role.userCount }}</b> 人
        </p>
      </header>

      <!-- 只读态：无权限者不渲染维护入口；服务端同样会 403 -->
      <el-alert
        v-if="readonlyPanel"
        type="info"
        :closable="false"
        show-icon
        title="只读查看：当前账号没有角色授权维护权限"
        description="按 PRD 5.2，分公司流程管理员不可再向下分配权限，因此本面板不渲染保存与权限树维护入口。服务端才是裁决方——即使绕过界面直接提交，也会被 403 拒绝。"
      />

      <el-alert
        v-if="authzStore.permissionTreeError"
        type="error"
        :closable="false"
        show-icon
        :title="authzStore.permissionTreeError"
      />
      <el-alert
        v-else-if="authzStore.grantError"
        type="error"
        :closable="false"
        show-icon
        :title="authzStore.grantError"
      />

      <!-- ============ 数据域 ============ -->
      <section class="block">
        <div class="oa-section-band">数据域（角色可见范围，PRD 5.3）</div>
        <el-radio-group
          :model-value="draft.dataScope"
          :disabled="readonlyPanel"
          class="scope-group"
          @change="onScopeChange"
        >
          <el-radio v-for="item in dataScopeOptions" :key="item.value" :value="item.value" class="scope-item">
            <span class="scope-label">{{ item.label }}</span>
            <span class="oa-text-caption oa-text-subtle">{{ item.description }}</span>
          </el-radio>
        </el-radio-group>

        <div v-if="categoryRequired" class="category-box" :class="{ 'is-error': !!categoryError }">
          <p class="oa-text-label">
            事项类别 <span class="required">*</span>
            <span class="oa-text-caption oa-text-subtle">
              数据域「{{ currentScope?.label || dataScopeLabel(draft.dataScope) }}」必须至少勾选一个类别
            </span>
          </p>
          <el-checkbox-group
            :model-value="draft.categories"
            :disabled="readonlyPanel"
            @change="onCategoriesChange"
          >
            <el-checkbox v-for="item in MATTER_CATEGORY_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
          <p v-if="categoryError" class="error-text">{{ categoryError }}</p>
          <p v-else class="oa-text-caption oa-text-subtle">
            类别为配置项（五值：经营/经济/行政/人力/投资），不参与流程路由，仅用于按分管业务线限定可见范围。
          </p>
        </div>
      </section>

      <!-- ============ 权限树逐级勾选 ============ -->
      <section class="block">
        <div class="oa-section-band">
          权限树（逐级勾选：父子联动 + 半选态）
          <span class="oa-text-caption oa-text-subtle band-note">
            勾上父节点 = 该分支全部可用；只勾部分子节点时父节点显示半选
          </span>
        </div>

        <div class="tree-tools">
          <el-input
            v-model="filterKeyword"
            class="tree-filter"
            clearable
            placeholder="过滤：权限名称 / 权限码"
          />
          <el-button :disabled="readonlyPanel" @click="selectAll">全选</el-button>
          <el-button :disabled="readonlyPanel" @click="invertSelection">反选</el-button>
          <el-button :disabled="readonlyPanel" @click="clearSelection">清空</el-button>
          <el-button @click="expandAll">展开全部</el-button>
          <el-button @click="collapseAll">折叠全部</el-button>
          <el-button v-if="canGrant" type="primary" plain @click="openCreateNode(null)">
            新增根权限
          </el-button>
        </div>

        <div v-loading="authzStore.grantLoading" class="tree-wrap" :class="{ 'is-readonly': readonlyPanel }">
          <el-tree
            ref="treeRef"
            :key="treeKey"
            class="perm-tree"
            :data="authzStore.permissionTree"
            :props="treeProps"
            node-key="id"
            show-checkbox
            :check-strictly="false"
            :default-checked-keys="authzStore.draftCheckedLeafIds"
            :default-expanded-keys="expandedKeys"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            @check="onCheck"
          >
            <template #default="{ data }">
              <span class="node-row">
                <span class="node-name">{{ data.name }}</span>
                <span class="oa-mono node-code">{{ data.code }}</span>
                <span class="oa-tag is-info node-type">
                  {{ permissionTypeLabel(data.permType, data.permTypeLabel) }}
                </span>
                <span v-if="canGrant" class="node-actions">
                  <el-button link type="primary" @click.stop="openCreateNode(asPermission(data))">
                    新增下级
                  </el-button>
                  <el-button link type="primary" @click.stop="openEditNode(asPermission(data))">
                    编辑
                  </el-button>
                  <el-button link type="danger" @click.stop="removeNode(asPermission(data))">
                    删除
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>

          <div v-if="!authzStore.permissionTree.length && !authzStore.permissionTreeLoading" class="oa-empty">
            <p>权限树为空：请确认后端已初始化 `sys_permission` 种子数据</p>
          </div>
        </div>
      </section>

      <!-- ============ 组织节点范围 ============ -->
      <section class="block">
        <div class="oa-section-band">可访问组织节点（授权粒度：组织节点 × 功能）</div>
        <el-alert
          v-if="authzStore.orgNodesUnavailable"
          type="warning"
          :closable="false"
          show-icon
          title="组织节点范围接口暂不可用（或当前账号无权查看）"
          description="该分区已禁用，避免把「没取到」误当成「本来就没授权」；权限树与数据域不受影响。"
        />
        <el-tree-select
          v-else
          :model-value="orgNodeSelection"
          :data="orgNodeOptions"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          multiple
          check-strictly
          show-checkbox
          collapse-tags
          collapse-tags-tooltip
          :render-after-expand="false"
          filterable
          clearable
          :disabled="readonlyPanel"
          class="fill"
          placeholder="留空 = 不额外限定组织节点（仍按数据域口径生效）"
          @update:model-value="onOrgNodeChange"
        />
        <p class="oa-text-caption oa-text-subtle">
          仅可选启用中的组织节点；无数据权限的节点本就不下发（不可见优于不可用）。
          <span v-if="authzStore.draftOrgPaths.length">
            服务端回传范围：{{ authzStore.draftOrgPaths.join('、') }}
          </span>
        </p>
      </section>

      <!-- ============ 保存前差异 ============ -->
      <section v-if="!readonlyPanel" class="block">
        <div class="oa-section-band">保存前差异（变更前后值将写入审计日志，REQ-LOG-004）</div>
        <p v-if="!authzStore.dirty" class="oa-text-caption oa-text-subtle">尚未产生任何修改。</p>
        <template v-else>
          <p class="diff-line">
            权限树：新增 <b class="is-add oa-tnum">{{ diff.addedIds.length }}</b> 项，移除
            <b class="is-remove oa-tnum">{{ diff.removedIds.length }}</b> 项
          </p>
          <el-collapse v-if="!diff.empty">
            <el-collapse-item
              :title="`查看新增 ${diff.addedIds.length} 项 / 移除 ${diff.removedIds.length} 项明细`"
            >
              <div v-if="diff.added.length" class="diff-list">
                <p class="diff-title">新增（{{ diff.added.length }}）</p>
                <ul>
                  <li v-for="node in diff.added" :key="`add-${node.id}`">
                    <span class="is-add">+</span>
                    {{ node.name }}
                    <span class="oa-mono oa-text-caption">{{ node.code }}</span>
                    <span class="oa-text-caption oa-text-subtle">
                      {{ permissionPathText(authzStore.permissionIndex, node.id) }}
                    </span>
                  </li>
                </ul>
              </div>
              <div v-if="diff.removed.length" class="diff-list">
                <p class="diff-title">移除（{{ diff.removed.length }}）</p>
                <ul>
                  <li v-for="node in diff.removed" :key="`rm-${node.id}`">
                    <span class="is-remove">−</span>
                    {{ node.name }}
                    <span class="oa-mono oa-text-caption">{{ node.code }}</span>
                    <span class="oa-text-caption oa-text-subtle">
                      {{ permissionPathText(authzStore.permissionIndex, node.id) }}
                    </span>
                  </li>
                </ul>
              </div>
            </el-collapse-item>
          </el-collapse>
          <p class="oa-text-caption oa-text-subtle">
            数据域：{{ dataScopeLabel(draft.savedDataScope) }}
            <template v-if="authzStore.dataScopeDirty">→ {{ dataScopeLabel(draft.dataScope) }}</template>
            <template v-if="authzStore.categoryDirty"> · 事项类别范围已修改</template>
            <template v-if="authzStore.orgNodeDirty"> · 组织节点范围已修改</template>
          </p>
        </template>
      </section>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <span class="oa-text-caption oa-text-subtle">
          <template v-if="readonlyPanel">只读视图（无授权维护权限）</template>
          <template v-else-if="authzStore.dirty">有未保存的修改</template>
          <template v-else>没有未保存的修改</template>
        </span>
        <el-button @click="close">关闭</el-button>
        <el-button
          v-if="!readonlyPanel"
          type="primary"
          :loading="saving"
          :disabled="!authzStore.dirty"
          @click="submit"
        >
          保存授权
        </el-button>
      </div>
    </template>
  </el-drawer>

  <!-- ==================== 权限节点新增 / 编辑 ==================== -->
  <el-dialog
    v-model="nodeDialog.visible"
    :title="nodeDialog.mode === 'create' ? '新增权限节点' : '编辑权限节点'"
    width="560px"
    append-to-body
  >
    <el-form label-position="top">
      <el-form-item label="上级节点">
        <el-tree-select
          v-model="nodeDialog.parentId"
          :data="permissionSelectTree"
          node-key="value"
          check-strictly
          :render-after-expand="false"
          filterable
          clearable
          class="fill"
          placeholder="留空 = 根节点（一级菜单）"
        />
      </el-form-item>
      <el-form-item label="节点类型" required>
        <el-select v-model="nodeDialog.permType" class="fill">
          <el-option
            v-for="type in PERMISSION_TYPES"
            :key="type"
            :value="type"
            :label="PERMISSION_TYPE_LABEL[type]"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="权限码" required>
        <el-input v-model="nodeDialog.code" class="oa-mono" placeholder="唯一权限码（冒号风格），如 admin:role:grant" />
        <p class="hint">唯一性由服务端裁决（`uk_sys_permission_code`）；权限码下发后不要改名。</p>
      </el-form-item>
      <el-form-item label="权限名称" required>
        <el-input v-model="nodeDialog.name" maxlength="50" show-word-limit placeholder="不超过 50 字" />
      </el-form-item>
      <el-form-item label="前端路由 / 接口路径">
        <el-input v-model="nodeDialog.url" placeholder="选填，如 /admin/roles 或 /authz/roles" />
      </el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="nodeDialog.sortNo" :min="0" :max="9999" />
        <span class="hint inline">同层升序展示（`sys_permission.sort_no`）</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="nodeDialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="nodeDialog.submitting" @click="submitNode">确认保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-sm);
}

.role-head .line {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  flex-wrap: wrap;
}

.role-head p {
  margin-top: var(--oa-space-xxs);
}

.block {
  display: flex;
  flex-direction: column;
  gap: var(--oa-space-xs);
}

.band-note {
  margin-left: auto;
  font-weight: 400;
}

.scope-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--oa-space-xxs);
}

.scope-item {
  height: auto;
  margin-right: 0;
}

.scope-label {
  margin-right: var(--oa-space-xs);
  font: var(--oa-font-label);
  color: var(--oa-color-ink);
}

.category-box {
  margin-top: var(--oa-space-xs);
  padding: var(--oa-space-sm);
  background: var(--oa-color-canvas-subtle);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
}

.category-box.is-error {
  border-color: var(--oa-color-error);
  background: var(--oa-color-error-surface);
}

.required {
  margin: 0 2px;
  color: var(--oa-color-error);
}

.error-text {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  color: var(--oa-color-error);
}

.tree-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--oa-space-xs);
}

.tree-filter {
  width: 240px;
}

.tree-wrap {
  min-height: 200px;
  max-height: 420px;
  overflow: auto;
  padding: var(--oa-space-xs);
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-sm);
}

/* 只读态：整棵树只能看不能点（比逐节点置灰更明确，也避免误导为「可勾选但没生效」） */
.tree-wrap.is-readonly {
  background: var(--oa-color-canvas-subtle);
}

.tree-wrap.is-readonly .perm-tree {
  pointer-events: none;
  opacity: 0.85;
}

.perm-tree {
  background: var(--oa-color-canvas);
}

.node-row {
  display: inline-flex;
  align-items: center;
  gap: var(--oa-space-xs);
  width: 100%;
}

.node-name {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.node-code {
  color: var(--oa-color-ink-subtle);
}

.node-type {
  flex: none;
}

.node-actions {
  margin-left: auto;
  opacity: 0;
  transition: opacity 120ms ease-out;
}

.perm-tree :deep(.el-tree-node__content:hover) .node-actions {
  opacity: 1;
}

.diff-line {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
}

.is-add {
  color: var(--oa-color-success);
  font-weight: 500;
}

.is-remove {
  color: var(--oa-color-error);
  font-weight: 500;
}

.diff-list ul {
  margin: var(--oa-space-xxs) 0 var(--oa-space-xs);
  padding-left: var(--oa-space-md);
}

.diff-list li {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-muted);
  line-height: 1.8;
}

.diff-title {
  font: var(--oa-font-label);
  color: var(--oa-color-ink);
}

.drawer-footer {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  width: 100%;
}

.drawer-footer > span:first-child {
  margin-right: auto;
}

.fill {
  width: 100%;
}

.hint {
  margin-top: var(--oa-space-xxs);
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-subtle);
}

.hint.inline {
  margin-left: var(--oa-space-xs);
}
</style>
