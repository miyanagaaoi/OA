---
uid: 2d3d5e48
id: oa.workflow.task
parent: oa.workflow
name: {zh: "审批任务与决议模式", en: "Tasks & Decision Modes"}
description:
  zh: >
      审批任务：会签节点同节点多条任务；同意/驳回（必填意见 ≥5 字）、决议模式（或签；会签按百分比或绝对人数阈值；依次串行）、候选人去重、转办（同数据域内可见人）、管理员改派、前/后加签。
      
  en: >
      Approval tasks including one row per approver on countersign nodes: decisions (approve/reject with a mandatory five-character opinion), decision modes (any/all with percentage or absolute threshold/sequential), deduplication, transfer, admin reassignment and add-sign before/after.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.171Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_task`（§5. 流程运行时）
