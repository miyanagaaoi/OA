---
uid: 7d1a4c92
id: oa.identity.position.leader-import
parent: oa.identity.position
name: {zh: "负责人批量导入导出", en: "Org-Leader Bulk Import & Export"}
description:
  zh: >
      组织负责人模板（org_leader.csv）的预检与执行：逐行解析 (org_path, user_account, leader_type, business_line)，先跑全量校验（含 E-LEAD-020 数据域闸门与「同组织同业务线至多一个正职」），确认后在单事务内幂等 upsert sys_org_leader；并提供同列口径的导出（仅系统管理员，用于往返维护）。
      
  en: >
      Preview and commit for the org-leader template (org_leader.csv): parse (org_path, user_account, leader_type, business_line), run the full validation pass (E-LEAD-020 data-scope gate plus the one-primary-per-org-and-line rule), then idempotently upsert sys_org_leader in one transaction; a matching export (system admin only) supports round-trip maintenance.
      
revision: 966907fad0c5f0d01bc6a76ddba80bbbf67f586a
updated_at: "2026-10-03T04:41:37.370Z"
fingerprint: 7256282b6e6fbf115d51815fc8594f204a04670751c5590947254f4bf3727f36
source:
  - path: "oa-server/src/main/java/com/oa/admin/bulk/strategy/OrgLeaderImportStrategy.java"
  - path: "oa-server/src/main/java/com/oa/admin/bulk/api/BulkImportController.java"
    line: 120
    end_line: 132
apis:
  - protocol: http
    method: POST
    path: "/api/v1/identity/org-leaders/import/preview"
    description:
      zh: >
          负责人模板预检（dry-run，不落库；返回逐行错误码与整批判定）。
          
      en: >
          Dry-run preview of the org-leader template (no writes; returns per-row codes and the batch verdict).
          
  - protocol: http
    method: POST
    path: "/api/v1/identity/org-leaders/import"
    description:
      zh: >
          确认后执行负责人批量导入（单事务；错误零落库）。
          
      en: >
          Commits the org-leader import in one transaction (zero rows on error).
          
  - protocol: http
    method: GET
    path: "/api/v1/identity/org-leaders/export"
    description:
      zh: >
          导出负责人模板同列口径的 CSV（仅系统管理员）。
          
      en: >
          Exports org leaders as a template-aligned CSV (system admin only).
          
deps:
  - kind: call
    to: oa.admin.org.bulk.engine
    from_api: "POST /api/v1/identity/org-leaders/import"
    to_api: "rpc:bulk.pipeline.commit"
    label: {zh: "复用导入引擎（校验/落库/锁）", en: "Reuse the bulk-import engine"}
---
