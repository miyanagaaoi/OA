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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.720Z"
fingerprint: 4421092e639ef98e9cd4aac53b5975054268b604256d140e3544a8a3c2ba5551
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
