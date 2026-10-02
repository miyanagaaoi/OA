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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.765Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 534
    end_line: 534
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
