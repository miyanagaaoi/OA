---
uid: de270a26
id: oa.admin.boundary.settings.session
parent: oa.admin.boundary.settings
name: {zh: "会话与登录策略", en: "Session & Login Policy"}
description:
  zh: >
      可配置项：同时在线设备上限（默认 3 台，超出踢出最早登录设备）、「记住我」有效期（默认 7 天，期间自动续期）、登录失败锁定（默认 5 次，锁定 15 分钟）。
      
  en: >
      Adjustable items: concurrent device cap (default three, earliest device evicted), remember-me validity (default seven days with automatic renewal) and login failure lockout (default five attempts, fifteen-minute lockout).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.181Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-006`（§第9章 非功能需求）
