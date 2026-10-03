---
uid: 2d3d5e49
id: oa.workflow.routing
parent: oa.workflow
state: planned
name: {zh: "集团层流转与回退", en: "Group Routing & Rollback"}
description:
  zh: >
      集团层链式流转：指定下一承接部门并填原因、支持连续流转（A→B→C）、回退上一已完成节点（同节点≤2 次）、回到本部门（连续≤2 次）；两道闸门：流转+回退总数≤5，禁止回流到已处理过的部门。
      
  en: >
      Group-level routing: route to a next department with a reason and continuous chains (A→B→C), roll back to the previous completed node (max twice), return to own department (max twice consecutively), and the two gates: routing plus rollback count at most five, and no routing back to a department already handled.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.821Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 312
    end_line: 341
  - path: "doc/data-model.md"
    line: 375
    end_line: 528
---
