---
uid: 29d31ba1
id: oa.archive.policy.migration.plan
parent: oa.archive.policy.migration
state: planned
name: {zh: "归档批次计划", en: "Archive Batch Plan"}
description:
  zh: >
      把归档候选编排为可重入的归档批次任务，记录批次范围、执行窗口与重试策略，执行前可预览影响条数。
      
  en: >
      Organizes archive candidates into re-entrant batch jobs, recording scope, execution window and retry policy, with an impact preview before execution.
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.678Z"
fingerprint: affe2755b0ec3d7b6adffe3259b4864634f794b5be85e144d9ec84d32aeba3d7
source:
  - path: "doc/data-model.md"
    line: 822
    end_line: 823
apis:
  - protocol: http
    method: POST
    path: "/api/v1/archive/jobs"
    description:
      zh: >
          创建归档批次任务。
          
      en: >
          Creates an archive batch job.
          
  - protocol: http
    method: GET
    path: "/api/v1/archive/jobs"
    description:
      zh: >
          查看归档批次任务与进度。
          
      en: >
          Lists archive batch jobs and progress.
          
deps:
  - kind: call
    to: oa.archive.policy.criteria
    from_api: "POST /api/v1/archive/jobs"
    to_api: "GET /api/v1/archive/candidates"
    label: {zh: "取归档候选", en: "Fetch archive candidates"}
  - kind: call
    to: oa.archive.policy.migration.execute
    from_api: "POST /api/v1/archive/jobs"
    to_api: "POST /api/v1/archive/jobs/{job_id}/run"
    label: {zh: "批次计划触发执行", en: "Trigger batch execution"}
  - kind: reference
    to: oa.admin.boundary
    from_api: "POST /api/v1/archive/jobs"
    label: {zh: "大批量搬迁前复核", en: "Review before bulk move"}
---
