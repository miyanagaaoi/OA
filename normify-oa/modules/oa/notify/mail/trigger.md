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
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
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
