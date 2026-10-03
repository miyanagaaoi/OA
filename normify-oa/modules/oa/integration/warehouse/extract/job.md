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
      
revision: 132f2f51c4aae5754c6b7e000d979f87a56fe10a
updated_at: "2026-10-03T02:09:10.345Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
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
