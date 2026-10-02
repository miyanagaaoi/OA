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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.633Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
