---
uid: 5bf574c0
id: oa.admin.authz.changelog
parent: oa.admin.authz
state: planned
name: {zh: "权限变更留痕", en: "Permission Change Log"}
description:
  zh: >
      记录角色、数据域、权限树勾选的全部变更（操作人、时间、变更前后值），并提供审计查询与 CSV 导出。
  en: >
      Records every change to roles, data scopes and permission-tree ticks with actor, timestamp and before/after values, and exposes them for audit and CSV export.
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:10:00Z"
fingerprint: pending
source:
  - path: "doc/prd-0.1.md"
    line: 167
    end_line: 167
  - path: "doc/prd-0.1.md"
    line: 429
    end_line: 429
  - path: "doc/data-model.md"
    line: 654
    end_line: 655
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/authz-changes"
    description:
      zh: >
          查询权限变更记录（含变更前后值）。
      en: >
          Query permission-change entries with before and after values.
  - protocol: http
    method: GET
    path: "/api/v1/admin/authz-changes/{change_id}"
    description:
      zh: >
          查询单条权限变更明细。
      en: >
          Fetch one permission-change entry in detail.
  - protocol: file
    path: "export/authz-changes.csv"
    description:
      zh: >
          权限变更日志导出件。
      en: >
          Exported permission-change log.
deps:
  - kind: call
    to: oa.audit.oplog
    label: {zh: "写入只追加审计日志", en: "Append to the audit log"}
---
