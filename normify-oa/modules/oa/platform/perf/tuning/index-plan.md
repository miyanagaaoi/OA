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
      
revision: c758a5ce22cb282c4b7c7462f8c4c29ee3818a73
updated_at: "2026-10-03T05:55:35.565Z"
fingerprint: 4e545cc1c566ce9e10c8fb0b82fc64bfd49ae277034ea19e5092531bb0c1231e
source:
  - path: "doc/data-model.md"
apis:
  - protocol: file
    path: "config/db/index-plan.yml"
    description:
      zh: >
          热点查询路径的索引清单。
          
      en: >
          Declared index plan for the hot query paths.
          
---

## 证据锚点
- `doc/data-model.md` → `## 11. 表清单与需求追溯`（§11. 表清单与需求追溯）
