---
uid: 1d10bbfc
id: oa.integration.gateway.authz.scope
parent: oa.integration.gateway.authz
state: planned
name: {zh: "数据域过滤", en: "Data Scope Filter"}
description:
  zh: >
      把可验收的数据域口径应用到每次读取，子公司永远看不到其它公司的单据。
      
  en: >
      Applies the verifiable data-scope rules to every read so a subsidiary can never see another company's documents.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.742Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 169
    end_line: 199
apis:
  - protocol: http
    method: POST
    path: "/api/v1/gateway/scope-filter"
    description:
      zh: >
          在响应离开网关前按调用方数据域过滤结果集。
          
      en: >
          Filters a result set by the caller's data scope before it leaves the gateway.
          
---
