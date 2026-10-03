---
uid: d6418b51
id: oa.platform.perf.tuning.slow-query
parent: oa.platform.perf.tuning
state: planned
name: {zh: "慢查询复核", en: "Slow Query Review"}
description:
  zh: >
      定期慢查询复核，归档日志并在响应预算未达标时记录整改结论。
      
  en: >
      Periodic slow-query review with archived logs and remediation notes when the latency budget is missed.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.404Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/db/slow-queries"
    description:
      zh: >
          查询慢查询清单与索引建议。
          
      en: >
          Reads the slow-query list with index suggestions.
          
  - protocol: file
    path: "reports/db/slow-query-{date}.log"
    description:
      zh: >
          按日归档的慢查询日志。
          
      en: >
          Archived slow-query log per day.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-NFR-004`（§第9章 非功能需求）
