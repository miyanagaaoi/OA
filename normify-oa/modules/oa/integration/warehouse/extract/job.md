---
uid: 48fbefd9
id: oa.integration.warehouse.extract.job
parent: oa.integration.warehouse.extract
name: {zh: "抽取任务", en: "Extraction Job"}
description:
  zh: >
      创建与跟踪抽取任务，暴露任务状态，失败后可从断点续跑而不必全量重导。
      
  en: >
      Creates and tracks extraction jobs, exposing status so a failed run can be resumed without re-exporting everything.
      
revision: 132aa90a08178648b1a131bbeda138f5fe01cc16
updated_at: "2026-10-03T07:42:28.488Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
