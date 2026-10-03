---
uid: 01f4c7cf
id: oa.admin.org.tree
parent: oa.admin.org
state: planned
name: {zh: "组织架构维护", en: "Org Structure Admin"}
description:
  zh: >
      后台维护集团-公司-部门-科室四级组织树：节点增删改、停用闸门与正副职负责人维护；停用前必须处理完该节点全部在途单据，停用后不可作为发起者归属节点。
      
  en: >
      Console-side maintenance of the four-level org tree: node CRUD, a disable gate and leader maintenance; disabling requires every in-flight document of that node to be finished first, and a disabled node can no longer own new initiators.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.431Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
