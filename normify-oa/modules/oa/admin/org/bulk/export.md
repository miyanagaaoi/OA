---
uid: 36b178c6
id: oa.admin.org.bulk.export
parent: oa.admin.org.bulk
state: planned
name: {zh: "人员与组织导出", en: "User & Org Export"}
description:
  zh: >
      导出组织架构与人员清单，导出功能仅系统管理员可用；导出内容不含完整手机号，仅管理员可见完整值，导出动作留痕。
      
  en: >
      Export org and user listings; export is available to the system administrator only, omits full mobile numbers, and is written to the audit trail.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.603Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 435
    end_line: 435
  - path: "doc/prd-0.1.md"
    line: 195
    end_line: 196
  - path: "doc/prd-0.1.md"
    line: 561
    end_line: 561
apis:
  - protocol: file
    path: "export/orgs.xlsx"
    description:
      zh: >
          组织架构导出文件。
          
      en: >
          Exported org structure file.
          
  - protocol: file
    path: "export/users.xlsx"
    description:
      zh: >
          人员清单导出文件（手机号脱敏）。
          
      en: >
          Exported user listing with masked phone numbers.
          
  - protocol: http
    method: POST
    path: "/api/v1/admin/org-exports"
    description:
      zh: >
          创建导出任务（仅系统管理员）。
          
      en: >
          Create an export job (system administrator only).
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/org-exports/{export_id}"
    description:
      zh: >
          查询导出任务状态与下载链接。
          
      en: >
          Fetch export job status and download link.
          
deps:
  - kind: call
    to: oa.admin.boundary
    from_api: "POST /api/v1/admin/org-exports"
    label: {zh: "导出权限边界校验", en: "Export privilege check"}
---
