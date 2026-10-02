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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.673Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
