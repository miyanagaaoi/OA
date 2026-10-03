---
uid: 9a54de46
id: oa.admin.report.export
parent: oa.admin.report
name: {zh: "报表导出", en: "Report Export"}
description:
  zh: >
      排队生成报表的 CSV 导出；导出仅系统管理员可用，金额字段对非财务角色不可导出。
      
  en: >
      Queues CSV exports of the reports; export is limited to the system administrator and amount fields stay non-exportable for non-finance roles.
      
revision: fb862dbb9a22f7ff0c7fedd0607eb431a9de3a50
updated_at: "2026-10-03T07:50:07.215Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
