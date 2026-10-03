---
uid: 2d3d5e49
id: oa.workflow.routing
parent: oa.workflow
name: {zh: "集团层流转与回退", en: "Group Routing & Rollback"}
description:
  zh: >
      集团层链式流转：指定下一承接部门并填原因、支持连续流转（A→B→C）、回退上一已完成节点（同节点≤2 次）、回到本部门（连续≤2 次）；两道闸门：流转+回退总数≤5，禁止回流到已处理过的部门。
      
  en: >
      Group-level routing: route to a next department with a reason and continuous chains (A→B→C), roll back to the previous completed node (max twice), return to own department (max twice consecutively), and the two gates: routing plus rollback count at most five, and no routing back to a department already handled.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.415Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-020`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
