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
      
revision: 112ab0a1d46779714029044fc8e0b46627804f30
updated_at: "2026-10-03T01:51:21.709Z"
fingerprint: 096973f1fba51dd7db650df9d8f89410da3245f99fe2654f82416f2b8bc3909e
source:
  - path: "doc/prd-0.1.md"
    line: 471
    end_line: 508
  - path: "doc/data-model.md"
    line: 375
    end_line: 528
---
