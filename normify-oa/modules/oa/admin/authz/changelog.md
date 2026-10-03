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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.234Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-004`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
