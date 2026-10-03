---
uid: 1d10bbfc
id: oa.integration.gateway.authz.scope
parent: oa.integration.gateway.authz
name: {zh: "数据域过滤", en: "Data Scope Filter"}
description:
  zh: >
      把可验收的数据域口径应用到每次读取，子公司永远看不到其它公司的单据。
      
  en: >
      Applies the verifiable data-scope rules to every read so a subsidiary can never see another company's documents.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.311Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
