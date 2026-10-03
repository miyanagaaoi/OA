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
      
revision: 44fc7aba1c7e884ffa3553faf31bfce974b388a9
updated_at: "2026-10-03T04:12:20.760Z"
fingerprint: 3b00610613fe0f45aad673a1508d23c3d3cd2c88a03dfe3d41047751592de232
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
