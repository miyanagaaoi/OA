---
uid: 8148cc00
id: oa.notify.reminder.scan
parent: oa.notify.reminder
state: planned
name: {zh: "超时扫描", en: "Overdue Scanning"}
description:
  zh: >
      超时扫描：审批节点超时（flow_task 停留超过节点配置的 timeout_hours，须显式配置且 ≥24h）与补件时限超时（flow_supplement.deadline，默认 3 个工作日）两类；只产出催办信号，不自动跳过、不自动升级、不自动驳回。
      
  en: >
      Overdue scanning for stalled approval tasks (per-node timeout_hours, explicitly configured and ≥24h) and supplement deadlines (default 3 working days); it only emits reminder signals and never auto-skips, escalates or rejects.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.384Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
deps:
  - kind: reference
    to: oa.workflow.exception
    label: {zh: "超时异常路径规则", en: "Timeout exception rules"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-007`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_node_instance`（§5. 流程运行时）
