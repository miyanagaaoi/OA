---
uid: 18e089e0
id: oa.workflow.definition.approver-rule
parent: oa.workflow.definition
state: planned
name: {zh: "审批人解析规则声明", en: "Approver Rule Declaration"}
description:
  zh: >
      节点上 approver_rule 与 approver_param 的声明与校验：dept_leader / department_leader / finance_leader / company_exec / gm / group_dept_leader / group_dept / group_exec / chairman / designated / initiator_pick；designated 需给出 user_ids 或 role_code。此处只声明规则编码，实际解析由审批人解析模块在发起时执行（REQ-FLOW-011）。
      
  en: >
      Declares and validates the node's approver_rule and approver_param: dept_leader/department_leader/finance_leader/company_exec/gm/group_dept_leader/group_dept/group_exec/chairman/designated/initiator_pick; designated requires user_ids or role_code. Only the rule code is declared here — resolution runs at submission in the approver module (REQ-FLOW-011).
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.692Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/data-model.md"
    line: 307
    end_line: 308
  - path: "doc/prd-0.1.md"
    line: 204
    end_line: 216
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-nodes/{node_id}/approver-rule"
    description:
      zh: >
          读取节点的审批人解析规则与参数。
          
      en: >
          Reads the node approver rule and its parameters.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-nodes/{node_id}/approver-rule"
    description:
      zh: >
          写入审批人解析规则编码与参数。
          
      en: >
          Writes the approver rule code and parameters.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-nodes/{node_id}/approver-rule/validate"
    description:
      zh: >
          校验规则编码、designated 参数与 role_code 存在性。
          
      en: >
          Validates the rule code, designated parameters and role_code existence.
          
deps:
  - kind: reference
    to: oa.workflow.definition.node-schema
    from_api: "PUT /api/v1/flow-nodes/{node_id}/approver-rule"
    to_api: "GET /api/v1/flow-templates/{template_id}/nodes"
    label: {zh: "规则挂在节点上", en: "Rule hangs off node"}
  - kind: call
    to: oa.identity.org
    from_api: "POST /api/v1/flow-nodes/{node_id}/approver-rule/validate"
    label: {zh: "校验角色/组织编码", en: "Verify role/org code exists"}
---
