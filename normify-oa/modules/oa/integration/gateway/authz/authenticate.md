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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.742Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
