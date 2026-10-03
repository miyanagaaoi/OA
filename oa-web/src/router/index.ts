/**
 * oa-web · 路由与登录守卫
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md` › Layout（主导航只预留「审批中心」一个模块入口；
 *       审批中心内部为「列表 + 详情」双栏并置，列表页路由即 Tab）
 *       + `doc/tech-design.md` §6（会话 Cookie、未登录跳登录并回跳）
 *
 * 路由表：
 *   /login                    登录（账号 + 口令 + 记住我 + 锁定提示）
 *   /                         审批中心外壳（DefaultLayout）
 *     /task/pending           待我审批（默认落地页）
 *     /task/approved          我已审批
 *     /task/initiated         我发起的
 *     /task/cc                抄送我的（**接口未实现**，页面如实标注「待实现」）
 *     /archive                历史库（满 3 年归档，只读、可按单号检索）
 *     /form/new/:formType      发起单据（阶段 2b：schema 驱动表单页，四类共用）
 *     /form/instance/:instanceId  继续填单 / 补件 / 重提（实例锁定版本 schema）
 *     /task/:id               单据详情（表单 + 轨迹 + 审批动作 + 水印）
 *     /print/:id              A4 打印预览（正式打印签名栏空栏）
 *   /admin                    管理后台总览（其余模块占位）
 *     /admin/orgs             组织架构（阶段 1 · 1.1：组织树 + 负责人 + 岗位）
 *     /admin/users            人员管理（阶段 1 · 1.1：人员列表 + 岗位 + 离职/调岗/交接）
 *     /admin/roles            角色与权限（阶段 1 · 1.4：角色 + 权限树逐级勾选 + 数据域）
 *     /admin/authz-logs       权限变更日志（阶段 1 · 1.4：REQ-LOG-004，只读）
 *     /admin/flow/template    流程模板列表（阶段 2a.6：2a.2 模板接口；code/formType/status 过滤）
 *     /admin/flow/template/:templateId  流程设计器（阶段 2a.6：节点序列 + 节点配置 + Q6/Q7 闸门 +
 *                              12 条发布前校验 + 版本历史只读查看）
 *   /:pathMatch(.*)*          404
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  canEnterAdminConsole,
  canImportOrgUser,
  canManageOrg,
  canManageUser,
  canOpenAuthzLogAdmin,
  canOpenRoleAdmin,
  canReadFlowTemplate,
  FLOW_USE_PERMISSION,
} from '@/utils/admin'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/task/pending',
    children: [
      {
        path: 'task/pending',
        name: 'task-pending',
        component: () => import('@/views/TaskCenterView.vue'),
        meta: { tab: 'pending', title: '待我审批' },
      },
      {
        path: 'task/approved',
        name: 'task-approved',
        component: () => import('@/views/TaskCenterView.vue'),
        meta: { tab: 'approved', title: '我已审批' },
      },
      {
        path: 'task/initiated',
        name: 'task-initiated',
        component: () => import('@/views/TaskCenterView.vue'),
        meta: { tab: 'initiated', title: '我发起的' },
      },
      {
        path: 'task/cc',
        name: 'task-cc',
        component: () => import('@/views/TaskCenterView.vue'),
        meta: { tab: 'cc', title: '抄送我的' },
      },
      {
        // 阶段 2b：发起单据（schema 驱动表单页，四类共用一套渲染器）
        // 判据：**写**入口要 `flow` 单码（与 `POST /flow-instances` 的入口闸门同源）
        path: 'form/new/:formType',
        name: 'form-new',
        component: () => import('@/views/FormFillView.vue'),
        meta: { title: '发起单据', formSection: 'initiate', requiredAnyPermission: [FLOW_USE_PERMISSION.use] },
      },
      {
        // 阶段 2b：继续填单 / 补件 / 重提（按**实例锁定版本**的 schema 渲染，AC-09）
        // 判据：既要能读实例（flow ∪ admin:flow）也可能要写 → 用只读判据放行，写按钮在页内按 `flow` 收紧
        path: 'form/instance/:instanceId',
        name: 'form-edit',
        component: () => import('@/views/FormFillView.vue'),
        meta: {
          title: '单据填单',
          formSection: 'initiate',
          requiredAnyPermission: [FLOW_USE_PERMISSION.use, FLOW_USE_PERMISSION.adminFlow],
        },
      },
      {
        path: 'archive',
        name: 'archive',
        component: () => import('@/views/TaskCenterView.vue'),
        meta: { tab: 'approved', title: '历史库', archiveOnly: true },
      },
      {
        path: 'task/:id',
        name: 'task-detail',
        component: () => import('@/views/TaskDetailView.vue'),
        meta: { title: '单据详情' },
        props: true,
      },
      {
        path: 'print/:id',
        name: 'print-preview',
        component: () => import('@/views/PrintPreviewView.vue'),
        meta: { title: '打印预览' },
        props: true,
      },
      {
        // 管理后台总览（其余模块仍为占位，见 AdminPlaceholderView）
        // 判据见下方 adminSection gate：系统管理员，或命中任一 `admin:*` 权限
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/AdminPlaceholderView.vue'),
        meta: {
          title: '管理后台',
          adminSection: 'console',
        },
      },
      {
        // 阶段 1 · 1.1 组织树与人员：组织架构（左树右详情 + 负责人面板）
        // 判据：系统管理员，或命中 `admin:org:*`（前缀匹配，见 utils/admin.ts）
        path: 'admin/orgs',
        name: 'admin-orgs',
        component: () => import('@/views/admin/OrgTreeView.vue'),
        meta: {
          title: '组织架构',
          adminSection: 'org',
        },
      },
      {
        // 阶段 1 · 1.1 组织树与人员：人员管理（一人多岗、离职/调岗/交接）
        // 判据：系统管理员，或命中 `admin:user:*`（前缀匹配）
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserListView.vue'),
        meta: {
          title: '人员管理',
          adminSection: 'user',
        },
      },
      {
        // 阶段 1 · 1.4 角色与权限树：角色列表 + 权限树逐级勾选 + 数据域/类别/组织节点
        // 判据：系统管理员，或显式持有 `admin:role:list`（侧栏与路由同口径；
        // 页面内再按角色层级做分级可见性）
        path: 'admin/roles',
        name: 'admin-roles',
        component: () => import('@/views/admin/RoleListView.vue'),
        meta: {
          title: '角色与权限',
          adminSection: 'role',
        },
      },
      {
        // 阶段 1 · 1.8：组织人员批量导入（五步流水线：组织→人员→负责人→岗位→角色分配）
        // 判据：系统管理员 / 分公司流程管理员 / 显式持有 `admin:user:import`
        // 注意：入口可见 ≠ 可越域导入——分公司管理员只能导入本公司子树，服务端逐行 fail-closed
        path: 'admin/bulk-import',
        name: 'admin-bulk-import',
        component: () => import('@/views/admin/BulkImportView.vue'),
        meta: {
          title: '批量导入',
          adminSection: 'bulk-import',
        },
      },
      {
        // 阶段 1 · 1.4：权限变更日志（REQ-LOG-004 / AC-59，只读、不可删改 REQ-LOG-006）
        // 判据：系统管理员，或显式持有 `admin:audit:permission`
        path: 'admin/authz-logs',
        name: 'admin-authz-logs',
        component: () => import('@/views/admin/AuthzChangeLogView.vue'),
        meta: {
          title: '权限变更日志',
          adminSection: 'authz-log',
        },
      },
      {
        // 阶段 2a.6 流程设计器：模板列表（四类单据各不相同；筛选走服务端 @RequestParam）
        // 判据：系统管理员 / `admin:flow:template`（读）；写入口（开新草稿/发布/归档）在页内
        // 再按 `admin:flow:publish` 收紧，服务端仍是裁决方（越权一律 403 / 40906）
        path: 'admin/flow/template',
        name: 'admin-flow-templates',
        component: () => import('@/views/admin/FlowTemplateListView.vue'),
        meta: {
          title: '流程模板',
          adminSection: 'flow-template',
        },
      },
      {
        // 阶段 2a.6 流程设计器：单个模板的节点/闸门/发布前校验/版本历史
        // 判据同列表页（`admin:flow:template`）；节点写入按 `admin:flow:node`、
        // 版本写入按 `admin:flow:publish` 在页内收紧
        path: 'admin/flow/template/:templateId',
        name: 'admin-flow-designer',
        component: () => import('@/views/admin/FlowDesignerView.vue'),
        meta: {
          title: '流程设计器',
          adminSection: 'flow-template',
        },
        props: true,
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/task/pending',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// ---------------------------------------------------------------------------
// 登录守卫：未登录一律回 /login 并带 redirect；已登录访问 /login 直接进审批中心
// ---------------------------------------------------------------------------
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    if (userStore.isAuthenticated && to.name === 'login') {
      return { path: '/task/pending' }
    }
    return true
  }

  if (!userStore.isAuthenticated) {
    // 刷新页面后用 HttpOnly Cookie 重建会话画像
    await userStore.hydrate()
  }

  if (!userStore.isAuthenticated) {
    userStore.markSessionExpired()
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const required = to.meta.requiredPermission as string | undefined
  if (required && !userStore.hasPermission(required) && !userStore.isSuperAdmin) {
    // 权限不可见优于不可用：无权限直接回落到默认列表，不渲染入口
    return { path: '/task/pending' }
  }

  // 管理后台各分区的判据与 `utils/admin.ts` **逐条一致**（侧栏与路由同口径，避免「看得到、点不进」）：
  //   · console/org/user 支持 `admin:*` 前缀匹配（权限种子里是 `admin:org:tree` 这类细项）；
  //   · role/authz-log **不做 company_admin 角色码兜底**（PRD 5.2：分公司管理员不可再授权）。
  // ⚠ 前端只决定「渲不渲染 / 放不放行」：服务端才是裁决方，越权请求一律 403。
  const adminSection = to.meta.adminSection
  if (adminSection === 'console' && !canEnterAdminConsole(userStore)) {
    return { path: '/task/pending' }
  }
  if (adminSection === 'org' && !canManageOrg(userStore)) {
    return { path: '/task/pending' }
  }
  if (adminSection === 'user' && !canManageUser(userStore)) {
    return { path: '/task/pending' }
  }
  if (adminSection === 'role' && !canOpenRoleAdmin(userStore)) {
    return { path: '/task/pending' }
  }
  if (adminSection === 'authz-log' && !canOpenAuthzLogAdmin(userStore)) {
    return { path: '/task/pending' }
  }
  if (adminSection === 'bulk-import' && !canImportOrgUser(userStore)) {
    return { path: '/task/pending' }
  }
  // 阶段 2a.6：流程模板（列表 + 设计器）只要求**读**权限 `admin:flow:template`；
  // 节点写（admin:flow:node）与发布写（admin:flow:publish）在页面内按权限收紧入口，
  // 服务端（FlowConfigPermission）才是裁决方。
  if (adminSection === 'flow-template' && !canReadFlowTemplate(userStore)) {
    return { path: '/task/pending' }
  }

  // 「任一权限即可」场景（管理后台总览）：一个都不命中则回落
  const requiredAny = to.meta.requiredAnyPermission
  if (
    requiredAny?.length &&
    !userStore.isSuperAdmin &&
    !requiredAny.some((code) => userStore.hasPermission(code))
  ) {
    return { path: '/task/pending' }
  }

  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) || ''
  document.title = title ? `${title} · 集团OA审批系统` : '集团OA审批系统'
})

export default router
