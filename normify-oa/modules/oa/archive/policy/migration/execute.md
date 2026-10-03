---
uid: 2a21dc6e
id: oa.archive.policy.migration.execute
parent: oa.archive.policy.migration
name: {zh: "整单搬迁执行", en: "Archive Migration Run"}
description:
  zh: >
      以整单为单位把实例、节点、任务、轨迹、签名、附件元数据与表单数据搬移至 _history 历史库表，单事务可回滚，附件路径保持可解析。
      
  en: >
      Moves each document as a unit - instance, nodes, tasks, trail, signatures, attachment metadata and form data - into _history tables inside one rollback-safe transaction, keeping attachment paths resolvable.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.395Z"
fingerprint: d27f073aa0b7d919258285e22376dd379c55e1e74d7e28c48440b1100569087a
source:
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/jobs/{job_id}/run"
    description:
      zh: >
          执行批次整单搬迁。
          
      en: >
          Runs a batch migration to the history store.
          
  - protocol: http
    method: POST
    path: "/api/v1/archive/jobs/{job_id}/rollback"
    description:
      zh: >
          回滚中断的搬迁批次。
          
      en: >
          Rolls back an interrupted migration batch.
          
deps:
  - kind: dataflow
    to: oa.form.matter
    from_api: "POST /api/v1/archive/jobs/{job_id}/run"
    label: {zh: "搬迁订单类表单数据", en: "Move form data of instances"}
  - kind: dataflow
    to: oa.workflow.runtime
    from_api: "POST /api/v1/archive/jobs/{job_id}/run"
    label: {zh: "搬迁实例与节点任务", en: "Move instance and tasks"}
  - kind: dataflow
    to: oa.audit.trace.thread
    from_api: "POST /api/v1/archive/jobs/{job_id}/run"
    label: {zh: "轨迹随整单迁移", en: "Move approval trail"}
  - kind: dataflow
    to: oa.sign.record
    from_api: "POST /api/v1/archive/jobs/{job_id}/run"
    label: {zh: "签名记录随单迁移", en: "Move signature records"}
  - kind: dataflow
    to: oa.archive.policy.migration.verify
    from_api: "POST /api/v1/archive/jobs/{job_id}/run"
    to_api: "POST /api/v1/archive/jobs/{job_id}/verify"
    label: {zh: "执行结果交核对", en: "Hand results to verification"}
---

## 证据锚点
- `doc/data-model.md` → `## 10. 归档策略（对应 REQ-NFR-010）`（§10. 归档策略）
