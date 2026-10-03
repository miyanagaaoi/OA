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
      
revision: 94b9772b3364afcdfb2ecb408d7293d22a24a92f
updated_at: "2026-10-03T06:13:00.330Z"
fingerprint: dafba8454c2ea5a5ae6ebbfb022f1ad604e10180af201758bcae5ef7145ee3cf
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
deps:
  - kind: reference
    to: oa.workflow.runtime.instance.state
    to_api: "GET /api/v1/flow-instances/{instance_id}/state"
    label: {zh: "驱动实例状态", en: "Feed instance state"}
---

## 证据锚点
- `doc/prd-0.1.md` → `### 7.2 状态机`（§7.2 状态机）
- `doc/data-model.md` → `CREATE TABLE flow_node_instance`（§5. 流程运行时）
