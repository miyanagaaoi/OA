---
uid: 2c2427cb
id: oa.workflow.approver.rule-table.manual
parent: oa.workflow.approver.rule-table
state: planned
name: {zh: "手工与协同规则", en: "Manual & Collaboration Rules"}
description:
  zh: >
      指定人员/角色（IT 在流程设计器中固定指定）、发起人自选（发起时从通讯录选择）、协同部门（②节点审批人审批时勾选，每个被勾选部门取其负责人，多组独立会签且全部完成后才进入下一节点）三条规则的解析（REQ-FLOW-005）。
      
  en: >
      Resolves designated person/role (fixed by IT in the designer), initiator pick (chosen from the directory at submission) and collaborating departments (hooked by node ②'s approver at approval time; each department leader forms an independent countersign group and all groups must finish before moving on) (REQ-FLOW-005).
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.433Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/designated/resolve"
    description:
      zh: >
          解析流程设计器中固定指定的用户或角色。
          
      en: >
          Resolves a fixed person or role declared in approver_param.
          
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/initiator-pick/resolve"
    description:
      zh: >
          接收发起人从通讯录选择的候选人。
          
      en: >
          Accepts candidates picked by the initiator from the directory.
          
  - protocol: http
    method: POST
    path: "/api/v1/approver-rules/collaborating-dept/resolve"
    description:
      zh: >
          解析②节点审批人勾选的协同部门负责人，每部门一组。
          
      en: >
          Resolves leaders of departments hooked by node ②'s approver, one group per department.
          
deps:
  - kind: call
    to: oa.identity.user
    label: {zh: "校验指定人员", en: "Verify designated user"}
  - kind: call
    to: oa.identity.org
    label: {zh: "取协同部门负责人", en: "Collaborating depts"}
  - kind: reference
    to: oa.authz.visibility
    from_api: "POST /api/v1/approver-rules/collaborating-dept/resolve"
    label: {zh: "协同部门可见该单", en: "Dept can see the case"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-005`（§6.4 流程引擎核心能力）
