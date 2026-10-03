---
uid: 40faab29
id: oa.integration.masterdata.user
parent: oa.integration.masterdata
state: planned
name: {zh: "人员只读接口", en: "User Read-Only API"}
description:
  zh: >
      用户只读查询：按公司与状态列举、单用户详情；手机号对非特权调用方返回脱敏值。
      
  en: >
      Read-only user queries: user list with company and status, single-user detail, and mobile numbers returned masked unless the caller is privileged.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.616Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 514
    end_line: 516
  - path: "doc/data-model.md"
    line: 28
    end_line: 163
apis:
  - protocol: http
    method: GET
    path: "/api/v1/open/users"
    description:
      zh: >
          只读列举人员（手机号默认脱敏）。
          
      en: >
          Read-only user listing with masked phone numbers.
          
  - protocol: http
    method: GET
    path: "/api/v1/open/users/{id}"
    description:
      zh: >
          只读查询单个人员详情。
          
      en: >
          Read-only detail of one user.
          
---
