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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.760Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 394
    end_line: 394
  - path: "doc/prd-0.1.md"
    line: 398
    end_line: 399
  - path: "doc/data-model.md"
    line: 519
    end_line: 520
deps:
  - kind: reference
    to: oa.workflow.exception
    label: {zh: "超时异常路径规则", en: "Timeout exception rules"}
---
