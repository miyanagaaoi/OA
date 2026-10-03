---
uid: 3f4c61b7
id: oa.workflow.runtime.node-instance.state
parent: oa.workflow.runtime.node-instance
state: planned
name: {zh: "节点状态与更新", en: "Node Status & Updates"}
description:
  zh: >
      节点实例状态枚举与落库（pending/active/waiting_supplement/approved/rejected/skipped/returned/cancelled，值域与 doc/enums.md §5 一致），含候选人快照 approver_ids_json 与发起时冻结的决议模式、pass_threshold、returned_count、supplement_requested 字段维护（该表唯一写方）。
      
  en: >
      Persists the node instance status enum (pending/active/waiting_supplement/approved/rejected/skipped/returned/cancelled, per doc/enums.md section 5) together with the frozen candidate snapshot approver_ids_json, decision mode, pass_threshold, returned_count and supplement_requested (the single writer of that table).
      
revision: c974d064e39527a7b4ddd8fe34345b4615b42437
updated_at: "2026-10-03T03:27:10.314Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/data-model.md"
    line: 495
    end_line: 524
  - path: "doc/prd-0.1.md"
    line: 478
    end_line: 478
apis:
  - protocol: mysql
    path: "flow_node_instance"
    description:
      zh: >
          节点实例表：节点运行态与计数。
          
      en: >
          Node instance table: per-node status and runtime counters.
          
  - protocol: http
    method: GET
    path: "/api/v1/flow-instances/{instance_id}/node-instances"
    description:
      zh: >
          列出实例的节点实例及其状态。
          
      en: >
          Lists node instances of an instance with statuses.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-node-instances/{node_instance_id}/status"
    description:
      zh: >
          落库节点实例状态变更。
          
      en: >
          Persists a node instance status change.
          
  - protocol: kafka
    path: "oa.workflow.node-instance.status-changed"
    description:
      zh: >
          节点状态变更事件，供轨迹与通知消费。
          
      en: >
          Node status change event for traces and notifications.
          
deps:
  - kind: call
    to: oa.workflow.runtime.instance.state
    from_api: "PUT /api/v1/flow-node-instances/{node_instance_id}/status"
    to_api: "GET /api/v1/flow-instances/{instance_id}/state"
    label: {zh: "汇总到实例状态", en: "Roll up to instance"}
  - kind: dataflow
    to: oa.audit.trace
    from_api: "kafka:oa.workflow.node-instance.status-changed"
    label: {zh: "节点状态入轨迹", en: "Node status to trace"}
---
