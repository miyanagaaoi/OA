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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.688Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
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
