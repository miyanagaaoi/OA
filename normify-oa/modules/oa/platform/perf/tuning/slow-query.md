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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.766Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
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
