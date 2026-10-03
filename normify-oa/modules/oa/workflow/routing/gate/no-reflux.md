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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.805Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 338
    end_line: 338
  - path: "doc/prd-0.1.md"
    line: 322
    end_line: 322
  - path: "doc/prd-0.1.md"
    line: 506
    end_line: 506
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
