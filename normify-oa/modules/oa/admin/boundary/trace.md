---
uid: c0e8b612
id: oa.admin.boundary.trace
parent: oa.admin.boundary
state: planned
name: {zh: "管理员操作留痕", en: "Admin Action Trail"}
description:
  zh: >
      把管理员的每一次操作（操作人、时间、IP、对象、变更前后值）写入只追加的审计日志，并提供审计查询与 CSV 导出。
      
  en: >
      Records every administrator action with actor, timestamp, IP, target and before/after values in the append-only audit log, and exposes a query and CSV export for auditors.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.281Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/admin-actions"
    description:
      zh: >
          查询管理员操作留痕（谁/何时/何 IP/对何对象）。
          
      en: >
          Query administrator actions (who/when/from where/on what).
          
  - protocol: file
    path: "export/admin-actions.csv"
    description:
      zh: >
          导出管理员操作留痕供审计。
          
      en: >
          Export administrator action trail for audit.
          
deps:
  - kind: call
    to: oa.audit.oplog
    label: {zh: "操作日志留痕", en: "Operation log trail"}
  - kind: dataflow
    to: oa.audit.oplog.capture
    to_api: "mysql:sys_log"
    label: {zh: "写入操作日志", en: "Write operation log"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-006`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
