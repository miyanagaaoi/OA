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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.255Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
