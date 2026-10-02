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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.718Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
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
