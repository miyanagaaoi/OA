---
uid: 2bf93ada
id: oa.admin.org.staff.offboard
parent: oa.admin.org.staff
state: planned
name: {zh: "离职闸门", en: "Resignation Gate"}
description:
  zh: >
      办理离职前统计并提示名下未处理任务数量，强制先转办或改派；离职后不再参与新的审批人解析，在途单据仍由快照审批人处理。
      
  en: >
      Before resignation, surface the number of unhandled tasks and force transfer or reassignment first; after resignation the user is excluded from new approver resolution while in-flight documents stay with their snapshot approvers.
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.628Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 241
    end_line: 241
  - path: "doc/prd-0.1.md"
    line: 238
    end_line: 238
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
