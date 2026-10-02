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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.741Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 413
    end_line: 420
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
