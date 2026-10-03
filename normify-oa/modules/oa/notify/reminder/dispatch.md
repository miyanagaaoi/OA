---
uid: a0893b5f
id: oa.notify.reminder.dispatch
parent: oa.notify.reminder
name: {zh: "催办下发", en: "Reminder Dispatch"}
description:
  zh: >
      生成并下发催办：站内信 + 邮件双通道同时发送（可抄送上级），同一对象重复催办按间隔压制；一期超时仅催办，不自动跳过、不自动升级。
      
  en: >
      Builds and sends reminders over both in-app and mail channels (optionally escalating to a superior), suppressing repeats by interval; phase one never auto-skips or auto-escalates.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.324Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/notifications/reminders"
    description:
      zh: >
          生成并下发一条催办。
          
      en: >
          Builds and dispatches one reminder.
          
  - protocol: kafka
    path: "oa.notify.reminder.sent"
    description:
      zh: >
          催办已下发事件。
          
      en: >
          Event published when a reminder is sent.
          
deps:
  - kind: call
    to: oa.notify.inbox.publish
    from_api: "POST /api/v1/notifications/reminders"
    to_api: "POST /api/v1/notifications/inbox"
    label: {zh: "站内信催办", en: "In-app reminder"}
  - kind: call
    to: oa.notify.mail.trigger
    from_api: "POST /api/v1/notifications/reminders"
    to_api: "POST /api/v1/notifications/mail/dispatch"
    label: {zh: "邮件催办", en: "Mail reminder"}
  - kind: call
    to: oa.notify.reminder.escalate
    from_api: "POST /api/v1/notifications/reminders"
    to_api: "POST /api/v1/notifications/reminders/{id}/escalate"
    label: {zh: "抄送上级", en: "CC the superior"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
