---
uid: 9a54de46
id: oa.admin.report.export
parent: oa.admin.report
state: planned
name: {zh: "报表导出", en: "Report Export"}
description:
  zh: >
      排队生成报表的 CSV 导出；导出仅系统管理员可用，金额字段对非财务角色不可导出。
      
  en: >
      Queues CSV exports of the reports; export is limited to the system administrator and amount fields stay non-exportable for non-finance roles.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.137Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 581
    end_line: 581
  - path: "doc/prd-0.1.md"
    line: 195
    end_line: 195
apis:
  - protocol: file
    path: "export/reports/flow-volume.csv"
    description:
      zh: >
          流程量报表导出件。
          
      en: >
          Exported process-volume report.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/reports/exports"
    description:
      zh: >
          创建报表导出任务（仅系统管理员）。
          
      en: >
          Queue a report export (system administrator only).
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/exports/{export_id}"
    description:
      zh: >
          查询报表导出状态与下载链接。
          
      en: >
          Fetch report export status and download link.
          
deps:
  - kind: call
    to: oa.admin.boundary
    from_api: "POST /api/v1/admin/reports/exports"
    label: {zh: "导出权限校验", en: "Export privilege check"}
---
