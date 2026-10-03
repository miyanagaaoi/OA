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
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.473Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org_leader`（§2. 身份与组织）
