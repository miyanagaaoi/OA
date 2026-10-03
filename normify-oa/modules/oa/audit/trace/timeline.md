---
uid: 094f5e5a
id: oa.audit.trace.timeline
parent: oa.audit.trace
state: planned
name: {zh: "轨迹时间轴展示", en: "Trail Timeline"}
description:
  zh: >
      按轨迹顺序输出单据审批时间轴，以及按节点实例分组的轨迹视图，供详情页、打印与历史预览复用；读侧不做任何写回。
      
  en: >
      Renders the per-document approval timeline by trail sequence and a node-grouped trail view reused by detail, print and archived preview; the read side never writes back.
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.551Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 427
    end_line: 427
  - path: "doc/data-model.md"
    line: 625
    end_line: 642
apis:
  - protocol: http
    method: GET
    path: "/api/v1/instances/{instance_id}/trail"
    description:
      zh: >
          按顺序返回单据审批时间轴。
          
      en: >
          Returns the ordered approval timeline of a document.
          
  - protocol: http
    method: GET
    path: "/api/v1/instances/{instance_id}/trail/nodes"
    description:
      zh: >
          按节点实例分组返回轨迹。
          
      en: >
          Returns the trail grouped by node instance.
          
deps:
  - kind: dataflow
    to: oa.audit.trace.thread
    from_api: "GET /api/v1/instances/{instance_id}/trail"
    to_api: "mysql:sys_thread"
    label: {zh: "读取轨迹事件", en: "Read trail events"}
  - kind: call
    to: oa.portal.detail
    from_api: "GET /api/v1/instances/{instance_id}/trail"
    label: {zh: "详情页时间轴渲染", en: "Render timeline in detail"}
---
