---
uid: 58765b4d
id: oa.admin.flow.node.routing
parent: oa.admin.flow.node
state: planned
name: {zh: "流转与回退开关", en: "Routing Switches"}
description:
  zh: >
      控制节点是否允许流转到其他部门、回退、加签或自由跳转；流转与回退仅在集团层节点开启，并受闸门上限约束。
      
  en: >
      Controls whether a node may route to another department, roll back, add signers or jump freely; routing and rollback are enabled only on group-level nodes and are bounded by the gate limits.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.599Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 352
    end_line: 352
  - path: "doc/data-model.md"
    line: 313
    end_line: 315
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
