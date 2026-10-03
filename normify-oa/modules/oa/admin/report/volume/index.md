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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.438Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
