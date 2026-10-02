---
uid: 4b97e5c4
id: oa.integration.warehouse.extract.partition
parent: oa.integration.warehouse.extract
state: planned
name: {zh: "月分区落盘", en: "Partitioned Output"}
description:
  zh: >
      把抽取结果按年/月分区落盘，便于数仓增量加载与审计按期间定位。
      
  en: >
      Writes extracted documents into year/month partitions so the warehouse can load incrementally and audits can locate a period.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.749Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 515
    end_line: 515
apis:
  - protocol: file
    path: "export/warehouse/{yyyy}/{mm}/documents.csv"
    description:
      zh: >
          抽取任务写出的月分区文件。
          
      en: >
          Monthly partition file written by the extraction job.
          
---
