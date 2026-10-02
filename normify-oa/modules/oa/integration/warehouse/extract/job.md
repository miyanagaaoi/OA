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
      
revision: c3342bbdedcde68c9955e4600fa972afa9b10579
updated_at: "2026-10-02T10:35:01.753Z"
fingerprint: 7872b306824e7e0aec72e7e778da11de4bba5c2f41495b6a049266a26da5e9b1
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
