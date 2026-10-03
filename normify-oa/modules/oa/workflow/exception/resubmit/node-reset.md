---
uid: 733ce7d6
id: oa.workflow.exception.resubmit.node-reset
parent: oa.workflow.exception.resubmit
state: planned
name: {zh: "已审节点重置", en: "Reset Reviewed Nodes"}
description:
  zh: >
      重新提交时已审过的节点不保留：清空上一次的节点实例、任务与流转计数（routing_seq/routing_count/supplement_count 视策略重置），从①重新生成节点实例与任务，避免复用过期决议。
      
  en: >
      When a document is resubmitted the previously reviewed nodes are not kept: node instances, tasks and routing counters from the earlier attempt are cleared according to policy and node instances plus tasks are recreated from node one, so no stale decision is reused.
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.427Z"
fingerprint: 7c384f475eefe31be143493f21b22df3dd45e45087da0f4d0d7eb7e98cc2078b
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/prd-0.1.md"
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow/instances/{instance_id}/node-instances/reset"
    description:
      zh: >
          重置已审节点与任务（从①重走）。
          
      en: >
          Reset reviewed nodes and tasks so the flow restarts from node one.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow/instances/{instance_id}/reset-plan"
    description:
      zh: >
          重置计划预览（将重建的节点与任务清单）。
          
      en: >
          Preview the nodes and tasks that will be rebuilt.
          
deps:
  - kind: call
    to: oa.workflow.runtime
    from_api: "POST /api/v1/flow/instances/{instance_id}/node-instances/reset"
    label: {zh: "重建节点实例与任务", en: "Rebuild nodes and tasks"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-017`（§6.6 异常路径）
