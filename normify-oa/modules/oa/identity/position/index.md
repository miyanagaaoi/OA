---
uid: 0b1f3c24
id: oa.identity.position
parent: oa.identity
name: {zh: "岗位与负责人", en: "Posts & Leaders"}
description:
  zh: >
      组织负责人绑定（正职/副职、可多人、可排序）与一人多岗：一名员工可同时挂职多个组织节点；集团层按业务线绑定分管领导（财务/人力/行政/经营）。
      
  en: >
      Leaders per org node (primary/deputy, sortable, multiple allowed) and multi-post assignment: one employee may hold posts in several org nodes; group-level business-line leaders are bound by line.
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.234Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org_leader`（§2. 身份与组织）
