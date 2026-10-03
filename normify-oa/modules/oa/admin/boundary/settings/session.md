---
uid: de270a26
id: oa.admin.boundary.settings.session
parent: oa.admin.boundary.settings
state: planned
name: {zh: "会话与登录策略", en: "Session & Login Policy"}
description:
  zh: >
      可配置项：同时在线设备上限（默认 3 台，超出踢出最早登录设备）、「记住我」有效期（默认 7 天，期间自动续期）、登录失败锁定（默认 5 次，锁定 15 分钟）。
      
  en: >
      Adjustable items: concurrent device cap (default three, earliest device evicted), remember-me validity (default seven days with automatic renewal) and login failure lockout (default five attempts, fifteen-minute lockout).
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.113Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 577
    end_line: 577
  - path: "doc/prd-0.1.md"
    line: 416
    end_line: 417
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/session-policy"
    description:
      zh: >
          查询会话与登录策略当前值。
          
      en: >
          Read the session and login policy values.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/session-policy"
    description:
      zh: >
          设置在线设备上限、记住我天数与失败锁定规则。
          
      en: >
          Set device cap, remember-me days and lockout rules.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/session-policy/reset"
    description:
      zh: >
          恢复文档默认值。
          
      en: >
          Restore the documented default values.
          
deps:
  - kind: call
    to: oa.identity.session
    from_api: "PUT /api/v1/admin/session-policy"
    label: {zh: "会话策略由身份域执行", en: "Session policy in identity"}
---
