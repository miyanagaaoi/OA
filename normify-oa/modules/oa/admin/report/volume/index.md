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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.140Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
