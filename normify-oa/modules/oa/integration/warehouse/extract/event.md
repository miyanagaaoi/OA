---
uid: 5277b5b1
id: oa.integration.warehouse.extract.event
parent: oa.integration.warehouse.extract
name: {zh: "抽取完成事件", en: "Extraction Event"}
description:
  zh: >
      每次抽取完成后发出事件，下游管道无需轮询文件即可开始加载。
      
  en: >
      Emits an extracted event per completed run so downstream pipelines can start loading without polling files.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.080Z"
fingerprint: acf5d54845d53a19544bf9794fc33dc41c37be311916cfcfbf52a40c34cc40cf
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
