---
# 版本注记：V0.4 配套修订（2026-10-02）——与 PRD V0.4 章节号、打印版式口径同步
version: alpha
name: 集团OA审批系统
description: "面向集团-公司-部门-科室四级组织的企业审批界面设计语言：白色画布 + 深墨文字 + 单一企业蓝（#1F5AE0）作为唯一强调色，左侧深色导航栏承载组织树，内容区以 1px 细线与层级表面区分信息，几乎不使用投影。高数据密度是默认值——审批列表、流程轨迹、金额与工号一律使用等宽数字并右对齐。状态色（通过/待审/超时/驳回）是系统中唯一的第二色系，且严格限定在状态徽标、流程节点与风险提示上。整体观感：克制、精确、可审计，长时间批量审批不疲劳。"

colors:
  primary: "#1f5ae0"
  primary-hover: "#1749bd"
  primary-active: "#12398f"
  on-primary: "#ffffff"
  primary-subtle: "#eef3fe"
  primary-border: "#c4d5f8"
  ink: "#14181f"
  ink-muted: "#4a5563"
  ink-subtle: "#5f6b7a"
  ink-disabled: "#aeb7c4"
  canvas: "#ffffff"
  canvas-subtle: "#f7f8fa"
  surface-1: "#f2f4f7"
  surface-2: "#e6e9ef"
  hairline: "#e2e5ea"
  hairline-strong: "#c8cdd6"
  inverse-canvas: "#151a22"
  inverse-surface-1: "#1e242e"
  inverse-surface-2: "#28303c"
  inverse-ink: "#f4f6f9"
  inverse-ink-muted: "#9aa4b2"
  semantic-success: "#1f7a4d"
  semantic-success-surface: "#e6f4ec"
  semantic-warning: "#9a6200"
  semantic-warning-surface: "#fdf1dd"
  semantic-error: "#c02b25"
  semantic-error-surface: "#fbeaea"
  semantic-info: "#1f5ae0"
  semantic-info-surface: "#eef3fe"
  semantic-neutral: "#5b6472"
  semantic-neutral-surface: "#eef0f4"
  overlay: "#000000"
  focus-ring: "#1f5ae0"

typography:
  display:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: -0.4px
  title-page:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: -0.2px
  title-section:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: 0
  body:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: 0
  body-sm:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: 0
  label:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0
  caption:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.45
    letterSpacing: 0.1px
  table-header:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0.2px
  amount:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: 0
    fontFeature: tnum
  amount-lg:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 32px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: -0.6px
    fontFeature: tnum
  mono:
    fontFamily: "'JetBrains Mono', ui-monospace, 'SF Mono', Consolas, monospace"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
    fontFeature: tnum
  button:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: 0

rounded:
  none: 0px
  xs: 2px
  sm: 4px
  md: 6px
  lg: 8px
  pill: 9999px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 64px
  control: 32px
  control-compact: 28px
  control-comfortable: 40px
  control-h5: 44px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 0px 16px
    height: 32px
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  button-primary-pressed:
    backgroundColor: "{colors.primary-active}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  button-primary-disabled:
    backgroundColor: "{colors.primary-border}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  button-primary-focused:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 0px 16px
    height: 32px
  button-secondary-hover:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  button-ghost:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 0px 8px
    height: 32px
  button-danger:
    backgroundColor: "{colors.semantic-error}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 0px 16px
    height: 32px
  button-danger-ghost:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.semantic-error}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    padding: 0px 12px
    height: 32px
  button-h5-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 44px
  button-h5-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 44px
  input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 0px 12px
    height: 32px
  input-hover:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    height: 32px
  input-focused:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    height: 32px
  input-error:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    height: 32px
  input-h5:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 0px 12px
    height: 44px
  textarea:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 8px 12px
  select:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 0px 12px
    height: 32px
  date-picker:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    height: 32px
  amount-input:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.amount}"
    rounded: "{rounded.sm}"
    padding: 0px 12px
    height: 32px
  cascader:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 8px 0px
    height: 240px
  checkbox:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xs}"
    size: 16px
  radio:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    rounded: "{rounded.full}"
    size: 16px
  switch:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    width: 36px
    height: 20px
  upload-dropzone:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: 24px
  card:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: 20px
  panel-header:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.title-section}"
    rounded: "{rounded.none}"
    padding: 0px 0px 12px 0px
  form-section:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: 20px 24px
  field-row:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 8px 0px
  table:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.sm}"
  table-header:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.table-header}"
    rounded: "{rounded.none}"
    height: 40px
  table-row:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    height: 44px
  table-row-hover:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    height: 44px
  table-row-selected:
    backgroundColor: "{colors.primary-subtle}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    height: 44px
  table-row-compact:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    height: 36px
  pagination:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.sm}"
    height: 32px
  tabs:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    height: 40px
  tab-active:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.none}"
    height: 40px
  tag-neutral:
    backgroundColor: "{colors.semantic-neutral-surface}"
    textColor: "{colors.semantic-neutral}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 2px 8px
  tag-info:
    backgroundColor: "{colors.semantic-info-surface}"
    textColor: "{colors.semantic-info}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 2px 8px
  tag-success:
    backgroundColor: "{colors.semantic-success-surface}"
    textColor: "{colors.semantic-success}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 2px 8px
  tag-warning:
    backgroundColor: "{colors.semantic-warning-surface}"
    textColor: "{colors.semantic-warning}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 2px 8px
  tag-error:
    backgroundColor: "{colors.semantic-error-surface}"
    textColor: "{colors.semantic-error}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 2px 8px
  status-pill-pending:
    backgroundColor: "{colors.semantic-warning-surface}"
    textColor: "{colors.semantic-warning}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: 2px 8px
  status-pill-approved:
    backgroundColor: "{colors.semantic-success-surface}"
    textColor: "{colors.semantic-success}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: 2px 8px
  status-pill-rejected:
    backgroundColor: "{colors.semantic-error-surface}"
    textColor: "{colors.semantic-error}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: 2px 8px
  status-pill-processing:
    backgroundColor: "{colors.semantic-info-surface}"
    textColor: "{colors.semantic-info}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: 2px 8px
  status-pill-closed:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.semantic-neutral}"
    typography: "{typography.caption}"
    rounded: "{rounded.sm}"
    padding: 2px 8px
  workflow-step-done:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.full}"
    size: 24px
  workflow-step-current:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.full}"
    size: 24px
  workflow-step-pending:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-disabled}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.full}"
    size: 24px
  workflow-step-timeout:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.semantic-error}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.full}"
    size: 24px
  workflow-step-parallel-group:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 2px 8px
  approval-opinion-block:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 12px
  approval-action-bar:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 12px 16px
  approval-action-approve:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  approval-action-reject:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.semantic-error}"
    typography: "{typography.button}"
    rounded: "{rounded.sm}"
    height: 32px
  signature-pad:
    backgroundColor: "{colors.canvas-subtle}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    width: 640px
    height: 200px
  signature-stamp:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 4px 8px
  avatar:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
    rounded: "{rounded.full}"
    size: 28px
  sidebar:
    backgroundColor: "{colors.inverse-canvas}"
    textColor: "{colors.inverse-ink-muted}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.none}"
    width: 224px
  sidebar-item-active:
    backgroundColor: "{colors.inverse-surface-1}"
    textColor: "{colors.inverse-ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.xs}"
    height: 36px
  sidebar-tree-node:
    backgroundColor: "{colors.inverse-canvas}"
    textColor: "{colors.inverse-ink-muted}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.xs}"
    height: 32px
  sidebar-org-switcher:
    backgroundColor: "{colors.inverse-surface-1}"
    textColor: "{colors.inverse-ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.xs}"
    padding: 0px 8px
    height: 48px
  topbar:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.none}"
    height: 56px
  breadcrumb:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-subtle}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.none}"
    height: 24px
  modal:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
  modal-backdrop:
    backgroundColor: "{colors.overlay}"
    rounded: "{rounded.none}"
  drawer:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    width: 480px
  notification-toast:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.sm}"
    padding: 12px 16px
  watermark:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.caption}"
  h5-bottom-action-bar:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 8px 12px
    height: 60px
  empty-state:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink-subtle}"
    typography: "{typography.body}"
    rounded: "{rounded.none}"
    padding: 48px 24px
---

## Overview

**克制的企业级审批界面。** 这套设计语言服务于集团 OA 审批系统：集团—公司—部门—科室四级组织、四类审批单（事项/资金/合同/印鉴证照）、流程引擎驱动的多节点流转（主干 **7 个审批节点**），用户在桌面端批量处理待办，也在手机浏览器（H5）上随手审批与手写签名。

> **版本注记：V0.4 配套修订（2026-10-02）。** 本次修订只对齐口径、不改令牌：① 附录 C 与 Known Gaps 的 PRD 章节号同步到 V0.4；② 打印稿**签名栏统一为"屏幕可显示已签缩略图、正式 A4 打印一律空栏"**；③ 打印分页统一为"常规一页、超长按行分页、页脚第 X / Y 页"；④ 打印版式来源补齐**事项审批单 → 子公司内部审批单版式（统一）**；⑤ 新增字段「其他会审部门」的打印呈现口径。`colors` / `typography` / `components` 等 YAML 令牌**未作任何改动**。

设计气质由三件事决定：

- **白色画布 + 深墨文字 + 唯一强调色。** 画布是纯白 `{colors.canvas}`，正文是近黑的 `{colors.ink}` (#14181f)。整个系统只有一个彩色强调：企业蓝 `{colors.primary}` (#1f5ae0)，且只在主按钮、链接、焦点环、选中态四处出现。它不做背景、不做装饰、不做卡片填充。
- **深色左栏承载组织。** 侧边栏使用 `{colors.inverse-canvas}` (#151a22) 深色导航，与白色内容区形成明确分区——这正是"左侧组织树 + 右侧业务内容"的经典企业后台结构。深色只属于导航，不侵入业务区。
- **高数据密度是默认值。** 表格行高 44px（紧凑模式 36px），控件高度 32px，正文 14px。审批是重复劳动，屏幕上多显示 5 行待办比多留 20px 白边更有价值。金额、工号、单号一律使用等宽数字（`fontFeature: tnum`）并右对齐，方便纵向扫读比对。

**状态色是唯一的第二色系。** 通过=绿 `{colors.semantic-success}`、待办/超时预警=琥珀 `{colors.semantic-warning}`、驳回/终止=红 `{colors.semantic-error}`、进行中/信息=蓝 `{colors.semantic-info}`、已关闭/失效=灰 `{colors.semantic-neutral}`。每个状态色都配套一个浅底 `*-surface` 令牌，徽标一律"浅底 + 同色深字"，不用饱和填充块——满屏彩色块会让待办列表失去焦点。

**深度靠线，不靠影。** 卡片与面板是"白底 + 1px `{colors.hairline}`"，层级靠表面阶梯（canvas → canvas-subtle → surface-1 → surface-2）表达。投影只留给真正浮起的元素：下拉菜单、模态框、抽屉。这与 Carbon 类企业设计系统的选择一致——扁平让数据表更清晰，也让长时间阅读更少视觉噪音。

**关键特征清单：**

- 单一强调色企业蓝，使用场景白名单严格限定为四项。
- 左侧深色导航（224px）+ 白色内容区，双区不互串颜色。
- 表格优先：任何列表默认按数据表设计，而不是卡片流。
- 等宽数字 + 右对齐金额，money-critical 信息的可读性优先。
- 状态语义色五套，浅底徽标，禁止饱和填充块。
- 圆角极小：控件 4px、卡片 6px、面板 8px，永不用 pill 圆角做按钮。
- 投影克制：默认 0 阴影，浮层才用双层柔和阴影。
- H5 与桌面共用同一套令牌，只改变密度与控制尺寸。

## Colors

> 令牌全部定义在 YAML front matter 的 `colors` 中；本节说明每个颜色的**职责边界**——什么时候可以出现，什么时候绝对不可以。

### Brand & Accent

**品牌标识（唯一的多色例外）**：集团 logo 使用**透明底版本 `logo-180.png`**（180×147，12.9 KB；原始 RGB 素材 `logo.png` 保留在根目录备查）。红色为品牌色，是界面上唯一一处非企业蓝的彩色元素。使用规则：

| 场景 | 规格 |
| --- | --- |
| 抽屉导航头部（展开态） | 28×28px，**直接置于深色底**（透明底，无需承托砖）；收起态同尺寸居于 64px 图标条中央 |
| 登录页左侧品牌区 | 44×44px |
| 打印稿页脚 | 高 8mm、宽自适应（纸张白底，透明底同样适用） |
| 禁止 | 改变 logo 颜色 / 拉伸变形 / 加投影 / 把 logo 红用于任何交互元素（按钮、链接、状态） |

> **关于白底**：原始素材为白底不透明位图，直接置于深色 `{inverse-canvas}` 上会出现生硬白块。**已弃用承托砖方案**——透明底版本在任何底色上都成立，且不需要额外装饰。若后续更换 logo 素材，须同样提供透明底版本。

- **Primary (`{colors.primary}` #1f5ae0)** — 企业蓝，系统中唯一的强调色。**使用场景白名单（仅此四项）**：① 主按钮背景；② 文本链接与可点击的操作文字；③ 输入框/按钮的焦点环；④ 当前选中项（选中行、当前 Tab、当前流程节点）。
- **Primary Hover (`{colors.primary-hover}` #1749bd)** — 主按钮悬停态，压暗而非提亮（保证白字对比度始终 ≥ 4.5:1）。
- **Primary Active (`{colors.primary-active}` #12398f)** — 按下态，进一步压暗。
- **Primary Subtle (`{colors.primary-subtle}` #eef3fe)** — 极浅蓝底。用于表格选中行、Tab 激活页签背景、信息类提示条背景。**不允许**用作卡片或区块背景。
- **Primary Border (`{colors.primary-border}` #c4d5f8)** — 浅蓝描边，用于禁用主按钮背景（"看起来像主按钮但不可点"）与选中项描边。
- **On Primary (`{colors.on-primary}` #ffffff)** — 主色之上的文字与图标，唯一用途。

> **禁止**：把企业蓝用作页面/卡片/区块背景（大面积蓝会让审批列表失去焦点）；引入第二个彩色强调（橙、紫、青）来做"品牌感"；用彩色渐变。

### Surface

四级表面阶梯，按此顺序表达层级，**不要跳级**：

- **Canvas (`{colors.canvas}` #ffffff)** — 页面与卡片默认底。整个系统 90% 的面积是它。
- **Canvas Subtle (`{colors.canvas-subtle}` #f7f8fa)** — 表头、只读字段块、审批意见块、上传区、行悬停态。
- **Surface 1 (`{colors.surface-1}` #f2f4f7)** — 分组容器、禁用控件底、骨架屏、头像底。
- **Surface 2 (`{colors.surface-2}` #e6e9ef)** — 分隔较重的区块、开关关闭态轨道。
- **Hairline (`{colors.hairline}` #e2e5ea)** — 1px 边框与分隔线，卡片、表格、输入框默认描边。
- **Hairline Strong (`{colors.hairline-strong}` #c8cdd6)** — 需要更明确的边界：输入框悬停、嵌套面板外框、表格分组分隔。

### Text

- **Ink (`{colors.ink}` #14181f)** — 标题、正文、数值、表格主要内容。对比度 16.8:1。
- **Ink Muted (`{colors.ink-muted}` #4a5563)** — 次要信息：表头文字、字段标签、辅助说明、未激活导航。对比度 7.8:1。
- **Ink Subtle (`{colors.ink-subtle}` #5f6b7a)** — 第三级：时间戳、序号、面包屑、空状态文案、图表次要标签。对比度 5.2:1（达 AA）。
- **Ink Disabled (`{colors.ink-disabled}` #aeb7c4)** — 禁用态文字、未到达的流程节点。**禁止用在需要阅读的内容上**（对比度约 2.0:1）；它只表示"当前不可用"。若某段文字虽然次要但仍需阅读，请用 `{colors.ink-subtle}`。

### Inverse（左侧导航专用）

- **Inverse Canvas (`{colors.inverse-canvas}` #151a22)** — 侧边栏底色。深而不纯黑，避免与投影/模态遮罩混淆。
- **Inverse Surface 1 (`{colors.inverse-surface-1}` #1e242e)** — 侧栏选中项背景、组织切换器底。
- **Inverse Surface 2 (`{colors.inverse-surface-2}` #28303c)** — 侧栏悬停态、二级展开背景。
- **Inverse Ink (`{colors.inverse-ink}` #f4f6f9)** — 侧栏激活文字。对比度 14.6:1。
- **Inverse Ink Muted (`{colors.inverse-ink-muted}` #9aa4b2)** — 侧栏未激活项文字，对比度 6.4:1。

> 反向令牌**只在侧边栏内使用**。不要在内容区做深色卡片、深色表头，也不要在 H5 上使用反向表面（H5 用白色顶栏 + 白色内容区）。

### Semantic（状态与风险）

状态色仅用于**状态徽标、流程节点、风险提示、校验信息**。禁止用于装饰性分类着色；若必须区分多类别，优先用文字 + `{colors.semantic-neutral}` 灰底标签。

- **Success (`{colors.semantic-success}` #1f7a4d / surface #e6f4ec)** — 审批通过、已办结、签署完成。
- **Warning (`{colors.semantic-warning}` #9a6200 / surface #fdf1dd)** — 待处理、待我审批、超时预警、即将到期。刻意选择偏暗的琥珀（#9a6200）而非明亮黄，以满足白底文字对比度 4.6:1。
- **Error (`{colors.semantic-error}` #c02b25 / surface #fbeaea)** — 驳回、拒绝、终止、校验失败、必填缺失。
- **Info (`{colors.semantic-info}` #1f5ae0 / surface #eef3fe)** — 进行中、已抄送、一般提示。与 primary 同值：系统里"蓝"只有一个含义。
- **Neutral (`{colors.semantic-neutral}` #5b6472 / surface #eef0f4)** — 已关闭、已撤回、已失效、草稿、无状态。

### Overlay & Focus

- **Overlay (`{colors.overlay}` #000000)** — 模态遮罩基准色，实际渲染使用 40% alpha。
- **Focus Ring (`{colors.focus-ring}` #1f5ae0)** — 焦点环。键盘 Tab 可见，鼠标点击不显示（`:focus-visible`）。**全系统不可移除焦点环**——审批是键盘密集型操作，且审计场景要求可操作性可追溯。

## Typography

### Font Family

- **UI 主字体** — `Inter`（拉丁与数字）+ `PingFang SC` / `Microsoft YaHei` / `Noto Sans SC`（中文回退）+ `system-ui`。中文界面下拉丁数字走 Inter、汉字走系统黑体，是中文企业系统最稳的组合：数字对齐好，汉字渲染无需额外字体加载。
- **等宽字体** — `JetBrains Mono` + `ui-monospace / SF Mono / Consolas` 回退。用于单号（如 `OA-2026-000123`）、流程实例 ID、工号、IP、日志时间戳、签名哈希。

### Hierarchy

| Token | 字号 | 字重 | 行高 | 字距 | 用途 |
|---|---|---|---|---|---|
| `{typography.display}` | 28px | 600 | 1.25 | -0.4px | 门户首页欢迎语、统计大标题 |
| `{typography.title-page}` | 20px | 600 | 1.3 | -0.2px | 页面标题（"待我审批"） |
| `{typography.title-section}` | 16px | 600 | 1.4 | 0 | 卡片/面板标题（"基本信息"） |
| `{typography.body}` | 14px | 400 | 1.6 | 0 | 正文与表单值（默认字号） |
| `{typography.body-sm}` | 13px | 400 | 1.55 | 0 | 表格单元格、密集列表 |
| `{typography.label}` | 13px | 500 | 1.4 | 0 | 字段标签、Tab、按钮文案以外的控件标签 |
| `{typography.caption}` | 12px | 400 | 1.45 | 0.1px | 时间戳、序号、徽标、水印文字 |
| `{typography.table-header}` | 13px | 500 | 1.4 | 0.2px | 表头（微正字距，区分于数据行） |
| `{typography.amount}` | 14px | 500 | 1.4 | 0 | 表格中的金额、数量（`tnum`） |
| `{typography.amount-lg}` | 32px | 600 | 1.2 | -0.6px | 资金审批单金额主视觉（`tnum`） |
| `{typography.mono}` | 13px | 400 | 1.5 | 0 | 单号、ID、日志、哈希（`tnum`） |
| `{typography.button}` | 14px | 500 | 1.2 | 0 | 所有按钮文字 |

### Principles

- **正文 14px 是默认值，不是最小值。** 不要为了"呼吸感"把表单与表格放大到 16px——那会让一屏待办从 12 条降到 8 条。
- **数字一律等宽 + 右对齐。** 金额、数量、工号、单号使用 `tnum`。左对齐金额会让 1,200.00 与 98,000.00 无法纵向比对。
- **层级靠字重，不靠字号跳跃。** 标题 600、标签 500、正文 400。三级字重足够，不再引入 700/800 的粗黑标题。
- **中文不使用负字距。** 仅拉丁展示级字号（28px）用 -0.4px；中文标题字距为 0，否则汉字会挤压变形。
- **等宽字体只出现在标识符场景。** 不要用等宽字体排版正文或金额（金额用 `amount`，它带 `tnum` 但是比例字体，长金额更好读）。
- **不用斜体表达强调。** 中文斜体是伪斜体，观感差；强调用 500/600 字重或 `{colors.primary}` 文字色。

### Note on Font Substitutes

参考企业后台的字体现成方案：Inter 是 Geist / SF Pro / Söhne 类"技术感无衬线"最接近的免费替代；中文若需要统一观感可全站使用 `Noto Sans SC`（自托管子集）；等宽可用 `JetBrains Mono` 或 `Geist Mono` 替代 SF Mono。生产环境建议自托管字体子集并 `font-display: swap`，避免内网环境请求外网字体失败导致回退错乱。

## Layout

### Spacing System

- **基准单位 4px。** 所有间距、尺寸、行高都是 4 的倍数。
- **令牌**：`{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.md}` 16px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 64px。
- **控件尺寸**：`{spacing.control}` 32px（桌面默认）· `{spacing.control-compact}` 28px（表格内联操作、穿梭框）· `{spacing.control-comfortable}` 40px（流程设计器、低频配置页）· `{spacing.control-h5}` 44px（H5 触控，**不低于 44px**）。
- **常用组合**：卡片内边距 `{spacing.lg}` 24px；表单区块内边距 20px 24px；字段行间距 `{spacing.xs}` 8px；表格单元格水平内边距 `{spacing.sm}` 12px；页面内容区外边距 `{spacing.lg}` 24px。

### Grid & Container

**主导航采用「抽屉式可抽缩」侧栏**（参照飞书 PC 端的抽屉导航）。三种状态：

| 状态 | 宽度 | 触发 | 说明 |
| --- | --- | --- | --- |
| **展开** | 224px | 默认（≥1280px） | 显示分组标题 + 图标 + 文字 |
| **收起（图标条）** | **64px** | 点击抽屉把手 / `Ctrl+\` | 仅保留图标，鼠标悬停显示文字气泡；**状态持久化到本地**，下次进入沿用 |
| **浮层抽屉** | 224px 覆盖在内容之上 | ≤1024px | 收起时不占位，点击把手滑出；带遮罩，按 `Esc` 或点遮罩关闭 |

**主导航只预留「审批中心」一个模块入口**。将来新增的业务模块（合同管理、计划管理、经营决策、人力资源等）**按同一分组样式追加**，不改变抽屉机制。当前分组结构：

```
[组织切换器]
审批中心
  待我审批 / 我已审批 / 我发起的 / 抄送我的
（未来模块占位，一期不出现）
[用户区：头像 + 姓名 + 工号]
```

**审批中心内容区采用"列表 + 详情"双栏并置**——这是本系统**最主要的导航形态**：

| 区域 | 宽度 | 说明 |
| --- | --- | --- |
| 主导航（抽屉） | 224px / **64px（收起）** | 见上表 |
| **审批列表栏** | 固定 **380px**（可拖拽 320–480px） | 标签页 + 搜索 + 卡片列表 |
| **详情面板** | 自适应，**最小 480px** | 单号栏 + 表单区 + 审批记录 + 底部操作栏 |
| 详情面板内部 | 12 栅格 | 主表单 **8 列**、审批记录 **4 列**（≥1440px）；窄屏时记录移至表单下方 |

- **审批列表**（待我审批 / 我已审批 / 我发起的 / 抄送我的）以**卡片列表项**呈现，一屏约 8–10 条；点击后在右侧**就地加载详情**，不跳页。
- **列表项结构**（两行）：首行 =「类型图标 + 标题 + 状态徽标」；次行 =「单号 · 发起人 · 部门」左对齐、**金额右对齐等宽**。
- **选中态**：`{colors.primary-subtle}` 底 + **左侧 2px `{colors.primary}` 指示条**（与侧栏选中态同一语法）。
- **详情面板头部**：单据标题 + 状态徽标 + 单号，右侧放次要操作（打印 / 流转 / 更多）；**主按钮只在底部操作栏出现一次**。
- **列表与详情各自独立滚动**，顶部栏与列表栏的标签页固定不动。
- **业务表格仍用表格**：审计日志、超时统计、流程模板列表等**数据比对型**页面继续用 `table`（44px 行高、金额右对齐）——"列表+详情"只用于**审批单据的浏览与处理**。
- **表单栅格**：桌面 2 列（字段标签 96px 右对齐 + 控件自适应），H5 单列（标签在上，控件在下）。

### Panel 布局令牌（审批中心专用）

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| 抽屉展开宽 | 224px | `--nav-w` |
| **抽屉收起宽** | **64px** | `--nav-w-rail` |
| 列表栏宽 | 380px | `--panel-list` |
| 列表栏最小宽 | 320px | 拖拽下限 |
| 列表项高度 | 两行 ≈ 68px | 标题行 + 元信息行 |
| 列表项间距 | 0（用 1px 分隔线分栏，不用卡片投影） |
| 详情头部高 | 56px | 与顶栏同高，形成横向对齐 |
| 底部操作栏高 | 60px | 固定，操作按钮右对齐 |
| 抽屉动效 | `160ms ease-out` | 宽度过渡；`prefers-reduced-motion` 下取消动画 |

### Whitespace Philosophy

留白用于**分组**，不用于**撑高**。区块之间 24px，区块内部 12px，字段之间 8px——层级差距本身就是留白语法。审批中心顶部到第一个列表项只需 12px：审批系统不是营销页，首屏尽可能多地呈现待办。

### Responsive Strategy

| 名称 | 宽度 | 关键变化 |
|---|---|---|
| Desktop-XL | ≥1680px | 侧栏 224px 常驻；列表栏 380px；详情面板内 表单 8 列 + 轨迹 4 列 |
| Desktop | 1440px | 默认布局（列表 + 详情并置） |
| Laptop | 1280px | 列表栏收窄至 320px；详情面板轨迹移至表单下方（单列） |
| Tablet | 1024px | 侧栏折叠为 64px 图标条；**列表与详情切换为「列表 → 详情」两级**（不再并置），详情页带返回 |
| Mobile-Lg | 768px | 单列流式；顶部栏 48px |
| Mobile | ≤480px | H5 布局：底部操作栏 60px 常驻，控件 44px |

## Elevation & Depth

| 层级 | 处理 | 用途 |
|---|---|---|
| 0（扁平） | 无阴影、无边框 | 页面背景、标题、正文、表格行 |
| 1（细线） | 1px `{colors.hairline}` 边框 | 卡片、面板、输入框、表格外框 — **默认层级** |
| 2（表面抬升） | `{colors.canvas-subtle}` 底 + 1px `{colors.hairline}` | 表头、只读字段块、审批意见块 |
| 3（浮层） | `0 4px 12px rgba(20, 24, 31, 0.10), 0 1px 3px rgba(20, 24, 31, 0.06)` | 下拉菜单、日期面板、气泡提示 |
| 4（模态） | `0 12px 32px rgba(20, 24, 31, 0.16)` + 遮罩 `{colors.overlay}` 40% | 模态框、居中对话框 |
| 5（抽屉） | `-8px 0 24px rgba(20, 24, 31, 0.12)` | 右侧抽屉（流程配置、日志详情） |
| 焦点 | `0 0 0 2px {colors.canvas}, 0 0 0 4px {colors.focus-ring}` | 键盘焦点环（`:focus-visible`） |

**规则**：能靠 1px 细线和表面阶梯解决的层级，就不要加阴影。表格行、表单区块、分组容器**永不带阴影**。

### Decorative Depth

- 侧边栏与内容区之间用 1px `{colors.hairline}` 分隔，**不用阴影**——阴影会让 224px 的导航看起来像浮层。
- 模态遮罩固定 40% 黑，点击遮罩关闭（有未保存内容时二次确认）。
- **不使用**渐变、光斑、玻璃拟态（backdrop-blur）、彩色投影。审批界面不做视觉特效。
- H5 底部操作栏加 1px 上边框而非阴影，避免滚动时出现闪烁带。

## Shapes

### Border Radius Scale

| Token | 值 | 用途 |
|---|---|---|
| `{rounded.none}` | 0px | 面板、表格、侧栏、分隔线 |
| `{rounded.xs}` | 2px | 徽标、标签、复选框、小型容器 |
| `{rounded.sm}` | 4px | **按钮、输入框、下拉框（默认控件圆角）** |
| `{rounded.md}` | 6px | 卡片、模态框、上传区、抽屉内容块 |
| `{rounded.lg}` | 8px | 大面板、流程设计器画布容器 |
| `{rounded.pill}` | 9999px | 开关轨道、胶囊标签（仅此两类） |
| `{rounded.full}` | 9999px | 头像、流程节点圆点 |

**规则**：按钮圆角恒为 4px，**永不使用 pill 圆角按钮**——胶囊按钮属于消费级产品语言，会削弱审批系统的正式感。徽标用 2px 直角感，不要圆角胶囊（除非是状态筛选 chip）。

### Photography & Illustration Geometry

- **不使用装饰性照片与插画。** 这是内部审批工具，视觉资产只有三类：① 用户头像（`{rounded.full}`，28px 列表 / 40px 详情）；② 手写签名图片（白底、无圆角、等比缩放，最大高度 64px）；③ 空状态图标（单色线性图标，`{colors.ink-disabled}`，64px，下方配一行说明 + 一个主按钮）。
- 图标统一使用 **16px / 20px 线性图标**（stroke 1.5px），跟随文字色；操作列图标按钮尺寸 28px，命中区域 ≥32px。

## Components

### Buttons

- **`button-primary`** — 主操作，每个界面**同时只出现一个**（提交、同意、保存）。背景 `{colors.primary}`，白字，`{rounded.sm}` 4px，高 32px，水平内边距 16px。悬停压暗至 `{colors.primary-hover}`，按下至 `{colors.primary-active}`；禁用态为 `{colors.primary-border}` 底 + `{colors.ink-muted}` 文字（禁用态不受 AA 约束，但仍需清晰可辨"这是按钮、只是不能点"）。
- **`button-secondary`** — 次要操作（取消、返回、导出）。白底 + 1px `{colors.hairline-strong}` 边框 + `{colors.ink}` 文字，悬停底色转 `{colors.canvas-subtle}`。
- **`button-ghost`** — 表格行内操作（查看、编辑、催办）。无边框无底色，文字用 `{colors.primary}`，内边距 8px。行内操作**最多 3 个**，超出收进"更多"下拉。
- **`button-danger`** — 破坏性主操作（驳回并终止、删除草稿），背景 `{colors.semantic-error}`。**驳回**场景优先使用 `button-danger-ghost`（白底红字），避免用户误触红色实心按钮。
- **`button-h5-primary` / `button-h5-secondary`** — H5 底部操作栏专用，高 44px，主按钮占剩余宽度，次按钮固定 96px。

### Inputs & Forms

- **`input`** — 白底，1px `{colors.hairline}` 边框，`{rounded.sm}`，高 32px，内边距 12px，文字 `{typography.body}`。悬停边框转 `{colors.hairline-strong}`；聚焦边框转 `{colors.primary}` **并保留 2px 焦点环**；错误态边框 `{colors.semantic-error}`，下方 12px 红字说明。
- **`textarea`** — 审批意见主输入控件，最小高度 88px，`padding: 8px 12px`，右下角显示字数（上限 500，超出禁用提交）。
- **`amount-input`** — 资金/合同金额必填项。`{typography.amount}` + `tnum`，**右对齐**，输入时显示千分位，左侧固定"¥"前缀，失焦时规范为两位小数。金额为 0 或空时禁止提交（对应 PRD 6.2 资金审批单「金额必填」与 13.2）。
- **`select` / `date-picker`** — 高 32px，与 `input` 同尺寸同圆角；下拉面板使用层级 3 浮层阴影，选中项用 `{colors.primary-subtle}` 底 + `{colors.primary}` 文字。
- **`cascader`** — 组织架构选择器（集团—公司—部门—科室四级）。四级联级面板，每级 240px 宽，支持搜索定位。**数据权限边界内才展示节点**：无权限的组织节点不可见（而非禁用），避免通过选择器探测组织架构。
- **`checkbox` / `radio` / `switch`** — 选中态使用 `{colors.primary}`；`switch` 轨道 36×20px，关闭态 `{colors.surface-2}`。
- **`upload-dropzone`** — 附件上传。虚线框（1px dashed `{colors.hairline-strong}`），`{colors.canvas-subtle}` 底，`{rounded.md}`，内边距 24px。已上传文件以列表行展示（图标 + 文件名 + 大小 + 删除），**不做图片大图预览格子**。单文件超限时提示明确上限数值。
- **`field-row`** — 表单字段行：标签 96px 右对齐 `{colors.ink-muted}`（`{typography.label}`），控件自适应；必填项标签前加红色星号 `{colors.semantic-error}`；只读字段值使用 `{colors.canvas-subtle}` 底块呈现，不加边框。

### 单据发起与表单填写（参考集团现行表单习惯）

集团现行的电子表单（参照 `doc/参考文档/` 的「发起审批页面」「合同审批表单填写页面」）与本系统既有规范有四处差异，**本规范采纳其版式习惯**：

| 项目 | 规范 |
| --- | --- |
| **字段排列** | 表单填写页采用**单列、标签在上**（不再用 `field-row` 的"标签右对齐 + 两列"）；字段行间距 `{spacing.md}` 16px，控件**占满表单宽度**。理由：单列长表单逐项填写不易错行，且与集团现行纸质/电子单据的填写习惯一致 |
| **表单宽度** | 内容列**最大 760px 居中**；≥1440px 时右侧出现「本页导航」吸附目录（140px，`{colors.ink-muted}`，当前分区用 `{colors.primary}`） |
| **分区标题带** | 每个字段分组用**整行浅底带**：`{colors.canvas-subtle}` 底、高 32px、`{typography.label}`、左内边距 12px、`{rounded.xs}`；分组标题带**可折叠**（默认展开） |
| **底部操作** | 表单底部固定操作区：**主按钮「提交」+ 次按钮「取消」**，左对齐；提交前滚动到第一个校验失败字段 |
| **草稿入口** | 顶部提供「保存草稿」与「草稿箱（N）」；**检测到未提交的上次编辑时**，顶部出现提示条「有上次编辑记录 / 继续编辑 / 删除」（`{colors.semantic-info-surface}` 底） |
| **上传控件** | 使用**块状上传按钮 + 容量说明**（"上限 30 个文件，最大 500MB/个"），而非大面积虚线拖拽区；已上传文件仍以列表行展示 |

> **不采纳的部分**：参考件的模板图标使用多色块（蓝/粉/绿/橙/青）。本系统**单一强调色**，图标块统一为 `{colors.primary-subtle}` 底 + `{colors.primary}` 图标；**确需区分类型时用图标形状而非颜色**。

提交后行为与既有约束一致：字段全部只读，修改须由审批人驳回后重提。

### Data Display

- **`table`** — **系统中的默认列表形态**。`{colors.canvas}` 底，1px 外框，行高 44px（`table-row-compact` 36px 用于审计日志与明细）。表头 `{colors.canvas-subtle}` 底、`{colors.ink-muted}` 文字、`{typography.table-header}`、高 40px、**滚动时粘性固定**。行悬停 `{colors.canvas-subtle}`，选中 `{colors.primary-subtle}`。首列固定（单号或类型），操作列右固定。
- **`table` 的金额列** — 必须使用 `{typography.amount}`（`tnum`）右对齐；金额 ≥ 100 万时同时显示"万元"换算（如 `1,250,000.00 ¥ / 125.00 万`）以降低误读风险。
- **`status-pill-*`** — 单据状态徽标，`{rounded.sm}` + 浅底同色深字 + 12px 文字。状态与颜色**一一映射且全局唯一**：草稿=neutral、审批中=info、待我审批=warning、已通过=success、已驳回=error、已撤回/终止=neutral。**不要为不同单据类型发明新配色**。
- **`tag-*`** — 分类标签（事项类别、合同类型、用印类型）。默认 `tag-neutral`；仅当标签本身表达风险时使用彩色（如"经济类-强制财务"用 info）。
- **`workflow-step-*`** — 审批轨迹节点圆点：已完成=`workflow-step-done`（白底 + 1px `{colors.primary-border}` + `{colors.ink}` 文字，内含勾选图标）、进行中=`workflow-step-current`（企业蓝实心 + 白字）、未到达=`workflow-step-pending`（白底灰字灰边）、超时=`workflow-step-timeout`（白底红边红字）。节点间连接线 2px，已完成段 `{colors.primary}`，未完成段 `{colors.hairline}`。
- **`workflow-step-parallel-group`** — 并行协同审批分组（对应 PRD **6.4** REQ-FLOW-005 并行协同）：多个协同部门折叠为一个分组框，显示"协同审批 · N 个部门"并展开全部子节点；**分组内全部完成才推进**，分组标题右侧显示进度（3/4）。
- **`approval-opinion-block`** — 审批意见块：`{colors.canvas-subtle}` 底、`{rounded.sm}`、内边距 12px，结构为「意见正文 → 签名图（若有）→ 审批人 + 岗位 + 时间戳」。签名图与时间戳不可编辑、不可删除（对应 PRD **6.5** 电子签名与 **6.9** 审计日志 REQ-LOG-002/003）。
- **`pagination`** — 32px 高，`{colors.ink-muted}`，当前页 `{colors.primary}` 文字 + `{colors.primary-subtle}` 底；同时提供每页条数切换（10/20/50）与总条数。
- **`tabs` / `tab-active`** — 高 40px，下划线指示器 2px `{colors.primary}`；用于「待我审批 / 我已审批 / 我发起的 / 抄送我的」。**待办数量用徽标显示在标签右侧**，不使用彩色圆点。
- **`empty-state`** — 64px 单色图标 + `{typography.body}` 说明 + 一个主按钮（如"发起审批"），垂直居中，上下留白 48px。

### Navigation

- **`sidebar`** — 左侧导航，宽 224px，`{colors.inverse-canvas}` 底。顶部为**组织切换器**（`sidebar-org-switcher`，48px，显示当前公司/组织 + 下拉切换），中部为功能菜单，底部为用户区（头像 + 姓名 + 工号 + 退出）。折叠状态宽 64px，只留图标 + 悬停文字提示。
- **`sidebar-item-active`** — 选中项 `{colors.inverse-surface-1}` 底 + `{colors.inverse-ink}` 文字 + 左侧 2px `{colors.primary}` 指示条。悬停态 `{colors.inverse-surface-2}`。
- **`sidebar-tree-node`** — 组织树节点（四级），缩进 12px/级，高 32px，展开箭头 16px。**组织树只出现在侧栏或选择器内，不做独立页面**。
- **`topbar`** — 高 56px，白底，底部 1px `{colors.hairline}`。左为面包屑，右为「消息通知（未读数）」+「用户菜单」。**不做全局搜索框**（审批系统按单据类型与状态检索，检索器在列表页内）。
- **`breadcrumb`** — `{typography.body-sm}` + `{colors.ink-subtle}`，最后一级用 `{colors.ink}`，分隔符用 `/`。层级示例：`审批中心 / 待我审批 / 资金审批单`。
- **`h5-bottom-action-bar`** — H5 底部操作栏，高 60px，白底 + 上边框 1px。**同意**为主按钮（占满剩余宽度），**拒绝**为次按钮（96px），"更多"（转办/加签）为文本按钮。安全区适配 `env(safe-area-inset-bottom)`。

### Feedback & Overlays

- **`modal`** — 居中对话框，白底，`{rounded.md}` 6px，层级 4 阴影，宽 560px（确认类 400px，表单类 720px）。结构：标题 20px/600 → 内容 → 右下角操作（取消 + 确认，确认在右）。**确认按钮文案要具体**（"确认驳回"而非"确定"）。
- **`drawer`** — 右侧抽屉，宽 480px（日志详情 640px），层级 5 阴影，用于不打断上下文的详情查看。
- **`notification-toast`** — 右上角浮层，宽 360px，白底 + 层级 3 阴影 + 左侧 3px 状态色竖条。成功 3 秒自动消失；失败**不自动消失**并附"重试"按钮。Toast 只用于结果反馈，**不用 Toast 承载需要在流程中阅读的信息**（如节点流转结果请用站内信）。
- **`watermark`** — H5 与详情页水印层：`{typography.caption}` + `{colors.ink}` 5%–8% 透明度，旋转 -24°，内容为「姓名 + 工号」，间距 240×160px，`pointer-events: none`，`user-select: none`。水印**不遮挡按钮与表单值**（对应 PRD **6.8** REQ-USER-004 防截屏泄露要求）。
- **`signature-pad`** — 手写签名面板：640×200px（H5 自适应宽度、高 200px），`{colors.canvas-subtle}` 底 + 1px 虚线边框 + `{rounded.sm}`。上方提示"请在框内签名"，下方按钮为「清除」「使用预存签名」「确认签名」。线条为 2px `{colors.ink}` 圆头平滑曲线；未签名时"确认"禁用。
- **`signature-stamp`** — 已签名回显：白底无边框、等比缩放、最大高度 64px，下方 12px 灰字标注「签名人 + 时间戳」。签名图**不可删除**，仅可重新签署（保留历史版本）。

### Agent Usage Rules（组件使用硬约束）

1. **一屏一主按钮。** 任何页面/模态/抽屉，`button-primary` 只允许出现一次。
2. **列表先问"能不能用表格"。** 能表格就表格；只有 H5 与卡片式门户例外。
3. **状态只从 `status-pill-*` 五个令牌里选。** 需要新状态时，先判断它属于五种语义中的哪一种。
4. **金额永远 `tnum` + 右对齐 + 两位小数。**
5. **危险操作二次确认，且确认按钮文案包含动作与对象**（"确认驳回《XX合同审批单》"）。
6. **权限不可见优于不可用。** 无数据权限的单据与组织节点不渲染（而非置灰），避免信息泄露与猜测。

## Do's and Don'ts

### Do

- 把 `{colors.primary}` 企业蓝限制在白名单四场景：主按钮、链接、焦点环、选中态。
- 用 1px `{colors.hairline}` 细线和表面阶梯建立层级；能用线解决的就不加阴影。
- 表格行高 44px、控件 32px、正文 14px —— 保持这一密度基准。
- 金额、工号、单号使用 `tnum` 等宽数字；金额右对齐并显示两位小数。
- 状态徽标统一使用「浅底 + 同色深字」的 `status-pill-*` 令牌。
- 审批轨迹节点使用"已完成 / 进行中 / 未到达 / 超时"四态，线色随进度变化。
- H5 触控目标 ≥44px，底部操作栏适配安全区。
- 保留键盘焦点环，用 `:focus-visible` 控制显示时机。

### Don't

- 不要把企业蓝用作页面、卡片或区块背景。
- 不要引入第二个彩色强调（橙/紫/青/绿）来做视觉区分。
- 不要对按钮使用 pill 圆角，也不要给表格行、表单区块加阴影。
- 不要用 `{colors.ink-disabled}` 承载需要阅读的信息。
- 不要用彩色渐变、玻璃拟态、光斑、彩色投影。
- 不要用饱和填充块做状态徽标（满屏色块会淹没待办优先级）。
- 不要在 H5 上使用深色导航；H5 用白色顶栏 + 白色内容区 + 白色底部操作栏。
- 不要为不同单据类型发明不同的状态配色体系。
- 不要把表格改成卡片流来追求"现代感"——审批的核心是纵向比对。
- 不要把 Toast 当作流程结果通知渠道。

## Iteration Guide

1. **一次只改一个组件**，并在评审时用 `components:` 里的令牌名指代它（例如"`table-row-hover` 的底色偏重"）。
2. **新增区块前先决定它的表面层级**（canvas / canvas-subtle / surface-1），层级决定之后再谈间距。
3. **新增颜色之前先检查语义色五套能否覆盖**；能覆盖就不加新颜色。
4. 改动令牌后运行 `npx @google/design.md lint DESIGN.md` 校验结构、令牌引用与 WCAG 对比度。
5. 新变体（悬停/按下/禁用/错误）**作为独立组件条目新增**，不要塞进同一个条目的说明文字里。
6. 界面评审时对照三张清单：状态色是否越界、主按钮是否唯一、金额是否右对齐等宽。
7. 每个页面上线前确认：H5 与桌面端均使用同一套令牌（只改密度），没有为移动端临时造色值。
8. 交付给编码智能体时的标准提示词：**"阅读 `DESIGN.md`，按其中令牌与组件规则实现《XXX》页面，不要引入 `colors` 与 `typography` 之外的色值与字号。"**

## Known Gaps

- **暗色模式未纳入本期。** 令牌中含 `inverse-*` 与 `semantic-*` 已为暗色留好语义位，但业务区的暗色映射（表格斑马纹、水印透明度、签名图白底处理）尚未定义，需要单独一轮视觉设计。
- **流程设计器（拖拽画布）的专用视觉未完全定义。** 本文件覆盖了画布容器的圆角与表面层级，但连线样式、节点手柄、吸附提示、缩放控件的视觉细节需要在实现阶段补充（对应 PRD **6.4** 流程引擎 · REQ-FLOW-008 流程设计器）。
- **数据可视化色板未定义。** PRD **6.10 管理后台**的报表（REQ-ADMIN-005：流程量、平均耗时、超时率、审批人效率、驳回率）需要 6–8 色分类色板，本文件刻意未提供——避免与状态色体系冲突，建议作为独立 `CHART-TOKENS.md` 输出。
- **邮件与站内信模板样式未定义**（PRD **6.7** 消息通知）：邮件客户端兼容性（Outlook 表格布局）与站内信富文本样式需要单独规范。
- ~~**表单模板的具体字段字典未定义。**~~ **✅ 已关闭（V0.4）**：四类审批单（事项/资金/合同/印鉴证照）的字段级类型、长度、必填、校验、联动、三态读写与打印标签，已由 [`doc/forms.md`](doc/forms.md) 承接；本文件只约束其视觉与交互规则，两者分工不变。字典类型总表见 `doc/enums.md`、种子数据见 `doc/dict-seed.md`（本次配套新增）。
- ~~**打印稿的字段缺口待确认。**~~ **✅ 已关闭（V0.4）**：实单中系统原先没有的 3 项已全部落地——**计划类别**（`plan_category`，**布尔 checkbox**：勾选＝计划内，**不入 `dict_type`**）、**付款归属**（`payment_belong`，**布尔 checkbox**：勾选＝本月度，**不入 `dict_type`**）、**事项分类新增「投资」类**（Q8/Q9/Q10 均已关闭，见 PRD 附录 D 与 `doc/forms.md` 第 9 节）。三项**只存不用**，且**事项类别不再参与路由**。本次修订同时关闭了另外两处打印缺口：**「其他会审部门」的字典来源与打印呈现**（见「打印规格」）、**跨页分页与附页口径**（见「纸张与版心」）。
- **字体授权未核实。** Inter 为 SIL OFL、JetBrains Mono 为 OFL、Noto Sans SC 为 OFL，可商用自托管；`PingFang SC` 与 `Microsoft YaHei` 为系统字体（随操作系统授权），跨平台回退顺序需在实现阶段验证。
- **图标库未指定。** 建议 16/20px 单色线性图标集（如 Lucide / Remix Icon），需在实现阶段统一 stroke 宽度为 1.5px。
- **对比度基线以白底计算。** 若后续启用暗色模式，`{colors.primary}` 与状态色都需要重新配对暗底变体（企业蓝在深底上对比度不足）。

## 打印规格（审批单 A4）

审批单打印稿是本系统唯一需要**严格贴合纸质单据**的界面，因此独立成节。**完整实现见 [`DESIGN.print-a4.html`](DESIGN.print-a4.html)**（A4 实际尺寸，含 4 张单据样张）。

### 版式来源

**四类单据 × 两级办理层 → 版式映射（V0.4 拍板口径）**：

| 单据 | 集团层办理 | 子公司层办理 | 版式依据 | 对应参考件 |
| --- | --- | --- | --- | --- |
| **事项审批单** | **子公司内部审批单版式** | **子公司内部审批单版式** | **业务拍板：统一使用「子公司内部审批单」版式，不论办理层级** | `doc/参考文档/内部审批单.png` |
| 合同审批单 | 集团合同类文件流转审批单 | 子公司内部审批单 | 严格对齐实单结构（PRD 6.2 打印要求） | `doc/参考文档/集团合同审批流转单.png` / `内部审批单.png` |
| 资金审批单 | 资金审批单 | 子公司内部审批单 | 严格对齐实单结构（PRD 6.2 打印要求） | `doc/参考文档/集团资金审批单.png` / `内部审批单.png` |
| 印鉴证照审批单 | 印鉴证照使用审批单 | 印鉴证照使用审批单 | 实单无对应件，沿用集团单版式推导 | — |

> **两级办理层的判定**：以**发起人所属公司**为准——发起人属集团本部即为"集团层办理"，属子公司即为"子公司层办理"。
>
> **事项审批单是唯一不按层级切换版式的单据**：它的字段量小（标题、事项分类、描述、是否涉及费用、期望日期、抄送），用「竖向字段 + 审批记录流水」的子公司内部审批单版式在任何层级都更省纸、更贴近日常用单；**集团层办理的事项单不再套用「集团合同类文件流转审批单」版式**。
>
> 四张样张见 [`DESIGN.print-a4.html`](DESIGN.print-a4.html)：P1 集团合同类文件流转审批单、P2 资金审批单、P3 子公司内部审批单（**同时作为事项审批单的模板**）、P4 印鉴证照使用审批单。
>
> 本表与 [`doc/templates.md`](doc/templates.md) §5.3「四类单据打印版式映射」**逐行一致**（含样张编号 P1–P4）；两处如有差异，以本节为准并同步 `templates.md`。

### 纸张与版心

| 项目 | 规格 |
| --- | --- |
| 纸张 | **A4 纵向 210mm × 297mm** |
| 页边距 | `@page { size: A4; margin: 0 }`，由内容区 `padding: 12mm 12mm 10mm` 控制版心 |
| 版心宽度 | 186mm |
| 单页容量 | 正文 9.5pt、行高 1.42 时，实测可容纳约 55 行（含多行意见区） |
| 分页 | **常规数据量下每张单据恰好一页**；当明细行或流转段落**超出 A4 可容纳行数（约 55 行）**时**按行分页**并保留表头（`thead` 重复），页脚标注「**第 X / Y 页**」（X 为当前页、Y 为总页数）。**不在页内压缩字号、不删行**；「公文接收及处理」的每一段**不得被跨页截断**，整段移至下一页 |
| 页脚 | 左边系统与单据名、中间单号 + 模板版本 + 生成时间、右边页码 |

### 打印稿专用规则（与业务界面的差异）

业务界面用「细线 + 表面阶梯 + 克制用色」；打印稿必须更朴素，规则不同且**不可互相套用**：

| 项目 | 业务界面 | 打印稿 |
| --- | --- | --- |
| 圆角 | 控件 4px / 卡片 6px | **全部 0px**（直角） |
| 表格线 | 仅外框 + 表头分隔线 | **全表 1pt 实线**（`.35mm`），外框可加粗至 `.6mm` |
| 投影 | 浮层才用 | **完全禁止** |
| 渐变 | 禁止 | 禁止 |
| 主色 | `{colors.primary}` 用于主按钮等四处 | **不使用任何主题色**，全部黑色 |
| 状态色 | 状态徽标使用语义色 | 状态以**文字**呈现（已通过 / 已撤回），不使用色块 |
| 字号 | 12–16px | 9.5pt 正文 / 8.5pt 附注 / 16pt 抬头，**不随屏幕缩放** |
| 数字与日期 | `tnum` 等宽 | 等宽字体（Consolas / Courier New），便于归档核对 |
| 签名 | 签名图 + 时间戳 + 设备指纹（屏幕） | **屏幕预览**可显示已签缩略图 + 时间戳文字；**正式 A4 打印稿的签名栏一律为空栏**，供手签（与集团现行实单一致）。空栏格式 `签名：____ 年 月 日`，**不打印任何签名图、缩略图、姓名或时间戳** |
| 颜色依赖 | 状态色承担语义 | **黑白复印后仍须可读**——不得依赖颜色传递任何信息 |

### 结构要件（集团单共有）

1. **单据抬头**：居中、加粗、字号 16pt、字距 2px，下方可带副标题。
2. **三栏表头行**：`提报单位 | 责任部门 | 报送时间` 等，标签列居中等宽。
3. **「公文接收及处理」重复块**（合同单核心特征，**单页版式容量为最多 3 段**）：每段为「接收单位 / 接收人 / 签收时间」（内嵌表格分三格，仅竖线）+「处理意见」多行区 + 落款与日期右对齐。**每段对应一次集团层流转**（`flow_routing`）。**段数与流转次数的关系**：3 段是**版式容量**，而引擎允许**流转 + 回退合计 ≤5 次**；若实际流转达 4–5 次，第 4、5 段**以附页承载**——正文排满 3 段后另起「附页：接收及处理（续）」，抬头加「（续）」、页脚顺延（如「第 2 / 3 页」），**任何一次流转记录都不得省略或覆盖**。分段时**整段不可被跨页截断**。
4. **多轮签名栏**：资金单为「集团职能部门 / 集团分管领导 / 集团董事长」三段，每段含「签名：____ 年 月 日」。**这些签名栏在正式 A4 打印稿上一律为空栏**（供手签）；屏幕预览可在对应栏位显示已签缩略图与时间戳，两者**不得混用**。
5. **合并栏**：合同单的「集团领导意见」把分管领导与董事长意见**放在同一单元格**，中间留 5–6mm 间距。
6. **收尾行**：合同单为「公文回传」+「印鉴证照管理部门」；资金单为「系统关联」（关联单号 + 审批链），便于归档核对。
7. **复选框**：使用文字符号 `☑ / ☐`（`{typography.body}` 字号），不使用 `input[type=checkbox]` —— 保证打印与复印观感一致。
8. **写值区**：可填字段单元格给 3–4% 极浅灰底（`#fafafa`），屏幕上可辨、打印几乎不可见；**标签列不填色**，与实单一致。
9. **无框手写体**：示意已签署内容时用楷体（Kaiti），并在末尾以 8.5pt 楷体附签名与日期——**仅用于样张示意与屏幕预览**；**正式 A4 打印稿的签名栏一律为空栏**，不输出任何示意性签名与日期。

### 子公司内部审批单的结构（与集团单不同）

| 部分 | 结构 |
| --- | --- |
| 顶部信息条 | 左「申请编号」、右「打印人 / 打印时间」（两行，右对齐） |
| 抬头 | 单据全称（如「XXX 合同申请」），居中加粗 |
| 竖向字段表 | `标签列 | 值列 | 标签列 | 值列` 四列，一行一字段（申请人 / 申请时间 / 所属部门 / 审批状态） |
| 分组标题行 | 整行合并居中：**审批详情 / 合同有效期 / 我方信息 / 对方信息 / 审批记录** |
| 审批记录流水 | 每行三段：`阶段`（发起/审批/抄送/上传备案，居中）· `处理人 / 动作 / 时间` · 意见与附件（独占整行宽） |
| 撤回与重审 | **各自独立一行**（如「降泽宇 / 已撤回」「降泽宇 / 已通过」），不做合并——保留真实轨迹 |

> **事项审批单复用此版式（V0.4 拍板，不论集团层或子公司层）**：字段区按 `doc/forms.md` 第 2 节渲染（事项标题 / 事项分类 / 事项描述 / 是否涉及费用 / 涉及金额 / 费用承担主体 / 期望完成日期 / 抄送人），分组标题简化为「**审批详情 / 审批记录**」；不使用集团单的「公文接收及处理」重复块与多轮签名栏。

### 页眉页脚与呈现

- **打印时自动隐藏屏幕工具条**（`.bar`），无需手动操作。
- **打印时自动隐藏屏幕签名缩略图与时间戳**：签名栏一律输出空栏（见「打印稿专用规则」的「签名」行）。
- 页脚固定三栏：左「系统名 · 单据名」、中「单号 · 模板版本 · 生成时间」、右「**第 X / Y 页**」；三栏**每页重复**。
- 屏幕预览按 1:1 毫米尺寸渲染（`width: 210mm`），与打印结果一致；**屏幕预览与打印稿的唯一差异**是前者可显示已签缩略图与时间戳，后者一律空栏。

### 业务确认后的版式口径（V0.4 追加，2026-10-02）

| 项目 | 口径 |
| --- | --- |
| **「其他会审部门」一行** | 纸质实单上它与「事项分类」语义相近但**允许多选**（实单同时勾选了经济与行政）。系统侧映射为字段 `other_review_depts`（合同审批单，**multiselect**，≤10 项），**字典来源为后台数据字典的 `review_dept_other` 类型**（REQ-ADMIN-004；种子数据见 `doc/dict-seed.md` §8.2，字段定义见 `doc/forms.md` 6.9）。**打印稿呈现**：标签沿用实单写法「其他会审部门」，值以**逗号分隔的多行文本**（`经发部、财务部、集团办`）排版，一格内自动换行、不压字距、不使用标签云或徽标；**若无值则打印为空行**；**若不保留该字段，整行隐藏**（`templates.md` §5.2 口径：从 `form_schema_json.fields[]` 删除该字段项，而非仅设 `printVisible = false`） |
| **集团归口部门统一后的标签** | 归口统一为财务部，但**打印稿保留实单标签**（资金单的「集团职能部门」、印鉴单的「集团职能部门」），不改为「财务部」——纸质单据的栏位名称不宜随系统术语变化 |
| **事项审批单的版式** | **统一使用「子公司内部审批单」版式**（不论集团层还是子公司层办理），见「版式来源」；集团层办理的事项单**不再套用「集团合同类文件流转审批单」版式** |
| **打印签名栏** | **正式 A4 打印稿一律空栏**（供手签，与集团现行实单一致）；**屏幕预览**才可显示已签缩略图与时间戳。`signature-stamp` 属屏幕组件，**不进入打印样式表** |
| **计划类别 / 付款归属** | 两者是**布尔 checkbox**（勾选＝计划内 / 勾选＝本月度），打印为 `☑ / ☐` 勾选框，**不打印 true/false**；它们**不是 `dict_type` 字典项** |

### 实测页面高度（限 297mm）

| 单据 | 实测高度 | 结果 |
| --- | --- | --- |
| P1 集团合同类文件流转审批单 | 274mm | ✓ 剩余约 23mm |
| P2 资金审批单 | 242mm | ✓ |
| P3 子公司内部审批单（13 条记录） | 270mm | ✓ 记录条数接近上限 |
| P4 印鉴证照使用审批单 | 205mm | ✓ |

> **注意**：P1 与 P3 余量较小。若后续新增字段或审批记录条数增多，需同步复查——**P3（子公司内部审批单，也是事项审批单的版式）**的记录流水超过约 15 条将超出单页，届时应**按行分页**并重复表头，页脚按「第 X / Y 页」顺延；**不得**为塞进一页而压缩字号或省略记录。合同单的「公文接收及处理」同理：超过 3 段请走**附页**（见「结构要件」第 3 条）。

### 实现约束

1. 打印样式使用 `mm` / `pt` 单位，**不使用 `px`** 定义纸张与边距。
2. **打印稿不复用业务界面的组件类**（`.btn` / `.card` / `.pill` 等），避免状态色与圆角渗入打印稿。
3. 单据内容由数据驱动：字段顺序与标签取自表单模板，印章/签名取自 `flow_signature`，流水取自 `sys_thread` + `flow_routing`。
4. 黑白复印后信息不得丢失：任何仅靠颜色区分的信息都必须补文字。
5. 打印稿与业务界面共享**数据**，不共享**样式**——这是刻意的分离。

## 附录 A：令牌 → CSS 变量映射（实现参考）

```css
:root {
  /* Color · Brand */
  --oa-color-primary: #1f5ae0;
  --oa-color-primary-hover: #1749bd;
  --oa-color-primary-active: #12398f;
  --oa-color-on-primary: #ffffff;
  --oa-color-primary-subtle: #eef3fe;
  --oa-color-primary-border: #c4d5f8;

  /* Color · Ink */
  --oa-color-ink: #14181f;
  --oa-color-ink-muted: #4a5563;
  --oa-color-ink-subtle: #5f6b7a;
  --oa-color-ink-disabled: #aeb7c4;

  /* Color · Surface */
  --oa-color-canvas: #ffffff;
  --oa-color-canvas-subtle: #f7f8fa;
  --oa-color-surface-1: #f2f4f7;
  --oa-color-surface-2: #e6e9ef;
  --oa-color-hairline: #e2e5ea;
  --oa-color-hairline-strong: #c8cdd6;

  /* Color · Inverse (sidebar only) */
  --oa-color-inverse-canvas: #151a22;
  --oa-color-inverse-surface-1: #1e242e;
  --oa-color-inverse-surface-2: #28303c;
  --oa-color-inverse-ink: #f4f6f9;
  --oa-color-inverse-ink-muted: #9aa4b2;

  /* Color · Semantic */
  --oa-color-success: #1f7a4d;
  --oa-color-success-surface: #e6f4ec;
  --oa-color-warning: #9a6200;
  --oa-color-warning-surface: #fdf1dd;
  --oa-color-error: #c02b25;
  --oa-color-error-surface: #fbeaea;
  --oa-color-info: #1f5ae0;
  --oa-color-info-surface: #eef3fe;
  --oa-color-neutral: #5b6472;
  --oa-color-neutral-surface: #eef0f4;

  /* Space */
  --oa-space-xxs: 4px;
  --oa-space-xs: 8px;
  --oa-space-sm: 12px;
  --oa-space-md: 16px;
  --oa-space-lg: 24px;
  --oa-space-xl: 32px;
  --oa-space-control: 32px;
  --oa-space-control-h5: 44px;

  /* Radius */
  --oa-radius-xs: 2px;
  --oa-radius-sm: 4px;
  --oa-radius-md: 6px;
  --oa-radius-lg: 8px;
  --oa-radius-full: 9999px;

  /* Elevation */
  --oa-shadow-layer-3: 0 4px 12px rgba(20, 24, 31, 0.1), 0 1px 3px rgba(20, 24, 31, 0.06);
  --oa-shadow-layer-4: 0 12px 32px rgba(20, 24, 31, 0.16);
  --oa-shadow-drawer: -8px 0 24px rgba(20, 24, 31, 0.12);
  --oa-focus-ring: 0 0 0 2px #ffffff, 0 0 0 4px #1f5ae0;
}
```

## 附录 B：状态与颜色映射表（全局唯一，实现时不得扩展）

| 业务状态 | 归属单据阶段 | 令牌 | 底色 | 文字色 |
|---|---|---|---|---|
| 草稿 | 流程实例·草稿 | `status-pill-closed` | `{colors.surface-1}` | `{colors.semantic-neutral}` |
| 待我审批 | 任务·待处理 | `status-pill-pending` | `{colors.semantic-warning-surface}` | `{colors.semantic-warning}` |
| 审批中（他人在审） | 流程实例·审批中 | `status-pill-processing` | `{colors.semantic-info-surface}` | `{colors.semantic-info}` |
| 已通过 / 已办结 | 流程实例·通过 | `status-pill-approved` | `{colors.semantic-success-surface}` | `{colors.semantic-success}` |
| 已驳回 / 已终止 | 流程实例·拒绝/终止 | `status-pill-rejected` | `{colors.semantic-error-surface}` | `{colors.semantic-error}` |
| 已撤回 / 已关闭 | 流程实例·撤回 | `status-pill-closed` | `{colors.surface-1}` | `{colors.semantic-neutral}` |
| 已转办 / 已加签 | 任务·已转办/已加签 | `tag-info` | `{colors.semantic-info-surface}` | `{colors.semantic-info}` |
| 超时预警 | 任务·即将超时 | `status-pill-pending` + `workflow-step-timeout` | `{colors.semantic-warning-surface}` | `{colors.semantic-warning}` |

## 附录 C：与 PRD 的对应关系（V0.4 章节号）

> 本节按 **PRD V0.4** 的章节号核对。第 6 章结构：6.1 一期路由原则 / 6.2 四类审批单与事项类别 / 6.3 主干审批链（+ 6.3.1 集团层流转）/ 6.4 流程引擎 / 6.5 电子签名 / 6.6 异常路径 / 6.7 消息通知 / 6.8 H5 与登录保持 / 6.9 审计日志 / 6.10 管理后台。**旧稿的 6.1 单据、6.2 流程引擎、6.3 签名、6.4 H5、6.5 消息、6.6 审计、6.7 后台编号一律作废。**

| PRD 章节（V0.4） | 本规范的承接方式 |
|---|---|
| 6.2 四类审批单与事项类别 | `form-section` / `field-row` / `amount-input` / `tag-*`：四类单据共用同一表单视觉骨架；**事项类别是五类配置项且不参与路由**，视觉上仅作标签，不做分支提示 |
| 6.3 主干审批链（7 个审批节点） | `workflow-step-*`：节点四态渲染，编号 ①–⑦，发起者与结束**不计入节点编号** |
| 6.4 流程引擎（加签/跳转/并行/版本） | `workflow-step-*` + `workflow-step-parallel-group`：节点四态 + 并行分组进度 |
| 6.5 电子签名 | `signature-pad` + `signature-stamp` + `approval-opinion-block`：签名不可删除、绑定时间戳；**`signature-stamp` 仅在屏幕与预览出现，正式 A4 打印一律空栏** |
| 6.6 异常路径（驳回/回退/补件） | 状态徽标 `status-pill-rejected` + 补件态提示；待补件期主字段只读（**印鉴单的归还状态/归还日期为唯一例外**） |
| 6.7 消息通知 | `notification-toast`（结果反馈）+ 站内信列表沿用 `table` 密度规范 |
| 6.8 移动端 H5 与登录保持 | `button-h5-*` / `input-h5` / `h5-bottom-action-bar` / `watermark`：44px 触控 + 安全区 + 姓名工号水印 |
| 6.9 审计日志 | `table-row-compact` 36px + `{typography.mono}` 时间戳与 IP，只读、无操作列删除入口 |
| 6.10 管理后台 | `sidebar-org-switcher` / `cascader` / `sidebar-tree-node`：四级组织与权限树的可视化载体；数据字典（REQ-ADMIN-004）承载事项类别 `matter_category`、合同类型、用印类型、**证照类型** `cert_type`、**其他会审部门** `review_dept_other`（字典类型命名以 `doc/enums.md` §14 与 `doc/dict-seed.md` 为准） |
| 5.2 / 5.3 权限模型与数据域 | 「Agent Usage Rules」第 6 条：无权限数据不渲染（不可见 > 不可用） |
| 第 9 章 非功能需求（浏览器兼容） | 令牌全部为标准 CSS 值，无实验性属性；字体走系统回退，不依赖外网字体 |
| 第 10 章 验收标准（含 10.3 设计侧验收） | 设计侧验收补充：主按钮唯一、状态色不越界、金额右对齐等宽、H5 触控 ≥44px；打印侧 AC-31 / AC-32 见「打印规格」 |
| 第 13 章 设计规范与 UI 约定 | 本文件即该章的落地实现（13.1 视觉基调 / 13.2 审批业务强相关约定） |
| 配套文档 | `doc/forms.md`（字段字典，已承接本文件 Known Gaps 的字段缺口）、`doc/enums.md`、`doc/dict-seed.md`、`doc/templates.md`、`doc/test-cases.md`（本次配套新增） |

## 附录 D：参考来源

本文件遵循 [Stitch DESIGN.md 规范](https://stitch.withgoogle.com/docs/design-md/specification/)，设计语言在企业级后台方向上参考了以下公开设计系统分析（原始素材见 `.refs/awesome-design-md/`，来源仓库 [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md)，MIT License）：

| 参考 | 借鉴内容 |
|---|---|
| IBM Carbon（`design-md/ibm`） | 扁平方形美学、白底 + 单一蓝强调、细线卡片、状态语义色、Enterprise gravitas |
| Vercel（`design-md/vercel`） | 黑白精度、密集表格与开发者工具的克制排版、Geist 级技术感 |
| Linear（`design-md/linear.app`） | 表面阶梯承载层级、字重 500/600 + 负字距的精确感、状态徽标克制用法 |
| ClickHouse（`design-md/clickhouse`） | 高数据密度文档风格、表头与徽标的紧凑处理 |
| Stripe（`design-md/stripe`） | 金额等宽数字（`tnum`）处理、金融数据的可读性优先原则 |

> 说明：以上均为对**公开设计语言的分析**，本项目仅继承其中的结构性原则（密度、层级、克制用色），未使用任何品牌的商标、专有字体或视觉资产；配色与令牌为本项目独立定义。
