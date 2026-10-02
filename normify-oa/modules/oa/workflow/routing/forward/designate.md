---
uid: 2b0a407d
id: oa.workflow.routing.forward.designate
parent: oa.workflow.routing.forward
state: planned
name: {zh: "指定承接部门", en: "Designate Receiving Department"}
description:
  zh: >
      流转动作的受理入口：审批人从可选部门中指定下一个承接部门，必须填写流转原因；候选部门按「流转可见性」过滤（承接部门对本案可见），且排除已处理过的部门；通过后写 flow_routing（action_type=route）并更新实例当前承接部门。
      
  en: >
      Entry point of the routing action: the approver picks the next receiving department from the eligible set and must give a routing reason. Candidates are filtered by routing visibility (the department must be able to see the document) and by the departments already handled; on success action_type=route is written and the current department updated.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.761Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
source:
  - path: "doc/prd-0.1.md"
    line: 322
    end_line: 322
  - path: "doc/prd-0.1.md"
    line: 357
    end_line: 357
  - path: "doc/data-model.md"
    line: 478
    end_line: 498
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/route"
    description:
      zh: >
          指定下一承接部门并流转（原因必填）。
          
      en: >
          Designate the next receiving department with a mandatory reason.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/route-candidates"
    description:
      zh: >
          可承接部门候选（排除已处理过的部门）。
          
      en: >
          Candidate receiving departments excluding handled ones.
          
  - protocol: mysql
    path: "flow_routing"
    description:
      zh: >
          集团层流转链记录（流转/回退上一节点/回到本部门）。
          
      en: >
          Group routing chain rows: route, rollback and back-home.
          
deps:
  - kind: call
    to: oa.authz.visibility
    from_api: "GET /api/v1/flow/instances/{instance_id}/route-candidates"
    label: {zh: "承接部门可见性过滤", en: "Filter by document visibility"}
  - kind: reference
    to: oa.identity.org
    from_api: "GET /api/v1/flow/instances/{instance_id}/route-candidates"
    label: {zh: "部门主数据", en: "Department master data"}
  - kind: call
    to: oa.workflow.routing.gate.quota
    from_api: "POST /api/v1/flow/instances/{instance_id}/route"
    to_api: "POST /api/v1/flow/instances/{instance_id}/routing-quota/check"
    label: {zh: "流转前过次数闸门", en: "Enforce routing quota gate"}
---
