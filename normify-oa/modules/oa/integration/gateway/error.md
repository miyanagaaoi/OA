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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.696Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
