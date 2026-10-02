---
uid: 23ece1ca
id: oa.audit.integrity.retention
parent: oa.audit.integrity
state: planned
name: {zh: "保留期策略", en: "Retention Policy"}
description:
  zh: >
      审计日志与审批轨迹保留不少于 10 年、登录日志保留 1 年；到期数据只允许进入归档流程，一期不做物理删除，保留期调整本身必须留痕。
      
  en: >
      Audit logs and approval trails are retained ten years or more and login logs one year; expired data may only enter the archive flow, never physical deletion in phase one, and retention changes are themselves logged.
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.657Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 537
    end_line: 537
  - path: "doc/data-model.md"
    line: 828
    end_line: 828
apis:
  - protocol: http
    method: GET
    path: "/api/v1/audit/retention-policy"
    description:
      zh: >
          读取各日志类型的保留期。
          
      en: >
          Reads retention periods per log type.
          
  - protocol: http
    method: PUT
    path: "/api/v1/audit/retention-policy"
    description:
      zh: >
          调整保留期（须留痕）。
          
      en: >
          Updates retention periods (logged).
          
  - protocol: http
    method: POST
    path: "/api/v1/audit/retention-jobs"
    description:
      zh: >
          发起到期检查作业。
          
      en: >
          Starts an expiry evaluation job.
          
deps:
  - kind: call
    to: oa.archive.policy
    from_api: "POST /api/v1/audit/retention-jobs"
    label: {zh: "到期数据交归档流程", en: "Hand expired data to archiving"}
  - kind: reference
    to: oa.platform.backup
    from_api: "GET /api/v1/audit/retention-policy"
    label: {zh: "与备份保留策略协同", en: "Align with backup retention"}
  - kind: reference
    to: oa.audit.security.login
    from_api: "GET /api/v1/audit/retention-policy"
    label: {zh: "登录日志一年保留口径", en: "One-year scope for login logs"}
---
