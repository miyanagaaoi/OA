---
uid: aabf44e6
id: oa.admin.report.approver.backlog
parent: oa.admin.report.approver
state: planned
name: {zh: "待办积压与账龄", en: "Backlog & Ageing"}
description:
  zh: >
      展示每位审批人当前积压的工作量以及最老待办的停留时长，为工作重平衡提供依据。
      
  en: >
      Shows how much work is currently sitting with each approver and how old the oldest pending task is, supporting workload rebalancing.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.631Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
  - path: "doc/prd-0.1.md"
    line: 241
    end_line: 241
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/approver-backlog"
    description:
      zh: >
          各审批人待办积压量与最长等待时长。
          
      en: >
          Pending volume and longest wait per approver.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/approver-backlog/aging"
    description:
      zh: >
          待办账龄分布。
          
      en: >
          Ageing distribution of pending tasks.
          
---
