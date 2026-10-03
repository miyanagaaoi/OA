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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.143Z"
fingerprint: 0f754e6e48eb41faa517ea45e35f49a012f228e36b05c082501dd8c8a7c05aae
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
