---
uid: 58765b4d
id: oa.admin.flow.node.routing
parent: oa.admin.flow.node
name: {zh: "流转与回退开关", en: "Routing Switches"}
description:
  zh: >
      控制节点是否允许流转到其他部门、回退、加签或自由跳转；流转与回退仅在集团层节点开启，并受闸门上限约束。
      
  en: >
      Controls whether a node may route to another department, roll back, add signers or jump freely; routing and rollback are enabled only on group-level nodes and are bounded by the gate limits.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.382Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-nodes/{node_id}/routing"
    description:
      zh: >
          查询节点的流转/回退/加签开关。
          
      en: >
          Read the routing, rollback and add-sign switches.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/flow-nodes/{node_id}/routing"
    description:
      zh: >
          设置流转、回退、加签、跳转开关。
          
      en: >
          Set routing, rollback, add-sign and jump switches.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/flow-routing-limits"
    description:
      zh: >
          查询流转与回退闸门限制。
          
      en: >
          Read the routing and rollback gate limits.
          
deps:
  - kind: call
    to: oa.workflow.routing
    from_api: "PUT /api/v1/admin/flow-nodes/{node_id}/routing"
    label: {zh: "闸门参数供流转执行", en: "Limits used by routing"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-024`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
