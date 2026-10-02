---
uid: b34a3f68
id: oa.admin.report.reject.rate
parent: oa.admin.report.reject
state: planned
name: {zh: "驳回率统计", en: "Rejection Rate"}
description:
  zh: >
      按节点、部门与时间窗拆分驳回率，定位驳回最集中的审核环节。
      
  en: >
      Computes the rejection rate split by node, department and time window, exposing which review step rejects most often.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.644Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/reject-rate"
    description:
      zh: >
          按节点/部门/时间的驳回率。
          
      en: >
          Rejection rate by node, department and period.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/reject-rate/trend"
    description:
      zh: >
          驳回率趋势。
          
      en: >
          Rejection rate trend over the selected window.
          
---
