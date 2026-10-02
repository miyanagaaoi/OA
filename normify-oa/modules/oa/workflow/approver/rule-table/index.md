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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.748Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 204
    end_line: 216
deps:
  - kind: reference
    to: oa.workflow.definition.approver-rule
    label: {zh: "规则编码来自节点声明", en: "Rule codes come from nodes"}
---
