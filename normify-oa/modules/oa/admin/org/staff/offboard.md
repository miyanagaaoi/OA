---
uid: 2bf93ada
id: oa.admin.org.staff.offboard
parent: oa.admin.org.staff
name: {zh: "离职闸门", en: "Resignation Gate"}
description:
  zh: >
      办理离职前统计并提示名下未处理任务数量，强制先转办或改派；离职后不再参与新的审批人解析，在途单据仍由快照审批人处理。
      
  en: >
      Before resignation, surface the number of unhandled tasks and force transfer or reassignment first; after resignation the user is excluded from new approver resolution while in-flight documents stay with their snapshot approvers.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.898Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/users/{user_id}/pending-tasks"
    description:
      zh: >
          统计名下未处理待办数量（离职前置提示）。
          
      en: >
          Count unhandled tasks as a pre-resignation prompt.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/users/{user_id}/resign"
    description:
      zh: >
          办理离职；存在未处理待办时拒绝。
          
      en: >
          Complete resignation; rejected while tasks remain.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/users/{user_id}/reassign"
    description:
      zh: >
          离职前批量转办/改派名下待办。
          
      en: >
          Bulk transfer or reassign tasks before resignation.
          
deps:
  - kind: call
    to: oa.workflow.task
    from_api: "GET /api/v1/admin/users/{user_id}/pending-tasks"
    label: {zh: "待办任务数量与转办", en: "Pending tasks and transfer"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-002`（§5.5 组织与人员变更的处理）
