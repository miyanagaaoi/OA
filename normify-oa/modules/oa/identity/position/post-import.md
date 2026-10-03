---
uid: 5e83b7a1
id: oa.identity.position.post-import
parent: oa.identity.position
name: {zh: "岗位任职批量导入导出", en: "Post Assignment Bulk Import & Export"}
description:
  zh: >
      岗位任职模板（user_position.csv）的预检与执行：校验 (user_account, org_path) 不得重复（uk_user_org，E-POS-004）、每人至多一个主岗（E-POS-005）与 E-POS-020 数据域闸门；确认后单事务 upsert sys_user_position，并把主岗同步回 sys_user.position；同列口径导出（仅系统管理员）。
      
  en: >
      Preview and commit for the user_position.csv template: enforce unique (user_account, org_path) (uk_user_org, E-POS-004), at most one primary post per person (E-POS-005) and the E-POS-020 data-scope gate; then upsert sys_user_position in one transaction and mirror the primary post into sys_user.position; a matching export (system admin only) is provided.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.538Z"
fingerprint: c495f617ae85329f4ef3aba77f178c3a545740fd5d83bb748ae25e44dfcfce36
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/strategy/UserPositionImportStrategy.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/api/BulkImportController.java"
    line: 136
    end_line: 148
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/user-positions/import/preview"
    description:
      zh: >
          岗位任职模板预检（dry-run，不落库）。
          
      en: >
          Dry-run preview of the post-assignment template.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/user-positions/import"
    description:
      zh: >
          确认后执行岗位任职批量导入（单事务）。
          
      en: >
          Commits the post-assignment import in one transaction.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/user-positions/export"
    description:
      zh: >
          导出岗位任职 CSV（仅系统管理员）。
          
      en: >
          Exports post assignments as CSV (system admin only).
          
deps:
  - kind: call
    to: oa.admin.org.bulk.engine
    from_api: "POST /api/v1/identity/user-positions/import"
    to_api: "rpc:bulk.pipeline.commit"
    label: {zh: "复用导入引擎（校验/落库/锁）", en: "Reuse the bulk-import engine"}
---
