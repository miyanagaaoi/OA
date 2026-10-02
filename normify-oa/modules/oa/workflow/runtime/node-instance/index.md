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
      
revision: 0c43a9d242a848aa27f0f7096f3f665de30618cc
updated_at: "2026-10-02T08:54:26.769Z"
fingerprint: d5b4933a64a6731ce61ce8bf6124aed59bc0b9c7e8708d0d9023a825d94afebf
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
