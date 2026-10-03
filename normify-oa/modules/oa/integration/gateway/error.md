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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.248Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
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
