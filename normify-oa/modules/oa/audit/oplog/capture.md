---
uid: 01690f2c
id: oa.audit.oplog.capture
parent: oa.audit.oplog
name: {zh: "操作日志写入", en: "Operation Log Writer"}
description:
  zh: >
      操作日志统一采集入口：记录操作人、时间、来源 IP 与 User-Agent、目标对象类型与 ID、动作编码，以只追加方式写入 sys_log，不做更新与删除。
      
  en: >
      Single ingestion entry for the operation log: records actor, timestamp, source IP and user agent, target type and ID, and action code, appending only to sys_log with no update or delete.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.940Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-LOG-001`（§6.9 审计日志）
- `doc/data-model.md` → `CREATE TABLE sys_log`（§6. 签名、附件、抄送、消息、审计）
