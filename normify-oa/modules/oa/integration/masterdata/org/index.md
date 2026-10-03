---
uid: 3feff260
id: oa.integration.masterdata.org
parent: oa.integration.masterdata
state: planned
name: {zh: "组织只读接口", en: "Org Read-Only API"}
description:
  zh: >
      面向 HR 等下游系统的组织只读查询：组织树遍历、单节点详情与节点负责人。
      
  en: >
      Read-only organisation queries exposed to downstream systems such as HR: tree traversal, single-node detail and node leaders.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.251Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
