---
uid: 4147748e
id: oa.workflow.runtime.node-instance.advance
parent: oa.workflow.runtime.node-instance
state: planned
name: {zh: "节点推进与回退", en: "Node Advancement & Return"}
description:
  zh: >
      节点通过后推进到下一节点（skip_condition 命中则标记已跳过并继续）、回退上一已完成节点重审（上一节点 returned_count+1，通过后自动回到本节点，同节点最多被回退 2 次，REQ-FLOW-021）、推进时为新节点生成任务。
      
  en: >
      Advances to the next node after approval (marking skip_condition hits as skipped and continuing), returns to the previous completed node for re-review (the previous node's returned_count+1, automatically returning here afterwards, at most twice per node, REQ-FLOW-021) and creates tasks for the newly entered node.
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.768Z"
fingerprint: 6dd8a5326256879a451e30d488649b7c90dc47267b9befea71a5e35929383e2d
source:
  - path: "doc/prd-0.1.md"
    line: 500
    end_line: 507
  - path: "doc/prd-0.1.md"
    line: 323
    end_line: 323
apis:
  - protocol: http
    method: POST
    path: "/api/v1/flow-node-instances/{node_instance_id}/advance"
    description:
      zh: >
          推进到下一节点并标记跳过节点。
          
      en: >
          Advances to the next node and marks skipped nodes.
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-node-instances/{node_instance_id}/return-previous"
    description:
      zh: >
          回退上一已完成节点重审。
          
      en: >
          Returns to the previous completed node for re-review.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-node-instances/{node_instance_id}/next"
    description:
      zh: >
          解析下一节点并应用跳过条件。
          
      en: >
          Resolves the next node, applying skip conditions.
          
deps:
  - kind: call
    to: oa.workflow.definition.node-behavior.skip
    from_api: "POST /api/v1/flow-node-instances/{node_instance_id}/advance"
    to_api: "GET /api/v1/flow-nodes/{node_id}/skip-condition"
    label: {zh: "解析跳过条件", en: "Evaluate skip condition"}
  - kind: call
    to: oa.workflow.runtime.counters
    from_api: "POST /api/v1/flow-node-instances/{node_instance_id}/return-previous"
    to_api: "POST /api/v1/flow-instances/{instance_id}/counters/increment"
    label: {zh: "回退计数自增", en: "Count return per node"}
  - kind: call
    to: oa.workflow.task
    from_api: "POST /api/v1/flow-node-instances/{node_instance_id}/advance"
    label: {zh: "为新节点生成任务", en: "Create tasks for node"}
  - kind: call
    to: oa.workflow.routing
    from_api: "POST /api/v1/flow-node-instances/{node_instance_id}/return-previous"
    label: {zh: "回退记入流转链", en: "Log return in routing"}
  - kind: call
    to: oa.workflow.runtime.node-instance.state
    from_api: "POST /api/v1/flow-node-instances/{node_instance_id}/advance"
    to_api: "PUT /api/v1/flow-node-instances/{node_instance_id}/status"
    label: {zh: "更新节点状态", en: "Update node status"}
---
