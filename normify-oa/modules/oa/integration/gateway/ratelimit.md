---
uid: 200b8559
id: oa.integration.gateway.ratelimit
parent: oa.integration.gateway
state: planned
name: {zh: "限流与幂等", en: "Rate Limit & Idempotency"}
description:
  zh: >
      按客户端与时间窗口限流，并提供幂等键，避免重试的审批提交产生重复单据。
      
  en: >
      Rate limiting per client and window plus idempotency keys so retried approval submissions cannot create duplicate documents.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.387Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/gateway/rate-limit/check"
    description:
      zh: >
          限流判定，超限返回 429。
          
      en: >
          Rate-limit check returning 429 when exceeded.
          
  - protocol: redis
    path: "gateway:rate:{client}:{window}"
    description:
      zh: >
          限流计数键。
          
      en: >
          Rate-limit counter key.
          
  - protocol: http
    method: POST
    path: "/api/v1/gateway/idempotency"
    description:
      zh: >
          提交类接口的幂等键校验。
          
      en: >
          Idempotency-key guard for submit endpoints.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
