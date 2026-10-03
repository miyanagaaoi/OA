---
uid: 6ce4f64f
id: oa.notify.mail.failure
parent: oa.notify.mail
state: planned
name: {zh: "邮件发送失败记录", en: "Mail Failure Records"}
description:
  zh: >
      邮件发送失败记录、查询与手动重试（失败记录可查，REQ-MSG-002）；失败仅记录不阻塞流程，也不无限重发。
      
  en: >
      Records, queries and manually retries failed mail (failures are inspectable, REQ-MSG-002); failures never block the flow nor retry indefinitely.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.356Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 408
    end_line: 408
  - path: "doc/prd-0.1.md"
    line: 411
    end_line: 411
apis:
  - protocol: http
    method: POST
    path: "/api/v1/notifications/mail/failures"
    description:
      zh: >
          记录一封发送失败的邮件。
          
      en: >
          Records a failed mail delivery.
          
  - protocol: http
    method: GET
    path: "/api/v1/notifications/mail/failures"
    description:
      zh: >
          查询发送失败记录（管理员）。
          
      en: >
          Queries failed deliveries (administrators).
          
  - protocol: http
    method: POST
    path: "/api/v1/notifications/mail/failures/{id}/retry"
    description:
      zh: >
          手动重试发送失败的邮件。
          
      en: >
          Manually retries a failed mail.
          
deps:
  - kind: reference
    to: oa.admin.report
    label: {zh: "通知失败率统计", en: "Mail failure statistics"}
---
