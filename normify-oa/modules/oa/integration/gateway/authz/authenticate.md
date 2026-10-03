---
uid: 0057508e
id: oa.integration.gateway.authz.authenticate
parent: oa.integration.gateway.authz
state: planned
name: {zh: "身份认证", en: "Authentication"}
description:
  zh: >
      校验调用方凭证或会话，在任何业务处理前拒绝匿名请求。
      
  en: >
      Validates the caller's credential or session and rejects anonymous requests before any business handler runs.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.672Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 511
    end_line: 516
apis:
  - protocol: http
    method: POST
    path: "/api/v1/gateway/authorize"
    description:
      zh: >
          校验调用方凭证或会话并返回授权判定。
          
      en: >
          Validates the caller credential or session and returns an authorisation decision.
          
  - protocol: redis
    path: "gateway:session:{token}"
    description:
      zh: >
          会话缓存，避免每次跳转都重新校验凭证。
          
      en: >
          Session cache used to avoid re-validating a credential on every hop.
          
---
