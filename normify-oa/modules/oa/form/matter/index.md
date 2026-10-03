---
uid: 3e4c6f56
id: oa.form.matter
parent: oa.form
name: {zh: "事项审批单", en: "Matter Approval Form"}
description:
  zh: >
      事项审批单（form_type=matter）：发起人选择事项类别（配置项，仅作分类标签不参与路由）；四类单据中唯一带「是否涉及费用」判断者，决定节点②财务部复核是否跳过。
      
  en: >
      Matter approval form (form_type=matter): the initiator picks a configurable matter category which never reroutes the document, and this is the only type carrying the involves-cost flag that decides whether the Finance node is skipped.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.349Z"
fingerprint: 2e5e7385be861a09ce95e0c6292674210e79f65c6ff5d78935219e0bf915ca82
source:
  - path: "doc/forms.md"
  - path: "oa-server/src/main/java/com/oa/form/matter/MatterFormRules.java"
  - path: "oa-server/src/main/java/com/oa/form/api/FormRuleController.java"
---

## 证据锚点
- `doc/forms.md` → `## 2. 事项审批单（`form_type = matter`）`（§2. 事项审批单）
