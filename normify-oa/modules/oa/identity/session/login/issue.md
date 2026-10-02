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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.735Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
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
