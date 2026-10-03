---
uid: c75c4371
id: oa.platform.perf.tuning.index-plan
parent: oa.platform.perf.tuning
state: planned
name: {zh: "索引设计", en: "Index Plan"}
description:
  zh: >
      热点路径的索引清单：按处理人与状态查任务、按单号查实例、流转与补件链路。
      
  en: >
      Declared index plan for the hot paths: tasks by assignee and status, instance lookup by document number, routing and supplement chains.
      
revision: c22d447e6e63ccb0edfd9624026f21e8d1413077
updated_at: "2026-10-03T02:20:07.693Z"
fingerprint: 1d71d83c11c75a93b7ff4af24882ab247a2d9cf90243263cdddb7b8ade83fa75
source:
  - path: "doc/data-model.md"
    line: 800
    end_line: 817
apis:
  - protocol: file
    path: "config/db/index-plan.yml"
    description:
      zh: >
          热点查询路径的索引清单。
          
      en: >
          Declared index plan for the hot query paths.
          
---
