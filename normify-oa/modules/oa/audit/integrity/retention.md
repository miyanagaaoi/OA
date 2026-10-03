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
      
revision: e3b34a3c59417096ade647fab4261b06f6b605e2
updated_at: "2026-10-03T02:49:57.378Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 557
    end_line: 557
  - path: "doc/data-model.md"
    line: 925
    end_line: 925
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
