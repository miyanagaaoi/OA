---
uid: 063dc913
id: oa.integration.gateway.routing
parent: oa.integration.gateway
state: planned
name: {zh: "路由与版本", en: "Gateway Routing"}
description:
  zh: >
      统一 API 网关按版本与所属模块路由请求，一期只暴露允许的接口（健康检查、版本探针与只读主数据），保持对外契约稳定。
      
  en: >
      Routes every request by API version and owning module, exposing only the endpoints allowed in phase one (health/version probes plus read-only master data).
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.382Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/open/health"
    description:
      zh: >
          网关与后端服务健康检查。
          
      en: >
          Health probe for the gateway and backend services.
          
  - protocol: http
    method: GET
    path: "/api/v1/open/version"
    description:
      zh: >
          查询对外接口版本。
          
      en: >
          Returns the exposed API version.
          
  - protocol: file
    path: "deploy/gateway/routes.yml"
    description:
      zh: >
          版本与模块路由配置。
          
      en: >
          Version and module routing configuration.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
