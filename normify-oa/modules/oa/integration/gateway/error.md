---
uid: 36e4e009
id: oa.integration.gateway.error
parent: oa.integration.gateway
state: planned
name: {zh: "统一错误与追踪", en: "Errors & Tracing"}
description:
  zh: >
      统一错误码与响应体、结构化访问日志与追踪 ID，使审计记录能回溯到原始请求。
      
  en: >
      Unified error envelope with stable codes, structured access logging and trace identifiers that let an audit entry be tied back to the originating request.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.740Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 511
    end_line: 516
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
