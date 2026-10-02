# oa-web · 前端工程（Vue 3 + TypeScript + Vite）

集团OA审批系统的 Web/H5 前端。**设计令牌全部来自 [`../DESIGN.md`](../DESIGN.md)**，界面不得自创色值与间距。

## 快速开始

```bash
# Node ≥ 20.19；推荐 pnpm 11
pnpm install          # 内网离线：pnpm install --offline（需携带 store 或 node_modules）
pnpm dev              # 开发服务器（默认 4272，代理 /api → http://127.0.0.1:8080）
pnpm typecheck        # 类型检查（vue-tsc，两个 tsconfig 都跑）
pnpm build            # 产出 dist/（纯静态，交由 Nginx 托管）
pnpm preview          # 预览构建产物
```

> **依赖安装脚本**：`pnpm-workspace.yaml` 的 `allowBuilds` 显式放行 `esbuild` / `vue-demi` / `@parcel/watcher`——pnpm 10+ 默认不执行依赖的安装脚本，`esbuild` 不放行会导致 `vite build` 直接失败。内网离线交付同样依赖这份白名单。

## 目录

| 路径 | 说明 |
| --- | --- |
| `src/styles/tokens.scss` | **设计令牌**（CSS 变量 + SCSS 变量），逐项对应 `DESIGN.md` |
| `src/styles/element-overrides.scss` | 用令牌覆写 Element Plus 主题 |
| `src/styles/print-a4.scss` | A4 打印样式（`@page` 无边距、灰阶、**正式打印签名栏空栏**） |
| `src/api/http.ts` | axios 封装：统一响应解包、401 跳登录、traceId 透出 |
| `src/stores/user.ts` | 当前用户 / 角色 / 数据域 / 会话状态 |
| `src/router/index.ts` | 路由与登录守卫（登录、审批中心、详情、打印预览、后台占位） |
| `src/components/Watermark.vue` | 「姓名 + 工号」水印（5%–8% 透明度、-24°、`pointer-events:none`，不遮挡按钮与表单值） |
| `src/components/StateBanner.vue` | 三态提示：草稿可编辑 / 审批中只读 / 待补件仅附件与说明 |
| `src/utils/readwrite.ts` | 三态可写字段白名单（与后端 `FormWritePolicy` 同源口径，服务端仍为最终裁决） |
| `src/views/` | 登录、待办列表、单据详情、打印预览、后台占位 |

## 约定

- **三态读写**：详情页按状态置灰，但**服务端白名单才是权威**（前端置灰不代表可写）；印鉴单的 `return_status` / `return_date` 是唯一例外（仅发起人与节点⑦）。
- **无公网依赖**：不使用任何 CDN 与在线字体；图标走本地资源或 Element Plus 内置。
- **H5**：触控目标 ≥44px、底部操作栏 60px 常驻并适配安全区（`env(safe-area-inset-bottom)`）。
- **打印**：正式 A4 稿**签名栏一律空栏**，屏幕预览可显示缩略图与时间戳。

## 与后端的联调

默认开发代理见 `vite.config.ts`（`/api` → `http://127.0.0.1:8080`）。后端启动：`cd ../oa-server && mvn spring-boot:run`（需 MySQL 8 / Redis 7，或直接用 `../oa-deploy/docker-compose.yml` 起依赖）。
