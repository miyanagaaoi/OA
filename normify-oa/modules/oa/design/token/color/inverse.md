---
uid: 7f3cada6
id: oa.design.token.color.inverse
parent: oa.design.token.color
state: planned
name: {zh: "侧栏反向令牌", en: "Inverse (Sidebar) Tokens"}
description:
  zh: >
      反向令牌仅在左侧导航内使用：inverse-canvas #151a22、inverse-surface-1 #1e242e（选中项底）、inverse-surface-2 #28303c（悬停态）、inverse-ink #f4f6f9（14.6:1）、inverse-ink-muted #9aa4b2（6.4:1）；不得在内容区做深色卡片或深色表头，H5 也不使用反向表面。
      
  en: >
      Inverse tokens are used inside the left navigation only: inverse-canvas #151a22, inverse-surface-1 #1e242e for the selected item, inverse-surface-2 #28303c for hover and second-level expansion, inverse-ink #f4f6f9 at 14.6:1 and inverse-ink-muted #9aa4b2 at 6.4:1; dark cards or dark table headers in the content area are forbidden, and H5 never uses inverse surfaces.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.504Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 656
    end_line: 664
  - path: "DESIGN.md"
    line: 1084
    end_line: 1089
apis:
  - protocol: file
    path: "styles/tokens/color-inverse.css"
    description:
      zh: >
          侧栏专用的反向 CSS 变量（一层底 + 两层表面 + 两级文字）。
          
      en: >
          Sidebar-only inverse CSS custom properties (canvas, two surfaces, two ink levels).
          
deps:
  - kind: reference
    to: oa.design.a11y
    label: {zh: "侧栏反向底对比度", en: "Sidebar contrast checks"}
---
