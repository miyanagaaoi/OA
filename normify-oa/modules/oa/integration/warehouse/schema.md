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
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.386Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
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
