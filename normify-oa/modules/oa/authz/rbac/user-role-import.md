---
uid: 9c42f0d8
id: oa.authz.rbac.user-role-import
parent: oa.authz.rbac
name: {zh: "角色分配批量导入导出", en: "User-Role Bulk Import & Export"}
description:
  zh: >
      角色分配模板（user_role.csv）的预检与执行：role_code 必须命中已初始化角色集（E-ROLE-001，导入不创建角色），(user_account, role_code, scope_org_path) 不得重复（E-ROLE-003，与库唯一键 uk_sys_user_role(user_id, role_id, scope_org_key) 完全一致，空数据域按 0 归一），并受 E-ROLE-020 数据域闸门约束；确认后单事务幂等 upsert sys_user_role；同列口径导出（仅系统管理员）。
      
  en: >
      Preview and commit for the user_role.csv template: role_code must hit the initialised role set (E-ROLE-001; import never creates roles), (user_account, role_code, scope_org_path) must be unique (E-ROLE-003, exactly matching uk_sys_user_role(user_id, role_id, scope_org_key), empty scope normalised to 0) and the E-ROLE-020 data-scope gate applies; then idempotently upsert sys_user_role in one transaction; a matching export (system admin only) is provided.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.555Z"
fingerprint: 88327b4b36a96e6763bfd037d931545e5ebbdd51674512ea9fb681dbfebacef6
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/strategy/UserRoleImportStrategy.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/api/BulkImportController.java"
    line: 152
    end_line: 164
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/user-roles/import/preview"
    description:
      zh: >
          角色分配模板预检（dry-run，不落库）。
          
      en: >
          Dry-run preview of the user-role template.
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/user-roles/import"
    description:
      zh: >
          确认后执行角色分配批量导入（单事务）。
          
      en: >
          Commits the user-role import in one transaction.
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/user-roles/export"
    description:
      zh: >
          导出角色分配 CSV（仅系统管理员）。
          
      en: >
          Exports user-role assignments as CSV (system admin only).
          
deps:
  - kind: call
    to: oa.admin.org.bulk.engine
    from_api: "POST /api/v1/identity/user-roles/import"
    to_api: "rpc:bulk.pipeline.commit"
    label: {zh: "复用导入引擎（校验/落库/锁）", en: "Reuse the bulk-import engine"}
---
