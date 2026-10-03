---
uid: 2d3d5e46
id: oa.workflow.approver
parent: oa.workflow
state: planned
name: {zh: "审批人解析与快照", en: "Approver Resolution & Snapshot"}
description:
  zh: >
      审批人解析规则（直属部门负责人、集团财务部负责人、分公司分管领导、子公司总经理、协同部门、集团分管领导、董事长、指定人员/角色、发起人自选）在发起时一次性解析并固化为快照；候选人集合为空则禁止发起并提示具体节点。
      
  en: >
      Approver resolution (direct leader, group Finance owner, subsidiary leader, GM, collaborating departments picked at approval time, group line leader, chairman, fixed person/role, initiator choice) resolved once at submission into an immutable snapshot; empty candidate sets block submission.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.673Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-011`（§6.4 流程引擎核心能力）
