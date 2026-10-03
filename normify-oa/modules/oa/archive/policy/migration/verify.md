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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.665Z"
fingerprint: 3b00610613fe0f45aad673a1508d23c3d3cd2c88a03dfe3d41047751592de232
source:
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
