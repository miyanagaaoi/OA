---
uid: 2a21dc6e
id: oa.archive.policy.migration.execute
parent: oa.archive.policy.migration
state: planned
name: {zh: "整单搬迁执行", en: "Archive Migration Run"}
description:
  zh: >
      以整单为单位把实例、节点、任务、轨迹、签名、附件元数据与表单数据搬移至 _history 历史库表，单事务可回滚，附件路径保持可解析。
      
  en: >
      Moves each document as a unit - instance, nodes, tasks, trail, signatures, attachment metadata and form data - into _history tables inside one rollback-safe transaction, keeping attachment paths resolvable.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.539Z"
fingerprint: 1d71d83c11c75a93b7ff4af24882ab247a2d9cf90243263cdddb7b8ade83fa75
source:
  - path: "doc/data-model.md"
    line: 822
    end_line: 826
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
