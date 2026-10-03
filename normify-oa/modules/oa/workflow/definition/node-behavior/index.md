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
      
revision: 257a32acb48c626488a22291ada46052401b64c8
updated_at: "2026-10-03T05:17:27.422Z"
fingerprint: 06cb98ae93ba59a5716fa191b94af0a4ddc4f8a19f41d71a41adcc39f3ee37a7
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
