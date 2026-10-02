---
uid: a3e902b1
id: oa.admin.report.volume.count
parent: oa.admin.report.volume
state: planned
name: {zh: "流程量统计", en: "Process Volume"}
description:
  zh: >
      按时间、公司、事项类别聚合流程量，直接使用实例表上为报表保留的公司、类别与状态列。
      
  en: >
      Aggregates process volume by period, company and matter category, using the company, category and status columns deliberately kept on the instance table for reporting.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.675Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
  - path: "doc/data-model.md"
    line: 408
    end_line: 411
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
