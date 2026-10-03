---
uid: 5f0e2e03
id: oa.integration.warehouse.extract
parent: oa.integration.warehouse
state: planned
name: {zh: "抽取任务与调度", en: "Extraction Jobs"}
description:
  zh: >
      定时抽取任务：把已完结单据发布为按月分区的文件，并发出抽取完成事件供下游消费。
      
  en: >
      Scheduled extraction jobs that publish finished documents into per-month files and emit an extracted event for downstream consumers.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.391Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
