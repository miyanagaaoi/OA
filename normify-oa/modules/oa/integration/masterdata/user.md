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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.745Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
