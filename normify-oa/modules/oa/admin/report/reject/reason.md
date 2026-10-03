---
uid: b60f8ec7
id: oa.admin.report.reject.reason
parent: oa.admin.report.reject
state: planned
name: {zh: "驳回原因分布", en: "Rejection Reason Mix"}
description:
  zh: >
      把审批意见中的驳回原因聚合为分布，使反复出现的驳回原因可以源头修复。
      
  en: >
      Aggregates rejection reasons captured in approval opinions into a distribution, so recurring rejection causes can be fixed at the source.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.545Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
  - path: "doc/prd-0.1.md"
    line: 427
    end_line: 427
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/reject-reasons"
    description:
      zh: >
          驳回原因分布（按原因分类聚合）。
          
      en: >
          Distribution of rejection reasons across categories.
          
  - protocol: file
    path: "export/reports/reject-reasons.csv"
    description:
      zh: >
          驳回原因分布导出件。
          
      en: >
          Exported rejection-reason distribution.
          
deps:
  - kind: reference
    to: oa.audit.trace
    label: {zh: "驳回意见取自审批轨迹", en: "Rejection opinions in trace"}
---
