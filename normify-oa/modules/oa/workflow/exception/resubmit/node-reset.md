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
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.812Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 389
    end_line: 389
  - path: "doc/prd-0.1.md"
    line: 480
    end_line: 480
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
