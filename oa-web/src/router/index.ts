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
 *     /task/cc                抄送我的
 *     /archive                历史库（满 3 年归档，只读、可按单号检索）
 *     /task/:id               单据详情（表单 + 轨迹 + 操作区 + 水印）
 *     /print/:id              A4 打印预览（正式打印签名栏空栏）
 *   /admin                    管理后台占位（一期只留入口，不实现）
 *   /:pathMatch(.*)*          404
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
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
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/AdminPlaceholderView.vue'),
        meta: { title: '管理后台', requiredPermission: 'admin.console' },
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

  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) || ''
  document.title = title ? `${title} · 集团OA审批系统` : '集团OA审批系统'
})

export default router
