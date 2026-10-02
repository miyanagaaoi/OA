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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.747Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
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
