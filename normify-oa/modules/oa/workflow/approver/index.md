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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.418Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-011`（§6.4 流程引擎核心能力）
