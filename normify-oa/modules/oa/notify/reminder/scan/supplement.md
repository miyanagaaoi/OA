---
uid: 9c9cc871
id: oa.notify.reminder.scan.supplement
parent: oa.notify.reminder.scan
state: planned
name: {zh: "补件时限扫描", en: "Supplement Deadline Scan"}
description:
  zh: >
      扫描待补件请求 flow_supplement.deadline（默认请求后 3 个工作日），超期将 status 置为 overdue 并产出仅面向发起人的催办信号；补件超时不自动驳回、不自动通过。
      
  en: >
      Scans pending supplement requests against flow_supplement.deadline (3 working days by default), marks them overdue and emits a reminder aimed only at the initiator; no auto-reject or auto-approve.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.711Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 398
    end_line: 399
  - path: "doc/prd-0.1.md"
    line: 503
    end_line: 503
  - path: "doc/data-model.md"
    line: 500
    end_line: 524
apis:
  - protocol: http
    method: GET
    path: "/api/v1/notifications/reminders/overdue-supplements"
    description:
      zh: >
          扫描超期未提交的补件请求。
          
      en: >
          Scans supplement requests past their deadline.
          
  - protocol: http
    method: PUT
    path: "/api/v1/notifications/reminders/supplements/{id}/overdue"
    description:
      zh: >
          标记补件请求为已超时。
          
      en: >
          Marks a supplement request as overdue.
          
deps:
  - kind: reference
    to: oa.workflow.supplement
    label: {zh: "补件请求与轮次", en: "Supplement request & round"}
  - kind: call
    to: oa.notify.reminder.dispatch
    from_api: "GET /api/v1/notifications/reminders/overdue-supplements"
    to_api: "POST /api/v1/notifications/reminders"
    label: {zh: "触发补件催办", en: "Trigger supplement reminder"}
---
