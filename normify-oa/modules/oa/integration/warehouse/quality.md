---
uid: 6238ffef
id: oa.integration.warehouse.quality
parent: oa.integration.warehouse
name: {zh: "数据质量与对账", en: "Extraction Reconciliation"}
description:
  zh: >
      每次抽取后对源单据与目标行数对账，在脏数据进入数仓前暴露缺口或重复主键。
      
  en: >
      Reconciliation between source documents and extracted rows per run, surfacing gaps or duplicated keys before they reach the warehouse.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.082Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
