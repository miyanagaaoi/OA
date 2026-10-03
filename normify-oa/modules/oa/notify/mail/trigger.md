---
uid: 63c06bb4
id: oa.notify.mail.trigger
parent: oa.notify.mail
state: planned
name: {zh: "邮件触发场景", en: "Mail Triggers"}
description:
  zh: >
      邮件通知触发点：待办产生、被驳回、超时催办、终审通过；触发后异步投递，发送失败不阻塞审批流程，是系统唯一的主动提醒通道。
      
  en: >
      Mail triggers for new tasks, rejection, timeout reminders and final approval; dispatch is asynchronous and a failure never blocks the approval flow — it is the only proactive channel.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.397Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/notifications/mail/dispatch"
    description:
      zh: >
          触发一次邮件通知（异步）。
          
      en: >
          Triggers one mail notification asynchronously.
          
  - protocol: kafka
    path: "oa.notify.mail.requested"
    description:
      zh: >
          邮件发送请求事件。
          
      en: >
          Event requesting a mail delivery.
          
deps:
  - kind: call
    to: oa.notify.mail.sender
    from_api: "POST /api/v1/notifications/mail/dispatch"
    to_api: "POST /api/v1/notifications/mail/send"
    label: {zh: "投递邮件", en: "Deliver mail"}
  - kind: call
    to: oa.notify.mail.template
    from_api: "POST /api/v1/notifications/mail/dispatch"
    to_api: "GET /api/v1/notifications/mail/templates"
    label: {zh: "渲染邮件内容", en: "Render mail body"}
  - kind: reference
    to: oa.identity.user
    label: {zh: "收件人邮箱", en: "Recipient mailbox"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-002`（§6.7 消息通知）
