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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.642Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
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
