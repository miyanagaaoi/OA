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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.547Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
