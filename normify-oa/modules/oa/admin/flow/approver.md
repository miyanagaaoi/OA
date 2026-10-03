---
uid: 39aaceda
id: oa.admin.flow.approver
parent: oa.admin.flow
state: planned
name: {zh: "审批人解析规则", en: "Approver Resolution Rules"}
description:
  zh: >
      配置每个节点的解析规则（直属部门负责人、集团归口部门、子公司总经理、指定人员/角色、发起人自选），并试算候选人集合，在候选人集合为空时提前预警。
      
  en: >
      Configures the resolution rule for each node (department leader, group owner department, subsidiary GM, designated user or role, initiator pick) and previews the candidate set so an empty set is caught before go-live.
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:30:28.593Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 436
    end_line: 436
  - path: "doc/prd-0.1.md"
    line: 204
    end_line: 216
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/approver-rules"
    description:
      zh: >
          查询各节点的审批人解析规则。
          
      en: >
          List the approver resolution rules per node code.
          
  - protocol: http
    method: PUT
    path: "/api/v1/admin/approver-rules/{node_code}"
    description:
      zh: >
          设置解析规则与参数（指定人员或角色）。
          
      en: >
          Set a rule and its params (users or role code).
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/approver-rules/preview"
    description:
      zh: >
          试算指定组织的候选人集合（空集预警）。
          
      en: >
          Simulate the candidate set for an org before go-live.
          
deps:
  - kind: call
    to: oa.workflow.approver
    from_api: "PUT /api/v1/admin/approver-rules/{node_code}"
    label: {zh: "解析规则由审批人解析执行", en: "Rules run in approver"}
  - kind: reference
    to: oa.admin.org.tree.leader
    from_api: "POST /api/v1/admin/approver-rules/preview"
    to_api: "GET /api/v1/admin/orgs/{org_id}/leaders"
    label: {zh: "候选人取自组织负责人", en: "Leaders supply candidates"}
---
