---
uid: 5277b5b1
id: oa.integration.warehouse.extract.event
parent: oa.integration.warehouse.extract
state: planned
name: {zh: "抽取完成事件", en: "Extraction Event"}
description:
  zh: >
      每次抽取完成后发出事件，下游管道无需轮询文件即可开始加载。
      
  en: >
      Emits an extracted event per completed run so downstream pipelines can start loading without polling files.
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.551Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
apis:
  - protocol: kafka
    path: "oa.archive.document.extracted"
    description:
      zh: >
          归档单据抽取完成事件。
          
      en: >
          Event emitted after archive documents are extracted.
          
---

## 证据锚点
- `doc/prd-0.1.md` → `### 8.1 接口设计原则`（§8.1 接口设计原则）
