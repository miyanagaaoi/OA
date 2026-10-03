---
uid: a83674dc
id: oa.admin.report.approver.efficiency
parent: oa.admin.report.approver
state: planned
name: {zh: "审批人效率", en: "Approver Efficiency"}
description:
  zh: >
      统计每位审批人的处理量与平均处理时长，即一期口径下的审批人效率。
      
  en: >
      Computes how much each approver handles and how long they take, which is the phase-one definition of approver efficiency.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.515Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/approver-efficiency"
    description:
      zh: >
          人均审批量与平均处理时长。
          
      en: >
          Per-approver approval count and mean handling time.
          
  - protocol: file
    path: "export/reports/approver-efficiency.csv"
    description:
      zh: >
          审批人效率报表导出件。
          
      en: >
          Exported approver-efficiency report.
          
deps:
  - kind: dataflow
    to: oa.workflow.task.record
    to_api: "mysql:flow_task"
    label: {zh: "读取任务记录", en: "Read task records"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
