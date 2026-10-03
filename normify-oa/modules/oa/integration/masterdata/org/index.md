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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.550Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
