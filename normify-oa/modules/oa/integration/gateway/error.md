---
uid: 36e4e009
id: oa.integration.gateway.error
parent: oa.integration.gateway
name: {zh: "统一错误与追踪", en: "Errors & Tracing"}
description:
  zh: >
      统一错误码与响应体、结构化访问日志与追踪 ID，使审计记录能回溯到原始请求。
      
  en: >
      Unified error envelope with stable codes, structured access logging and trace identifiers that let an audit entry be tied back to the originating request.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.075Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/gateway/trace/{trace_id}"
    description:
      zh: >
          按追踪 ID 查请求链路。
          
      en: >
          Looks up a request trace by trace id.
          
  - protocol: file
    path: "logs/gateway/access.log"
    description:
      zh: >
          网关结构化访问日志。
          
      en: >
          Structured gateway access log.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
