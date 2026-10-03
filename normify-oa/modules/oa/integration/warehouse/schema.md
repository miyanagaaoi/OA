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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.749Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 515
    end_line: 515
  - path: "doc/data-model.md"
    line: 818
    end_line: 831
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
