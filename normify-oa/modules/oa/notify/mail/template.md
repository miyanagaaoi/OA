---
uid: 686e9f66
id: oa.notify.mail.template
parent: oa.notify.mail
name: {zh: "邮件模板渲染", en: "Mail Template Rendering"}
description:
  zh: >
      邮件主题与正文模板（按通知类型区分），渲染单据要素与直达链接；字体与图片一律本地化，不依赖公网 CDN。
      
  en: >
      Mail subject and body templates per notification type, rendering document elements and a deep link; fonts and images are localized with no public CDN.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.324Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-MSG-002`（§6.7 消息通知）
