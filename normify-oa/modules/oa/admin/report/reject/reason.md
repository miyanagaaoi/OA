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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.434Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
