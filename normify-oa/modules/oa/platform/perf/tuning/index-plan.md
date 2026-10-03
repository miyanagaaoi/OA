---
uid: c75c4371
id: oa.platform.perf.tuning.index-plan
parent: oa.platform.perf.tuning
name: {zh: "索引设计", en: "Index Plan"}
description:
  zh: >
      热点路径的索引清单：按处理人与状态查任务、按单号查实例、流转与补件链路。
      
  en: >
      Declared index plan for the hot paths: tasks by assignee and status, instance lookup by document number, routing and supplement chains.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:40.099Z"
fingerprint: d27f073aa0b7d919258285e22376dd379c55e1e74d7e28c48440b1100569087a
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
