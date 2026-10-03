---
uid: a3e902b1
id: oa.admin.report.volume.count
parent: oa.admin.report.volume
name: {zh: "流程量统计", en: "Process Volume"}
description:
  zh: >
      按时间、公司、事项类别聚合流程量，直接使用实例表上为报表保留的公司、类别与状态列。
      
  en: >
      Aggregates process volume by period, company and matter category, using the company, category and status columns deliberately kept on the instance table for reporting.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.915Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/flow-volume"
    description:
      zh: >
          按时间/公司/事项类别的流程量。
          
      en: >
          Process volume by period, company and category.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/flow-volume/trend"
    description:
      zh: >
          流程量趋势。
          
      en: >
          Process volume trend over the selected window.
          
deps:
  - kind: dataflow
    to: oa.workflow.runtime.instance.state
    to_api: "mysql:flow_instance"
    label: {zh: "读取流程实例", en: "Read flow instances"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
