---
uid: 2c404555
id: oa.workflow.routing.forward.chain
parent: oa.workflow.routing.forward
name: {zh: "连续流转链", en: "Continuous Routing Chain"}
description:
  zh: >
      支持连续流转 A→B→C：每次流转递增 flow_routing.seq 与实例 routing_seq/routing_count，更新 current_dept_id，将当前节点实例置为已通过，并由新承接部门的负责人产生新任务；流转链历史对审批人可见。
      
  en: >
      Supports continuous routing A-B-C: each hop increments flow_routing.seq and the instance routing counters, updates the current receiving department, marks the current node instance approved and creates tasks for the leader of the new department. The chain remains visible to approvers.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.411Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/routing-chain"
    description:
      zh: >
          流转链历史（序号、来源/承接部门、动作、原因、状态）。
          
      en: >
          Routing chain history: seq, departments, action, reason, status.
          
  - protocol: kafka
    path: "oa.workflow.routing.forwarded"
    description:
      zh: >
          流转完成事件（生成承接部门任务并推进节点）。
          
      en: >
          Event emitted when a routing action completes.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "kafka:oa.workflow.routing.forwarded"
    label: {zh: "推进实例节点与计数", en: "Advance node and counters"}
  - kind: call
    to: oa.workflow.approver
    from_api: "kafka:oa.workflow.routing.forwarded"
    label: {zh: "解析承接部门负责人", en: "Resolve receiving dept leader"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-020`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
