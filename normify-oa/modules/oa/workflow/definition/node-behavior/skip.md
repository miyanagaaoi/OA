---
uid: 11022ca9
id: oa.workflow.definition.node-behavior.skip
parent: oa.workflow.definition.node-behavior
state: planned
name: {zh: "跳过条件与一期分支", en: "Skip Condition & Phase-1 Branch"}
description:
  zh: >
      skip_condition JSON 的写入与解析（如 {"field":"involve_cost","op":"eq","value":false}）：一期唯一的“分支”是事项审批单的「是否涉及费用」——不涉及则节点②财务部复核标记为已跳过，归口部门仍记为财务部（REQ-FLOW-001）。
      
  en: >
      Writes and resolves skip_condition JSON (for example {"field":"involve_cost","op":"eq","value":false}): the only phase-1 branch is a matter form's involve-cost flag — when false node ② (Finance review) is marked skipped while the owning department is still recorded as Finance (REQ-FLOW-001).
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.426Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 264
    end_line: 264
  - path: "doc/prd-0.1.md"
    line: 307
    end_line: 308
  - path: "doc/data-model.md"
    line: 316
    end_line: 316
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-nodes/{node_id}/skip-condition"
    description:
      zh: >
          读取节点跳过条件。
          
      en: >
          Reads the node skip condition.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-nodes/{node_id}/skip-condition"
    description:
      zh: >
          写入跳过条件（字段/操作符/值）。
          
      en: >
          Writes the skip condition (field/operator/value).
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-nodes/{node_id}/skip-condition/validate"
    description:
      zh: >
          校验字段存在性与操作符白名单，拒绝二期条件表达式。
          
      en: >
          Validates field existence and the operator whitelist, rejecting phase-2 expressions.
          
deps:
  - kind: reference
    to: oa.workflow.definition.node-schema
    from_api: "PUT /api/v1/flow-nodes/{node_id}/skip-condition"
    to_api: "PUT /api/v1/flow-nodes/{node_id}"
    label: {zh: "跳过条件属于节点", en: "Skip condition on node"}
  - kind: call
    to: oa.form.template
    from_api: "POST /api/v1/flow-nodes/{node_id}/skip-condition/validate"
    label: {zh: "校验字段在表单 schema", en: "Verify field in form schema"}
---
