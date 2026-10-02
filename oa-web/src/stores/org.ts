/**
 * oa-web · 组织树 Store
 * ----------------------------------------------------------------------------
 * 职责：组织树缓存、选中节点、展开态与检索结果。
 * 为什么要有这一层：`OrgTreeView`（左树右详情）、人员列表的组织筛选、人员编辑的组织选择器
 * 共享同一份组织数据；若各页面各拉一次 `/identity/orgs/tree`，会出现「树刚改名、下拉还是旧名」
 * 的不一致。因此组织树在这里做**单一数据源**，写操作成功后 `reload()` 一次即可全站一致。
 *
 * 口径来源：
 *   · `doc/import-spec.md` §3.2（org_path 为唯一业务键、四级层级）
 *   · `doc/prd-0.1.md` 5.1（集团-公司-部门-科室）、5.5（组织变更处理）
 *   · `DESIGN.md` › Navigation › `sidebar-tree-node`（四级、缩进 12px/级、高 32px）
 *
 * 注意：停用节点**保留在树里**（管理后台必须能看见并重新启用），仅用状态徽标区分；
 *      对外提供 `activeTree` 供「只能选启用节点」的场景（如发起归属、调岗目标）使用。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchOrgTree, searchOrgs } from '@/api/org'
import type { OrgBrief, OrgTreeNode } from '@/types/identity'

/**
 * 把树摊平成 id → 节点 的索引。
 * `seen` 兼作环保护：脏数据（父子互指）只会让该节点被跳过，不会把页面挂死。
 */
function indexTree(
  nodes: OrgTreeNode[],
  map: Record<string, OrgTreeNode>,
  seen: Set<string> = new Set<string>(),
): void {
  for (const node of nodes) {
    if (seen.has(node.id)) continue
    seen.add(node.id)
    map[node.id] = node
    if (node.children?.length) indexTree(node.children, map, seen)
  }
}

/** 从根到指定节点的祖先链（不含自身），用于面包屑与「展开到该节点」 */
function ancestorIdsOf(id: string, map: Record<string, OrgTreeNode>): string[] {
  const chain: string[] = []
  let cursor = map[id]?.parentId ?? null
  let guard = 0
  while (cursor && map[cursor] && guard < 32) {
    chain.unshift(cursor)
    cursor = map[cursor].parentId
    guard += 1
  }
  return chain
}

/** 收集某节点及其整棵子树的 id（移动节点时必须排除自身分支，避免把父节点挂到自己的子孙下） */
export function collectSubtreeIds(nodes: OrgTreeNode[], id: string): string[] {
  const result: string[] = []
  const walk = (list: OrgTreeNode[]): void => {
    for (const node of list) {
      if (node.id === id) {
        collectAll(node, result)
        return
      }
      if (node.children?.length) walk(node.children)
    }
  }
  walk(nodes)
  return result
}

function collectAll(node: OrgTreeNode, out: string[]): void {
  out.push(node.id)
  for (const child of node.children ?? []) collectAll(child, out)
}

/** 组织树副本：剔除被移动的子树，供「选择新的上级节点」使用 */
export function treeWithoutSubtree(nodes: OrgTreeNode[], excludeId: string): OrgTreeNode[] {
  const excluded = new Set(collectSubtreeIds(nodes, excludeId))
  const filter = (list: OrgTreeNode[]): OrgTreeNode[] =>
    list
      .filter((node) => !excluded.has(node.id))
      .map((node) => (node.children?.length ? { ...node, children: filter(node.children) } : { ...node }))
  return filter(nodes)
}

export const useOrgStore = defineStore('org', () => {
  // ---- state ----
  const tree = ref<OrgTreeNode[]>([])
  const flatMap = ref<Record<string, OrgTreeNode>>({})
  const selectedId = ref<string>('')
  const expandedIds = ref<string[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const errorMessage = ref('')

  const searchKeyword = ref('')
  const searchResults = ref<OrgBrief[]>([])
  const searching = ref(false)

  // ---- getters ----
  const rootOrg = computed<OrgTreeNode | null>(() => tree.value[0] ?? null)

  const selectedNode = computed<OrgTreeNode | null>(() =>
    selectedId.value ? flatMap.value[selectedId.value] ?? null : null,
  )

  const flatList = computed<OrgTreeNode[]>(() => Object.values(flatMap.value))

  /** 选中节点的祖先链（含自身），用于面包屑 */
  const selectedChain = computed<OrgTreeNode[]>(() => {
    if (!selectedId.value || !flatMap.value[selectedId.value]) return []
    const ids = [...ancestorIdsOf(selectedId.value, flatMap.value), selectedId.value]
    return ids.map((id) => flatMap.value[id]).filter(Boolean)
  })

  /** 仅启用节点的树：发起归属、调岗目标等「不能选停用节点」的场景使用 */
  const activeTree = computed<OrgTreeNode[]>(() => {
    const filterTree = (list: OrgTreeNode[]): OrgTreeNode[] =>
      list
        .filter((node) => node.status === 'active')
        .map((node) => ({ ...node, children: node.children?.length ? filterTree(node.children) : undefined }))
    return filterTree(tree.value)
  })

  const hasTree = computed(() => tree.value.length > 0)

  /**
   * 「未设正职」节点（AC-11 / W-ORG-014）：部门与科室必须有正职负责人，否则成员发起审批时
   * 审批人候选为空、服务端直接拒绝发起。
   *
   * 判据是树接口的 `OrgView.hasPrimaryLeader`（服务端按 `sys_org_leader` 中
   * `leader_type='primary' AND category IS NULL` 全树**一次批量查询**聚合的权威值），
   * **不再用 `leaderId` 推断**——那只是 `sys_org.leader_id` 的冗余列，
   * 集团层业务线分管领导（category 非空）会让判定误判为「已设正职」。
   * 由 `api/org.ts` 的映射层写入节点，这里只做汇总，供树上与详情区常驻提示。
   */
  const orgsMissingPrimaryLeader = computed<OrgTreeNode[]>(() =>
    flatList.value.filter(
      (node) => (node.orgType === 'dept' || node.orgType === 'section') && node.hasPrimaryLeader === false,
    ),
  )

  /** 全量展开 / 收起用：所有含子节点的节点 id */
  const expandableIds = computed<string[]>(() =>
    flatList.value.filter((node) => (node.children?.length ?? 0) > 0).map((node) => node.id),
  )

  function isExpanded(id: string): boolean {
    return expandedIds.value.includes(id)
  }

  // ---- actions ----
  /** 拉取组织树；`force` 用于写操作成功后的强制刷新 */
  async function loadTree(force = false): Promise<void> {
    if (loading.value) return
    if (loaded.value && !force) return
    loading.value = true
    errorMessage.value = ''
    try {
      const nodes = await fetchOrgTree({ includeDisabled: true })
      tree.value = nodes
      const map: Record<string, OrgTreeNode> = {}
      indexTree(nodes, map)
      flatMap.value = map
      loaded.value = true
      // 首次进入默认展开根节点，避免整屏只有一个可点行
      if (expandedIds.value.length === 0 && nodes[0]) expandedIds.value = [nodes[0].id]
      if (!selectedId.value && nodes[0]) selectedId.value = nodes[0].id
    } catch (error) {
      errorMessage.value = (error as Error).message || '组织树加载失败'
      tree.value = []
      flatMap.value = {}
    } finally {
      loading.value = false
    }
  }

  async function reload(): Promise<void> {
    loaded.value = false
    await loadTree(true)
  }

  /** 选中节点并自动展开其祖先链（否则选中项在折叠分支里看不见） */
  function select(id: string): void {
    selectedId.value = id
    if (!flatMap.value[id]) return
    const need = ancestorIdsOf(id, flatMap.value).filter((item) => !expandedIds.value.includes(item))
    if (need.length) expandedIds.value = [...expandedIds.value, ...need]
  }

  function toggleExpand(id: string): void {
    expandedIds.value = isExpanded(id)
      ? expandedIds.value.filter((item) => item !== id)
      : [...expandedIds.value, id]
  }

  function setExpandedAll(expand: boolean): void {
    expandedIds.value = expand ? [...expandableIds.value] : []
  }

  function removeNode(id: string): void {
    const drop = (list: OrgTreeNode[]): OrgTreeNode[] =>
      list
        .filter((node) => node.id !== id)
        .map((node) => ({ ...node, children: node.children?.length ? drop(node.children) : undefined }))
    tree.value = drop(tree.value)
    const map: Record<string, OrgTreeNode> = {}
    indexTree(tree.value, map)
    flatMap.value = map
    expandedIds.value = expandedIds.value.filter((item) => item !== id)
    if (selectedId.value === id) selectedId.value = tree.value[0]?.id ?? ''
  }

  /** 组织检索（服务端）：命中 path/name，用于搜索框与远程选择器 */
  async function search(keyword: string): Promise<void> {
    searchKeyword.value = keyword
    const trimmed = keyword.trim()
    if (!trimmed) {
      searchResults.value = []
      return
    }
    searching.value = true
    try {
      // 后端 /orgs/search 只接受 keyword（无 limit），返回平铺 OrgView
      searchResults.value = await searchOrgs(trimmed)
    } catch {
      searchResults.value = []
    } finally {
      searching.value = false
    }
  }

  function clearSearch(): void {
    searchKeyword.value = ''
    searchResults.value = []
  }

  /** 退出登录 / 切换账号时必须清空，避免残留他人可见的组织结构 */
  function reset(): void {
    tree.value = []
    flatMap.value = {}
    selectedId.value = ''
    expandedIds.value = []
    loading.value = false
    loaded.value = false
    errorMessage.value = ''
    clearSearch()
  }

  return {
    // state
    tree,
    flatMap,
    selectedId,
    expandedIds,
    loading,
    loaded,
    errorMessage,
    searchKeyword,
    searchResults,
    searching,
    // getters
    rootOrg,
    selectedNode,
    flatList,
    selectedChain,
    activeTree,
    hasTree,
    orgsMissingPrimaryLeader,
    expandableIds,
    // helpers
    isExpanded,
    // actions
    loadTree,
    reload,
    select,
    toggleExpand,
    setExpandedAll,
    removeNode,
    search,
    clearSearch,
    reset,
    // pure helpers（对外复用）
    treeWithoutSubtree,
  }
})
