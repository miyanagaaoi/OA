---
uid: a591afec
id: oa.admin.report.volume.duration
parent: oa.admin.report.volume
state: planned
name: {zh: "平均耗时统计", en: "Average Duration"}
description:
  zh: >
      根据节点实例的开始与完成时间戳，计算整单与各节点的平均耗时，并给出最慢节点排行。
      
  en: >
      Computes average elapsed time for whole documents and for each node from node-instance start and finish timestamps, and ranks the slowest nodes.
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.646Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 439
    end_line: 439
  - path: "doc/data-model.md"
    line: 402
    end_line: 403
apis:
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/flow-duration"
    description:
      zh: >
          整单与节点平均耗时。
          
      en: >
          Average duration for whole documents and for nodes.
          
  - protocol: http
    method: GET
    path: "/api/v1/admin/reports/flow-duration/slowest"
    description:
      zh: >
          最慢节点排行。
          
      en: >
          Ranking of the slowest nodes.
          
deps:
  - kind: reference
    to: oa.workflow.runtime
    label: {zh: "耗时取自节点实例时间戳", en: "Duration from node runs"}
---
