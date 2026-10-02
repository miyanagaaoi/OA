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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.609Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
