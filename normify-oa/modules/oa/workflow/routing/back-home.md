---
uid: 3375d2c9
id: oa.workflow.routing.back-home
parent: oa.workflow.routing
name: {zh: "回到本部门", en: "Return to Own Department"}
description:
  zh: >
      把后续流转收束回本部门，由本部门决定下一步；同一部门连续「回到本部门」不超过 2 次，超出拒绝；该动作不计入流转+回退总次数，但必须记入审计日志；是「禁止回流已处理部门」闸门的唯一例外。
      
  en: >
      Funnels the subsequent routing back to the own department so that this department decides the next step; the same department may return home at most twice consecutively, after which the action is rejected. The action does not count towards the routing quota but must be written to the audit log, and it is the only exception to the no-reflux gate.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.371Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-022`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
