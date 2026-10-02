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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.622Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 440
    end_line: 440
  - path: "doc/data-model.md"
    line: 647
    end_line: 663
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
