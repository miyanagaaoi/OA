---
uid: 0d27ae7e
id: oa.workflow.definition.node-behavior.decision
parent: oa.workflow.definition.node-behavior
name: {zh: "决议模式与通过阈值", en: "Decision Mode & Pass Threshold"}
description:
  zh: >
      decision_mode（any 或签，默认 / all 会签 / sequence 依次审批）与 pass_threshold（"50%" 百分比或 "2" 绝对人数，两者同时存在时绝对人数优先）的写入与解析；驳回后的去向**一期固定为「回到发起人」，不做节点级配置**（会签/协同任务中任一人驳回即该节点驳回；节点级驳回去向属二期，见 doc/prd-0.1.md §5.4）。
      
  en: >
      Writes and resolves decision_mode (any-sign default / countersign / sequential) and pass_threshold (percentage such as "50%" or absolute such as "2"; absolute wins when both are set). The post-rejection destination is fixed to "back to the initiator" in phase 1, with no per-node configuration (any one rejection in a countersign/collaboration task rejects that node; per-node reject routing belongs to phase 2; see doc/prd-0.1.md §5.4).
      
revision: 7e0c41c54edf2d106fd4e2a995349e6c3132252f
updated_at: "2026-10-03T07:15:53.403Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/prd-0.1.md"
  - path: "doc/data-model.md"
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-nodes/{node_id}/decision"
    description:
      zh: >
          读取节点决议模式与通过阈值（**一期不提供驳回去向的读接口**：驳回后去向固定为「回到发起人」，不做节点级配置，节点级属二期）。
          
      en: >
          Reads the node decision mode and pass threshold (phase 1 exposes NO reject-routing read endpoint: reject always returns to the initiator, no per-node config; phase 2).
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-nodes/{node_id}/decision"
    description:
      zh: >
          写入决议模式与通过阈值（**一期不提供驳回去向的写接口**：「驳回即终止/回退」等节点级驳回去向属二期，见 doc/prd-0.1.md §5.4）。
          
      en: >
          Writes the decision mode and pass threshold (phase 1 exposes NO reject-routing write endpoint: per-node reject terminate/return belongs to phase 2, PRD 5.4).
          
  - protocol: http
    method: POST
    path: "/api/v1/flow-nodes/{node_id}/decision/resolve"
    description:
      zh: >
          按候选人集合解析通过条件（百分比/绝对人数，绝对优先）。
          
      en: >
          Resolves the pass condition from the candidate set (percentage or absolute, absolute first).
          
deps:
  - kind: reference
    to: oa.workflow.definition.node-schema
    from_api: "PUT /api/v1/flow-nodes/{node_id}/decision"
    to_api: "PUT /api/v1/flow-nodes/{node_id}"
    label: {zh: "决议字段属于节点", en: "Decision fields on node"}
---

## 证据锚点
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
