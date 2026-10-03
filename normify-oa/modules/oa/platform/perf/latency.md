---
uid: aa654b52
id: oa.platform.perf.latency
parent: oa.platform.perf
state: planned
name: {zh: "页面与提交性能", en: "Response Latency"}
description:
  zh: >
      列表页与详情页响应 ≤2 秒（95 分位）、提交审批操作 ≤2 秒，并定期出报告。
      
  en: >
      Latency budget for list and detail pages and for approval submission, measured at the ninety-fifth percentile and reported periodically.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.263Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 554
    end_line: 554
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/perf/latency-report"
    description:
      zh: >
          查询页面与提交响应分位数据。
          
      en: >
          Reads page and submit latency percentiles.
          
  - protocol: file
    path: "reports/perf/latency-{date}.csv"
    description:
      zh: >
          按日导出的性能报告。
          
      en: >
          Daily latency report export.
          
---
