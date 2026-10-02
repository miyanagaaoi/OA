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
      
revision: "0000000000000000000000000000000000000000"
updated_at: "2026-10-02T08:01:04.995Z"
fingerprint: pending
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
