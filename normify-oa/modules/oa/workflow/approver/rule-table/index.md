---
uid: 1f956a6c
id: oa.workflow.approver.rule-table
parent: oa.workflow.approver
name: {zh: "解析规则表", en: "Resolution Rule Table"}
description:
  zh: >
      PRD 5.4 的九条审批人解析规则实现：组织链派生（直属部门负责人、分公司分管领导、子公司总经理）、财务部归口（恒取集团财务部负责人）、集团层（按事项类别匹配集团分管领导、取集团董事长）、手工指定（指定人员/角色、发起人自选、协同部门勾选）。
      
  en: >
      Implementation of the nine approver rules in PRD 5.4: org-chain derivation (direct department leader, branch line leader, subsidiary GM), Finance ownership (always the group Finance owner), group layer (line leader by category, chairman) and manual rules (designated person/role, initiator pick, collaborating departments picked at approval time).
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.360Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
deps:
  - kind: reference
    to: oa.workflow.definition.approver-rule
    label: {zh: "规则编码来自节点声明", en: "Rule codes come from nodes"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-011`（§6.4 流程引擎核心能力）
