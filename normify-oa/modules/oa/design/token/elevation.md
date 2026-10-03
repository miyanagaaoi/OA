---
uid: 8bbbe1da
id: oa.design.token.elevation
parent: oa.design.token
state: planned
name: {zh: "层级与投影令牌", en: "Elevation & Depth Tokens"}
description:
  zh: >
      六级层级：0 扁平无阴影、1 细线（1px 描边，默认层级）、2 表面抬升（canvas-subtle 底 + 1px 细线）、3 浮层（下拉菜单、日期面板、气泡）、4 模态（居中对话框 + 40% 遮罩）、5 抽屉（右侧划出），另有双层焦点环；能靠细线与表面阶梯解决的层级不加阴影，表格行、表单区块、分组容器永不带阴影。
      
  en: >
      Six elevation levels: flat 0 with no shadow, hairline 1 as the default 1px border, surface lift 2 as a canvas-subtle fill plus hairline, layer 3 for dropdowns and date panels, layer 4 for modals with a 40% overlay scrim, layer 5 for right drawers, plus the two-ring focus style; anything solvable with a hairline and the surface ladder must not get a shadow, and table rows, form sections and grouping containers never carry one.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.572Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 793
    end_line: 813
  - path: "DESIGN.md"
    line: 1120
    end_line: 1124
apis:
  - protocol: file
    path: "styles/tokens/elevation.css"
    description:
      zh: >
          层级 3–5 的投影与焦点环 CSS 变量。
          
      en: >
          Elevation and focus-ring CSS custom properties for layers 3 to 5.
          
---
