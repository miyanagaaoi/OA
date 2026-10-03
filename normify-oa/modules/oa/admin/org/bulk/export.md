---
uid: 36b178c6
id: oa.admin.org.bulk.export
parent: oa.admin.org.bulk
name: {zh: "导出引擎与审计日志导出", en: "Export Engine & Audit-log Export"}
description:
  zh: >
      主数据导出与审计日志导出共用的 CSV 导出引擎：UTF-8 BOM + RFC4180 的表头/行渲染、名称业务路径解析、审计 JSON 内金额键递归剔除；主数据导出仅系统管理员可用且写审计日志。
      
  en: >
      CSV export engine shared by the master-data exports plus the audit-log export: header and row rendering with UTF-8 BOM and RFC4180 escaping, business-path resolution and recursive redaction of amount keys inside audit JSON; master-data exports are system-admin only and write an audit entry.
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.124Z"
fingerprint: 2e7b8a7d1c7885b6b85af1520475aad562015db5c8cf18a70a6f742bcea60d5d
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/BulkExportService.java"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/audit-logs/export"
    description:
      zh: >
          导出审计日志 CSV（金额键递归剔除；仅系统管理员）。
          
      en: >
          Exports the append-only audit log as CSV (amount keys redacted).
          
  - protocol: rpc
    path: "bulk.export.csv"
    description:
      zh: >
          按 UTF-8 BOM + RFC4180 转义渲染 CSV 行（导出物即维护载体）。
          
      en: >
          Renders CSV rows with UTF-8 BOM and RFC4180 escaping.
          
deps:
  - kind: call
    to: oa.authz.visibility.export
    from_api: "rpc:bulk.export.csv"
    to_api: "GET /api/v1/authz/export-policy"
    label: {zh: "按导出策略裁决生效列", en: "Apply export column policy"}
---
