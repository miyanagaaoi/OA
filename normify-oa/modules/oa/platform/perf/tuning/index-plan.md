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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.270Z"
fingerprint: 0f754e6e48eb41faa517ea45e35f49a012f228e36b05c082501dd8c8a7c05aae
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
