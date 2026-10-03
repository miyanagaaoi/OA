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
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.678Z"
fingerprint: 9fd64de2f67f7b0a8cc7047fb4bd8b44ebe03e4d2344e11d6ccc469b5b3bda3c
source:
  - path: "doc/prd-0.1.md"
    line: 515
    end_line: 515
apis:
  - protocol: kafka
    path: "oa.archive.document.extracted"
    description:
      zh: >
          归档单据抽取完成事件。
          
      en: >
          Event emitted after archive documents are extracted.
          
---
