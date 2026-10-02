---
uid: 3d95809b
id: oa.workflow.runtime.node-instance
parent: oa.workflow.runtime
state: planned
name: {zh: "节点实例状态机", en: "Node Instance State Machine"}
description:
  zh: >
      节点实例运行时状态（flow_node_instance）：未开始 → 进行中 active → 等待补件 waiting_supplement → 已通过/已驳回/已跳过/已退回/已取消，覆盖节点推进、被退回重审与跳过留痕（REQ-FLOW-001/014/021/023）。
      
  en: >
      Node-level runtime state on flow_node_instance: pending → active → waiting_supplement → approved / rejected / skipped / returned / cancelled, covering node advancement, return-for-review and skip marking (REQ-FLOW-001/014/021/023).
      
revision: c2ffc2b95024aef3046aa878cf334f4b21fad885
updated_at: "2026-10-02T09:24:29.824Z"
fingerprint: 6be246c6834b9b56d4a42d5b955150bb6a06a766a90c031fd7f3af6b4c917a6d
source:
  - path: "doc/prd-0.1.md"
    line: 488
    end_line: 488
  - path: "doc/data-model.md"
    line: 420
    end_line: 447
deps:
  - kind: reference
    to: oa.workflow.runtime.instance.state
    to_api: "GET /api/v1/flow-instances/{instance_id}/state"
    label: {zh: "驱动实例状态", en: "Feed instance state"}
---
