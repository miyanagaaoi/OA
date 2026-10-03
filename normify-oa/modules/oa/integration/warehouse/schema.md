---
uid: 55a8023d
id: oa.integration.warehouse.schema
parent: oa.integration.warehouse
state: planned
name: {zh: "标准化归档结构", en: "Archive Schema"}
description:
  zh: >
      版本化的标准化归档结构，让数据仓库在跨版本时仍能消费稳定的字段语义。
      
  en: >
      A versioned, standardised archive schema for finished documents so the data warehouse can consume stable field semantics across releases.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.553Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/warehouse/schema"
    description:
      zh: >
          查询归档结构版本与字段定义。
          
      en: >
          Returns the archive schema version and field definitions.
          
  - protocol: file
    path: "export/warehouse/schema/{version}.json"
    description:
      zh: >
          按版本冻结的归档结构定义。
          
      en: >
          Versioned archive schema definition.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
