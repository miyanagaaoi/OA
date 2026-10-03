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
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.369Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 534
    end_line: 534
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
