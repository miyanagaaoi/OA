---
uid: 11022ca9
id: oa.workflow.definition.node-behavior.skip
parent: oa.workflow.definition.node-behavior
name: {zh: "跳过条件与一期分支", en: "Skip Condition & Phase-1 Branch"}
description:
  zh: >
      skip_condition JSON 的写入与解析（如 {"field":"involve_cost","op":"eq","value":false}）：一期唯一的“分支”是事项审批单的「是否涉及费用」——不涉及则节点②财务部复核标记为已跳过，归口部门仍记为财务部（REQ-FLOW-001）。
      
  en: >
      Writes and resolves skip_condition JSON (for example {"field":"involve_cost","op":"eq","value":false}): the only phase-1 branch is a matter form's involve-cost flag — when false node ② (Finance review) is marked skipped while the owning department is still recorded as Finance (REQ-FLOW-001).
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.404Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-025`（§6.3 主干审批链）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
