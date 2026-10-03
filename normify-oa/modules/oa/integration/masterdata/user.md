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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.254Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
- `doc/data-model.md` → `CREATE TABLE sys_user`（§2. 身份与组织）
