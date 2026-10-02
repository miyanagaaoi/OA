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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.741Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
