---
uid: a25fc1e1
id: oa.design.component.data-display.table
parent: oa.design.component.data-display
name: {zh: "表格", en: "Table"}
description:
  zh: >
      表格：canvas 底 + 1px 外框，行高 44px（审计日志与明细用 36px 紧凑行），表头 canvas-subtle 底、40px 高、滚动时粘性固定，行悬停 canvas-subtle、选中 primary-subtle，首列（单号/类型）与操作列右固定；金额列 tnum 右对齐并保留两位小数。
      
  en: >
      The table: canvas fill with a 1px outer border, 44px rows (36px compact rows for audit logs and details), a canvas-subtle header 40px tall that sticks while scrolling, canvas-subtle row hover and primary-subtle selection, a frozen first column (number/type) and a frozen right action column; amount columns are tnum right-aligned with two decimals.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.420Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 875
    end_line: 876
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "styles/components/table.css"
    description:
      zh: >
          表格样式：44px / 36px 行高、粘滞表头、首列与操作列固定。
          
      en: >
          Table CSS: 44px and 36px row heights, sticky header, frozen first and action columns.
          
deps:
  - kind: reference
    to: oa.design.token.spacing
    from_api: "file:styles/components/table.css"
    to_api: "file:styles/tokens/spacing.css"
    label: {zh: "行高与单元格内边距", en: "Row height & padding"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
