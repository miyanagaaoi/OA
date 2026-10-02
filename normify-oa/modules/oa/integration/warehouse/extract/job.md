---
uid: 48fbefd9
id: oa.integration.warehouse.extract.job
parent: oa.integration.warehouse.extract
state: planned
name: {zh: "抽取任务", en: "Extraction Job"}
description:
  zh: >
      创建与跟踪抽取任务，暴露任务状态，失败后可从断点续跑而不必全量重导。
      
  en: >
      Creates and tracks extraction jobs, exposing status so a failed run can be resumed without re-exporting everything.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.702Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 515
    end_line: 515
apis:
  - protocol: http
    method: POST
    path: "/api/v1/warehouse/extract-jobs"
    description:
      zh: >
          按期间创建抽取任务。
          
      en: >
          Creates an extraction job for a period.
          
  - protocol: http
    method: GET
    path: "/api/v1/warehouse/extract-jobs/{id}"
    description:
      zh: >
          查询抽取任务状态与进度。
          
      en: >
          Reads extraction job status and progress.
          
---
