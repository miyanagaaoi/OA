---
uid: 2440610a
id: oa.identity.session.login.issue
parent: oa.identity.session.login
state: planned
name: {zh: "会话签发与登出", en: "Session Issue & Logout"}
description:
  zh: >
      校验通过后签发登录会话（Cookie/Token 的具体实现由技术方案决定），维护服务端会话注册表，提供当前登录人上下文与登出。
      
  en: >
      Issues the login session after successful verification (cookie vs token is a technical decision), maintains the server-side session registry and provides the current-user context and logout.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.691Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 413
    end_line: 420
  - path: "doc/data-model.md"
    line: 59
    end_line: 83
apis:
  - protocol: http
    method: POST
    path: "/api/v1/auth/login"
    description:
      zh: >
          账号密码登录并签发会话。
          
      en: >
          Logs in and issues a session.
          
  - protocol: http
    method: POST
    path: "/api/v1/auth/logout"
    description:
      zh: >
          登出并注销当前会话。
          
      en: >
          Logs out and revokes the session.
          
  - protocol: http
    method: GET
    path: "/api/v1/auth/me"
    description:
      zh: >
          返回当前登录人、角色与数据域上下文。
          
      en: >
          Returns the current user, roles and scope context.
          
  - protocol: redis
    path: "auth:session:{token}"
    description:
      zh: >
          服务端会话注册表缓存键。
          
      en: >
          Server-side session registry key.
          
deps:
  - kind: call
    to: oa.identity.session.login.password
    from_api: "POST /api/v1/auth/login"
    to_api: "POST /api/v1/auth/password/verify"
    label: {zh: "校验账号密码", en: "Verify credentials"}
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/auth/login"
    to_api: "GET /api/v1/identity/users"
    label: {zh: "载入登录人上下文", en: "Load caller context"}
---
