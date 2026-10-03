---
uid: 200b8559
id: oa.integration.gateway.ratelimit
parent: oa.integration.gateway
name: {zh: "限流与幂等", en: "Rate Limit & Idempotency"}
description:
  zh: >
      按客户端与时间窗口限流，并提供幂等键，避免重试的审批提交产生重复单据。
      
  en: >
      Rate limiting per client and window plus idempotency keys so retried approval submissions cannot create duplicate documents.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.076Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
