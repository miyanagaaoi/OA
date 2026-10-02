import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * oa-web 构建配置（对应 doc/tech-design.md §3.1 / §4.1）
 * - 纯静态产物，不引用任何 CDN / 外部字体（REQ-NFR-001 私有化无公网）
 * - 开发期通过 Vite 代理把 /api 转发到本地后端，避免跨域与自签证书问题
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const proxyTarget = env.VITE_API_PROXY_TARGET

  return {
    plugins: [vue()],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    css: {
      preprocessorOptions: {
        scss: {
          // token 文件只允许在 styles/index.scss 里 @use 一次，组件内一律用 var(--oa-*)
          quietDeps: true,
        },
      },
    },

    server: {
      host: '127.0.0.1',
      port: 5273,
      strictPort: false,
      proxy: proxyTarget
        ? {
            '/api': {
              target: proxyTarget,
              changeOrigin: true,
              secure: false,
              // 开发期把 Origin 改写为后端自身来源：与生产（Nginx 同源反代）语义一致，
              // 避免浏览器带 Origin 时被后端 CORS 判为 Invalid CORS request（403）。
              // 注意：后端 application-dev.yml 的 oa.web.allowed-origins 也需包含本 dev server 端口；
              // 两处任一生效即可，双保险避免再出现「curl 200、浏览器 403」的误判。
              headers: { origin: proxyTarget },
            },
          }
        : undefined,
    },

    build: {
      target: 'es2020',
      outDir: 'dist',
      assetsDir: 'assets',
      cssCodeSplit: false,
      sourcemap: false,
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['vue', 'vue-router', 'pinia', 'axios'],
            element: ['element-plus'],
          },
        },
      },
    },

    preview: {
      port: 4273,
    },
  }
})
