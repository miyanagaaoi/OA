---
uid: a591afec
id: oa.admin.report.volume.duration
parent: oa.admin.report.volume
name: {zh: "平均耗时统计", en: "Average Duration"}
description:
  zh: >
      根据节点实例的开始与完成时间戳，计算整单与各节点的平均耗时，并给出最慢节点排行。
      
  en: >
      Computes average elapsed time for whole documents and for each node from node-instance start and finish timestamps, and ranks the slowest nodes.
      
revision: b08abc4417060c06343ed11a019f7fea44966e64
updated_at: "2026-10-03T08:18:39.916Z"
fingerprint: 5892235bf362330ec273a37ebb0cbbea0196d4812363113164ca1a4cd71bb494
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
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

## 证据锚点
- `doc/prd-0.1.md` → `REQ-ADMIN-005`（§6.10 管理后台）
- `doc/data-model.md` → `CREATE TABLE flow_node_instance`（§5. 流程运行时）
