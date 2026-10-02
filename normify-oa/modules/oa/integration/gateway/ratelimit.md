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
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T07:59:23.046Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 533
    end_line: 534
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
