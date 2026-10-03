---
uid: 6ce4f64f
id: oa.notify.mail.failure
parent: oa.notify.mail
name: {zh: "邮件发送失败记录", en: "Mail Failure Records"}
description:
  zh: >
      邮件发送失败记录、查询与手动重试（失败记录可查，REQ-MSG-002）；失败仅记录不阻塞流程，也不无限重发。
      
  en: >
      Records, queries and manually retries failed mail (failures are inspectable, REQ-MSG-002); failures never block the flow nor retry indefinitely.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.365Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-002`（§6.7 消息通知）
