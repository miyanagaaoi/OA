---
uid: 0bd6f085
id: oa.identity.user.resign
parent: oa.identity.user
name: {zh: "离职与待办清理闸门", en: "Resignation Handover Gate"}
description:
  zh: >
      员工离职前必须处理完名下全部待办：系统提示未处理任务数量并强制先转办或改派，完成后才允许将状态置为离职（快照策略的补偿控制，AC-12）。
      
  en: >
      Resignation requires all of a user's pending tasks to be handled first: the system reports the count of unfinished tasks and forces transfer or reassignment before the status can become resigned — the compensating control for snapshots.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.741Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 234
    end_line: 244
  - path: "doc/prd-0.1.md"
    line: 563
    end_line: 600
apis:
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/{id}/pending-tasks"
    description:
      zh: >
          返回名下未处理待办数量与清单。
          
      en: >
          Returns the count and list of pending tasks.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/users/{id}/resign"
    description:
      zh: >
          待办清空后办理离职。
          
      en: >
          Marks resignation after tasks are cleared.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/users/{id}/handover"
    description:
      zh: >
          批量转办/改派名下待办。
          
      en: >
          Bulk-transfers or reassigns pending tasks.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/identity/users/{id}/resign"
    to_api: "PUT /api/v1/identity/users/{id}"
    label: {zh: "回写离职状态", en: "Write back status"}
  - kind: dataflow
    to: oa.workflow.task
    label: {zh: "查询名下待办并转办/改派", en: "List and reassign tasks"}
---
