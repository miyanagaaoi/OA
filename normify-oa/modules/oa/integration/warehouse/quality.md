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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.553Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
