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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.702Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
