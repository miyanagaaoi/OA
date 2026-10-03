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
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.524Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
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
