---
uid: 7082b1ee
id: oa.design.token.color.surface.ladder
parent: oa.design.token.color.surface
state: planned
name: {zh: "四级表面阶梯", en: "Surface Ladder"}
description:
  zh: >
      四级表面：canvas #ffffff（页面与卡片默认底，占 90% 面积）、canvas-subtle #f7f8fa（表头、只读字段块、审批意见块、上传区、行悬停）、surface-1 #f2f4f7（分组容器、禁用控件底、骨架屏、头像底）、surface-2 #e6e9ef（分隔较重的区块、开关关闭态轨道）；按顺序使用，不跳级。
      
  en: >
      The four surfaces: canvas #ffffff for pages and cards (about 90% of the area), canvas-subtle #f7f8fa for table headers, read-only blocks, opinion blocks, the upload area and row hover, surface-1 #f2f4f7 for grouping containers, disabled control fills, skeletons and avatar backgrounds, and surface-2 #e6e9ef for heavier separations and the off track of a switch; used in order, never skipped.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.624Z"
fingerprint: 575a8794373e5c9b787da64ecd4e108e7ac3726be946e9988d72588fa3b166fb
source:
  - path: "DESIGN.md"
    line: 642
    end_line: 645
  - path: "DESIGN.md"
    line: 1076
    end_line: 1081
apis:
  - protocol: file
    path: "styles/tokens/color-surface.css"
    description:
      zh: >
          四级表面阶梯的 CSS 变量。
          
      en: >
          Surface ladder CSS custom properties for the four elevation surfaces.
          
deps:
  - kind: reference
    to: oa.design.token.color.surface.hairline
    from_api: "file:styles/tokens/color-surface.css"
    to_api: "file:styles/tokens/color-hairline.css"
    label: {zh: "阶梯与细线配对使用", en: "Ladder & hairline pairing"}
---
