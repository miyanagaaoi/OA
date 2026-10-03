---
uid: 0d27ae7e
id: oa.workflow.definition.node-behavior.decision
parent: oa.workflow.definition.node-behavior
state: planned
name: {zh: "决议模式与通过阈值", en: "Decision Mode & Pass Threshold"}
description:
  zh: >
      decision_mode（any 或签，默认 / all 会签 / sequence 依次审批）与 pass_threshold（"50%" 百分比或 "2" 绝对人数，两者同时存在时绝对人数优先）的写入与解析；会签节点必须显式定义驳回即终止或驳回即回退（REQ-FLOW-002）。
      
  en: >
      Writes and resolves decision_mode (any-sign default / countersign / sequential) and pass_threshold (percentage such as "50%" or absolute such as "2"; absolute wins when both are set). A countersign node must explicitly declare reject-terminate or reject-return (REQ-FLOW-002).
      
revision: d5b96030fa491b789a78f1772859bcc23cb95a78
updated_at: "2026-10-03T01:41:29.794Z"
fingerprint: 45de2030cdb84b6b065d6ae29d070a0561de3211039af1703055f01a49a9f2e4
source:
  - path: "doc/prd-0.1.md"
    line: 218
    end_line: 232
  - path: "doc/data-model.md"
    line: 309
    end_line: 310
apis:
  - protocol: http
    method: GET
    path: "/api/v1/flow-nodes/{node_id}/decision"
    description:
      zh: >
          读取节点决议模式、阈值与驳回策略。
          
      en: >
          Reads the node decision mode, threshold and reject policy.
          
  - protocol: http
    method: PUT
    path: "/api/v1/flow-nodes/{node_id}/decision"
    description:
      zh: >
          写入决议模式、通过阈值与驳回即终止/回退。
          
      en: >
          Writes decision mode, pass threshold and reject-terminate/return.
          
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
