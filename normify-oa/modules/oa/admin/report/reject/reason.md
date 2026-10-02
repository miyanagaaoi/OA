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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.674Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
