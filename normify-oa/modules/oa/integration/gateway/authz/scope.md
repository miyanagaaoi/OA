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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.714Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
