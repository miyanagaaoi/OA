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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.138Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
