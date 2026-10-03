---
uid: dbb9492b
id: oa.design.a11y.density
parent: oa.design.a11y
state: planned
name: {zh: "密度基准", en: "Density Baseline"}
description:
  zh: >
      密度基准：正文 14px 是默认值不是最小值（放大到 16px 会让一屏待办从 12 条降到 8 条）、表格行高 44px（紧凑 36px）、控件高 32px、卡片内边距 24px；层级靠字重（600/500/400）不靠字号跳跃，不再引入 700/800 粗黑标题。
      
  en: >
      The density baseline: 14px body is the default and not a minimum (going to 16px would cut a screen of pending items from twelve to eight), table rows are 44px with a 36px compact variant, controls are 32px tall and cards pad 24px; hierarchy comes from weights 600/500/400 rather than size jumps, and no 700 or 800 headings are introduced.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.326Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 695
    end_line: 695
  - path: "DESIGN.md"
    line: 707
    end_line: 709
  - path: "DESIGN.md"
    line: 919
    end_line: 919
apis:
  - protocol: file
    path: "styles/tokens/density.css"
    description:
      zh: >
          固定正文 14px 与控件 32px 基准的密度 CSS 变量。
          
      en: >
          Density CSS custom properties fixing the 14px body and 32px control baseline.
          
deps:
  - kind: reference
    to: oa.design.token.typography
    from_api: "file:styles/tokens/density.css"
    to_api: "file:styles/tokens/typography.css"
    label: {zh: "正文 14px 基准", en: "14px body baseline"}
---
