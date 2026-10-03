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
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.747Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
