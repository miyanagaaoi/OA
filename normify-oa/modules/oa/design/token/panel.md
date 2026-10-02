---
uid: "93637688"
id: oa.design.token.panel
parent: oa.design.token
state: planned
name: {zh: "面板布局令牌", en: "Panel Layout Tokens"}
description:
  zh: >
      审批中心布局令牌：抽屉展开 224px、收起图标条 64px、列表栏 380px（拖拽下限 320px）、两行列表项约 68px 且用 1px 分隔线而非卡片投影、详情头部 56px 与顶栏对齐、底部操作栏固定 60px；抽屉宽度过渡 160ms ease-out，prefers-reduced-motion 下取消动画。
      
  en: >
      Approval-centre layout tokens: nav-w 224px expanded, nav-w-rail 64px collapsed, panel-list 380px with a 320px drag lower bound, a two-line list item of about 68px separated by 1px lines rather than card shadows, a 56px detail header aligned with the top bar and a fixed 60px bottom action bar; the drawer transitions over 160ms ease-out and respects prefers-reduced-motion.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.671Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 764
    end_line: 776
  - path: "DESIGN.md"
    line: 747
    end_line: 752
apis:
  - protocol: file
    path: "styles/tokens/panel.css"
    description:
      zh: >
          审批中心面板布局的 CSS 变量（抽屉宽、图标条宽、列表栏宽、栏高）。
          
      en: >
          Approval-centre panel layout CSS custom properties (nav, rail, list and bar widths).
          
deps:
  - kind: reference
    to: oa.design.token.spacing
    from_api: "file:styles/tokens/panel.css"
    to_api: "file:styles/tokens/spacing.css"
    label: {zh: "面板宽度由间距基准推导", en: "Spacing-derived widths"}
---
