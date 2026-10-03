---
uid: 2ec5f230
id: oa.identity.session.remember-me
parent: oa.identity.session
name: {zh: "记住我与自动续期", en: "Remember Me & Renewal"}
description:
  zh: >
      勾选「记住我」后 7 天内免登录，期间访问自动续期；未勾选时关闭浏览器即失效。有效期来自会话策略配置（默认 7 天）。
      
  en: >
      Remember-me keeps a user logged in for seven days with automatic renewal on each visit; without it the session dies when the browser closes. The lifetime comes from the session policy (seven days by default).
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:47:41.779Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 435
    end_line: 435
  - path: "doc/prd-0.1.md"
    line: 546
    end_line: 561
apis:
  - protocol: http
    method: POST
    path: "/api/v1/auth/refresh"
    description:
      zh: >
          续期当前会话（自动续期）。
          
      en: >
          Renews the current session.
          
  - protocol: http
    method: POST
    path: "/api/v1/auth/remember"
    description:
      zh: >
          登录时写入记住我长效凭证。
          
      en: >
          Stores the remember-me credential at login.
          
  - protocol: http
    method: GET
    path: "/api/v1/auth/session-policy"
    description:
      zh: >
          读取记住我有效期与多设备上限。
          
      en: >
          Reads remember-me lifetime and device cap.
          
deps:
  - kind: call
    to: oa.identity.session.login.issue
    from_api: "POST /api/v1/auth/remember"
    to_api: "POST /api/v1/auth/login"
    label: {zh: "登录后签发长效凭证", en: "Issue long-lived session"}
---
