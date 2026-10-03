---
uid: 6238ffef
id: oa.integration.warehouse.quality
parent: oa.integration.warehouse
state: planned
name: {zh: "数据质量与对账", en: "Extraction Reconciliation"}
description:
  zh: >
      每次抽取后对源单据与目标行数对账，在脏数据进入数仓前暴露缺口或重复主键。
      
  en: >
      Reconciliation between source documents and extracted rows per run, surfacing gaps or duplicated keys before they reach the warehouse.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.618Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 515
    end_line: 515
apis:
  - protocol: http
    method: GET
    path: "/api/v1/warehouse/reconciliation"
    description:
      zh: >
          查询抽取对账结果。
          
      en: >
          Reads extraction reconciliation results.
          
  - protocol: file
    path: "reports/warehouse/reconciliation-{date}.csv"
    description:
      zh: >
          对账明细文件。
          
      en: >
          Reconciliation detail file.
          
---
