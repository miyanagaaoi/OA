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
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.295Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ORG-001`（§5.1 组织架构模型）
- `doc/data-model.md` → `CREATE TABLE sys_org`（§2. 身份与组织）
