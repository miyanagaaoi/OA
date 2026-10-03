---
uid: 0c7218c7
id: oa.workflow.definition.node-behavior
parent: oa.workflow.definition
state: planned
name: {zh: "节点行为配置", en: "Node Behavior Config"}
description:
  zh: >
      节点级行为配置三件套：决议模式与通过阈值、签名策略与超时/加签跳转开关、跳过条件解析；均在流程设计器中逐节点配置，并在发起时冻结进节点实例作为运行时依据。
      
  en: >
      Node-level behavior configuration in three parts: decision mode and pass threshold, signature policy with timeout/add-sign/jump switches, and skip-condition resolution; all configured per node in the designer and frozen into node instances at submission.
      
revision: e6f40ca3d3fabae44e2601c81472fafe9370a8b7
updated_at: "2026-10-03T06:52:10.436Z"
fingerprint: f2112a3f56e4ca573fed099fe39c14837f7bba516b0b76dfa0b1ee47e81fcc06
source:
  - path: "doc/data-model.md"
  - path: "doc/prd-0.1.md"
deps:
  - kind: reference
    to: oa.workflow.definition.node-schema
    label: {zh: "配置落在节点表列", en: "Behavior lives on flow_node"}
---

## 证据锚点
- `doc/data-model.md` → `CREATE TABLE flow_node`（§4. 流程定义）
- `doc/prd-0.1.md` → `REQ-FLOW-002`（§6.4 流程引擎核心能力）
