---
uid: 1f956a6c
id: oa.workflow.approver.rule-table
parent: oa.workflow.approver
state: planned
name: {zh: "解析规则表", en: "Resolution Rule Table"}
description:
  zh: >
      PRD 5.4 的九条审批人解析规则实现：组织链派生（直属部门负责人、分公司分管领导、子公司总经理）、财务部归口（恒取集团财务部负责人）、集团层（按事项类别匹配集团分管领导、取集团董事长）、手工指定（指定人员/角色、发起人自选、协同部门勾选）。
      
  en: >
      Implementation of the nine approver rules in PRD 5.4: org-chain derivation (direct department leader, branch line leader, subsidiary GM), Finance ownership (always the group Finance owner), group layer (line leader by category, chairman) and manual rules (designated person/role, initiator pick, collaborating departments picked at approval time).
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.764Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 204
    end_line: 216
deps:
  - kind: reference
    to: oa.workflow.definition.approver-rule
    label: {zh: "规则编码来自节点声明", en: "Rule codes come from nodes"}
---
