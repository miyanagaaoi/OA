---
uid: 83a0964e
id: oa.admin.report.volume
parent: oa.admin.report
state: planned
name: {zh: "流程量与耗时", en: "Volume & Duration"}
description:
  zh: >
      基于流程实例与任务的一期报表（P1）：流程量、平均耗时、超时率、审批人效率、驳回率与驳回原因分布。
      
  en: >
      Phase-one reports (P1) built from flow instances and tasks: process volume, average duration, timeout rate, approver efficiency, rejection rate and rejection-reason distribution.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.286Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
