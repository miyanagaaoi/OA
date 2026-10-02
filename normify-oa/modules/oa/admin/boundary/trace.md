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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.660Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
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
