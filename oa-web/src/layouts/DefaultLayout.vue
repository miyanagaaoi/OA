<script setup lang="ts">
/**
 * oa-web · 默认布局（审批中心外壳）
 * ----------------------------------------------------------------------------
 * 来源：`DESIGN.md` › Layout › Grid & Container + Components › Navigation
 *   · 左侧深色抽屉导航：展开 224px / 收起 64px（图标条）/ ≤1024px 浮层抽屉
 *   · 主导航只预留「审批中心」一个模块入口：
 *       待我审批 / 我已审批 / 我发起的 / 抄送我的（+ 历史库）
 *   · 顶部栏 56px 白底 + 底部 1px hairline；左面包屑，右侧用户菜单
 *   · 反向令牌只在侧栏内使用；H5 用白色顶栏 + 白色内容区（不用深色导航）
 *   · 导航文字一律用文字 + 计数徽标，不引入图标库（DESIGN.md Known Gaps 未指定图标库）
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { fetchWorkbenchSummary } from '@/api/task'
import Watermark from '@/components/Watermark.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 抽屉展开 / 收起（状态持久化到本地，DESIGN.md 抽屉导航） */
const SIDEBAR_KEY = 'oa.sidebar.rail'
const railed = ref(localStorage.getItem(SIDEBAR_KEY) === '1')
/** ≤768px 时的浮层抽屉开关 */
const overlayOpen = ref(false)
/** 是否处于 H5 断点（决定把手是切换图标条还是浮层抽屉） */
const isMobile = ref(false)

const navItems = [
  { key: 'pending', label: '待我审批', path: '/task/pending', permission: 'portal.workbench.pending' },
  { key: 'approved', label: '我已审批', path: '/task/approved', permission: 'portal.workbench.approved' },
  { key: 'initiated', label: '我发起的', path: '/task/initiated', permission: 'portal.workbench.initiated' },
  { key: 'cc', label: '抄送我的', path: '/task/cc', permission: 'portal.workbench.cc' },
  { key: 'archive', label: '历史库', path: '/archive', permission: 'archive.search' },
] as const

/**
 * 管理后台分组（阶段 1 · 1.1）：组织架构与人员管理。
 * 口径：`admin.org.manage` / `admin.user.manage` / `admin.console`（见 `utils/admin.ts`），
 * 系统管理员兜底可见（PRD 6.10 REQ-ADMIN-006）。
 */
const adminNavItems = [
  { key: 'admin-console', label: '管理后台', path: '/admin', permission: 'admin.console' },
  { key: 'admin-orgs', label: '组织架构', path: '/admin/orgs', permission: 'admin.org.manage' },
  { key: 'admin-users', label: '人员管理', path: '/admin/users', permission: 'admin.user.manage' },
] as const

/**
 * 权限不可见优于不可用：无权限的入口不渲染（DESIGN.md Agent Usage Rules 第 6 条）。
 * 权限数据尚未取回时（阶段 1 骨架）不做隐藏，避免整栏空白。
 */
const visibleNavItems = computed(() => {
  const hasPermissionData = userStore.permissions.length > 0
  if (!hasPermissionData) return navItems
  return navItems.filter((item) => userStore.isSuperAdmin || userStore.hasPermission(item.permission))
})

/**
 * 管理入口的判定更严格：权限数据未取回时**一律不渲染**。
 * 审批中心入口空着只是体验问题，管理入口误闪会给无权限用户泄露后台结构。
 */
const visibleAdminNavItems = computed(() => {
  if (userStore.isSuperAdmin) return adminNavItems
  if (userStore.permissions.length === 0) return []
  return adminNavItems.filter((item) => userStore.hasPermission(item.permission))
})

const activeKey = computed(() => {
  if (route.path.startsWith('/archive')) return 'archive'
  if (route.path.startsWith('/admin/orgs')) return 'admin-orgs'
  if (route.path.startsWith('/admin/users')) return 'admin-users'
  if (route.path.startsWith('/admin')) return 'admin-console'
  const tab = route.meta.tab as string | undefined
  if (tab) return tab
  if (route.name === 'task-detail' || route.name === 'print-preview') return 'pending'
  return 'pending'
})

const breadcrumb = computed(() => {
  if (route.path.startsWith('/admin')) {
    const title = (route.meta.title as string | undefined) || '管理后台'
    // 总览页不再重复「管理后台 / 管理后台」
    return title === '管理后台' ? ['管理后台'] : ['管理后台', title]
  }
  const parts = ['审批中心']
  if (route.path.startsWith('/archive')) {
    parts.push('历史库')
  } else if (route.name === 'task-detail') {
    parts.push('待我审批', '单据详情')
  } else if (route.name === 'print-preview') {
    parts.push('单据详情', '打印预览')
  } else {
    parts.push(route.meta.title || '待我审批')
  }
  return parts
})

/** 待办数量：来自工作台汇总，供侧栏徽标使用 */
const pendingCount = ref(0)

watch(railed, (value) => {
  localStorage.setItem(SIDEBAR_KEY, value ? '1' : '0')
})

let mediaQuery: MediaQueryList | null = null
function handleMediaChange(event: MediaQueryListEvent): void {
  isMobile.value = event.matches
  if (!event.matches) overlayOpen.value = false
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 768px)')
  isMobile.value = mediaQuery.matches
  mediaQuery.addEventListener('change', handleMediaChange)

  void (async () => {
    await userStore.hydrate()
    await Promise.all([userStore.hydrateWatermark(), userStore.hydrateClientConfig()])
    try {
      const summary = await fetchWorkbenchSummary()
      pendingCount.value = summary.pending
    } catch {
      pendingCount.value = 0
    }
  })()
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', handleMediaChange)
})

/** 把手：H5 打开浮层抽屉，桌面切换 224px / 64px 图标条 */
function toggleRail(): void {
  if (isMobile.value) {
    overlayOpen.value = !overlayOpen.value
    return
  }
  railed.value = !railed.value
}

function goto(path: string): void {
  overlayOpen.value = false
  void router.push(path)
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('退出后需要重新登录，未提交的草稿仍会保留。', '确认退出登录', {
      confirmButtonText: '确认退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await userStore.logout()
  ElMessage({ type: 'success', message: '已退出登录' })
  void router.replace({ name: 'login' })
}

function toggleWatermark(enabled: boolean): void {
  userStore.toggleWatermark(enabled)
}
</script>

<template>
  <!-- 深色导航只在桌面出现；H5 用白色顶栏 + 白色内容区 -->
  <div class="oa-shell" :class="{ 'is-rail': railed }">
    <!-- ============ 侧边导航（展开 224px / 收起 64px） ============ -->
    <aside class="oa-sidebar" :class="{ 'is-overlay-open': overlayOpen }">
      <!-- 组织切换器（sidebar-org-switcher：48px，深色底） -->
      <button class="org-switcher" type="button" @click="goto('/task/pending')">
        <span class="org-mark" aria-hidden="true">{{ userStore.orgName.slice(0, 1) || 'OA' }}</span>
        <span class="org-text">
          <b>{{ userStore.orgName || '集团财务部' }}</b>
          <i>{{ userStore.user?.positionName || '审批人' }}</i>
        </span>
      </button>

      <!-- 导航分组：一期只预留「审批中心」 -->
      <nav class="oa-nav" aria-label="主导航">
        <p class="nav-caption">审批中心</p>
        <button
          v-for="item in visibleNavItems"
          :key="item.key"
          class="nav-item"
          :class="{ 'is-active': activeKey === item.key }"
          type="button"
          :data-tip="item.label"
          @click="goto(item.path)"
        >
          <span class="nav-glyph" aria-hidden="true">{{ item.label.slice(0, 1) }}</span>
          <span class="nav-label">{{ item.label }}</span>
          <span v-if="item.key === 'pending'" class="nav-badge">{{ pendingCount }}</span>
        </button>

        <p class="nav-placeholder">
          未来模块（合同管理、计划管理、经营决策、人力资源）按同一分组样式追加，不改抽屉机制。
        </p>

        <!-- 管理后台分组：仅管理员可见（权限不可见优于不可用） -->
        <template v-if="visibleAdminNavItems.length">
          <p class="nav-caption">管理后台</p>
          <button
            v-for="item in visibleAdminNavItems"
            :key="item.key"
            class="nav-item"
            :class="{ 'is-active': activeKey === item.key }"
            type="button"
            :data-tip="item.label"
            @click="goto(item.path)"
          >
            <span class="nav-glyph" aria-hidden="true">{{ item.label.slice(0, 1) }}</span>
            <span class="nav-label">{{ item.label }}</span>
          </button>
        </template>
      </nav>

      <!-- 用户区：头像 + 姓名 + 工号 + 退出 -->
      <div class="user-box">
        <span class="avatar" aria-hidden="true">{{ userStore.user?.avatarText || 'OA' }}</span>
        <span class="user-text">
          <b>{{ userStore.displayName || '未登录' }}</b>
          <i class="oa-tnum">工号 {{ userStore.employeeNo || '—' }}</i>
        </span>
        <button class="logout" type="button" title="退出登录" @click="handleLogout">退出</button>
      </div>
    </aside>

    <!-- 浮层抽屉遮罩（≤1024px） -->
    <div v-if="overlayOpen" class="oa-overlay" @click="overlayOpen = false" />

    <!-- ============ 主区 ============ -->
    <div class="oa-main">
      <!-- 顶栏 56px：面包屑 + 水印开关 + 用户菜单；不做全局搜索框 -->
      <header class="oa-topbar">
        <button class="rail-toggle" type="button" :aria-expanded="!railed" @click="toggleRail">
          {{ railed ? '»' : '«' }}
          <span class="oa-sr-only">切换导航展开/收起</span>
        </button>

        <nav class="oa-breadcrumb" aria-label="面包屑">
          <template v-for="(part, index) in breadcrumb" :key="part + index">
            <span :class="{ 'is-current': index === breadcrumb.length - 1 }">{{ part }}</span>
            <i v-if="index < breadcrumb.length - 1" aria-hidden="true">/</i>
          </template>
        </nav>

        <div class="topbar-tools">
          <label class="wm-switch" title="水印为防截屏泄露要求（REQ-USER-004），可按个人偏好关闭">
            <input
              type="checkbox"
              :checked="userStore.watermarkEnabled"
              @change="toggleWatermark(($event.target as HTMLInputElement).checked)"
            />
            <span>水印</span>
          </label>

          <span class="notify" title="消息通知（未读）">
            通知
            <b class="oa-tnum">3</b>
          </span>

          <span class="who">
            <span class="avatar sm" aria-hidden="true">{{ userStore.user?.avatarText || 'OA' }}</span>
            <span class="who-text">
              <b>{{ userStore.displayName || '未登录' }}</b>
              <i class="oa-tnum">{{ userStore.employeeNo || '—' }}</i>
            </span>
          </span>
        </div>
      </header>

      <!-- 内容区：外层含水印（H5 与桌面详情页都启用） -->
      <Watermark
        class="oa-content-wm"
        :name="userStore.user?.name"
        :employee-no="userStore.employeeNo"
        :enabled="userStore.watermarkEnabled"
        :opacity="userStore.watermarkOpacity"
        :rotate="userStore.watermarkRotate"
      >
        <main class="oa-content">
          <RouterView />
        </main>
      </Watermark>
    </div>
  </div>
</template>

<style scoped>
.oa-shell {
  display: grid;
  grid-template-columns: var(--oa-nav-w) minmax(0, 1fr);
  height: 100%;
  background: var(--oa-color-canvas);
  transition: grid-template-columns var(--oa-drawer-motion) var(--oa-drawer-easing);
}

.oa-shell.is-rail {
  grid-template-columns: var(--oa-nav-w-rail) minmax(0, 1fr);
}

@media (prefers-reduced-motion: reduce) {
  .oa-shell {
    transition: none;
  }
}

/* ---------------- 侧边导航 ---------------- */
.oa-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: var(--oa-color-inverse-canvas);
  color: var(--oa-color-inverse-ink-muted);
  border-right: 1px solid var(--oa-color-hairline); /* 用 1px 线分区，不用阴影 */
}

.org-switcher {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  flex: none;
  height: 48px;
  margin: var(--oa-space-xs);
  padding: 0 var(--oa-space-xs);
  border: 0;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-inverse-surface-1);
  color: var(--oa-color-inverse-ink);
  cursor: pointer;
  text-align: left;
}

.org-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
  font: var(--oa-font-caption);
}

.org-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.org-text b {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-inverse-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-text i {
  font: var(--oa-font-caption);
  font-style: normal;
  color: var(--oa-color-inverse-ink-muted);
}

.oa-nav {
  flex: 1 1 auto;
  min-height: 0;
  padding: var(--oa-space-xs);
  overflow-y: auto;
}

.nav-caption {
  padding: var(--oa-space-xs);
  font: var(--oa-font-caption);
  letter-spacing: 0.4px;
  color: var(--oa-color-inverse-ink-muted);
  opacity: 0.75;
  white-space: nowrap;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  width: 100%;
  height: 36px;
  padding: 0 var(--oa-space-sm);
  border: 0;
  border-radius: var(--oa-radius-xs);
  background: transparent;
  color: var(--oa-color-inverse-ink-muted);
  font: var(--oa-font-body-sm);
  cursor: pointer;
  white-space: nowrap;
  text-align: left;
}

.nav-item:hover {
  background: var(--oa-color-inverse-surface-2);
}

/* 选中态：inverse-surface-1 底 + 左侧 2px 企业蓝指示条 */
.nav-item.is-active {
  background: var(--oa-color-inverse-surface-1);
  color: var(--oa-color-inverse-ink);
}

.nav-item.is-active::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 9px;
  bottom: 9px;
  width: 2px;
  border-radius: 1px;
  background: var(--oa-color-primary);
}

.nav-glyph {
  flex: none;
  width: 20px;
  text-align: center;
  font: var(--oa-font-caption);
}

.nav-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 待办数量用徽标显示在标签右侧，不使用彩色圆点 */
.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-primary);
  color: var(--oa-color-on-primary);
  font: var(--oa-font-caption);
  font-variant-numeric: tabular-nums;
}

.nav-placeholder {
  margin-top: var(--oa-space-sm);
  padding: var(--oa-space-sm);
  border-top: 1px dashed var(--oa-color-inverse-surface-2);
  font: var(--oa-font-caption);
  line-height: 1.6;
  color: var(--oa-color-inverse-ink-muted);
  opacity: 0.55;
}

.user-box {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  margin-top: auto;
  padding: var(--oa-space-sm);
  border-top: 1px solid var(--oa-color-inverse-surface-1);
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: var(--oa-radius-full);
  background: var(--oa-color-inverse-surface-1);
  color: var(--oa-color-inverse-ink);
  font: var(--oa-font-caption);
}

.avatar.sm {
  background: var(--oa-color-surface-1);
  color: var(--oa-color-ink-muted);
}

.user-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-text b {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-inverse-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-text i {
  font: var(--oa-font-caption);
  font-style: normal;
  color: var(--oa-color-inverse-ink-muted);
}

.logout {
  margin-left: auto;
  padding: 0 var(--oa-space-xs);
  height: 28px;
  border: 0;
  border-radius: var(--oa-radius-xs);
  background: transparent;
  color: var(--oa-color-inverse-ink-muted);
  font: var(--oa-font-caption);
  cursor: pointer;
}

.logout:hover {
  background: var(--oa-color-inverse-surface-2);
  color: var(--oa-color-inverse-ink);
}

/* 收起为 64px 图标条：只留图标，悬停显示文字气泡 */
.oa-shell.is-rail .org-text,
.oa-shell.is-rail .nav-label,
.oa-shell.is-rail .nav-badge,
.oa-shell.is-rail .nav-caption,
.oa-shell.is-rail .nav-placeholder,
.oa-shell.is-rail .user-text,
.oa-shell.is-rail .logout {
  display: none;
}

.oa-shell.is-rail .nav-item {
  justify-content: center;
  padding: 0;
}

.oa-shell.is-rail .org-switcher,
.oa-shell.is-rail .user-box {
  justify-content: center;
}

.oa-shell.is-rail .nav-item:hover::after {
  content: attr(data-tip);
  position: absolute;
  left: calc(100% + 8px);
  top: 6px;
  z-index: 30;
  padding: 4px 8px;
  border-radius: var(--oa-radius-sm);
  background: var(--oa-color-ink);
  color: #fff;
  font: var(--oa-font-caption);
  white-space: nowrap;
}

/* ---------------- 主区 ---------------- */
.oa-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--oa-color-canvas);
}

.oa-topbar {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--oa-space-md);
  height: 56px;
  padding: 0 var(--oa-space-lg);
  border-bottom: 1px solid var(--oa-color-hairline);
  background: var(--oa-color-canvas);
}

.rail-toggle {
  flex: none;
  width: 28px;
  height: 28px;
  border: 1px solid var(--oa-color-hairline);
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-canvas);
  color: var(--oa-color-ink-muted);
  cursor: pointer;
  font-size: 12px;
}

.rail-toggle:hover {
  background: var(--oa-color-canvas-subtle);
  color: var(--oa-color-ink);
}

.oa-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--oa-space-xs);
  min-width: 0;
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink-subtle);
}

.oa-breadcrumb .is-current {
  color: var(--oa-color-ink);
}

.oa-breadcrumb i {
  font-style: normal;
  color: var(--oa-color-ink-disabled);
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: var(--oa-space-md);
  margin-left: auto;
  white-space: nowrap;
}

.wm-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
  cursor: pointer;
}

.notify {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font: var(--oa-font-caption);
  color: var(--oa-color-ink-muted);
}

.notify b {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: var(--oa-radius-xs);
  background: var(--oa-color-error-surface);
  color: var(--oa-color-error);
  font-weight: 500;
}

.who {
  display: inline-flex;
  align-items: center;
  gap: var(--oa-space-xs);
}

.who-text {
  display: flex;
  flex-direction: column;
}

.who-text b {
  font: var(--oa-font-body-sm);
  color: var(--oa-color-ink);
}

.who-text i {
  font: var(--oa-font-caption);
  font-style: normal;
  color: var(--oa-color-ink-subtle);
}

.oa-content-wm {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.oa-content {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: var(--oa-space-lg);
  background: var(--oa-color-canvas);
}

.oa-overlay {
  display: none;
}

/* ---------------- 断点：≤1024px 侧栏折叠为 64px；≤768px 用白色顶栏 ---------------- */
@media (max-width: 1280px) {
  .oa-shell {
    grid-template-columns: var(--oa-nav-w-rail) minmax(0, 1fr);
  }

  .oa-shell .org-text,
  .oa-shell .nav-label,
  .oa-shell .nav-badge,
  .oa-shell .nav-caption,
  .oa-shell .nav-placeholder,
  .oa-shell .user-text,
  .oa-shell .logout {
    display: none;
  }
}

@media (max-width: 1024px) {
  .oa-shell,
  .oa-shell.is-rail {
    grid-template-columns: var(--oa-nav-w-rail) minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  /* H5：不使用深色导航；导航退化为浮层，顶栏 48px */
  .oa-shell,
  .oa-shell.is-rail {
    grid-template-columns: minmax(0, 1fr);
  }

  .oa-sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 40;
    width: var(--oa-nav-w);
    transform: translateX(-100%);
    transition: transform var(--oa-drawer-motion) var(--oa-drawer-easing);
  }

  .oa-sidebar.is-overlay-open {
    transform: translateX(0);
  }

  .oa-sidebar .org-text,
  .oa-sidebar .user-text {
    display: flex;
  }

  .oa-sidebar .nav-label,
  .oa-sidebar .nav-caption,
  .oa-sidebar .logout {
    display: block;
  }

  .oa-sidebar .nav-badge {
    display: grid;
  }

  .oa-overlay {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 35;
    background: var(--oa-color-overlay-40);
  }

  .oa-topbar {
    height: var(--oa-h5-topbar-h);
    padding: 0 var(--oa-space-sm);
    gap: var(--oa-space-xs);
  }

  .oa-breadcrumb span:not(.is-current) {
    display: none;
  }

  .oa-breadcrumb i {
    display: none;
  }

  .who-text,
  .wm-switch span,
  .notify {
    display: none;
  }

  .oa-content {
    padding: var(--oa-space-sm);
  }
}
</style>
