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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.434Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
---

## 证据锚点
- `doc/prd-0.1.md` → `### 7.2 状态机`（§7.2 状态机）
- `doc/data-model.md` → `CREATE TABLE flow_instance`（§5. 流程运行时）
