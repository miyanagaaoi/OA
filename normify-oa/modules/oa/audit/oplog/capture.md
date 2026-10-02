---
uid: 01690f2c
id: oa.audit.oplog.capture
parent: oa.audit.oplog
state: planned
name: {zh: "操作日志写入", en: "Operation Log Writer"}
description:
  zh: >
      操作日志统一采集入口：记录操作人、时间、来源 IP 与 User-Agent、目标对象类型与 ID、动作编码，以只追加方式写入 sys_log，不做更新与删除。
      
  en: >
      Single ingestion entry for the operation log: records actor, timestamp, source IP and user agent, target type and ID, and action code, appending only to sys_log with no update or delete.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.683Z"
fingerprint: 1ebf4aee4ac8648bfaec0ea0afbfecc1b3e2f5fecc0dff532be914d9e85be0f2
source:
  - path: "doc/prd-0.1.md"
    line: 426
    end_line: 426
  - path: "doc/data-model.md"
    line: 647
    end_line: 663
apis:
  - protocol: mysql
    path: "sys_log"
    description:
      zh: >
          审计日志表（只追加）。
          
      en: >
          Audit log table (append-only).
          
  - protocol: http
    method: POST
    path: "/api/v1/audit/logs"
    description:
      zh: >
          写入一条操作日志。
          
      en: >
          Appends one operation log entry.
          
deps:
  - kind: call
    to: oa.identity.session
    from_api: "POST /api/v1/audit/logs"
    label: {zh: "取操作人与来源会话信息", en: "Resolve actor and session"}
  - kind: dataflow
    to: oa.audit.oplog.query
    from_api: "POST /api/v1/audit/logs"
    to_api: "GET /api/v1/audit/logs"
    label: {zh: "写入后供检索与导出", en: "Feed query and export"}
---
