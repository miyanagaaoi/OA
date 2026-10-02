/// <reference types="vite/client" />

/**
 * 环境变量类型（对应 .env.development / .env.production）
 */
interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_ENV: 'development' | 'production' | string
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_PROXY_TARGET: string
  readonly VITE_SESSION_COOKIE_NAME: string
  readonly VITE_FORCE_HTTPS: string
  readonly VITE_USE_MOCK: string
  readonly VITE_WATERMARK_OPACITY: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
