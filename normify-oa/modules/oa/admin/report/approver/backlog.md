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
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.278Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
