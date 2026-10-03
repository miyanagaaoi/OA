---
uid: 3375d2c9
id: oa.workflow.routing.back-home
parent: oa.workflow.routing
state: planned
name: {zh: "回到本部门", en: "Return to Own Department"}
description:
  zh: >
      把后续流转收束回本部门，由本部门决定下一步；同一部门连续「回到本部门」不超过 2 次，超出拒绝；该动作不计入流转+回退总次数，但必须记入审计日志；是「禁止回流已处理部门」闸门的唯一例外。
      
  en: >
      Funnels the subsequent routing back to the own department so that this department decides the next step; the same department may return home at most twice consecutively, after which the action is rejected. The action does not count towards the routing quota but must be written to the audit log, and it is the only exception to the no-reflux gate.
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.438Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 324
    end_line: 324
  - path: "doc/prd-0.1.md"
    line: 359
    end_line: 359
  - path: "doc/data-model.md"
    line: 482
    end_line: 482
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/return-to-department"
    description:
      zh: >
          将后续流转收束回本部门（连续 ≤2 次）。
          
      en: >
          Funnel subsequent routing back to the own department (max twice consecutively).
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/back-home-count"
    description:
      zh: >
          读取本部门连续「回到本部门」次数。
          
      en: >
          Read how many times the department returned home in a row.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/instances/{instance_id}/return-to-department"
    label: {zh: "收束流转回本部门", en: "Funnel routing back home"}
  - kind: call
    to: oa.audit.oplog
    from_api: "POST /api/v1/flow/instances/{instance_id}/return-to-department"
    label: {zh: "计入审计日志", en: "Write to the audit log"}
---
