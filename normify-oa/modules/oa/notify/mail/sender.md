---
uid: 6ba485f6
id: oa.notify.mail.sender
parent: oa.notify.mail
state: planned
name: {zh: "SMTP 投递", en: "SMTP Delivery"}
description:
  zh: >
      通过私有化部署的 SMTP 服务器投递邮件（超时、重试与连通性自检），投递结果回传失败记录模块；投递失败不改变单据状态。
      
  en: >
      Delivers mail through a privately deployed SMTP server with timeouts, retries and a connectivity self-check; results feed the failure module and never change document state.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.755Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 408
    end_line: 408
  - path: "doc/prd-0.1.md"
    line: 531
    end_line: 531
apis:
  - protocol: http
    method: POST
    path: "/api/v1/notifications/mail/send"
    description:
      zh: >
          通过 SMTP 投递邮件。
          
      en: >
          Delivers a mail over SMTP.
          
  - protocol: http
    method: GET
    path: "/api/v1/notifications/mail/health"
    description:
      zh: >
          SMTP 连通性与凭据自检。
          
      en: >
          SMTP connectivity and credential self-check.
          
deps:
  - kind: call
    to: oa.notify.mail.failure
    from_api: "POST /api/v1/notifications/mail/send"
    to_api: "POST /api/v1/notifications/mail/failures"
    label: {zh: "记录发送失败", en: "Record send failure"}
---
