---
uid: 0c4490c1
id: oa.identity.user.import
parent: oa.identity.user
name: {zh: "人员批量导入导出", en: "User Bulk Import"}
description:
  zh: >
      人员 Excel 批量导入与导出（含组织归属与岗位列）：先校验账号唯一、组织存在与必填字段，预览冲突后由管理员确认执行。
      
  en: >
      Excel bulk import and export of users including org and post columns: account uniqueness, org existence and required fields are validated first, conflicts are previewed and an admin confirms execution.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.544Z"
fingerprint: 0d926276edc133b5faf063ca8b494521d02f00a762f9dc601c276b71886ffba9
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/strategy/UserImportStrategy.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/api/BulkImportController.java"
    line: 104
    end_line: 116
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
  - kind: call
    to: oa.admin.org.bulk.engine
    from_api: "POST /api/v1/identity/users/import"
    to_api: "rpc:bulk.pipeline.commit"
    label: {zh: "复用导入引擎（校验/落库/锁）", en: "Reuse the bulk-import engine"}
---
