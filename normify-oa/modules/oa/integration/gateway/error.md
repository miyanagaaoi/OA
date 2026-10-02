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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.746Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
