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
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.610Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org_leader`（§2. 身份与组织）
