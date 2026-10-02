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
      
revision: 995f830121c4ff56f7e42231c23bd7e008a484bd
updated_at: "2026-10-02T10:53:53.632Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
  - path: "doc/prd-0.1.md"
    line: 561
    end_line: 561
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
