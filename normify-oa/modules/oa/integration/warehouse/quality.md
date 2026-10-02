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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.754Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
