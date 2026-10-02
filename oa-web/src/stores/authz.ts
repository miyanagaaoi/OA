/**
 * oa-web · 权限域 Store（角色列表 / 权限树缓存 / 当前授权草稿）
 * ----------------------------------------------------------------------------
 * 职责：
 *   1. **角色列表缓存**：`RoleListView` 与 `UserRolesDrawer`（分配角色下拉）共用一份角色数据，
 *      避免「刚改完角色名、分配下拉还是旧名」。
 *   2. **权限树缓存**：权限树是全站唯一的一棵树（menu/button/api），只拉一次；
 *      保存成功后才强制刷新（服务端可能回填父节点）。
 *   3. **授权草稿（含 dirty 标记）**：`RolePermissionPanel` 的勾选先落在草稿里，
 *      与「已保存基线」对比得出 `dirty` 与「新增/移除 N 项」的差异；
 *      **未保存前不打后端**，切换角色或关闭前由界面提示放弃修改。
 *
 * 口径来源：
 *   · `doc/prd-0.1.md` 5.2（权限树配置逐级分配）、6.10 REQ-ADMIN-003（权限配置）、
 *     REQ-ADMIN-006（管理员边界）、6.9 REQ-LOG-004（变更留痕）
 *   · `doc/data-model.md` 3.1（`data_scope` 五值）/ 3.3（类别五值，仅 `group_category` 需要）
 *   · `DESIGN.md` Agent Usage Rules 第 6 条「权限不可见优于不可用」
 *
 * 与 `stores/org.ts` 的差别：本 store 不做演示数据降级——角色与权限是安全主数据，
 * 静默回落演示数据会让管理员误判真实授权范围（同 `api/authz.ts` 的约定）。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchCategories,
  fetchDataScopes,
  fetchPermissionTree,
  fetchRoleCategories,
  fetchRoleOrgNodes,
  fetchRolePermissions,
  fetchRoles,
  invalidatePermissionCache,
  saveRolePermissions,
  updateRoleCategories,
  updateRoleDataScope,
  updateRoleOrgNodes,
} from '@/api/authz'
import type { DataScope } from '@/types/api'
import type {
  CategoryOption,
  DataScopeOption,
  MatterCategory,
  PermissionDiff,
  PermissionNode,
  RoleItem,
  RolePermissionUpdatePayload,
} from '@/types/authz'
import {
  checkDataScopeCategories,
  collectPermissionLeafIds,
  diffPermissionIdSets,
  indexPermissionTree,
  resolveCheckedStateFromLeaves,
  toCheckableLeafIds,
  toPermissionNodes,
} from '@/utils/authz'

/** 集合比较：顺序无关（服务端返回顺序不可依赖） */
function sameIdSet(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false
  const set = new Set(a)
  return b.every((item) => set.has(item))
}

/** 授权草稿：保存基线 + 未保存的修改（同一结构，便于直接对比） */
export interface RoleGrantDraft {
  roleId: string
  roleCode: string
  roleName: string
  /** ---- 已保存基线（用于差异与 dirty） ---- */
  savedPermissionIds: string[]
  savedOrgNodeIds: string[]
  savedCategories: MatterCategory[]
  savedDataScope: DataScope
  /** ---- 草稿 ---- */
  /** 全量已勾选（含联动勾上的父节点） */
  permissionIds: string[]
  /** 半选父节点（只用于回显与提交副本，不参与 dirty 判定） */
  halfCheckedIds: string[]
  orgNodeIds: string[]
  categories: MatterCategory[]
  dataScope: DataScope
}

function emptyDraft(): RoleGrantDraft {
  return {
    roleId: '',
    roleCode: '',
    roleName: '',
    savedPermissionIds: [],
    savedOrgNodeIds: [],
    savedCategories: [],
    savedDataScope: 'self',
    permissionIds: [],
    halfCheckedIds: [],
    orgNodeIds: [],
    categories: [],
    dataScope: 'self',
  }
}

/** 一次保存的变更摘要（界面据此拼提示文案） */
export interface RoleGrantSaveResult {
  permissionChanged: boolean
  orgNodeChanged: boolean
  categoryChanged: boolean
  dataScopeChanged: boolean
  /** 服务端返回的缓存失效说明（缺省时用统一文案） */
  cacheNote: string
}

export const useAuthzStore = defineStore('authz', () => {
  // ---- state：角色列表 ----
  const roles = ref<RoleItem[]>([])
  const rolesLoading = ref(false)
  const rolesLoaded = ref(false)
  const rolesError = ref('')

  // ---- state：权限树 ----
  const permissionTree = ref<PermissionNode[]>([])
  const permissionTreeLoading = ref(false)
  const permissionTreeLoaded = ref(false)
  const permissionTreeError = ref('')

  // ---- state：字典（数据域 / 事项类别） ----
  const dataScopes = ref<DataScopeOption[]>([])
  const categories = ref<CategoryOption[]>([])
  const dictionariesLoaded = ref(false)

  // ---- state：授权草稿 ----
  const draft = ref<RoleGrantDraft>(emptyDraft())
  const grantLoading = ref(false)
  const grantError = ref('')
  const saving = ref(false)
  /**
   * 「角色 × 组织节点」接口不可用（未就绪 / 无权限）标记。
   * 该子项不是权限树勾选主路径的依赖，取不到时**不阻断**整页，
   * 但界面必须显式说明并禁用该分区——否则会把「没取到」误当成「本来就没授权」。
   */
  const orgNodesUnavailable = ref(false)
  /** 服务端回传的已授权组织节点路径（缺省时界面用组织树本地补全） */
  const draftOrgPaths = ref<string[]>([])

  // ---- getters ----
  const permissionIndex = computed(() => indexPermissionTree(permissionTree.value))

  const permissionList = computed<PermissionNode[]>(() => [...permissionIndex.value.values()])

  /** 全部叶子 id（「存叶子」口径的回显与提交都要用） */
  const permissionLeafIds = computed(() => collectPermissionLeafIds(permissionTree.value))

  /** el-tree 回显用：只把「末端已勾选节点」交给 setCheckedKeys，父节点由联动推导 */
  const draftCheckedLeafIds = computed(() =>
    toCheckableLeafIds(permissionTree.value, draft.value.permissionIds),
  )

  const permissionDirty = computed(() =>
    !sameIdSet(draft.value.savedPermissionIds, draft.value.permissionIds),
  )
  const orgNodeDirty = computed(() => !sameIdSet(draft.value.savedOrgNodeIds, draft.value.orgNodeIds))
  const categoryDirty = computed(() => !sameIdSet(draft.value.savedCategories, draft.value.categories))
  const dataScopeDirty = computed(() => draft.value.savedDataScope !== draft.value.dataScope)

  /** 是否有未保存修改（离开页面前提示） */
  const dirty = computed(
    () => permissionDirty.value || orgNodeDirty.value || categoryDirty.value || dataScopeDirty.value,
  )

  /** 「新增 / 移除 N 项」差异（权限树部分） */
  const permissionDiff = computed<PermissionDiff>(() => {
    const { addedIds, removedIds } = diffPermissionIdSets(
      draft.value.savedPermissionIds,
      draft.value.permissionIds,
    )
    return {
      addedIds,
      removedIds,
      added: toPermissionNodes(permissionIndex.value, addedIds),
      removed: toPermissionNodes(permissionIndex.value, removedIds),
      empty: addedIds.length === 0 && removedIds.length === 0,
    }
  })

  /** 草稿校验：`group_category` 必须配类别（硬要求 4，保存前置拦截） */
  const draftValidationError = computed(() =>
    checkDataScopeCategories(draft.value.dataScope, draft.value.categories),
  )

  // ---- actions：角色列表 ----
  async function loadRoles(force = false): Promise<void> {
    if (rolesLoading.value) return
    if (rolesLoaded.value && !force) return
    rolesLoading.value = true
    rolesError.value = ''
    try {
      const result = await fetchRoles({ page: 1, pageSize: 200 })
      roles.value = result.list
      rolesLoaded.value = true
    } catch (error) {
      roles.value = []
      rolesError.value = (error as Error).message || '角色列表加载失败'
    } finally {
      rolesLoading.value = false
    }
  }

  async function reloadRoles(): Promise<void> {
    rolesLoaded.value = false
    await loadRoles(true)
  }

  /** 按 id 取角色（草稿标题、分配下拉都用它，避免各页面各自 filter） */
  function roleById(roleId: string): RoleItem | null {
    return roles.value.find((role) => role.roleId === roleId) ?? null
  }

  // ---- actions：权限树与字典 ----
  async function loadPermissionTree(force = false): Promise<void> {
    if (permissionTreeLoading.value) return
    if (permissionTreeLoaded.value && !force) return
    permissionTreeLoading.value = true
    permissionTreeError.value = ''
    try {
      permissionTree.value = await fetchPermissionTree()
      permissionTreeLoaded.value = true
    } catch (error) {
      permissionTree.value = []
      permissionTreeError.value = (error as Error).message || '权限树加载失败'
    } finally {
      permissionTreeLoading.value = false
    }
  }

  /** 数据域与类别字典：一次取回并缓存（两者都是小字典，且总是一起用） */
  async function loadDictionaries(force = false): Promise<void> {
    if (dictionariesLoaded.value && !force) return
    try {
      const [scopeList, categoryList] = await Promise.all([fetchDataScopes(), fetchCategories()])
      dataScopes.value = scopeList
      categories.value = categoryList
      dictionariesLoaded.value = true
    } catch (error) {
      // 字典失败不阻断页面：标签由 `utils/authz.ts` 的常量兜底
      categories.value = []
      dictionariesLoaded.value = false
      throw error
    }
  }

  // ---- actions：授权草稿 ----
  /**
   * 载入某角色的授权草稿（权限树勾选 + 组织节点 + 类别 + 数据域）。
   * 权限树、类别、组织节点分别请求：**权限/类别失败即整体失败**（半截草稿比报错更危险），
   * 只有「组织节点范围」允许降级——它在服务端可能尚未就绪，且取不到时空集合不会造成误写
   * （草稿与基线同为空 → `orgNodeDirty=false` → 保存时根本不会提交该子项）。
   */
  async function loadGrant(role: RoleItem): Promise<void> {
    grantLoading.value = true
    grantError.value = ''
    try {
      if (!permissionTreeLoaded.value) await loadPermissionTree()
      const [grant, roleCategories] = await Promise.all([
        fetchRolePermissions(role.roleId),
        fetchRoleCategories(role.roleId),
      ])

      let orgNodeIds: string[] = []
      let orgPaths: string[] = []
      try {
        const orgGrant = await fetchRoleOrgNodes(role.roleId)
        orgNodeIds = orgGrant.orgNodeIds
        orgPaths = orgGrant.orgPaths ?? []
        orgNodesUnavailable.value = false
      } catch {
        orgNodesUnavailable.value = true
      }
      draftOrgPaths.value = orgPaths

      /*
       * 基线归一：服务端可能回「含父节点的全量集合」也可能只回叶子（后端尚未定稿口径）。
       * 两种都先用 `toCheckableLeafIds` 收成叶子，再用 `resolveCheckedStateFromLeaves`
       * 按 el-tree 的联动语义**重新推导**成全量集合——这样草稿与基线才可比，
       * 差异清单不会因为口径差异凭空冒出「新增/移除」。
       */
      const savedLeaves = toCheckableLeafIds(permissionTree.value, grant.permissionIds)
      const savedState = resolveCheckedStateFromLeaves(permissionTree.value, savedLeaves)

      draft.value = {
        roleId: role.roleId,
        roleCode: role.code,
        roleName: role.name,
        savedPermissionIds: savedState.checkedIds,
        savedOrgNodeIds: orgNodeIds,
        savedCategories: roleCategories.categories,
        savedDataScope: role.dataScope,
        permissionIds: [...savedState.checkedIds],
        halfCheckedIds: savedState.halfCheckedIds,
        orgNodeIds: [...orgNodeIds],
        categories: [...roleCategories.categories],
        dataScope: role.dataScope,
      }
    } catch (error) {
      draft.value = emptyDraft()
      draftOrgPaths.value = []
      grantError.value = (error as Error).message || '角色授权加载失败'
    } finally {
      grantLoading.value = false
    }
  }

  function resetDraft(): void {
    draft.value = emptyDraft()
    draftOrgPaths.value = []
    grantError.value = ''
  }

  /** 权限树勾选变化：`checked` 为全选中的 id（含联动父节点），`halfChecked` 为半选父节点 */
  function setCheckedPermissionIds(checked: string[], halfChecked: string[]): void {
    draft.value = {
      ...draft.value,
      permissionIds: [...checked],
      halfCheckedIds: [...halfChecked],
    }
  }

  function setOrgNodeIds(ids: string[]): void {
    draft.value = { ...draft.value, orgNodeIds: [...ids] }
  }

  function setCategories(list: MatterCategory[]): void {
    draft.value = { ...draft.value, categories: [...list] }
  }

  function setDataScope(scope: DataScope): void {
    // 切到不需要类别的数据域时**不清空**已选类别：可能是误切回来，清空会丢用户输入
    draft.value = { ...draft.value, dataScope: scope }
  }

  /**
   * 保存草稿（只提交发生变化的部分）。
   *
   * 顺序：权限树 → 组织节点 → 类别 → 数据域 → 失效权限缓存。
   * 每一步失败即中断并抛出，**不做乐观更新**：界面在成功后才把基线对齐。
   * 数据域与类别的前置校验（`group_category` 必须配类别）在提交前完成。
   */
  async function saveGrant(reason?: string): Promise<RoleGrantSaveResult> {
    const current = draft.value
    if (!current.roleId) throw new Error('未选择角色')

    const validationError = checkDataScopeCategories(current.dataScope, current.categories)
    if (validationError) throw new Error(validationError)

    saving.value = true
    try {
      const permissionChanged = permissionDirty.value
      if (permissionChanged) {
        const payload: RolePermissionUpdatePayload = {
          // 全量已勾选 id 集合（含半选父节点，保证父级菜单可达）
          permissionIds: [...current.permissionIds],
          leafIds: current.permissionIds.filter((id) => permissionLeafIds.value.includes(id)),
          halfCheckedIds: [...current.halfCheckedIds],
          reason,
        }
        await saveRolePermissions(current.roleId, payload)
      }

      const orgNodeChanged = orgNodeDirty.value
      if (orgNodeChanged) await updateRoleOrgNodes(current.roleId, current.orgNodeIds, reason)

      const categoryChanged = categoryDirty.value
      if (categoryChanged) await updateRoleCategories(current.roleId, current.categories, reason)

      const dataScopeChanged = dataScopeDirty.value
      if (dataScopeChanged) await updateRoleDataScope(current.roleId, current.dataScope, reason)

      // 任一变更都要刷新本人权限画像：管理员改的可能是自己所属角色的权限
      let cacheNote = ''
      if (permissionChanged || categoryChanged || dataScopeChanged || orgNodeChanged) {
        try {
          const result = await invalidatePermissionCache({
            roleIds: [current.roleId],
            reason: reason || '角色授权变更',
          })
          cacheNote = result.note ?? ''
        } catch {
          // 缓存失效失败不影响「授权已保存」的事实：界面仍提示「下次请求生效」
          cacheNote = ''
        }
      }

      // 基线对齐：把草稿的当前值视为已保存值（避免再拉一次树导致的闪烁）
      draft.value = {
        ...current,
        savedPermissionIds: [...current.permissionIds],
        savedOrgNodeIds: [...current.orgNodeIds],
        savedCategories: [...current.categories],
        savedDataScope: current.dataScope,
      }

      return {
        permissionChanged,
        orgNodeChanged,
        categoryChanged,
        dataScopeChanged,
        cacheNote,
      }
    } finally {
      saving.value = false
    }
  }

  /** 退出登录 / 切换账号时清空（权限数据不得跨账号残留） */
  function reset(): void {
    roles.value = []
    rolesLoaded.value = false
    rolesError.value = ''
    permissionTree.value = []
    permissionTreeLoaded.value = false
    permissionTreeError.value = ''
    dataScopes.value = []
    categories.value = []
    dictionariesLoaded.value = false
    orgNodesUnavailable.value = false
    resetDraft()
  }

  return {
    // state
    roles,
    rolesLoading,
    rolesLoaded,
    rolesError,
    permissionTree,
    permissionTreeLoading,
    permissionTreeLoaded,
    permissionTreeError,
    dataScopes,
    categories,
    dictionariesLoaded,
    draft,
    grantLoading,
    grantError,
    saving,
    orgNodesUnavailable,
    draftOrgPaths,
    // getters
    permissionIndex,
    permissionList,
    permissionLeafIds,
    draftCheckedLeafIds,
    permissionDirty,
    orgNodeDirty,
    categoryDirty,
    dataScopeDirty,
    dirty,
    permissionDiff,
    draftValidationError,
    // actions
    loadRoles,
    reloadRoles,
    roleById,
    loadPermissionTree,
    loadDictionaries,
    loadGrant,
    resetDraft,
    setCheckedPermissionIds,
    setOrgNodeIds,
    setCategories,
    setDataScope,
    saveGrant,
    reset,
  }
})
