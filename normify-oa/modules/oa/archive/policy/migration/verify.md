---
uid: 2b04284d
id: oa.archive.policy.migration.verify
parent: oa.archive.policy.migration
state: planned
name: {zh: "迁移结果核对", en: "Migration Verification"}
description:
  zh: >
      搬迁后核对在线库与历史库的条数、单号与哈希一致性，输出核对报告；不一致时阻断后续批次并告警。
      
  en: >
      Reconciles row counts, document numbers and hashes between the live store and the history store after migration; a mismatch blocks subsequent batches and raises an alert.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.652Z"
fingerprint: 7241121ccd161ffddfd3e9166a1a5bc6bd672932d1333daa0959b62334d40745
source:
  - path: "doc/data-model.md"
    line: 823
    end_line: 826
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/jobs/{job_id}/verify"
    description:
      zh: >
          执行搬迁后核对。
          
      en: >
          Runs post-migration reconciliation.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/jobs/{job_id}/verify"
    description:
      zh: >
          查看核对报告。
          
      en: >
          Reads the reconciliation report.
          
deps:
  - kind: call
    to: oa.audit.integrity.verify
    from_api: "POST /api/v1/archive/jobs/{job_id}/verify"
    to_api: "POST /api/v1/audit/integrity/verify"
    label: {zh: "迁移后哈希可校验", en: "Verify hashes after move"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "GET /api/v1/archive/jobs/{job_id}/verify"
    label: {zh: "不一致时人工复核", en: "Manual review on mismatch"}
---
