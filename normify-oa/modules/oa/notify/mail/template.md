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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.263Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
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
