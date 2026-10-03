---
uid: 08f1e4db
id: oa.portal.workbench.table.columns
parent: oa.portal.workbench.table
name: {zh: "列表列定义", en: "List Columns"}
description:
  zh: >
      全宽表格的列定义：类型图标 + 标题 + 状态徽标、单号、发起人、部门、当前节点、提交时间、金额；金额列使用 typography.amount（tnum）右对齐并保留两位小数，≥100 万时同时显示万元换算；时间戳与单号用等宽字体。
      
  en: >
      Column definitions for the full-width table: type icon + title + status badge, document number, initiator, department, current node, submitted time, amount; the amount column uses typography.amount (tnum), right-aligned with two decimals and a ten-thousand-yuan conversion at 1,000,000 and above; timestamps and numbers use the monospace face.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.518Z"
fingerprint: d7d0d1b9e6e41e9590b00d6806c9c43da38139e084006a3b5c0ed7a820865bdb
source:
  - path: "DESIGN.md"
    line: 875
    end_line: 876
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/portal/workbench/columns"
    description:
      zh: >
          按单据类型与用户偏好返回列表列定义。
          
      en: >
          Returns column definitions for the list by document type and user preference.
          
deps:
  - kind: dataflow
    to: oa.workflow.task.record
    from_api: "GET /api/v1/portal/workbench/columns"
    to_api: "mysql:flow_task"
    label: {zh: "读取待办任务字段", en: "Read pending task fields"}
  - kind: reference
    to: oa.design.token
    label: {zh: "金额与时间戳的令牌约束", en: "Amount & timestamp tokens"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 13.2 与审批业务强相关的约定`（§13.2 与审批业务强相关的约定）
