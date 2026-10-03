---
uid: 4b97e5c4
id: oa.integration.warehouse.extract.partition
parent: oa.integration.warehouse.extract
name: {zh: "月分区落盘", en: "Partitioned Output"}
description:
  zh: >
      把抽取结果按年/月分区落盘，便于数仓增量加载与审计按期间定位。
      
  en: >
      Writes extracted documents into year/month partitions so the warehouse can load incrementally and audits can locate a period.
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.359Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: file
    path: "export/warehouse/{yyyy}/{mm}/documents.csv"
    description:
      zh: >
          抽取任务写出的月分区文件。
          
      en: >
          Monthly partition file written by the extraction job.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
