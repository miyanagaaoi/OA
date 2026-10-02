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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.778Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
