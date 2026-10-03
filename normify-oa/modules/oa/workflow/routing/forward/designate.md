---
uid: 2b0a407d
id: oa.workflow.routing.forward.designate
parent: oa.workflow.routing.forward
name: {zh: "指定承接部门", en: "Designate Receiving Department"}
description:
  zh: >
      流转动作的受理入口：审批人从可选部门中指定下一个承接部门，必须填写流转原因；候选部门按「流转可见性」过滤（承接部门对本案可见），且排除已处理过的部门；通过后写 flow_routing（action_type=route）并更新实例当前承接部门。
      
  en: >
      Entry point of the routing action: the approver picks the next receiving department from the eligible set and must give a routing reason. Candidates are filtered by routing visibility (the department must be able to see the document) and by the departments already handled; on success action_type=route is written and the current department updated.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.152Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-020`（§6.3.1 集团层流转机制）
- `doc/data-model.md` → `CREATE TABLE flow_routing`（§5. 流程运行时）
