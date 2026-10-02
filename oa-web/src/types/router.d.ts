/**
 * oa-web · 路由 meta 类型扩展
 * ----------------------------------------------------------------------------
 * 独立成文件的原因：模块增强（declare module 'vue-router'）必须写在
 * **模块**里（文件顶层有 import/export）；若放在 `src/env.d.ts` 这类全局脚本中，
 * TypeScript 会把它当成"新声明一个同名模块"，从而遮蔽 vue-router 的真实导出。
 */
import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 公开页面，不需要登录 */
    public?: boolean
    /** 页面标题，写入 document.title 与面包屑 */
    title?: string
    /** 审批中心的工作台 Tab */
    tab?: 'pending' | 'approved' | 'initiated' | 'cc'
    /** 历史库模式（归档后只读，按单号检索） */
    archiveOnly?: boolean
    /** 需要的权限码，缺失则不渲染入口（权限不可见优于不可用） */
    requiredPermission?: string
  }
}
