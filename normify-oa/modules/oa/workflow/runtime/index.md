---
uid: 2d3d5e47
id: oa.workflow.runtime
parent: oa.workflow
state: planned
name: {zh: "实例运行时与状态机", en: "Instance Runtime & State Machine"}
description:
  zh: >
      实例与节点实例运行时：草稿→审批中→已通过，含已驳回/已撤回/已终止分支与「待补件」子状态、当前节点指针、流转与补件计数器；节点或实例转入终态时对其余任务与节点实例的联动关闭。
      
  en: >
      Instance and node-instance runtime: draft → in approval → approved, with rejected/withdrawn/terminated reaches, the pending-supplement sub-status, current node pointer, routing and supplement counters, and the cascade rules that close tasks when a node or instance reaches a terminal state.
      
revision: 939b76191ad354700ff099851baf5cadf4a0db09
updated_at: "2026-10-03T04:05:58.691Z"
fingerprint: 6d9cef647f836df43b112d559d9426ebc050ad12b109eafc2db793d5d2a1b3b1
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 7.2 状态机`（§7.2 状态机）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
