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
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.338Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
