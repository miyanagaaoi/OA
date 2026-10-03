---
uid: 686e9f66
id: oa.notify.mail.template
parent: oa.notify.mail
state: planned
name: {zh: "邮件模板渲染", en: "Mail Template Rendering"}
description:
  zh: >
      邮件主题与正文模板（按通知类型区分），渲染单据要素与直达链接；字体与图片一律本地化，不依赖公网 CDN。
      
  en: >
      Mail subject and body templates per notification type, rendering document elements and a deep link; fonts and images are localized with no public CDN.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.756Z"
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
    method: GET
    path: "/api/v1/notifications/mail/templates"
    description:
      zh: >
          列出邮件模板。
          
      en: >
          Lists the mail templates.
          
  - protocol: http
    method: PUT
    path: "/api/v1/notifications/mail/templates/{code}"
    description:
      zh: >
          维护指定邮件模板内容。
          
      en: >
          Maintains one mail template.
          
deps:
  - kind: reference
    to: oa.notify.inbox.template
    to_api: "GET /api/v1/notifications/inbox/templates"
    label: {zh: "与站内信共用占位符", en: "Shared placeholders"}
---
