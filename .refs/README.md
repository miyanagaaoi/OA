# 参考素材：awesome-design-md（留档）

本目录是构建项目根目录 [`DESIGN.md`](../../DESIGN.md) 时使用的**外部参考素材留档**，不是项目运行依赖，也不参与构建。

## 来源

- 上游仓库：[VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md)
- 许可证：MIT（见 `awesome-design-md/LICENSE`）
- 抓取分支：`main`，抓取时间：2026-10-02
- 抓取方式：`node fetch-design-md.js`（通过 raw.githubusercontent.com 逐文件下载；本机环境无 git/curl）

## 内容

| 路径 | 说明 |
|------|------|
| `awesome-design-md/README.md` | 上游仓库说明（DESIGN.md 格式与目录） |
| `awesome-design-md/LICENSE` | 上游 MIT 许可证 |
| `awesome-design-md/design-md/ibm/DESIGN.md` | IBM Carbon：扁平方形、单一蓝、状态语义色 |
| `awesome-design-md/design-md/vercel/DESIGN.md` | Vercel：黑白精度、Geist 级技术感 |
| `awesome-design-md/design-md/linear.app/DESIGN.md` | Linear：表面阶梯、精确字重与负字距 |
| `awesome-design-md/design-md/clickhouse/DESIGN.md` | ClickHouse：高数据密度文档风格 |
| `awesome-design-md/design-md/stripe/DESIGN.md` | Stripe：金额等宽数字（`tnum`）与金融数据可读性 |
| `awesome-design-md/design-md/supabase/DESIGN.md` | Supabase：暗色令牌组织方式（备选参考） |
| `awesome-design-md/design-md/posthog/DESIGN.md` | PostHog：组件分节写法（备选参考） |
| `awesome-design-md/design-md/shopify/DESIGN.md` | Shopify：暗色表层阶梯（备选参考） |
| `fetch-design-md.js` | 下载脚本，可重跑以更新素材 |

## 使用边界

本项目的 `DESIGN.md` **只继承这些参考的结构性设计原则**（密度、层级、克制用色、数字排版），配色、字号、间距、组件定义均为独立制定，未使用任何品牌的商标、专有字体或视觉资产。素材仅作留档与设计评审依据。

## 校验工具

本机沙箱环境下 `npx @google/design.md lint` 会崩溃（STATUS_ACCESS_VIOLATION），因此改用等价的本地校验器：

```bash
node ../tools/validate-design-md.js ../DESIGN.md
```

校验内容：front matter 结构、令牌值合法性、令牌引用可解析性、章节顺序与重复、组件前景/背景 WCAG 对比度。
