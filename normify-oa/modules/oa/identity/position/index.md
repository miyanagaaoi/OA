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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.361Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org_leader`（§2. 身份与组织）
