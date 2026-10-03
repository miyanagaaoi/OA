---
uid: a83674dc
id: oa.admin.report.approver.efficiency
parent: oa.admin.report.approver
name: {zh: "审批人效率", en: "Approver Efficiency"}
description:
  zh: >
      统计每位审批人的处理量与平均处理时长，即一期口径下的审批人效率。
      
  en: >
      Computes how much each approver handles and how long they take, which is the phase-one definition of approver efficiency.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.909Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
