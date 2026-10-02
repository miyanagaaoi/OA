---
uid: 0c4490c1
id: oa.identity.user.import
parent: oa.identity.user
state: planned
name: {zh: "人员批量导入导出", en: "User Bulk Import"}
description:
  zh: >
      人员 Excel 批量导入与导出（含组织归属与岗位列）：先校验账号唯一、组织存在与必填字段，预览冲突后由管理员确认执行。
      
  en: >
      Excel bulk import and export of users including org and post columns: account uniqueness, org existence and required fields are validated first, conflicts are previewed and an admin confirms execution.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.692Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 433
    end_line: 440
  - path: "doc/prd-0.1.md"
    line: 234
    end_line: 244
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/users/import/preview"
    description:
      zh: >
          解析导入文件并返回冲突清单。
          
      en: >
          Parses the upload and returns conflicts.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/users/import"
    description:
      zh: >
          确认后批量写入人员。
          
      en: >
          Bulk-creates users after confirmation.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/users/export"
    description:
      zh: >
          导出人员列表。
          
      en: >
          Exports the user list.
          
deps:
  - kind: call
    to: oa.identity.user.profile
    from_api: "POST /api/v1/identity/users/import"
    to_api: "POST /api/v1/identity/users"
    label: {zh: "批量落库人员", en: "Persist imported users"}
  - kind: call
    to: oa.identity.position.multi-post
    from_api: "POST /api/v1/identity/users/import"
    to_api: "POST /api/v1/identity/users/{id}/positions"
    label: {zh: "写入一人多岗归属", en: "Write multi-post records"}
---
