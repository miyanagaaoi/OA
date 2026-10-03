---
uid: aabf44e6
id: oa.admin.report.approver.backlog
parent: oa.admin.report.approver
name: {zh: "待办积压与账龄", en: "Backlog & Ageing"}
description:
  zh: >
      展示每位审批人当前积压的工作量以及最老待办的停留时长，为工作重平衡提供依据。
      
  en: >
      Shows how much work is currently sitting with each approver and how old the oldest pending task is, supporting workload rebalancing.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.908Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
