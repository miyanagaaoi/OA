---
uid: 3d95809b
id: oa.workflow.runtime.node-instance
parent: oa.workflow.runtime
name: {zh: "节点实例状态机", en: "Node Instance State Machine"}
description:
  zh: >
      节点实例运行时状态（flow_node_instance）：未开始 → 进行中 active → 等待补件 waiting_supplement → 已通过/已驳回/已跳过/已退回/已取消，覆盖节点推进、被退回重审与跳过留痕（REQ-FLOW-001/014/021/023）。
      
  en: >
      Node-level runtime state on flow_node_instance: pending → active → waiting_supplement → approved / rejected / skipped / returned / cancelled, covering node advancement, return-for-review and skip marking (REQ-FLOW-001/014/021/023).
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.419Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
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
