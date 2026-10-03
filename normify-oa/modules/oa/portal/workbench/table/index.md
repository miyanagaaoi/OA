---
uid: 08ba5655
id: oa.portal.workbench.table
parent: oa.portal.workbench
state: planned
name: {zh: "审批数据表", en: "Approval Data Table"}
description:
  zh: >
      列表主体按 PRD 13.2 采用「全宽数据表 + 浮层详情」：列全展开（含「当前节点」列）、44px 行高、表头粘滞、首列（单号/类型）与操作列右固定；不做列合并、不做常驻详情栏，以保留金额 / 节点 / 时间的纵向比对能力。
      
  en: >
      The list body follows PRD 13.2: a full-width data table with an overlay detail — all columns expanded (including a current-node column), 44px rows, sticky header, frozen first column (number/type) and frozen right action column; no column merging and no permanent detail pane, preserving vertical comparison of amount, node and time.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.408Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "doc/prd-0.1.md"
  - path: "DESIGN.md"
    line: 875
    end_line: 876
deps:
  - kind: reference
    to: oa.design.component
    label: {zh: "表格组件与状态徽标规范", en: "Table & status-badge specs"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
