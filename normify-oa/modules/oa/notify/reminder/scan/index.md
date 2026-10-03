---
uid: 8148cc00
id: oa.notify.reminder.scan
parent: oa.notify.reminder
name: {zh: "超时扫描", en: "Overdue Scanning"}
description:
  zh: >
      超时扫描：审批节点超时（flow_task 停留超过节点配置的 timeout_hours，须显式配置且 ≥24h）与补件时限超时（flow_supplement.deadline，默认 3 个工作日）两类；只产出催办信号，不自动跳过、不自动升级、不自动驳回。
      
  en: >
      Overdue scanning for stalled approval tasks (per-node timeout_hours, explicitly configured and ≥24h) and supplement deadlines (default 3 working days); it only emits reminder signals and never auto-skips, escalates or rejects.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.368Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
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
