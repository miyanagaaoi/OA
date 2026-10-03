---
uid: 38b3318d
id: oa.workflow.routing.gate.no-reflux
parent: oa.workflow.routing.gate
state: planned
name: {zh: "禁止回流闸门", en: "No-reflux Gate"}
description:
  zh: >
      已处理过的部门不可再次被指定为流转目标（A→B→A 拒绝）：「回到本部门」是唯一例外且限连续 2 次；判定依据是 flow_routing 流转链中已出现过的承接部门集合；命中即拒绝操作并返回具体原因。
      
  en: >
      A department already handled cannot be designated as a routing target again (A-B-A is refused); returning to the own department is the only exception and is limited to two consecutive times. The judgement uses the set of receiving departments already present in the routing chain, and a hit is refused with a concrete reason and no state change.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.447Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/reflux-check"
    description:
      zh: >
          校验目标部门是否已处理过（命中则拒绝流转）。
          
      en: >
          Check whether the target department was already handled and refuse routing.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/handled-departments"
    description:
      zh: >
          已处理部门集合（由流转链推导）。
          
      en: >
          Departments already handled, derived from the routing chain.
          
deps:
  - kind: reference
    to: oa.workflow.routing.forward.designate
    to_api: "mysql:flow_routing"
    label: {zh: "依据流转链判定", en: "Judge from the routing chain"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-024`（§6.4 流程引擎核心能力）
