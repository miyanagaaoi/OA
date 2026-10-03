---
uid: 6ba485f6
id: oa.notify.mail.sender
parent: oa.notify.mail
name: {zh: "SMTP 投递", en: "SMTP Delivery"}
description:
  zh: >
      通过私有化部署的 SMTP 服务器投递邮件（超时、重试与连通性自检），投递结果回传失败记录模块；投递失败不改变单据状态。
      
  en: >
      Delivers mail through a privately deployed SMTP server with timeouts, retries and a connectivity self-check; results feed the failure module and never change document state.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.323Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-002`（§6.7 消息通知）
