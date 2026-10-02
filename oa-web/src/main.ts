/**
 * oa-web · 应用入口
 * ----------------------------------------------------------------------------
 * 来源：`doc/tech-design.md` §3.1（Vue 3 + TS + Vite + Element Plus）、
 *       §6（安全：全站 HTTPS、会话 Cookie）
 * 约束：
 *   · 纯静态产物，不引用 CDN / 外部字体（REQ-NFR-001）
 *   · 生产环境强制 HTTPS（REQ-NFR-005）
 *   · Element Plus 主题由 `src/styles/element-overrides.scss` 接入 DESIGN.md 令牌
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { useUserStore } from './stores/user'

// 全局样式（唯一 SCSS 入口：tokens → element-overrides → print-a4 → 基础与工具类）
import './styles/index.scss'

// ---------------------------------------------------------------------------
// 传输层守卫：生产环境必须 HTTPS（REQ-NFR-005 / 技术方案 §6）
// ---------------------------------------------------------------------------
function assertSecureTransport(): void {
  const forceHttps = import.meta.env.VITE_FORCE_HTTPS === 'true'
  if (!forceHttps) return
  if (location.protocol !== 'https:' && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
    document.body.innerHTML =
      '<div style="padding:48px;font:400 14px/1.6 sans-serif;color:#14181f">' +
      '<h2 style="margin:0 0 8px;font-size:20px">必须通过 HTTPS 访问</h2>' +
      '<p style="color:#4a5563">按安全基线要求，本系统全站启用 HTTPS。请使用 https:// 地址重新访问。</p>' +
      '</div>'
    throw new Error('OA 系统要求 HTTPS 访问')
  }
}

assertSecureTransport()

const app = createApp(App)

app.use(createPinia())

// 401 统一处理：清空画像并跳登录（带 redirect 回跳），避免 http ↔ router 循环依赖
setUnauthorizedHandler(() => {
  const userStore = useUserStore()
  userStore.reset()
  userStore.markSessionExpired()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
  // 控件默认尺寸跟随令牌（32px），H5 断点内由样式表抬到 44px
  size: 'default',
  zIndex: 2000,
})

app.mount('#app')
